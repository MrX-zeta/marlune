package com.luis.marlune.ui.player.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import com.luis.marlune.R
import com.luis.marlune.domain.model.RepeatMode
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme

// Lenguaje de motion del play/pausa (compartido con el botón del mini-player):
// morph un punto más visible y perceptible, sin cruzar a llamativo.
private const val PlayMorphMillis = 210
private const val PlayPulseMillis = 220
private const val PlayMorphScale = 0.8f
private const val PlayPressScale = 0.88f

/**
 * Fila de transporte: aleatorio · anterior · play/pause · siguiente · repetir.
 *
 * Motion por elemento (ver marlune-motion):
 *  - Play/pause: morph de icono 150 ms + escala de press con spring.
 *  - Prev/next: solo feedback de press (acción de alta frecuencia, sin transición).
 *  - Aleatorio/repetir: color a acento 180 ms + pequeño pop al activarse.
 */
@Composable
fun PlayerControls(
    isPlaying: Boolean,
    isShuffleOn: Boolean,
    repeatMode: RepeatMode,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StateToggle(
            active = isShuffleOn,
            icon = Icons.Rounded.Shuffle,
            contentDescription = stringResource(R.string.player_shuffle),
            onClick = onToggleShuffle,
        )
        SkipButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = stringResource(R.string.player_previous),
            onClick = onPrevious,
        )
        PlayPauseButton(isPlaying = isPlaying, onClick = onPlayPause)
        SkipButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.player_next),
            onClick = onNext,
        )
        StateToggle(
            active = repeatMode != RepeatMode.OFF,
            icon = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            contentDescription = stringResource(
                if (repeatMode == RepeatMode.ONE) R.string.player_repeat_one else R.string.player_repeat,
            ),
            onClick = onToggleRepeat,
        )
    }
}

/**
 * Botón principal: círculo de acento, morph play↔pause (~210 ms, visible) y press-feedback
 * amplio (0.88 → 1) con spring de rigidez media y asentamiento calmado, sin rebote. Al pasar a
 * "playing", el círculo da un pulso breve al acento vivo.
 */
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) PlayPressScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "playPressScale",
    )

    // Pulso de acento al iniciar reproducción: el círculo va a acento vivo y decae, calmado.
    val accentVivid = MarluneTheme.colors.accentVivid
    val basePrimary = MaterialTheme.colorScheme.primary
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, reducedMotion) {
        if (isPlaying && !reducedMotion) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(PlayPulseMillis))
        } else {
            pulse.snapTo(0f)
        }
    }
    val circleColor = lerp(basePrimary, accentVivid, pulse.value)

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = CircleShape,
        color = circleColor, // acento (sigue el color dinámico) + pulso
        contentColor = MaterialTheme.colorScheme.onPrimary,
        interactionSource = interaction,
    ) {
        Box(contentAlignment = Alignment.Center) {
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
                label = "playIconMorph",
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(
                        if (playing) R.string.player_pause else R.string.player_play,
                    ),
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

/** Prev/next: 48 dp de toque, solo escala de press. Sin transición (alta frecuencia). */
@Composable
private fun SkipButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "skipPressScale",
    )

    IconButton(onClick = onClick, interactionSource = interaction, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MarluneTheme.colors.textPrimary,
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

/** Toggle de estado (aleatorio/repetir): gris → acento en 180 ms + pop al activarse. */
@Composable
private fun StateToggle(
    active: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val tint by animateColorAsState(
        targetValue = if (active) MarluneTheme.colors.accent else MarluneTheme.colors.textTertiary,
        animationSpec = if (reducedMotion) snap() else tween(180),
        label = "toggleTint",
    )
    val pop = rememberActivationPop(trigger = active, start = 1f, peak = 1.12f)

    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                scaleX = pop
                scaleY = pop
            },
        )
    }
}

/**
 * Botón "me gusta": contorno → relleno con acento y pop de escala (0.6 → 1.2 → 1) al marcar.
 * Bajo (favorito) es de baja frecuencia, así que el feedback expresivo está permitido.
 */
@Composable
fun LikeButton(
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val tint by animateColorAsState(
        targetValue = if (isLiked) MarluneTheme.colors.accent else MarluneTheme.colors.textSecondary,
        animationSpec = if (reducedMotion) snap() else tween(180),
        label = "likeTint",
    )
    val pop = rememberActivationPop(trigger = isLiked, start = 0.6f, peak = 1.2f)

    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = stringResource(
                if (isLiked) R.string.player_unlike else R.string.player_like,
            ),
            tint = tint,
            modifier = Modifier.graphicsLayer {
                scaleX = pop
                scaleY = pop
            },
        )
    }
}

/**
 * Pop de activación: al pasar `trigger` a `true`, escala `start → peak → 1` con spring.
 * Instantáneo (sin pop) con movimiento reducido o al desactivar.
 */
@Composable
private fun rememberActivationPop(trigger: Boolean, start: Float, peak: Float): Float {
    val reducedMotion = LocalReducedMotion.current
    val scale = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger && !reducedMotion) {
            scale.snapTo(start)
            scale.animateTo(peak, tween(110, easing = FastOutSlowInEasing))
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        } else {
            scale.snapTo(1f)
        }
    }
    return scale.value
}
