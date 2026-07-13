package com.luis.marlune.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luis.marlune.domain.model.RepeatMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel del Reproductor.
 *
 * De momento mantiene el estado y avanza la posición con un tick de marcador de posición
 * para que la marea y los controles respondan; el motor real (ExoPlayer/MediaSession) llegará
 * en la capa `playback/`. La UI solo observa [uiState] y emite [PlayerEvent].
 */
class PlayerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState.Preview.copy(isPlaying = false))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var trackChangeCounter = 0
    private var currentIndex = 0 // índice en la cola; su variación define la dirección canónica

    fun onEvent(event: PlayerEvent) {
        when (event) {
            PlayerEvent.PlayPause -> setPlaying(!_uiState.value.isPlaying)
            PlayerEvent.Next -> skipToNext()
            PlayerEvent.Previous -> skipToPrevious()
            PlayerEvent.ToggleShuffle -> _uiState.update { it.copy(isShuffleOn = !it.isShuffleOn) }
            PlayerEvent.ToggleRepeat -> _uiState.update { it.copy(repeatMode = it.repeatMode.next()) }
            PlayerEvent.ToggleLike -> _uiState.update { it.copy(isLiked = !it.isLiked) }
            is PlayerEvent.SeekTo -> seekTo(event.positionMs)
        }
    }

    private fun setPlaying(playing: Boolean) {
        _uiState.update { it.copy(isPlaying = playing) }
        if (playing) startTicker() else tickerJob?.cancel()
    }

    private fun skipToNext() = changeTrackTo(currentIndex + 1)

    private fun skipToPrevious() = changeTrackTo(currentIndex - 1)

    /**
     * Cambia de pista a [newIndex] en la cola. La dirección CANÓNICA sale de comparar el índice
     * nuevo con el anterior (avanzó = siguiente; retrocedió = anterior) y se registra en
     * [PlayerUiState.trackTransition] —única fuente de la que la UI deriva la dirección de la
     * animación—. TODOS los orígenes (botones, swipe, auto-avance, notificación/Bluetooth) pasan
     * por aquí. El `MediaController` real llegará en `playback/`; de momento reinicia la posición.
     */
    private fun changeTrackTo(newIndex: Int) {
        if (newIndex == currentIndex) return
        val forward = newIndex > currentIndex
        currentIndex = newIndex
        trackChangeCounter++
        _uiState.update {
            it.copy(
                positionMs = 0L,
                trackTransition = TrackTransition(id = trackChangeCounter, forward = forward),
            )
        }
    }

    /** Salto de posición dentro de la pista. El `MediaController.seekTo` real irá en `playback/`. */
    private fun seekTo(positionMs: Long) {
        _uiState.update { it.copy(positionMs = positionMs.coerceIn(0L, it.durationMs)) }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                val current = _uiState.value
                if (!current.isPlaying) break
                val next = (current.positionMs + TICK_MS).coerceAtMost(current.durationMs)
                _uiState.update { it.copy(positionMs = next) }
                if (next >= current.durationMs) {
                    // Auto-avance al terminar: mismo camino que el botón/swipe → misma dirección.
                    skipToNext()
                }
            }
        }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val TICK_MS = 500L
    }
}
