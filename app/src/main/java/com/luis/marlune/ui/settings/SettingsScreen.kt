package com.luis.marlune.ui.settings

import android.os.SystemClock
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luis.marlune.R
import com.luis.marlune.data.datastore.SettingsStore
import com.luis.marlune.data.datastore.ShortClipFilter
import com.luis.marlune.data.lyrics.LyricsCacheStats
import com.luis.marlune.data.repository.LyricsRepository
import com.luis.marlune.data.repository.MusicRepository
import com.luis.marlune.di.rememberLyricsRepository
import com.luis.marlune.di.rememberMusicRepository
import com.luis.marlune.di.rememberPlaybackRepository
import com.luis.marlune.di.rememberSettingsStore
import com.luis.marlune.playback.PlaybackRepository
import com.luis.marlune.playback.SleepTimerOption
import com.luis.marlune.playback.SleepTimerState
import com.luis.marlune.ui.permissions.areNotificationsEnabled
import com.luis.marlune.ui.permissions.openAppNotificationSettings
import com.luis.marlune.ui.components.StaggeredReveal
import com.luis.marlune.ui.theme.MarluneTheme
import com.luis.marlune.ui.widget.WidgetPinner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** ViewModel de Ajustes: opt-in de letras por internet (off por defecto), borrado de su caché y temporizador. */
class SettingsViewModel(
    private val settings: SettingsStore,
    private val lyrics: LyricsRepository,
    private val playback: PlaybackRepository,
    private val music: MusicRepository,
) : ViewModel() {

    val internetLyrics: StateFlow<Boolean> =
        settings.internetLyrics.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Temporizador de apagado activo (o `null`). Vive en la capa de playback. */
    val sleepTimer: StateFlow<SleepTimerState?> = playback.sleepTimer

    fun setSleepTimer(option: SleepTimerOption) = playback.startSleepTimer(option)

    // Escaneo manual de medios: MISMA acción que el pull-to-refresh de Biblioteca (no se duplica lógica).
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    fun scanMedia() {
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            try {
                music.rescan()
            } finally {
                _scanning.value = false
            }
        }
    }

    // Filtro de clips cortos (presentación): al cambiarlo, la biblioteca se refresca reactivamente.
    val shortClipFilter: StateFlow<ShortClipFilter> =
        settings.shortClipFilter.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShortClipFilter(true, 30))

    fun setShortClipEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setShortClipEnabled(enabled) }
    }

    fun setShortClipMinSeconds(seconds: Int) {
        viewModelScope.launch { settings.setShortClipMinSeconds(seconds) }
    }

    // Reactivo: se recalcula al descargar o borrar (señal `cacheChanges`), así el contador de Ajustes
    // se actualiza en tiempo real sin re-entrar a la pantalla.
    @OptIn(ExperimentalCoroutinesApi::class)
    val cacheStats: StateFlow<LyricsCacheStats> =
        lyrics.cacheChanges
            .mapLatest { lyrics.lyricsCacheStats() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsCacheStats(0, 0L))

    fun setInternetLyrics(enabled: Boolean) {
        viewModelScope.launch { settings.setInternetLyrics(enabled) }
    }

    fun clearLyricsCache() {
        viewModelScope.launch { lyrics.clearNetworkCache() } // stats se refresca sola vía cacheChanges
    }

    companion object {
        fun factory(
            settings: SettingsStore,
            lyrics: LyricsRepository,
            playback: PlaybackRepository,
            music: MusicRepository,
        ) = viewModelFactory { initializer { SettingsViewModel(settings, lyrics, playback, music) } }
    }
}

@Composable
fun SettingsRoute(onBack: () -> Unit, contentPadding: PaddingValues, highlightLyrics: Boolean = false) {
    val vm: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            rememberSettingsStore(),
            rememberLyricsRepository(),
            rememberPlaybackRepository(),
            rememberMusicRepository(),
        ),
    )
    val internet by vm.internetLyrics.collectAsStateWithLifecycle()
    val stats by vm.cacheStats.collectAsStateWithLifecycle()
    val sleepTimer by vm.sleepTimer.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val shortClip by vm.shortClipFilter.collectAsStateWithLifecycle()
    SettingsScreen(
        internetLyrics = internet,
        cacheStats = stats,
        sleepTimer = sleepTimer,
        scanning = scanning,
        shortClip = shortClip,
        onToggleInternet = vm::setInternetLyrics,
        onClearCache = vm::clearLyricsCache,
        onSetSleepTimer = vm::setSleepTimer,
        onScanMedia = vm::scanMedia,
        onToggleShortClip = vm::setShortClipEnabled,
        onSetShortClipSeconds = vm::setShortClipMinSeconds,
        onBack = onBack,
        contentPadding = contentPadding,
        highlightLyrics = highlightLyrics,
    )
}

@Composable
private fun SettingsScreen(
    internetLyrics: Boolean,
    cacheStats: LyricsCacheStats,
    sleepTimer: SleepTimerState?,
    scanning: Boolean,
    shortClip: ShortClipFilter,
    onToggleInternet: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onSetSleepTimer: (SleepTimerOption) -> Unit,
    onScanMedia: () -> Unit,
    onToggleShortClip: (Boolean) -> Unit,
    onSetShortClipSeconds: (Int) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    highlightLyrics: Boolean = false,
) {
    val context = LocalContext.current
    var showConfirm by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }

    // Resaltado de la card de Letras al llegar desde "Buscar letras en internet": auto-scroll + parpadeo.
    // Scroll DETERMINISTA: medimos la posición real de la card y de la ventana de scroll y desplazamos
    // hasta ella (bringIntoView solo movía lo mínimo, y si estaba algo visible, no hacía nada).
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var viewportTopY by remember { mutableStateOf(0f) }
    var lyricsCardTopY by remember { mutableStateOf(0f) }
    val lyricsBlink = remember { Animatable(0f) }
    // ONE-SHOT: solo la PRIMERA vez que se llega con highlight. El flag sobrevive (rememberSaveable) a
    // ir/volver de Now Playing, así el destello NO se repite cada vez que se sale de la carátula.
    var lyricsHighlighted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(highlightLyrics) {
        if (highlightLyrics && !lyricsHighlighted) {
            lyricsHighlighted = true
            // Espera a que Ajustes termine de ENTRAR (transición Now Playing → shell) y esté medido.
            delay(380)
            val margin = with(density) { 16.dp.toPx() }
            val target = (scrollState.value + (lyricsCardTopY - viewportTopY) - margin).roundToInt().coerceAtLeast(0)
            scrollState.animateScrollTo(target)
            repeat(3) {
                lyricsBlink.animateTo(1f, tween(180))
                lyricsBlink.animateTo(0f, tween(220))
            }
        }
    }

    // Estado de notificaciones (solo lectura): se refresca al volver de los ajustes del sistema.
    var notificationsEnabled by remember { mutableStateOf(areNotificationsEnabled(context)) }
    LifecycleStartEffect(Unit) {
        notificationsEnabled = areNotificationsEnabled(context)
        onStopOrDispose {}
    }

    // Resultado breve al terminar de escanear (transición escaneando → listo).
    var scanCompleted by remember { mutableStateOf(false) }
    LaunchedEffect(scanning) {
        if (scanning) scanCompleted = true
        else if (scanCompleted) {
            scanCompleted = false
            Toast.makeText(context, R.string.scan_done, Toast.LENGTH_SHORT).show()
        }
    }

    // Etiqueta del temporizador en VIVO: cuenta atrás cada segundo para los modos temporizados, o texto
    // fijo para "al terminar la canción"; `null` cuando no hay temporizador.
    var timerLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sleepTimer) {
        val st = sleepTimer
        val end = st?.endElapsedRealtime
        when {
            st == null -> timerLabel = null
            end == null -> timerLabel = context.getString(R.string.sleep_end_of_track)
            else -> while (true) {
                val remaining = end - SystemClock.elapsedRealtime()
                if (remaining <= 0L) { timerLabel = null; break }
                val minutes = ((remaining + 59_999L) / 60_000L).toInt() // redondeo hacia arriba
                timerLabel = context.getString(R.string.sleep_remaining, minutes)
                delay(1_000L)
            }
        }
    }

    // Contador/tamaño de letras descargadas: MISMO texto y lógica que antes (solo cambia dónde se pinta).
    val cacheDescription = if (cacheStats.count == 0) {
        stringResource(R.string.settings_cache_empty)
    } else {
        pluralStringResource(
            R.plurals.settings_cache_count,
            cacheStats.count,
            cacheStats.count,
            Formatter.formatShortFileSize(context, cacheStats.bytes),
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(horizontal = 20.dp),
        ) {
            // Cabecera: título grande + subtítulo + ✕ para cerrar.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MarluneTheme.typography.headlineMedium,
                        color = MarluneTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_subtitle),
                        style = MarluneTheme.typography.bodyMedium,
                        color = MarluneTheme.colors.textSecondary,
                    )
                }
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.settings_close),
                        tint = MarluneTheme.colors.textPrimary,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { viewportTopY = it.localToRoot(Offset.Zero).y }
                    .verticalScroll(scrollState),
            ) {
                // Grupo REPRODUCCIÓN: temporizador de apagado.
                StaggeredReveal(index = 0) {
                    SettingsGroup(
                        label = stringResource(R.string.settings_playback_section),
                        icon = Icons.Rounded.PlayCircleOutline,
                    ) {
                        SettingRow(
                            icon = Icons.Rounded.Bedtime,
                            title = stringResource(R.string.sleep_title),
                            description = stringResource(R.string.sleep_desc),
                            onClick = { showTimerDialog = true },
                            trailing = {
                                Text(
                                    text = timerLabel ?: stringResource(R.string.sleep_off),
                                    style = MarluneTheme.typography.labelLarge,
                                    color = if (timerLabel != null) MarluneTheme.colors.accent
                                    else MarluneTheme.colors.textTertiary,
                                )
                            },
                        )
                        SettingRowDivider()
                        // Solo MUESTRA el estado y lleva a los ajustes del sistema (los permisos no se
                        // revocan desde la app; nada de un toggle que no puede cumplir).
                        SettingRow(
                            icon = Icons.Rounded.Notifications,
                            title = stringResource(R.string.settings_notifications),
                            description = stringResource(
                                if (notificationsEnabled) R.string.settings_notifications_on
                                else R.string.settings_notifications_off,
                            ),
                            onClick = { context.openAppNotificationSettings() },
                            trailing = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MarluneTheme.colors.textTertiary,
                                )
                            },
                        )
                    }
                }

                // Grupo BIBLIOTECA: escaneo manual de medios (misma acción que el pull-to-refresh).
                StaggeredReveal(index = 1) {
                    SettingsGroup(
                        label = stringResource(R.string.settings_library_section),
                        icon = Icons.Rounded.LibraryMusic,
                    ) {
                        SettingRow(
                            icon = Icons.Rounded.Sync,
                            title = stringResource(R.string.scan_title),
                            description = stringResource(R.string.scan_desc),
                            onClick = { if (!scanning) onScanMedia() },
                            trailing = {
                                if (scanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                        color = MarluneTheme.colors.accent,
                                    )
                                }
                            },
                        )
                        SettingRowDivider()
                        SettingRow(
                            icon = Icons.Rounded.Timer,
                            title = stringResource(R.string.short_clip_title),
                            description = stringResource(R.string.short_clip_desc),
                            trailing = {
                                Switch(
                                    checked = shortClip.enabled,
                                    onCheckedChange = onToggleShortClip,
                                    colors = SwitchDefaults.colors(
                                        uncheckedBorderColor = MarluneTheme.colors.textTertiary,
                                        uncheckedTrackColor = MarluneTheme.colors.surfaceElevated,
                                        uncheckedThumbColor = MarluneTheme.colors.textSecondary,
                                    ),
                                )
                            },
                        )
                        // Umbral seleccionable: solo cuando el filtro está activo.
                        if (shortClip.enabled) {
                            DurationChips(
                                selectedSeconds = shortClip.minSeconds,
                                onSelect = onSetShortClipSeconds,
                            )
                        }
                    }
                }

                // Grupo LETRAS: toggle de internet + letras descargadas.
                StaggeredReveal(index = 2) {
                    SettingsGroup(
                        label = stringResource(R.string.settings_lyrics_section),
                        icon = Icons.Rounded.Lyrics,
                        cardModifier = Modifier
                            .onGloballyPositioned { lyricsCardTopY = it.localToRoot(Offset.Zero).y }
                            .border(
                                width = 2.dp,
                                color = MarluneTheme.colors.accent.copy(alpha = lyricsBlink.value),
                                shape = RoundedCornerShape(20.dp),
                            ),
                    ) {
                        SettingRow(
                            icon = Icons.Rounded.Language,
                            title = stringResource(R.string.settings_internet_lyrics),
                            description = stringResource(R.string.settings_internet_lyrics_desc),
                            trailing = {
                                Switch(
                                    checked = internetLyrics,
                                    onCheckedChange = onToggleInternet,
                                    // Borde visible cuando está desactivado (antes casi imperceptible).
                                    colors = SwitchDefaults.colors(
                                        uncheckedBorderColor = MarluneTheme.colors.textTertiary,
                                        uncheckedTrackColor = MarluneTheme.colors.surfaceElevated,
                                        uncheckedThumbColor = MarluneTheme.colors.textSecondary,
                                    ),
                                )
                            },
                        )
                        SettingRowDivider()
                        SettingRow(
                            icon = Icons.Rounded.CloudDownload,
                            title = stringResource(R.string.settings_downloaded_lyrics),
                            description = cacheDescription,
                            trailing = {
                                // Borrar solo si hay algo; abre la MISMA confirmación de siempre.
                                if (cacheStats.count > 0) {
                                    IconButton(onClick = { showConfirm = true }) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = stringResource(R.string.settings_clear_downloaded),
                                            tint = MarluneTheme.colors.accent,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
                // Grupo PANTALLA DE INICIO: anclar el widget. En launchers que no lo soportan, se
                // muestra la pista manual en vez de un botón que no hace nada.
                val widgetSupported = remember { WidgetPinner.isSupported(context) }
                StaggeredReveal(index = 3) {
                    SettingsGroup(
                        label = stringResource(R.string.settings_home_section),
                        icon = Icons.Rounded.Widgets,
                    ) {
                        SettingRow(
                            icon = Icons.Rounded.Widgets,
                            title = stringResource(R.string.settings_add_widget_title),
                            description = stringResource(
                                if (widgetSupported) R.string.settings_add_widget_desc
                                else R.string.settings_add_widget_manual,
                            ),
                            onClick = if (widgetSupported) {
                                { WidgetPinner.requestPin(context) }
                            } else null,
                            trailing = {
                                if (widgetSupported) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MarluneTheme.colors.textTertiary,
                                    )
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 24.dp))
            }
        }
    }

    // Confirmación + toast: IDÉNTICOS al comportamiento anterior.
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onClearCache()
                    Toast.makeText(context, R.string.settings_clear_done, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.settings_clear_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(R.string.settings_clear_confirm_no))
                }
            },
        )
    }

    // Opciones del temporizador de apagado (elegir uno lo activa; "Desactivado" lo cancela).
    if (showTimerDialog) {
        SleepTimerDialog(
            current = sleepTimer?.option ?: SleepTimerOption.OFF,
            onSelect = {
                showTimerDialog = false
                onSetSleepTimer(it)
            },
            onDismiss = { showTimerDialog = false },
        )
    }
}

private val SleepTimerOptions = listOf(
    SleepTimerOption.OFF,
    SleepTimerOption.MIN_15,
    SleepTimerOption.MIN_30,
    SleepTimerOption.MIN_45,
    SleepTimerOption.HOUR_1,
    SleepTimerOption.END_OF_TRACK,
)

@Composable
private fun sleepOptionLabel(option: SleepTimerOption): String = stringResource(
    when (option) {
        SleepTimerOption.OFF -> R.string.sleep_off
        SleepTimerOption.MIN_15 -> R.string.sleep_15
        SleepTimerOption.MIN_30 -> R.string.sleep_30
        SleepTimerOption.MIN_45 -> R.string.sleep_45
        SleepTimerOption.HOUR_1 -> R.string.sleep_60
        SleepTimerOption.END_OF_TRACK -> R.string.sleep_end_of_track
    },
)

/** Diálogo de selección única del temporizador; marca la opción activa con el acento. */
@Composable
private fun SleepTimerDialog(
    current: SleepTimerOption,
    onSelect: (SleepTimerOption) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                SleepTimerOptions.forEach { option ->
                    val selected = option == current
                    Text(
                        text = sleepOptionLabel(option),
                        style = MarluneTheme.typography.bodyLarge,
                        color = if (selected) MarluneTheme.colors.accent else MarluneTheme.colors.textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Grupo de ajustes: encabezado "susurro" (icono en acento tenue + etiqueta en mayúsculas, tracking
 * amplio, texto terciario) FUERA de la tarjeta, y las filas dentro de una tarjeta elevada (mismo
 * radio que el resto de la app). Sin adornos.
 */
@Composable
private fun SettingsGroup(
    label: String,
    icon: ImageVector,
    cardModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MarluneTheme.colors.accent.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label.uppercase(),
                style = MarluneTheme.typography.labelMedium,
                color = MarluneTheme.colors.textTertiary,
                letterSpacing = 1.5.sp,
            )
        }
        Surface(
            modifier = cardModifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MarluneTheme.colors.surfaceElevated,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { content() }
        }
    }
}

/** Fila de ajuste: icono (acento) + título/descripción (texto secundario) + control a la derecha. */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    description: String?,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MarluneTheme.colors.accent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MarluneTheme.typography.bodyLarge,
                color = MarluneTheme.colors.textPrimary,
            )
            if (description != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MarluneTheme.typography.bodySmall,
                    color = MarluneTheme.colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

private val ShortClipSecondsOptions = listOf(15, 30, 60)

/** Chips de duración mínima (15/30/60 s): la activa se tiñe con el acento. Alineados tras el icono. */
@Composable
private fun DurationChips(selectedSeconds: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 52.dp, end = 16.dp, top = 2.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShortClipSecondsOptions.forEach { seconds ->
            val selected = seconds == selectedSeconds
            Text(
                text = stringResource(R.string.short_clip_seconds, seconds),
                style = MarluneTheme.typography.labelLarge,
                color = if (selected) MarluneTheme.colors.accent else MarluneTheme.colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) MarluneTheme.colors.accentMuted else MarluneTheme.colors.divider)
                    .clickable { onSelect(seconds) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

/** Divisor sutil entre filas de una tarjeta, alineado tras el icono. */
@Composable
private fun SettingRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        thickness = 1.dp,
        color = MarluneTheme.colors.divider,
    )
}
