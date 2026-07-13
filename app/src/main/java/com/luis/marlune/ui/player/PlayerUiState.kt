package com.luis.marlune.ui.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.luis.marlune.domain.model.RepeatMode

/**
 * Señal del ÚLTIMO cambio de pista, leída del player. [id] incrementa en cada cambio (para
 * detectarlo una sola vez) y [forward] indica si fue "siguiente" (true) o "anterior" (false).
 * Es la ÚNICA fuente de la que deriva la dirección de la transición de carátula, sin importar
 * quién la disparó (swipe, botón, notificación, auto-avance).
 */
@Immutable
data class TrackTransition(val id: Int = 0, val forward: Boolean = true)

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
    val trackTransition: TrackTransition = TrackTransition(),
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

    /** Salto a una posición (ms). El seekTo real irá por el MediaController/PlaybackRepository. */
    data class SeekTo(val positionMs: Long) : PlayerEvent
}
