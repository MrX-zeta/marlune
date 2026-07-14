package com.luis.marlune.ui.player

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.luis.marlune.domain.model.RepeatMode
import com.luis.marlune.playback.TrackChange

/**
 * Señal del ÚLTIMO cambio de pista, leída del player. [id] incrementa en cada cambio (para
 * detectarlo una sola vez) y [kind] dice si fue siguiente, anterior o una CARGA DIRECTA (cola nueva
 * por selección). Es la ÚNICA fuente de la que deriva la animación de carátula: DIRECT no desliza
 * (crossfade/aparición); NEXT/PREVIOUS sí, en su sentido. Deriva del `reason` de Media3.
 */
@Immutable
data class TrackTransition(val id: Int = 0, val kind: TrackChange = TrackChange.DIRECT)

/**
 * Estado inmutable de la pantalla del Reproductor, derivado de la reproducción REAL
 * (PlaybackRepository/MediaController). Sin datos mock.
 *
 * `hasTrack = false` es el estado VACÍO (nada en la cola): la UI muestra un mensaje, no una pista
 * falsa. `artworkUri` es el content URI de la carátula (Coil la carga perezosamente); `source` es
 * el origen LOCAL de la cola bajo "REPRODUCIENDO DESDE".
 */
@Immutable
data class PlayerUiState(
    val hasTrack: Boolean,
    val title: String,
    val artist: String,
    val source: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val isShuffleOn: Boolean,
    val repeatMode: RepeatMode,
    val isLiked: Boolean,
    val artworkUri: Uri? = null,
    val trackTransition: TrackTransition = TrackTransition(),
) {
    /** Avance en [0, 1] para la marea. */
    val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    companion object {
        /** Estado vacío: nada sonando (app recién abierta o cola terminada). */
        val Empty = PlayerUiState(
            hasTrack = false,
            title = "",
            artist = "",
            source = "",
            positionMs = 0L,
            durationMs = 0L,
            isPlaying = false,
            isShuffleOn = false,
            repeatMode = RepeatMode.OFF,
            isLiked = false,
            artworkUri = null,
        )

        /** Estado de ejemplo para previews (biblioteca local, sin red). */
        val Preview = PlayerUiState(
            hasTrack = true,
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

    /** Salto a una posición (ms), aplicado sobre el MediaController real. */
    data class SeekTo(val positionMs: Long) : PlayerEvent
}
