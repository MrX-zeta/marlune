package com.luis.marlune.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Miniatura de pista teñida con su acento asociado (color dinámico del prompt 1) para que la
 * lista se pueda escanear. Solo el acento cambia; no hay carátula real aún, así que muestra un
 * degradado del acento con una nota. Cuando llegue el bitmap local, aquí irá la `Image`.
 */
@Composable
fun TrackThumbnail(
    accent: Color,
    modifier: Modifier = Modifier,
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
    }
}
