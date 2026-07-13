package com.luis.marlune.ui.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.luis.marlune.domain.model.RepeatMode

/**
 * Estado inmutable de la pantalla del Reproductor.
 *
 * `source` es el origen LOCAL de la cola (p. ej. "Tu biblioteca", una lista o un álbum);
 * se muestra bajo la etiqueta "REPRODUCIENDO DESDE". `artwork` es el bitmap de la carátula
 * usado para derivar el acento dinámico; `null` mientras no hay carátula.
 */
@Immutable
data class PlayerUiState(
    val title: String,
    val artist: String,
    val source: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val isShuffleOn: Boolean,
    val repeatMode: RepeatMode,
    val isLiked: Boolean,
    val artwork: ImageBitmap? = null,
) {
    /** Avance en [0, 1] para la marea. */
    val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    companion object {
        /** Estado de ejemplo para previews y arranque (biblioteca local, sin red). */
        val Preview = PlayerUiState(
            title = "Marea nocturna",
            artist = "Lún",
            source = "Tu biblioteca",
            positionMs = 82_000L,
            durationMs = 214_000L,
            isPlaying = true,
            isShuffleOn = false,
            repeatMode = RepeatMode.OFF,
            isLiked = false,
        )
    }
}

/** Acciones del usuario que mutan el estado del reproductor. */
sealed interface PlayerEvent {
    data object PlayPause : PlayerEvent
    data object Next : PlayerEvent
    data object Previous : PlayerEvent
    data object ToggleShuffle : PlayerEvent
    data object ToggleRepeat : PlayerEvent
    data object ToggleLike : PlayerEvent
}
