package com.luis.marlune.ui.player

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luis.marlune.R
import com.luis.marlune.domain.model.RepeatMode
import com.luis.marlune.ui.components.Marea
import com.luis.marlune.ui.player.components.AlbumArt
import com.luis.marlune.ui.player.components.LikeButton
import com.luis.marlune.ui.player.components.PlayerControls
import com.luis.marlune.ui.theme.LocalMarluneAccentController
import com.luis.marlune.ui.theme.MarluneTheme

/**
 * Punto de entrada con estado del Reproductor: observa el [PlayerViewModel] y delega en
 * [PlayerScreen] sin estado. `onMinimize` lo resuelve la navegación (mini-player).
 */
@Composable
fun PlayerRoute(
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlayerScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onMinimize = onMinimize,
        modifier = modifier,
    )
}

/**
 * Pantalla del Reproductor (sin estado).
 *
 * Integra el acento dinámico de la carátula (prompt 1): al cambiar `artwork` se recalcula el
 * acento —play, toggles activos y marea lo siguen vía `colorScheme.primary`— y fondos/texto se
 * mantienen neutros. Haptics ligeros (CLOCK_TICK) en play/pausa y cambio de pista.
 */
@Composable
fun PlayerScreen(
    uiState: PlayerUiState,
    onEvent: (PlayerEvent) -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentController = LocalMarluneAccentController.current
    LaunchedEffect(uiState.artwork) {
        val artwork = uiState.artwork
        if (artwork != null) {
            accentController.updateFromArtwork(artwork.asAndroidBitmap())
        } else {
            accentController.reset()
        }
    }

    val view = LocalView.current
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    val onPlayPause = { tick(); onEvent(PlayerEvent.PlayPause) }
    val onNext = { tick(); onEvent(PlayerEvent.Next) }
    val onPrevious = { tick(); onEvent(PlayerEvent.Previous) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            PlayerTopBar(source = uiState.source, onMinimize = onMinimize)

            Spacer(Modifier.weight(0.5f))

            AlbumArt(
                artwork = uiState.artwork,
                onPrevious = onPrevious,
                onNext = onNext,
                onMinimize = onMinimize,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.weight(0.5f))

            TrackInfo(
                title = uiState.title,
                artist = uiState.artist,
                isLiked = uiState.isLiked,
                onToggleLike = { onEvent(PlayerEvent.ToggleLike) },
            )

            Spacer(Modifier.height(20.dp))

            // La marea usa por defecto colorScheme.background para el anillo del playhead.
            Marea(
                progress = uiState.progress,
                isPlaying = uiState.isPlaying,
            )
            TimeRow(positionMs = uiState.positionMs, durationMs = uiState.durationMs)

            Spacer(Modifier.height(24.dp))

            PlayerControls(
                isPlaying = uiState.isPlaying,
                isShuffleOn = uiState.isShuffleOn,
                repeatMode = uiState.repeatMode,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onToggleShuffle = { onEvent(PlayerEvent.ToggleShuffle) },
                onToggleRepeat = { onEvent(PlayerEvent.ToggleRepeat) },
            )

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlayerTopBar(
    source: String,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onMinimize) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.player_minimize),
                tint = MarluneTheme.colors.textPrimary,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.player_playing_from).uppercase(),
                style = MarluneTheme.typography.labelMedium,
                color = MarluneTheme.colors.textSecondary, // contraste corregido (antes apagado)
            )
            Text(
                text = source,
                style = MarluneTheme.typography.titleSmall,
                color = MarluneTheme.colors.textPrimary,
            )
        }
        // Equilibra el ancho del IconButton izquierdo para centrar el bloque.
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun TrackInfo(
    title: String,
    artist: String,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MarluneTheme.typography.titleLarge,
                color = MarluneTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // "Me gusta" vive en la fila del título, no sobre la carátula.
            LikeButton(isLiked = isLiked, onClick = onToggleLike)
        }
        // Sin indicador de verificación/descarga: la biblioteca es 100 % local.
        Text(
            text = artist,
            style = MarluneTheme.typography.bodyMedium,
            color = MarluneTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TimeRow(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatTime(positionMs),
            style = MarluneTheme.typography.bodySmall,
            color = MarluneTheme.colors.textSecondary,
        )
        Text(
            text = formatTime(durationMs),
            style = MarluneTheme.typography.bodySmall,
            color = MarluneTheme.colors.textSecondary,
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0910, heightDp = 780)
@Composable
private fun PlayerScreenPlayingPreview() {
    MarluneTheme {
        PlayerScreen(
            uiState = PlayerUiState.Preview.copy(isPlaying = true, isShuffleOn = true, isLiked = true),
            onEvent = {},
            onMinimize = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0910, heightDp = 780)
@Composable
private fun PlayerScreenPausedPreview() {
    MarluneTheme {
        PlayerScreen(
            uiState = PlayerUiState.Preview.copy(isPlaying = false, repeatMode = RepeatMode.ONE),
            onEvent = {},
            onMinimize = {},
        )
    }
}
