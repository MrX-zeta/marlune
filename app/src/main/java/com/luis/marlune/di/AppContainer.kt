package com.luis.marlune.di

import android.content.Context
import com.luis.marlune.data.mediastore.MediaStoreAudioSource
import com.luis.marlune.data.repository.MusicRepository
import com.luis.marlune.playback.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Contenedor de dependencias manual (sin framework de DI). Vive en la Application y expone los
 * singletons de datos que los ViewModels consumirán. Todo local, sin red.
 */
class AppContainer(context: Context) {

    // Scope de aplicación para los Flows compartidos del repositorio.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mediaStoreAudioSource = MediaStoreAudioSource(context.applicationContext)

    val musicRepository: MusicRepository = MusicRepository(mediaStoreAudioSource, appScope)

    /** Motor de reproducción (Media3), envuelto para no acoplar la UI a ExoPlayer/MediaController. */
    val playbackRepository: PlaybackRepository = PlaybackRepository(context.applicationContext)
}
