package com.luis.marlune.ui.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.marlune.R
import com.luis.marlune.data.repository.LibraryState
import com.luis.marlune.di.rememberMusicRepository
import com.luis.marlune.di.rememberPlaylistRepository
import com.luis.marlune.ui.components.EmptyState
import com.luis.marlune.ui.components.LoadingRows
import com.luis.marlune.ui.library.PlaylistsPane
import com.luis.marlune.ui.library.components.CircleCover
import com.luis.marlune.ui.library.components.RoundedCover
import kotlinx.coroutines.launch

/** Lista de álbumes; tocar uno abre su detalle. */
@Composable
fun AlbumsRoute(contentPadding: PaddingValues, onBack: () -> Unit, onOpenAlbum: (Long) -> Unit) {
    val music = rememberMusicRepository()
    val albums by music.albums.collectAsStateWithLifecycle()
    val libraryState by music.library.collectAsStateWithLifecycle()

    DetailScaffold(stringResource(R.string.shortcut_albums), onBack, contentPadding) { bottomPadding ->
        when {
            libraryState is LibraryState.Loading -> LoadingRows()
            albums.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Album,
                title = stringResource(R.string.home_empty_title),
                hint = stringResource(R.string.home_empty_hint),
            )
            else -> EntryList(
                entries = albums.map { it.toLibraryEntry() },
                coverIcon = Icons.Rounded.Album,
                coverShape = RoundedCover,
                bottomPadding = bottomPadding,
                onEntryClick = { entry -> onOpenAlbum(entry.id) },
            )
        }
    }
}

/** Lista de artistas; tocar uno abre su detalle. */
@Composable
fun ArtistsRoute(contentPadding: PaddingValues, onBack: () -> Unit, onOpenArtist: (Long) -> Unit) {
    val music = rememberMusicRepository()
    val artists by music.artists.collectAsStateWithLifecycle()
    val libraryState by music.library.collectAsStateWithLifecycle()

    DetailScaffold(stringResource(R.string.shortcut_artists), onBack, contentPadding) { bottomPadding ->
        when {
            libraryState is LibraryState.Loading -> LoadingRows(circularCover = true)
            artists.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Person,
                title = stringResource(R.string.home_empty_title),
                hint = stringResource(R.string.home_empty_hint),
            )
            else -> EntryList(
                entries = artists.map { it.toLibraryEntry() },
                coverIcon = Icons.Rounded.Person,
                coverShape = CircleCover,
                bottomPadding = bottomPadding,
                onEntryClick = { entry -> onOpenArtist(entry.id) },
            )
        }
    }
}

/**
 * "Listas": las playlists reales del usuario, en paralelo a [AlbumsRoute]/[ArtistsRoute]. Reutiliza la
 * MISMA fuente de datos y el MISMO componente que el panel de Biblioteca ([PlaylistsPane]: mosaico de
 * portada, crear/renombrar/borrar y vacío con acción de crear), sin duplicar lógica. Tocar una lista
 * abre su detalle real ([onOpenPlaylist] → detalle con Reproducir/Aleatorio).
 */
@Composable
fun PlaylistsRoute(contentPadding: PaddingValues, onBack: () -> Unit, onOpenPlaylist: (Long) -> Unit) {
    val playlists = rememberPlaylistRepository()
    val entries by playlists.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    DetailScaffold(stringResource(R.string.shortcut_playlists), onBack, contentPadding) { bottomPadding ->
        PlaylistsPane(
            playlists = entries.map { it.toLibraryEntry() },
            bottomPadding = bottomPadding,
            onCreate = { name -> scope.launch { playlists.create(name) } },
            onRename = { id, name -> scope.launch { playlists.rename(id, name) } },
            onDelete = { id -> scope.launch { playlists.delete(id) } },
            onOpen = onOpenPlaylist,
        )
    }
}

