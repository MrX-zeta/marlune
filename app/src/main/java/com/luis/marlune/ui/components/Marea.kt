package com.luis.marlune.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val MareaHeight = 24.dp
private val HitAreaHeight = 44.dp // área tocable cómoda aunque la línea sea fina
private const val MareaAmplitudeDp = 2f // amplitud máxima de la onda (dp)
private val MareaWavelength = 22.dp
private val MareaStroke = 2.dp
private val MareaTrackStroke = 1.5.dp
private val PlayheadRadius = 4.5.dp
private val PlayheadRing = 6.5.dp // anillo de separación (color de fondo)
private val PlayheadHalo = 10.dp
private const val MareaLoopMillis = 3500 // periodo del bucle (3–4 s)

/**
 * La **marea**: barra de progreso firma de Marlune.
 *
 * - Parte no reproducida: línea fina y plana al ~30 % de alpha ([trackColor]).
 * - Parte reproducida: onda sinusoidal de amplitud mínima teñida con el acento ([waveColor]).
 * - Playhead: punto con halo en la frontera reproducido/no reproducido.
 *
 * Motion: la fase corre en un bucle lineal de ~3,5 s. La amplitud se calma a ~0 al pausar.
 *
 * Interacción (opcional): si se pasa [onSeek], la marea deja de ser solo indicador y acepta
 * scrubbing (arrastrar) y tap (saltar). Mientras se arrastra, IGNORA [progress] del player y sigue
 * al dedo (flag de scrubbing); al soltar, invoca [onSeek] con la fracción destino y vuelve a
 * escuchar al player. El gesto se maneja sobre un hit area de 44 dp; la marea solo dibuja.
 *
 * @param progress avance en [0, 1].
 * @param isPlaying si la reproducción está activa (conduce onda y amplitud).
 * @param onSeek si no es nulo, activa el seek; recibe la fracción destino [0, 1] al soltar.
 * @param durationMs duración de la pista, para el label de tiempo destino durante el scrubbing.
 */
@Composable
fun Marea(
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    waveColor: Color = MarluneTheme.colors.accent, // acento dinámico ANIMADO (lector fino)
    trackColor: Color = MarluneTheme.colors.accentMuted,
    playheadColor: Color = waveColor,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    amplitudeScale: Float = 1f,
    onSeek: ((Float) -> Unit)? = null,
    durationMs: Long = 0L,
) {
    if (onSeek == null) {
        // Indicador puro (sin cambios de comportamiento).
        MareaCanvas(
            progress = progress,
            isPlaying = isPlaying,
            waveColor = waveColor,
            trackColor = trackColor,
            playheadColor = playheadColor,
            backgroundColor = backgroundColor,
            amplitudeScale = amplitudeScale,
            modifier = modifier
                .fillMaxWidth()
                .height(MareaHeight)
                .padding(horizontal = PlayheadHalo),
        )
    } else {
        InteractiveMarea(
            progress = progress,
            isPlaying = isPlaying,
            modifier = modifier,
            waveColor = waveColor,
            trackColor = trackColor,
            playheadColor = playheadColor,
            backgroundColor = backgroundColor,
            amplitudeScale = amplitudeScale,
            onSeek = onSeek,
            durationMs = durationMs,
        )
    }
}

@Composable
private fun InteractiveMarea(
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier,
    waveColor: Color,
    trackColor: Color,
    playheadColor: Color,
    backgroundColor: Color,
    amplitudeScale: Float,
    onSeek: (Float) -> Unit,
    durationMs: Long,
) {
    // Flag de scrubbing: mientras no es null, la marea sigue al dedo e ignora al player.
    val scrubState = remember { mutableStateOf<Float?>(null) }
    val scrub = scrubState.value
    val effectiveProgress = scrub ?: progress.coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(HitAreaHeight)
            // El padding va antes del pointerInput: el gesto comparte el espacio de coordenadas
            // (0..anchoInterno) con el Canvas, y deja hueco para que el halo no se recorte.
            .padding(horizontal = PlayheadHalo)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    fun fractionAt(x: Float) = (x / size.width).coerceIn(0f, 1f)
                    // Tap: el playhead salta al punto tocado ya en el "down".
                    scrubState.value = fractionAt(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        scrubState.value = fractionAt(change.position.x) // scrubbing: sigue al dedo
                    }
                    val target = scrubState.value ?: fractionAt(down.position.x)
                    onSeek(target) // seekTo va por el MediaController vía callback
                    scrubState.value = null // vuelve a escuchar al player
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        MareaCanvas(
            progress = effectiveProgress,
            isPlaying = isPlaying,
            waveColor = waveColor,
            trackColor = trackColor,
            playheadColor = playheadColor,
            backgroundColor = backgroundColor,
            amplitudeScale = amplitudeScale,
            modifier = Modifier
                .fillMaxWidth()
                .height(MareaHeight),
        )

        // Label de tiempo DESTINO que sigue al pulgar; solo visible durante el scrubbing.
        if (scrub != null && durationMs > 0L) {
            Text(
                text = formatTime((scrub * durationMs).toLong()),
                style = MarluneTheme.typography.labelMedium,
                color = MarluneTheme.colors.textPrimary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val widthPx = maxWidth.toPx()
                        val labelHalf = 18.dp.toPx()
                        val x = (scrub * widthPx - labelHalf)
                            .coerceIn(0f, (widthPx - 2f * labelHalf).coerceAtLeast(0f))
                        IntOffset(x.roundToInt(), 0)
                    },
            )
        }
    }
}

/** Solo dibuja la marea (sin interacción). El playhead con halo y la calma en pausa se conservan. */
@Composable
private fun MareaCanvas(
    progress: Float,
    isPlaying: Boolean,
    waveColor: Color,
    trackColor: Color,
    playheadColor: Color,
    backgroundColor: Color,
    amplitudeScale: Float,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val clampedProgress = progress.coerceIn(0f, 1f)

    // Fase del bucle, conducida desde un Animatable en un efecto para poder suspenderla al
    // pausar y al salir de composición. Battery-first.
    val phase = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, reducedMotion) {
        if (isPlaying && !reducedMotion) {
            phase.animateTo(
                targetValue = phase.value + 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(MareaLoopMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            )
        }
    }

    // La amplitud calma a 0 al pausar y regresa al reproducir.
    val maxAmplitude = MareaAmplitudeDp * amplitudeScale.coerceIn(0f, 1f)
    val targetAmplitude = if (isPlaying && !reducedMotion) maxAmplitude else 0f
    val amplitudeDp by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "mareaAmplitude",
    )

    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val playedWidth = size.width * clampedProgress
        val amplitudePx = amplitudeDp.dp.toPx()
        val wavelengthPx = MareaWavelength.toPx()
        val angularStep = (2f * PI.toFloat()) / wavelengthPx
        val phaseOffset = phase.value * 2f * PI.toFloat()

        // 1) No reproducido: línea plana fina al ~30 % de alpha.
        if (playedWidth < size.width) {
            drawLine(
                color = trackColor.copy(alpha = 0.30f),
                start = Offset(playedWidth, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = MareaTrackStroke.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // 2) Reproducido: onda sinusoidal (plana cuando la amplitud calma a 0).
        fun waveY(x: Float): Float = centerY + amplitudePx * sin(x * angularStep + phaseOffset)

        if (playedWidth > 0f) {
            val stepPx = 2.dp.toPx().coerceAtLeast(1f)
            val wavePath = Path().apply {
                moveTo(0f, waveY(0f))
                var x = stepPx
                while (x < playedWidth) {
                    lineTo(x, waveY(x))
                    x += stepPx
                }
                lineTo(playedWidth, waveY(playedWidth))
            }
            drawPath(
                path = wavePath,
                color = waveColor,
                style = Stroke(width = MareaStroke.toPx(), cap = StrokeCap.Round),
            )
        }

        // 3) Playhead: halo + anillo de separación (color de fondo) + punto sólido.
        val headY = waveY(playedWidth)
        val headCenter = Offset(playedWidth, headY)
        drawCircle(
            color = playheadColor.copy(alpha = 0.22f),
            radius = PlayheadHalo.toPx(),
            center = headCenter,
        )
        drawCircle(
            color = backgroundColor,
            radius = PlayheadRing.toPx(),
            center = headCenter,
        )
        drawCircle(
            color = playheadColor,
            radius = PlayheadRadius.toPx(),
            center = headCenter,
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0910, widthDp = 320)
@Composable
private fun MareaPlayingPreview() {
    MarluneTheme {
        Marea(progress = 0.42f, isPlaying = true)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0910, widthDp = 320)
@Composable
private fun MareaPausedPreview() {
    MarluneTheme {
        Marea(progress = 0.66f, isPlaying = false)
    }
}
