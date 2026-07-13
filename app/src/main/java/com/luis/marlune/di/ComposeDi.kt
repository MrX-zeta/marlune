package com.luis.marlune.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.luis.marlune.MarluneApplication
import com.luis.marlune.data.repository.MusicRepository

/**
 * Puente entre Compose y el [AppContainer] manual: recupera los singletons de datos desde la
 * `Application` para inyectarlos en las factorías de ViewModel de cada pantalla. Sin framework de DI.
 */
@Composable
fun rememberMusicRepository(): MusicRepository {
    val app = LocalContext.current.applicationContext as MarluneApplication
    return app.container.musicRepository
}
