package com.luis.marlune.ui.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.abs

/**
 * Dirección del cambio de pista. Convención de producto ÚNICA (estándar tipo Spotify):
 * izquierda = siguiente, derecha = anterior.
 */
enum class TrackSwipeDirection { NEXT, PREVIOUS }

/**
 * A partir del gesto, decide qué COMANDO pedir (siguiente/anterior) combinando el desplazamiento
 * horizontal neto y la velocidad del fling. `null` si no se confirma. OJO: esto elige el comando
 * del swipe; NO decide la dirección de la animación (esa sale del cambio de pista, ver
 * [runTrackSlideAnimation]).
 *
 * Convención de producto (estándar tipo Spotify): IZQUIERDA (negativo) = siguiente; DERECHA
 * (positivo) = anterior. La carátula sigue al dedo y la confirmación continúa en la MISMA dirección
 * (ver el `exitSign` de [runTrackSlideAnimation]), sin inversión.
 */
fun resolveTrackSwipe(
    netOffsetX: Float,
    velocityX: Float,
    commitDistancePx: Float,
    flingVelocity: Float,
): TrackSwipeDirection? {
    val committed = abs(netOffsetX) >= commitDistancePx || abs(velocityX) >= flingVelocity
    if (!committed) return null
    val directional = if (netOffsetX != 0f) netOffsetX else velocityX
    return if (directional > 0f) TrackSwipeDirection.PREVIOUS else TrackSwipeDirection.NEXT
}

/**
 * Animación de confirmación del cambio de pista: la carátula sale por un lado y la nueva entra
 * por el opuesto. La DIRECCIÓN sale de [forward] —que deriva del cambio de pista real leído del
 * player— con un ÚNICO mapeo aquí (siguiente = la nueva entra por la derecha / la actual sale por
 * la izquierda; anterior = al revés).
 *
 * NO ejecuta ningún comando: el comando (skipToNext/skipToPrevious) es solo el disparador; esta
 * función solo anima. Así todos los orígenes (swipe, botones, notificación, auto-avance) animan
 * en la misma dirección para "siguiente" y la misma para "anterior". Respeta el movimiento
 * reducido y el techo de 300 ms.
 */
suspend fun runTrackSlideAnimation(
    forward: Boolean,
    offsetX: Animatable<Float, *>,
    widthPx: Float,
    reducedMotion: Boolean,
) {
    // ÚNICO punto que mapea dirección de cambio de pista → dirección de animación, alineado con el
    // swipe (estándar: izquierda = siguiente): siguiente → la actual sale por la IZQUIERDA (-) y la
    // nueva entra por la derecha; anterior → al revés.
    val exitSign = if (forward) -1f else 1f
    if (reducedMotion) {
        offsetX.snapTo(0f)
        return
    }
    offsetX.animateTo(exitSign * widthPx, tween(180, easing = FastOutLinearInEasing)) // sale acelerando
    offsetX.snapTo(-exitSign * widthPx) // la pista nueva entra desde el lado opuesto
    offsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium))
}
