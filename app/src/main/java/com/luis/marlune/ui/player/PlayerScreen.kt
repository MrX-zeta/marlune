package com.luis.marlune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import com.luis.marlune.R
import com.luis.marlune.domain.model.RepeatMode
import com.luis.marlune.ui.components.Marea
import com.luis.marlune.ui.player.components.AlbumArt
import com.luis.marlune.ui.player.components.LikeButton
import com.luis.marlune.ui.player.components.PlayerControls
import com.luis.marlune.ui.theme.LocalMarluneAccentController
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme
import kotlinx.coroutines.launch

/**
 * Pantalla del Reproductor (sin estado).
 *
 * Integra el acento dinámico de la carátula (prompt 1): al cambiar `artwork` se recalcula el
 * acento —play, toggles activos y marea lo siguen vía `colorScheme.primary`— y fondos/texto se
 * mantienen neutros. Los haptics están consolidados aguas arriba (ver `MarluneApp`), no aquí.
 * `artModifier` recibe el modificador de elemento compartido para que la carátula viaje entre
 * mini-player y reproductor completo.
 */
@Composable
fun PlayerScreen(
    uiState: PlayerUiState,
    onEvent: (PlayerEvent) -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
    artModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    artistModifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val accentController = LocalMarluneAccentController.current
    LaunchedEffect(uiState.artwork) {
        val artwork = uiState.artwork
        if (artwork != null) {
            accentController.updateFromArtwork(artwork.asAndroidBitmap())
        } else {
            accentController.reset()
        }
    }

    // Desplazamiento horizontal del cambio de pista, compartido por la carátula y el título
    // para que ambos acompañen el dedo con el mismo offset.
    val trackOffset = remember { Animatable(0f) }

    // Colapso dirigido hacia el mini-player: 0 = completo, 1 = colapsado. La vista sigue el
    // dedo (Animatable) y hace snap al soltar.
    val collapse = remember { Animatable(0f) }
    // Recorrido para llegar a 1 (media pantalla).
    val collapseDistancePx = with(density) { (configuration.screenHeightDp.dp * 0.5f).toPx() }

    val onCollapseDrag: (Float) -> Unit = { dyPx ->
        scope.launch {
            collapse.snapTo((collapse.value + dyPx / collapseDistancePx).coerceIn(0f, 1f))
        }
    }
    val onCollapseRelease: (Float) -> Unit = { velocityY ->
        // Umbral: >35 % del recorrido o velocidad de descarte suficiente → completar.
        val commit = collapse.value >= 0.35f || velocityY >= 1200f
        if (commit) {
            onMinimize() // la carátula viaja al mini-player (elemento compartido)
        } else {
            scope.launch {
                if (reducedMotion) {
                    collapse.snapTo(0f)
                } else {
                    // Regreso a pantalla completa; spring rápido (se asienta bajo 300 ms).
                    collapse.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    // Efecto de colapso acoplado al progreso, SOLO para el fondo y el "chrome" (controles
    // secundarios): fade + escala (+ blur en API 31+). La carátula y el texto quedan FUERA de
    // este efecto: se mantienen nítidos y viajan/mórfean con el elemento compartido.
    val progress = collapse.value
    val chromeAlpha = lerp(1f, 0.4f, progress)
    val chromeScale = lerp(1f, 0.92f, progress)
    val blurRadius = lerp(0f, 16f, progress).dp
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val chrome = Modifier
        .graphicsLayer {
            alpha = chromeAlpha
            scaleX = chromeScale
            scaleY = chromeScale
        }
        .then(if (supportsBlur && progress > 0f) Modifier.blur(blurRadius) else Modifier)

    Box(modifier = modifier.fillMaxSize()) {
        // Fondo neutro que se atenúa con el colapso; la carátula/texto van por encima, nítidos.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = chromeAlpha }
                .background(MaterialTheme.colorScheme.background),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            PlayerTopBar(source = uiState.source, onMinimize = onMinimize, modifier = chrome)

            Spacer(Modifier.weight(0.5f))

            // Carátula héroe: elemento compartido, nítido, sin efecto de colapso.
            AlbumArt(
                artwork = uiState.artwork,
                trackOffset = trackOffset,
                onPrevious = { onEvent(PlayerEvent.Previous) },
                onNext = { onEvent(PlayerEvent.Next) },
                onCollapseDrag = onCollapseDrag,
                onCollapseRelease = onCollapseRelease,
                modifier = Modifier.fillMaxWidth().then(artModifier),
            )

            Spacer(Modifier.weight(0.5f))

            // Título y artista mórfean de posición/tamaño (sharedBounds); el "me gusta" es chrome.
            // Acompañan el swipe horizontal de pista con el mismo offset que la carátula.
            TrackInfo(
                title = uiState.title,
                artist = uiState.artist,
                isLiked = uiState.isLiked,
                onToggleLike = { onEvent(PlayerEvent.ToggleLike) },
                titleModifier = titleModifier,
                artistModifier = artistModifier,
                likeModifier = chrome,
                modifier = Modifier.graphicsLayer { translationX = trackOffset.value },
            )

            Spacer(Modifier.height(20.dp))

            // Marea + tiempos: chrome (se atenúan/difuminan con el colapso).
            Column(modifier = chrome) {
                Marea(progress = uiState.progress, isPlaying = uiState.isPlaying)
                TimeRow(positionMs = uiState.positionMs, durationMs = uiState.durationMs)
            }

            Spacer(Modifier.height(24.dp))

            // Controles: se desvanecen como set (crossfade del AnimatedContent); además, chrome.
            PlayerControls(
                isPlaying = uiState.isPlaying,
                isShuffleOn = uiState.isShuffleOn,
                repeatMode = uiState.repeatMode,
                onPlayPause = { onEvent(PlayerEvent.PlayPause) },
                onPrevious = { onEvent(PlayerEvent.Previous) },
                onNext = { onEvent(PlayerEvent.Next) },
                onToggleShuffle = { onEvent(PlayerEvent.ToggleShuffle) },
                onToggleRepeat = { onEvent(PlayerEvent.ToggleRepeat) },
                modifier = chrome,
            )

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlayerTopBar(
    source: String,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onMinimize) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.player_minimize),
                tint = MarluneTheme.colors.textPrimary,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.player_playing_from).uppercase(),
                style = MarluneTheme.typography.labelMedium,
                color = MarluneTheme.colors.textSecondary, // contraste corregido (antes apagado)
            )
            Text(
                text = source,
                style = MarluneTheme.typography.titleSmall,
                color = MarluneTheme.colors.textPrimary,
            )
        }
        // Equilibra el ancho del IconButton izquierdo para centrar el bloque.
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun TrackInfo(
    title: String,
    artist: String,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    titleModifier: Modifier = Modifier,
    artistModifier: Modifier = Modifier,
    likeModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MarluneTheme.typography.titleLarge,
                color = MarluneTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).then(titleModifier),
            )
            // "Me gusta" vive en la fila del título, no sobre la carátula; es chrome (se desvanece).
            LikeButton(isLiked = isLiked, onClick = onToggleLike, modifier = likeModifier)
        }
        // Sin indicador de verificación/descarga: la biblioteca es 100 % local.
        Text(
            text = artist,
            style = MarluneTheme.typography.bodyMedium,
            color = MarluneTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = artistModifier,
        )
    }
}

@Composable
private fun TimeRow(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatTime(positionMs),
            style = MarluneTheme.typography.bodySmall,
            color = MarluneTheme.colors.textSecondary,
        )
        Text(
            text = formatTime(durationMs),
            style = MarluneTheme.typography.bodySmall,
            color = MarluneTheme.colors.textSecondary,
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0910, heightDp = 780)
@Composable
private fun PlayerScreenPlayingPreview() {
    MarluneTheme {
        PlayerScreen(
            uiState = PlayerUiState.Preview.copy(isPlaying = true, isShuffleOn = true, isLiked = true),
            onEvent = {},
            onMinimize = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0910, heightDp = 780)
@Composable
private fun PlayerScreenPausedPreview() {
    MarluneTheme {
        PlayerScreen(
            uiState = PlayerUiState.Preview.copy(isPlaying = false, repeatMode = RepeatMode.ONE),
            onEvent = {},
            onMinimize = {},
        )
    }
}
