package com.luis.marlune.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.ui.components.PressableCard
import com.luis.marlune.ui.home.components.TrackThumbnail
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val PlayMorphMillis = 150

// Recorrido (corto) para llegar al progreso 1 y magnitud del "levantamiento" acoplado.
private val ExpandDragDistance = 100.dp
private val ExpandLift = 20.dp
private const val TrackCommitFraction = 0.28f // fracción del ancho para confirmar cambio de pista
private const val TrackFlingVelocity = 1000f

private enum class MiniDragAxis { Undecided, ExpandUp, Horizontal, Down }

/**
 * Mini-player como tarjeta flotante sobre la barra inferior. Tocarlo (fuera del botón) o
 * deslizarlo hacia arriba lo expande al reproductor completo; la carátula es el elemento
 * compartido que viaja en esa transición ([artModifier]). El botón play/pausa comparte el
 * lenguaje de motion del botón grande de Now Playing; no lleva marea para mantenerlo compacto.
 *
 * El deslizar-arriba es el espejo del deslizar-abajo que colapsa Now Playing: al subir, la
 * tarjeta se levanta y crece acoplada al dedo (la carátula compartida viaja con ella); al
 * soltar, hace snap (>35 % o velocidad → expande con la misma transición; si no, vuelve al mini
 * con spring). Tap y swipe-arriba hacen lo mismo, así siempre se puede abrir.
 *
 * Usa [PressableCard] para heredar el mismo radio de esquina que el resto de tarjetas de la app.
 */
@Composable
fun MiniPlayer(
    uiState: PlayerUiState,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    artModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    artistModifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val dragProgress = remember { Animatable(0f) } // 0 = mini en reposo, 1 = listo para expandir
    val offsetX = remember { Animatable(0f) } // desplazamiento del cambio de pista horizontal

    val expandGesture = Modifier
        .graphicsLayer {
            // Efecto acoplado: la tarjeta (y con ella la carátula compartida) se levanta y crece
            // al subir, o acompaña el dedo en horizontal al cambiar de pista.
            translationX = offsetX.value
            translationY = -dragProgress.value * ExpandLift.toPx()
            val grow = 1f + dragProgress.value * 0.06f
            scaleX = grow
            scaleY = grow
        }
        .pointerInput(reducedMotion) {
            val touchSlop = viewConfiguration.touchSlop
            val distancePx = ExpandDragDistance.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)
                var axis = MiniDragAxis.Undecided

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) break

                    if (axis == MiniDragAxis.Undecided) {
                        val totalDx = change.position.x - down.position.x
                        val totalDy = change.position.y - down.position.y
                        if (abs(totalDx) >= touchSlop || abs(totalDy) >= touchSlop) {
                            axis = when {
                                abs(totalDx) > abs(totalDy) -> MiniDragAxis.Horizontal
                                totalDy < 0f -> MiniDragAxis.ExpandUp
                                else -> MiniDragAxis.Down // hacia abajo: sin acción en el mini
                            }
                        }
                    }

                    // Se consume el eje bloqueado (así no dispara el tap). El tap (sin pasar el
                    // slop) NO se consume → lo maneja el onClick del PressableCard.
                    when (axis) {
                        MiniDragAxis.ExpandUp -> {
                            change.consume()
                            val deltaUp = -change.positionChange().y
                            scope.launch {
                                dragProgress.snapTo((dragProgress.value + deltaUp / distancePx).coerceIn(0f, 1f))
                            }
                        }

                        MiniDragAxis.Horizontal -> {
                            change.consume()
                            val deltaX = change.positionChange().x
                            scope.launch { offsetX.snapTo(offsetX.value + deltaX) }
                        }

                        else -> {}
                    }
                }

                val velocity = velocityTracker.calculateVelocity()
                when (axis) {
                    MiniDragAxis.ExpandUp -> {
                        val commit = dragProgress.value >= 0.35f || velocity.y <= -1200f
                        if (commit) {
                            onExpand() // misma transición mini↔full, recorrida en sentido inverso
                            scope.launch { dragProgress.snapTo(0f) }
                        } else {
                            scope.launch {
                                if (reducedMotion) {
                                    dragProgress.snapTo(0f)
                                } else {
                                    // Vuelta al mini; spring rápido (se asienta bajo 300 ms), sin rebote.
                                    dragProgress.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
                                }
                            }
                        }
                    }

                    MiniDragAxis.Horizontal -> {
                        val width = size.width.toFloat()
                        val threshold = width * TrackCommitFraction
                        val dx = offsetX.value
                        when {
                            // Derecha → siguiente; izquierda → anterior (preferencia explícita).
                            dx >= threshold || velocity.x >= TrackFlingVelocity ->
                                scope.launch { trackCrossSlide(offsetX, width, exitToLeft = false, reducedMotion, onNext) }

                            dx <= -threshold || velocity.x <= -TrackFlingVelocity ->
                                scope.launch { trackCrossSlide(offsetX, width, exitToLeft = true, reducedMotion, onPrevious) }

                            else -> scope.launch {
                                if (reducedMotion) {
                                    offsetX.snapTo(0f)
                                } else {
                                    offsetX.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
                                }
                            }
                        }
                    }

                    else -> {}
                }
            }
        }

    PressableCard(
        onClick = onExpand,
        modifier = modifier.then(expandGesture).fillMaxWidth(),
        color = MarluneTheme.colors.surfaceElevated,
        shadowElevation = 6.dp, // flota sobre la barra
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tinte con el acento dinámico (colorScheme.primary); es la carátula compartida.
            TrackThumbnail(
                accent = MaterialTheme.colorScheme.primary,
                modifier = artModifier,
                size = 44.dp,
                corner = 10.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.title,
                    style = MarluneTheme.typography.titleSmall,
                    color = MarluneTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = titleModifier,
                )
                Text(
                    text = uiState.artist,
                    style = MarluneTheme.typography.bodySmall,
                    color = MarluneTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = artistModifier,
                )
            }
            MiniPlayPauseButton(isPlaying = uiState.isPlaying, onClick = onPlayPause)
        }
    }
}

/** El mini sale por un lado, se cambia la pista y el contenido nuevo entra por el opuesto. */
private suspend fun trackCrossSlide(
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
    offsetX.snapTo(-exit) // el contenido de la pista nueva entra desde el lado opuesto
    offsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium))
}

/**
 * Botón play/pausa del mini-player con el MISMO lenguaje de motion que el botón grande:
 *  - Morph de icono con crossfade + ligero cambio de escala, 150 ms.
 *  - Press-feedback: baja a 0.94 al presionar y vuelve con spring (mismos tokens que el grande).
 *  - Pulso brevísimo de acento (→ acento vivo) al pasar a "playing", que decae en 150 ms sin
 *    rebote. Micro; es acción de alta frecuencia.
 */
@Composable
private fun MiniPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "miniPlayPressScale",
    )

    // Pulso de acento al iniciar reproducción: parte de acento vivo y decae a acento (sin rebote).
    val accent = MarluneTheme.colors.accent
    val accentVivid = MarluneTheme.colors.accentVivid
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, reducedMotion) {
        if (isPlaying && !reducedMotion) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(PlayMorphMillis))
        } else {
            pulse.snapTo(0f)
        }
    }
    val iconTint = lerp(accent, accentVivid, pulse.value)

    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        },
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else {
                    (fadeIn(tween(PlayMorphMillis)) + scaleIn(tween(PlayMorphMillis), initialScale = 0.85f)) togetherWith
                        (fadeOut(tween(PlayMorphMillis)) + scaleOut(tween(PlayMorphMillis), targetScale = 0.85f))
                }
            },
            label = "miniPlayIcon",
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(
                    if (playing) R.string.player_pause else R.string.player_play,
                ),
                tint = iconTint,
            )
        }
    }
}
