package com.luis.marlune.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luis.marlune.data.repository.LibraryState
import com.luis.marlune.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime

private const val RecentLimit = 8

/**
 * ViewModel de Inicio: proyecta la biblioteca LOCAL real ([MusicRepository]) al estado de la
 * pantalla. "Escuchado hace poco" son, de momento, las canciones recién añadidas (por `dateAdded`);
 * cuando llegue el historial (Room, Fase 3) se cambiará solo la fuente. Sin mocks, sin red.
 */
class HomeViewModel(repository: MusicRepository) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = repository.library
        .map { state -> state.toHomeUiState() }
        .flowOn(Dispatchers.Default) // el ordenado por fecha corre fuera del hilo principal
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(greetingNow(), emptyList(), isLoading = true),
        )

    private fun LibraryState.toHomeUiState(): HomeUiState = when (this) {
        LibraryState.Loading -> HomeUiState(greetingNow(), emptyList(), isLoading = true)
        is LibraryState.Content -> HomeUiState(
            greeting = greetingNow(),
            recent = songs.sortedByDescending { it.dateAdded }.take(RecentLimit),
            isLoading = false,
        )
    }

    private fun greetingNow(): Greeting = when (LocalTime.now().hour) {
        in 5..11 -> Greeting.MORNING
        in 12..19 -> Greeting.AFTERNOON
        else -> Greeting.NIGHT
    }

    companion object {
        fun factory(repository: MusicRepository): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory { initializer { HomeViewModel(repository) } }
    }
}
