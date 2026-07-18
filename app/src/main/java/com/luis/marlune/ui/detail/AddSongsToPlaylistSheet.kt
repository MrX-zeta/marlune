package com.luis.marlune.ui.detail

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.luis.marlune.data.repository.LibraryState
import com.luis.marlune.di.rememberMusicRepository
import com.luis.marlune.di.rememberPlaylistRepository
import com.luis.marlune.domain.model.Song
import com.luis.marlune.ui.components.EmptyState
import com.luis.marlune.ui.home.components.TrackThumbnail
import com.luis.marlune.ui.search.components.SearchField
import com.luis.marlune.ui.theme.MarluneTheme
import com.luis.marlune.ui.theme.placeholderAccentFor
import kotlinx.coroutines.launch

/**
 * Hoja para AÑADIR canciones a una lista existente: toda la biblioteca con buscador (reutiliza la
 * búsqueda y el limpiador de nombres de [rememberMusicRepository]) y selección múltiple. Las que ya
 * están en la lista salen marcadas y deshabilitadas ("Ya en la lista") para no duplicarlas. Al
 * confirmar se añaden AL FINAL vía [PlaylistRepository.addSong] (mismo orden/inserción/dedupe de
 * siempre); la lista se refresca sola (Room). Sin tocar el reordenado por arrastre ya existente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsToPlaylistSheet(playlistId: Long, onDismiss: () -> Unit) {
    val music = rememberMusicRepository()
    val repo = rememberPlaylistRepository()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    val libraryState by remember { music.library }.collectAsStateWithLifecycle()
    val allSongs = (libraryState as? LibraryState.Content)?.songs.orEmpty()
    // Filtrado reutilizando la búsqueda existente (limpiador incluido); vacío = biblioteca completa.
    val results by remember(query) { music.searchSongs(query) }.collectAsStateWithLifecycle(emptyList())
    val shown = if (query.isBlank()) allSongs else results

    val existing by remember(playlistId) { repo.playlistSongs(playlistId) }.collectAsStateWithLifecycle(emptyList())
    val existingIds = remember(existing) { existing.mapTo(HashSet()) { it.id } }

    val selected = remember { mutableStateListOf<Long>() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MarluneTheme.colors.surfaceElevated) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(
                text = stringResource(R.string.playlist_add_songs),
                style = MarluneTheme.typography.titleMedium,
                color = MarluneTheme.colors.textPrimary,
            )
            Spacer(Modifier.padding(top = 12.dp))
            SearchField(
                query = query,
                onQueryChange = { query = it },
                onSubmit = {},
                onClear = { query = "" },
            )
            Spacer(Modifier.padding(top = 12.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    allSongs.isEmpty() -> EmptyState(
                        icon = Icons.Rounded.LibraryMusic,
                        title = stringResource(R.string.library_empty_title),
                        hint = stringResource(R.string.library_empty_hint),
                    )
                    shown.isEmpty() -> Note(stringResource(R.string.search_no_results))
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(shown, key = { it.id }, contentType = { "songPickRow" }) { song ->
                            val inList = song.id in existingIds
                            SongPickRow(
                                song = song,
                                inList = inList,
                                checked = inList || song.id in selected,
                                onToggle = {
                                    if (song.id in selected) selected.remove(song.id) else selected.add(song.id)
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.padding(top = 8.dp))
            Button(
                onClick = {
                    val toAdd = selected.toList()
                    scope.launch {
                        var added = 0
                        toAdd.forEach { if (repo.addSong(playlistId, it)) added++ }
                        Toast.makeText(
                            context,
                            context.resources.getQuantityString(R.plurals.playlist_songs_added, added, added),
                            Toast.LENGTH_SHORT,
                        ).show()
                        onDismiss()
                    }
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MarluneTheme.colors.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(stringResource(R.string.playlist_add_songs_action, selected.size))
            }
            Spacer(Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun SongPickRow(song: Song, inList: Boolean, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !inList, onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackThumbnail(
            accent = remember(song.albumId) { placeholderAccentFor(song.albumId) },
            artworkUri = song.artworkUri,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MarluneTheme.typography.titleMedium,
                color = if (inList) MarluneTheme.colors.textTertiary else MarluneTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (inList) stringResource(R.string.playlist_song_in_list) else song.artist,
                style = MarluneTheme.typography.bodyMedium,
                color = if (inList) MarluneTheme.colors.accent else MarluneTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        // Callback SIEMPRE no-nulo (aunque esté deshabilitado): así todas conservan el área táctil de
        // 48 dp de Material3 y quedan con la MISMA alineación/margen, estén marcadas por "ya en la lista"
        // o no. La interacción real la corta `enabled`.
        Checkbox(
            checked = checked,
            onCheckedChange = { if (!inList) onToggle() },
            enabled = !inList,
            colors = CheckboxDefaults.colors(
                checkedColor = MarluneTheme.colors.accent,
                uncheckedColor = MarluneTheme.colors.textTertiary,
            ),
        )
    }
}

@Composable
private fun Note(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.TopCenter) {
        Text(text = text, style = MarluneTheme.typography.bodyMedium, color = MarluneTheme.colors.textTertiary)
    }
}

/** Botón "Añadir canciones" (acento), para la cabecera del vacío de una lista. Abre [AddSongsToPlaylistSheet]. */
@Composable
fun AddSongsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MarluneTheme.colors.accent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.playlist_add_songs))
    }
}
