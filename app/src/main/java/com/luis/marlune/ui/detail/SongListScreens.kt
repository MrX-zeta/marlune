package com.luis.marlune.ui.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luis.marlune.R
import com.luis.marlune.data.repository.LibraryState
import com.luis.marlune.di.rememberFavoritesRepository
import com.luis.marlune.di.rememberHistoryRepository
import com.luis.marlune.di.rememberMusicRepository
import com.luis.marlune.di.rememberPlaybackRepository
import com.luis.marlune.di.rememberPlaylistRepository
import com.luis.marlune.ui.components.EmptyState
import com.luis.marlune.ui.components.LoadingRows
import com.luis.marlune.ui.library.NowPlayingUi
import com.luis.marlune.ui.library.components.RoundedCover
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** "Me gusta": canciones favoritas reales (Room), reproducibles. */
@Composable
fun LikedSongsRoute(contentPadding: PaddingValues, onBack: () -> Unit) {
    val favorites = rememberFavoritesRepository()
    val vm: SongListViewModel = viewModel(
        factory = SongListViewModel.factory(
            rememberPlaybackRepository(),
            rememberMusicRepository(),
            remember { favorites.favoriteSongs },
        ),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()

    DetailScaffold(stringResource(R.string.shortcut_liked), onBack, contentPadding) { bottomPadding ->
        SongListBody(
            state = state,
            nowPlaying = nowPlaying,
            bottomPadding = bottomPadding,
            emptyIcon = Icons.Rounded.FavoriteBorder,
            emptyTitle = stringResource(R.string.liked_empty_title),
            emptyHint = stringResource(R.string.liked_empty_hint),
            onPlay = vm::play,
        )
    }
}

/** "Escuchado hace poco": historial completo real (Room), deduplicado, más reciente arriba. */
@Composable
fun HistoryRoute(contentPadding: PaddingValues, onBack: () -> Unit) {
    val history = rememberHistoryRepository()
    val vm: SongListViewModel = viewModel(
        factory = SongListViewModel.factory(
            rememberPlaybackRepository(),
            rememberMusicRepository(),
            remember { history.recentlyPlayed },
        ),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()

    DetailScaffold(stringResource(R.string.home_section_recent), onBack, contentPadding) { bottomPadding ->
        SongListBody(
            state = state,
            nowPlaying = nowPlaying,
            bottomPadding = bottomPadding,
            emptyIcon = Icons.Rounded.History,
            emptyTitle = stringResource(R.string.history_empty_title),
            emptyHint = stringResource(R.string.home_recent_empty),
            onPlay = vm::play,
        )
    }
}

/** "Añadidas recientemente": canciones de la biblioteca por `DATE_ADDED` (más reciente arriba). */
@Composable
fun RecentlyAddedRoute(contentPadding: PaddingValues, onBack: () -> Unit) {
    val music = rememberMusicRepository()
    val songsFlow = remember {
        music.library.map { state ->
            (state as? LibraryState.Content)?.songs?.sortedByDescending { it.dateAdded }.orEmpty()
        }
    }
    val vm: SongListViewModel = viewModel(
        factory = SongListViewModel.factory(rememberPlaybackRepository(), music, songsFlow),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()

    DetailScaffold(stringResource(R.string.shortcut_recently_added), onBack, contentPadding) { bottomPadding ->
        SongListBody(
            state = state,
            nowPlaying = nowPlaying,
            bottomPadding = bottomPadding,
            emptyIcon = Icons.Rounded.MusicNote,
            emptyTitle = stringResource(R.string.home_empty_title),
            emptyHint = stringResource(R.string.home_empty_hint),
            onPlay = vm::play,
        )
    }
}

/** Detalle de álbum: sus canciones, reproducibles. El título es el nombre del álbum. */
@Composable
fun AlbumDetailRoute(albumId: Long, contentPadding: PaddingValues, onBack: () -> Unit) {
    val music = rememberMusicRepository()
    val songsFlow = remember(albumId) {
        music.library.map { state ->
            (state as? LibraryState.Content)?.songs?.filter { it.albumId == albumId }.orEmpty()
        }
    }
    val vm: SongListViewModel = viewModel(
        factory = SongListViewModel.factory(rememberPlaybackRepository(), music, songsFlow),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val title = state.songs.firstOrNull()?.album.orEmpty()

    // El nombre vive en la cabecera desplazable; la barra deja solo el retroceso.
    DetailScaffold("", onBack, contentPadding) { bottomPadding ->
        SongListBody(
            state, nowPlaying, bottomPadding, Icons.Rounded.MusicNote, title, "", vm::play,
            header = {
                DetailHeader(
                    covers = headerCovers(state.songs, max = 1), // álbum: una sola carátula
                    coverFallbackKey = albumId,
                    title = title,
                    songCount = state.songs.size,
                    onPlay = { vm.play(0) },
                    onShuffle = vm::playShuffled,
                )
            },
        )
    }
}

/** Detalle de artista: sus canciones, reproducibles. El título es el nombre del artista. */
@Composable
fun ArtistDetailRoute(artistId: Long, contentPadding: PaddingValues, onBack: () -> Unit) {
    val music = rememberMusicRepository()
    val songsFlow = remember(artistId) {
        music.library.map { state ->
            (state as? LibraryState.Content)?.songs?.filter { it.artistId == artistId }.orEmpty()
        }
    }
    val vm: SongListViewModel = viewModel(
        factory = SongListViewModel.factory(rememberPlaybackRepository(), music, songsFlow),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val title = state.songs.firstOrNull()?.artist.orEmpty()

    DetailScaffold("", onBack, contentPadding) { bottomPadding ->
        SongListBody(
            state, nowPlaying, bottomPadding, Icons.Rounded.MusicNote, title, "", vm::play,
            header = {
                DetailHeader(
                    covers = headerCovers(state.songs, max = 4), // artista: mosaico de sus canciones
                    coverFallbackKey = artistId,
                    title = title,
                    songCount = state.songs.size,
                    onPlay = { vm.play(0) },
                    onShuffle = vm::playShuffled,
                )
            },
        )
    }
}

/** Detalle de una lista: sus canciones en orden, reproducibles, con "Quitar de la lista". */
@Composable
fun PlaylistDetailRoute(playlistId: Long, contentPadding: PaddingValues, onBack: () -> Unit) {
    val playlists = rememberPlaylistRepository()
    val music = rememberMusicRepository()
    val name by playlists.playlistName(playlistId).collectAsStateWithLifecycle(initialValue = null)
    val songsFlow = remember(playlistId) { playlists.playlistSongs(playlistId) }
    val vm: SongListViewModel = viewModel(
        factory = SongListViewModel.factory(rememberPlaybackRepository(), music, songsFlow),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showAddSongs by remember { mutableStateOf(false) }

    DetailScaffold("", onBack, contentPadding) { bottomPadding ->
        SongListBody(
            state = state,
            nowPlaying = nowPlaying,
            bottomPadding = bottomPadding,
            emptyIcon = Icons.AutoMirrored.Rounded.QueueMusic,
            emptyTitle = stringResource(R.string.playlist_detail_empty_title),
            emptyHint = stringResource(R.string.playlist_detail_empty_hint),
            onPlay = vm::play,
            onRemoveFromPlaylist = { songId -> scope.launch { playlists.removeSong(playlistId, songId) } },
            header = {
                DetailHeader(
                    covers = headerCovers(state.songs, max = 4),
                    coverFallbackKey = playlistId,
                    title = name.orEmpty(),
                    songCount = state.songs.size,
                    onPlay = { vm.play(0) },
                    onShuffle = vm::playShuffled,
                    onAddSongs = { showAddSongs = true },
                )
            },
            onReorder = { orderedIds -> scope.launch { playlists.reorderSongs(playlistId, orderedIds) } },
            // Lista vacía: la cabecera (con "+") no se muestra, así que ofrecemos la acción aquí.
            emptyAction = { AddSongsButton(onClick = { showAddSongs = true }) },
        )
    }

    if (showAddSongs) {
        AddSongsToPlaylistSheet(playlistId = playlistId, onDismiss = { showAddSongs = false })
    }
}

/** Cuerpo común de las listas de canciones: carga (shimmer) / vacío / lista reutilizando la fila. */
@Composable
private fun SongListBody(
    state: SongListState,
    nowPlaying: NowPlayingUi,
    bottomPadding: Dp,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyTitle: String,
    emptyHint: String,
    onPlay: (Int) -> Unit,
    onRemoveFromPlaylist: ((Long) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    onReorder: ((List<Long>) -> Unit)? = null,
    emptyAction: (@Composable () -> Unit)? = null,
) {
    when {
        state.isLoading -> LoadingRows()
        state.songs.isEmpty() -> EmptyState(icon = emptyIcon, title = emptyTitle, hint = emptyHint, action = emptyAction)
        else -> EntryList(
            entries = state.songs.map { it.toLibraryEntry() },
            coverIcon = Icons.Rounded.MusicNote,
            coverShape = RoundedCover,
            bottomPadding = bottomPadding,
            nowPlayingId = nowPlaying.songId,
            isPlaying = nowPlaying.isPlaying,
            enableAddToPlaylist = true,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            onEntryClick = { entry -> onPlay(state.songs.indexOfFirst { it.id == entry.id }) },
            header = header,
            onReorder = onReorder,
        )
    }
}

/** Construye las carátulas del mosaico de cabecera a partir de las primeras canciones. */
private fun headerCovers(songs: List<com.luis.marlune.domain.model.Song>, max: Int) =
    songs.take(max).map { com.luis.marlune.domain.model.PlaylistCover(it.id, it.artworkUri) }
