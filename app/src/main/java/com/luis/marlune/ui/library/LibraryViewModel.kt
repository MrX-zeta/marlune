package com.luis.marlune.ui.library

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel de Biblioteca.
 *
 * Sirve datos de ejemplo de la biblioteca LOCAL por categoría; la fuente real (MediaStore /
 * base de datos de listas) llegará en la capa `data/`. La UI observa [uiState] y cambia el
 * filtro con [onFilterSelected].
 */
class LibraryViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        LibraryUiState(
            selectedFilter = LibraryFilter.PLAYLISTS,
            entriesByFilter = sampleData(),
        ),
    )
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    fun onFilterSelected(filter: LibraryFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    private companion object {
        fun sampleData(): Map<LibraryFilter, List<LibraryEntry>> = mapOf(
            LibraryFilter.PLAYLISTS to listOf(
                LibraryEntry(101L, "Noches de bruma", "18 canciones"),
                LibraryEntry(102L, "Foco profundo", "42 canciones"),
                LibraryEntry(103L, "Marea baja", "9 canciones"),
                LibraryEntry(104L, "Domingo lento", "27 canciones"),
            ),
            LibraryFilter.ALBUMS to listOf(
                LibraryEntry(201L, "Costa dormida", "Maréas"),
                LibraryEntry(202L, "Vidrio", "Nocta"),
                LibraryEntry(203L, "Reflejo", "Aiko"),
            ),
            LibraryFilter.ARTISTS to listOf(
                LibraryEntry(301L, "Lún", "12 canciones"),
                LibraryEntry(302L, "Maréas", "1 álbum"),
                LibraryEntry(303L, "Nocta", "2 álbumes"),
            ),
            LibraryFilter.SONGS to listOf(
                LibraryEntry(401L, "Bruma", "Lún"),
                LibraryEntry(402L, "Sal", "Lún"),
                LibraryEntry(403L, "Costa dormida", "Maréas"),
                LibraryEntry(404L, "Vidrio", "Nocta"),
            ),
        )
    }
}
