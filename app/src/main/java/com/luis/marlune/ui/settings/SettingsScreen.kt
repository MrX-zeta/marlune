package com.luis.marlune.ui.settings

import android.text.format.Formatter
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luis.marlune.R
import com.luis.marlune.data.datastore.SettingsStore
import com.luis.marlune.data.lyrics.LyricsCacheStats
import com.luis.marlune.data.repository.LyricsRepository
import com.luis.marlune.di.rememberLyricsRepository
import com.luis.marlune.di.rememberSettingsStore
import com.luis.marlune.ui.components.StaggeredReveal
import com.luis.marlune.ui.theme.MarluneTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** ViewModel de Ajustes: opt-in de letras por internet (off por defecto) y borrado de su caché. */
class SettingsViewModel(
    private val settings: SettingsStore,
    private val lyrics: LyricsRepository,
) : ViewModel() {

    val internetLyrics: StateFlow<Boolean> =
        settings.internetLyrics.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
        fun factory(settings: SettingsStore, lyrics: LyricsRepository) =
            viewModelFactory { initializer { SettingsViewModel(settings, lyrics) } }
    }
}

@Composable
fun SettingsRoute(onBack: () -> Unit, contentPadding: PaddingValues) {
    val vm: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(rememberSettingsStore(), rememberLyricsRepository()),
    )
    val internet by vm.internetLyrics.collectAsStateWithLifecycle()
    val stats by vm.cacheStats.collectAsStateWithLifecycle()
    SettingsScreen(
        internetLyrics = internet,
        cacheStats = stats,
        onToggleInternet = vm::setInternetLyrics,
        onClearCache = vm::clearLyricsCache,
        onBack = onBack,
        contentPadding = contentPadding,
    )
}

@Composable
private fun SettingsScreen(
    internetLyrics: Boolean,
    cacheStats: LyricsCacheStats,
    onToggleInternet: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    var showConfirm by remember { mutableStateOf(false) }

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
                    .verticalScroll(rememberScrollState()),
            ) {
                // Grupo LETRAS (único que existe hoy): toggle de internet + letras descargadas.
                StaggeredReveal(index = 0) {
                    SettingsGroup(
                        label = stringResource(R.string.settings_lyrics_section),
                        icon = Icons.Rounded.Lyrics,
                    ) {
                        SettingRow(
                            icon = Icons.Rounded.Language,
                            title = stringResource(R.string.settings_internet_lyrics),
                            description = stringResource(R.string.settings_internet_lyrics_desc),
                            trailing = { Switch(checked = internetLyrics, onCheckedChange = onToggleInternet) },
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
            modifier = Modifier.fillMaxWidth(),
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
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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

/** Divisor sutil entre filas de una tarjeta, alineado tras el icono. */
@Composable
private fun SettingRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        thickness = 1.dp,
        color = MarluneTheme.colors.divider,
    )
}
