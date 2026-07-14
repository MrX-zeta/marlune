package com.luis.marlune.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luis.marlune.data.repository.LibraryState
import com.luis.marlune.data.repository.MusicRepository
import com.luis.marlune.domain.model.Song
import com.luis.marlune.playback.PlaybackRepository
import com.luis.marlune.ui.library.NowPlayingUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Estado de una lista de canciones de detalle. `isLoading` = biblioteca aún cargando. */
data class SongListState(
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * ViewModel genérico de listas de canciones (Me gusta, historial, detalle de álbum/artista). Recibe
 * el [Flow] de canciones ya resuelto (contra la biblioteca real) y añade el estado de carga, el
 * resaltado de la pista actual (reactivo al MediaController) y la reproducción con cola real.
 */
class SongListViewModel(
    private val playback: PlaybackRepository,
    music: MusicRepository,
    songs: Flow<List<Song>>,
) : ViewModel() {

    val state: StateFlow<SongListState> =
        combine(music.library, songs) { libraryState, list ->
            SongListState(songs = list, isLoading = libraryState is LibraryState.Loading)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongListState())

    val nowPlaying: StateFlow<NowPlayingUi> =
        playback.state
            .map { NowPlayingUi(songId = if (it.hasItem) it.mediaId?.toLongOrNull() else null, isPlaying = it.isPlaying) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlayingUi(null, false))

    /** Reproduce la lista actual como cola, empezando en [index]. */
    fun play(index: Int) {
        if (index >= 0) playback.playSongs(state.value.songs, index)
    }

    companion object {
        fun factory(
            playback: PlaybackRepository,
            music: MusicRepository,
            songs: Flow<List<Song>>,
        ): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory { initializer { SongListViewModel(playback, music, songs) } }
    }
}
