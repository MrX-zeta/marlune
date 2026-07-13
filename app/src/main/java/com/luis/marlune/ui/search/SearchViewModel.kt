package com.luis.marlune.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luis.marlune.data.repository.MusicRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * ViewModel de Buscar: filtra en vivo la biblioteca LOCAL real ([MusicRepository]) a cada cambio de
 * texto (búsqueda en el repositorio, sin retardo). Gestiona además las búsquedas recientes (en
 * memoria por sesión; su persistencia llegará con Room). Sin mocks, sin red.
 */
class SearchViewModel(repository: MusicRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val recents = MutableStateFlow<List<String>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val results = query.flatMapLatest { repository.searchSongs(it) }

    val uiState: StateFlow<SearchUiState> = combine(query, recents, results) { q, r, res ->
        SearchUiState(query = q, recentSearches = r, results = res)
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

    fun onSubmit() {
        val term = query.value.trim()
        if (term.isEmpty()) return
        recents.update { current ->
            val deduped = current.filterNot { it.equals(term, ignoreCase = true) }
            (listOf(term) + deduped).take(MAX_RECENT)
        }
    }

    fun onClearQuery() {
        query.value = ""
    }

    companion object {
        private const val MAX_RECENT = 10

        fun factory(repository: MusicRepository): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory { initializer { SearchViewModel(repository) } }
    }
}
