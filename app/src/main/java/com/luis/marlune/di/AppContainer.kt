package com.luis.marlune.di

import android.content.Context
import androidx.room.Room
import com.luis.marlune.data.database.MarluneDatabase
import com.luis.marlune.data.datastore.AppPrefsStore
import com.luis.marlune.data.datastore.LibrarySortStore
import com.luis.marlune.data.datastore.LyricsFolderStore
import com.luis.marlune.data.datastore.SearchHistoryStore
import com.luis.marlune.data.datastore.SessionStore
import com.luis.marlune.data.datastore.SettingsStore
import com.luis.marlune.data.lyrics.LrcLibClient
import com.luis.marlune.data.lyrics.NetworkLyricsCache
import com.luis.marlune.data.mediastore.MediaStoreAudioSource
import com.luis.marlune.data.repository.FavoritesRepository
import com.luis.marlune.data.repository.HistoryRepository
import com.luis.marlune.data.repository.LibraryState
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
import kotlinx.coroutines.flow.first
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

    /** Ajustes de la app (opt-in de letras por internet off por defecto; filtro de clips cortos). */
    val settingsStore = SettingsStore(context.applicationContext)

    val musicRepository: MusicRepository = MusicRepository(mediaStoreAudioSource, settingsStore, appScope)

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

    /** Sesión de reproducción en crudo (DataStore). La ESCRITURA la hace el servicio de playback. */
    val sessionStore = SessionStore(context.applicationContext)

    /** Sesión de reproducción persistida (DataStore) resuelta contra la biblioteca. */
    val savedSessionRepository: SavedSessionRepository = SavedSessionRepository(sessionStore, musicRepository)

    private val lyricsFolderStore = LyricsFolderStore(context.applicationContext)

    /** Flags de primera experiencia (onboarding completado, permiso de notificaciones ya pedido). */
    val appPrefsStore = AppPrefsStore(context.applicationContext)

    /** Preferencias de Biblioteca (orden de Canciones), persistidas entre sesiones. */
    val librarySortStore = LibrarySortStore(context.applicationContext)

    /** Búsquedas recientes de Buscar (DataStore), persistidas entre sesiones. */
    val searchHistoryStore = SearchHistoryStore(context.applicationContext)

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
        // La persistencia de la sesión NO vive aquí: la escribe el servicio (MarluneMediaService),
        // que posee el player y sobrevive a la UI. El escritor que había aquí observaba el
        // PlaybackRepository de la UI y, al morir esta, seguía re-escribiendo una posición RANCIA
        // (estado congelado) cada 10 s, pisando los datos frescos.
        restoreSessionOnColdStart()
    }

    /**
     * Restaura la sesión guardada al ABRIR la app con el proceso frío: rearma la cola EN PAUSA en la
     * pista/posición/modos guardados (el usuario reanuda con play). Corre UNA sola vez por proceso
     * (vive en el init del contenedor), y solo actúa cuando:
     *  - el `MediaController` conectó — lo que implica permisos/onboarding superados (la UI solo
     *    conecta tras ese gate), y
     *  - la biblioteca está CARGADA — la sesión se resuelve contra MediaStore: ids borrados se
     *    omiten y el índice se reajusta ([SavedSessionRepository.resolve]); si no queda nada, no
     *    hay nada que restaurar (sin mini-player fantasma).
     * Si al llegar aquí YA hay cola (el servicio seguía vivo, o el usuario reprodujo algo antes de
     * cargar la biblioteca), no toca nada: la cola activa siempre gana.
     */
    private fun restoreSessionOnColdStart() {
        appScope.launch {
            playbackRepository.isConnected.first { it }
            musicRepository.library.first { it is LibraryState.Content }
            val session = savedSessionRepository.savedSession.first() ?: return@launch
            if (playbackRepository.state.value.hasItem) return@launch // cola activa: no pisar
            playbackRepository.restoreSession(
                songs = session.songs,
                startIndex = session.index,
                startPositionMs = session.positionMs,
                shuffle = session.shuffle,
                repeatMode = session.repeatMode,
            )
        }
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
