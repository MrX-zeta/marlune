package com.luis.marlune.ui.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
private const val VerticalCommitFraction = 0.30f

/**
 * Carátula cuadrada con gestos:
 *  - Swipe horizontal: cambia de pista. La carátula sigue el dedo y, al superar el umbral,
 *    sale acelerando y la nueva entra desde el lado opuesto asentándose con spring (cross-slide).
 *  - Swipe hacia abajo: minimiza al mini-player (`onMinimize`). Sigue el dedo con desvanecido.
 *  - Por debajo del umbral: regresa a su sitio con spring.
 *
 * Con movimiento reducido, los gestos se resuelven al instante (sin recorrido animado).
 */
@Composable
fun AlbumArt(
    artwork: ImageBitmap?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val horizontalThreshold = widthPx * HorizontalCommitFraction
        val verticalThreshold = heightPx * VerticalCommitFraction

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
                    translationX = offsetX.value
                    translationY = offsetY.value
                    // Encoge levemente y desvanece al arrastrar hacia abajo (sensación de soltar).
                    val downFraction = (offsetY.value.coerceAtLeast(0f) / heightPx).coerceIn(0f, 1f)
                    val dragFraction = (abs(offsetX.value) / widthPx + downFraction).coerceIn(0f, 1f)
                    val shrink = 1f - dragFraction * 0.1f
                    scaleX = shrink
                    scaleY = shrink
                    alpha = 1f - downFraction * 0.35f
                }
                .pointerInput(reducedMotion, widthPx, heightPx) {
                    detectDragGestures(
                        onDrag = { change, drag ->
                            change.consume()
                            scope.launch { offsetX.snapTo(offsetX.value + drag.x) }
                            // Solo hacia abajo en el eje vertical.
                            scope.launch { offsetY.snapTo((offsetY.value + drag.y).coerceAtLeast(0f)) }
                        },
                        onDragEnd = {
                            val dx = offsetX.value
                            val dy = offsetY.value
                            when {
                                dy > verticalThreshold && dy > abs(dx) -> scope.launch {
                                    if (!reducedMotion) {
                                        offsetY.animateTo(heightPx, tween(200, easing = FastOutLinearInEasing))
                                    }
                                    onMinimize()
                                    offsetX.snapTo(0f)
                                    offsetY.snapTo(0f)
                                }

                                dx <= -horizontalThreshold ->
                                    scope.launch { crossSlide(offsetX, offsetY, widthPx, exitToLeft = true, reducedMotion, onNext) }

                                dx >= horizontalThreshold ->
                                    scope.launch { crossSlide(offsetX, offsetY, widthPx, exitToLeft = false, reducedMotion, onPrevious) }

                                else -> {
                                    scope.launch { offsetX.animateTo(0f, settleSpring()) }
                                    scope.launch { offsetY.animateTo(0f, settleSpring()) }
                                }
                            }
                        },
                    )
                },
        )
    }
}

/** La carátula sale por un lado, se cambia la pista y la nueva entra por el opuesto. */
private suspend fun crossSlide(
    offsetX: Animatable<Float, *>,
    offsetY: Animatable<Float, *>,
    widthPx: Float,
    exitToLeft: Boolean,
    reducedMotion: Boolean,
    onCommit: () -> Unit,
) {
    if (reducedMotion) {
        onCommit()
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
        return
    }
    val exit = if (exitToLeft) -widthPx else widthPx
    offsetY.snapTo(0f)
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
        androidx.compose.foundation.layout.Box(
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
