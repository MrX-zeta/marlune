package com.luis.marlune.ui.library

import android.net.Uri
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
 * Modelo de presentación derivado de la biblioteca real; `id` es el `_ID`/`ALBUM_ID`/`ARTIST_ID`
 * de MediaStore según la categoría, y `artworkUri` la carátula (nula para artistas o sin arte).
 */
@Immutable
data class LibraryEntry(
    val id: Long,
    val title: String,
    val subtitle: String,
    val artworkUri: Uri? = null,
)

/**
 * Estado de Biblioteca: filtro seleccionado y entradas por categoría (biblioteca LOCAL real).
 * `isLoading` cubre el arranque (shimmer); `isRefreshing`, el escaneo manual (pull-to-refresh).
 */
@Immutable
data class LibraryUiState(
    val selectedFilter: LibraryFilter,
    val entriesByFilter: Map<LibraryFilter, List<LibraryEntry>>,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
) {
    val currentEntries: List<LibraryEntry>
        get() = entriesByFilter[selectedFilter].orEmpty()

    val isEmpty: Boolean get() = !isLoading && currentEntries.isEmpty()
}
