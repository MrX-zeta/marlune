package com.luis.marlune.ui.library

import androidx.annotation.StringRes
import com.luis.marlune.R
import com.luis.marlune.domain.model.Song

/**
 * Criterio de orden de la lista de Canciones. El orden es el orden visual del menú de orden.
 * Se persiste por su [name] (clave estable) en DataStore; ver [LibrarySort.fromKey].
 */
enum class LibrarySort(@param:StringRes val labelRes: Int) {
    TITLE(R.string.library_sort_title),
    ARTIST(R.string.library_sort_artist),
    ALBUM(R.string.library_sort_album),
    DATE_ADDED(R.string.library_sort_date),
    DURATION(R.string.library_sort_duration);

    companion object {
        val Default = TITLE

        /** Mapea la clave persistida (o nula/desconocida → [Default]). */
        fun fromKey(key: String?): LibrarySort = entries.firstOrNull { it.name == key } ?: Default
    }
}

/**
 * Ordena las canciones según el criterio. Se invoca desde el ViewModel dentro de `Dispatchers.Default`
 * (sobre la lista YA cacheada), nunca en el hilo principal ni re-consultando MediaStore.
 */
fun LibrarySort.sortSongs(songs: List<Song>): List<Song> = when (this) {
    LibrarySort.TITLE -> songs.sortedBy { it.title.lowercase() }
    LibrarySort.ARTIST -> songs.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
    LibrarySort.ALBUM -> songs.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber }))
    LibrarySort.DATE_ADDED -> songs.sortedByDescending { it.dateAdded } // más reciente primero
    LibrarySort.DURATION -> songs.sortedBy { it.durationMs }
}
