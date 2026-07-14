package com.luis.marlune.di

import android.content.Context
import androidx.room.Room
import com.luis.marlune.data.database.MarluneDatabase
import com.luis.marlune.data.mediastore.MediaStoreAudioSource
import com.luis.marlune.data.repository.HistoryRepository
import com.luis.marlune.data.repository.MusicRepository
import com.luis.marlune.playback.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PlayRecordDelayMs = 5_000L // ~5s reproduciendo antes de registrar (evita skips)

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

    private val database: MarluneDatabase = Room.databaseBuilder(
        context.applicationContext,
        MarluneDatabase::class.java,
        "marlune.db",
    ).build()

    /** Historial de reproducción (Room) resuelto contra la biblioteca real. */
    val historyRepository: HistoryRepository = HistoryRepository(database.playHistoryDao(), musicRepository)

    init {
        recordPlaysToHistory()
    }

    /**
     * Registra en el historial la pista que suena tras [PlayRecordDelayMs] reproduciéndose: observa
     * el estado real y, con `collectLatest`, si la pista cambia o se pausa antes del umbral, se
     * cancela el registro (no cuenta skips). Solo emite al cambiar de pista o play/pausa.
     */
    private fun recordPlaysToHistory() {
        appScope.launch {
            playbackRepository.state
                .map { if (it.hasItem && it.isPlaying) it.mediaId?.toLongOrNull() else null }
                .distinctUntilChanged()
                .collectLatest { songId ->
                    if (songId != null) {
                        delay(PlayRecordDelayMs)
                        historyRepository.recordPlay(songId)
                    }
                }
        }
    }
}
