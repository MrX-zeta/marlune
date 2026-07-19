package com.luis.marlune.ui.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.luis.marlune.MainActivity
import com.luis.marlune.MarluneApplication
import com.luis.marlune.playback.MarluneMediaService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Extra del Intent que pide a la app abrir directamente Now Playing al lanzarse desde el widget. */
const val EXTRA_OPEN_NOW_PLAYING = "com.luis.marlune.widget.OPEN_NOW_PLAYING"

/**
 * Intent para abrir la app desde el widget. [nowPlaying] pide expandir el reproductor a pantalla
 * completa. `SINGLE_TOP | CLEAR_TOP` reutiliza la instancia existente (entrega por `onNewIntent`) en
 * vez de crear otra; `NEW_TASK` es obligatorio al lanzar desde un contexto que no es Activity.
 */
fun openAppIntent(context: Context, nowPlaying: Boolean): Intent =
    Intent(context, MainActivity::class.java).apply {
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
        if (nowPlaying) putExtra(EXTRA_OPEN_NOW_PLAYING, true)
    }

// --- Un ActionCallback DISTINTO por control ---
// Glance deduplica los PendingIntents y los parámetros/extras NO cuentan para diferenciarlos: dos
// `actionRunCallback` de la MISMA clase colapsarían en el mismo intent. Una clase por acción garantiza
// intents (y por tanto PendingIntents) distintos, así que cada botón hace algo diferente.
//
// El transporte va por el [WidgetPlaybackBus] (player del servicio, mismo proceso, en main): instantáneo,
// sin reconexión de MediaController. Tras el comando se fuerza `update` para que el widget cambie ya, sin
// esperar al evento de vuelta (el servicio, además, empujará el estado autoritativo poco después).

class WidgetPlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (!WidgetPlaybackBus.hasPlayer) {
            // Proceso/servicio muertos: REANUDAR, no resetear. Un startService plano desde aquí lo
            // BLOQUEA el sistema ("Background start not allowed"); lo permitido desde el toque de un
            // widget es un arranque FOREGROUND, y el camino canónico es el intent de BOTÓN MULTIMEDIA:
            // Media3 lo enruta a onPlaybackResumption (servicio), que devuelve la sesión guardada y
            // reproduce desde donde iba gestionando él la notificación. Sin sesión, no pasa nada.
            val resume = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(context, MarluneMediaService::class.java))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            runCatching { ContextCompat.startForegroundService(context, resume) }
            return
        }
        WidgetPlaybackBus.playPause()
        WidgetPlaybackBus.optimisticTogglePlaying() // icono al instante
        MarluneWidget().update(context, glanceId)
    }
}

class WidgetNextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (ensureRestoredQueue(context, KeyEvent.KEYCODE_MEDIA_NEXT)) WidgetPlaybackBus.next()
        MarluneWidget().update(context, glanceId)
    }
}

class WidgetPreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (ensureRestoredQueue(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)) WidgetPlaybackBus.previous()
        MarluneWidget().update(context, glanceId)
    }
}

// Techo de espera a que el servicio recién despertado restaure la sesión (biblioteca incluida).
private const val COLD_RESTORE_WAIT_MS = 8_000L

/**
 * Garantiza servicio VIVO con la cola RESTAURADA antes de aplicar un salto (siguiente/anterior) en
 * frío: despierta el servicio con el intent de botón multimedia de la propia acción (arranque
 * foreground permitido desde el widget; el comando llega con la cola aún vacía → no-op inofensivo),
 * y espera a que la restauración EN PAUSA del servicio publique la cola en el bus. El salto queda
 * entonces idéntico al caso caliente-en-pausa: cambia la pista SIN sonar; play la reproduce.
 * `false` (sin sesión guardada / sin permiso / timeout) → el llamador no hace nada, sin crashear.
 */
private suspend fun ensureRestoredQueue(context: Context, wakeKeyCode: Int): Boolean {
    if (WidgetPlaybackBus.state.value.hasItem) return true // servicio vivo y con cola: caso normal
    if (!WidgetPlaybackBus.hasPlayer) {
        val wake = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(context, MarluneMediaService::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, wakeKeyCode))
        val started = runCatching { ContextCompat.startForegroundService(context, wake) }.isSuccess
        if (!started) return false
    }
    // Espera OBSERVANDO el bus (el servicio publica al armar la cola restaurada), con techo.
    return withTimeoutOrNull(COLD_RESTORE_WAIT_MS) {
        WidgetPlaybackBus.state.first { it.hasItem }
        true
    } ?: false
}

class WidgetShuffleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetPlaybackBus.toggleShuffle()
        MarluneWidget().update(context, glanceId)
    }
}

/**
 * Alterna el "me gusta" de la pista ACTUAL. En caliente, el mediaId sale del estado publicado por el
 * servicio; en FRÍO (servicio muerto, widget pintado desde la sesión guardada), sale del propio
 * store — Room no necesita servicio, así el corazón funciona al primer toque tras un reinicio.
 */
class WidgetFavoriteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as MarluneApplication
        val id = WidgetPlaybackBus.state.value.mediaId?.toLongOrNull()
            ?: runCatching { app.container.sessionStore.session.first() }.getOrNull()
                ?.let { it.ids.getOrNull(it.index.coerceIn(0, it.ids.lastIndex)) }
            ?: return
        app.container.favoritesRepository.toggle(id)
        MarluneWidget().update(context, glanceId)
    }
}
