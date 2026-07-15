package com.luis.marlune.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.luis.marlune.domain.model.RepeatMode
import com.luis.marlune.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Naturaleza del último cambio de pista, para que la UI elija la animación:
 * [NEXT]/[PREVIOUS] → slide direccional dentro de la cola; [DIRECT] → carga directa (cola nueva
 * por selección), sin slide: solo crossfade/aparición.
 */
enum class TrackChange { DIRECT, NEXT, PREVIOUS }

/**
 * Estado de reproducción REAL, leído del `MediaController`. `hasItem = false` significa que no hay
 * nada en la cola (estado vacío: la UI no muestra una pista falsa). Sin datos mock.
 *
 * [transitionId] incrementa en cada cambio de pista (para reaccionar una sola vez) y [transition]
 * dice de qué tipo fue, derivado del `reason` de Media3 (fuente ÚNICA de dirección de animación).
 */
data class PlaybackState(
    val hasItem: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val artworkUri: Uri? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val currentIndex: Int = 0,
    val queueSize: Int = 0,
    /** Hay pista a la que saltar (según la cola y el modo de repetición). */
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    /** Ids (`_ID` de MediaStore) de la cola en orden, para persistir la sesión. */
    val queueIds: List<Long> = emptyList(),
    val transitionId: Int = 0,
    val transition: TrackChange = TrackChange.DIRECT,
)

/**
 * Una entrada de la cola real del `MediaController`, con los metadatos que la UI necesita para
 * pintarla (sin re-consultar la biblioteca). [mediaId] es el `_ID` de MediaStore como texto.
 * [manual] indica que se añadió a mano con "Añadir a la cola" (marcado en los extras del MediaItem):
 * la UI de cola las agrupa aparte mientras no se hayan reproducido. Solo es presentación; la cola
 * real del player sigue siendo una sola.
 */
data class QueueItem(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val manual: Boolean = false,
)

/** Opciones del temporizador de apagado. [minutes] = -1 significa "al terminar la canción". */
enum class SleepTimerOption(val minutes: Int) {
    OFF(0),
    MIN_15(15),
    MIN_30(30),
    MIN_45(45),
    HOUR_1(60),
    END_OF_TRACK(-1),
}

/**
 * Estado del temporizador ACTIVO. [endElapsedRealtime] (base `SystemClock.elapsedRealtime`) permite a
 * la UI mostrar el tiempo restante en vivo; es `null` para "al terminar la canción" (sin cuenta atrás).
 */
data class SleepTimerState(
    val option: SleepTimerOption,
    val endElapsedRealtime: Long?,
)

/**
 * Envuelve Media3 para que los ViewModels/UI no se acoplen a ExoPlayer ni al `MediaController`.
 *
 * El controlador se conecta de forma ASÍNCRONA al [MarluneMediaService] (`ListenableFuture`); su
 * disponibilidad se expone como [isConnected] y no se asume lista al instante: una acción emitida
 * antes de conectar queda pendiente y se ejecuta al conectar. El [state] refleja la reproducción
 * real (pista, posición, isPlaying, shuffle/repeat), actualizado por eventos del player y por un
 * ticker de posición mientras suena. Todo el acceso al controlador ocurre en el hilo principal.
 */
class PlaybackRepository(context: Context) {

    private val appContext = context.applicationContext
    private val mainScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingAction: ((MediaController) -> Unit)? = null
    private var positionJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    // Cola real como lista de items (con metadatos). Se reconstruye en los eventos del player
    // (añadir/quitar/mover/auto-avance), no en los ticks de posición. Para la UI de "A continuación".
    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    // Temporizador de apagado. Vive aquí (capa de playback, proceso de la app): mientras el proceso
    // sigue vivo —servicio en foreground durante la reproducción— funciona con la app en segundo plano.
    // NO se persiste: si el proceso muere, expira solo (no se reactiva al reabrir).
    private val _sleepTimer = MutableStateFlow<SleepTimerState?>(null)
    val sleepTimer: StateFlow<SleepTimerState?> = _sleepTimer.asStateFlow()
    private var sleepJob: Job? = null
    private var pauseAtTrackEnd = false

    // Naturaleza del último cambio de pista, derivada del `reason` de Media3 (fuente única).
    private var transitionId = 0
    private var transitionKind = TrackChange.DIRECT
    private var lastTransitionIndex = 0
    // Dirección PEDIDA por un skip explícito (next/previous, sea botón o swipe): la conocemos con
    // certeza, así no la inferimos por índice (la inferencia se invierte en colas de 2 elementos, donde
    // el wrap idx1→idx0 se confunde con "siguiente"). `null` para saltos por índice (cola) → se infiere.
    private var pendingSeekDirection: TrackChange? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val c = controller ?: return
            val newIndex = c.currentMediaItemIndex
            transitionKind = when (reason) {
                // Cola armada/reemplazada por una selección directa: sin slide (crossfade/aparición).
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> TrackChange.DIRECT
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> TrackChange.NEXT // auto-avance al terminar
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> // skip/prev/swipe o salto en la cola
                    // Si vino de un skip explícito, usa su dirección real; si no (salto por índice
                    // desde la cola), infiérela del cambio de índice.
                    pendingSeekDirection
                        ?: if (forwardFrom(lastTransitionIndex, newIndex, c.mediaItemCount)) TrackChange.NEXT
                        else TrackChange.PREVIOUS
                else -> TrackChange.DIRECT // REPEAT (repetir una): misma pista, sin slide
            }
            pendingSeekDirection = null // consumida (o irrelevante para este tipo de transición)
            lastTransitionIndex = newIndex
            transitionId++
            // Temporizador "al terminar la canción": la pista actual acabó y auto-avanzó → pausa aquí
            // (la siguiente queda cargada en pausa) y se desactiva solo.
            if (pauseAtTrackEnd && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                pauseAtTrackEnd = false
                _sleepTimer.value = null
                c.pause()
            }
            // El estado se re-emite en onEvents (que agrupa esta transición); ahí se incluye.
        }

        override fun onEvents(player: Player, events: Player.Events) = refresh()
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
                refresh()
                pendingAction?.let { it(c); pendingAction = null }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    /** Libera el controlador (no detiene la reproducción: el servicio sigue). Hilo principal. */
    fun release() {
        positionJob?.cancel()
        positionJob = null
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        _isConnected.value = false
    }

    /**
     * Fija la COLA real desde la biblioteca y empieza a reproducir en [startIndex] (opcionalmente
     * desde [startPositionMs], p. ej. al reanudar la sesión). Si el controlador aún no está
     * conectado, la acción queda pendiente y se ejecuta al conectar (y dispara la conexión).
     */
    fun playSongs(songs: List<Song>, startIndex: Int, startPositionMs: Long = 0L) {
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem() }
        val index = startIndex.coerceIn(0, items.lastIndex)
        runOrQueue { c ->
            c.setMediaItems(items, index, startPositionMs.coerceAtLeast(0L))
            c.prepare()
            c.play()
        }
    }

    /**
     * Encola [song] JUSTO DESPUÉS de la pista actual, sin interrumpir lo que suena (inserta en la cola
     * del `MediaController`). Si no hay nada en la cola, inicia la reproducción con esa canción. Si el
     * controlador aún no está conectado, la acción queda pendiente y se ejecuta al conectar.
     */
    fun addToQueueNext(song: Song) {
        runOrQueue { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItem(song.toMediaItem(manual = true))
                c.prepare()
                c.play()
            } else {
                val insertIndex = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
                c.addMediaItem(insertIndex, song.toMediaItem(manual = true))
            }
        }
    }

    /**
     * Quita de la cola la canción con este `_ID` (p. ej. al borrar su archivo). Si era la que sonaba,
     * ExoPlayer pasa a la siguiente automáticamente; si era la última, la reproducción se detiene con
     * gracia. Hilo principal. No hace nada si no está en la cola o el controlador no está listo.
     */
    fun removeFromQueue(songId: Long) {
        val c = controller ?: return
        val target = songId.toString()
        for (i in 0 until c.mediaItemCount) {
            if (c.getMediaItemAt(i).mediaId == target) {
                c.removeMediaItem(i)
                return
            }
        }
    }

    fun playPause() = controller?.let { if (it.isPlaying) it.pause() else it.play() }

    /** Salta a la pista de la cola en [index] sin rearmar la cola (seek al item) y reproduce. */
    fun playQueueItem(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        pendingSeekDirection = null // salto por índice: la dirección se infiere del cambio de índice
        c.seekToDefaultPosition(index)
        c.play()
    }

    /** Quita de la cola la pista en [index] (no toca la biblioteca ni el archivo). */
    fun removeQueueItem(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun next() {
        pendingSeekDirection = TrackChange.NEXT // dirección conocida: no inferir por índice
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        pendingSeekDirection = TrackChange.PREVIOUS // dirección conocida: no inferir por índice
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let { it.repeatMode = it.repeatMode.toRepeatMode().next().toMedia3() }
    }

    /**
     * Arma (o reemplaza) el temporizador de apagado. [SleepTimerOption.OFF] lo cancela. Para los modos
     * temporizados, al cumplirse hace un fade-out corto y pausa; para "al terminar la canción", se
     * pausa en el auto-avance (ver el listener). No persiste; expira solo si el proceso muere.
     */
    fun startSleepTimer(option: SleepTimerOption) {
        cancelSleepTimer()
        when (option) {
            SleepTimerOption.OFF -> Unit
            SleepTimerOption.END_OF_TRACK -> {
                pauseAtTrackEnd = true
                _sleepTimer.value = SleepTimerState(option, endElapsedRealtime = null)
            }
            else -> {
                val durationMs = option.minutes * 60_000L
                _sleepTimer.value = SleepTimerState(option, SystemClock.elapsedRealtime() + durationMs)
                sleepJob = mainScope.launch {
                    kotlinx.coroutines.delay(durationMs)
                    fadeOutAndPause()
                    _sleepTimer.value = null
                }
            }
        }
    }

    /** Cancela el temporizador de apagado (si hay uno). */
    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        pauseAtTrackEnd = false
        _sleepTimer.value = null
    }

    /** Fade-out breve del volumen y pausa; restaura el volumen para la próxima reproducción. */
    private suspend fun fadeOutAndPause() {
        val c = controller ?: return
        val startVolume = c.volume
        val steps = 12
        repeat(steps) { i ->
            c.volume = startVolume * (1f - (i + 1f) / steps)
            kotlinx.coroutines.delay(SLEEP_FADE_STEP_MS)
        }
        c.pause()
        c.volume = startVolume // el próximo play arranca a volumen normal
    }

    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        _state.value = PlaybackState(
            hasItem = item != null && c.mediaItemCount > 0,
            mediaId = item?.mediaId,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            artworkUri = item?.mediaMetadata?.artworkUri,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.knownOrZero(),
            isPlaying = c.isPlaying,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode.toRepeatMode(),
            currentIndex = c.currentMediaItemIndex,
            queueSize = c.mediaItemCount,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
            queueIds = c.queueIds(),
            transitionId = transitionId,
            transition = transitionKind,
        )
        _queue.value = c.queueItems()
        syncTicker(c.isPlaying)
    }

    /** Snapshot de la cola con metadatos (solo en eventos del player; barato). */
    private fun MediaController.queueItems(): List<QueueItem> =
        (0 until mediaItemCount).map { i ->
            val item = getMediaItemAt(i)
            QueueItem(
                mediaId = item.mediaId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                artworkUri = item.mediaMetadata.artworkUri,
                manual = item.mediaMetadata.extras?.getBoolean(EXTRA_MANUAL_QUEUE, false) == true,
            )
        }

    // Recorre la cola solo en eventos del player (no en cada tick de posición: el ticker copia el
    // estado sin llamar a refresh()), así que reconstruir la lista aquí es barato.
    private fun MediaController.queueIds(): List<Long> =
        (0 until mediaItemCount).mapNotNull { getMediaItemAt(it).mediaId.toLongOrNull() }

    /** Dirección canónica de un salto por índice (incluye el wrap última→primera al auto-avanzar). */
    private fun forwardFrom(old: Int, new: Int, size: Int): Boolean = when {
        size <= 1 -> true
        old == size - 1 && new == 0 -> true
        old == 0 && new == size - 1 -> false
        else -> new >= old
    }

    /** Ticker de posición: solo corre mientras suena (nada de polling en reposo); en el hilo principal. */
    private fun syncTicker(playing: Boolean) {
        positionJob?.cancel()
        if (!playing) return
        positionJob = mainScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(POSITION_TICK_MS)
                val c = controller ?: break
                _state.update {
                    it.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0L),
                        durationMs = c.duration.knownOrZero(),
                    )
                }
            }
        }
    }

    private fun runOrQueue(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else {
            pendingAction = action
            connect()
        }
    }

    private fun Long.knownOrZero(): Long = if (this == C.TIME_UNSET) 0L else this.coerceAtLeast(0L)

    private fun Int.toRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }

    private fun RepeatMode.toMedia3(): Int = when (this) {
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    }

    private fun Song.toMediaItem(manual: Boolean = false): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString()) // el _ID de MediaStore: clave para referenciar (Room, luego)
            .setUri(contentUri) // content URI, nunca ruta (scoped storage)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artworkUri) // la notificación y la UI cargan la carátula desde aquí
                    // Marca de "añadido a mano": viaja con el ítem por el MediaController y deja que la
                    // UI de cola lo agrupe aparte. No altera la reproducción ni la cola real.
                    .apply { if (manual) setExtras(Bundle().apply { putBoolean(EXTRA_MANUAL_QUEUE, true) }) }
                    .build(),
            )
            .build()

    private companion object {
        const val POSITION_TICK_MS = 500L
        const val EXTRA_MANUAL_QUEUE = "com.luis.marlune.MANUAL_QUEUE"
        const val SLEEP_FADE_STEP_MS = 40L // 12 pasos ≈ 480 ms de fade-out
    }
}
