package com.luis.marlune.ui.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Portada de una entrada de Biblioteca, teñida con su acento asociado (color dinámico del
 * prompt 1) para que las listas se distingan de un vistazo y no sean un muro monocromo.
 * Solo cambia el acento; la escala neutra no se toca. La forma la decide la categoría
 * (círculo para listas/artistas, cuadrado redondeado para álbumes/canciones).
 */
@Composable
fun LibraryCover(
    accent: Color,
    icon: ImageVector,
    shape: Shape,
    modifier: Modifier = Modifier,
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
    }
}
