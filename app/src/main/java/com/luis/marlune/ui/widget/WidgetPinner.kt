package com.luis.marlune.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

/**
 * Ancla el widget de Marlune a la pantalla de inicio sin que el usuario tenga que buscarlo en el cajón
 * de widgets (escondido en MIUI). Usa `requestPinAppWidget`: el sistema muestra su propio diálogo de
 * confirmación. No hay permiso ni toggle que "active/desactive" widgets; el usuario los añade y quita
 * desde el launcher.
 */
object WidgetPinner {

    /** `true` si el launcher soporta anclar widgets (API 26+ y launcher compatible). */
    fun isSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    }

    /**
     * `true` si ya hay al menos una instancia del widget anclada. Se consulta EN EL MOMENTO del clic
     * (no cachear): el usuario puede añadir o quitar el widget desde el launcher con la app abierta.
     */
    fun isPinned(context: Context): Boolean {
        val provider = ComponentName(context, MarluneWidgetReceiver::class.java)
        return AppWidgetManager.getInstance(context).getAppWidgetIds(provider).isNotEmpty()
    }

    /**
     * Pide anclar el widget. Devuelve `false` si no está soportado (SDK < 26 o launcher incompatible),
     * en cuyo caso conviene mostrar la pista manual en vez de un botón que no hace nada.
     */
    fun requestPin(context: Context): Boolean {
        if (!isSupported(context)) return false
        val provider = ComponentName(context, MarluneWidgetReceiver::class.java)
        return AppWidgetManager.getInstance(context).requestPinAppWidget(provider, null, null)
    }
}
