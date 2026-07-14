package com.luis.marlune.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luis.marlune.di.rememberPlaybackRepository
import com.luis.marlune.ui.components.rememberHapticTick
import com.luis.marlune.ui.home.HomeRoute
import com.luis.marlune.ui.library.LibraryRoute
import com.luis.marlune.ui.player.MiniPlayer
import com.luis.marlune.ui.player.PlayerEvent
import com.luis.marlune.ui.player.PlayerScreen
import com.luis.marlune.ui.permissions.PermissionRationaleScreen
import com.luis.marlune.ui.permissions.RequestNotificationPermissionOnce
import com.luis.marlune.ui.permissions.rememberAudioPermissionState
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
    // Sin permiso de audio no hay música local que mostrar: se pide en runtime y, si no está
    // concedido, se explica por qué (con acción para conceder o abrir Ajustes).
    val audioPermission = rememberAudioPermissionState()
    if (!audioPermission.isGranted) {
        PermissionRationaleScreen(state = audioPermission, modifier = modifier)
        return
    }

    // Notificación de reproducción (Android 13+); opcional, no bloquea la reproducción.
    RequestNotificationPermissionOnce()

    // Conecta el MediaController al servicio mientras la UI está viva; lo suelta al salir (la
    // reproducción sigue en el servicio). La conexión es asíncrona dentro del PlaybackRepository.
    val playback = rememberPlaybackRepository()
    DisposableEffect(playback) {
        playback.connect()
        onDispose { playback.release() }
    }

    val reducedMotion = LocalReducedMotion.current
    val playerViewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.factory(playback))
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()

    var selected by rememberSaveable { mutableStateOf(MarluneDestination.HOME) }
    var playerExpanded by rememberSaveable { mutableStateOf(false) }

    // Callbacks de navegación ESTABLES: al cambiar el estado de reproducción (posición, isPlaying)
    // no cambia su identidad, así que las pestañas (y sus listas) no se recomponen por el playback.
    val expandPlayer = remember { { playerExpanded = true } }
    val selectTab = remember { { destination: MarluneDestination -> selected = destination } }

    // Retroceso del dispositivo en Now Playing: primero MINIMIZA al mini-player, reutilizando la
    // MISMA transición de colapso que el swipe abajo y el chevron (`playerExpanded = false` conduce
    // el `AnimatedContent`). Ya colapsado, se desactiva y el retroceso se propaga normal (salir).
    BackHandler(enabled = playerExpanded) {
        playerExpanded = false
    }

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
                    onSelect = selectTab,
                    reducedMotion = reducedMotion,
                    playerState = playerState,
                    onExpandPlayer = expandPlayer,
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
                // Mini-player solo cuando hay pista real: en vacío se OCULTA (nada de canción falsa)
                // y aparece con fade+slide al empezar a sonar. La reproducción vive en el servicio.
                AnimatedVisibility(
                    visible = playerState.hasTrack,
                    enter = if (reducedMotion) fadeIn(snap()) else
                        fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 2 },
                    exit = if (reducedMotion) fadeOut(snap()) else
                        fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 2 },
                ) {
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
                }
                MarluneBottomBar(selected = selected, onSelect = onSelect)
            }
        },
    ) { innerPadding ->
        // Pestañas aisladas del [playerState]: solo dependen de `selected` y `reducedMotion`, así que
        // un cambio de reproducción no las recompone (ni sus listas). Tocar una canción reproduce por
        // el PlaybackRepository (dentro de cada Route); no abre Now Playing (el mini-player aparece).
        MarluneTabs(
            selected = selected,
            reducedMotion = reducedMotion,
            contentPadding = innerPadding,
        )
    }
}

/**
 * Contenido de pestañas. Deliberadamente NO recibe el estado de reproducción: al leerse el playback
 * solo donde se usa (mini-player), esta subárbol es "saltable" y las listas no se repintan cuando
 * avanza la posición o cambia isPlaying. El cambio de pestaña sigue siendo un fade rápido (150 ms).
 */
@Composable
private fun MarluneTabs(
    selected: MarluneDestination,
    reducedMotion: Boolean,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
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
                onShortcutClick = {},
                onSeeAllRecent = {},
                contentPadding = contentPadding,
            )

            MarluneDestination.LIBRARY -> LibraryRoute(
                onOpenEntry = {},
                modifier = Modifier.padding(contentPadding),
            )

            MarluneDestination.SEARCH -> SearchRoute(
                modifier = Modifier.padding(contentPadding),
            )
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
