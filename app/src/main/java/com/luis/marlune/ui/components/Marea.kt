package com.luis.marlune.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme
import kotlin.math.PI
import kotlin.math.sin

private val MareaHeight = 24.dp
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
 * Motion: la fase corre en un bucle lineal de ~3,5 s (única excepción de easing lineal).
 * La amplitud se calma a ~0 al pausar y vuelve al reproducir. La animación se **suspende**
 * al pausar y al salir de composición (la fase la conduce un `Animatable` en `LaunchedEffect`,
 * que se cancela en ambos casos), y respeta el movimiento reducido.
 *
 * @param progress avance en [0, 1].
 * @param isPlaying si la reproducción está activa (conduce onda y amplitud).
 */
@Composable
fun Marea(
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MarluneTheme.colors.accentMuted,
    playheadColor: Color = waveColor,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
) {
    val reducedMotion = LocalReducedMotion.current
    val clampedProgress = progress.coerceIn(0f, 1f)

    // Fase del bucle. Conducirla desde un Animatable en un efecto (en vez de
    // rememberInfiniteTransition) permite cancelarla —y suspender el trabajo— al
    // pausar o al abandonar la composición. Battery-first.
    val phase = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, reducedMotion) {
        if (isPlaying && !reducedMotion) {
            // start y start+1 difieren en un periodo completo → el reinicio no salta.
            phase.animateTo(
                targetValue = phase.value + 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(MareaLoopMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            )
        }
    }

    // La amplitud calma a 0 al pausar y regresa al reproducir (el corazón emocional).
    val targetAmplitude = if (isPlaying && !reducedMotion) MareaAmplitudeDp else 0f
    val amplitudeDp by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "mareaAmplitude",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(MareaHeight)
            .padding(horizontal = PlayheadHalo), // margen para que el halo no se recorte
    ) {
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

        // 3) Playhead: halo + anillo de separación (color de fondo) + punto sólido,
        //    para que destaque con claridad sobre las ondulaciones de la marea.
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
