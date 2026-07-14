package com.luis.marlune.ui.library.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.luis.marlune.ui.theme.LocalReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val BarHeight = 16.dp
private val StaticScales = floatArrayOf(0.5f, 0.85f, 0.6f) // alturas en reposo (pausa / mov. reducido)
private val Durations = intArrayOf(300, 220, 260) // ritmos distintos por barra para que no lata al unísono

/**
 * Pequeño ecualizador de "sonando": barras teñidas con el acento que oscilan mientras suena. Anima
 * `scaleY` con `graphicsLayer` (GPU, sin relayout por frame), origen abajo. Con movimiento reducido
 * o en pausa queda estático; los bucles se cancelan al pausar o al salir de composición (batería).
 */
@Composable
fun NowPlayingBars(
    color: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val animate = isPlaying && !reducedMotion
    val scales = remember { List(StaticScales.size) { Animatable(StaticScales[it]) } }

    LaunchedEffect(animate) {
        if (!animate) {
            scales.forEachIndexed { i, a -> a.snapTo(StaticScales[i]) }
            return@LaunchedEffect
        }
        coroutineScope {
            scales.forEachIndexed { i, a ->
                launch {
                    val dur = Durations[i]
                    while (isActive) {
                        a.animateTo(1f, tween(dur, easing = FastOutSlowInEasing))
                        a.animateTo(0.3f, tween(dur, easing = FastOutSlowInEasing))
                    }
                }
            }
        }
    }

    Row(
        modifier = modifier.height(BarHeight),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        scales.forEach { scale ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleY = scale.value
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}
