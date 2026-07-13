package com.luis.marlune.domain.model

import android.net.Uri

/**
 * Una pista de la biblioteca LOCAL del dispositivo.
 *
 * `artworkUri` apunta a la carátula incrustada/local (vía MediaStore); no hay origen remoto.
 * La carga real de la carátula y su bitmap la resolverá la capa de datos en una tarea posterior.
 */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long,
    val artworkUri: Uri? = null,
)
