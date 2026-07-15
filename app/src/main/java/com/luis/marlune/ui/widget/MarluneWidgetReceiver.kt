package com.luis.marlune.ui.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Receiver del widget de Marlune (registrado en el manifest). Glance enruta aquí las actualizaciones
 * del AppWidget y delega la UI en [MarluneWidget].
 */
class MarluneWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MarluneWidget()
}
