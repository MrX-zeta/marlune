package com.luis.marlune.ui.library

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.marlune.R
import com.luis.marlune.di.rememberPlaylistRepository
import com.luis.marlune.ui.theme.MarluneTheme
import kotlinx.coroutines.launch

/** Diálogo simple de nombre (crear/renombrar): campo de texto + confirmar/cancelar. Reutilizable. */
@Composable
fun PlaylistNameDialog(
    titleRes: Int,
    confirmRes: Int,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.playlist_name_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(confirmRes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Selector "Añadir a lista" para una canción: lista las playlists existentes + "+ Nueva lista"
 * (crear y añadir en un paso). Evita duplicados (avisa "Ya está en la lista"). Persiste en Room.
 */
@Composable
fun AddToPlaylistSheet(songId: Long, onDismiss: () -> Unit) {
    val repo = rememberPlaylistRepository()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playlists by repo.playlists.collectAsStateWithLifecycle(emptyList())
    var showCreate by remember { mutableStateOf(false) }

    // onDismiss() se llama DENTRO de la corrutina, tras persistir: si se llamara antes, al desmontar
    // el diálogo se cancelaría este `scope` (rememberCoroutineScope) y la escritura no terminaría.
    fun addTo(playlistId: Long, name: String) {
        scope.launch {
            val added = repo.addSong(playlistId, songId)
            val msg = if (added) context.getString(R.string.playlist_song_added, name)
            else context.getString(R.string.playlist_song_exists)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_playlist_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                SelectorRow(
                    icon = Icons.Rounded.Add,
                    label = stringResource(R.string.playlist_new_option),
                    accent = true,
                    onClick = { showCreate = true },
                )
                playlists.forEach { playlist ->
                    SelectorRow(
                        icon = null,
                        label = playlist.name,
                        accent = false,
                        onClick = { addTo(playlist.id, playlist.name) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (showCreate) {
        PlaylistNameDialog(
            titleRes = R.string.playlist_create_title,
            confirmRes = R.string.action_create,
            initial = "",
            onConfirm = { name ->
                showCreate = false
                scope.launch {
                    repo.createAndAdd(name, songId)
                    Toast.makeText(context, context.getString(R.string.playlist_song_added, name), Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun SelectorRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    accent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = MarluneTheme.colors.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = label,
            style = MarluneTheme.typography.bodyLarge,
            color = if (accent) MarluneTheme.colors.accent else MarluneTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
