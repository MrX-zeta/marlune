package com.luis.marlune.ui.search

import androidx.compose.runtime.Immutable
import com.luis.marlune.domain.model.Song

/**
 * Estado de la vista Buscar.
 *
 * Búsqueda simple sobre la biblioteca LOCAL real (MediaStore). `results` se recalcula en vivo a
 * cada pulsación (sin retardo) filtrando por título, álbum, artista y género. `recentSearches` son
 * términos previos (más reciente primero). No hay catálogo remoto ni descubrimiento online.
 */
@Immutable
data class SearchUiState(
    val query: String,
    val recentSearches: List<String>,
    val results: List<Song>,
) {
    val isSearching: Boolean get() = query.isNotBlank()
}
