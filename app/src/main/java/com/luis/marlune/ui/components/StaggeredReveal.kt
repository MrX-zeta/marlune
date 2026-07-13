package com.luis.marlune.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.luis.marlune.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay

private const val StaggerStepMs = 35L
private const val MaxStaggerIndex = 6 // agrupa 3–7 ítems; el resto entra con el último
private const val RiseDp = 8

/**
 * Entrada escalonada fade+rise para filas de lista en la PRIMERA carga: alpha 0→1 y
 * translationY 8dp→0 con desaceleración; el primer ítem lidera y cada siguiente arranca
 * 35 ms después (tope de 6 para no cascadear de más). Corre una sola vez —está ligada a la
 * composición, no al scroll— así el desplazamiento se siente instantáneo. Respeta el
 * movimiento reducido (aparece al instante).
 *
 * Solo transforma `graphicsLayer` (alpha/translationY); nunca layout.
 *
 * `enabled` se captura en la primera composición: al pasar `false` (p. ej. al cambiar de
 * pestaña, donde el contenido entra con un fade rápido y sin coreografía por fila) la fila
 * aparece al instante, y cambiarlo después no perturba una animación en curso.
 */
@Composable
fun StaggeredReveal(
    index: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    val animate = remember { enabled && !reducedMotion }
    val reveal = remember { Animatable(if (animate) 0f else 1f) }

    LaunchedEffect(Unit) {
        if (animate) {
            delay(index.coerceAtMost(MaxStaggerIndex) * StaggerStepMs)
            reveal.animateTo(1f, tween(durationMillis = 220, easing = LinearOutSlowInEasing))
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            alpha = reveal.value
            translationY = (1f - reveal.value) * RiseDp.dp.toPx()
        },
    ) {
        content()
    }
}
