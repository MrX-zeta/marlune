package com.luis.marlune.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.playback.QueueItem
import com.luis.marlune.ui.library.components.LibraryCover
import com.luis.marlune.ui.library.components.NowPlayingBars
import com.luis.marlune.ui.theme.MarluneTheme
import com.luis.marlune.ui.theme.placeholderAccentFor

private val CoverShape = RoundedCornerShape(10.dp)

/**
 * Panel "A continuación": la cola REAL del `MediaController`. Muestra lo ya reproducido (atenuado),
 * la pista actual (resaltada con el ecualizador de "sonando") y lo que viene. Tocar una fila salta a
 * esa pista (sin rearmar la cola); la X la quita de la cola (no toca biblioteca ni archivos). Se abre
 * desde Now Playing sin minimizar. Reutiliza portada, ecualizador y paleta de la Biblioteca.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: QueueUiState,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MarluneTheme.colors.surfaceElevated,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "queue-header") {
                Text(
                    text = stringResource(R.string.queue_title),
                    style = MarluneTheme.typography.titleMedium,
                    color = MarluneTheme.colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (queue.isEmpty) {
                item(key = "queue-empty") {
                    Text(
                        text = stringResource(R.string.queue_empty),
                        style = MarluneTheme.typography.bodyMedium,
                        color = MarluneTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }
            } else {
                itemsIndexed(
                    queue.items,
                    key = { index, item -> "$index:${item.mediaId}" },
                    contentType = { _, _ -> "queueRow" },
                ) { index, item ->
                    QueueRow(
                        item = item,
                        isCurrent = index == queue.currentIndex,
                        isPlaying = queue.isPlaying,
                        played = index < queue.currentIndex,
                        onClick = {
                            onJumpTo(index)
                            onDismiss()
                        },
                        onRemove = { onRemove(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    played: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val accent = remember(item.mediaId) { placeholderAccentFor(item.mediaId.toLongOrNull() ?: 0L) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            // Lo ya reproducido se atenúa; la pista actual y lo que viene, a plena opacidad.
            .graphicsLayer { alpha = if (played) 0.5f else 1f }
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryCover(
            accent = accent,
            icon = Icons.Rounded.MusicNote,
            shape = CoverShape,
            artworkUri = item.artworkUri,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MarluneTheme.typography.titleMedium,
                color = if (isCurrent) MarluneTheme.colors.accent else MarluneTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist,
                style = MarluneTheme.typography.bodyMedium,
                color = MarluneTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // La pista actual muestra el ecualizador (reproduciendo ahora); el resto, la acción de quitar.
        if (isCurrent) {
            NowPlayingBars(
                color = MarluneTheme.colors.accent,
                isPlaying = isPlaying,
                modifier = Modifier.padding(start = 8.dp, end = 12.dp),
            )
        } else {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.queue_remove),
                    tint = MarluneTheme.colors.textSecondary,
                )
            }
        }
    }
}
