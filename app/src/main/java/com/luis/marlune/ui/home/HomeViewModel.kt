package com.luis.marlune.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luis.marlune.data.repository.HistoryRepository
import com.luis.marlune.data.repository.LibraryState
import com.luis.marlune.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime

/**
 * ViewModel de Inicio: "Escuchado hace poco" viene del historial REAL (Room), resuelto contra la
 * biblioteca; se combina con el estado de la biblioteca para distinguir "cargando" y "sin música".
 * Sin mocks, sin red.
 */
class HomeViewModel(
    music: MusicRepository,
    history: HistoryRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(music.library, history.recentlyPlayed) { libraryState, recent ->
            HomeUiState(
                greeting = greetingNow(),
                recent = recent,
                isLoading = libraryState is LibraryState.Loading,
                libraryEmpty = libraryState is LibraryState.Content && libraryState.songs.isEmpty(),
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeUiState(greetingNow(), emptyList(), isLoading = true, libraryEmpty = false),
            )

    private fun greetingNow(): Greeting = when (LocalTime.now().hour) {
        in 5..11 -> Greeting.MORNING
        in 12..19 -> Greeting.AFTERNOON
        else -> Greeting.NIGHT
    }

    companion object {
        fun factory(
            music: MusicRepository,
            history: HistoryRepository,
        ): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory { initializer { HomeViewModel(music, history) } }
    }
}
