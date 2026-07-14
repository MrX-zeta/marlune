package com.luis.marlune.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luis.marlune.data.repository.FavoritesRepository
import com.luis.marlune.playback.PlaybackRepository
import com.luis.marlune.playback.PlaybackState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val LibrarySource = "Tu biblioteca"

/**
 * ViewModel del Reproductor: proyecta la reproducción REAL ([PlaybackRepository]) al estado de la
 * UI y traduce los [PlayerEvent] a comandos sobre el `MediaController`. Sin datos mock, sin ticker
 * propio (la posición viene del repositorio).
 *
 * La dirección de la transición de carátula la aporta el repositorio (reason de Media3). El "me
 * gusta" es PERSISTENTE (Room, [FavoritesRepository]): el corazón refleja el estado real de la pista
 * actual y lo alterna guardándolo.
 */
class PlayerViewModel(
    private val playback: PlaybackRepository,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    // Id de la pista actual, para alternar su favorito.
    private var currentSongId: Long? = null

    val uiState: StateFlow<PlayerUiState> =
        combine(playback.state, favorites.favoriteIds) { state, favIds -> state.toUiState(favIds) }
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
        val id = currentSongId ?: return
        viewModelScope.launch { favorites.toggle(id) }
    }

    private fun PlaybackState.toUiState(favoriteIds: Set<Long>): PlayerUiState {
        if (!hasItem) {
            currentSongId = null
            return PlayerUiState.Empty
        }
        val songId = mediaId?.toLongOrNull()
        currentSongId = songId
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
            isLiked = songId != null && songId in favoriteIds,
            artworkUri = artworkUri,
            // La dirección de la animación es la única fuente del repositorio (reason de Media3).
            trackTransition = TrackTransition(transitionId, transition),
        )
    }

    companion object {
        fun factory(
            playback: PlaybackRepository,
            favorites: FavoritesRepository,
        ): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory { initializer { PlayerViewModel(playback, favorites) } }
    }
}
