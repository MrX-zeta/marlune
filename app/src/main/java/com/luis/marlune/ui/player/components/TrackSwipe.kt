package com.luis.marlune.ui.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.abs

/**
 * Dirección del cambio de pista. Convención de producto ÚNICA: derecha = siguiente,
 * izquierda = anterior.
 */
enum class TrackSwipeDirection { NEXT, PREVIOUS }

/**
 * ÚNICA fuente de verdad de la dirección. Combina el desplazamiento horizontal NETO del gesto
 * ([netOffsetX], desde el inicio del propio gesto) y la velocidad del fling ([velocityX]) con la
 * MISMA convención de signo (positivo = derecha = siguiente). Devuelve `null` si no se confirma
 * (no se superó ni la distancia ni la velocidad).
 *
 * El resultado alimenta tanto la animación como el comando; nunca se decide dos veces.
 */
fun resolveTrackSwipe(
    netOffsetX: Float,
    velocityX: Float,
    commitDistancePx: Float,
    flingVelocity: Float,
): TrackSwipeDirection? {
    val committed = abs(netOffsetX) >= commitDistancePx || abs(velocityX) >= flingVelocity
    if (!committed) return null
    // Una sola cantidad con signo: el desplazamiento neto manda; si es exactamente 0, el fling.
    val directional = if (netOffsetX != 0f) netOffsetX else velocityX
    return if (directional >= 0f) TrackSwipeDirection.NEXT else TrackSwipeDirection.PREVIOUS
}

/**
 * Ejecuta el cross-slide y el comando derivando AMBOS de la misma [direction]: la carátula/tarjeta
 * sale hacia el lado del gesto y la nueva entra por el opuesto, e invoca skipToNext/skipToPrevious
 * (que van por el MediaController). Con movimiento reducido salta sin animar. Techo de 300 ms.
 */
suspend fun runTrackCrossSlide(
    direction: TrackSwipeDirection,
    offsetX: Animatable<Float, *>,
    widthPx: Float,
    reducedMotion: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    // Derecha (NEXT) sale por la derecha (+); izquierda (PREVIOUS) por la izquierda (−).
    val exitSign = if (direction == TrackSwipeDirection.NEXT) 1f else -1f
    val command = if (direction == TrackSwipeDirection.NEXT) onNext else onPrevious

    if (reducedMotion) {
        command()
        offsetX.snapTo(0f)
        return
    }
    offsetX.animateTo(exitSign * widthPx, tween(180, easing = FastOutLinearInEasing)) // sale acelerando
    command()
    offsetX.snapTo(-exitSign * widthPx) // la pista nueva entra desde el lado opuesto
    offsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium))
}
