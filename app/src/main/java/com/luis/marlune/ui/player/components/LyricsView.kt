package com.luis.marlune.ui.player.components

import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luis.marlune.R
import com.luis.marlune.domain.model.LyricLine
import com.luis.marlune.domain.model.LyricsFolderRequest
import com.luis.marlune.ui.player.LyricsUiState
import com.luis.marlune.ui.theme.MarluneTheme
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

private val LyricsCorner = 24.dp
private val EdgeFade = 72.dp
private const val ManualScrollPauseMs = 4_000L

/**
 * Vista de letras del reproductor (ocupa el cuadrado de la carátula). Solo LEE el [state] que arma el
 * ViewModel; nada de IO aquí. Sincronizada = línea activa en acento dinámico + auto-scroll centrado;
 * plana = texto scrolleable; sin letra = vacío discreto (con opción de conectar carpeta). Respeta
 * [reducedMotion] (sin animar el scroll). Fades arriba/abajo para el efecto "solo el centro nítido".
 */
@Composable
fun LyricsView(
    state: LyricsUiState,
    reducedMotion: Boolean,
    folderError: Boolean,
    internetEnabled: Boolean,
    onGrantAccess: (initialUri: Uri?) -> Unit,
    onSearchOnline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = MarluneTheme.colors.surfaceElevated
    Box(
        modifier
            .clip(RoundedCornerShape(LyricsCorner))
            .background(surface),
    ) {
        when (state) {
            LyricsUiState.Loading -> CenteredNote(stringResource(R.string.lyrics_loading))
            is LyricsUiState.Error -> CenteredNote(
                stringResource(if (state.offline) R.string.lyrics_no_connection else R.string.lyrics_service_error),
            )
            is LyricsUiState.None -> EmptyLyrics(state.request, folderError, internetEnabled, onGrantAccess, onSearchOnline)
            is LyricsUiState.Plain -> PlainLyrics(state.lines)
            is LyricsUiState.Synced -> SyncedLyrics(state.lines, state.activeIndex, reducedMotion)
        }
        // Fades de borde (solo visuales; sin pointerInput → no bloquean el scroll).
        EdgeFadeOverlay(surface)
    }
}

@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    activeIndex: Int,
    reducedMotion: Boolean,
) {
    val listState = rememberLazyListState()

    // Pausa del auto-scroll al arrastrar a mano; se reanuda pasado [ManualScrollPauseMs].
    var pausedUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                pausedUntil = SystemClock.uptimeMillis() + ManualScrollPauseMs
            }
        }
    }

    // Auto-scroll que centra la línea activa. Reacciona al cambio de línea y al fin de la pausa.
    LaunchedEffect(activeIndex, pausedUntil, reducedMotion) {
        if (activeIndex < 0) return@LaunchedEffect
        val now = SystemClock.uptimeMillis()
        if (!reducedMotion && now < pausedUntil) {
            delay(pausedUntil - now)
        }
        val info = listState.layoutInfo
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        val itemSize = info.visibleItemsInfo.firstOrNull { it.index == activeIndex }?.size ?: (viewport / 8)
        val centerOffset = -((viewport - itemSize) / 2)
        if (reducedMotion) {
            listState.scrollToItem(activeIndex, centerOffset)
        } else {
            listState.animateScrollToItem(activeIndex, centerOffset)
        }
    }

    val accent = MaterialTheme.colorScheme.primary // acento DINÁMICO
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = EdgeFade),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val active = index == activeIndex
            Text(
                text = line.text.ifBlank { "♪" },
                style = MarluneTheme.typography.titleMedium,
                color = when {
                    active -> accent
                    index < activeIndex -> MarluneTheme.colors.textTertiary // ya cantadas: más tenues
                    else -> MarluneTheme.colors.textSecondary
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlainLyrics(lines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = EdgeFade),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MarluneTheme.typography.titleMedium,
                color = MarluneTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CenteredNote(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MarluneTheme.typography.bodyMedium,
            color = MarluneTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyLyrics(
    request: LyricsFolderRequest?,
    folderError: Boolean,
    internetEnabled: Boolean,
    onGrantAccess: (Uri?) -> Unit,
    onSearchOnline: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (request == null) {
            // Ya hay acceso (o no se dedujo la carpeta): simplemente no existe el .lrc.
            Text(
                text = stringResource(R.string.lyrics_empty),
                style = MarluneTheme.typography.bodyMedium,
                color = MarluneTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        } else {
            // Falta acceso a la carpeta de ESTA canción: lenguaje humano + nombre visible + botón.
            Text(
                text = stringResource(R.string.lyrics_need_access, request.folderName),
                style = MarluneTheme.typography.bodyMedium,
                color = MarluneTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            if (folderError) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.lyrics_wrong_folder),
                    style = MarluneTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MarluneTheme.colors.accent.copy(alpha = 0.14f))
                    .clickable { onGrantAccess(request.initialUri) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MarluneTheme.colors.accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.lyrics_grant),
                    style = MarluneTheme.typography.labelLarge,
                    color = MarluneTheme.colors.accent,
                )
            }
        }

        // Descubrimiento: con el opt-in de red APAGADO, enlace discreto que lleva a Ajustes (no lo
        // enciende por el usuario). Con el ajuste ON no aparece (ya se intentó la red).
        if (!internetEnabled) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.lyrics_search_online),
                style = MarluneTheme.typography.bodySmall,
                color = MarluneTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onSearchOnline() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Overlay de degradados en los bordes superior e inferior (efecto "solo el centro nítido"). */
@Composable
private fun BoxScope.EdgeFadeOverlay(surface: Color) {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(EdgeFade)
            .background(Brush.verticalGradient(listOf(surface, Color.Transparent))),
    )
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(EdgeFade)
            .background(Brush.verticalGradient(listOf(Color.Transparent, surface))),
    )
}
