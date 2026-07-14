package com.luis.marlune.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.playback.TrackChange
import com.luis.marlune.ui.components.PressableCard
import com.luis.marlune.ui.home.components.TrackThumbnail
import com.luis.marlune.ui.player.components.TrackSwipeDirection
import com.luis.marlune.ui.player.components.resolveTrackSwipe
import com.luis.marlune.ui.player.components.runTrackSlideAnimation
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// Lenguaje de motion del play/pausa (idéntico al botón grande de Now Playing).
private const val PlayMorphMillis = 210
private const val PlayPulseMillis = 220
private const val PlayMorphScale = 0.8f
private const val PlayPressScale = 0.88f

// Recorrido (corto) para llegar al progreso 1 y magnitud del "levantamiento" acoplado.
private val ExpandDragDistance = 100.dp
private val ExpandLift = 20.dp
private const val TrackCommitFraction = 0.22f // ~22 % del ancho para confirmar cambio de pista
private const val TrackFlingVelocity = 1000f
private const val TapHighlightDelayMillis = 100L // espera antes de iluminar (evita destello al deslizar)

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
    // State layer de la card, controlado por el detector: solo se ilumina en tap real, no al deslizar.
    val interactionSource = remember { MutableInteractionSource() }
    var cardWidthPx by remember { mutableStateOf(0f) }

    // Misma fuente única que Now Playing: la dirección sale del cambio de pista del player (reason
    // de Media3). NEXT/PREVIOUS deslizan; DIRECT (carga por selección) NO desliza (crossfade).
    var lastHandledTransition by remember { mutableStateOf(uiState.trackTransition.id) }
    LaunchedEffect(uiState.trackTransition.id) {
        val transition = uiState.trackTransition
        if (transition.id != lastHandledTransition) {
            lastHandledTransition = transition.id
            if (cardWidthPx > 0f) {
                when (transition.kind) {
                    TrackChange.NEXT -> runTrackSlideAnimation(true, offsetX, cardWidthPx, reducedMotion)
                    TrackChange.PREVIOUS -> runTrackSlideAnimation(false, offsetX, cardWidthPx, reducedMotion)
                    TrackChange.DIRECT -> {} // carga directa: sin slide
                }
            }
        }
    }

    // IMPORTANTE: el detector de gestos va ANTES del graphicsLayer que traslada la tarjeta. Si fuera
    // al revés, el pointerInput viviría dentro de la capa que él mismo mueve y las coordenadas del
    // arrastre se retroalimentarían (amortiguan/enturbian el desplazamiento → delay y dirección poco
    // fiable). Aquí el gesto se mide en el espacio estable del padre; el graphicsLayer solo pinta.
    val expandGesture = Modifier
        .pointerInput(reducedMotion) {
            val touchSlop = viewConfiguration.touchSlop
            val distancePx = ExpandDragDistance.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)
                var axis = MiniDragAxis.Undecided
                var consumedByChild = false // p. ej. el botón play recoge el tap
                // Acumulado horizontal desde el inicio de ESTE gesto (parte de 0). Decide la
                // dirección por su signo; nunca por la posición absoluta del toque.
                var dragX = 0f

                // Press diferido: la iluminación solo aparece si el dedo se mantiene sin superar el
                // slop (se confirma como tap). Se cancela en cuanto empieza el arrastre.
                var pressInteraction: PressInteraction.Press? = null
                val pressJob = scope.launch {
                    delay(TapHighlightDelayMillis)
                    val press = PressInteraction.Press(down.position)
                    pressInteraction = press
                    interactionSource.emit(press)
                }
                fun cancelHighlight() {
                    pressJob.cancel()
                    pressInteraction?.let { press ->
                        pressInteraction = null
                        scope.launch { interactionSource.emit(PressInteraction.Cancel(press)) }
                    }
                }

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    if (axis == MiniDragAxis.Undecided && change.isConsumed) consumedByChild = true
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
                            cancelHighlight() // empezó el arrastre → sin iluminación; manda el gesto
                        }
                    }

                    // Se consume el eje bloqueado (así no dispara el tap). El tap (sin pasar el slop)
                    // no se consume → se resuelve como tap al soltar. Un arrastre que EMPIEZA sobre el
                    // mini-player lo reclama esta card desde el primer movimiento, para que la lista
                    // scrolleable de detrás (Biblioteca) no alcance su slop vertical y robe el gesto
                    // (era la causa del delay/titubeo del swipe solo en Biblioteca).
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
                            dragX += change.positionChange().x
                            val target = dragX // la tarjeta sigue el acumulado (misma dirección)
                            scope.launch { offsetX.snapTo(target) }
                        }

                        // Hacia abajo sobre el mini: sin acción, pero se consume (la lista no scrollea).
                        MiniDragAxis.Down -> change.consume()

                        // Antes de decidir el eje: reclama el movimiento (salvo que un hijo lo tomara,
                        // p. ej. el botón play) para no competir con el scroll de la lista de detrás.
                        MiniDragAxis.Undecided ->
                            if (!consumedByChild && change.positionChange() != Offset.Zero) change.consume()
                    }
                }

                val velocity = velocityTracker.calculateVelocity()
                when (axis) {
                    MiniDragAxis.Undecided -> {
                        // Sin arrastre. Si un hijo (botón play) recogió el tap, no ilumines ni expandas.
                        if (consumedByChild) {
                            cancelHighlight()
                        } else {
                            // Tap real: asegura el press (si el retardo no llegó) y suéltalo → ripple.
                            pressJob.cancel()
                            val press = pressInteraction ?: PressInteraction.Press(down.position).also {
                                pressInteraction = it
                                scope.launch { interactionSource.emit(it) }
                            }
                            scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                            onExpand()
                        }
                    }

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
                        // Solo elige QUÉ comando pedir; la animación de confirmación la corre el
                        // observador de trackTransition (fuente única de dirección). Si NO hay pista
                        // a la que saltar (extremo/cola de 1), la tarjeta REGRESA con spring.
                        fun settleBack() = scope.launch {
                            if (reducedMotion) offsetX.snapTo(0f)
                            else offsetX.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
                        }
                        when (
                            resolveTrackSwipe(dragX, velocity.x, width * TrackCommitFraction, TrackFlingVelocity)
                        ) {
                            TrackSwipeDirection.NEXT -> if (uiState.hasNext) onNext() else settleBack()
                            TrackSwipeDirection.PREVIOUS -> if (uiState.hasPrevious) onPrevious() else settleBack()
                            null -> settleBack()
                        }
                    }

                    else -> {}
                }
            }
        }
        .graphicsLayer {
            // Efecto acoplado (solo visual): la tarjeta se levanta y crece al subir, o acompaña el
            // dedo en horizontal al cambiar de pista. No afecta a las coordenadas del gesto (va debajo).
            translationX = offsetX.value
            translationY = -dragProgress.value * ExpandLift.toPx()
            val grow = 1f + dragProgress.value * 0.06f
            scaleX = grow
            scaleY = grow
        }

    PressableCard(
        onClick = null, // el tap lo resuelve el detector unificado (para distinguir tap de swipe)
        modifier = modifier
            .then(expandGesture)
            .fillMaxWidth()
            .onSizeChanged { cardWidthPx = it.width.toFloat() },
        color = MarluneTheme.colors.surfaceElevated,
        shadowElevation = 6.dp, // flota sobre la barra
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Carátula real (placeholder teñido con el acento dinámico); es el elemento compartido.
            TrackThumbnail(
                accent = MaterialTheme.colorScheme.primary,
                artworkUri = uiState.artworkUri,
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

/**
 * Botón play/pausa del mini-player con el MISMO lenguaje de motion que el botón grande:
 *  - Morph de icono play↔pause con crossfade + escala, ~210 ms (visible, no llamativo).
 *  - Press-feedback amplio (0.88 → 1) con spring de rigidez media y asentamiento calmado, sin rebote.
 *  - Pulso de acento (→ acento vivo) al pasar a "playing", que decae en ~220 ms sin rebote.
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
        targetValue = if (pressed && !reducedMotion) PlayPressScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "miniPlayPressScale",
    )

    // Pulso de acento al iniciar reproducción: parte de acento vivo y decae a acento (sin rebote).
    val accent = MarluneTheme.colors.accent
    val accentVivid = MarluneTheme.colors.accentVivid
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, reducedMotion) {
        if (isPlaying && !reducedMotion) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(PlayPulseMillis))
        } else {
            pulse.snapTo(0f)
        }
    }
    val iconTint = lerp(accent, accentVivid, pulse.value)

    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .size(52.dp) // ligeramente más grande que los 48 dp por defecto
            .graphicsLayer {
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
                    (fadeIn(tween(PlayMorphMillis)) + scaleIn(tween(PlayMorphMillis), initialScale = PlayMorphScale)) togetherWith
                        (fadeOut(tween(PlayMorphMillis)) + scaleOut(tween(PlayMorphMillis), targetScale = PlayMorphScale))
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
                modifier = Modifier.size(28.dp), // icono un poco mayor (24 dp por defecto)
            )
        }
    }
}
