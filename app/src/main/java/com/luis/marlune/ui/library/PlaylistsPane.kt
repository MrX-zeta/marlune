package com.luis.marlune.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.ui.components.ContextMenuItem
import com.luis.marlune.ui.components.EmptyState
import com.luis.marlune.ui.components.StaggeredReveal
import com.luis.marlune.ui.library.components.CircleCover
import com.luis.marlune.ui.library.components.LibraryRow
import com.luis.marlune.ui.theme.MarluneTheme

private const val StaggerVisibleCount = 10

/**
 * Contenido del chip "Listas": un acceso para crear la primera lista, luego las listas del usuario
 * (reutilizando [LibraryRow]) con menú Renombrar/Borrar, o un vacío que invita a crear. Los diálogos
 * (crear, renombrar, confirmar borrado) viven aquí como estado local.
 */
@Composable
fun PlaylistsPane(
    playlists: List<LibraryEntry>,
    bottomPadding: Dp,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<LibraryEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<LibraryEntry?>(null) }

    androidx.compose.foundation.lazy.LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "new-playlist", contentType = "newPlaylist") {
            NewPlaylistRow(onClick = { showCreate = true })
        }
        if (playlists.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = stringResource(R.string.playlists_empty_title),
                    hint = stringResource(R.string.playlists_empty_hint),
                )
            }
        } else {
            itemsIndexed(playlists, key = { _, e -> e.id }, contentType = { _, _ -> "playlistRow" }) { index, entry ->
                StaggeredReveal(index = index, enabled = index < StaggerVisibleCount) {
                    LibraryRow(
                        entry = entry,
                        coverIcon = Icons.AutoMirrored.Rounded.QueueMusic,
                        coverShape = CircleCover,
                        onClick = { onOpen(entry.id) },
                        menuItems = listOf(
                            ContextMenuItem(R.string.menu_playlist_rename) { renameTarget = entry },
                            ContextMenuItem(R.string.menu_playlist_delete) { deleteTarget = entry },
                        ),
                    )
                }
            }
        }
    }

    if (showCreate) {
        PlaylistNameDialog(
            titleRes = R.string.playlist_create_title,
            confirmRes = R.string.action_create,
            initial = "",
            onConfirm = { onCreate(it); showCreate = false },
            onDismiss = { showCreate = false },
        )
    }
    renameTarget?.let { target ->
        PlaylistNameDialog(
            titleRes = R.string.playlist_rename_title,
            confirmRes = R.string.action_save,
            initial = target.title,
            onConfirm = { onRename(target.id, it); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.playlist_delete_title)) },
            text = { Text(stringResource(R.string.playlist_delete_body)) },
            confirmButton = {
                TextButton(onClick = { onDelete(target.id); deleteTarget = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Acceso para crear una lista nueva (icono + texto en acento). */
@Composable
private fun NewPlaylistRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = MarluneTheme.colors.accent,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.playlist_new_action),
            style = MarluneTheme.typography.titleMedium,
            color = MarluneTheme.colors.accent,
        )
    }
}
