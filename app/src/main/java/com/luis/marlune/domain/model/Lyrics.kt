package com.luis.marlune.domain.model

/**
 * Una línea de letra. [timeMs] no nulo = línea con marca de tiempo (modo SINCRONIZADO); nulo = línea
 * de texto plano.
 */
data class LyricLine(val timeMs: Long?, val text: String)

/**
 * Letra resuelta de una canción, 100% local (.lrc por SAF o tag embebido). [synced] indica si las
 * líneas llevan marca de tiempo (resaltado + auto-scroll) o es texto plano scrolleable. [lines] no
 * está vacía cuando hay letra; la ausencia de letra se representa con `null` aguas arriba.
 */
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
)
