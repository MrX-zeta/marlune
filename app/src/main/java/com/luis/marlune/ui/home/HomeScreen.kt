package com.luis.marlune.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import com.luis.marlune.R
import com.luis.marlune.di.rememberFavoritesRepository
import com.luis.marlune.di.rememberHistoryRepository
import com.luis.marlune.di.rememberMusicRepository
import com.luis.marlune.di.rememberPlaybackRepository
import com.luis.marlune.domain.model.Song
import com.luis.marlune.playback.PlaybackRepository
import com.luis.marlune.ui.components.EmptyState
import com.luis.marlune.ui.components.LoadingRows
import com.luis.marlune.ui.components.StaggeredReveal
import com.luis.marlune.ui.home.components.LibraryShortcutsGrid
import com.luis.marlune.ui.home.components.RecentTrackRow
import com.luis.marlune.ui.theme.MarluneTheme

private const val RecentPreviewCount = 4

/** Punto de entrada con estado de Inicio. Los callbacks los resolverá la navegación. */
@Composable
fun HomeRoute(
    onShortcutClick: (LibraryShortcut) -> Unit,
    onSeeAllRecent: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            rememberMusicRepository(),
            rememberHistoryRepository(),
            rememberFavoritesRepository(),
        ),
    ),
    playback: PlaybackRepository = rememberPlaybackRepository(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        // Tocar una canción reproduce la COLA real; el mini-player aparece con esa pista al sonar.
        onPlayTrack = { song ->
            playback.playSongs(uiState.recent, uiState.recent.indexOf(song))
        },
        onShortcutClick = onShortcutClick,
        onSeeAllRecent = onSeeAllRecent,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

/**
 * Pantalla de Inicio (sin estado).
 *
 * Cabecera con UN saludo protagonista. La lista "Escuchado hace poco" muestra hasta 4 ítems con
 * un enlace "Ver todo" y entra con stagger fade+rise solo en la primera carga. El contenido se
 * desplaza bajo el mini-player flotante: [contentPadding] (alturas reales del mini-player + barra
 * vía WindowInsets del Scaffold) despeja la última fila del grid para que nada quede tapado.
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onPlayTrack: (Song) -> Unit,
    onShortcutClick: (LibraryShortcut) -> Unit,
    onSeeAllRecent: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = contentPadding.calculateTopPadding())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // Saludo protagonista (único hero de la cabecera).
            Text(
                text = stringResource(uiState.greeting.labelRes),
                style = MarluneTheme.typography.headlineMedium,
                color = MarluneTheme.colors.textPrimary,
            )

            Spacer(Modifier.height(28.dp))
            RecentHeader(onSeeAllRecent = onSeeAllRecent)
            Spacer(Modifier.height(8.dp))

            when {
                uiState.isLoading -> LoadingRows(count = RecentPreviewCount)
                // Sin música en el dispositivo (distinto de "aún no has reproducido nada").
                uiState.libraryEmpty -> EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.home_empty_title),
                    hint = stringResource(R.string.home_empty_hint),
                )
                // Hay música pero historial vacío: pista discreta hasta la primera reproducción.
                uiState.recent.isEmpty() -> Text(
                    text = stringResource(R.string.home_recent_empty),
                    style = MarluneTheme.typography.bodyMedium,
                    color = MarluneTheme.colors.textTertiary,
                )
                // Máximo 4 ítems visibles; el stagger se conserva sobre ellos.
                else -> uiState.recent.take(RecentPreviewCount).forEachIndexed { index, song ->
                    StaggeredReveal(index = index) {
                        RecentTrackRow(song = song, onClick = { onPlayTrack(song) })
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            LibraryShortcutsGrid(onShortcutClick = onShortcutClick, likedCount = uiState.likedCount)

            // Padding inferior dinámico: sube el grid por encima del mini-player flotante + barra.
            Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 12.dp))
        }
    }
}

@Composable
private fun RecentHeader(
    onSeeAllRecent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_section_recent),
            style = MarluneTheme.typography.titleMedium,
            color = MarluneTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.home_see_all),
            style = MarluneTheme.typography.labelLarge,
            color = MarluneTheme.colors.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onSeeAllRecent)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private val Greeting.labelRes: Int
    get() = when (this) {
        Greeting.MORNING -> R.string.greeting_morning
        Greeting.AFTERNOON -> R.string.greeting_afternoon
        Greeting.NIGHT -> R.string.greeting_night
    }

@Preview(showBackground = true, backgroundColor = 0xFF0A0910, heightDp = 900)
@Composable
private fun HomeScreenPreview() {
    MarluneTheme {
        HomeScreen(
            uiState = HomeUiState.Preview,
            onPlayTrack = {},
            onShortcutClick = {},
            onSeeAllRecent = {},
            contentPadding = PaddingValues(0.dp),
        )
    }
}
