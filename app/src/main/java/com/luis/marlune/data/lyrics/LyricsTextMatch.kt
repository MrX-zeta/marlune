package com.luis.marlune.data.lyrics

import java.text.Normalizer
import java.util.Locale

/**
 * Normaliza un título/artista para comparar IDENTIDAD de forma tolerante: minúsculas, sin acentos, sin
 * puntuación, espacios colapsados, y descartando sufijos de ruido ("lyrics", "karaoke", "(123)"…).
 *
 * Es la ÚNICA implementación: la usa tanto el emparejado de `.lrc` locales
 * ([com.luis.marlune.data.repository.LyricsRepository]) como la validación de candidatos de LRCLIB
 * ([LrcLibClient]). No escribir otra copia.
 */
internal fun normalizeForMatch(raw: String): String {
    var s = raw.lowercase(Locale.ROOT)
    s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    s = s.replace(Regex("(?:[_\\-\\s]*(?:private|lyrics?|karaoke)|\\s*\\(\\d+\\))+\\s*$"), "")
    return s.replace(Regex("[^a-z0-9]+"), " ").trim()
}
