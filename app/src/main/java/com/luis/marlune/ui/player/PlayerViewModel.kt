package com.luis.marlune.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luis.marlune.data.repository.FavoritesRepository
import com.luis.marlune.data.repository.LibraryState
import com.luis.marlune.data.repository.LyricsRepository
import com.luis.marlune.data.repository.MusicRepository
import com.luis.marlune.domain.model.LyricLine
import com.luis.marlune.domain.model.Lyrics
import com.luis.marlune.domain.model.Song
import com.luis.marlune.playback.PlaybackRepository
import com.luis.marlune.playback.PlaybackState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
) : ViewModel() {

    // Id de la pista actual, para alternar su favorito.
    private var currentSongId: Long? = null

    val uiState: StateFlow<PlayerUiState> =
        combine(playback.state, favorites.favoriteIds) { state, favIds -> state.toUiState(favIds) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState.Empty)

    // --- Letras (100% local) ---
    private data class LyricsLoad(val loading: Boolean, val lyrics: Lyrics?)

    // Resuelve la letra al cambiar de pista O al conceder carpeta; emite "cargando" mientras va a IO.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val loadedLyrics: Flow<LyricsLoad> =
        combine(
            playback.state.map { it.mediaId }.distinctUntilChanged(),
            lyrics.hasFolder.distinctUntilChanged(),
        ) { mediaId, hasFolder -> mediaId to hasFolder }
            .distinctUntilChanged()
            .transformLatest { (mediaId, _) ->
                emit(LyricsLoad(loading = true, lyrics = null))
                val song = songFor(mediaId)
                emit(LyricsLoad(loading = false, lyrics = song?.let { lyrics.lyricsFor(it) }))
            }

    /** Estado de la vista de letras: la línea activa (sincronizada) sigue la posición del player. */
    val lyricsState: StateFlow<LyricsUiState> =
        combine(loadedLyrics, playback.state, lyrics.hasFolder) { load, state, hasFolder ->
            val lrc = load.lyrics
            when {
                load.loading -> LyricsUiState.Loading
                lrc == null -> LyricsUiState.None(canPickFolder = !hasFolder)
                lrc.synced -> LyricsUiState.Synced(lrc.lines, activeIndex(lrc.lines, state.positionMs))
                else -> LyricsUiState.Plain(lrc.lines.map { it.text })
            }
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsUiState.Loading)

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

    /** Persiste la carpeta SAF elegida por el usuario (para leer `.lrc`) y re-resuelve la letra. */
    fun onLyricsFolderPicked(uri: Uri) {
        viewModelScope.launch { lyrics.setFolder(uri) }
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
        ): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory { initializer { PlayerViewModel(playback, favorites, music, lyrics) } }
    }
}
