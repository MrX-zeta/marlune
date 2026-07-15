package com.luis.marlune.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.luis.marlune.MarluneApplication
import com.luis.marlune.data.repository.FavoritesRepository
import com.luis.marlune.data.repository.HistoryRepository
import com.luis.marlune.data.repository.LyricsRepository
import com.luis.marlune.data.repository.MusicRepository
import com.luis.marlune.data.repository.PlaylistRepository
import com.luis.marlune.data.datastore.AppPrefsStore
import com.luis.marlune.data.datastore.LibrarySortStore
import com.luis.marlune.data.datastore.SettingsStore
import com.luis.marlune.data.repository.SavedSessionRepository
import com.luis.marlune.playback.PlaybackRepository

/**
 * Puente entre Compose y el [AppContainer] manual: recupera los singletons de datos desde la
 * `Application` para inyectarlos en las factorías de ViewModel de cada pantalla. Sin framework de DI.
 */
@Composable
fun rememberMusicRepository(): MusicRepository {
    val app = LocalContext.current.applicationContext as MarluneApplication
    return app.container.musicRepository
}

@Composable
fun rememberPlaybackRepository(): PlaybackRepository {
    val app = LocalContext.current.applicationContext as MarluneApplication
    return app.container.playbackRepository
}

@Composable
fun rememberHistoryRepository(): HistoryRepository {
    val app = LocalContext.current.applicationContext as MarluneApplication
    return app.container.historyRepository
}

@Composable
fun rememberFavoritesRepository(): FavoritesRepository {
    val app = LocalContext.current.applicationContext as MarluneApplication
    return app.container.favoritesRepository
}

@Composable
fun rememberSavedSessionRepository(): SavedSessionRepository {
    val app = LocalContext.current.applicationContext as MarluneApplication
    return app.container.savedSessionRepository
}

@Composable
fun rememberLyricsRepository(): LyricsRepository {
    val app = LocalContext.current.applicationContext as MarluneApplication
    return app.container.lyricsRepository
}

@Composable
fun rememberSettingsStore(): SettingsStore {
    val app = LocalContext.current.applicationContext as MarluneApplication
    return app.container.settingsStore
}

@Composable
fun rememberLibrarySortStore(): LibrarySortStore {
    val app = LocalContext.current.applicationContext as MarluneApplication
    return app.container.librarySortStore
}

@Composable
fun rememberAppPrefsStore(): AppPrefsStore {
    val app = LocalContext.current.applicationContext as MarluneApplication
    return app.container.appPrefsStore
}

@Composable
fun rememberPlaylistRepository(): PlaylistRepository {
    val app = LocalContext.current.applicationContext as MarluneApplication
    return app.container.playlistRepository
}
