package com.luis.marlune.ui.settings

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.luis.marlune.data.repository.LyricsRepository
import com.luis.marlune.di.rememberLyricsRepository
import com.luis.marlune.di.rememberSettingsStore
import com.luis.marlune.ui.detail.DetailScaffold
import com.luis.marlune.ui.theme.MarluneTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** ViewModel de Ajustes: opt-in de letras por internet (off por defecto) y borrado de su caché. */
class SettingsViewModel(
    private val settings: SettingsStore,
    private val lyrics: LyricsRepository,
) : ViewModel() {

    val internetLyrics: StateFlow<Boolean> =
        settings.internetLyrics.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setInternetLyrics(enabled: Boolean) {
        viewModelScope.launch { settings.setInternetLyrics(enabled) }
    }

    fun clearLyricsCache() = lyrics.clearNetworkCache()

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
    SettingsScreen(
        internetLyrics = internet,
        onToggleInternet = vm::setInternetLyrics,
        onClearCache = vm::clearLyricsCache,
        onBack = onBack,
        contentPadding = contentPadding,
    )
}

@Composable
private fun SettingsScreen(
    internetLyrics: Boolean,
    onToggleInternet: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
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

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.settings_clear_cache),
                style = MarluneTheme.typography.bodyLarge,
                color = MarluneTheme.colors.accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClearCache() }
                    .padding(vertical = 12.dp),
            )
        }
    }
}
