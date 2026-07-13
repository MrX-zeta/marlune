package com.luis.marlune.ui.library.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.ui.components.ContextMenu
import com.luis.marlune.ui.components.ContextMenuItem
import com.luis.marlune.ui.library.LibraryEntry
import com.luis.marlune.ui.theme.MarluneTheme
import com.luis.marlune.ui.theme.placeholderAccentFor

/**
 * Fila de Biblioteca: portada teñida + título/subtítulo + "3 puntos".
 * El botón de "3 puntos" es un `IconButton` (área de toque de 48 dp) y abre el menú contextual.
 */
@Composable
fun LibraryRow(
    entry: LibraryEntry,
    coverIcon: ImageVector,
    coverShape: Shape,
    onClick: () -> Unit,
    menuItems: List<ContextMenuItem>,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val accent = remember(entry.id) { placeholderAccentFor(entry.id) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(
                selected = false,
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryCover(
            accent = accent,
            icon = coverIcon,
            shape = coverShape,
            artworkUri = entry.artworkUri,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MarluneTheme.typography.titleMedium,
                color = MarluneTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.subtitle,
                style = MarluneTheme.typography.bodyMedium,
                color = MarluneTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // "3 puntos": IconButton garantiza 48 dp de toque; ancla del menú contextual.
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.library_more_options),
                    tint = MarluneTheme.colors.textSecondary,
                )
            }
            ContextMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                items = menuItems,
            )
        }
    }
}

/** Formas de portada por categoría, expuestas para que la pantalla las asigne. */
val CircleCover: Shape = CircleShape
val RoundedCover: Shape = RoundedCornerShape(12.dp)

/** Icono placeholder de portada; el de artistas se distingue. */
fun coverIconFor(isArtist: Boolean): ImageVector =
    if (isArtist) Icons.Rounded.Person else Icons.Rounded.MusicNote
