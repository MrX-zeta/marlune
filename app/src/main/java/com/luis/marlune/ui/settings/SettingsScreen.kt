package com.luis.marlune.ui.settings

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.luis.marlune.ui.detail.DetailScaffold
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

    DetailScaffold(stringResource(R.string.settings_title), onBack, contentPadding) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.settings_lyrics_section),
                style = MarluneTheme.typography.titleMedium,
                color = MarluneTheme.colors.textPrimary,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            // Toggle "Buscar letras en internet" + explicación honesta debajo.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_internet_lyrics),
                    style = MarluneTheme.typography.bodyLarge,
                    color = MarluneTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Switch(checked = internetLyrics, onCheckedChange = onToggleInternet)
            }
            Text(
                text = stringResource(R.string.settings_internet_lyrics_desc),
                style = MarluneTheme.typography.bodySmall,
                color = MarluneTheme.colors.textTertiary,
            )

            Spacer(Modifier.height(24.dp))

            // Letras descargadas: cuántas hay y cuánto ocupan; borrar solo si hay algo.
            Text(
                text = stringResource(R.string.settings_downloaded_lyrics),
                style = MarluneTheme.typography.bodyLarge,
                color = MarluneTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            if (cacheStats.count == 0) {
                Text(
                    text = stringResource(R.string.settings_cache_empty),
                    style = MarluneTheme.typography.bodySmall,
                    color = MarluneTheme.colors.textTertiary,
                )
            } else {
                Text(
                    text = pluralStringResource(
                        R.plurals.settings_cache_count,
                        cacheStats.count,
                        cacheStats.count,
                        Formatter.formatShortFileSize(context, cacheStats.bytes),
                    ),
                    style = MarluneTheme.typography.bodySmall,
                    color = MarluneTheme.colors.textTertiary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.settings_clear_downloaded),
                    style = MarluneTheme.typography.labelLarge,
                    color = MarluneTheme.colors.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showConfirm = true }
                        .padding(vertical = 10.dp),
                )
            }
        }
    }

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
