package com.luis.marlune.ui.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.marlune.R
import com.luis.marlune.data.repository.LibraryState
import com.luis.marlune.di.rememberMusicRepository
import com.luis.marlune.di.rememberPlaylistRepository
import com.luis.marlune.ui.components.EmptyState
import com.luis.marlune.ui.components.LoadingRows
import com.luis.marlune.ui.library.components.CircleCover
import com.luis.marlune.ui.library.components.RoundedCover

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

/** "Listas": aún sin playlists (siguiente sub-paso). Estado vacío. */
@Composable
fun PlaylistsRoute(contentPadding: PaddingValues, onBack: () -> Unit) {
    DetailScaffold(stringResource(R.string.shortcut_playlists), onBack, contentPadding) { _ ->
        EmptyState(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            title = stringResource(R.string.playlists_empty_title),
            hint = stringResource(R.string.playlists_empty_hint),
        )
    }
}

/** Detalle de una lista: vacío por ahora (las canciones se añaden en el siguiente sub-paso). */
@Composable
fun PlaylistDetailRoute(playlistId: Long, contentPadding: PaddingValues, onBack: () -> Unit) {
    val playlists = rememberPlaylistRepository()
    val name by playlists.playlistName(playlistId).collectAsStateWithLifecycle(initialValue = null)

    DetailScaffold(name.orEmpty(), onBack, contentPadding) { _ ->
        EmptyState(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            title = stringResource(R.string.playlist_detail_empty_title),
            hint = stringResource(R.string.playlist_detail_empty_hint),
        )
    }
}
