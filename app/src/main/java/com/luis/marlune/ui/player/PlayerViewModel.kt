package com.luis.marlune.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luis.marlune.data.datastore.SettingsStore
import com.luis.marlune.data.repository.AddFolderResult
import com.luis.marlune.data.repository.FavoritesRepository
import com.luis.marlune.data.repository.LibraryState
import com.luis.marlune.data.repository.LyricsRepository
import com.luis.marlune.data.repository.LyricsResolution
import com.luis.marlune.data.repository.MusicRepository
import com.luis.marlune.domain.model.LyricLine
import com.luis.marlune.domain.model.Lyrics
import com.luis.marlune.domain.model.LyricsFolderRequest
import com.luis.marlune.domain.model.Song
import com.luis.marlune.playback.PlaybackRepository
import com.luis.marlune.playback.PlaybackState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

private const val LibrarySource = "Tu biblioteca"

/**
 * ViewModel del Reproductor: proyecta la reproducción REAL ([PlaybackRepository]) al estado de la
 * UI y traduce los [PlayerEvent] a comandos sobre el `MediaController`. Sin datos mock, sin ticker
 * propio (la posición viene del repositorio).
 *
 * La dirección de la transición de carátula la aporta el repositorio (reason de Media3). El "me
 * gusta" es PERSISTENTE (Room, [FavoritesRepository]): el corazón refleja el estado real de la pista
 * actual y lo alterna guardándolo.
 */
class PlayerViewModel(
    private val playback: PlaybackRepository,
    private val favorites: FavoritesRepository,
    private val music: MusicRepository,
    private val lyrics: LyricsRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    /** Opt-in de letras por internet (para el enlace de descubrimiento en el panel vacío). */
    val internetLyricsEnabled: StateFlow<Boolean> =
        settings.internetLyrics.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Id de la pista actual, para alternar su favorito.
    private var currentSongId: Long? = null

    val uiState: StateFlow<PlayerUiState> =
        combine(playback.state, favorites.favoriteIds) { state, favIds -> state.toUiState(favIds) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState.Empty)

    /** Cola real ("A continuación"), reactiva a añadir/quitar/mover/auto-avance. */
    val queue: StateFlow<QueueUiState> =
        combine(playback.queue, playback.state) { items, state ->
            QueueUiState(items = items, currentIndex = state.currentIndex, isPlaying = state.isPlaying)
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QueueUiState.Empty)

    // --- Letras (local + red opt-in) ---
    private data class LyricsLoad(
        val loading: Boolean,
        val resolution: LyricsResolution?,
        val request: LyricsFolderRequest?,
    )

    // Claves que fuerzan re-resolver: pista, carpetas concedidas, ajuste de red y la señal de borrado
    // de caché (para que al borrar la letra descargada la UI deje de mostrarla).
    private data class LyricsKey(
        val mediaId: String?,
        val folders: Set<android.net.Uri>,
        val net: Boolean,
        val cacheInvalidation: Int,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val loadedLyrics: Flow<LyricsLoad> =
        combine(
            playback.state.map { it.mediaId }.distinctUntilChanged(),
            lyrics.grantedFolders.distinctUntilChanged(),
            settings.internetLyrics.distinctUntilChanged(),
            lyrics.cacheInvalidations,
        ) { mediaId, folders, net, inv -> LyricsKey(mediaId, folders, net, inv) }
            .distinctUntilChanged()
            .transformLatest { key ->
                emit(LyricsLoad(loading = true, resolution = null, request = null))
                val song = songFor(key.mediaId)
                if (song == null) {
                    emit(LyricsLoad(false, LyricsResolution.NotFound, null))
                    return@transformLatest
                }
                val res = lyrics.resolve(song, allowNetwork = key.net)
                val request = if (res is LyricsResolution.NotFound) lyrics.folderRequestFor(song) else null
                emit(LyricsLoad(false, res, request))
            }

    /** Estado de la vista de letras: la línea activa (sincronizada) sigue la posición del player. */
    val lyricsState: StateFlow<LyricsUiState> =
        combine(loadedLyrics, playback.state) { load, state ->
            val res = load.resolution
            when {
                load.loading -> LyricsUiState.Loading
                res is LyricsResolution.Found && res.lyrics.synced ->
                    LyricsUiState.Synced(res.lyrics.lines, activeIndex(res.lyrics.lines, state.positionMs))
                res is LyricsResolution.Found -> LyricsUiState.Plain(res.lyrics.lines.map { it.text })
                res is LyricsResolution.NoConnection -> LyricsUiState.Error(offline = true)
                res is LyricsResolution.ServiceError -> LyricsUiState.Error(offline = false)
                else -> LyricsUiState.None(request = load.request)
            }
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsUiState.Loading)

    // Error transitorio: el usuario eligió una carpeta que no contiene la canción actual. Se olvida
    // al cambiar de pista.
    private val _folderError = MutableStateFlow(false)
    val folderError: StateFlow<Boolean> = _folderError.asStateFlow()

    init {
        viewModelScope.launch {
            playback.state.map { it.mediaId }.distinctUntilChanged().collect { _folderError.value = false }
        }
    }

    fun onEvent(event: PlayerEvent) {
        when (event) {
            PlayerEvent.PlayPause -> playback.playPause()
            PlayerEvent.Next -> playback.next()
            PlayerEvent.Previous -> playback.previous()
            PlayerEvent.ToggleShuffle -> playback.toggleShuffle()
            PlayerEvent.ToggleRepeat -> playback.cycleRepeat()
            PlayerEvent.ToggleLike -> toggleLike()
            is PlayerEvent.SeekTo -> playback.seekTo(event.positionMs)
        }
    }

    private fun toggleLike() {
        val id = currentSongId ?: return
        viewModelScope.launch { favorites.toggle(id) }
    }

    /** Salta a la pista [index] de la cola (sin rearmarla). */
    fun playQueueItem(index: Int) = playback.playQueueItem(index)

    /** Quita la pista [index] de la cola (no toca biblioteca ni archivos). */
    fun removeQueueItem(index: Int) = playback.removeQueueItem(index)

    /** Mueve la pista de [from] a [to] en la cola (reordena la reproducción en vivo). */
    fun moveQueueItem(from: Int, to: Int) = playback.moveQueueItem(from, to)

    /**
     * Concede la carpeta SAF elegida en el contexto de la canción actual. Si esa carpeta NO contiene
     * la canción, marca el error (la UI ofrece reintentar) y NO la persiste. Si la contiene, se guarda
     * (multi-carpeta) y la letra se re-resuelve al cambiar el conjunto de carpetas.
     */
    fun onLyricsFolderPicked(uri: Uri) {
        viewModelScope.launch {
            val song = songFor(playback.state.value.mediaId)
            _folderError.value = lyrics.addFolderForSong(uri, song) == AddFolderResult.WRONG_FOLDER
        }
    }

    private fun songFor(mediaId: String?): Song? =
        mediaId?.toLongOrNull()?.let { id ->
            (music.library.value as? LibraryState.Content)?.songs?.firstOrNull { it.id == id }
        }

    /** Última línea cuya marca de tiempo ya pasó (-1 si la canción aún no llegó a la primera). */
    private fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var idx = -1
        for (i in lines.indices) {
            val t = lines[i].timeMs ?: continue
            if (t <= positionMs) idx = i else break
        }
        return idx
    }

    private fun PlaybackState.toUiState(favoriteIds: Set<Long>): PlayerUiState {
        if (!hasItem) {
            currentSongId = null
            return PlayerUiState.Empty
        }
        val songId = mediaId?.toLongOrNull()
        currentSongId = songId
        return PlayerUiState(
            hasTrack = true,
            title = title,
            artist = artist,
            source = LibrarySource,
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            isShuffleOn = shuffle,
            repeatMode = repeatMode,
            isLiked = songId != null && songId in favoriteIds,
            hasNext = hasNext,
            hasPrevious = hasPrevious,
            artworkUri = artworkUri,
            // La dirección de la animación es la única fuente del repositorio (reason de Media3).
            trackTransition = TrackTransition(transitionId, transition),
        )
    }

    companion object {
        fun factory(
            playback: PlaybackRepository,
            favorites: FavoritesRepository,
            music: MusicRepository,
            lyrics: LyricsRepository,
            settings: SettingsStore,
        ): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory { initializer { PlayerViewModel(playback, favorites, music, lyrics, settings) } }
    }
}
