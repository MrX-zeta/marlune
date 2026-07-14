package com.luis.marlune.ui.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
private const val HorizontalCommitFraction = 0.22f // ~22 % del ancho para confirmar cambio de pista
private const val HorizontalFlingVelocity = 1000f

private enum class DragAxis { Undecided, Horizontal, VerticalDown, Ignored }

/**
 * Carátula cuadrada con gestos de EJE RESTRINGIDO (se bloquea la dirección dominante al
 * superar el touch slop, sin movimiento libre en 2D):
 *  - Horizontal → cambia de pista: izquierda = siguiente, derecha = anterior (convención estándar
 *    tipo Spotify). La carátula sigue el dedo vía [trackOffset] (hoisteado para que el título de
 *    Now Playing acompañe el mismo desplazamiento) y, al superar el umbral de distancia o
 *    velocidad, sale acelerando y la nueva entra desde el lado opuesto (cross-slide).
 *  - Vertical hacia abajo → minimizar: NO mueve la carátula por libre; reporta el avance a la
 *    pantalla ([onCollapseDrag]/[onCollapseRelease]), que acopla fade/escala/blur a toda la vista
 *    y decide el snap. Es la mitad de colapso de la expansión mini↔full.
 *
 * Con movimiento reducido, el cierre de gestos horizontales se resuelve al instante.
 */
@Composable
fun AlbumArt(
    artwork: ImageBitmap?,
    trackOffset: Animatable<Float, AnimationVector1D>,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCollapseDrag: (dyPx: Float) -> Unit,
    onCollapseRelease: (velocityYPx: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offsetX = trackOffset

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
                // El detector va ANTES del graphicsLayer que traslada la carátula: así el gesto se
                // mide en coordenadas ESTABLES y el arrastre no se retroalimenta con la traslación
                // (evita amortiguación/dirección poco fiable). El graphicsLayer, debajo, solo pinta.
                .pointerInput(reducedMotion, widthPx) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        var axis = DragAxis.Undecided
                        // Desplazamiento horizontal ACUMULADO desde el inicio de ESTE gesto (parte
                        // de 0, independiente de dónde empezó el dedo y de residuos previos). Decide
                        // la dirección por su signo; nunca por la posición absoluta.
                        var dragX = 0f

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
                                    dragX += delta.x
                                    val target = dragX // la carátula sigue el acumulado (misma dirección)
                                    scope.launch { offsetX.snapTo(target) }
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
                                // El swipe solo elige QUÉ comando pedir (siguiente/anterior) según
                                // el offset neto + fling. La dirección de la animación de
                                // confirmación NO se decide aquí: la corre el observador de
                                // `trackTransition` (fuente única), derivándola del cambio de pista.
                                when (
                                    resolveTrackSwipe(dragX, velocity.x, horizontalThreshold, HorizontalFlingVelocity)
                                ) {
                                    TrackSwipeDirection.NEXT -> if (canGoNext) onNext()
                                    TrackSwipeDirection.PREVIOUS -> if (canGoPrevious) onPrevious()
                                    null -> {}
                                }
                                // Red de seguridad: SIEMPRE se asienta a 0 al soltar. Si el cambio de
                                // pista ocurre, su `runTrackSlideAnimation` toma este MISMO Animatable
                                // y hace el cross-slide (cancela este settle por el mutatorMutex). Si
                                // NO llega transición (extremo, hasNext/hasPrevious obsoleto o carga
                                // DIRECT), esto evita que la carátula quede CORTADA a medias.
                                scope.launch { offsetX.animateTo(0f, settleSpring()) }
                            }

                            DragAxis.VerticalDown -> onCollapseRelease(velocity.y)

                            else -> {}
                        }
                    }
                }
                .graphicsLayer {
                    // Solo visual: la carátula sigue el dedo en horizontal (cambio de pista); va
                    // debajo del detector, así no altera las coordenadas del gesto.
                    translationX = offsetX.value
                    val dragFraction = (abs(offsetX.value) / widthPx).coerceIn(0f, 1f)
                    val shrink = 1f - dragFraction * 0.06f
                    scaleX = shrink
                    scaleY = shrink
                },
        )
    }
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
