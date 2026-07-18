package com.luis.marlune.ui.player.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
private const val LyricsCrossfadeMillis = 240

private enum class DragAxis { Undecided, Horizontal, VerticalDown, Ignored }

/**
 * Carátula cuadrada con DETECTOR UNIFICADO de eje restringido (sin movimiento libre en 2D):
 *  - Tap (sin superar el slop) → alterna carátula ↔ letras ([onToggleLyrics]).
 *  - Horizontal → cambia de pista: izquierda = siguiente, derecha = anterior (convención estándar).
 *  - Vertical hacia abajo → minimizar, PERO solo en modo carátula; en modo letras el eje vertical se
 *    cede a la lista de letras (scroll). Es la mitad de colapso de la expansión mini↔full.
 *
 * En modo letras se muestra [lyricsContent] con un crossfade (≤300 ms; salto con movimiento reducido).
 * El swipe de cambiar pista y el gesto de minimizar siguen funcionando igual que antes.
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
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    lyricsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offsetX = trackOffset
    // El pointerInput no se re-crea al cambiar estos flags (su key es reducedMotion/widthPx), así que
    // los capturaría congelados. rememberUpdatedState da el valor ACTUAL dentro del gesto.
    val latestCanGoNext = rememberUpdatedState(canGoNext)
    val latestCanGoPrevious = rememberUpdatedState(canGoPrevious)
    val latestShowLyrics = rememberUpdatedState(showLyrics)

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val horizontalThreshold = widthPx * HorizontalCommitFraction

        fun settleSpring() = if (reducedMotion) {
            snap<Float>()
        } else {
            spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Detector ANTES del graphicsLayer que traslada el contenido: el gesto se mide en
                // coordenadas ESTABLES. Va en un Box PADRE del contenido: la lista de letras (hijo)
                // consume el scroll vertical y el tap/horizontal burbujean aquí.
                .pointerInput(reducedMotion, widthPx) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        var axis = DragAxis.Undecided
                        var consumedByChild = false // p. ej. el enlace "buscar carpeta" del vacío
                        var dragX = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            if (axis == DragAxis.Undecided && change.isConsumed) consumedByChild = true
                            if (!change.pressed) break

                            if (axis == DragAxis.Undecided) {
                                val totalDx = change.position.x - down.position.x
                                val totalDy = change.position.y - down.position.y
                                if (abs(totalDx) >= touchSlop || abs(totalDy) >= touchSlop) {
                                    axis = when {
                                        abs(totalDx) > abs(totalDy) -> DragAxis.Horizontal
                                        // Minimizar solo en modo carátula; en letras el vertical scrollea.
                                        !latestShowLyrics.value && totalDy > 0f -> DragAxis.VerticalDown
                                        else -> DragAxis.Ignored
                                    }
                                }
                            }

                            val delta = change.positionChange()
                            when (axis) {
                                DragAxis.Horizontal -> {
                                    change.consume()
                                    dragX += delta.x
                                    val target = dragX
                                    scope.launch { offsetX.snapTo(target) }
                                }

                                DragAxis.VerticalDown -> {
                                    change.consume()
                                    onCollapseDrag(delta.y)
                                }

                                // Undecided / Ignored: NO se consume → la lista de letras (hijo) puede
                                // scrollear en vertical y el tap se resuelve al soltar.
                                else -> {}
                            }
                        }

                        val velocity = velocityTracker.calculateVelocity()
                        when (axis) {
                            DragAxis.Horizontal -> {
                                when (
                                    resolveTrackSwipe(dragX, velocity.x, horizontalThreshold, HorizontalFlingVelocity)
                                ) {
                                    TrackSwipeDirection.NEXT -> if (latestCanGoNext.value) onNext()
                                    TrackSwipeDirection.PREVIOUS -> if (latestCanGoPrevious.value) onPrevious()
                                    null -> {}
                                }
                                scope.launch { offsetX.animateTo(0f, settleSpring()) }
                            }

                            DragAxis.VerticalDown -> onCollapseRelease(velocity.y)

                            // Tap real (nunca superó el slop) y ningún hijo lo tomó → alterna letras.
                            DragAxis.Undecided -> if (!consumedByChild) onToggleLyrics()

                            else -> {}
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Solo visual: el contenido (carátula o letras) sigue el dedo en horizontal.
                        translationX = offsetX.value
                        val dragFraction = (abs(offsetX.value) / widthPx).coerceIn(0f, 1f)
                        val shrink = 1f - dragFraction * 0.06f
                        scaleX = shrink
                        scaleY = shrink
                    },
            ) {
                Crossfade(
                    targetState = showLyrics,
                    animationSpec = if (reducedMotion) snap() else tween(LyricsCrossfadeMillis),
                    label = "artLyricsCrossfade",
                ) { lyrics ->
                    if (lyrics) {
                        Box(Modifier.fillMaxSize()) { lyricsContent() }
                    } else {
                        ArtSurface(artwork = artwork, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtSurface(
    artwork: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val clipped = modifier
        .clip(RoundedCornerShape(ArtCorner))
        .background(MarluneTheme.colors.surfaceElevated)

    // Crossfade corto SOLO del contenido (imagen ↔ placeholder). No toca el gesto ni el slide: durante
    // el deslizamiento la carátula mostrada se mantiene fija, así que esto solo suaviza la sustitución
    // final (o la llegada tardía de la imagen sobre el placeholder). Nunca un destello a media animación.
    Crossfade(
        targetState = artwork,
        animationSpec = if (reducedMotion) snap() else tween(120),
        label = "artContentCrossfade",
        modifier = clipped,
    ) { art ->
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = stringResource(R.string.player_artwork),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Marcador de posición hasta que la capa de datos entregue la carátula local.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MarluneTheme.colors.textTertiary,
                    modifier = Modifier.fillMaxSize(0.28f),
                )
            }
        }
    }
}
