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
 * Estrategia: `/get` (coincidencia exacta por título+artista+álbum+duración) y, si no hay, `/search`
 * (más tolerante) eligiendo el candidato con letra y duración más cercana. Todo bloqueante → llamar
 * desde `Dispatchers.IO`.
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
        val getUrl = "$BASE/get?" + query(
            "track_name" to title,
            "artist_name" to artist,
            "album_name" to album,
            "duration" to durationSec.takeIf { it > 0 }?.toString().orEmpty(),
        )
        val direct = request(getUrl) { parseObject(it) }
        Log.d(TAG, "  /get -> ${describe(direct)}")
        when (direct) {
            is LrcLibResult.Found -> return direct
            LrcLibResult.NoConnection -> return LrcLibResult.NoConnection
            LrcLibResult.ServiceError -> return LrcLibResult.ServiceError
            LrcLibResult.NotFound -> Unit // sigue al search
        }

        val searchUrl = "$BASE/search?" + query("track_name" to title, "artist_name" to artist)
        val search = request(searchUrl) { body -> parseSearch(body, durationSec) }
        Log.d(TAG, "  /search -> ${describe(search)}")
        return search
    }

    private fun describe(r: LrcLibResult): String = when (r) {
        is LrcLibResult.Found -> "Found(synced=${r.lyrics.syncedLyrics != null}, plain=${r.lyrics.plainLyrics != null})"
        LrcLibResult.NotFound -> "NotFound"
        LrcLibResult.NoConnection -> "NoConnection"
        LrcLibResult.ServiceError -> "ServiceError"
    }

    /**
     * Ejecuta la petición con UN reintento ante fallo transitorio de conexión: la 1ª petición tras
     * activar el ajuste fallaba ("sin conexión") por DNS/handshake en frío y la 2ª funcionaba. Solo se
     * reintenta [LrcLibResult.NoConnection] (fallo de conexión); éxito, 404 o error del servicio no.
     */
    private fun request(url: String, parse: (String) -> LrcLibResult): LrcLibResult {
        var result: LrcLibResult = LrcLibResult.NoConnection
        repeat(MAX_ATTEMPTS) { attempt ->
            result = requestOnce(url, parse)
            if (result !is LrcLibResult.NoConnection) return result
            if (attempt < MAX_ATTEMPTS - 1) {
                Log.d(TAG, "  reintento tras fallo transitorio de conexión")
                runCatching { Thread.sleep(RETRY_DELAY_MS) }
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

    private fun parseObject(body: String): LrcLibResult {
        val obj = runCatching { JSONObject(body) }.getOrNull() ?: return LrcLibResult.NotFound
        return obj.toLyrics()?.let { LrcLibResult.Found(it) } ?: LrcLibResult.NotFound
    }

    private fun parseSearch(body: String, durationSec: Long): LrcLibResult {
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return LrcLibResult.NotFound
        // Prioriza SIEMPRE las entradas con letra SINCRONIZADA; entre iguales, la duración más cercana.
        var bestSynced: LrcLibLyrics? = null
        var bestSyncedScore = Long.MAX_VALUE
        var bestPlain: LrcLibLyrics? = null
        var bestPlainScore = Long.MAX_VALUE
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val lyrics = item.toLyrics() ?: continue
            val dur = item.optLong("duration", -1)
            val score = if (dur > 0 && durationSec > 0) abs(dur - durationSec) else Long.MAX_VALUE - 1
            if (lyrics.syncedLyrics != null) {
                if (score < bestSyncedScore) { bestSynced = lyrics; bestSyncedScore = score }
            } else {
                if (score < bestPlainScore) { bestPlain = lyrics; bestPlainScore = score }
            }
        }
        return (bestSynced ?: bestPlain)?.let { LrcLibResult.Found(it) } ?: LrcLibResult.NotFound
    }

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
        const val MAX_ATTEMPTS = 2 // 1 intento + 1 reintento ante fallo transitorio de conexión
        const val RETRY_DELAY_MS = 500L
        const val USER_AGENT = "Marlune/1.0 (reproductor de musica local; https://github.com/luis/marlune)"
        const val TAG = "MarluneLyrics"
    }
}
