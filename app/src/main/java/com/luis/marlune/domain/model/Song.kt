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
    /** `DATE_ADDED` de MediaStore (segundos epoch). Ordena "recién añadidas" hasta que exista historial. */
    val dateAdded: Long,
    /** `DISPLAY_NAME` de MediaStore (nombre de archivo, p. ej. "cancion.mp3"). Casa el `.lrc` sidecar. */
    val displayName: String,
    /**
     * `RELATIVE_PATH` de MediaStore (carpeta relativa al volumen, p. ej. "Download/SnapTube Audio/").
     * Deduce la carpeta de la canción para preseleccionarla al pedir acceso SAF. Vacío en API < 29.
     */
    val relativePath: String,
    val contentUri: Uri,
    val artworkUri: Uri?,
    /**
     * Título/artista ORIGINALES tal cual vienen de MediaStore (antes de la limpieza de presentación de
     * [com.luis.marlune.data.SongTitleCleaner]). Se conservan para que la búsqueda encuentre la canción
     * tanto por el nombre limpio como por el sucio. Por defecto vacíos (los rellena el limpiador).
     */
    val rawTitle: String = "",
    val rawArtist: String = "",
)
