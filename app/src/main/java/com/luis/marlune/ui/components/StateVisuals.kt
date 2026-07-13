package com.luis.marlune.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme

/**
 * Pulso de carga (shimmer) discreto: la opacidad late suavemente entre dos valores con easing
 * estándar (nunca `LinearEasing`) y `Reverse`, en lugar de un barrido. Calmado y barato en
 * batería, en línea con "animar menos". Con movimiento reducido queda estático.
 */
@Composable
private fun rememberPulseAlpha(): Float {
    if (LocalReducedMotion.current) return 0.45f
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.60f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    return alpha
}

/** Placeholder de una fila de lista (portada + dos líneas) que late mientras se carga. */
@Composable
fun LoadingRows(
    modifier: Modifier = Modifier,
    count: Int = 6,
    circularCover: Boolean = false,
) {
    val alpha = rememberPulseAlpha()
    val block = MarluneTheme.colors.surfaceElevated
    val coverShape = if (circularCover) CircleShape else RoundedCornerShape(12.dp)

    Column(modifier = modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }) {
        repeat(count) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).clip(coverShape).background(block))
                Spacer(Modifier.width(14.dp))
                Column {
                    Box(Modifier.height(14.dp).width(160.dp).clip(RoundedCornerShape(6.dp)).background(block))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.height(12.dp).width(100.dp).clip(RoundedCornerShape(6.dp)).background(block))
                }
            }
        }
    }
}

/** Estado vacío neutro: icono tenue + título + pista de qué hacer. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 64.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MarluneTheme.colors.textTertiary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MarluneTheme.typography.titleMedium,
            color = MarluneTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = hint,
            style = MarluneTheme.typography.bodyMedium,
            color = MarluneTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}
