package com.luis.marlune.domain.model

import android.net.Uri

/** Álbum derivado de MediaStore agrupando canciones por `ALBUM_ID`. */
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val songCount: Int,
)
