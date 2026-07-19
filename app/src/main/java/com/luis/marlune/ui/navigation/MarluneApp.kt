package com.luis.marlune.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.luis.marlune.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.luis.marlune.di.rememberAppPrefsStore
import com.luis.marlune.di.rememberFavoritesRepository
import com.luis.marlune.di.rememberLyricsRepository
import com.luis.marlune.di.rememberMusicRepository
import com.luis.marlune.di.rememberPlaybackRepository
import com.luis.marlune.di.rememberSettingsStore
import com.luis.marlune.ui.detail.AlbumDetailRoute
import com.luis.marlune.ui.detail.AlbumsRoute
import com.luis.marlune.ui.detail.ArtistDetailRoute
import com.luis.marlune.ui.detail.ArtistsRoute
import com.luis.marlune.ui.detail.HistoryRoute
import com.luis.marlune.ui.detail.LikedSongsRoute
import com.luis.marlune.ui.detail.PlaylistDetailRoute
import com.luis.marlune.ui.detail.PlaylistsRoute
import com.luis.marlune.ui.detail.RecentlyAddedRoute
import com.luis.marlune.ui.theme.LocalMarluneAccentController
import com.luis.marlune.ui.components.rememberHapticTick
import com.luis.marlune.ui.home.HomeRoute
import com.luis.marlune.ui.library.LibraryRoute
import com.luis.marlune.ui.onboarding.OnboardingFlow
import com.luis.marlune.ui.player.MiniPlayer
import com.luis.marlune.ui.player.PlayerEvent
import com.luis.marlune.ui.player.PlayerScreen
import com.luis.marlune.ui.permissions.PermissionRationaleScreen
import com.luis.marlune.ui.permissions.RequestNotificationPermissionOnFirstPlay
import com.luis.marlune.ui.permissions.rememberAudioPermissionState
import com.luis.marlune.ui.settings.SettingsRoute
import com.luis.marlune.ui.player.PlayerViewModel
import com.luis.marlune.ui.search.SearchRoute
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme

private const val ALBUM_ART_KEY = "album-art"
private const val TITLE_KEY = "player-title"
private const val ARTIST_KEY = "player-artist"

// Margen para calentar carátulas vecinas FUERA de la ventana del cambio de pista (slide ≈ 600 ms):
// el decode nunca cae en los frames de la animación.
private const val NEIGHBOR_ART_PRECACHE_DELAY_MS = 700L

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
fun MarluneApp(
    modifier: Modifier = Modifier,
    openNowPlaying: Boolean = false,
    onNowPlayingConsumed: () -> Unit = {},
) {
    // Primera experiencia: en el PRIMER arranque se muestra el onboarding (bienvenida + explicación
    // del permiso de música), nunca diálogos de permiso a secas. Ya completado, la app abre directa.
    val appPrefs = rememberAppPrefsStore()
    val onboardingCompleted by appPrefs.onboardingCompleted.collectAsStateWithLifecycle(initialValue = null)
    val audioPermission = rememberAudioPermissionState()
    val prefsScope = rememberCoroutineScope()

    // Cargando el flag: fondo neutro (evita parpadear antes de decidir qué mostrar).
    if (onboardingCompleted == null) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
        return
    }
    // Primer arranque → onboarding; se completa al conceder el permiso de música.
    if (onboardingCompleted == false) {
        OnboardingFlow(
            permission = audioPermission,
            onCompleted = { prefsScope.launch { appPrefs.setOnboardingCompleted() } },
            modifier = modifier,
        )
        return
    }
    // Onboarding ya hecho: si el permiso se revocó después, se explica (reintentar / abrir Ajustes).
    if (!audioPermission.isGranted) {
        PermissionRationaleScreen(state = audioPermission, modifier = modifier)
        return
    }

    // Conecta el MediaController al servicio mientras la UI está viva; lo suelta al salir (la
    // reproducción sigue en el servicio). La conexión es asíncrona dentro del PlaybackRepository.
    val playback = rememberPlaybackRepository()
    DisposableEffect(playback) {
        playback.connect()
        onDispose { playback.release() }
    }

    val reducedMotion = LocalReducedMotion.current
    val playerViewModel: PlayerViewModel =
        viewModel(
            factory = PlayerViewModel.factory(
                playback,
                rememberFavoritesRepository(),
                rememberMusicRepository(),
                rememberLyricsRepository(),
                rememberSettingsStore(),
            ),
        )
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val queueState by playerViewModel.queue.collectAsStateWithLifecycle()

    // Notificación de reproducción (Android 13+): se pide en el PRIMER play, una sola vez. Opcional;
    // sin ella la reproducción sigue igual (solo no se ve la notificación con controles).
    RequestNotificationPermissionOnFirstPlay(hasStartedPlayback = playerState.isPlaying)
    val lyricsState by playerViewModel.lyricsState.collectAsStateWithLifecycle()
    val lyricsPicker by playerViewModel.lyricsPicker.collectAsStateWithLifecycle()
    val lyricsFolderError by playerViewModel.folderError.collectAsStateWithLifecycle()
    val internetLyricsEnabled by playerViewModel.internetLyricsEnabled.collectAsStateWithLifecycle()

    // Acento dinámico COMPARTIDO: se extrae (Palette, fuera del hilo principal) al cambiar la pista
    // ACTUAL —aquí, siempre compuesto—, no dentro de Now Playing. Así el color se refresca en tiempo
    // real en mini-player, marea y toggles al seleccionar, sin abrir el reproductor. El tema lo anima
    // (240 ms) sobre el acento; fondos y texto siguen neutros. Cacheado por el `artworkUri` (la key).
    val accentController = LocalMarluneAccentController.current
    val accentContext = LocalContext.current
    LaunchedEffect(playerState.artworkUri) {
        val uri = playerState.artworkUri
        val bitmap = if (uri == null) null else runCatching {
            val result = accentContext.imageLoader.execute(
                ImageRequest.Builder(accentContext).data(uri).allowHardware(false).size(256).build(),
            )
            (result as? SuccessResult)?.drawable?.toBitmap()
        }.getOrNull()
        if (bitmap != null) accentController.updateFromArtwork(bitmap) else accentController.reset()
    }

    // Precarga de carátulas VECINAS (anterior y siguiente de la cola) en la caché de Coil con la clave
    // estable compartida (TrackThumbnail/AlbumArt): al cambiar de pista, la imagen entrante ya está
    // decodificada y el swap del slide es un hit de memoria. IMPORTANTE: corre APLAZADA, en REPOSO —
    // este efecto se relanza justo cuando cambia currentIndex (la ventana de la transición), y decodificar
    // el vecino fresco AHÍ competía con los frames del arranque del slide (la micro-pausa al deslizar
    // sonando, primera pasada por la cola: sonando se avanza a territorio frío; en pausa se juega entre
    // pistas ya cacheadas, por eso ahí era fluido). Con el margen, el slide ya asentó cuando decodifica;
    // si llega otro cambio antes, el efecto se relanza y el margen vuelve a contar. Solo calienta caché.
    LaunchedEffect(queueState.currentIndex, queueState.items) {
        delay(NEIGHBOR_ART_PRECACHE_DELAY_MS)
        val items = queueState.items
        listOf(queueState.currentIndex - 1, queueState.currentIndex + 1).forEach { index ->
            val uri = items.getOrNull(index)?.artworkUri ?: return@forEach
            val key = uri.toString()
            accentContext.imageLoader.enqueue(
                ImageRequest.Builder(accentContext).data(uri).memoryCacheKey(key).diskCacheKey(key).build(),
            )
        }
    }

    val navController = rememberNavController()
    var playerExpanded by rememberSaveable { mutableStateOf(false) }

    // Callback ESTABLE: al cambiar el estado de reproducción no cambia su identidad, así que el
    // contenido (NavHost) no se recompone por el playback (el mini-player sí, donde se usa).
    val expandPlayer = remember { { playerExpanded = true } }

    // Petición del widget de abrir Now Playing: expande el reproductor si hay pista. Se consume una
    // sola vez (aditivo; no altera el resto del flujo). Sin pista, solo se consume (queda en Inicio).
    LaunchedEffect(openNowPlaying) {
        if (openNowPlaying) {
            if (playerState.hasTrack) playerExpanded = true
            onNowPlayingConsumed()
        }
    }

    // Apertura de la hoja de cola hoisteada aquí para poder abrirla desde el snackbar de "Añadir a la
    // cola" (que vive en Biblioteca, otra pantalla): abrirla expande el reproductor y muestra la hoja.
    var showQueue by rememberSaveable { mutableStateOf(false) }
    val openQueue: () -> Unit = {
        playerExpanded = true
        showQueue = true
    }
    // Snackbar a nivel de app: "Añadida a la cola" + acción "Ver". Se permite duplicar (es una cola).
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val queuedMessage = stringResource(R.string.library_queued)
    val viewLabel = stringResource(R.string.action_view)
    val onSongQueued: () -> Unit = {
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = queuedMessage,
                actionLabel = viewLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) openQueue()
        }
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

    // Retiene el estado GUARDABLE de cada rama (shell colapsado / reproductor) aunque salga de
    // composición al expandir: así el shell conserva scroll, chip y pestaña al minimizar. No altera
    // el elemento compartido ni el BackHandler; solo preserva estado. Aplica igual a Inicio/Buscar.
    val contentStateHolder = rememberSaveableStateHolder()

    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = playerExpanded,
            transitionSpec = {
                val spec = tween<Float>(transitionMs)
                fadeIn(spec) togetherWith fadeOut(spec)
            },
            label = "playerExpand",
        ) { expanded ->
          contentStateHolder.SaveableStateProvider(expanded) {
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
                // Retroceso: minimiza Now Playing (misma transición de colapso: `playerExpanded=false`
                // conduce el AnimatedContent). Depende de que esté EXPANDIDO, no del estado de
                // reproducción. Se registra DENTRO de la rama expandida (después que el NavHost del
                // shell) para tener prioridad sobre él; ya minimizado, el retroceso se propaga normal.
                BackHandler(enabled = playerExpanded) { playerExpanded = false }
                PlayerScreen(
                    uiState = playerState,
                    lyricsState = lyricsState,
                    lyricsPicker = lyricsPicker,
                    onChangeLyrics = playerViewModel::openLyricsPicker,
                    onChooseLyricsCandidate = playerViewModel::chooseLyricsCandidate,
                    onUseAutomaticLyrics = playerViewModel::useAutomaticLyrics,
                    onCloseLyricsPicker = playerViewModel::closeLyricsPicker,
                    folderError = lyricsFolderError,
                    internetLyricsEnabled = internetLyricsEnabled,
                    onEvent = dispatchPlayer,
                    onMinimize = { playerExpanded = false },
                    onLyricsFolderPicked = playerViewModel::onLyricsFolderPicked,
                    // "Buscar letras en internet" lleva a Ajustes resaltando la card de Letras
                    // (auto-scroll + parpadeo), para que el usuario vea de inmediato el ajuste a activar.
                    // Ancla Ajustes SIEMPRE sobre Inicio (consistente con tabForRoute=HOME) aunque el
                    // reproductor se abriera sobre Biblioteca/Buscar: si no, Ajustes quedaba en la pila de
                    // otra pestaña y tocar esa pestaña restauraba Ajustes ("solo se ilumina, no cambia").
                    // saveState conserva la pestaña anterior para restaurarla al volver a ella.
                    onOpenSettings = {
                        playerExpanded = false
                        navController.navigate(Routes.settings(Routes.SETTINGS_HIGHLIGHT_LYRICS)) {
                            popUpTo(Routes.TABS) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    queue = queueState,
                    showQueue = showQueue,
                    onOpenQueue = { showQueue = true },
                    onCloseQueue = { showQueue = false },
                    onJumpToQueueItem = playerViewModel::playQueueItem,
                    onRemoveQueueItem = playerViewModel::removeQueueItem,
                    onMoveQueueItem = playerViewModel::moveQueueItem,
                    artModifier = artModifier,
                    titleModifier = titleModifier,
                    artistModifier = artistModifier,
                )
            } else {
                MarluneShell(
                    navController = navController,
                    reducedMotion = reducedMotion,
                    playerState = playerState,
                    snackbarHostState = snackbarHostState,
                    onSongQueued = onSongQueued,
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
}

/** Estado colapsado: NavHost de contenido + mini-player sobre la barra inferior. */
@Composable
private fun MarluneShell(
    navController: NavHostController,
    reducedMotion: Boolean,
    playerState: com.luis.marlune.ui.player.PlayerUiState,
    snackbarHostState: SnackbarHostState,
    onSongQueued: () -> Unit,
    onExpandPlayer: () -> Unit,
    onMiniPlayPause: () -> Unit,
    onMiniNext: () -> Unit,
    onMiniPrevious: () -> Unit,
    miniArtModifier: Modifier,
    miniTitleModifier: Modifier,
    miniArtistModifier: Modifier,
) {
    // Pager de las 3 pestañas (swipe horizontal). Vive en el shell para compartirlo con la barra
    // inferior; su estado se preserva con el shell (rememberSaveable → sobrevive a ir/volver de Now
    // Playing). Los detalles (ajustes, álbum…) siguen apilándose sobre el pager en el NavHost.
    val pagerState = rememberPagerState(pageCount = { MarluneDestination.entries.size })
    val tabScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                // En las pestañas, la resaltada = la página del pager; en un detalle, la pestaña padre.
                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                val onTabs = currentRoute == Routes.TABS
                MarluneBottomBar(
                    selected = if (onTabs) MarluneDestination.entries[pagerState.currentPage]
                    else tabForRoute(currentRoute),
                    onSelect = { destination ->
                        if (onTabs) {
                            tabScope.launch { pagerState.animateScrollToPage(destination.ordinal) }
                        } else {
                            // Desde un detalle: quita los detalles (vuelve al pager) y coloca la pestaña.
                            navController.popBackStack(Routes.TABS, inclusive = false)
                            tabScope.launch { pagerState.scrollToPage(destination.ordinal) }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        // Contenido navegable. NO recibe el estado de reproducción: es "saltable" y las listas no se
        // repintan cuando avanza la posición. Tocar una canción reproduce por el PlaybackRepository.
        MarluneNavHost(
            navController = navController,
            contentPadding = innerPadding,
            onSongQueued = onSongQueued,
            pagerState = pagerState,
        )
    }
}

/** Grafo de navegación del contenido: pestañas de primer nivel + pantallas de detalle. */
@Composable
private fun MarluneNavHost(
    navController: NavHostController,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onSongQueued: () -> Unit,
    pagerState: PagerState,
) {
    // Transiciones de pantalla cortas. La de SALIDA (y pop-salida) es la más breve a propósito: la
    // pantalla saliente queda por encima durante la animación y, si es larga, se come el toque siguiente
    // (obliga a tocar dos veces) o lo desvía a una fila —abriendo un detalle "fantasma"—. Acortarla cierra
    // esa ventana. Respeta el movimiento reducido (cambio instantáneo).
    val reducedMotion = LocalReducedMotion.current
    val enterSpec = if (reducedMotion) snap<Float>() else tween<Float>(150, easing = LinearOutSlowInEasing)
    val exitSpec = if (reducedMotion) snap<Float>() else tween<Float>(90, easing = FastOutLinearInEasing)
    NavHost(
        navController = navController,
        startDestination = Routes.TABS,
        enterTransition = { fadeIn(enterSpec) },
        exitTransition = { fadeOut(exitSpec) },
        popEnterTransition = { fadeIn(enterSpec) },
        popExitTransition = { fadeOut(exitSpec) },
    ) {
        composable(Routes.TABS) {
            TabsPager(
                pagerState = pagerState,
                navController = navController,
                contentPadding = contentPadding,
                onSongQueued = onSongQueued,
            )
        }
        composable(
            route = Routes.SETTINGS_ROUTE,
            arguments = listOf(
                navArgument(Routes.SETTINGS_HIGHLIGHT_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            SettingsRoute(
                onBack = navController::popBackStack,
                contentPadding = contentPadding,
                highlightLyrics = entry.arguments?.getString(Routes.SETTINGS_HIGHLIGHT_ARG) ==
                    Routes.SETTINGS_HIGHLIGHT_LYRICS,
            )
        }
        composable(Routes.LIKED) {
            LikedSongsRoute(contentPadding = contentPadding, onBack = navController::popBackStack)
        }
        composable(Routes.RECENTLY_ADDED) {
            RecentlyAddedRoute(contentPadding = contentPadding, onBack = navController::popBackStack)
        }
        composable(Routes.HISTORY) {
            HistoryRoute(contentPadding = contentPadding, onBack = navController::popBackStack)
        }
        composable(Routes.ALBUMS) {
            AlbumsRoute(
                contentPadding = contentPadding,
                onBack = navController::popBackStack,
                onOpenAlbum = { id -> navController.navigateOnce(Routes.album(id)) },
            )
        }
        composable(Routes.ARTISTS) {
            ArtistsRoute(
                contentPadding = contentPadding,
                onBack = navController::popBackStack,
                onOpenArtist = { id -> navController.navigateOnce(Routes.artist(id)) },
            )
        }
        composable(Routes.PLAYLISTS) {
            PlaylistsRoute(
                contentPadding = contentPadding,
                onBack = navController::popBackStack,
                onOpenPlaylist = { id -> navController.navigateOnce(Routes.playlist(id)) },
            )
        }
        composable(
            route = Routes.ALBUM_DETAIL,
            arguments = listOf(navArgument(Routes.ALBUM_ARG) { type = NavType.LongType }),
        ) { entry ->
            AlbumDetailRoute(
                albumId = entry.arguments?.getLong(Routes.ALBUM_ARG) ?: 0L,
                contentPadding = contentPadding,
                onBack = navController::popBackStack,
            )
        }
        composable(
            route = Routes.ARTIST_DETAIL,
            arguments = listOf(navArgument(Routes.ARTIST_ARG) { type = NavType.LongType }),
        ) { entry ->
            ArtistDetailRoute(
                artistId = entry.arguments?.getLong(Routes.ARTIST_ARG) ?: 0L,
                contentPadding = contentPadding,
                onBack = navController::popBackStack,
            )
        }
        composable(
            route = Routes.PLAYLIST_DETAIL,
            arguments = listOf(navArgument(Routes.PLAYLIST_ARG) { type = NavType.LongType }),
        ) { entry ->
            PlaylistDetailRoute(
                playlistId = entry.arguments?.getLong(Routes.PLAYLIST_ARG) ?: 0L,
                contentPadding = contentPadding,
                onBack = navController::popBackStack,
            )
        }
    }
}

/**
 * Pestañas de primer nivel en un [HorizontalPager]: SWIPE horizontal para navegar (Inicio · Biblioteca ·
 * Buscar), con la barra inferior de apoyo. Los detalles (ajustes, álbum…) se abren en el NavHost por
 * ENCIMA del pager. Back en una pestaña ≠ Inicio vuelve a Inicio (como el bottom nav). El estado de cada
 * página lo preserva el propio Pager (SaveableStateHolder), incluido al deslizar entre ellas.
 */
@Composable
private fun TabsPager(
    pagerState: PagerState,
    navController: NavHostController,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onSongQueued: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }
    HorizontalPager(
        state = pagerState,
        // Precompone las páginas vecinas (Inicio · Biblioteca · Buscar) fuera de la ruta crítica del
        // swipe: al deslizar, la página destino ya está construida (sin el coste de primera composición
        // —lista, carátulas, derivación de álbumes/artistas— cayendo en los frames del gesto). De paso
        // mantiene vivos sus flujos de datos, evitando la re-derivación por el timeout de WhileSubscribed.
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize(),
        key = { it },
    ) { page ->
        when (page) {
            MarluneDestination.HOME.ordinal -> HomeRoute(
                onOpenLiked = { navController.navigateTab(Routes.LIKED) },
                onOpenRecentlyAdded = { navController.navigateTab(Routes.RECENTLY_ADDED) },
                onSeeAllRecent = { navController.navigateTab(Routes.HISTORY) },
                onOpenSettings = { navController.navigateTab(Routes.SETTINGS) },
                contentPadding = contentPadding,
            )
            MarluneDestination.LIBRARY.ordinal -> LibraryRoute(
                onOpenAlbum = { id -> navController.navigateTab(Routes.album(id)) },
                onOpenArtist = { id -> navController.navigateTab(Routes.artist(id)) },
                onOpenPlaylist = { id -> navController.navigateTab(Routes.playlist(id)) },
                onSongQueued = onSongQueued,
                // Con la pre-composición de la página vecina, la entrada de filas se dispara al hacerse
                // VISIBLE la Biblioteca (página actual del pager), no en la pre-composición fuera de pantalla.
                active = pagerState.currentPage == MarluneDestination.LIBRARY.ordinal,
                contentPadding = contentPadding,
            )
            MarluneDestination.SEARCH.ordinal -> SearchRoute(
                onOpenAlbums = { navController.navigateTab(Routes.ALBUMS) },
                onOpenArtists = { navController.navigateTab(Routes.ARTISTS) },
                onOpenPlaylists = { navController.navigateTab(Routes.PLAYLISTS) },
                onOpenLiked = { navController.navigateTab(Routes.LIKED) },
                modifier = Modifier.padding(contentPadding),
            )
        }
    }
}

/**
 * Navega dedupando el destino en la cima ([launchSingleTop]): toques repetidos al MISMO acceso (o el
 * mismo álbum/artista/lista) NO apilan pantallas duplicadas —el destello de contenido "fantasma" venía
 * de esos duplicados encimados—. Ir a un destino DISTINTO nunca se bloquea, así el cambio entre accesos
 * sigue siendo instantáneo (a diferencia de gatear por el ciclo de vida RESUMED, que se comía el primer
 * toque durante la animación de entrada).
 */
private fun NavHostController.navigateOnce(route: String) {
    navigate(route) { launchSingleTop = true }
}

/**
 * Navegación a un acceso de NIVEL 1 (los que cuelgan directo de las pestañas: Álbumes, Artistas,
 * Listas, Me gusta, Añadidas, historial y los detalles abiertos desde Biblioteca). Reemplaza al
 * hermano en la cima ([popUpTo] TABS + [launchSingleTop]): tocar varios accesos seguidos NO apila una
 * pila de pantallas —de la que salían las listas "fantasma"—, se aterriza limpio en el último y el
 * retroceso vuelve siempre a las pestañas. Ir a otro acceso nunca se bloquea (fluidez intacta).
 */
private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(Routes.TABS) { }
        launchSingleTop = true
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0910)
@Composable
private fun MarluneAppPreview() {
    MarluneTheme {
        MarluneApp()
    }
}
