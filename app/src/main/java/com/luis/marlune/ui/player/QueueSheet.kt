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
import androidx.compose.foundation.lazy.items
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
 * Panel "A continuación": la cola REAL del `MediaController`, mirando SOLO hacia adelante (estilo
 * Spotify). No muestra el historial (lo ya reproducido): la lista EMPIEZA en la pista actual.
 *  - Fila 1: pista actual, resaltada con el ecualizador de "sonando", sin ✕.
 *  - "A continuación en la cola": lo añadido a mano con "Añadir a la cola" que aún no ha sonado
 *    (justo debajo de la actual; se omite si no hay).
 *  - "A continuación de: <origen>": el resto de lo que viene por contexto (biblioteca, álbum…).
 *
 * Solo es presentación: los índices que se pasan a saltar/quitar son los REALES de la cola del
 * player. Tocar una fila salta a esa pista (sin rearmar la cola); la ✕ la quita.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: QueueUiState,
    source: String,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val current = queue.items.getOrNull(queue.currentIndex)
    // Solo lo que VIENE (índice REAL > actual); el historial no se muestra. Se separan los añadidos a
    // mano (sección manual) del resto (contexto); un manual sale de su sección al pasar a ser la actual.
    val upcoming = buildList {
        for (i in (queue.currentIndex + 1) until queue.items.size) add(i to queue.items[i])
    }
    val manualUpcoming = upcoming.filter { (_, item) -> item.manual }
    val contextUpcoming = upcoming.filterNot { (_, item) -> item.manual }

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
            if (current == null) {
                item(key = "queue-empty") {
                    Text(
                        text = stringResource(R.string.queue_empty),
                        style = MarluneTheme.typography.bodyMedium,
                        color = MarluneTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }
                return@LazyColumn
            }

            // Fila 1: la pista actual (resaltada, con ecualizador, sin ✕).
            item(key = "cur:${queue.currentIndex}:${current.mediaId}", contentType = "queueRow") {
                QueueRow(
                    item = current,
                    isCurrent = true,
                    isPlaying = queue.isPlaying,
                    onClick = { onJumpTo(queue.currentIndex); onDismiss() },
                    onRemove = {},
                )
            }

            // Sección "A continuación en la cola": añadidas a mano y aún no reproducidas (solo si hay).
            if (manualUpcoming.isNotEmpty()) {
                item(key = "hdr-manual") { QueueSectionHeader(stringResource(R.string.queue_section_added)) }
                items(
                    manualUpcoming,
                    key = { (index, item) -> "m:$index:${item.mediaId}" },
                    contentType = { _ -> "queueRow" },
                ) { (index, item) ->
                    QueueRow(
                        item = item,
                        isCurrent = false,
                        isPlaying = queue.isPlaying,
                        onClick = { onJumpTo(index); onDismiss() },
                        onRemove = { onRemove(index) },
                    )
                }
            }

            // Sección "A continuación de: <origen>": el resto de lo que viene por contexto.
            if (contextUpcoming.isNotEmpty()) {
                item(key = "hdr-context") {
                    QueueSectionHeader(stringResource(R.string.queue_section_from, source))
                }
                items(
                    contextUpcoming,
                    key = { (index, item) -> "c:$index:${item.mediaId}" },
                    contentType = { _ -> "queueRow" },
                ) { (index, item) ->
                    QueueRow(
                        item = item,
                        isCurrent = false,
                        isPlaying = queue.isPlaying,
                        onClick = { onJumpTo(index); onDismiss() },
                        onRemove = { onRemove(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueSectionHeader(text: String) {
    Text(
        text = text,
        style = MarluneTheme.typography.labelLarge,
        color = MarluneTheme.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun QueueRow(
    item: QueueItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val accent = remember(item.mediaId) { placeholderAccentFor(item.mediaId.toLongOrNull() ?: 0L) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
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
