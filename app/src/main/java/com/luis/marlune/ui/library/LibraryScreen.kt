package com.luis.marlune.ui.library

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luis.marlune.R
import com.luis.marlune.di.rememberMusicRepository
import com.luis.marlune.ui.components.ContextMenuItem
import com.luis.marlune.ui.components.EmptyState
import com.luis.marlune.ui.components.LoadingRows
import com.luis.marlune.ui.components.StaggeredReveal
import com.luis.marlune.ui.library.components.CircleCover
import com.luis.marlune.ui.library.components.LibraryFilterChips
import com.luis.marlune.ui.library.components.LibraryRow
import com.luis.marlune.ui.library.components.RoundedCover
import com.luis.marlune.ui.library.components.coverIconFor
import com.luis.marlune.ui.theme.MarluneTheme

/** Punto de entrada con estado de Biblioteca. */
@Composable
fun LibraryRoute(
    onOpenEntry: (LibraryEntry) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(rememberMusicRepository())),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        uiState = uiState,
        onFilterSelected = viewModel::onFilterSelected,
        onRefresh = viewModel::onRefresh,
        onOpenEntry = onOpenEntry,
        modifier = modifier,
    )
}

/**
 * Pantalla de Biblioteca (sin estado).
 *
 * Cabecera con el título y UN control de filtro (la búsqueda vive en su pestaña, sin lupa aquí).
 * Los chips animan la selección (color 180 ms + indicador deslizante); el cambio de contenido
 * entre categorías es un fade rápido (150 ms) sin slide. Las filas entran con stagger solo en la
 * primera carga.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onFilterSelected: (LibraryFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpenEntry: (LibraryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    // El stagger de filas corre una sola vez (primera carga), no en cada cambio de filtro.
    var firstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { firstLoad = false }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            LibraryTopBar()
            LibraryFilterChips(
                selected = uiState.selectedFilter,
                onSelect = onFilterSelected,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // Pull-to-refresh = escaneo manual (red de seguridad); el spinner es el estado "escaneando".
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when {
                    uiState.isLoading -> LoadingRows(
                        circularCover = uiState.selectedFilter == LibraryFilter.ARTISTS ||
                            uiState.selectedFilter == LibraryFilter.PLAYLISTS,
                    )

                    // Contenedor desplazable para que el pull-to-refresh también funcione en vacío.
                    uiState.isEmpty -> Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    ) {
                        EmptyState(
                            icon = Icons.Rounded.LibraryMusic,
                            title = stringResource(R.string.library_empty_title),
                            hint = stringResource(R.string.library_empty_hint),
                        )
                    }

                    else -> Crossfade(
                        targetState = uiState.selectedFilter,
                        animationSpec = snapOrFade(),
                        label = "libraryContent",
                    ) { filter ->
                        LibraryList(
                            entries = uiState.entriesByFilter[filter].orEmpty(),
                            filter = filter,
                            animateEntrance = firstLoad,
                            onOpenEntry = onOpenEntry,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryList(
    entries: List<LibraryEntry>,
    filter: LibraryFilter,
    animateEntrance: Boolean,
    onOpenEntry: (LibraryEntry) -> Unit,
) {
    val isArtist = filter == LibraryFilter.ARTISTS
    val coverShape = if (filter == LibraryFilter.PLAYLISTS || isArtist) CircleCover else RoundedCover
    val coverIcon = coverIconFor(isArtist)

    // LazyColumn: solo compone (y solo pide carátula a Coil) las filas VISIBLES; keys estables por id.
    // El stagger corre una vez, solo en la primera pantalla visible; el scroll queda instantáneo.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
            StaggeredReveal(index = index, enabled = animateEntrance && index < StaggerVisibleCount) {
                LibraryRow(
                    entry = entry,
                    coverIcon = coverIcon,
                    coverShape = coverShape,
                    onClick = { onOpenEntry(entry) },
                    menuItems = demoMenuItems(),
                )
            }
        }
    }
}

/** Filas que reciben la entrada escalonada en la primera pantalla (grupos de 3–7 según la skill). */
private const val StaggerVisibleCount = 7

@Composable
private fun LibraryTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.library_title),
            style = MarluneTheme.typography.headlineMedium,
            color = MarluneTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        // Se conserva el filtro; se eliminó la lupa (la búsqueda vive en su pestaña).
        IconButton(onClick = { /* Filtro/orden: acción en tarea posterior */ }) {
            Icon(
                imageVector = Icons.Rounded.FilterList,
                contentDescription = stringResource(R.string.library_filter),
                tint = MarluneTheme.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun demoMenuItems(): List<ContextMenuItem> = listOf(
    ContextMenuItem(R.string.library_menu_play) {},
    ContextMenuItem(R.string.library_menu_add_queue) {},
    ContextMenuItem(R.string.library_menu_add_playlist) {},
)

// Fade rápido (150 ms) para el cambio de contenido; instantáneo con movimiento reducido.
@Composable
private fun snapOrFade() =
    if (com.luis.marlune.ui.theme.LocalReducedMotion.current) snap() else tween<Float>(150)

@Preview(showBackground = true, backgroundColor = 0xFF0A0910, heightDp = 800)
@Composable
private fun LibraryScreenPreview() {
    MarluneTheme {
        LibraryScreen(
            uiState = LibraryUiState(
                selectedFilter = LibraryFilter.PLAYLISTS,
                entriesByFilter = mapOf(
                    LibraryFilter.PLAYLISTS to listOf(
                        LibraryEntry(101L, "Noches de bruma", "18 canciones"),
                        LibraryEntry(102L, "Foco profundo", "42 canciones"),
                        LibraryEntry(103L, "Marea baja", "9 canciones"),
                    ),
                ),
            ),
            onFilterSelected = {},
            onRefresh = {},
            onOpenEntry = {},
        )
    }
}
