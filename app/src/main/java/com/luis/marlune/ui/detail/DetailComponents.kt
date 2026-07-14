package com.luis.marlune.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.domain.model.Album
import com.luis.marlune.domain.model.Artist
import com.luis.marlune.domain.model.Song
import com.luis.marlune.ui.library.LibraryEntry
import com.luis.marlune.ui.library.components.LibraryRow
import com.luis.marlune.ui.theme.MarluneTheme

/** Filas que reciben la entrada escalonada en la primera pantalla; el resto entra instantáneo. */
private const val StaggerVisibleCount = 10

/**
 * Andamiaje común de una pantalla de detalle: barra superior con retroceso + título, y el contenido
 * bajo ella. [content] recibe el padding inferior ya calculado (inset del mini-player/barra + respiro)
 * para que la última fila no quede cortada, igual que en Biblioteca.
 */
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable (bottomPadding: Dp) -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back),
                        tint = MarluneTheme.colors.textPrimary,
                    )
                }
                Text(
                    text = title,
                    style = MarluneTheme.typography.headlineSmall,
                    color = MarluneTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content(contentPadding.calculateBottomPadding() + 24.dp)
            }
        }
    }
}

/**
 * Lista de detalle: reutiliza [LibraryRow] (misma fila que Biblioteca) en una `LazyColumn` perezosa
 * con keys estables, `contentType`, mismo espaciado y respiro inferior. [nowPlayingId] resalta la
 * fila que suena (título en acento + ecualizador). Sin menú de "3 puntos" en detalle (lista vacía).
 */
@Composable
fun EntryList(
    entries: List<LibraryEntry>,
    coverIcon: ImageVector,
    coverShape: Shape,
    bottomPadding: Dp,
    onEntryClick: (LibraryEntry) -> Unit,
    modifier: Modifier = Modifier,
    nowPlayingId: Long? = null,
    isPlaying: Boolean = false,
) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(entries, key = { _, e -> e.id }, contentType = { _, _ -> "libraryRow" }) { index, entry ->
            com.luis.marlune.ui.components.StaggeredReveal(index = index, enabled = index < StaggerVisibleCount) {
                LibraryRow(
                    entry = entry,
                    coverIcon = coverIcon,
                    coverShape = coverShape,
                    onClick = { onEntryClick(entry) },
                    menuItems = emptyList(),
                    isCurrent = nowPlayingId != null && entry.id == nowPlayingId,
                    isPlaying = isPlaying,
                )
            }
        }
    }
}

fun Song.toLibraryEntry(): LibraryEntry =
    LibraryEntry(id = id, title = title, subtitle = artist, artworkUri = artworkUri)

fun Album.toLibraryEntry(): LibraryEntry =
    LibraryEntry(id = id, title = title, subtitle = artist, artworkUri = artworkUri)

fun Artist.toLibraryEntry(): LibraryEntry =
    LibraryEntry(
        id = id,
        title = name,
        subtitle = "$songCount ${if (songCount == 1) "canción" else "canciones"}",
        artworkUri = null,
    )
