package com.luis.marlune.ui.search.components

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.ui.components.PressableCard
import com.luis.marlune.ui.theme.MarluneTheme

/**
 * Accesos directos a las secciones de la biblioteca (Álbumes · Artistas · Listas · Me gusta) para el
 * estado inicial de Buscar. Es un índice, no un catálogo: cuatro cards aireadas en 2×2, con el mismo
 * lenguaje visual que los accesos de Inicio (icono en acento + etiqueta, feedback de press 0.97 vía
 * [PressableCard]). Reutiliza las rutas ya existentes de cada pantalla.
 */
@Composable
fun SearchCategoryGrid(
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenLiked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards = listOf<Triple<ImageVector, Int, () -> Unit>>(
        Triple(Icons.Rounded.Album, R.string.shortcut_albums, onOpenAlbums),
        Triple(Icons.Rounded.Person, R.string.shortcut_artists, onOpenArtists),
        Triple(Icons.AutoMirrored.Rounded.QueueMusic, R.string.shortcut_playlists, onOpenPlaylists),
        Triple(Icons.Rounded.Favorite, R.string.shortcut_liked, onOpenLiked),
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cards.chunked(2).forEach { rowCards ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowCards.forEach { (icon, labelRes, onClick) ->
                    CategoryCard(
                        icon = icon,
                        label = stringResource(labelRes),
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressableCard(onClick = onClick, modifier = modifier.fillMaxWidth().height(64.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MarluneTheme.colors.accent,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MarluneTheme.typography.titleSmall,
                color = MarluneTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
