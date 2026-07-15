package com.luis.marlune.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Estado ligero que el widget necesita para pintarse. Incluye la carátula ya decodificada como
 * [Bitmap] y el acento extraído de ella ([accentArgb], argb; `null` = acento de marca), ambos calculados
 * por el servicio (no el widget), para que la composición del widget sea síncrona: sin `produceState`
 * ni recargas que parpadeen al empujar `updateAll`. Sin posición: el widget ya no muestra progreso.
 */
data class WidgetPlaybackState(
    val hasItem: Boolean,
    val mediaId: String?,
    val title: String,
    val artist: String,
    val artwork: Bitmap?,
    val accentArgb: Int?,
    val isPlaying: Boolean,
    val shuffle: Boolean,
) {
    companion object {
        val Empty = WidgetPlaybackState(false, null, "", "", null, null, false, false)
    }
}

/**
 * Bus en memoria del proceso: el [com.luis.marlune.playback.MarluneMediaService] PUBLICA aquí el
 * estado (está vivo mientras suena la música y conoce cada cambio al instante), y el widget lo OBSERVE.
 * Así el widget no depende del ciclo de vida de la UI ni de reconectar un `MediaController`.
 *
 * Los comandos de transporte operan sobre el [Player] del servicio (mismo proceso), en el hilo
 * principal: instantáneos, sin reconexión. Si el servicio no está activo (nada sonando), el player es
 * nulo y los comandos son no-op; el estado queda vacío y el widget muestra "Toca para abrir".
 */
object WidgetPlaybackBus {

    private val _state = MutableStateFlow(WidgetPlaybackState.Empty)
    val state: StateFlow<WidgetPlaybackState> = _state.asStateFlow()

    @Volatile private var player: Player? = null
    private val main = Handler(Looper.getMainLooper())

    fun attachPlayer(p: Player) { player = p }

    fun detachPlayer() {
        player = null
        _state.value = WidgetPlaybackState.Empty
    }

    fun publish(state: WidgetPlaybackState) { _state.value = state }

    fun playPause() = post { if (it.isPlaying) it.pause() else it.play() }
    fun next() = post { it.seekToNextMediaItem() }
    fun previous() = post { it.seekToPreviousMediaItem() }
    fun toggleShuffle() = post { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    /** Reflejo OPTIMISTA de play/pausa: el icono del widget cambia al instante, sin esperar el evento. */
    fun optimisticTogglePlaying() = _state.update { it.copy(isPlaying = !it.isPlaying) }

    private fun post(block: (Player) -> Unit) = main.post { player?.let(block) }
}

/**
 * Decodifica la carátula (Coil→Bitmap software) para RemoteViews. Copia NUEVA con dimensiones que
 * varían por pista, para vencer el caché de `ImageView` de MIUI (no repinta si la imagen "no cambia").
 */
suspend fun loadWidgetArtwork(context: Context, uri: Uri?, mediaId: String?): Bitmap? {
    if (uri == null) return null
    val targetSizePx = 256 + (abs((mediaId ?: "").hashCode()) % 8)
    return runCatching {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false)
            .size(targetSizePx)
            .build()
        val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
        drawable?.toBitmap()?.copy(Bitmap.Config.ARGB_8888, false)
    }.getOrNull()
}
