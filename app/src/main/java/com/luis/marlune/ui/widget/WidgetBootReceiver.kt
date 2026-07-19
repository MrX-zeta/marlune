package com.luis.marlune.ui.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Repinta los widgets al COMPLETAR el arranque del sistema. Sin esto, el launcher se queda con su
 * placeholder de "cargando" hasta que algo despierte el proceso (el widget nunca llega a componer):
 * tras reiniciar, el widget parecía colgado. Al componer, [MarluneWidget] pinta la última pista de
 * la sesión guardada (en pausa, con play que reanuda) o el estado vacío.
 *
 * Nota MIUI/HyperOS: recibir BOOT_COMPLETED requiere el permiso de "Autoinicio" concedido a la app;
 * sin él, el sistema retiene el broadcast (límite del fabricante, no nuestro).
 */
class WidgetBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync() // updateAll suspende; mantiene vivo el receiver mientras repinta
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { MarluneWidget().updateAll(context.applicationContext) }
            pending.finish()
        }
    }
}
