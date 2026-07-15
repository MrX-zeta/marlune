package com.luis.marlune.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.luis.marlune.data.datastore.LyricsFolderStore
import com.luis.marlune.data.lyrics.LrcParser
import com.luis.marlune.domain.model.Lyrics
import com.luis.marlune.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Fuente de letras 100% LOCAL. Prioridad: (1) archivo `.lrc` en la carpeta que el usuario concede por
 * SAF; (2) [Fase 2] tags embebidos. Lectura/parseo en [Dispatchers.IO], cacheado por canción.
 *
 * La resolución del `.lrc` es TOLERANTE (los descargadores renombran: sufijos, acentos, espacios)
 * pero CONSERVADORA (mejor no mostrar letra que mostrar la de otra pista): exacto → normalizado →
 * prefijo (solape máximo, empate = ninguno) → metadatos (artista Y título). Ver [matchLrc].
 */
class LyricsRepository(
    context: Context,
    private val folderStore: LyricsFolderStore,
) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** Hay carpeta de letras concedida (para el estado vacío ofrecer o no el pick). */
    val hasFolder: Flow<Boolean> = folderStore.folderUri.map { it != null }

    // Índice de .lrc del árbol concedido (base cruda + normalizada + doc), cacheado por árbol.
    private val indexMutex = Mutex()
    @Volatile private var indexedTree: String? = null
    @Volatile private var lrcIndex: List<LrcEntry> = emptyList()

    // Caché por canción (incluye "no hay letra" para no re-buscar en cada apertura).
    private val cache = ConcurrentHashMap<Long, CachedLyrics>()

    /** Persiste la carpeta SAF, toma el permiso persistente e invalida índice y caché. */
    suspend fun setFolder(uri: Uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Invalidar ANTES de emitir la carpeta nueva: el re-resolve que dispara `hasFolder=true`
            // no debe encontrar el "sin letra" cacheado de cuando aún no había carpeta (era la causa
            // de "no sale a la primera").
            cache.clear()
            indexMutex.withLock {
                indexedTree = null
                lrcIndex = emptyList()
            }
            folderStore.setFolder(uri)
            // Pre-construye el índice para que la primera letra tras conceder salga sin esperar el scan.
            runCatching { ensureIndex(uri) }
        }
    }

    /** Letra de [song] (o `null` si no hay). Cacheada; segura para llamar por cada cambio de pista. */
    suspend fun lyricsFor(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        cache[song.id]?.let { return@withContext it.value }
        val resolved = loadFromLrc(song)
        cache[song.id] = CachedLyrics(resolved)
        resolved
    }

    private suspend fun loadFromLrc(song: Song): Lyrics? {
        val tree = folderStore.folderUri.first() ?: return null
        val entry = matchLrc(song, ensureIndex(tree)) ?: return null
        val text = runCatching {
            resolver.openInputStream(entry.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull() ?: return null
        return LrcParser.parse(text)
    }

    /**
     * Resuelve el `.lrc` de [song] de forma tolerante y conservadora. Etapas (en orden; una etapa
     * ambigua NO adivina, cae a la siguiente):
     *  1. Nombre base EXACTO (ignora mayúsculas).
     *  2. Nombre base NORMALIZADO igual (sin acentos, espacios/underscores, sufijos de descargador).
     *  3. PREFIJO: uno empieza por el otro (normalizado); mayor solape; empate → ninguno.
     *  4. METADATOS: el nombre contiene artista Y título de los tags.
     */
    private fun matchLrc(song: Song, entries: List<LrcEntry>): LrcEntry? {
        val audioBase = song.displayName.substringBeforeLast('.')
        val audioNorm = normalize(audioBase)
        Log.d(LYRICS_TAG, "buscar audio base='$audioBase' norm='$audioNorm' (.lrc en carpeta=${entries.size})")
        if (audioNorm.isEmpty() || entries.isEmpty()) {
            Log.d(LYRICS_TAG, "NO MATCH (base vacía o carpeta sin .lrc)")
            return null
        }

        // 1) exacto
        entries.firstOrNull { it.base.equals(audioBase, ignoreCase = true) }
            ?.let { return matched("1-exacto", it) }

        // 2) normalizado (único)
        entries.filter { it.normalized == audioNorm }.singleOrNull()
            ?.let { return matched("2-normalizado", it) }

        // 3) prefijo con mayor solape; empate → ninguno (sigue a metadatos)
        val prefix = entries.mapNotNull { e ->
            val overlap = when {
                e.normalized.startsWith(audioNorm) -> audioNorm.length
                audioNorm.startsWith(e.normalized) -> e.normalized.length
                else -> 0
            }
            if (overlap > 0) e to overlap else null
        }
        if (prefix.isNotEmpty()) {
            val max = prefix.maxOf { it.second }
            val best = prefix.filter { it.second == max }.map { it.first }
            best.singleOrNull()?.let { return matched("3-prefijo(solape=$max)", it) }
            Log.d(LYRICS_TAG, "prefijo AMBIGUO (${best.size} con solape $max): ${best.map { it.base }} -> sigo")
        }

        // 4) metadatos: nombre contiene artista Y título (único)
        val artist = normalize(song.artist)
        val title = normalize(song.title)
        if (artist.isNotBlank() && title.isNotBlank()) {
            entries.filter { it.normalized.contains(artist) && it.normalized.contains(title) }
                .singleOrNull()
                ?.let { return matched("4-metadatos", it) }
        }

        val nearMisses = entries.filter { title.isNotBlank() && it.normalized.contains(title) }.take(8).map { it.base }
        Log.d(LYRICS_TAG, "NO MATCH para '$audioBase'. Candidatos con título: $nearMisses")
        return null
    }

    private fun matched(stage: String, e: LrcEntry): LrcEntry {
        Log.d(LYRICS_TAG, "MATCH [$stage] -> '${e.base}'")
        return e
    }

    /**
     * Normaliza para comparar: minúsculas, sin acentos, sin sufijos de descargador al final
     * (`_private`, `_lyrics`, `(1)`, …), y todo lo no alfanumérico colapsado a espacios.
     */
    private fun normalize(raw: String): String {
        var s = raw.lowercase(Locale.ROOT)
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "") // quita acentos
        // sufijos añadidos por descargadores, al final y posiblemente repetidos.
        s = s.replace(Regex("(?:[_\\-\\s]*(?:private|lyrics?|karaoke)|\\s*\\(\\d+\\))+\\s*$"), "")
        return s.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    private suspend fun ensureIndex(tree: Uri): List<LrcEntry> = indexMutex.withLock {
        if (indexedTree == tree.toString()) return lrcIndex
        val built = runCatching { buildLrcIndex(tree) }.getOrDefault(emptyList())
        lrcIndex = built
        indexedTree = tree.toString()
        built
    }

    /** Recorre el árbol SAF (BFS con `DocumentsContract`) recogiendo solo los `.lrc`. */
    private fun buildLrcIndex(tree: Uri): List<LrcEntry> {
        val out = ArrayList<LrcEntry>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val stack = ArrayDeque<String>().apply { add(DocumentsContract.getTreeDocumentId(tree)) }
        while (stack.isNotEmpty()) {
            val docId = stack.removeLast()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            runCatching {
                resolver.query(childrenUri, projection, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val childId = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                            stack.add(childId)
                        } else if (name.endsWith(".lrc", ignoreCase = true)) {
                            val base = name.dropLast(4)
                            out += LrcEntry(
                                base = base,
                                normalized = normalize(base),
                                uri = DocumentsContract.buildDocumentUriUsingTree(tree, childId),
                            )
                        }
                    }
                }
            }
        }
        return out
    }

    private class LrcEntry(val base: String, val normalized: String, val uri: Uri)

    private class CachedLyrics(val value: Lyrics?)

    private companion object {
        const val LYRICS_TAG = "MarluneLyrics"
    }
}
