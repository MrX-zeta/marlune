package com.luis.marlune.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.luis.marlune.data.datastore.LyricsFolderStore
import com.luis.marlune.data.lyrics.CandidatesResult
import com.luis.marlune.data.lyrics.LrcLibClient
import com.luis.marlune.data.lyrics.LrcLibLyrics
import com.luis.marlune.data.lyrics.LrcLibResult
import com.luis.marlune.data.lyrics.LrcParser
import com.luis.marlune.data.lyrics.LyricsCacheStats
import com.luis.marlune.data.lyrics.NetworkLyricsCache
import com.luis.marlune.data.lyrics.normalizeForMatch
import com.luis.marlune.domain.model.LyricLine
import com.luis.marlune.domain.model.Lyrics
import com.luis.marlune.domain.model.LyricsFolderRequest
import com.luis.marlune.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Resultado de conceder una carpeta en el contexto de una canción. */
enum class AddFolderResult { ADDED, WRONG_FOLDER }

/** Resultado de resolver la letra (local o red): encontrada, no encontrada, sin conexión, o error. */
sealed interface LyricsResolution {
    data class Found(val lyrics: Lyrics) : LyricsResolution
    data object NotFound : LyricsResolution
    data object NoConnection : LyricsResolution
    data object ServiceError : LyricsResolution
}

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
    private val lrcLibClient: LrcLibClient,
    private val networkCache: NetworkLyricsCache,
) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** Carpetas SAF concedidas (para re-resolver al conceder y para Ajustes). */
    val grantedFolders: Flow<Set<Uri>> = folderStore.folders

    // Se incrementa al BORRAR la caché, para que la UI RE-RESUELVA (si no, el StateFlow de letras
    // seguiría mostrando la letra descargada ya borrada hasta el siguiente cambio de pista).
    private val _cacheInvalidations = MutableStateFlow(0)
    val cacheInvalidations: StateFlow<Int> = _cacheInvalidations.asStateFlow()

    // Se incrementa en CUALQUIER cambio de la caché de descargas (guardar una nueva o borrar), para
    // que Ajustes actualice el contador/tamaño en tiempo real sin re-entrar a la pantalla.
    private val _cacheChanges = MutableStateFlow(0)
    val cacheChanges: StateFlow<Int> = _cacheChanges.asStateFlow()

    // Índice de .lrc de TODAS las carpetas concedidas, cacheado hasta que cambie el conjunto.
    private val indexMutex = Mutex()
    @Volatile private var indexedKey: String? = null
    @Volatile private var lrcIndex: List<LrcEntry> = emptyList()

    // Caché por canción (incluye "no hay letra" para no re-buscar en cada apertura).
    private val cache = ConcurrentHashMap<Long, CachedLyrics>()

    /** Letra LOCAL de [song] (o `null`). Cacheada; segura para llamar por cada cambio de pista. */
    suspend fun lyricsFor(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        cache[song.id]?.let {
            Log.d(LYRICS_TAG, "local cache-memoria HIT id=${song.id} -> ${it.value?.let { l -> "synced=${l.synced}" } ?: "sin letra"}")
            return@withContext it.value
        }
        val resolved = loadFromLrc(song)
        cache[song.id] = CachedLyrics(resolved)
        resolved
    }

    /**
     * Resuelve la letra priorizando por CALIDAD, no solo por fuente:
     *  1. Local SINCRONIZADA (.lrc con timestamps) → gana siempre, sin red.
     *  2. Caché de red SINCRONIZADA → dato local, se muestra con o sin conexión.
     *  3. Si lo local es PLANO (o no hay) y [allowNetwork] (el opt-in) está ON y aún no se consultó,
     *     se pide a LRCLIB una versión SINCRONIZADA (una .lrc plana descargada no debe bloquear la
     *     sincronizada que ofrece internet).
     *  4. Sin sincronizada por ningún lado: la mejor PLANA (local > caché de red) o vacío.
     *
     * Con `allowNetwork=false` jamás se sale a internet; lo cacheado (dato local) sigue apareciendo.
     * Solo se hace UNA petición por canción (mientras haya algo cacheado de red no se repite).
     */
    suspend fun resolve(song: Song, allowNetwork: Boolean): LyricsResolution = withContext(Dispatchers.IO) {
        Log.d(LYRICS_TAG, "resolve id=${song.id} allowNetwork=$allowNetwork title='${song.title}' artist='${song.artist}' dur=${song.durationMs / 1000}s")
        val local = lyricsFor(song)
        if (local != null && local.synced) return@withContext LyricsResolution.Found(local)

        // ELECCIÓN MANUAL del usuario: gana sobre la caché de red y la automática (solo la cede la
        // local sincronizada, que está cuadrada contra el archivo exacto). Es un dato local ya
        // descargado: se muestra con o sin conexión.
        val manual = networkCache.getManual(song.id)
        if (manual != null) {
            Log.d(LYRICS_TAG, "resolve id=${song.id} -> ELECCIÓN MANUAL (synced=${manual.synced})")
            return@withContext LyricsResolution.Found(manual)
        }

        val cached = networkCache.get(song.id)
        if (cached != null && cached.synced) return@withContext LyricsResolution.Found(cached)

        // Pedir a la red SOLO si el opt-in está ON y aún no se consultó (no hay nada cacheado de red).
        var netPlain: Lyrics? = null
        if (allowNetwork && cached == null) {
            Log.d(LYRICS_TAG, "resolve -> LRCLIB '${song.title}' / '${song.artist}' (local=${if (local == null) "ausente" else "plano"})")
            when (val r = lrcLibClient.fetch(song.title, song.artist, song.album, song.durationMs / 1000)) {
                is LrcLibResult.Found -> {
                    networkCache.put(song.id, r.lyrics.syncedLyrics, r.lyrics.plainLyrics)
                    _cacheChanges.value += 1
                    val net = toLyrics(r.lyrics)
                    Log.d(LYRICS_TAG, "DESFASE LRCLIB: audio=${song.durationMs / 1000}s entry=${r.lyrics.durationSec}s (diff=${song.durationMs / 1000 - r.lyrics.durationSec}s)")
                    logTiming("LRCLIB", song, net)
                    if (net != null && net.synced) return@withContext LyricsResolution.Found(net)
                    netPlain = net // la red solo trajo plano: sirve de respaldo abajo
                }
                LrcLibResult.NoConnection -> if (local == null) return@withContext LyricsResolution.NoConnection
                LrcLibResult.ServiceError -> if (local == null) return@withContext LyricsResolution.ServiceError
                LrcLibResult.NotFound -> Unit
            }
        }

        // Sin sincronizada: la mejor PLANA disponible (local > caché de red > red recién traída) o vacío.
        when {
            local != null -> LyricsResolution.Found(local)
            cached != null -> LyricsResolution.Found(cached)
            netPlain != null -> LyricsResolution.Found(netPlain)
            else -> LyricsResolution.NotFound
        }
    }

    /** Candidatos de LRCLIB para la elección MANUAL (lista sin validar; ver [LrcLibClient.searchCandidates]). */
    suspend fun candidatesFor(song: Song): CandidatesResult = withContext(Dispatchers.IO) {
        lrcLibClient.searchCandidates(song.title, song.artist, song.durationMs / 1000)
    }

    /** Id de LRCLIB de la elección manual activa de la canción (para marcarla en la lista), o `null`. */
    suspend fun activeManualId(song: Song): Long? = withContext(Dispatchers.IO) { networkCache.getManualId(song.id) }

    /**
     * Fija la letra elegida a mano: la trae por id (`/api/get/{id}`), la persiste como elección de la
     * canción y re-dispara la resolución. Devuelve `true` si se pudo traer y guardar.
     */
    suspend fun chooseManualLyrics(song: Song, candidateId: Long): Boolean = withContext(Dispatchers.IO) {
        when (val r = lrcLibClient.fetchById(candidateId)) {
            is LrcLibResult.Found -> {
                networkCache.putManual(song.id, candidateId, r.lyrics.syncedLyrics, r.lyrics.plainLyrics)
                _cacheInvalidations.value += 1
                _cacheChanges.value += 1
                true
            }
            else -> false
        }
    }

    /** Descarta la elección manual y vuelve a la resolución automática. */
    suspend fun clearManualChoice(song: Song) = withContext(Dispatchers.IO) {
        networkCache.clearManual(song.id)
        _cacheInvalidations.value += 1
        _cacheChanges.value += 1
    }

    /**
     * Borra TODAS las letras descargadas: disco privado + caché en memoria del repo, y RE-DISPARA la
     * resolución (vía [cacheInvalidations]) para que la UI deje de mostrar la letra ya borrada. Incluye
     * las ELECCIONES MANUALES (viven en la misma carpeta). NO toca las letras locales (.lrc/etiquetas).
     */
    suspend fun clearNetworkCache() = withContext(Dispatchers.IO) {
        Log.d(LYRICS_TAG, "borrar letras descargadas (disco + memoria) + re-resolver")
        networkCache.clear()
        cache.clear() // memoria del repo (se re-lee lo local; lo descargado desaparece)
        _cacheInvalidations.value += 1 // re-resolver la pista actual
        _cacheChanges.value += 1 // actualizar el contador de Ajustes
    }

    /** Cuántas letras descargadas hay y cuánto ocupan (para Ajustes). */
    suspend fun lyricsCacheStats(): LyricsCacheStats = withContext(Dispatchers.IO) { networkCache.stats() }

    private fun toLyrics(l: LrcLibLyrics): Lyrics? {
        l.syncedLyrics?.let { s ->
            val parsed = LrcParser.parse(s)
            Log.d(LYRICS_TAG, "parse(syncedLyrics ${s.length} chars) -> lineas=${parsed?.lines?.size ?: 0} synced=${parsed?.synced}")
            if (parsed != null) return parsed
        }
        l.plainLyrics?.let { p ->
            val lines = p.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { LyricLine(null, it) }.toList()
            Log.d(LYRICS_TAG, "sin syncedLyrics utilizable -> PLANO (${lines.size} lineas)")
            if (lines.isNotEmpty()) return Lyrics(lines, synced = false)
        }
        return null
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
        if (trees.isEmpty()) {
            Log.d(LYRICS_TAG, "local: sin carpetas SAF concedidas")
            return null
        }
        val entry = matchLrc(song, ensureIndex(trees)) ?: return null
        val text = runCatching {
            resolver.openInputStream(entry.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        if (text == null) {
            Log.d(LYRICS_TAG, "local: no se pudo leer '${entry.base}'")
            return null
        }
        val parsed = LrcParser.parse(text)
        val head = text.take(160).replace("\n", "\\n").replace("\r", "\\r")
        Log.d(LYRICS_TAG, "local '${entry.base}' -> lineas=${parsed?.lines?.size ?: 0} synced=${parsed?.synced} head=[$head]")
        logTiming("local", song, parsed)
        return parsed
    }

    /** [DEBUG] Para verificar el desfase: duración del audio y cuándo arranca la 1ª línea vocal. */
    private fun logTiming(source: String, song: Song, lyrics: Lyrics?) {
        val firstVocal = lyrics?.lines?.firstOrNull { it.timeMs != null && it.text.isNotBlank() }?.timeMs
        Log.d(LYRICS_TAG, "TIMING [$source] audio=${song.durationMs / 1000}s primeraVocal=${firstVocal ?: -1}ms synced=${lyrics?.synced}")
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

    // Delegado a la implementación ÚNICA compartida con LrcLibClient (ver LyricsTextMatch).
    private fun normalize(raw: String): String = normalizeForMatch(raw)

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
