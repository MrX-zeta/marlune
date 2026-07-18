package com.luis.marlune.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luis.marlune.ui.components.StaggeredReveal
import com.luis.marlune.ui.components.rememberDragReorderState
import com.luis.marlune.ui.components.reorderableItem
import com.luis.marlune.R
import com.luis.marlune.domain.model.Album
import com.luis.marlune.domain.model.Artist
import com.luis.marlune.domain.model.Playlist
import com.luis.marlune.domain.model.Song
import com.luis.marlune.ui.components.ContextMenuItem
import com.luis.marlune.ui.library.AddToPlaylistSheet
import com.luis.marlune.ui.library.LibraryEntry
import com.luis.marlune.ui.library.components.LibraryRow
import com.luis.marlune.ui.theme.MarluneTheme

/** Filas que reciben la entrada escalonada en la primera pantalla; el resto entra instantáneo. */
private const val StaggerVisibleCount = 10

/**
 * Andamiaje común de una pantalla de detalle: barra superior con retroceso + título, y el contenido
 * bajo ella. [content] recibe el padding inferior ya calculado (inset del mini-player/barra + respiro)
 * para que la última fila no quede cortada, igual que en Biblioteca.
 */
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable (bottomPadding: Dp) -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back),
                        tint = MarluneTheme.colors.textPrimary,
                    )
                }
                Text(
                    text = title,
                    style = MarluneTheme.typography.headlineSmall,
                    color = MarluneTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content(contentPadding.calculateBottomPadding() + 24.dp)
            }
        }
    }
}

/**
 * Lista de detalle: reutiliza [LibraryRow] (misma fila que Biblioteca) en una `LazyColumn` perezosa
 * con keys estables, `contentType`, mismo espaciado y respiro inferior. [nowPlayingId] resalta la
 * fila que suena (título en acento + ecualizador). Sin menú de "3 puntos" en detalle (lista vacía).
 */
@Composable
fun EntryList(
    entries: List<LibraryEntry>,
    coverIcon: ImageVector,
    coverShape: Shape,
    bottomPadding: Dp,
    onEntryClick: (LibraryEntry) -> Unit,
    modifier: Modifier = Modifier,
    nowPlayingId: Long? = null,
    isPlaying: Boolean = false,
    // Solo para listas de CANCIONES: "Añadir a lista" y (en el detalle de una lista) "Quitar".
    enableAddToPlaylist: Boolean = false,
    onRemoveFromPlaylist: ((Long) -> Unit)? = null,
    // Cabecera opcional que se desplaza con la lista (primer item), p. ej. el detalle de una lista.
    header: (@Composable () -> Unit)? = null,
    // Si no es nulo, se habilita el reordenado por arrastre (mantener pulsado). Persiste el nuevo orden
    // de ids. Solo el detalle de una LISTA lo pasa; el resto de detalles no reordena.
    onReorder: ((List<Long>) -> Unit)? = null,
) {
    var addTarget by remember { mutableStateOf<Long?>(null) }
    val reorderable = onReorder != null
    val listState = rememberLazyListState()
    val reorderState = rememberDragReorderState(listState)

    // Orden local durante el arrastre (optimista); se re-sincroniza con `entries` cuando NO se arrastra.
    val items = remember { entries.toMutableStateList() }
    LaunchedEffect(entries) {
        if (reorderState.draggedKey == null) {
            items.clear()
            items.addAll(entries)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (header != null) {
            item(key = "detail-header", contentType = "detailHeader") { header() }
        }
        itemsIndexed(items, key = { _, e -> e.id }, contentType = { _, _ -> "libraryRow" }) { index, entry ->
            val menu = buildList {
                if (enableAddToPlaylist) {
                    add(ContextMenuItem(R.string.menu_add_to_playlist) { addTarget = entry.id })
                }
                if (onRemoveFromPlaylist != null) {
                    add(ContextMenuItem(R.string.menu_remove_from_playlist) { onRemoveFromPlaylist(entry.id) })
                }
            }
            val rowModifier = if (reorderable) {
                // Al soltar persiste el ORDEN completo en Room (no toca la cola en curso).
                reorderableItem(
                    state = reorderState,
                    item = entry,
                    items = items,
                    keyOf = { it.id },
                    enabled = true,
                    onSettle = { _, _, _ -> onReorder?.invoke(items.map { it.id }) },
                )
            } else {
                Modifier
            }
            StaggeredReveal(
                index = index,
                enabled = !reorderable && index < StaggerVisibleCount,
                modifier = rowModifier,
            ) {
                LibraryRow(
                    entry = entry,
                    coverIcon = coverIcon,
                    coverShape = coverShape,
                    onClick = { onEntryClick(entry) },
                    menuItems = menu,
                    isCurrent = nowPlayingId != null && entry.id == nowPlayingId,
                    isPlaying = isPlaying,
                )
            }
        }
    }

    addTarget?.let { songId ->
        AddToPlaylistSheet(songId = songId, onDismiss = { addTarget = null })
    }
}

/**
 * Cabecera de detalle (lista/álbum/artista): mosaico grande + nombre + conteo + acciones Reproducir /
 * Aleatorio. Se desplaza con la lista (no fija). Sin acciones no aplica: solo se muestra con canciones.
 */
@Composable
fun DetailHeader(
    covers: List<com.luis.marlune.domain.model.PlaylistCover>,
    coverFallbackKey: Long,
    title: String,
    songCount: Int,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    // Solo el detalle de una LISTA lo aporta: añade canciones a la lista en cualquier momento.
    onAddSongs: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        com.luis.marlune.ui.library.components.PlaylistMosaicCover(
            covers = covers,
            modifier = Modifier.size(168.dp),
            fallbackKey = coverFallbackKey,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MarluneTheme.typography.headlineSmall,
            color = MarluneTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
        Text(
            text = "$songCount ${if (songCount == 1) "canción" else "canciones"}",
            style = MarluneTheme.typography.bodyMedium,
            color = MarluneTheme.colors.textSecondary,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.Button(
                onClick = onPlay,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MarluneTheme.colors.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.player_play))
            }
            androidx.compose.material3.Button(
                onClick = onShuffle,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MarluneTheme.colors.surfaceElevated,
                    contentColor = MarluneTheme.colors.textPrimary,
                ),
            ) {
                Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.player_shuffle))
            }
            // "+" para añadir canciones (solo listas): misma fila, botón circular en superficie elevada.
            if (onAddSongs != null) {
                androidx.compose.material3.FilledIconButton(
                    onClick = onAddSongs,
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = MarluneTheme.colors.surfaceElevated,
                        contentColor = MarluneTheme.colors.accent,
                    ),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.playlist_add_songs))
                }
            }
        }
    }
}

fun Song.toLibraryEntry(): LibraryEntry =
    LibraryEntry(id = id, title = title, subtitle = artist, artworkUri = artworkUri)

fun Album.toLibraryEntry(): LibraryEntry =
    LibraryEntry(id = id, title = title, subtitle = artist, artworkUri = artworkUri)

fun Artist.toLibraryEntry(): LibraryEntry =
    LibraryEntry(
        id = id,
        title = name,
        subtitle = "$songCount ${if (songCount == 1) "canción" else "canciones"}",
        artworkUri = null,
    )

fun Playlist.toLibraryEntry(): LibraryEntry =
    LibraryEntry(
        id = id,
        title = name,
        subtitle = "$songCount ${if (songCount == 1) "canción" else "canciones"}",
        artworkUri = null,
        playlistCovers = covers,
    )
