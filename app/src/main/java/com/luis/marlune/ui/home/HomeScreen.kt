package com.luis.marlune.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luis.marlune.R
import com.luis.marlune.domain.model.Track
import com.luis.marlune.ui.components.StaggeredReveal
import com.luis.marlune.ui.home.components.ContinueListeningCard
import com.luis.marlune.ui.home.components.LibraryShortcutsGrid
import com.luis.marlune.ui.home.components.RecentTrackRow
import com.luis.marlune.ui.theme.MarluneTheme

/** Punto de entrada con estado de Inicio. Los callbacks los resolverá la navegación. */
@Composable
fun HomeRoute(
    onOpenPlayer: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onShortcutClick: (LibraryShortcut) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onOpenPlayer = onOpenPlayer,
        onPlayTrack = onPlayTrack,
        onShortcutClick = onShortcutClick,
        modifier = modifier,
    )
}

/**
 * Pantalla de Inicio (sin estado).
 *
 * Cabecera con UN saludo protagonista; el resto de textos pasan a jerarquía secundaria.
 * La lista "Escuchado hace poco" entra con stagger fade+rise solo en la primera carga y no
 * anima al hacer scroll. Todo procede de la biblioteca local.
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenPlayer: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onShortcutClick: (LibraryShortcut) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // Saludo protagonista (único hero de la cabecera).
            Text(
                text = stringResource(uiState.greeting.labelRes),
                style = MarluneTheme.typography.headlineMedium,
                color = MarluneTheme.colors.textPrimary,
            )

            uiState.continueListening?.let { continueState ->
                Spacer(Modifier.height(20.dp))
                ContinueListeningCard(state = continueState, onClick = onOpenPlayer)
            }

            Spacer(Modifier.height(28.dp))
            SectionTitle(text = stringResource(R.string.home_section_recent))
            Spacer(Modifier.height(8.dp))

            uiState.recentTracks.forEachIndexed { index, track ->
                StaggeredReveal(index = index) {
                    RecentTrackRow(track = track, onClick = { onPlayTrack(track) })
                }
            }

            Spacer(Modifier.height(28.dp))
            LibraryShortcutsGrid(onShortcutClick = onShortcutClick)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MarluneTheme.typography.titleMedium,
        color = MarluneTheme.colors.textPrimary,
        modifier = modifier,
    )
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
            onOpenPlayer = {},
            onPlayTrack = {},
            onShortcutClick = {},
        )
    }
}
