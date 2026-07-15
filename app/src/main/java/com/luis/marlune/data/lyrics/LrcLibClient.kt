package com.luis.marlune.data.lyrics

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/** Resultado crudo de LRCLIB: LRC sincronizado y/o texto plano (cualquiera puede faltar). */
data class LrcLibLyrics(val syncedLyrics: String?, val plainLyrics: String?)

/** Estado de la búsqueda en red, para distinguir "no encontrada" de "sin conexión/error". */
sealed interface LrcLibResult {
    data class Found(val lyrics: LrcLibLyrics) : LrcLibResult
    data object NotFound : LrcLibResult
    data object NetworkError : LrcLibResult
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

        val getUrl = "$BASE/get?" + query(
            "track_name" to title,
            "artist_name" to artist,
            "album_name" to album,
            "duration" to durationSec.takeIf { it > 0 }?.toString().orEmpty(),
        )
        when (val direct = request(getUrl) { parseObject(it) }) {
            is LrcLibResult.Found -> return direct
            LrcLibResult.NetworkError -> return LrcLibResult.NetworkError
            LrcLibResult.NotFound -> Unit // sigue al search
        }

        val searchUrl = "$BASE/search?" + query("track_name" to title, "artist_name" to artist)
        return request(searchUrl) { body -> parseSearch(body, durationSec) }
    }

    /** Ejecuta la petición y mapea el cuerpo con [parse]; distingue 404 (NotFound) de error de red. */
    private fun request(url: String, parse: (String) -> LrcLibResult): LrcLibResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            when (val code = conn.responseCode) {
                in 200..299 -> parse(conn.inputStream.bufferedReader().use { it.readText() })
                404 -> LrcLibResult.NotFound
                else -> if (code >= 500) LrcLibResult.NetworkError else LrcLibResult.NotFound
            }
        } catch (_: Exception) {
            LrcLibResult.NetworkError // timeout / sin conexión / host no resuelto
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseObject(body: String): LrcLibResult {
        val obj = runCatching { JSONObject(body) }.getOrNull() ?: return LrcLibResult.NotFound
        return obj.toLyrics()?.let { LrcLibResult.Found(it) } ?: LrcLibResult.NotFound
    }

    private fun parseSearch(body: String, durationSec: Long): LrcLibResult {
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return LrcLibResult.NotFound
        var best: LrcLibLyrics? = null
        var bestScore = Long.MAX_VALUE
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val lyrics = item.toLyrics() ?: continue
            val dur = item.optLong("duration", -1)
            val score = if (dur > 0 && durationSec > 0) abs(dur - durationSec) else Long.MAX_VALUE - 1
            // Prefiere el más cercano en duración; si no hay duración, cualquiera con letra sirve.
            if (score < bestScore) { best = lyrics; bestScore = score }
        }
        return best?.let { LrcLibResult.Found(it) } ?: LrcLibResult.NotFound
    }

    private fun JSONObject.toLyrics(): LrcLibLyrics? {
        if (optBoolean("instrumental", false)) return null
        val synced = optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        return if (synced != null || plain != null) LrcLibLyrics(synced, plain) else null
    }

    private fun query(vararg params: Pair<String, String>): String =
        params.filter { it.second.isNotBlank() }
            .joinToString("&") { (k, v) -> "$k=${Uri.encode(v)}" }

    private companion object {
        const val BASE = "https://lrclib.net/api"
        const val TIMEOUT_MS = 6_000
        const val USER_AGENT = "Marlune/1.0 (reproductor de musica local; https://github.com/luis/marlune)"
    }
}
