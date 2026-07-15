package com.luis.marlune.ui.library

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luis.marlune.R
import com.luis.marlune.data.mediastore.MediaStoreAudioSource.DeleteOutcome
import com.luis.marlune.di.rememberFavoritesRepository
import com.luis.marlune.di.rememberHistoryRepository
import com.luis.marlune.di.rememberLibrarySortStore
import com.luis.marlune.di.rememberMusicRepository
import com.luis.marlune.di.rememberPlaybackRepository
import com.luis.marlune.di.rememberPlaylistRepository
import com.luis.marlune.ui.components.ContextMenu
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
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(
            rememberMusicRepository(),
            rememberPlaybackRepository(),
            rememberPlaylistRepository(),
            rememberFavoritesRepository(),
            rememberHistoryRepository(),
            rememberLibrarySortStore(),
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    LibraryScreen(
        uiState = uiState,
        nowPlaying = nowPlaying,
        currentSort = sort,
        onSelectSort = viewModel::setSort,
        contentPadding = contentPadding,
        onRefresh = viewModel::onRefresh,
        // Tocar una canción reproduce la COLA real; el mini-player aparece con esa pista al sonar.
        onPlaySong = viewModel::playSongEntry,
        onAddToQueue = viewModel::addSongToQueue,
        onBeginDelete = viewModel::beginDelete,
        onSongDeleted = viewModel::onSongDeleted,
        onCompleteDelete = viewModel::completeDelete,
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onOpenPlaylist = onOpenPlaylist,
        onCreatePlaylist = viewModel::createPlaylist,
        onRenamePlaylist = viewModel::renamePlaylist,
        onDeletePlaylist = viewModel::deletePlaylist,
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
    currentSort: LibrarySort,
    onSelectSort: (LibrarySort) -> Unit,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onPlaySong: (Long) -> Unit,
    onAddToQueue: (Long) -> Unit,
    onBeginDelete: (Long) -> DeleteOutcome,
    onSongDeleted: (Long) -> Unit,
    onCompleteDelete: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Selección local: instantánea y desacoplada del ViewModel y de la recomposición del contenido.
    var selectedFilter by rememberSaveable { mutableStateOf(LibraryFilter.SONGS) }

    // El toque depende del chip: Canciones reproduce; Álbumes/Artistas abren su detalle (id de la
    // entrada = ALBUM_ID/ARTIST_ID). Listas: sin acción hasta que existan las playlists.
    val onEntryClick: (LibraryEntry) -> Unit = { entry ->
        when (selectedFilter) {
            LibraryFilter.SONGS -> onPlaySong(entry.id)
            LibraryFilter.ALBUMS -> onOpenAlbum(entry.id)
            LibraryFilter.ARTISTS -> onOpenArtist(entry.id)
            LibraryFilter.PLAYLISTS -> {}
        }
    }

    // El stagger de filas corre una sola vez (primera carga), no en cada cambio de filtro.
    var firstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { firstLoad = false }

    // UNA sola LazyColumn persistente: su estado se hoistea aquí y NO se recrea al cambiar de chip.
    // Cambiar de filtro solo cambia el DATA; Compose recicla los slots (keys + contentType).
    val listState = rememberLazyListState()
    // Al cambiar de categoría se vuelve arriba (evita arrastrar el offset de una lista a otra). O(1).
    LaunchedEffect(selectedFilter) { listState.scrollToItem(0) }

    // Padding inferior dinámico: el alto real del mini-player (cuando hay pista) + la barra vía el
    // inset del Scaffold, + un respiro para que la última canción no quede pegada al borde/mini-player.
    val listBottomPadding = contentPadding.calculateBottomPadding() + 24.dp

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(horizontal = 20.dp),
        ) {
            LibraryTopBar(currentSort = currentSort, onSelectSort = onSelectSort)
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
                when {
                    uiState.isLoading -> LoadingRows(
                        circularCover = selectedFilter == LibraryFilter.ARTISTS ||
                            selectedFilter == LibraryFilter.PLAYLISTS,
                    )
                    // Listas: panel propio (crear + menú Renombrar/Borrar + diálogos).
                    selectedFilter == LibraryFilter.PLAYLISTS -> PlaylistsPane(
                        playlists = uiState.entriesFor(LibraryFilter.PLAYLISTS),
                        bottomPadding = listBottomPadding,
                        onCreate = onCreatePlaylist,
                        onRename = onRenamePlaylist,
                        onDelete = onDeletePlaylist,
                        onOpen = onOpenPlaylist,
                    )
                    else -> LibraryList(
                        entries = uiState.entriesFor(selectedFilter),
                        filter = selectedFilter,
                        listState = listState,
                        animateEntrance = firstLoad,
                        nowPlaying = nowPlaying,
                        bottomPadding = listBottomPadding,
                        onEntryClick = onEntryClick,
                        onAddToQueue = onAddToQueue,
                        onBeginDelete = onBeginDelete,
                        onSongDeleted = onSongDeleted,
                        onCompleteDelete = onCompleteDelete,
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
    bottomPadding: Dp,
    onEntryClick: (LibraryEntry) -> Unit,
    onAddToQueue: (Long) -> Unit,
    onBeginDelete: (Long) -> DeleteOutcome,
    onSongDeleted: (Long) -> Unit,
    onCompleteDelete: (Long) -> Unit,
) {
    // Solo se resalta en "Canciones" (los ids de álbum/artista viven en otro espacio de ids).
    val highlightId = if (filter == LibraryFilter.SONGS) nowPlaying.songId else null
    val isArtist = filter == LibraryFilter.ARTISTS
    val coverShape = if (filter == LibraryFilter.PLAYLISTS || isArtist) CircleCover else RoundedCover
    val coverIcon = coverIconFor(isArtist)
    // El menú de 3 puntos solo en Canciones (el id de la fila es el _ID de la pista); álbumes/artistas
    // no llevan menú. El selector "Añadir a lista" se hospeda aquí.
    val songMenu = filter == LibraryFilter.SONGS
    var addTarget by remember { mutableStateOf<Long?>(null) }
    // Confirmación discreta de "Añadir a la cola" (Reproducir ya se confirma solo: aparece el mini-player).
    val context = LocalContext.current

    // Borrado del archivo: nuestra confirmación primero (deleteTarget) y, al aceptar, la del SISTEMA.
    // `pendingConsentId` recuerda la pista mientras el sistema pide su confirmación (IntentSender).
    var deleteTarget by remember { mutableStateOf<LibraryEntry?>(null) }
    var pendingConsentId by remember { mutableStateOf<Long?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val id = pendingConsentId
        pendingConsentId = null
        // Solo si el usuario aprobó en el sistema; si canceló, no se hace nada (UI ya limpia).
        if (id != null && result.resultCode == Activity.RESULT_OK) onCompleteDelete(id)
    }

    // LazyColumn persistente: solo compone (y pide carátula a Coil) las filas VISIBLES; keys estables
    // por id + `contentType` único para reciclar slots al cambiar de chip (sin recargar arte). El
    // vacío es un item de la MISMA lista (no desmonta la LazyColumn ni rompe el pull-to-refresh). El
    // stagger corre una vez, solo en la primera pantalla visible; el scroll queda instantáneo.
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
                        menuItems = if (songMenu) {
                            songMenuItems(
                                onPlay = { onEntryClick(entry) },
                                onQueue = {
                                    onAddToQueue(entry.id)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.library_queued),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                onAddToPlaylist = { addTarget = entry.id },
                                onDelete = { deleteTarget = entry },
                            )
                        } else {
                            emptyList()
                        },
                        isCurrent = highlightId != null && entry.id == highlightId,
                        isPlaying = nowPlaying.isPlaying,
                    )
                }
            }
        }
    }

    addTarget?.let { songId ->
        AddToPlaylistSheet(songId = songId, onDismiss = { addTarget = null })
    }

    // Confirmación propia (destructiva) ANTES de lanzar la del sistema: deja claro que borra el ARCHIVO.
    deleteTarget?.let { target ->
        ConfirmDeleteSongDialog(
            onConfirm = {
                deleteTarget = null
                when (val outcome = onBeginDelete(target.id)) {
                    is DeleteOutcome.NeedsConsent -> {
                        pendingConsentId = target.id
                        deleteLauncher.launch(IntentSenderRequest.Builder(outcome.intentSender).build())
                    }
                    DeleteOutcome.Deleted -> onSongDeleted(target.id) // ruta directa (API < 29)
                    DeleteOutcome.Failed -> Toast.makeText(
                        context,
                        context.getString(R.string.library_delete_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** Confirmación propia del borrado de archivo: copy destructivo explícito, sin bloquear. */
@Composable
private fun ConfirmDeleteSongDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_delete_confirm_title)) },
        text = { Text(stringResource(R.string.library_delete_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Acciones del menú de 3 puntos de una canción, en orden: Reproducir · Añadir a la cola · Añadir a
 * lista · Borrar del dispositivo (destructiva, al final). Reemplaza al antiguo menú placeholder.
 */
private fun songMenuItems(
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
): List<ContextMenuItem> = listOf(
    ContextMenuItem(R.string.library_menu_play, onClick = onPlay),
    ContextMenuItem(R.string.library_menu_add_queue, onClick = onQueue),
    ContextMenuItem(R.string.menu_add_to_playlist, onClick = onAddToPlaylist),
    ContextMenuItem(R.string.library_menu_delete, onClick = onDelete),
)

/** Filas que reciben la entrada escalonada: cubre toda la primera pantalla (~10 filas visibles);
 *  el resto entra instantáneo por scroll, sin re-animar, para que el desplazamiento siga fluido. */
private const val StaggerVisibleCount = 10

@Composable
private fun LibraryTopBar(
    currentSort: LibrarySort,
    onSelectSort: (LibrarySort) -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
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
        // Botón de orden: abre un menú (reutiliza ContextMenu; enter Material desde la esquina) con la
        // opción activa marcada. El orden se aplica a Canciones y se persiste (DataStore).
        Box {
            IconButton(onClick = { sortMenuOpen = true }) {
                Icon(
                    imageVector = Icons.Rounded.FilterList,
                    contentDescription = stringResource(R.string.library_filter),
                    tint = MarluneTheme.colors.textPrimary,
                )
            }
            ContextMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false },
                items = LibrarySort.entries.map { option ->
                    ContextMenuItem(
                        labelRes = option.labelRes,
                        selected = option == currentSort,
                        onClick = { onSelectSort(option) },
                    )
                },
            )
        }
    }
}

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
            currentSort = LibrarySort.TITLE,
            onSelectSort = {},
            contentPadding = PaddingValues(0.dp),
            onRefresh = {},
            onPlaySong = {},
            onAddToQueue = {},
            onBeginDelete = { DeleteOutcome.Failed },
            onSongDeleted = {},
            onCompleteDelete = {},
            onOpenAlbum = {},
            onOpenArtist = {},
            onOpenPlaylist = {},
            onCreatePlaylist = {},
            onRenamePlaylist = { _, _ -> },
            onDeletePlaylist = {},
        )
    }
}
