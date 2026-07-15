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
import com.luis.marlune.R
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
    }
}
