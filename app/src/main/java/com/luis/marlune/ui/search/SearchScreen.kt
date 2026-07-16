package com.luis.marlune.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luis.marlune.R
import com.luis.marlune.di.rememberMusicRepository
import com.luis.marlune.di.rememberPlaybackRepository
import com.luis.marlune.di.rememberSearchHistoryStore
import com.luis.marlune.domain.model.Song
import com.luis.marlune.playback.PlaybackRepository
import com.luis.marlune.ui.components.StaggeredReveal
import com.luis.marlune.ui.search.components.RecentSearchChips
import com.luis.marlune.ui.search.components.SearchCategoryGrid
import com.luis.marlune.ui.search.components.SearchField
import com.luis.marlune.ui.search.components.SearchResultRow
import com.luis.marlune.ui.theme.MarluneTheme

/** Punto de entrada con estado de Buscar. */
@Composable
fun SearchRoute(
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenLiked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.factory(rememberMusicRepository(), rememberSearchHistoryStore()),
    ),
    playback: PlaybackRepository = rememberPlaybackRepository(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::onSubmit,
        onClear = viewModel::onClearQuery,
        onRecentSelected = viewModel::onRecentSelected,
        onRemoveRecent = viewModel::onRemoveRecent,
        onClearRecents = viewModel::onClearRecents,
        onOpenAlbums = onOpenAlbums,
        onOpenArtists = onOpenArtists,
        onOpenPlaylists = onOpenPlaylists,
        onOpenLiked = onOpenLiked,
        // Reproduce la COLA real de resultados; el mini-player aparece con esa pista al sonar.
        onOpenTrack = { song ->
            playback.playSongs(uiState.results, uiState.results.indexOf(song))
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
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenLiked: () -> Unit,
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
                        hasLibrary = uiState.hasLibrary,
                        onRecentSelected = onRecentSelected,
                        onRemoveRecent = onRemoveRecent,
                        onClearRecents = onClearRecents,
                        onOpenAlbums = onOpenAlbums,
                        onOpenArtists = onOpenArtists,
                        onOpenPlaylists = onOpenPlaylists,
                        onOpenLiked = onOpenLiked,
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

/**
 * Estado inicial (campo vacío): búsquedas recientes + accesos por categoría. Los accesos solo se
 * muestran si HAY biblioteca (sin música, no tendrían contenido → se respeta el vacío existente). Si
 * no hay ni recientes ni biblioteca, se muestra la pista vacía de siempre. Las secciones entran con
 * un stagger corto solo en la primera composición (vía [StaggeredReveal], que respeta el movimiento
 * reducido). Scroll vertical de seguridad para pantallas bajas.
 */
@Composable
private fun ExploreArea(
    recentSearches: List<String>,
    hasLibrary: Boolean,
    onRecentSelected: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenLiked: () -> Unit,
) {
    if (recentSearches.isEmpty() && !hasLibrary) {
        EmptyHint(text = stringResource(R.string.search_empty_hint))
        return
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        var section = 0
        if (recentSearches.isNotEmpty()) {
            StaggeredReveal(index = section++) {
                RecentSearchesSection(
                    recentSearches = recentSearches,
                    onRecentSelected = onRecentSelected,
                    onRemoveRecent = onRemoveRecent,
                    onClearRecents = onClearRecents,
                )
            }
        }
        if (hasLibrary) {
            if (recentSearches.isNotEmpty()) Spacer(Modifier.height(28.dp))
            StaggeredReveal(index = section++) {
                SearchCategoryGrid(
                    onOpenAlbums = onOpenAlbums,
                    onOpenArtists = onOpenArtists,
                    onOpenPlaylists = onOpenPlaylists,
                    onOpenLiked = onOpenLiked,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Sección de recientes: encabezado discreto (versalitas, terciario) con acción "Borrar" y chips. */
@Composable
private fun RecentSearchesSection(
    recentSearches: List<String>,
    onRecentSelected: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.search_recent).uppercase(),
                style = MarluneTheme.typography.labelMedium,
                color = MarluneTheme.colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.search_recent_clear),
                style = MarluneTheme.typography.labelMedium,
                color = MarluneTheme.colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onClearRecents)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        RecentSearchChips(
            terms = recentSearches,
            onSelect = onRecentSelected,
            onRemove = onRemoveRecent,
        )
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
            uiState = SearchUiState("", listOf("Lún", "lo-fi", "Bruma", "Ambient"), emptyList(), hasLibrary = true),
            onQueryChange = {}, onSubmit = {}, onClear = {}, onRecentSelected = {},
            onRemoveRecent = {}, onClearRecents = {},
            onOpenAlbums = {}, onOpenArtists = {}, onOpenPlaylists = {}, onOpenLiked = {}, onOpenTrack = {},
        )
    }
}
