package com.luis.marlune.ui.search

import androidx.lifecycle.ViewModel
import com.luis.marlune.domain.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * ViewModel de Buscar.
 *
 * Filtra en memoria la biblioteca LOCAL a cada cambio de texto (síncrono, sin retardo). La
 * fuente real (índice de MediaStore) llegará en la capa `data/`. Gestiona además las búsquedas
 * recientes.
 */
class SearchViewModel : ViewModel() {

    private val library: List<Track> = sampleLibrary()

    private val _uiState = MutableStateFlow(
        SearchUiState(
            query = "",
            recentSearches = listOf("Lún", "lo-fi", "Bruma"),
            results = emptyList(),
        ),
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, results = filter(query)) }
    }

    fun onRecentSelected(term: String) {
        _uiState.update { it.copy(query = term, results = filter(term)) }
    }

    fun onSubmit() {
        val term = _uiState.value.query.trim()
        if (term.isEmpty()) return
        _uiState.update { state ->
            val deduped = state.recentSearches.filterNot { it.equals(term, ignoreCase = true) }
            state.copy(recentSearches = (listOf(term) + deduped).take(MAX_RECENT))
        }
    }

    fun onClearQuery() {
        _uiState.update { it.copy(query = "", results = emptyList()) }
    }

    /** Coincidencia por título, álbum, artista o género (contains, sin distinguir mayúsculas). */
    private fun filter(query: String): List<Track> {
        val term = query.trim().lowercase(Locale.getDefault())
        if (term.isEmpty()) return emptyList()
        return library.filter { track ->
            track.title.lowercase(Locale.getDefault()).contains(term) ||
                track.artist.lowercase(Locale.getDefault()).contains(term) ||
                track.album?.lowercase(Locale.getDefault())?.contains(term) == true ||
                track.genre?.lowercase(Locale.getDefault())?.contains(term) == true
        }
    }

    private companion object {
        const val MAX_RECENT = 10

        fun sampleLibrary(): List<Track> = listOf(
            Track(id = 1L, title = "Bruma", artist = "Lún", album = "Bruma", genre = "Ambient", durationMs = 198_000L),
            Track(id = 2L, title = "Costa dormida", artist = "Maréas", album = "Costa dormida", genre = "Lo-fi", durationMs = 224_000L),
            Track(id = 3L, title = "Vidrio", artist = "Nocta", album = "Vidrio", genre = "Electrónica", durationMs = 176_000L),
            Track(id = 4L, title = "Reflejo", artist = "Aiko", album = "Reflejo", genre = "Lo-fi", durationMs = 205_000L),
            Track(id = 5L, title = "Sal", artist = "Lún", album = "Bruma", genre = "Ambient", durationMs = 189_000L),
            Track(id = 6L, title = "Marea baja", artist = "Maréas", album = "Costa dormida", genre = "Chillout", durationMs = 233_000L),
        )
    }
}
