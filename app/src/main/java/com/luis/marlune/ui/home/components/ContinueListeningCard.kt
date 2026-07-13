package com.luis.marlune.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.ui.components.Marea
import com.luis.marlune.ui.components.PressableCard
import com.luis.marlune.ui.home.ContinueListeningUi
import com.luis.marlune.ui.theme.MarluneTheme
import com.luis.marlune.ui.theme.placeholderAccentFor

/**
 * Card "Continuar escuchando". Comparte lenguaje visual con el grid (misma superficie, radio y
 * feedback de press vía [PressableCard]) para que no parezcan dos estéticas. Su progreso lleva
 * la marea a amplitud reducida (eco de la firma), no una barra plana.
 */
@Composable
fun ContinueListeningCard(
    state: ContinueListeningUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = placeholderAccentFor(state.track.id)

    PressableCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackThumbnail(accent = accent, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_continue_listening).uppercase(),
                        style = MarluneTheme.typography.labelMedium,
                        color = MarluneTheme.colors.textSecondary,
                    )
                    Text(
                        text = state.track.title,
                        style = MarluneTheme.typography.titleMedium,
                        color = MarluneTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.track.artist,
                        style = MarluneTheme.typography.bodyMedium,
                        color = MarluneTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // Marea a amplitud reducida, teñida con el acento de la pista; corre sobre la
            // superficie elevada de la card, así que el anillo del playhead usa ese color.
            Marea(
                progress = state.progress,
                isPlaying = state.isPlaying,
                waveColor = accent,
                playheadColor = accent,
                backgroundColor = MarluneTheme.colors.surfaceElevated,
                amplitudeScale = 0.6f,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
