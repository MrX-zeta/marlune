package com.luis.marlune.playback

import android.util.Log
import androidx.annotation.OptIn
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
import com.luis.marlune.R
import com.luis.marlune.ui.widget.MarluneWidget
import com.luis.marlune.ui.widget.WidgetPlaybackBus
import com.luis.marlune.ui.widget.WidgetPlaybackState
import com.luis.marlune.ui.widget.loadWidgetArtwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * evento. Así el widget no depende del `MediaController` de la UI (que se libera al cerrar la app) y no
 * sufre reconexiones ni congelamientos. Es una vía INDEPENDIENTE; no altera el connect/release de la UI.
 */
class MarluneMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    // Scope en el hilo principal: el acceso al player (posición, isPlaying…) debe ocurrir en main.
    private val widgetScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var tickerJob: Job? = null
    private var artworkJob: Job? = null
    @Volatile private var currentArtwork: android.graphics.Bitmap? = null
    private var artworkKey: String? = null

    // En cada evento relevante del player (pista, play/pausa, shuffle, seek), refresca el estado del
    // widget y lo empuja al instante. La POSICIÓN no viaja por aquí: tiene su propio pulso regular
    // (arranca/para según isPlaying, sin reiniciarse en cada evento → cadencia constante, sin tirones).
    private val widgetListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishWidgetState()
            if (player.isPlaying) ensureTicker() else stopTicker()
        }
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

        mediaSession = MediaSession.Builder(this, player).build()

        // Small icon de la notificación de reproducción: icono PLANO monocromo (`ic_notification`); el
        // sistema lo tiñe, así que NO se le da color (un icono a color se vería como un cuadro).
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification)
        setMediaNotificationProvider(notificationProvider)

        // Vía del widget: escucha eventos, expón el player para el transporte y siembra el estado.
        player.addListener(widgetListener)
        WidgetPlaybackBus.attachPlayer(player)
        Log.d(TAG, "service onCreate: player attached to widget bus")
        publishWidgetState()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Si el usuario descarta la app desde recientes y NADA se está reproduciendo, no tiene sentido
     * mantener el servicio vivo: se detiene. Si hay reproducción en curso, sigue (con su notificación).
     */
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        tickerJob?.cancel()
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

    /** Lee el estado real del player (en main), refresca la carátula si cambió la pista y empuja al widget. */
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
                isPlaying = p.isPlaying,
                shuffle = p.shuffleModeEnabled,
                positionMs = p.currentPosition.coerceAtLeast(0L),
                durationMs = if (p.duration == C.TIME_UNSET) 0L else p.duration.coerceAtLeast(0L),
            ),
        )
        Log.d(TAG, "publish: playing=${p.isPlaying} title='${item?.mediaMetadata?.title}' pos=${p.currentPosition}")
        updateWidgets()
    }

    /** Carga la carátula (Coil) solo cuando cambia la pista; una instancia nueva por pista (nota MIUI). */
    private fun refreshArtworkIfNeeded(item: MediaItem?) {
        val id = item?.mediaId
        if (id == artworkKey) return
        artworkKey = id
        currentArtwork = null
        artworkJob?.cancel()
        val uri = item?.mediaMetadata?.artworkUri ?: return
        artworkJob = widgetScope.launch {
            val bmp = withContext(Dispatchers.Default) { loadWidgetArtwork(applicationContext, uri, id) }
            if (artworkKey == id) {
                currentArtwork = bmp
                publishWidgetState() // re-publica ya con la carátula (early-return evita bucle: misma key)
            }
        }
    }

    private fun updateWidgets() {
        widgetScope.launch { runCatching { MarluneWidget().updateAll(applicationContext) } }
    }

    /**
     * Pulso de posición: cadencia FIJA de 2 s (como un reloj) mientras suena. NO se reinicia con los
     * eventos —solo arranca al empezar a sonar y para al pausar— así la marea avanza a saltos regulares.
     */
    private fun ensureTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = widgetScope.launch {
            while (isActive) {
                delay(POSITION_PUSH_MS)
                val p = mediaSession?.player ?: break
                if (!p.isPlaying) break
                publishPosition(p)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /** Empuja solo la posición (sin re-leer metadatos), copiando el estado publicado. */
    private fun publishPosition(p: Player) {
        WidgetPlaybackBus.publish(
            WidgetPlaybackBus.state.value.copy(
                positionMs = p.currentPosition.coerceAtLeast(0L),
                durationMs = if (p.duration == C.TIME_UNSET) 0L else p.duration.coerceAtLeast(0L),
            ),
        )
        updateWidgets()
    }

    private companion object {
        const val TAG = "MarluneWidget"
        const val POSITION_PUSH_MS = 2_000L // pulso fijo; no bajar (coste en batería)
    }
}
