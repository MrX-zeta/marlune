package com.luis.marlune.domain.model

import android.net.Uri

/**
 * Una canción de la biblioteca LOCAL, leída de MediaStore.
 *
 * [id] es el `_ID` de MediaStore (la clave con la que la referencia la persistencia propia).
 * [contentUri] es `content://…/id` para reproducir con ExoPlayer (scoped storage: NUNCA rutas de
 * archivo). [artworkUri] es el content URI de la carátula del álbum (o `null` si no hay).
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val genre: String?,
    val contentUri: Uri,
    val artworkUri: Uri?,
)
