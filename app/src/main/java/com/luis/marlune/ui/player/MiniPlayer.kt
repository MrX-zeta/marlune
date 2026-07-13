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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.ui.components.PressableCard
import com.luis.marlune.ui.home.components.TrackThumbnail
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme

private const val PlayMorphMillis = 150

/**
 * Mini-player como tarjeta flotante sobre la barra inferior. Tocarlo (fuera del botón) expande
 * al reproductor completo; la carátula es el elemento compartido que viaja en esa transición
 * ([artModifier]). El botón play/pausa comparte el lenguaje de motion del botón grande de
 * Now Playing; no lleva marea para mantenerlo compacto.
 *
 * Usa [PressableCard] para heredar el mismo radio de esquina que el resto de tarjetas de la app;
 * la sombra suave y el inset lateral (aplicados por quien lo coloca) lo despegan de la barra.
 */
@Composable
fun MiniPlayer(
    uiState: PlayerUiState,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    artModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    artistModifier: Modifier = Modifier,
) {
    PressableCard(
        onClick = onExpand,
        modifier = modifier.fillMaxWidth(),
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
