package com.luis.marlune.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.ui.components.PressableCard
import com.luis.marlune.ui.home.LibraryShortcut
import com.luis.marlune.ui.theme.MarluneTheme

/**
 * Grid 2×2 de accesos rápidos. Solo feedback de press (0.97) vía [PressableCard]. La tarjeta
 * "Me gusta" muestra el conteo real de favoritos ([likedCount]); las demás, solo su etiqueta.
 */
@Composable
fun LibraryShortcutsGrid(
    onShortcutClick: (LibraryShortcut) -> Unit,
    likedCount: Int,
    modifier: Modifier = Modifier,
) {
    val shortcuts = LibraryShortcut.entries
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        shortcuts.chunked(2).forEach { rowShortcuts ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowShortcuts.forEach { shortcut ->
                    ShortcutCard(
                        shortcut = shortcut,
                        count = if (shortcut == LibraryShortcut.LIKED && likedCount > 0) likedCount else null,
                        onClick = { onShortcutClick(shortcut) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutCard(
    shortcut: LibraryShortcut,
    count: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressableCard(onClick = onClick, modifier = modifier.height(64.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = shortcut.icon,
                contentDescription = null,
                tint = MarluneTheme.colors.accent,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(shortcut.labelRes),
                style = MarluneTheme.typography.titleSmall,
                color = MarluneTheme.colors.textPrimary,
            )
            if (count != null) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = count.toString(),
                    style = MarluneTheme.typography.bodyMedium,
                    color = MarluneTheme.colors.textSecondary,
                )
            }
        }
    }
}

private val LibraryShortcut.icon: ImageVector
    get() = when (this) {
        LibraryShortcut.LIKED -> Icons.Rounded.Favorite
        LibraryShortcut.PLAYLISTS -> Icons.AutoMirrored.Rounded.QueueMusic
        LibraryShortcut.ALBUMS -> Icons.Rounded.Album
        LibraryShortcut.ARTISTS -> Icons.Rounded.Person
    }

private val LibraryShortcut.labelRes: Int
    get() = when (this) {
        LibraryShortcut.LIKED -> R.string.shortcut_liked
        LibraryShortcut.PLAYLISTS -> R.string.shortcut_playlists
        LibraryShortcut.ALBUMS -> R.string.shortcut_albums
        LibraryShortcut.ARTISTS -> R.string.shortcut_artists
    }
