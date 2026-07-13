package com.luis.marlune.ui.library.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Portada de una entrada de Biblioteca: la carátula real (content URI de MediaStore, Coil + caché)
 * sobre un placeholder teñido con su acento asociado, para que las listas se distingan de un
 * vistazo. Si no hay carátula (o falla, p. ej. artistas sin arte), queda el degradado + icono.
 * Solo cambia el acento; la escala neutra no se toca. La forma la decide la categoría
 * (círculo para listas/artistas, cuadrado redondeado para álbumes/canciones).
 */
@Composable
fun LibraryCover(
    accent: Color,
    icon: ImageVector,
    shape: Shape,
    modifier: Modifier = Modifier,
    artworkUri: Uri? = null,
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.40f), accent.copy(alpha = 0.16f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp),
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
