package com.luis.marlune.ui.search

import androidx.compose.runtime.Immutable
import com.luis.marlune.domain.model.Song

/**
 * Estado de la vista Buscar.
 *
 * Búsqueda simple sobre la biblioteca LOCAL real (MediaStore). `results` se recalcula en vivo a
 * cada pulsación (sin retardo) filtrando por título, álbum, artista y género. `recentSearches` son
 * términos previos (más reciente primero). `hasLibrary` indica si hay música en el dispositivo (para
 * ocultar los accesos por categoría cuando no habría contenido). No hay catálogo remoto ni online.
 */
@Immutable
data class SearchUiState(
    val query: String,
    val recentSearches: List<String>,
    val results: List<Song>,
    val hasLibrary: Boolean = false,
) {
    val isSearching: Boolean get() = query.isNotBlank()
}
