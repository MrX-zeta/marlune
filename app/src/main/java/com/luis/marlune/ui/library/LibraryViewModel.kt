package com.luis.marlune.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luis.marlune.data.repository.LibraryState
import com.luis.marlune.data.repository.MusicRepository
import com.luis.marlune.data.repository.PlaylistRepository
import com.luis.marlune.domain.model.Album
import com.luis.marlune.domain.model.Artist
import com.luis.marlune.domain.model.Playlist
import com.luis.marlune.domain.model.Song
import com.luis.marlune.playback.PlaybackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de Biblioteca: proyecta la biblioteca LOCAL real ([MusicRepository]) por categoría.
 *
 * Rendimiento: las entradas por categoría (Álbumes/Artistas/Canciones → [LibraryEntry]) se derivan
 * UNA sola vez por cambio de biblioteca, en `Dispatchers.Default`, y quedan cacheadas ([entries]).
 * Cambiar de chip solo cambia QUÉ lista cacheada se muestra: no re-consulta MediaStore ni re-agrupa,
 * y no toca el hilo principal. Las Listas llegarán con Room (Fase 3). Sin mocks, sin red.
 */
class LibraryViewModel(
    private val repository: MusicRepository,
    private val playback: PlaybackRepository,
    private val playlists: PlaylistRepository,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)

    /** Entradas por categoría, precalculadas fuera del hilo principal y memorizadas. */
    private data class Entries(
        val byFilter: Map<LibraryFilter, List<LibraryEntry>>,
        val isLoading: Boolean,
    )

    private val entries: StateFlow<Entries> =
        combine(
            repository.library,
            repository.albums,
            repository.artists,
            playlists.playlists,
        ) { library, albums, artists, playlists ->
            val songs = (library as? LibraryState.Content)?.songs.orEmpty()
            Entries(
                byFilter = mapOf(
                    LibraryFilter.PLAYLISTS to playlists.map { it.toEntry() },
                    LibraryFilter.ALBUMS to albums.map { it.toEntry() },
                    LibraryFilter.ARTISTS to artists.map { it.toEntry() },
                    LibraryFilter.SONGS to songs.map { it.toEntry() },
                ),
                isLoading = library is LibraryState.Loading,
            )
        }.flowOn(Dispatchers.Default) // agrupación + mapeo a UI fuera del hilo principal
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = Entries(emptyMap(), isLoading = true),
            )

    // El mapa ya está precalculado; solo se re-empaqueta con el flag de refresco (barato). El chip
    // seleccionado lo maneja la UI, así que cambiarlo no toca este flujo ni recalcula nada.
    val uiState: StateFlow<LibraryUiState> =
        combine(entries, refreshing) { derived, isRefreshing ->
            LibraryUiState(
                entriesByFilter = derived.byFilter,
                isLoading = derived.isLoading,
                isRefreshing = isRefreshing,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(entriesByFilter = emptyMap(), isLoading = true),
        )

    /**
     * Pista actual para resaltar su fila, reactiva al MediaController. `distinctUntilChanged` evita
     * reaccionar a los ticks de posición: solo emite al cambiar de canción o el estado play/pausa.
     */
    val nowPlaying: StateFlow<NowPlayingUi> =
        playback.state
            .map { NowPlayingUi(songId = if (it.hasItem) it.mediaId?.toLongOrNull() else null, isPlaying = it.isPlaying) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlayingUi(null, false))

    /**
     * Reproduce la canción tocada en la pestaña "Canciones": arma la COLA con toda la biblioteca (en
     * el mismo orden mostrado) y empieza en su posición. Lectura directa de la caché ya cargada.
     */
    fun playSongEntry(entryId: Long) {
        val songs = (repository.library.value as? LibraryState.Content)?.songs ?: return
        val index = songs.indexOfFirst { it.id == entryId }
        if (index >= 0) playback.playSongs(songs, index)
    }

    // --- Gestión de listas (crear/renombrar/borrar; las canciones van en el siguiente sub-paso). ---

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { playlists.create(name) }
    }

    fun renamePlaylist(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { playlists.rename(id, name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { playlists.delete(id) }
    }

    /** Escaneo manual: revisa directorios públicos y re-consulta; muestra "refrescando" mientras. */
    fun onRefresh() {
        viewModelScope.launch {
            refreshing.value = true
            try {
                repository.rescan()
            } finally {
                refreshing.value = false
            }
        }
    }

    private fun Song.toEntry() = LibraryEntry(id = id, title = title, subtitle = artist, artworkUri = artworkUri)

    private fun Album.toEntry() = LibraryEntry(id = id, title = title, subtitle = artist, artworkUri = artworkUri)

    private fun Artist.toEntry() = LibraryEntry(
        id = id,
        title = name,
        subtitle = "$songCount ${if (songCount == 1) "canción" else "canciones"}",
        artworkUri = null,
    )

    private fun Playlist.toEntry() = LibraryEntry(
        id = id,
        title = name,
        subtitle = "$songCount ${if (songCount == 1) "canción" else "canciones"}",
        artworkUri = null,
    )

    companion object {
        fun factory(
            repository: MusicRepository,
            playback: PlaybackRepository,
            playlists: PlaylistRepository,
        ): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory { initializer { LibraryViewModel(repository, playback, playlists) } }
    }
}
