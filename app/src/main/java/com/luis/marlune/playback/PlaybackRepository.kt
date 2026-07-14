package com.luis.marlune.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.luis.marlune.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de reproducción expuesto a la app (Fase 2, mínimo). En la Fase 3 se ampliará con posición,
 * duración y cola para alimentar la UI del Player.
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentMediaId: String? = null,
)

/**
 * Envuelve Media3 para que los ViewModels/UI no se acoplen a ExoPlayer ni al `MediaController`.
 *
 * El controlador se conecta de forma ASÍNCRONA al [MarluneMediaService] (`ListenableFuture`); su
 * disponibilidad se expone como [isConnected] y no se asume lista al instante: una acción de
 * reproducción emitida antes de conectar queda pendiente y se ejecuta al conectar. Todo el acceso al
 * controlador ocurre en el hilo principal (requisito de Media3).
 */
class PlaybackRepository(context: Context) {

    private val appContext = context.applicationContext

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    /** Acción encolada mientras el controlador aún no está listo (p. ej. el primer "reproducir"). */
    private var pendingAction: ((MediaController) -> Unit)? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _playbackState.value = PlaybackState(
                isPlaying = player.isPlaying,
                currentMediaId = player.currentMediaItem?.mediaId,
            )
        }
    }

    /** Conecta el controlador al servicio (idempotente). Llamar desde el hilo principal. */
    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(appContext, ComponentName(appContext, MarluneMediaService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val c = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = c
                c.addListener(listener)
                _isConnected.value = true
                _playbackState.value = PlaybackState(c.isPlaying, c.currentMediaItem?.mediaId)
                pendingAction?.let { it(c); pendingAction = null }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    /** Libera el controlador (no detiene la reproducción: el servicio sigue). Llamar en el hilo principal. */
    fun release() {
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        _isConnected.value = false
    }

    /**
     * Fija la COLA real desde la biblioteca y empieza a reproducir en [startIndex]. Si el controlador
     * aún no está conectado, la acción queda pendiente y se ejecuta al conectar (y dispara la conexión).
     */
    fun playSongs(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem() }
        val index = startIndex.coerceIn(0, items.lastIndex)
        runOrQueue { c ->
            c.setMediaItems(items, index, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playPause() = controller?.let { if (it.isPlaying) it.pause() else it.play() }

    fun pause() {
        controller?.pause()
    }

    fun resume() {
        controller?.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    private fun runOrQueue(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            action(c)
        } else {
            pendingAction = action
            connect()
        }
    }

    private fun Song.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString()) // el _ID de MediaStore: clave para referenciar (Room, Fase 3)
            .setUri(contentUri) // content URI, nunca ruta (scoped storage)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artworkUri) // la notificación carga la carátula desde este URI
                    .build(),
            )
            .build()
}
