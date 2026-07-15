package com.luis.marlune.ui.player

import com.luis.marlune.domain.model.LyricLine

/**
 * Estado de la vista de letras del reproductor. La UI solo lo lee; la lectura/parseo vive en la capa
 * de datos ([com.luis.marlune.data.repository.LyricsRepository]).
 */
sealed interface LyricsUiState {
    /** Resolviendo la letra de la pista actual (IO). */
    data object Loading : LyricsUiState

    /** No hay letra (caso común, no un error). [canPickFolder] = aún no se concedió carpeta SAF. */
    data class None(val canPickFolder: Boolean) : LyricsUiState

    /** Letra plana: texto scrolleable, sin resaltado ni auto-scroll. */
    data class Plain(val lines: List<String>) : LyricsUiState

    /** Letra sincronizada: [activeIndex] = línea activa según la posición (-1 = aún no empezó). */
    data class Synced(val lines: List<LyricLine>, val activeIndex: Int) : LyricsUiState
}
