package com.luis.marlune.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luis.marlune.R
import com.luis.marlune.di.rememberMusicRepository
import com.luis.marlune.di.rememberPlaybackRepository
import com.luis.marlune.domain.model.Song
import com.luis.marlune.playback.PlaybackRepository
import com.luis.marlune.ui.components.StaggeredReveal
import com.luis.marlune.ui.search.components.RecentSearchChips
import com.luis.marlune.ui.search.components.SearchField
import com.luis.marlune.ui.search.components.SearchResultRow
import com.luis.marlune.ui.theme.MarluneTheme

/** Punto de entrada con estado de Buscar. */
@Composable
fun SearchRoute(
    onExpandPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(rememberMusicRepository())),
    playback: PlaybackRepository = rememberPlaybackRepository(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::onSubmit,
        onClear = viewModel::onClearQuery,
        onRecentSelected = viewModel::onRecentSelected,
        // Reproduce la COLA real de resultados (Fase 2); la UI del Player se conecta en la Fase 3.
        onOpenTrack = { song ->
            playback.playSongs(uiState.results, uiState.results.indexOf(song))
            onExpandPlayer()
        },
        modifier = modifier,
    )
}

/**
 * Pantalla de Buscar (sin estado).
 *
 * Núcleo minimalista: campo de búsqueda + búsquedas recientes. Sin tarjetas de descubrimiento
 * (implicarían un catálogo online, fuera del alcance local). Con texto, muestra resultados
 * filtrados en vivo que entran con fade+rise; sin texto, muestra las recientes.
 */
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onRecentSelected: (String) -> Unit,
    onOpenTrack: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.search_title),
                style = MarluneTheme.typography.headlineMedium,
                color = MarluneTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(16.dp))
            SearchField(
                query = uiState.query,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                onClear = onClear,
            )
            Spacer(Modifier.height(20.dp))

            // Área desplazable acotada: la lista perezosa maneja su propio scroll dentro de este hueco.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (uiState.isSearching) {
                    SearchResults(results = uiState.results, onOpenTrack = onOpenTrack)
                } else {
                    ExploreArea(
                        recentSearches = uiState.recentSearches,
                        onRecentSelected = onRecentSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    results: List<Song>,
    onOpenTrack: (Song) -> Unit,
) {
    if (results.isEmpty()) {
        EmptyHint(text = stringResource(R.string.search_no_results))
        return
    }
    // LazyColumn: solo compone y pide carátula para los resultados VISIBLES; keys estables por id
    // (un resultado que persiste entre búsquedas no re-anima; los nuevos entran con fade+rise).
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(
            results,
            key = { _, song -> song.id },
            contentType = { _, _ -> "searchResultRow" },
        ) { index, song ->
            StaggeredReveal(index = index, enabled = index < StaggerVisibleCount) {
                SearchResultRow(song = song, onClick = { onOpenTrack(song) })
            }
        }
    }
}

/** Resultados que reciben la entrada escalonada en la primera pantalla; el scroll queda instantáneo. */
private const val StaggerVisibleCount = 7

@Composable
private fun ExploreArea(
    recentSearches: List<String>,
    onRecentSelected: (String) -> Unit,
) {
    if (recentSearches.isEmpty()) {
        EmptyHint(text = stringResource(R.string.search_empty_hint))
        return
    }
    Column {
        Text(
            text = stringResource(R.string.search_recent),
            style = MarluneTheme.typography.titleMedium,
            color = MarluneTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(12.dp))
        RecentSearchChips(terms = recentSearches, onSelect = onRecentSelected)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = text,
            style = MarluneTheme.typography.bodyMedium,
            color = MarluneTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0910, heightDp = 700)
@Composable
private fun SearchRecentPreview() {
    MarluneTheme {
        SearchScreen(
            uiState = SearchUiState("", listOf("Lún", "lo-fi", "Bruma", "Ambient"), emptyList()),
            onQueryChange = {}, onSubmit = {}, onClear = {}, onRecentSelected = {}, onOpenTrack = {},
        )
    }
}
