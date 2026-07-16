package com.luis.marlune.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luis.marlune.data.datastore.SearchHistoryStore
import com.luis.marlune.data.repository.LibraryState
import com.luis.marlune.data.repository.MusicRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de Buscar: filtra en vivo la biblioteca LOCAL real ([MusicRepository]) a cada cambio de
 * texto (búsqueda en el repositorio, sin retardo). Gestiona además las búsquedas recientes,
 * persistidas entre sesiones en DataStore ([SearchHistoryStore]). Sin mocks, sin red.
 */
class SearchViewModel(
    repository: MusicRepository,
    private val history: SearchHistoryStore,
) : ViewModel() {

    private val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val results = query.flatMapLatest { repository.searchSongs(it) }

    // Hay biblioteca cuando MediaStore ya respondió con al menos una canción visible.
    private val hasLibrary = repository.library.map { it is LibraryState.Content && it.songs.isNotEmpty() }

    val uiState: StateFlow<SearchUiState> = combine(query, history.terms, results, hasLibrary) { q, r, res, lib ->
        SearchUiState(query = q, recentSearches = r, results = res, hasLibrary = lib)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(query = "", recentSearches = emptyList(), results = emptyList()),
    )

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun onRecentSelected(term: String) {
        query.value = term
    }

    /** Confirmación (Enter): guarda el término como reciente (sube al frente si ya estaba). */
    fun onSubmit() {
        val term = query.value.trim()
        if (term.isEmpty()) return
        viewModelScope.launch { history.add(term) }
    }

    fun onRemoveRecent(term: String) {
        viewModelScope.launch { history.remove(term) }
    }

    fun onClearRecents() {
        viewModelScope.launch { history.clear() }
    }

    fun onClearQuery() {
        query.value = ""
    }

    companion object {
        fun factory(
            repository: MusicRepository,
            history: SearchHistoryStore,
        ): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory { initializer { SearchViewModel(repository, history) } }
    }
}
