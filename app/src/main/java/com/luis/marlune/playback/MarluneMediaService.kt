package com.luis.marlune.playback

import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.toArgb
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.glance.appwidget.updateAll
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.luis.marlune.MarluneApplication
import com.luis.marlune.R
import com.luis.marlune.data.repository.LibraryState
import com.luis.marlune.data.repository.SavedSession
import com.luis.marlune.domain.model.RepeatMode
import com.luis.marlune.ui.theme.accentFromArtwork
import com.luis.marlune.ui.widget.MarluneWidget
import com.luis.marlune.ui.widget.WidgetPlaybackBus
import com.luis.marlune.ui.widget.WidgetPlaybackState
import com.luis.marlune.ui.widget.loadWidgetArtwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Servicio de reproducción de Marlune: aloja el [ExoPlayer] y su [MediaSession]. La UI se conecta
 * con un `MediaController` (nunca toca el player directamente). Reproduce desde los content URIs de
 * MediaStore (Fase 1), sin rutas de archivo ni red.
 *
 * Media3 gestiona por nosotros: foco de audio y ducking (baja volumen con notificaciones, pausa en
 * llamada) vía `setAudioAttributes(..., handleAudioFocus = true)`; pausa al desconectar auriculares/
 * Bluetooth vía `setHandleAudioBecomingNoisy(true)`; y la notificación de reproducción con controles
 * (incluida su gestión en segundo plano y al deslizar) a través de `MediaSessionService`.
 *
 * Además EMPUJA el estado al WIDGET: como el servicio vive mientras suena la música y conoce cada
 * cambio en el instante en que ocurre, publica en [WidgetPlaybackBus] y llama a `updateAll` en cada
 * evento (pista, play/pausa, shuffle, me gusta). El widget NO muestra progreso, así que no hay pulso
 * periódico: se actualiza SOLO por estos eventos (cero refrescos en reposo). Es una vía INDEPENDIENTE;
 * no altera el connect/release de la UI.
 */
class MarluneMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    // Scope en el hilo principal: el acceso al player (isPlaying, metadatos…) debe ocurrir en main.
    private val widgetScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var artworkJob: Job? = null
    @Volatile private var currentArtwork: android.graphics.Bitmap? = null
    @Volatile private var currentAccentArgb: Int? = null // acento extraído de la carátula (cache por pista)
    private var artworkKey: String? = null

    // En cada evento relevante del player, refresca el estado del widget y lo empuja al instante.
    private val widgetListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publishWidgetState()
    }

    // --- Persistencia de la SESIÓN (cola + índice + posición + modos) ---
    // Vive AQUÍ y no en la UI: el servicio posee el player y sobrevive a la UI, así la sesión se
    // guarda fresca aunque la app se cierre. Escrituras con DEBOUNCE (no castigar disco/batería) y
    // posición con throttle mientras suena; el guardado inmediato queda para pausar y morir.
    private val sessionStore by lazy { (application as MarluneApplication).container.sessionStore }
    private var sessionQueueIds: List<Long> = emptyList() // cacheada: solo se recorre la cola al CAMBIAR
    private var sessionHadQueue = false // para limpiar UNA vez cuando la cola se vacía de verdad
    private var sessionSaveJob: Job? = null
    private var sessionPositionJob: Job? = null

    private val sessionListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                // Único punto que recorre la cola entera (lección de perf: nunca por evento).
                sessionQueueIds = (0 until player.mediaItemCount)
                    .mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
                if (player.mediaItemCount == 0) {
                    // Cola vacía REAL (se quitó todo / stop): limpia la sesión guardada una vez.
                    if (sessionHadQueue) {
                        sessionHadQueue = false
                        widgetScope.launch { runCatching { sessionStore.clear() } }
                    }
                    return
                }
            }
            if (player.mediaItemCount == 0) return
            sessionHadQueue = true
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) {
                scheduleSessionSave()
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                syncSessionPositionTicker(player.isPlaying)
                // Al pausar, la posición se guarda AL INSTANTE (no se pierden los últimos segundos).
                if (!player.isPlaying) scheduleSessionSave(immediate = true)
            }
        }
    }

    /** Programa un guardado con debounce (o inmediato); un solo job pendiente, el último gana. */
    private fun scheduleSessionSave(immediate: Boolean = false) {
        sessionSaveJob?.cancel()
        sessionSaveJob = widgetScope.launch {
            if (!immediate) kotlinx.coroutines.delay(SESSION_SAVE_DEBOUNCE_MS)
            persistSessionSnapshot()
        }
    }

    /** Posición con throttle mientras suena (~5 s); parado, nada de polling. */
    private fun syncSessionPositionTicker(playing: Boolean) {
        sessionPositionJob?.cancel()
        if (!playing) return
        sessionPositionJob = widgetScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(SESSION_POSITION_INTERVAL_MS)
                persistSessionSnapshot()
            }
        }
    }

    /** Lee el snapshot del player (en main) y lo persiste; DataStore escribe fuera del hilo principal. */
    private suspend fun persistSessionSnapshot() {
        val p = mediaSession?.player ?: return
        if (p.mediaItemCount == 0 || sessionQueueIds.isEmpty()) return
        val meta = p.currentMediaItem?.mediaMetadata
        runCatching {
            sessionStore.save(
                ids = sessionQueueIds,
                index = p.currentMediaItemIndex,
                positionMs = p.currentPosition.coerceAtLeast(0L),
                shuffle = p.shuffleModeEnabled,
                repeatMode = p.repeatMode.toDomainRepeatMode(),
                // Metadatos de la pista actual: el widget pinta la última pista sin biblioteca.
                title = meta?.title?.toString().orEmpty(),
                artist = meta?.artist?.toString().orEmpty(),
                artworkUri = meta?.artworkUri?.toString(),
            )
        }
    }

    private fun Int.toDomainRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }

    private fun RepeatMode.toMedia3RepeatMode(): Int = when (this) {
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    }

    @OptIn(UnstableApi::class) // ExoPlayer y su Builder están marcados como API inestable en Media3.
    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            // Reanudación CANÓNICA: cuando llega un PLAY (widget frío vía intent de botón multimedia,
            // auriculares Bluetooth, reanudador de medios del sistema) con el player VACÍO, Media3
            // pide aquí la cola guardada, la arma, reproduce y gestiona él el foreground/notificación.
            .setCallback(SessionCallback())
            .build()

        // Small icon de la notificación de reproducción: icono PLANO monocromo (`ic_notification`); el
        // sistema lo tiñe, así que NO se le da color (un icono a color se vería como un cuadro).
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification)
        setMediaNotificationProvider(notificationProvider)

        // Vía del widget: escucha eventos, expón el player para el transporte y siembra el estado.
        player.addListener(widgetListener)
        // Vía de la sesión persistida: el servicio es el único escritor (sobrevive a la UI).
        player.addListener(sessionListener)
        WidgetPlaybackBus.attachPlayer(player)
        Log.d(TAG, "service onCreate: player attached to widget bus")
        publishWidgetState()
        maybeRestoreSessionCold()
    }

    /**
     * Arranque en FRÍO: si el player está vacío y hay sesión guardada, la restaura EN PAUSA (reanudar
     * es cosa del usuario, o de [SessionCallback.onPlaybackResumption] si el arranque vino de un PLAY).
     * Sin permiso, sin biblioteca o sin sesión, no hace nada (el widget publica vacío como hasta
     * ahora, sin crashear). Si mientras resolvía alguien armó cola (la UI restauró vía controller, o
     * el usuario reprodujo), NO la pisa. Al armar, los eventos re-publican el widget con la pista.
     */
    private fun maybeRestoreSessionCold() {
        widgetScope.launch {
            val session = resolveStoredSession() ?: return@launch
            val p = mediaSession?.player ?: return@launch
            if (p.mediaItemCount > 0) return@launch // ya hay cola (UI/usuario ganaron): no pisar
            p.shuffleModeEnabled = session.shuffle
            p.repeatMode = session.repeatMode.toMedia3RepeatMode()
            p.setMediaItems(
                session.songs.map { it.toMediaItem() },
                session.index.coerceIn(0, session.songs.lastIndex),
                session.positionMs.coerceAtLeast(0L),
            )
            p.prepare()
            Log.d(TAG, "cold restore: ${session.songs.size} pistas, en pausa")
        }
    }

    /**
     * Resuelve la sesión guardada contra la biblioteca real ([SavedSessionRepository]: omite pistas
     * borradas, reajusta el índice), con timeout. `null` = no hay sesión o no se pudo resolver
     * (sin permiso / biblioteca vacía) — el llamador no restaura nada.
     */
    private suspend fun resolveStoredSession(): SavedSession? =
        withTimeoutOrNull(SESSION_RESTORE_TIMEOUT_MS) {
            runCatching {
                val container = (application as MarluneApplication).container
                container.musicRepository.library.first { it is LibraryState.Content }
                container.savedSessionRepository.savedSession.first()
            }.getOrNull()
        }

    /**
     * Callback de la sesión: reanudación de reproducción. El sistema (o el widget, vía intent de
     * botón multimedia) pide reproducir con el player VACÍO → se devuelve la cola guardada con su
     * índice y posición; Media3 la arma, reproduce y gestiona la notificación/foreground. Los modos
     * (shuffle/repeat) se aplican sobre el player antes de resolver. Sin sesión → excepción en el
     * future (Media3 lo trata como "nada que reanudar", sin crashear).
     */
    private inner class SessionCallback : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            widgetScope.launch {
                val session = resolveStoredSession()
                if (session == null) {
                    future.setException(IllegalStateException("Sin sesión guardada que reanudar"))
                    return@launch
                }
                mediaSession.player.shuffleModeEnabled = session.shuffle
                mediaSession.player.repeatMode = session.repeatMode.toMedia3RepeatMode()
                Log.d(TAG, "playback resumption: ${session.songs.size} pistas desde ${session.positionMs}ms")
                future.set(
                    MediaSession.MediaItemsWithStartPosition(
                        session.songs.map { it.toMediaItem() },
                        session.index.coerceIn(0, session.songs.lastIndex),
                        session.positionMs.coerceAtLeast(0L),
                    ),
                )
            }
            return future
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Si el usuario descarta la app desde recientes y NADA se está reproduciendo, no tiene sentido
     * mantener el servicio vivo: se detiene. Si hay reproducción en curso, sigue (con su notificación).
     */
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Posición fresca ANTE un posible cierre inminente (deslizar de recientes).
        scheduleSessionSave(immediate = true)
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Último guardado de la sesión ANTES de soltar el player: snapshot en main aquí mismo y
        // escritura en un scope independiente (widgetScope se cancela abajo). Si el proceso muere de
        // golpe sin pasar por aquí, se pierden como mucho ~5 s de posición, nunca la cola.
        mediaSession?.player?.let { p ->
            if (p.mediaItemCount > 0 && sessionQueueIds.isNotEmpty()) {
                val ids = sessionQueueIds
                val index = p.currentMediaItemIndex
                val position = p.currentPosition.coerceAtLeast(0L)
                val shuffle = p.shuffleModeEnabled
                val repeat = p.repeatMode.toDomainRepeatMode()
                val meta = p.currentMediaItem?.mediaMetadata
                val title = meta?.title?.toString().orEmpty()
                val artist = meta?.artist?.toString().orEmpty()
                val artworkUri = meta?.artworkUri?.toString()
                val store = sessionStore
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { store.save(ids, index, position, shuffle, repeat, title, artist, artworkUri) }
                }
            }
        }
        sessionSaveJob?.cancel()
        sessionPositionJob?.cancel()
        mediaSession?.player?.removeListener(sessionListener)
        artworkJob?.cancel()
        mediaSession?.player?.removeListener(widgetListener)
        WidgetPlaybackBus.detachPlayer() // deja el estado vacío → el widget muestra "Toca para abrir"
        Log.d(TAG, "service onDestroy: detached; pushing empty widget")
        // Empuje final en un scope independiente (widgetScope se cancela abajo).
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { MarluneWidget().updateAll(applicationContext) }
        }
        widgetScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** Lee el estado real del player (en main), refresca carátula+acento si cambió la pista y empuja. */
    private fun publishWidgetState() {
        val p = mediaSession?.player ?: return
        val item = p.currentMediaItem
        refreshArtworkIfNeeded(item)
        val hasItem = item != null && p.mediaItemCount > 0
        WidgetPlaybackBus.publish(
            WidgetPlaybackState(
                hasItem = hasItem,
                mediaId = item?.mediaId,
                title = item?.mediaMetadata?.title?.toString().orEmpty(),
                artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
                artwork = currentArtwork,
                accentArgb = currentAccentArgb,
                isPlaying = p.isPlaying,
                shuffle = p.shuffleModeEnabled,
            ),
        )
        Log.d(TAG, "publish: playing=${p.isPlaying} title='${item?.mediaMetadata?.title}'")
        updateWidgets()
    }

    /**
     * Al cambiar de pista, carga la carátula (Coil, 256 px) y extrae su acento con el MISMO algoritmo
     * que la app ([accentFromArtwork]); ambos en fondo y cacheados por pista (no se repite el trabajo en
     * cada actualización). Una instancia de bitmap nueva por pista (nota MIUI).
     */
    private fun refreshArtworkIfNeeded(item: MediaItem?) {
        val id = item?.mediaId
        if (id == artworkKey) return
        artworkKey = id
        currentArtwork = null
        currentAccentArgb = null
        artworkJob?.cancel()
        val uri = item?.mediaMetadata?.artworkUri ?: return
        artworkJob = widgetScope.launch {
            val (bmp, accent) = withContext(Dispatchers.Default) {
                val b = loadWidgetArtwork(applicationContext, uri, id)
                b to b?.let { accentFromArtwork(it)?.toArgb() } // null si es monocroma → marca en el widget
            }
            if (artworkKey == id) {
                currentArtwork = bmp
                currentAccentArgb = accent
                publishWidgetState() // re-publica ya con carátula+acento (early-return evita bucle: misma key)
            }
        }
    }

    private fun updateWidgets() {
        widgetScope.launch { runCatching { MarluneWidget().updateAll(applicationContext) } }
    }

    private companion object {
        const val TAG = "MarluneWidget"
        const val SESSION_SAVE_DEBOUNCE_MS = 500L // agrupa ráfagas de eventos en una escritura
        const val SESSION_POSITION_INTERVAL_MS = 5_000L // posición fresca sin castigar el disco
        const val SESSION_RESTORE_TIMEOUT_MS = 10_000L // techo para resolver biblioteca al restaurar
    }
}
