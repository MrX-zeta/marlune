package com.luis.marlune.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luis.marlune.data.repository.LibraryState
import com.luis.marlune.data.repository.MusicRepository
import com.luis.marlune.domain.model.Album
import com.luis.marlune.domain.model.Artist
import com.luis.marlune.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de Biblioteca: proyecta la biblioteca LOCAL real ([MusicRepository]) por categoría.
 * Álbumes/Artistas/Canciones se derivan de MediaStore; las Listas llegarán con Room (Fase 3), por
 * lo que hasta entonces esa categoría queda vacía. Ofrece un escaneo manual (pull-to-refresh) como
 * red de seguridad. Sin mocks, sin red.
 */
class LibraryViewModel(private val repository: MusicRepository) : ViewModel() {

    private val selectedFilter = MutableStateFlow(LibraryFilter.SONGS)
    private val refreshing = MutableStateFlow(false)

    val uiState: StateFlow<LibraryUiState> =
        combine(
            repository.library,
            repository.albums,
            repository.artists,
            selectedFilter,
            refreshing,
        ) { library, albums, artists, filter, isRefreshing ->
            val loading = library is LibraryState.Loading
            val songs = (library as? LibraryState.Content)?.songs.orEmpty()
            LibraryUiState(
                selectedFilter = filter,
                entriesByFilter = mapOf(
                    // Listas: vacío hasta Room (Fase 3).
                    LibraryFilter.PLAYLISTS to emptyList(),
                    LibraryFilter.ALBUMS to albums.map { it.toEntry() },
                    LibraryFilter.ARTISTS to artists.map { it.toEntry() },
                    LibraryFilter.SONGS to songs.map { it.toEntry() },
                ),
                isLoading = loading,
                isRefreshing = isRefreshing,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(
                selectedFilter = LibraryFilter.SONGS,
                entriesByFilter = emptyMap(),
                isLoading = true,
            ),
        )

    fun onFilterSelected(filter: LibraryFilter) {
        selectedFilter.value = filter
    }

    /** Escaneo manual: revisa directorios públicos y re-consulta; muestra "refrescando" mientras. */
    fun onRefresh() {
        viewModelScope.launch {
            refreshing.value = true
            try {
                repository.rescan()
            } finally {
                refreshing.value = false
            }
        }
    }

    private fun Song.toEntry() = LibraryEntry(id = id, title = title, subtitle = artist, artworkUri = artworkUri)

    private fun Album.toEntry() = LibraryEntry(id = id, title = title, subtitle = artist, artworkUri = artworkUri)

    private fun Artist.toEntry() = LibraryEntry(
        id = id,
        title = name,
        subtitle = "$songCount ${if (songCount == 1) "canción" else "canciones"}",
        artworkUri = null,
    )

    companion object {
        fun factory(repository: MusicRepository): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory { initializer { LibraryViewModel(repository) } }
    }
}
