package com.luis.marlune.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import kotlin.math.sin

/**
 * La MAREA del widget, pintada a mano en un [Bitmap] (Glance no tiene onda ni progreso). Réplica en
 * miniatura de la marea de la app: la parte reproducida es una onda de baja amplitud en aqua
 * (#6FD8C6), el resto una línea plana tenue (#3A3357), y el playhead un punto vivo (#A99BFF) en el
 * límite.
 *
 * Se dibuja al ancho REAL en píxeles y se muestra con `ImageProvider(bitmap)`. Quien llama debe
 * `remember(fraccion)` el resultado para regenerarlo SOLO al cambiar el progreso (algunos launchers
 * MIUI cachean el ImageView y no repintan si el bitmap no cambia de identidad).
 */
internal object WidgetTide {

    private const val TRACK = 0xFF3A3357.toInt()
    private const val WAVE = 0xFF6FD8C6.toInt()
    private const val PLAYHEAD = 0xFFA99BFF.toInt()

    /**
     * @param widthPx ancho del lienzo en píxeles (idealmente el ancho real del widget).
     * @param heightPx alto del lienzo en píxeles.
     * @param fraction progreso reproducido en 0f..1f.
     */
    fun render(widthPx: Int, heightPx: Int, fraction: Float): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
        val midY = h / 2f
        val stroke = (h * 0.14f).coerceAtLeast(2f)
        val playedX = (w * fraction.coerceIn(0f, 1f))

        // Línea plana de fondo (todo el ancho): el "resto sin reproducir".
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TRACK
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(stroke, midY, w - stroke, midY, trackPaint)

        if (playedX > stroke) {
            // Onda de baja amplitud sobre la parte reproducida.
            val amplitude = (h * 0.24f)
            val wavelength = h * 3.2f // proporción calmada, ~3 alturas por ciclo
            val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = WAVE
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
            var x = stroke
            path.moveTo(x, midY)
            while (x <= playedX) {
                val y = midY + amplitude * sin((x / wavelength) * 2f * Math.PI).toFloat()
                path.lineTo(x, y)
                x += 2f
            }
            canvas.drawPath(path, wavePaint)

            // Playhead: punto vivo en el límite reproducido, con un halo sutil para leerse sobre la onda.
            val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PLAYHEAD; alpha = 60 }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PLAYHEAD }
            val cx = playedX.coerceIn(stroke, w - stroke)
            canvas.drawCircle(cx, midY, stroke * 2.2f, haloPaint)
            canvas.drawCircle(cx, midY, stroke * 1.15f, dotPaint)
        }
        return bitmap
    }
}
