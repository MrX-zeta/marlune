package com.luis.marlune.ui.library

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.luis.marlune.R

/** Categorías del filtro de Biblioteca. El orden es el orden visual de los chips. */
enum class LibraryFilter(@param:StringRes val labelRes: Int) {
    PLAYLISTS(R.string.library_filter_playlists),
    ALBUMS(R.string.library_filter_albums),
    ARTISTS(R.string.library_filter_artists),
    SONGS(R.string.library_filter_songs),
}

/**
 * Entrada genérica de una fila de Biblioteca (lista/álbum/artista/canción).
 * Modelo de presentación derivado de la biblioteca real; `id` es el `_ID`/`ALBUM_ID`/`ARTIST_ID`
 * de MediaStore según la categoría, y `artworkUri` la carátula (nula para artistas o sin arte).
 */
@Immutable
data class LibraryEntry(
    val id: Long,
    val title: String,
    val subtitle: String,
    val artworkUri: Uri? = null,
)

/**
 * Pista que suena ahora, para resaltar su fila en "Canciones". Se observa aparte del mapa de
 * entradas (que no debe recomputarse con la posición); [songId] es el `_ID` de MediaStore o `null`.
 */
@Immutable
data class NowPlayingUi(val songId: Long?, val isPlaying: Boolean)

/**
 * Estado de Biblioteca: entradas por categoría YA precalculadas (biblioteca LOCAL real).
 *
 * El filtro seleccionado NO vive aquí: es estado de UI ligero, propiedad de la pantalla, para que
 * tocar un chip sea instantáneo y desacoplado de esta recomposición. `isLoading` cubre el arranque
 * (shimmer); `isRefreshing`, el escaneo manual (pull-to-refresh).
 */
@Immutable
data class LibraryUiState(
    val entriesByFilter: Map<LibraryFilter, List<LibraryEntry>>,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
) {
    /** Entradas de una categoría (lectura pura del mapa precalculado; sin derivar ni consultar). */
    fun entriesFor(filter: LibraryFilter): List<LibraryEntry> = entriesByFilter[filter].orEmpty()

    /** Vacío para una categoría concreta (ya cargado y sin entradas). */
    fun isEmptyFor(filter: LibraryFilter): Boolean = !isLoading && entriesFor(filter).isEmpty()
}
