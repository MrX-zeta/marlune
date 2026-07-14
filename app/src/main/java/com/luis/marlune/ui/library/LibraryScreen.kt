package com.luis.marlune.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.luis.marlune.di.rememberPlaybackRepository
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
    viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(rememberMusicRepository(), rememberPlaybackRepository()),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    LibraryScreen(
        uiState = uiState,
        nowPlaying = nowPlaying,
        onRefresh = viewModel::onRefresh,
        // Tocar una canción reproduce la COLA real; el mini-player aparece con esa pista al sonar.
        onPlaySong = viewModel::playSongEntry,
        onOpenEntry = onOpenEntry,
        modifier = modifier,
    )
}

/**
 * Pantalla de Biblioteca (sin estado de datos).
 *
 * El chip seleccionado es estado de UI LOCAL y ligero: tocar un chip lo actualiza al instante, sin
 * pasar por el ViewModel, así el feedback nunca espera a que se repinte la lista. Los chips animan
 * la selección con `animate*AsState` (retargetable: en toques rápidos la pastilla persigue al último
 * sin encolarse). El cambio de contenido es un SWAP DIRECTO (sin Crossfade) para respuesta inmediata:
 * solo se lee la lista YA precalculada del UiState. Las filas entran con stagger solo en la 1.ª carga.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    nowPlaying: NowPlayingUi,
    onRefresh: () -> Unit,
    onPlaySong: (Long) -> Unit,
    onOpenEntry: (LibraryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Selección local: instantánea y desacoplada del ViewModel y de la recomposición del contenido.
    var selectedFilter by rememberSaveable { mutableStateOf(LibraryFilter.SONGS) }

    // En "Canciones" el toque reproduce; en Álbumes/Artistas abre la entrada (detalle, más adelante).
    val onEntryClick: (LibraryEntry) -> Unit = { entry ->
        if (selectedFilter == LibraryFilter.SONGS) onPlaySong(entry.id) else onOpenEntry(entry)
    }

    // El stagger de filas corre una sola vez (primera carga), no en cada cambio de filtro.
    var firstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { firstLoad = false }

    // UNA sola LazyColumn persistente: su estado se hoistea aquí y NO se recrea al cambiar de chip.
    // Cambiar de filtro solo cambia el DATA; Compose recicla los slots (keys + contentType).
    val listState = rememberLazyListState()
    // Al cambiar de categoría se vuelve arriba (evita arrastrar el offset de una lista a otra). O(1).
    LaunchedEffect(selectedFilter) { listState.scrollToItem(0) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            LibraryTopBar()
            LibraryFilterChips(
                selected = selectedFilter,
                onSelect = { selectedFilter = it },
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // Pull-to-refresh = escaneo manual (red de seguridad); el spinner es el estado "escaneando".
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                // Carga (solo arranque) = shimmer; el resto SIEMPRE es la misma LazyColumn (contenido
                // o vacío como item), así el camino caliente nunca desmonta ni recrea la lista.
                if (uiState.isLoading) {
                    LoadingRows(
                        circularCover = selectedFilter == LibraryFilter.ARTISTS ||
                            selectedFilter == LibraryFilter.PLAYLISTS,
                    )
                } else {
                    LibraryList(
                        entries = uiState.entriesFor(selectedFilter),
                        filter = selectedFilter,
                        listState = listState,
                        animateEntrance = firstLoad,
                        nowPlaying = nowPlaying,
                        onEntryClick = onEntryClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryList(
    entries: List<LibraryEntry>,
    filter: LibraryFilter,
    listState: LazyListState,
    animateEntrance: Boolean,
    nowPlaying: NowPlayingUi,
    onEntryClick: (LibraryEntry) -> Unit,
) {
    // Solo se resalta en "Canciones" (los ids de álbum/artista viven en otro espacio de ids).
    val highlightId = if (filter == LibraryFilter.SONGS) nowPlaying.songId else null
    val isArtist = filter == LibraryFilter.ARTISTS
    val coverShape = if (filter == LibraryFilter.PLAYLISTS || isArtist) CircleCover else RoundedCover
    val coverIcon = coverIconFor(isArtist)
    // Menú común a todas las filas: se construye una vez, no por fila ni por recomposición.
    val menuItems = remember { demoMenuItems() }

    // LazyColumn persistente: solo compone (y pide carátula a Coil) las filas VISIBLES; keys estables
    // por id + `contentType` único para reciclar slots al cambiar de chip (sin recargar arte). El
    // vacío es un item de la MISMA lista (no desmonta la LazyColumn ni rompe el pull-to-refresh). El
    // stagger corre una vez, solo en la primera pantalla visible; el scroll queda instantáneo.
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (entries.isEmpty()) {
            item(key = "library-empty", contentType = "empty") {
                EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.library_empty_title),
                    hint = stringResource(R.string.library_empty_hint),
                )
            }
        } else {
            itemsIndexed(
                entries,
                key = { _, entry -> entry.id },
                contentType = { _, _ -> "libraryRow" },
            ) { index, entry ->
                StaggeredReveal(index = index, enabled = animateEntrance && index < StaggerVisibleCount) {
                    LibraryRow(
                        entry = entry,
                        coverIcon = coverIcon,
                        coverShape = coverShape,
                        onClick = { onEntryClick(entry) },
                        menuItems = menuItems,
                        isCurrent = highlightId != null && entry.id == highlightId,
                        isPlaying = nowPlaying.isPlaying,
                    )
                }
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

// Ítems de ejemplo (acciones reales en fases posteriores); labelRes son constantes, no requiere composición.
private fun demoMenuItems(): List<ContextMenuItem> = listOf(
    ContextMenuItem(R.string.library_menu_play) {},
    ContextMenuItem(R.string.library_menu_add_queue) {},
    ContextMenuItem(R.string.library_menu_add_playlist) {},
)

@Preview(showBackground = true, backgroundColor = 0xFF0A0910, heightDp = 800)
@Composable
private fun LibraryScreenPreview() {
    MarluneTheme {
        LibraryScreen(
            uiState = LibraryUiState(
                entriesByFilter = mapOf(
                    LibraryFilter.SONGS to listOf(
                        LibraryEntry(101L, "Noches de bruma", "18 canciones"),
                        LibraryEntry(102L, "Foco profundo", "42 canciones"),
                        LibraryEntry(103L, "Marea baja", "9 canciones"),
                    ),
                ),
            ),
            nowPlaying = NowPlayingUi(songId = 102L, isPlaying = true),
            onRefresh = {},
            onPlaySong = {},
            onOpenEntry = {},
        )
    }
}
