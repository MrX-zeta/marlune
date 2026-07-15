package com.luis.marlune.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luis.marlune.data.datastore.LibrarySortStore
import com.luis.marlune.data.mediastore.MediaStoreAudioSource.DeleteOutcome
import com.luis.marlune.data.repository.FavoritesRepository
import com.luis.marlune.data.repository.HistoryRepository
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
import kotlinx.coroutines.withContext

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
    private val favorites: FavoritesRepository,
    private val history: HistoryRepository,
    private val sortStore: LibrarySortStore,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)

    /** Orden activo de Canciones (persistido). La UI lo lee para marcar la opción y para el menú. */
    val sort: StateFlow<LibrarySort> =
        sortStore.sortKey
            .map { LibrarySort.fromKey(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibrarySort.Default)

    /**
     * Entradas por categoría + la lista de Canciones YA ordenada, precalculadas fuera del hilo
     * principal. La misma lista ordenada alimenta la UI y la cola de reproducción, para que "Reproducir"
     * respete el orden mostrado.
     */
    private data class Entries(
        val byFilter: Map<LibraryFilter, List<LibraryEntry>>,
        val sortedSongs: List<Song>,
        val isLoading: Boolean,
    )

    private val entries: StateFlow<Entries> =
        combine(
            repository.library,
            repository.albums,
            repository.artists,
            playlists.playlists,
            sort,
        ) { library, albums, artists, playlists, sort ->
            val rawSongs = (library as? LibraryState.Content)?.songs.orEmpty()
            val sortedSongs = sort.sortSongs(rawSongs)
            Entries(
                byFilter = mapOf(
                    LibraryFilter.PLAYLISTS to playlists.map { it.toEntry() },
                    LibraryFilter.ALBUMS to albums.map { it.toEntry() },
                    LibraryFilter.ARTISTS to artists.map { it.toEntry() },
                    LibraryFilter.SONGS to sortedSongs.map { it.toEntry() },
                ),
                sortedSongs = sortedSongs,
                isLoading = library is LibraryState.Loading,
            )
        }.flowOn(Dispatchers.Default) // agrupación + orden + mapeo a UI fuera del hilo principal
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = Entries(emptyMap(), emptyList(), isLoading = true),
            )

    /** Cambia el orden de Canciones y lo persiste; el recálculo ocurre en el flujo `entries`. */
    fun setSort(sort: LibrarySort) {
        viewModelScope.launch { sortStore.setSortKey(sort.name) }
    }

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
        val songs = entries.value.sortedSongs
        val index = songs.indexOfFirst { it.id == entryId }
        if (index >= 0) playback.playSongs(songs, index)
    }

    /**
     * Encola la canción tocada justo después de la actual (sin interrumpir la reproducción); si no hay
     * nada sonando, inicia con ella. Lee la canción de la caché ya cargada, sin re-consultar MediaStore.
     */
    fun addSongToQueue(entryId: Long) {
        val song = entries.value.sortedSongs.firstOrNull { it.id == entryId } ?: return
        playback.addToQueueNext(song)
    }

    // --- Borrado del archivo del dispositivo (solo desde el menú de 3 puntos, con confirmación) ---

    /**
     * Inicia el borrado del archivo. Devuelve el desenlace para que la UI lance la confirmación del
     * SISTEMA ([DeleteOutcome.NeedsConsent]) o finalice si ya se borró ([DeleteOutcome.Deleted]).
     */
    fun beginDelete(entryId: Long): DeleteOutcome = repository.beginDelete(entryId)

    /**
     * Borrado ya efectivo sin paso de consentimiento posterior (ruta directa en API < 29): limpia las
     * referencias y ajusta la reproducción. La biblioteca se refresca sola (ContentObserver).
     */
    fun onSongDeleted(entryId: Long) {
        playback.removeFromQueue(entryId)
        cleanupOrphans(entryId)
    }

    /**
     * Completa el borrado tras el consentimiento del usuario: ejecuta el borrado real donde aplica
     * (API 29), limpia referencias huérfanas en Room y saca la pista de la cola.
     */
    fun completeDelete(entryId: Long) {
        playback.removeFromQueue(entryId)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.completeDelete(entryId) }
            removeReferences(entryId)
        }
    }

    /** Limpia en segundo plano las referencias huérfanas (favoritos, listas, historial). Silencioso. */
    private fun cleanupOrphans(entryId: Long) {
        viewModelScope.launch { removeReferences(entryId) }
    }

    private suspend fun removeReferences(entryId: Long) {
        favorites.removeReference(entryId)
        playlists.removeSongEverywhere(entryId)
        history.removeReference(entryId)
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
        playlistCovers = covers,
    )

    companion object {
        fun factory(
            repository: MusicRepository,
            playback: PlaybackRepository,
            playlists: PlaylistRepository,
            favorites: FavoritesRepository,
            history: HistoryRepository,
            sortStore: LibrarySortStore,
        ): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    LibraryViewModel(repository, playback, playlists, favorites, history, sortStore)
                }
            }
    }
}
