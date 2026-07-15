package com.luis.marlune.ui.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luis.marlune.domain.model.PlaylistCover
import com.luis.marlune.ui.theme.placeholderAccentFor

/**
 * Portada de una lista en MOSAICO, a partir de las carátulas de sus primeras canciones ([covers]):
 *  - 0 → placeholder con el icono de lista (teñido con [fallbackKey]).
 *  - 1 → carátula a tamaño completo · 2 → mitades · 3 → 2x2 con 3 carátulas + 1 celda placeholder ·
 *    4+ → 2x2 con las 4 primeras.
 * Las canciones sin carátula usan el placeholder teñido con su acento estable ([placeholderAccentFor]).
 * El tamaño lo fija el llamador (vía [modifier]); usa el mismo token de radio ([shape]) del resto de
 * tarjetas. Coil carga y cachea cada celda por su URI; el resultado se recalcula solo si cambian las 4.
 */
@Composable
fun PlaylistMosaicCover(
    covers: List<PlaylistCover>,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCover,
    fallbackKey: Long = 0L,
) {
    Box(modifier.clip(shape)) {
        when {
            covers.isEmpty() -> EmptyCell(fallbackKey)
            covers.size == 1 -> MosaicCell(covers[0], fallbackKey, Modifier.fillMaxSize())
            covers.size == 2 -> Row(Modifier.fillMaxSize()) {
                MosaicCell(covers[0], fallbackKey, Modifier.weight(1f).fillMaxHeight())
                MosaicCell(covers[1], fallbackKey, Modifier.weight(1f).fillMaxHeight())
            }
            else -> {
                // 3 → la 4ª celda queda como placeholder (null); 4+ → las 4 primeras.
                val cells = (0 until 4).map { covers.getOrNull(it) }
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        MosaicCell(cells[0], fallbackKey, Modifier.weight(1f).fillMaxHeight())
                        MosaicCell(cells[1], fallbackKey, Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        MosaicCell(cells[2], fallbackKey, Modifier.weight(1f).fillMaxHeight())
                        MosaicCell(cells[3], fallbackKey, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

/** Una celda del mosaico: carátula (Coil) sobre un degradado teñido; si no hay carátula, solo el tinte. */
@Composable
private fun MosaicCell(cover: PlaylistCover?, fallbackKey: Long, modifier: Modifier) {
    val accent = remember(cover?.songId, fallbackKey) { placeholderAccentFor(cover?.songId ?: fallbackKey) }
    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(accent.copy(alpha = 0.40f), accent.copy(alpha = 0.16f))),
        ),
    ) {
        val uri = cover?.artworkUri
        if (uri != null) {
            val context = LocalContext.current
            val key = remember(uri) { uri.toString() }
            AsyncImage(
                model = remember(key, context) {
                    ImageRequest.Builder(context)
                        .data(uri)
                        .memoryCacheKey(key)
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

/** Placeholder de lista vacía: icono de cola teñido con el acento estable de la lista. */
@Composable
private fun EmptyCell(fallbackKey: Long) {
    val accent = remember(fallbackKey) { placeholderAccentFor(fallbackKey) }
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(accent.copy(alpha = 0.40f), accent.copy(alpha = 0.16f))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.fillMaxSize(0.42f),
        )
    }
}
