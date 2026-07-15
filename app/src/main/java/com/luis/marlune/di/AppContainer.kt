package com.luis.marlune.di

import android.content.Context
import androidx.room.Room
import com.luis.marlune.data.database.MarluneDatabase
import com.luis.marlune.data.datastore.LibrarySortStore
import com.luis.marlune.data.datastore.LyricsFolderStore
import com.luis.marlune.data.datastore.SessionStore
import com.luis.marlune.data.datastore.SettingsStore
import com.luis.marlune.data.lyrics.LrcLibClient
import com.luis.marlune.data.lyrics.NetworkLyricsCache
import com.luis.marlune.data.mediastore.MediaStoreAudioSource
import com.luis.marlune.data.repository.FavoritesRepository
import com.luis.marlune.data.repository.HistoryRepository
import com.luis.marlune.data.repository.LyricsRepository
import com.luis.marlune.data.repository.MusicRepository
import com.luis.marlune.data.repository.PlaylistRepository
import com.luis.marlune.data.repository.SavedSessionRepository
import com.luis.marlune.playback.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val PlayRecordDelayMs = 5_000L // ~5s reproduciendo antes de registrar (evita skips)
private const val SessionSaveIntervalMs = 10_000L // guardado periódico de la posición mientras suena

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
    ).addMigrations(MarluneDatabase.MIGRATION_1_2, MarluneDatabase.MIGRATION_2_3).build()

    /** Historial de reproducción (Room) resuelto contra la biblioteca real. */
    val historyRepository: HistoryRepository = HistoryRepository(database.playHistoryDao(), musicRepository)

    /** Favoritos ("Me gusta") (Room) resueltos contra la biblioteca real. */
    val favoritesRepository: FavoritesRepository = FavoritesRepository(database.favoriteDao(), musicRepository)

    /** Listas de reproducción del usuario (Room), con canciones resueltas contra la biblioteca. */
    val playlistRepository: PlaylistRepository = PlaylistRepository(database.playlistDao(), musicRepository)

    private val sessionStore = SessionStore(context.applicationContext)

    /** Sesión de reproducción persistida (DataStore) resuelta contra la biblioteca. */
    val savedSessionRepository: SavedSessionRepository = SavedSessionRepository(sessionStore, musicRepository)

    private val lyricsFolderStore = LyricsFolderStore(context.applicationContext)

    /** Ajustes de la app (opt-in de letras por internet, off por defecto). */
    val settingsStore = SettingsStore(context.applicationContext)

    /** Preferencias de Biblioteca (orden de Canciones), persistidas entre sesiones. */
    val librarySortStore = LibrarySortStore(context.applicationContext)

    /**
     * Letras: local (.lrc por SAF) y, SOLO si el usuario activa el opt-in, red (LRCLIB) como último
     * recurso. Lectura/parseo en IO, cacheado (memoria + disco privado para las descargadas).
     */
    val lyricsRepository: LyricsRepository = LyricsRepository(
        context.applicationContext,
        lyricsFolderStore,
        LrcLibClient(),
        NetworkLyricsCache(context.applicationContext),
    )

    init {
        recordPlaysToHistory()
        persistSession()
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

    /**
     * Persiste la sesión (cola + índice + posición): guarda al cambiar cola/índice o al pausar
     * (`collectLatest` reinicia y guarda al instante), y periódicamente mientras suena (para
     * mantener la posición fresca ante un cierre en frío). NUNCA sobrescribe con estado vacío, así
     * que un arranque en frío sin reproducir no borra la sesión guardada.
     */
    private fun persistSession() {
        appScope.launch {
            playbackRepository.state
                .map { Triple(it.hasItem, it.currentIndex to it.mediaId, it.isPlaying) }
                .distinctUntilChanged()
                .collectLatest { (hasItem, _, _) ->
                    if (!hasItem) return@collectLatest
                    saveSessionNow()
                    while (isActive) {
                        delay(SessionSaveIntervalMs)
                        saveSessionNow()
                    }
                }
        }
    }

    private suspend fun saveSessionNow() {
        val state = playbackRepository.state.value
        if (state.hasItem && state.queueIds.isNotEmpty()) {
            savedSessionRepository.save(state.queueIds, state.currentIndex, state.positionMs)
        }
    }
}
