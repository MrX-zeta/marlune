package com.luis.marlune.ui.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ArtCorner = 24.dp
private const val HorizontalCommitFraction = 0.28f

private enum class DragAxis { Undecided, Horizontal, VerticalDown, Ignored }

/**
 * Carátula cuadrada con gestos de EJE RESTRINGIDO (se bloquea la dirección dominante al
 * superar el touch slop, sin movimiento libre en 2D):
 *  - Horizontal → cambia de pista: la carátula sigue el dedo y, al superar el umbral, sale
 *    acelerando y la nueva entra desde el lado opuesto (cross-slide).
 *  - Vertical hacia abajo → minimizar: NO mueve la carátula por libre; reporta el avance a la
 *    pantalla ([onCollapseDrag]/[onCollapseRelease]), que acopla fade/escala/blur a toda la vista
 *    y decide el snap. Es la mitad de colapso de la expansión mini↔full.
 *
 * Con movimiento reducido, el cierre de gestos horizontales se resuelve al instante.
 */
@Composable
fun AlbumArt(
    artwork: ImageBitmap?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCollapseDrag: (dyPx: Float) -> Unit,
    onCollapseRelease: (velocityYPx: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offsetX = remember { Animatable(0f) }

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val horizontalThreshold = widthPx * HorizontalCommitFraction

        fun settleSpring() = if (reducedMotion) {
            snap<Float>()
        } else {
            spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
        }

        ArtSurface(
            artwork = artwork,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Solo sigue el dedo en horizontal (cambio de pista); el eje vertical
                    // lo maneja la vista completa, así la carátula no queda flotando.
                    translationX = offsetX.value
                    val dragFraction = (abs(offsetX.value) / widthPx).coerceIn(0f, 1f)
                    val shrink = 1f - dragFraction * 0.06f
                    scaleX = shrink
                    scaleY = shrink
                }
                .pointerInput(reducedMotion, widthPx) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        var axis = DragAxis.Undecided

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) break

                            // Bloqueo de eje: dirección dominante al superar el slop.
                            if (axis == DragAxis.Undecided) {
                                val totalDx = change.position.x - down.position.x
                                val totalDy = change.position.y - down.position.y
                                if (abs(totalDx) >= touchSlop || abs(totalDy) >= touchSlop) {
                                    axis = when {
                                        abs(totalDx) > abs(totalDy) -> DragAxis.Horizontal
                                        totalDy > 0f -> DragAxis.VerticalDown
                                        else -> DragAxis.Ignored // hacia arriba: sin acción
                                    }
                                }
                            }

                            val delta = change.positionChange()
                            when (axis) {
                                DragAxis.Horizontal -> {
                                    change.consume()
                                    scope.launch { offsetX.snapTo(offsetX.value + delta.x) }
                                }

                                DragAxis.VerticalDown -> {
                                    change.consume()
                                    onCollapseDrag(delta.y)
                                }

                                else -> {}
                            }
                        }

                        val velocity = velocityTracker.calculateVelocity()
                        when (axis) {
                            DragAxis.Horizontal -> {
                                val dx = offsetX.value
                                when {
                                    dx <= -horizontalThreshold ->
                                        scope.launch { crossSlide(offsetX, widthPx, exitToLeft = true, reducedMotion, onNext) }

                                    dx >= horizontalThreshold ->
                                        scope.launch { crossSlide(offsetX, widthPx, exitToLeft = false, reducedMotion, onPrevious) }

                                    else -> scope.launch { offsetX.animateTo(0f, settleSpring()) }
                                }
                            }

                            DragAxis.VerticalDown -> onCollapseRelease(velocity.y)

                            else -> {}
                        }
                    }
                },
        )
    }
}

/** La carátula sale por un lado, se cambia la pista y la nueva entra por el opuesto. */
private suspend fun crossSlide(
    offsetX: Animatable<Float, *>,
    widthPx: Float,
    exitToLeft: Boolean,
    reducedMotion: Boolean,
    onCommit: () -> Unit,
) {
    if (reducedMotion) {
        onCommit()
        offsetX.snapTo(0f)
        return
    }
    val exit = if (exitToLeft) -widthPx else widthPx
    offsetX.animateTo(exit, tween(180, easing = FastOutLinearInEasing)) // salir acelerando
    onCommit()
    offsetX.snapTo(-exit) // la nueva entra desde el lado opuesto
    offsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium))
}

@Composable
private fun ArtSurface(
    artwork: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val clipped = modifier
        .clip(RoundedCornerShape(ArtCorner))
        .background(MarluneTheme.colors.surfaceElevated)

    if (artwork != null) {
        Image(
            bitmap = artwork,
            contentDescription = stringResource(R.string.player_artwork),
            contentScale = ContentScale.Crop,
            modifier = clipped,
        )
    } else {
        // Marcador de posición hasta que la capa de datos entregue la carátula local.
        Box(
            modifier = clipped,
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MarluneTheme.colors.textTertiary,
                modifier = Modifier.fillMaxSize(0.28f),
            )
        }
    }
}
