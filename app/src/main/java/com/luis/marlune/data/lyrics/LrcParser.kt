package com.luis.marlune.data.lyrics

import com.luis.marlune.domain.model.LyricLine
import com.luis.marlune.domain.model.Lyrics

/**
 * Parser de letras en formato `.lrc` (100% local, sin red). Soporta:
 *  - Líneas con marca(s) de tiempo `[mm:ss.xx]` (una o varias por línea) → letra SINCRONIZADA.
 *  - Etiqueta `[offset:±ms]` que desplaza todas las marcas.
 *  - Tags de metadatos (`[ti:]`, `[ar:]`, `[al:]`, `[by:]`, …) que se ignoran.
 *  - Un `.lrc` sin ninguna marca de tiempo → letra PLANA (texto scrolleable).
 *
 * Puro y sin dependencias de Android para poder testearlo fácil.
 */
object LrcParser {

    // [mm:ss], [mm:ss.xx] o [mm:ss.xxx] (fracción con '.' o ':'); tolera espacios dentro del corchete.
    private val TIME = Regex("""\[\s*(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\s*]""")
    private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)
    // Tag de metadatos: corchete que empieza por letras y contiene ':' (p. ej. [ar:Artista]).
    private val META = Regex("""^\[[a-zA-Z]+:.*]$""")

    /**
     * Tolerante (mismo parser para `.lrc` local y para `syncedLyrics` de LRCLIB):
     *  - Cero o más espacios entre el timestamp y el texto (`[..]Texto` y `[..] Texto`).
     *  - Variantes de precisión [mm:ss], [mm:ss.xx], [mm:ss.xxx].
     *  - Varios timestamps por línea (misma letra en varios momentos).
     *  - Líneas con timestamp y texto vacío se CONSERVAN como línea vacía, sin abortar el resto.
     *  - Metadatos ([ar:], [ti:], [al:], [by:]) ignorados; [offset:] aplicado a los tiempos.
     *  - Con AL MENOS una línea con timestamp → SINCRONIZADA; solo cae a plano si no hay ninguna.
     */
    fun parse(content: String): Lyrics? {
        if (content.isBlank()) return null

        var offsetMs = 0L
        val synced = ArrayList<LyricLine>()
        val plain = ArrayList<String>()

        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach

            OFFSET.find(line)?.let {
                offsetMs = it.groupValues[1].toLongOrNull() ?: 0L
                return@forEach
            }

            val times = TIME.findAll(line).toList()
            when {
                times.isNotEmpty() -> {
                    // Texto = lo que va tras el ÚLTIMO timestamp, recortado (puede quedar vacío).
                    val text = line.substring(times.last().range.last + 1).trim()
                    times.forEach { synced += LyricLine(timeMs = it.toMillis(), text = text) }
                }
                META.matches(line) -> Unit // metadatos: se ignoran sin romper
                else -> plain += line
            }
        }

        return when {
            synced.isNotEmpty() -> Lyrics(
                lines = synced
                    .map { it.copy(timeMs = ((it.timeMs ?: 0L) + offsetMs).coerceAtLeast(0L)) }
                    .sortedBy { it.timeMs },
                synced = true,
            )
            plain.isNotEmpty() -> Lyrics(lines = plain.map { LyricLine(null, it) }, synced = false)
            else -> null
        }
    }

    private fun MatchResult.toMillis(): Long {
        val min = groupValues[1].toLong()
        val sec = groupValues[2].toLong()
        val frac = groupValues[3]
        val fracMs = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100 // décimas
            2 -> frac.toLong() * 10 // centésimas
            else -> frac.take(3).toLong() // milésimas
        }
        return min * 60_000 + sec * 1_000 + fracMs
    }
}
