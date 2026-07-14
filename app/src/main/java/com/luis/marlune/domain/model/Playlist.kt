package com.luis.marlune.domain.model

/** Una lista de reproducción del usuario, con su conteo de canciones. */
data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int,
)
