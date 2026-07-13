package com.luis.marlune.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.luis.marlune.ui.home.HomeRoute
import com.luis.marlune.ui.library.LibraryRoute
import com.luis.marlune.ui.search.SearchRoute
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme

/**
 * Andamiaje raíz de Marlune: barra inferior de 3 pestañas y contenedor de contenido.
 *
 * El cambio de pestaña es un intercambio de contenido instantáneo (fade 150 ms), sin
 * deslizamiento de página —es la acción más frecuente—. La selección sobrevive a rotaciones
 * (`rememberSaveable`). Las tres pestañas (Inicio, Biblioteca, Buscar) ya son sus pantallas reales.
 */
@Composable
fun MarluneApp(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf(MarluneDestination.HOME) }
    val reducedMotion = LocalReducedMotion.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            MarluneBottomBar(
                selected = selected,
                onSelect = { selected = it },
            )
        },
    ) { innerPadding ->
        Crossfade(
            targetState = selected,
            animationSpec = if (reducedMotion) snap() else tween(durationMillis = 150),
            modifier = Modifier.padding(innerPadding),
            label = "tabContent",
        ) { destination ->
            when (destination) {
                MarluneDestination.HOME -> HomeRoute(
                    // La navegación real (abrir reproductor / secciones) llega en una tarea posterior.
                    onOpenPlayer = {},
                    onPlayTrack = {},
                    onShortcutClick = {},
                )

                MarluneDestination.LIBRARY -> LibraryRoute(
                    onOpenEntry = {},
                )

                MarluneDestination.SEARCH -> SearchRoute(
                    onOpenTrack = {},
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0910)
@Composable
private fun MarluneAppPreview() {
    MarluneTheme {
        MarluneApp()
    }
}
