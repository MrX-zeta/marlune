package com.luis.marlune.ui.home.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Miniatura de pista: la carátula real (content URI de MediaStore, cargada con Coil + caché) sobre
 * un placeholder teñido con el acento de la pista. Si no hay carátula o falla la carga, queda el
 * degradado del acento con una nota, de modo que la lista se pueda escanear sin muro monocromo.
 * Solo el acento tiñe; la escala neutra no se toca.
 */
@Composable
fun TrackThumbnail(
    accent: Color,
    modifier: Modifier = Modifier,
    artworkUri: Uri? = null,
    size: Dp = 52.dp,
    corner: Dp = 12.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.38f), accent.copy(alpha = 0.16f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size * 0.4f),
        )
        if (artworkUri != null) {
            val context = LocalContext.current
            val key = remember(artworkUri) { artworkUri.toString() }
            AsyncImage(
                model = remember(key, context) {
                    ImageRequest.Builder(context)
                        .data(artworkUri)
                        .memoryCacheKey(key) // clave estable por URI: reutiliza la caché, sin parpadeo
                        .diskCacheKey(key)
                        .crossfade(true)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
