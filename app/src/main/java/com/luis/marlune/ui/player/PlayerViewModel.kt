package com.luis.marlune.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luis.marlune.playback.PlaybackRepository
import com.luis.marlune.playback.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private const val LibrarySource = "Tu biblioteca"

/**
 * ViewModel del Reproductor: proyecta la reproducción REAL ([PlaybackRepository]) al estado de la
 * UI y traduce los [PlayerEvent] a comandos sobre el `MediaController`. Sin datos mock, sin ticker
 * propio (la posición viene del repositorio).
 *
 * La dirección de la transición de carátula se deriva aquí comparando el índice de la cola entre
 * emisiones (la ÚNICA fuente para la animación, venga de botón, swipe, notificación o auto-avance).
 * El "me gusta" es efímero de sesión hasta que exista persistencia (Room).
 */
class PlayerViewModel(private val playback: PlaybackRepository) : ViewModel() {

    private val likedIds = MutableStateFlow<Set<String>>(emptySet())

    // Solo para saber sobre qué pista opera "me gusta" (la dirección de animación viene del repo).
    private var lastMediaId: String? = null

    val uiState: StateFlow<PlayerUiState> =
        combine(playback.state, likedIds) { state, liked -> state.toUiState(liked) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState.Empty)

    fun onEvent(event: PlayerEvent) {
        when (event) {
            PlayerEvent.PlayPause -> playback.playPause()
            PlayerEvent.Next -> playback.next()
            PlayerEvent.Previous -> playback.previous()
            PlayerEvent.ToggleShuffle -> playback.toggleShuffle()
            PlayerEvent.ToggleRepeat -> playback.cycleRepeat()
            PlayerEvent.ToggleLike -> toggleLike()
            is PlayerEvent.SeekTo -> playback.seekTo(event.positionMs)
        }
    }

    private fun toggleLike() {
        val id = lastMediaId ?: return
        likedIds.update { if (id in it) it - id else it + id }
    }

    private fun PlaybackState.toUiState(liked: Set<String>): PlayerUiState {
        if (!hasItem) {
            lastMediaId = null
            return PlayerUiState.Empty
        }
        lastMediaId = mediaId
        return PlayerUiState(
            hasTrack = true,
            title = title,
            artist = artist,
            source = LibrarySource,
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            isShuffleOn = shuffle,
            repeatMode = repeatMode,
            isLiked = mediaId != null && mediaId in liked,
            artworkUri = artworkUri,
            // La dirección de la animación es la única fuente del repositorio (reason de Media3).
            trackTransition = TrackTransition(transitionId, transition),
        )
    }

    companion object {
        fun factory(playback: PlaybackRepository): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory { initializer { PlayerViewModel(playback) } }
    }
}
