package com.luis.marlune.domain.model

/** Artista derivado de MediaStore agrupando canciones por `ARTIST_ID`. */
data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val songCount: Int,
)
