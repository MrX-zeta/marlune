package com.luis.marlune.ui.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.ui.player.LyricsCandidateUi
import com.luis.marlune.ui.player.LyricsPickerState
import com.luis.marlune.ui.theme.MarluneTheme

/**
 * Hoja "Cambiar letra": lista las versiones que LRCLIB tiene realmente para que el usuario elija (no
 * adivinar). Cada fila: artista · álbum · duración + etiqueta "Sincronizada"/"Solo texto". Marca la
 * activa y ofrece volver a la automática. Estados de carga/vacío/error reutilizan los mensajes de letras.
 * Sobria, paleta del panel; la elección se aplica en vivo y la hoja se cierra (lo gestiona el ViewModel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsPickerSheet(
    state: LyricsPickerState,
    onChoose: (Long) -> Unit,
    onUseAutomatic: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MarluneTheme.colors.surfaceElevated,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .padding(horizontal = 20.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.lyrics_picker_title),
                    style = MarluneTheme.typography.titleMedium,
                    color = MarluneTheme.colors.textPrimary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            when (state) {
                LyricsPickerState.Loading -> item { Note(stringResource(R.string.lyrics_loading)) }
                is LyricsPickerState.Error -> item {
                    Note(stringResource(if (state.offline) R.string.lyrics_no_connection else R.string.lyrics_service_error))
                }
                is LyricsPickerState.Ready -> if (state.candidates.isEmpty()) {
                    item { Note(stringResource(R.string.lyrics_picker_empty)) }
                } else {
                    if (state.activeId != null) {
                        item { AutomaticRow(onClick = onUseAutomatic) }
                    }
                    items(state.candidates, key = { it.id }) { candidate ->
                        CandidateRow(
                            candidate = candidate,
                            active = candidate.id == state.activeId,
                            onClick = { onChoose(candidate.id) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.padding(bottom = 16.dp)) }
        }
    }
}

@Composable
private fun CandidateRow(candidate: LyricsCandidateUi, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.artist.ifBlank { stringResource(R.string.lyrics_picker_unknown_artist) },
                style = MarluneTheme.typography.bodyLarge,
                color = MarluneTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.padding(top = 2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = secondaryLine(candidate),
                    style = MarluneTheme.typography.bodySmall,
                    color = MarluneTheme.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(if (candidate.synced) R.string.lyrics_tag_synced else R.string.lyrics_tag_plain),
                    style = MarluneTheme.typography.labelMedium,
                    color = if (candidate.synced) MarluneTheme.colors.accent else MarluneTheme.colors.textTertiary,
                    maxLines = 1,
                )
            }
        }
        if (active) {
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.lyrics_picker_active),
                tint = MarluneTheme.colors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Fila para descartar la elección y volver a la automática. */
@Composable
private fun AutomaticRow(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.lyrics_use_automatic),
        style = MarluneTheme.typography.bodyLarge,
        color = MarluneTheme.colors.accent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun secondaryLine(candidate: LyricsCandidateUi): String {
    val parts = buildList {
        if (candidate.album.isNotBlank()) add(candidate.album)
        if (candidate.durationSec > 0) add(formatDuration(candidate.durationSec))
    }
    return parts.joinToString(" · ")
}

private fun formatDuration(totalSec: Long): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

@Composable
private fun Note(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MarluneTheme.typography.bodyMedium,
            color = MarluneTheme.colors.textTertiary,
        )
    }
}
