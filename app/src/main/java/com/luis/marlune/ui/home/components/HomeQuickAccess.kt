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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Shuffle
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
import com.luis.marlune.ui.home.ContinueInfo
import com.luis.marlune.ui.theme.MarluneTheme

/**
 * Accesos rápidos de Inicio (mismo estilo de card que antes, feedback de press 0.97). Reemplaza el
 * catálogo (Álbumes/Artistas/Listas) por acciones contextuales:
 *  - "Me gusta" y "Añadidas recientemente" NAVEGAN a su lista.
 *  - "Mix de tu biblioteca" reproduce una cola barajada.
 *  - "Continuar" (solo si hay [continueSession]) reanuda la sesión guardada; muestra carátula +
 *    título de la última pista. Sin barra de progreso (no es un clon del mini-player).
 *
 * Con 3 o 4 cards se reacomoda solo en filas de 2 (la última fila impar deja media columna libre).
 */
@Composable
fun HomeQuickAccess(
    continueSession: ContinueInfo?,
    onLiked: () -> Unit,
    onRecentlyAdded: () -> Unit,
    onMix: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards = buildList<@Composable () -> Unit> {
        if (continueSession != null) {
            add { ContinueCard(info = continueSession, onClick = onContinue) }
        }
        add {
            IconCard(
                icon = Icons.Rounded.Favorite,
                label = stringResource(R.string.shortcut_liked),
                onClick = onLiked,
            )
        }
        add {
            IconCard(
                icon = Icons.Rounded.NewReleases,
                label = stringResource(R.string.shortcut_recently_added),
                onClick = onRecentlyAdded,
            )
        }
        add {
            IconCard(
                icon = Icons.Rounded.Shuffle,
                label = stringResource(R.string.shortcut_mix),
                onClick = onMix,
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cards.chunked(2).forEach { rowCards ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowCards.forEach { card ->
                    Row(modifier = Modifier.weight(1f)) { card() }
                }
                // Fila impar: media columna libre para no estirar la card suelta a todo el ancho.
                if (rowCards.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Card de icono + etiqueta, sin adornos: las cuatro comparten el mismo lenguaje limpio. */
@Composable
private fun IconCard(
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

/** Card de reanudación: carátula (real, vía [TrackThumbnail]) + "Continuar" + última pista. */
@Composable
private fun ContinueCard(
    info: ContinueInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressableCard(onClick = onClick, modifier = modifier.fillMaxWidth().height(64.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackThumbnail(
                accent = MarluneTheme.colors.accent,
                artworkUri = info.artworkUri,
                size = 44.dp,
                corner = 10.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.shortcut_continue),
                    style = MarluneTheme.typography.titleSmall,
                    color = MarluneTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = info.title,
                    style = MarluneTheme.typography.bodySmall,
                    color = MarluneTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
