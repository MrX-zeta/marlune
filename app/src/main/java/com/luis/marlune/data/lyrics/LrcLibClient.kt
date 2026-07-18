package com.luis.marlune.data.lyrics

import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/** Resultado crudo de LRCLIB: LRC sincronizado y/o texto plano, y la duración de la entrada (s). */
data class LrcLibLyrics(val syncedLyrics: String?, val plainLyrics: String?, val durationSec: Long = -1)

/**
 * Estado de la búsqueda en red. Diferencia "no encontrada" de fallos, y dentro de los fallos separa
 * "sin conexión" (no hay red) de "error del servicio" (la petición respondió mal o falló por otra
 * causa), para poder mostrar el mensaje correcto.
 */
sealed interface LrcLibResult {
    data class Found(val lyrics: LrcLibLyrics) : LrcLibResult
    data object NotFound : LrcLibResult
    data object NoConnection : LrcLibResult
    data object ServiceError : LrcLibResult
}

/**
 * Cliente de [LRCLIB](https://lrclib.net/api): gratuito, comunitario, sin API key, con letras
 * sincronizadas. Usa `HttpURLConnection` (sin dependencias nuevas) con timeout corto y un `User-Agent`
 * que identifica la app (su etiqueta de uso). SOLO se invoca si el ajuste opt-in está encendido.
 *
 * Estrategia (una sola petición en el caso normal):
 *  1) `/search` (track+artist): el ÚNICO endpoint que da CANDIDATOS, que es lo que `parseSearch`
 *     necesita para validar identidad y duración.
 *  2) `/get` (con duración completa, para no provocar el 400) SOLO si `/search` no dio ninguna letra.
 *  3) Si nada trae synced, se devuelve el mejor PLANO acumulado; si tampoco hay, NotFound.
 * Se quitó `/get-cached`: devolvía 404 casi siempre para estas entradas (una petición inútil por
 * canción, justo la que subía a 2-3 y disparaba el 503). Todo bloqueante → llamar desde `Dispatchers.IO`.
 */
class LrcLibClient {

    fun fetch(
        title: String,
        artist: String,
        album: String,
        durationSec: Long,
    ): LrcLibResult {
        if (title.isBlank() || artist.isBlank()) return LrcLibResult.NotFound

        Log.d(TAG, "LRCLIB enviado: track='$title' artist='$artist' album='$album' dur=$durationSec")

        // Mejor PLANO acumulado (por si al final no aparece ninguna synced) y rastro de errores: solo
        // devolvemos NoConnection/ServiceError si NINGÚN paso logró letra.
        var bestPlain: LrcLibLyrics? = null
        var bestPlainScore = Long.MAX_VALUE
        var sawNoConnection = false
        var sawServiceError = false

        fun considerPlain(lyrics: LrcLibLyrics) {
            val score = if (lyrics.durationSec > 0 && durationSec > 0) {
                abs(lyrics.durationSec - durationSec)
            } else {
                Long.MAX_VALUE - 1
            }
            if (score < bestPlainScore) { bestPlain = lyrics; bestPlainScore = score }
        }

        // Procesa el resultado de un paso: devuelve la synced si la trae (fin), o acumula plano/errores.
        fun step(endpoint: String, result: LrcLibResult): LrcLibResult? {
            Log.d(TAG, "  /$endpoint -> ${describe(result)}")
            when (result) {
                is LrcLibResult.Found -> {
                    val ly = result.lyrics
                    if (ly.syncedLyrics != null) {
                        Log.d(TAG, "  RESUELTO por /$endpoint (synced)")
                        return result
                    }
                    if (ly.plainLyrics != null) considerPlain(ly)
                }
                LrcLibResult.NoConnection -> sawNoConnection = true
                LrcLibResult.ServiceError -> sawServiceError = true
                LrcLibResult.NotFound -> Unit
            }
            return null
        }

        // 1) search: el único que da CANDIDATOS; parseSearch valida identidad + duración.
        val searchUrl = "$BASE/search?" + query("track_name" to title, "artist_name" to artist)
        step("search", request(searchUrl) { parseSearch(it, title, artist, durationSec) })?.let { return it }

        // 2) get: último recurso, solo si /search no dio NINGUNA letra. Con duración completa (evita el 400).
        if (bestPlain == null) {
            val getUrl = "$BASE/get?" + query(
                "track_name" to title,
                "artist_name" to artist,
                "album_name" to album,
                "duration" to durationSec.takeIf { it > 0 }?.toString().orEmpty(),
            )
            step("get", request(getUrl) { parseGet(it, title, artist, durationSec) })?.let { return it }
        }

        // 4) Sin synced: mejor plano acumulado; si no hay, el error correspondiente o NotFound.
        bestPlain?.let {
            Log.d(TAG, "  RESUELTO con PLANO acumulado (sin synced disponible)")
            return LrcLibResult.Found(it)
        }
        val fallback = when {
            sawNoConnection -> LrcLibResult.NoConnection
            sawServiceError -> LrcLibResult.ServiceError
            else -> LrcLibResult.NotFound
        }
        Log.d(TAG, "  sin letra -> ${describe(fallback)}")
        return fallback
    }

    private fun describe(r: LrcLibResult): String = when (r) {
        is LrcLibResult.Found -> "Found(synced=${r.lyrics.syncedLyrics != null}, plain=${r.lyrics.plainLyrics != null})"
        LrcLibResult.NotFound -> "NotFound"
        LrcLibResult.NoConnection -> "NoConnection"
        LrcLibResult.ServiceError -> "ServiceError"
    }

    /**
     * Ejecuta la petición con UN reintento ante fallo TRANSITORIO: conexión en frío
     * ([LrcLibResult.NoConnection], DNS/handshake) y error del servicio ([LrcLibResult.ServiceError],
     * típicamente un 503 de rate-limit). Éxito y 404 no se reintentan. Para el 503 el backoff es mayor
     * (~1 s): es límite de tasa, no un fallo de red; reintentar de inmediato lo vuelve a pegar.
     */
    private fun request(url: String, parse: (String) -> LrcLibResult): LrcLibResult {
        var result: LrcLibResult = LrcLibResult.NoConnection
        repeat(MAX_ATTEMPTS) { attempt ->
            result = requestOnce(url, parse)
            val transient = result is LrcLibResult.NoConnection || result is LrcLibResult.ServiceError
            if (!transient) return result
            if (attempt < MAX_ATTEMPTS - 1) {
                val backoff = if (result is LrcLibResult.ServiceError) SERVICE_RETRY_DELAY_MS else RETRY_DELAY_MS
                Log.d(TAG, "  reintento tras fallo transitorio (${describe(result)}) en ${backoff}ms")
                runCatching { Thread.sleep(backoff) }
            }
        }
        return result
    }

    private fun requestOnce(url: String, parse: (String) -> LrcLibResult): LrcLibResult {
        var conn: HttpURLConnection? = null
        val start = System.currentTimeMillis()
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            val code = conn.responseCode
            Log.d(TAG, "  HTTP $code (${System.currentTimeMillis() - start}ms) $url")
            when {
                code in 200..299 -> parse(conn.inputStream.bufferedReader().use { it.readText() })
                else -> {
                    // Drena el cuerpo de error para devolver la conexión al POOL (keep-alive): la
                    // siguiente petición (p. ej. /search tras un /get 404) REUTILIZA el TLS ya negociado
                    // en vez de reconectar (~1-2 s menos), y también acelera las letras siguientes.
                    runCatching { conn.errorStream?.use { it.readBytes() } }
                    if (code >= 500) LrcLibResult.ServiceError else LrcLibResult.NotFound
                }
            }
        } catch (e: java.util.concurrent.CancellationException) {
            conn?.disconnect()
            throw e // NO tragarse la cancelación de corrutina (rompería la estructura de concurrencia)
        } catch (e: Exception) {
            val noConn = e is java.net.UnknownHostException ||
                e is java.net.ConnectException ||
                e is java.net.SocketTimeoutException ||
                e is java.net.NoRouteToHostException
            // [DIAG] stack trace completo + a qué estado se mapea, para distinguir sin-red de otro fallo.
            Log.w(TAG, "  EXCEPCIÓN ${e.javaClass.name}: ${e.message} -> ${if (noConn) "NoConnection" else "ServiceError"}", e)
            conn?.disconnect() // ante fallo SÍ cerramos (la conexión pudo quedar en mal estado)
            if (noConn) LrcLibResult.NoConnection else LrcLibResult.ServiceError
        }
        // Camino normal (2xx/404/5xx): NO se llama a disconnect() → la conexión vuelve al pool y se
        // reutiliza (keep-alive), evitando un handshake TLS por petición.
    }

    /**
     * Valida la respuesta EXACTA de `/get`: identidad (track+artist normalizados) y, para SINCRONIZADA,
     * duración cercana (±[DURATION_TOLERANCE_SEC]). Si la synced no cuadra en duración, cae a plano; sin
     * plano válido, NotFound. Nunca devuelve una synced que no case en duración.
     */
    private fun parseGet(body: String, title: String, artist: String, durationSec: Long): LrcLibResult {
        val obj = runCatching { JSONObject(body) }.getOrNull() ?: return LrcLibResult.NotFound
        val lyrics = obj.toLyrics() ?: return LrcLibResult.NotFound
        if (!identityMatches(obj, title, artist)) {
            Log.d(TAG, "  /get descartado (identidad): '${obj.optString("trackName")}' — '${obj.optString("artistName")}'")
            return LrcLibResult.NotFound
        }
        val dur = obj.optLong("duration", -1)
        val durClose = dur > 0 && durationSec > 0 && abs(dur - durationSec) <= DURATION_TOLERANCE_SEC
        return when {
            durClose && lyrics.syncedLyrics != null -> LrcLibResult.Found(lyrics)
            lyrics.plainLyrics != null -> LrcLibResult.Found(LrcLibLyrics(null, lyrics.plainLyrics, lyrics.durationSec))
            else -> LrcLibResult.NotFound
        }
    }

    /**
     * Elige el candidato de `/search` con validación ESTRICTA:
     *  - IDENTIDAD (obligatoria): track+artist normalizados coinciden (descarta versiones/idiomas ajenos).
     *  - SINCRONIZADA: además, duración a ±[DURATION_TOLERANCE_SEC]; entre las válidas, la más cercana.
     *  - Si ninguna synced válida: la PLANA de un candidato con identidad (no depende del timing).
     *  - Nunca una synced que no cuadre en duración (mejor plana honesta que synced corrida).
     */
    private fun parseSearch(body: String, title: String, artist: String, durationSec: Long): LrcLibResult {
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return LrcLibResult.NotFound
        val total = arr.length()
        var identityOk = 0
        var syncedValid = 0
        var bestSynced: LrcLibLyrics? = null
        var bestSyncedScore = Long.MAX_VALUE
        var bestPlain: LrcLibLyrics? = null
        var bestPlainScore = Long.MAX_VALUE
        for (i in 0 until total) {
            val item = arr.optJSONObject(i) ?: continue
            val lyrics = item.toLyrics() ?: continue // salta instrumentales / vacíos
            if (!identityMatches(item, title, artist)) {
                Log.d(TAG, "  descartado (identidad): '${item.optString("trackName")}' — '${item.optString("artistName")}'")
                continue
            }
            identityOk++
            val dur = item.optLong("duration", -1)
            val durClose = dur > 0 && durationSec > 0 && abs(dur - durationSec) <= DURATION_TOLERANCE_SEC
            if (lyrics.syncedLyrics != null) {
                if (durClose) {
                    syncedValid++
                    val score = abs(dur - durationSec)
                    if (score < bestSyncedScore) { bestSynced = lyrics; bestSyncedScore = score }
                } else {
                    Log.d(TAG, "  synced descartada (duración ${dur}s vs ${durationSec}s): '${item.optString("trackName")}'")
                }
            }
            // Plano: vale por identidad (el timing no importa). Se guarda SIN synced para no arrastrar
            // una sincronizada descartada. La duración solo ordena entre planos.
            if (lyrics.plainLyrics != null) {
                val score = if (dur > 0 && durationSec > 0) abs(dur - durationSec) else Long.MAX_VALUE - 1
                if (score < bestPlainScore) {
                    bestPlain = LrcLibLyrics(null, lyrics.plainLyrics, lyrics.durationSec)
                    bestPlainScore = score
                }
            }
        }
        Log.d(TAG, "  search: $total candidatos, $identityOk con identidad, $syncedValid synced válidas")
        return (bestSynced ?: bestPlain)?.let { LrcLibResult.Found(it) } ?: LrcLibResult.NotFound
    }

    /** Identidad tolerante: track y artist normalizados (misma implementación que los `.lrc` locales). */
    private fun identityMatches(item: JSONObject, title: String, artist: String): Boolean =
        normalizeForMatch(item.optString("trackName")) == normalizeForMatch(title) &&
            normalizeForMatch(item.optString("artistName")) == normalizeForMatch(artist)

    private fun JSONObject.toLyrics(): LrcLibLyrics? {
        if (optBoolean("instrumental", false)) return null
        val synced = optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        return if (synced != null || plain != null) {
            LrcLibLyrics(synced, plain, optLong("duration", -1))
        } else {
            null
        }
    }

    private fun query(vararg params: Pair<String, String>): String =
        params.filter { it.second.isNotBlank() }
            .joinToString("&") { (k, v) -> "$k=${Uri.encode(v)}" }

    private companion object {
        const val BASE = "https://lrclib.net/api"
        const val TIMEOUT_MS = 15_000 // red lenta/cargada: 6 s se quedaba corto y daba "sin conexión"
        const val MAX_ATTEMPTS = 2 // 1 intento + 1 reintento ante fallo transitorio
        const val RETRY_DELAY_MS = 500L // backoff para fallo de conexión en frío
        const val SERVICE_RETRY_DELAY_MS = 1_000L // backoff mayor para 503 (rate-limit)
        const val DURATION_TOLERANCE_SEC = 3L // margen para aceptar una synced como la misma grabación
        const val USER_AGENT = "Marlune/1.0 (reproductor de musica local; https://github.com/luis/marlune)"
        const val TAG = "MarluneLyrics"
    }
}
