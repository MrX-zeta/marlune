package com.luis.marlune.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luis.marlune.ui.components.rememberHapticTick
import com.luis.marlune.ui.home.HomeRoute
import com.luis.marlune.ui.library.LibraryRoute
import com.luis.marlune.ui.player.MiniPlayer
import com.luis.marlune.ui.player.PlayerEvent
import com.luis.marlune.ui.player.PlayerScreen
import com.luis.marlune.ui.player.PlayerViewModel
import com.luis.marlune.ui.search.SearchRoute
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme

private const val ALBUM_ART_KEY = "album-art"
private const val TITLE_KEY = "player-title"
private const val ARTIST_KEY = "player-artist"

/**
 * Andamiaje raíz de Marlune.
 *
 * - Barra inferior: cambio de pestaña = swap de contenido instantáneo (fade 150 ms), sin slide de
 *   página; solo el icono activo reacciona (ver [MarluneBottomBar]).
 * - Mini-player ↔ reproductor completo: transición de elemento compartido sobre la carátula
 *   (`SharedTransitionLayout` + `AnimatedContent`). La carátula viaja; los controles alrededor
 *   hacen fade. Es el gran gesto espacial —aquí se gasta el presupuesto de motion (≤300 ms).
 * - Haptics consolidados: un único `tick` ligero en play/pausa y cambio de pista.
 *
 * Un solo [PlayerViewModel] alimenta mini y completo, así ambos muestran la misma pista.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MarluneApp(modifier: Modifier = Modifier) {
    val reducedMotion = LocalReducedMotion.current
    val playerViewModel: PlayerViewModel = viewModel()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()

    var selected by rememberSaveable { mutableStateOf(MarluneDestination.HOME) }
    var playerExpanded by rememberSaveable { mutableStateOf(false) }

    // Haptics en un solo punto: solo play/pausa y cambio de pista, nunca en scroll.
    val hapticTick = rememberHapticTick()
    val dispatchPlayer: (PlayerEvent) -> Unit = { event ->
        if (event is PlayerEvent.PlayPause || event is PlayerEvent.Next || event is PlayerEvent.Previous) {
            hapticTick()
        }
        playerViewModel.onEvent(event)
    }

    val transitionMs = if (reducedMotion) 0 else 280

    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = playerExpanded,
            transitionSpec = {
                val spec = tween<Float>(transitionMs)
                fadeIn(spec) togetherWith fadeOut(spec)
            },
            label = "playerExpand",
        ) { expanded ->
            // Asentamiento de "marea": decelerate, sin rebote, ≤300 ms. Común a la carátula
            // (elemento compartido) y al texto (bounds compartidos).
            val settle = BoundsTransform { _, _ -> tween(transitionMs, easing = LinearOutSlowInEasing) }

            // Carátula = elemento héroe: viaja y se escala, y permanece nítida (no se difumina).
            val artModifier = with(this@SharedTransitionLayout) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(ALBUM_ART_KEY),
                    animatedVisibilityScope = this@AnimatedContent,
                    boundsTransform = settle,
                )
            }
            // Título y artista = bounds compartidos con ScaleToBounds: mórfean tamaño/posición
            // escalando el contenido (GPU), sin reflow de texto → sin jank.
            val titleModifier = with(this@SharedTransitionLayout) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(TITLE_KEY),
                    animatedVisibilityScope = this@AnimatedContent,
                    boundsTransform = settle,
                    // resizeMode por defecto = ScaleToBounds: escala el contenido (GPU), sin reflow.
                )
            }
            val artistModifier = with(this@SharedTransitionLayout) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(ARTIST_KEY),
                    animatedVisibilityScope = this@AnimatedContent,
                    boundsTransform = settle,
                    // resizeMode por defecto = ScaleToBounds: escala el contenido (GPU), sin reflow.
                )
            }

            if (expanded) {
                PlayerScreen(
                    uiState = playerState,
                    onEvent = dispatchPlayer,
                    onMinimize = { playerExpanded = false },
                    artModifier = artModifier,
                    titleModifier = titleModifier,
                    artistModifier = artistModifier,
                )
            } else {
                MarluneShell(
                    selected = selected,
                    onSelect = { selected = it },
                    reducedMotion = reducedMotion,
                    playerState = playerState,
                    onExpandPlayer = { playerExpanded = true },
                    onMiniPlayPause = { dispatchPlayer(PlayerEvent.PlayPause) },
                    onMiniNext = { dispatchPlayer(PlayerEvent.Next) },
                    onMiniPrevious = { dispatchPlayer(PlayerEvent.Previous) },
                    miniArtModifier = artModifier,
                    miniTitleModifier = titleModifier,
                    miniArtistModifier = artistModifier,
                )
            }
        }
    }
}

/** Estado colapsado: pestañas + mini-player sobre la barra inferior. */
@Composable
private fun MarluneShell(
    selected: MarluneDestination,
    onSelect: (MarluneDestination) -> Unit,
    reducedMotion: Boolean,
    playerState: com.luis.marlune.ui.player.PlayerUiState,
    onExpandPlayer: () -> Unit,
    onMiniPlayPause: () -> Unit,
    onMiniNext: () -> Unit,
    onMiniPrevious: () -> Unit,
    miniArtModifier: Modifier,
    miniTitleModifier: Modifier,
    miniArtistModifier: Modifier,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column {
                // Tarjeta flotante: inset lateral + margen inferior para despegarla de la barra.
                MiniPlayer(
                    uiState = playerState,
                    onExpand = onExpandPlayer,
                    onPlayPause = onMiniPlayPause,
                    onNext = onMiniNext,
                    onPrevious = onMiniPrevious,
                    artModifier = miniArtModifier,
                    titleModifier = miniTitleModifier,
                    artistModifier = miniArtistModifier,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
                MarluneBottomBar(selected = selected, onSelect = onSelect)
            }
        },
    ) { innerPadding ->
        Crossfade(
            targetState = selected,
            animationSpec = if (reducedMotion) snap() else tween(durationMillis = 150),
            label = "tabContent",
        ) { destination ->
            when (destination) {
                // Inicio recibe el inset como contentPadding y su lista se desplaza bajo el
                // mini-player flotante; Biblioteca y Buscar lo consumen como margen (quedan sobre
                // la barra, como hasta ahora).
                MarluneDestination.HOME -> HomeRoute(
                    onPlayTrack = { onExpandPlayer() },
                    onShortcutClick = {},
                    onSeeAllRecent = {},
                    contentPadding = innerPadding,
                )

                MarluneDestination.LIBRARY -> LibraryRoute(
                    onOpenEntry = {},
                    modifier = Modifier.padding(innerPadding),
                )

                MarluneDestination.SEARCH -> SearchRoute(
                    onOpenTrack = { onExpandPlayer() },
                    modifier = Modifier.padding(innerPadding),
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
