package com.luis.marlune.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.luis.marlune.data.datastore.LyricsFolderStore
import com.luis.marlune.data.lyrics.LrcParser
import com.luis.marlune.domain.model.Lyrics
import com.luis.marlune.domain.model.LyricsFolderRequest
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

/** Resultado de conceder una carpeta en el contexto de una canción. */
enum class AddFolderResult { ADDED, WRONG_FOLDER }

/**
 * Fuente de letras 100% LOCAL. Prioridad: (1) `.lrc` en las carpetas SAF concedidas; (2) [Fase 2]
 * tags embebidos. Lectura/parseo en [Dispatchers.IO], cacheado por canción.
 *
 * Multi-carpeta: la música puede estar repartida; se guardan TODAS las carpetas concedidas y se
 * buscan los `.lrc` en todas. La app DEDUCE la carpeta de cada canción (RELATIVE_PATH) para
 * preseleccionarla y no pedir al usuario que la busque. La resolución del `.lrc` es TOLERANTE pero
 * CONSERVADORA (ver [matchLrc]).
 */
class LyricsRepository(
    context: Context,
    private val folderStore: LyricsFolderStore,
) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** Carpetas SAF concedidas (para re-resolver al conceder y para Ajustes). */
    val grantedFolders: Flow<Set<Uri>> = folderStore.folders

    // Índice de .lrc de TODAS las carpetas concedidas, cacheado hasta que cambie el conjunto.
    private val indexMutex = Mutex()
    @Volatile private var indexedKey: String? = null
    @Volatile private var lrcIndex: List<LrcEntry> = emptyList()

    // Caché por canción (incluye "no hay letra" para no re-buscar en cada apertura).
    private val cache = ConcurrentHashMap<Long, CachedLyrics>()

    /** Letra de [song] (o `null` si no hay). Cacheada; segura para llamar por cada cambio de pista. */
    suspend fun lyricsFor(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        cache[song.id]?.let { return@withContext it.value }
        val resolved = loadFromLrc(song)
        cache[song.id] = CachedLyrics(resolved)
        resolved
    }

    /**
     * Si la carpeta de [song] AÚN no está concedida, devuelve la solicitud (nombre visible + URI
     * inicial para preseleccionarla). Si ya hay acceso (o no se puede deducir la carpeta), `null`.
     */
    suspend fun folderRequestFor(song: Song): LyricsFolderRequest? {
        val rel = pathOf(song.relativePath)
        if (rel.isEmpty()) return null // API 28 / sin RELATIVE_PATH: no se puede deducir
        val trees = folderStore.folders.first()
        if (trees.any { coversSong(it, song) }) return null // ya cubierta → no preguntar
        val initialUri = runCatching {
            DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:$rel")
        }.getOrNull()
        return LyricsFolderRequest(folderName = rel.substringAfterLast('/'), initialUri = initialUri)
    }

    /**
     * Concede una carpeta en el contexto de [song]: si NO contiene a la canción, la rechaza
     * ([AddFolderResult.WRONG_FOLDER]) sin persistir; si la contiene (o no hay canción/ruta), toma el
     * permiso persistente, la guarda e invalida caché/índice. [song] `null` = alta genérica (Ajustes).
     */
    suspend fun addFolderForSong(uri: Uri, song: Song?): AddFolderResult = withContext(Dispatchers.IO) {
        if (song != null && pathOf(song.relativePath).isNotEmpty() && !coversSong(uri, song)) {
            Log.d(LYRICS_TAG, "carpeta elegida NO contiene la canción (rel='${song.relativePath}') -> rechazada")
            return@withContext AddFolderResult.WRONG_FOLDER
        }
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        cache.clear()
        indexMutex.withLock { indexedKey = null; lrcIndex = emptyList() }
        folderStore.add(uri)
        runCatching { ensureIndex(folderStore.folders.first()) } // pre-carga el índice
        AddFolderResult.ADDED
    }

    /** Revoca una carpeta (Ajustes). */
    suspend fun removeFolder(uri: Uri) = withContext(Dispatchers.IO) {
        runCatching { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        folderStore.remove(uri)
        cache.clear()
        indexMutex.withLock { indexedKey = null; lrcIndex = emptyList() }
    }

    private suspend fun loadFromLrc(song: Song): Lyrics? {
        val trees = folderStore.folders.first()
        if (trees.isEmpty()) return null
        val entry = matchLrc(song, ensureIndex(trees)) ?: return null
        val text = runCatching {
            resolver.openInputStream(entry.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull() ?: return null
        return LrcParser.parse(text)
    }

    // --- Cobertura de carpeta (comparación por ruta relativa, ignorando el volumen) ---

    private fun coversSong(tree: Uri, song: Song): Boolean {
        val treePath = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()?.let(::pathOf) ?: return false
        val songPath = pathOf(song.relativePath)
        if (treePath.isEmpty() || songPath.isEmpty()) return false
        return songPath.equals(treePath, ignoreCase = true) || songPath.startsWith("$treePath/", ignoreCase = true)
    }

    /** Ruta relativa sin volumen ("primary:Download/X" o "Download/X/" → "Download/X"). */
    private fun pathOf(docIdOrRelative: String): String =
        docIdOrRelative.substringAfter(':', docIdOrRelative).trim('/').trim()

    // --- Índice + matcher tolerante ---

    private suspend fun ensureIndex(trees: Set<Uri>): List<LrcEntry> = indexMutex.withLock {
        val key = trees.map { it.toString() }.sorted().joinToString("|")
        if (indexedKey == key) return lrcIndex
        val built = trees.flatMap { runCatching { buildLrcIndex(it) }.getOrDefault(emptyList()) }
        lrcIndex = built
        indexedKey = key
        built
    }

    /**
     * Resuelve el `.lrc` de forma tolerante y conservadora (mejor nada que cruzar pistas): exacto →
     * normalizado (sin acentos/espacios/sufijos de descargador) → prefijo (mayor solape, empate=
     * ninguno) → metadatos (artista Y título). Una etapa ambigua NO adivina.
     */
    private fun matchLrc(song: Song, entries: List<LrcEntry>): LrcEntry? {
        val audioBase = song.displayName.substringBeforeLast('.')
        val audioNorm = normalize(audioBase)
        Log.d(LYRICS_TAG, "buscar audio base='$audioBase' norm='$audioNorm' (.lrc en carpetas=${entries.size})")
        if (audioNorm.isEmpty() || entries.isEmpty()) {
            Log.d(LYRICS_TAG, "NO MATCH (base vacía o sin .lrc)")
            return null
        }
        entries.firstOrNull { it.base.equals(audioBase, ignoreCase = true) }
            ?.let { return matched("1-exacto", it) }
        entries.filter { it.normalized == audioNorm }.singleOrNull()
            ?.let { return matched("2-normalizado", it) }
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
            Log.d(LYRICS_TAG, "prefijo AMBIGUO (${best.size} con solape $max) -> sigo")
        }
        val artist = normalize(song.artist)
        val title = normalize(song.title)
        if (artist.isNotBlank() && title.isNotBlank()) {
            entries.filter { it.normalized.contains(artist) && it.normalized.contains(title) }.singleOrNull()
                ?.let { return matched("4-metadatos", it) }
        }
        val near = entries.filter { title.isNotBlank() && it.normalized.contains(title) }.take(8).map { it.base }
        Log.d(LYRICS_TAG, "NO MATCH para '$audioBase'. Candidatos con título: $near")
        return null
    }

    private fun matched(stage: String, e: LrcEntry): LrcEntry {
        Log.d(LYRICS_TAG, "MATCH [$stage] -> '${e.base}'")
        return e
    }

    private fun normalize(raw: String): String {
        var s = raw.lowercase(Locale.ROOT)
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        s = s.replace(Regex("(?:[_\\-\\s]*(?:private|lyrics?|karaoke)|\\s*\\(\\d+\\))+\\s*$"), "")
        return s.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

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
                            out += LrcEntry(base, normalize(base), DocumentsContract.buildDocumentUriUsingTree(tree, childId))
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
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    }
}
