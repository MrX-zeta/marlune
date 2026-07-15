package com.luis.marlune.domain.model

import android.net.Uri

/**
 * Una lista de reproducción del usuario, con su conteo de canciones y las carátulas de sus primeras
 * canciones ([covers], hasta 4) para pintar el mosaico de portada.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val covers: List<PlaylistCover> = emptyList(),
)

/** Carátula de una de las primeras canciones de una lista (para el mosaico). [artworkUri] puede ser nula. */
data class PlaylistCover(val songId: Long, val artworkUri: Uri?)
