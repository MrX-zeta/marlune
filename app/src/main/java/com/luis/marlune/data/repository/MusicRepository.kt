package com.luis.marlune.data.repository

import com.luis.marlune.data.mediastore.MediaStoreAudioSource
import com.luis.marlune.domain.model.Album
import com.luis.marlune.domain.model.Artist
import com.luis.marlune.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

/**
 * Estado de la biblioteca local. `Loading` es el arranque (aún no llegó la primera consulta de
 * MediaStore); `Content` ya trae la lista real (que puede estar vacía si no hay música). Permite a
 * la UI distinguir "cargando" de "sin música".
 */
sealed interface LibraryState {
    data object Loading : LibraryState
    data class Content(val songs: List<Song>) : LibraryState
}

/**
 * Única puerta a la biblioteca LOCAL. Comparte un solo Flow de canciones (un solo
 * `ContentObserver`) y deriva de él álbumes, artistas y la búsqueda local. Sin red.
 */
class MusicRepository(
    private val source: MediaStoreAudioSource,
    scope: CoroutineScope,
) {

    /**
     * Biblioteca completa, auto-refrescada por MediaStore. Compartida entre todos los consumidores
     * (un único observer). Empieza en [LibraryState.Loading] hasta la primera consulta.
     */
    val library: StateFlow<LibraryState> = source.observeSongs()
        .map<List<Song>, LibraryState> { LibraryState.Content(it) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), LibraryState.Loading)

    /** Lista de canciones ya cargada (vacía mientras está en Loading). */
    private val songs: Flow<List<Song>> = library.map { state ->
        (state as? LibraryState.Content)?.songs.orEmpty()
    }

    val albums: Flow<List<Album>> = songs.map { list -> deriveAlbums(list) }

    val artists: Flow<List<Artist>> = songs.map { list -> deriveArtists(list) }

    /** Búsqueda LOCAL por título, álbum, artista o género (contains, sin distinguir mayúsculas). */
    fun searchSongs(query: String): Flow<List<Song>> {
        val term = query.trim().lowercase(Locale.getDefault())
        return songs.map { list ->
            if (term.isEmpty()) {
                emptyList()
            } else {
                list.filter { song ->
                    song.title.lowercase(Locale.getDefault()).contains(term) ||
                        song.artist.lowercase(Locale.getDefault()).contains(term) ||
                        song.album.lowercase(Locale.getDefault()).contains(term) ||
                        song.genre?.lowercase(Locale.getDefault())?.contains(term) == true
                }
            }
        }
    }

    /** Refresco manual inmediato (re-consulta MediaStore). */
    fun refresh() {
        source.requestRefresh()
    }

    /** Escaneo manual (red de seguridad): revisa directorios públicos y re-consulta. Suspende. */
    suspend fun rescan() {
        source.rescanPublicMedia()
    }

    private fun deriveAlbums(list: List<Song>): List<Album> =
        list.groupBy { it.albumId }
            .map { (albumId, songs) ->
                val first = songs.first()
                Album(
                    id = albumId,
                    title = first.album,
                    artist = first.artist,
                    artworkUri = first.artworkUri,
                    songCount = songs.size,
                )
            }
            .sortedBy { it.title.lowercase(Locale.getDefault()) }

    private fun deriveArtists(list: List<Song>): List<Artist> =
        list.groupBy { it.artistId }
            .map { (artistId, songs) ->
                Artist(
                    id = artistId,
                    name = songs.first().artist,
                    albumCount = songs.map { it.albumId }.distinct().size,
                    songCount = songs.size,
                )
            }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
}
