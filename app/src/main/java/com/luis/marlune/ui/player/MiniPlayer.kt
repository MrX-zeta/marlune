package com.luis.marlune.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.ui.home.components.TrackThumbnail
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme

/**
 * Mini-player fijo sobre la barra inferior. Tocarlo (fuera del botón) expande al reproductor
 * completo; la carátula es el elemento compartido que viaja en esa transición ([artModifier]).
 * El botón play/pausa hace morph de icono (150 ms); no lleva marea para mantenerlo compacto.
 */
@Composable
fun MiniPlayer(
    uiState: PlayerUiState,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    artModifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current

    Surface(
        onClick = onExpand,
        modifier = modifier.fillMaxWidth(),
        color = MarluneTheme.colors.surfaceElevated,
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
                )
                Text(
                    text = uiState.artist,
                    style = MarluneTheme.typography.bodySmall,
                    color = MarluneTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onPlayPause) {
                Crossfade(
                    targetState = uiState.isPlaying,
                    animationSpec = if (reducedMotion) snap() else tween(150),
                    label = "miniPlayIcon",
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (playing) R.string.player_pause else R.string.player_play,
                        ),
                        tint = MarluneTheme.colors.accent,
                    )
                }
            }
        }
    }
}
