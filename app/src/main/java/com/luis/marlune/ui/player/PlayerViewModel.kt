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

    fun onEvent(event: PlayerEvent) {
        when (event) {
            PlayerEvent.PlayPause -> setPlaying(!_uiState.value.isPlaying)
            PlayerEvent.Next -> skipTo(0L)
            PlayerEvent.Previous -> skipTo(0L)
            PlayerEvent.ToggleShuffle -> _uiState.update { it.copy(isShuffleOn = !it.isShuffleOn) }
            PlayerEvent.ToggleRepeat -> _uiState.update { it.copy(repeatMode = it.repeatMode.next()) }
            PlayerEvent.ToggleLike -> _uiState.update { it.copy(isLiked = !it.isLiked) }
        }
    }

    private fun setPlaying(playing: Boolean) {
        _uiState.update { it.copy(isPlaying = playing) }
        if (playing) startTicker() else tickerJob?.cancel()
    }

    /** Placeholder de cambio de pista: reinicia la posición. La cola real vive en `playback/`. */
    private fun skipTo(positionMs: Long) {
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
                    setPlaying(false)
                    break
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
