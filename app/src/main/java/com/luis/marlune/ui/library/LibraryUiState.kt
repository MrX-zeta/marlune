package com.luis.marlune.ui.library

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.luis.marlune.R

/** Categorías del filtro de Biblioteca. El orden es el orden visual de los chips. */
enum class LibraryFilter(@param:StringRes val labelRes: Int) {
    PLAYLISTS(R.string.library_filter_playlists),
    ALBUMS(R.string.library_filter_albums),
    ARTISTS(R.string.library_filter_artists),
    SONGS(R.string.library_filter_songs),
}

/**
 * Entrada genérica de una fila de Biblioteca (lista/álbum/artista/canción).
 * Modelo de presentación; los modelos de dominio reales llegarán con la capa `data/`.
 */
@Immutable
data class LibraryEntry(
    val id: Long,
    val title: String,
    val subtitle: String,
)

/**
 * Estado de Biblioteca: filtro seleccionado y entradas por categoría (biblioteca LOCAL).
 */
@Immutable
data class LibraryUiState(
    val selectedFilter: LibraryFilter,
    val entriesByFilter: Map<LibraryFilter, List<LibraryEntry>>,
) {
    val currentEntries: List<LibraryEntry>
        get() = entriesByFilter[selectedFilter].orEmpty()
}
