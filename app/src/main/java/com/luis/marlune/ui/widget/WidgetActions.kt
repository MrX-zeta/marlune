package com.luis.marlune.ui.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.luis.marlune.MainActivity
import com.luis.marlune.MarluneApplication

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
        WidgetPlaybackBus.playPause()
        WidgetPlaybackBus.optimisticTogglePlaying() // icono al instante
        MarluneWidget().update(context, glanceId)
    }
}

class WidgetNextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetPlaybackBus.next()
        MarluneWidget().update(context, glanceId)
    }
}

class WidgetPreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetPlaybackBus.previous()
        MarluneWidget().update(context, glanceId)
    }
}

class WidgetShuffleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetPlaybackBus.toggleShuffle()
        MarluneWidget().update(context, glanceId)
    }
}

/** Alterna el "me gusta" de la pista ACTUAL (mediaId del estado publicado). Persiste en Room. */
class WidgetFavoriteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = WidgetPlaybackBus.state.value.mediaId?.toLongOrNull() ?: return
        val app = context.applicationContext as MarluneApplication
        app.container.favoritesRepository.toggle(id)
        MarluneWidget().update(context, glanceId)
    }
}
