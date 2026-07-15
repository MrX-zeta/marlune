package com.luis.marlune.ui.player

import androidx.compose.runtime.Immutable
import com.luis.marlune.playback.QueueItem

/**
 * Estado de la cola ("A continuación") para la UI: los items reales del `MediaController`, el índice
 * de la pista actual (para resaltarla y separar lo ya reproducido de lo que viene) y si está sonando
 * (para el ecualizador). No incluye la posición, así los ticks no lo re-emiten.
 */
@Immutable
data class QueueUiState(
    val items: List<QueueItem>,
    val currentIndex: Int,
    val isPlaying: Boolean,
) {
    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        val Empty = QueueUiState(items = emptyList(), currentIndex = 0, isPlaying = false)
    }
}
