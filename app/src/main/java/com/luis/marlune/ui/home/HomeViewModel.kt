package com.luis.marlune.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luis.marlune.data.repository.HistoryRepository
import com.luis.marlune.data.repository.LibraryState
import com.luis.marlune.data.repository.MusicRepository
import com.luis.marlune.data.repository.SavedSession
import com.luis.marlune.data.repository.SavedSessionRepository
import com.luis.marlune.playback.PlaybackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

private const val MixSize = 50

/**
 * ViewModel de Inicio: "Escuchado hace poco" (historial real) y la card "Continuar" sobre la sesión
 * persistida. Además dos ACCIONES locales: un Mix barajado de la biblioteca (sesgado a las menos
 * escuchadas si hay historial) y reanudar la sesión guardada.
 */
class HomeViewModel(
    private val music: MusicRepository,
    private val history: HistoryRepository,
    private val playback: PlaybackRepository,
    savedSession: SavedSessionRepository,
) : ViewModel() {

    private val session: StateFlow<SavedSession?> =
        savedSession.savedSession.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<HomeUiState> =
        combine(
            music.library,
            history.recentlyPlayed,
            session,
        ) { libraryState, recent, savedSession ->
            HomeUiState(
                greeting = greetingNow(),
                recent = recent,
                isLoading = libraryState is LibraryState.Loading,
                libraryEmpty = libraryState is LibraryState.Content && libraryState.songs.isEmpty(),
                continueSession = savedSession?.current?.let { ContinueInfo(it.title, it.artworkUri) },
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeUiState(greetingNow(), emptyList(), isLoading = true, libraryEmpty = false, continueSession = null),
            )

    /**
     * Mix: arma una cola BARAJADA de la biblioteca local, sesgada hacia las MENOS escuchadas (nunca
     * reproducidas o hace más tiempo) usando el historial, y empieza a reproducir. 100% local.
     */
    fun playMix() {
        viewModelScope.launch {
            val songs = (music.library.value as? LibraryState.Content)?.songs.orEmpty()
            if (songs.isEmpty()) return@launch
            val lastPlayed = history.lastPlayedById()
            val queue = songs
                .shuffled() // base aleatoria
                .sortedBy { lastPlayed[it.id] ?: 0L } // sesgo: nunca reproducidas (0) y más antiguas primero
                .take(MixSize)
                .shuffled() // orden de reproducción final barajado
            playback.playSongs(queue, startIndex = 0)
        }
    }

    /** Reanuda la sesión guardada: carga la cola en su índice y posición, y reproduce. */
    fun resumeSession() {
        val saved = session.value ?: return
        playback.playSongs(saved.songs, startIndex = saved.index, startPositionMs = saved.positionMs)
    }

    private fun greetingNow(): Greeting = when (LocalTime.now().hour) {
        in 5..11 -> Greeting.MORNING
        in 12..19 -> Greeting.AFTERNOON
        else -> Greeting.NIGHT
    }

    companion object {
        fun factory(
            music: MusicRepository,
            history: HistoryRepository,
            playback: PlaybackRepository,
            savedSession: SavedSessionRepository,
        ): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory { initializer { HomeViewModel(music, history, playback, savedSession) } }
    }
}
