package com.luis.marlune.playback

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Servicio de reproducción de Marlune: aloja el [ExoPlayer] y su [MediaSession]. La UI se conecta
 * con un `MediaController` (nunca toca el player directamente). Reproduce desde los content URIs de
 * MediaStore (Fase 1), sin rutas de archivo ni red.
 *
 * Media3 gestiona por nosotros: foco de audio y ducking (baja volumen con notificaciones, pausa en
 * llamada) vía `setAudioAttributes(..., handleAudioFocus = true)`; pausa al desconectar auriculares/
 * Bluetooth vía `setHandleAudioBecomingNoisy(true)`; y la notificación de reproducción con controles
 * (incluida su gestión en segundo plano y al deslizar) a través de `MediaSessionService`.
 */
class MarluneMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

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
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
