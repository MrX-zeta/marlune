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
 * TODO se dibuja en el MISMO lienzo y sistema de coordenadas. Se reserva un padding interno igual al
 * radio del playhead + su halo a izquierda y derecha, y la amplitud de la onda se clampa a la altura
 * disponible, de modo que —sumada al grosor del trazo y al strokeCap redondeado— NUNCA se salga de la
 * caja. Los progresos 0 y 1 quedan dentro del área visible (el playhead no se corta en los extremos).
 *
 * Debe llamarse con el ancho REAL disponible en px (no un ancho fijo que luego se escale: el escalado
 * deforma la onda). Quien llama recuerda el resultado con `remember(fraction, wPx)`.
 */
internal object WidgetTide {

    private const val TRACK = 0xFF3A3357.toInt()
    private const val WAVE = 0xFF6FD8C6.toInt()
    private const val PLAYHEAD = 0xFFA99BFF.toInt()

    /**
     * @param widthPx ancho REAL del lienzo en píxeles.
     * @param heightPx alto del lienzo en píxeles.
     * @param fraction progreso reproducido en 0f..1f.
     */
    fun render(widthPx: Int, heightPx: Int, fraction: Float): Bitmap {
        val w = widthPx.coerceAtLeast(2)
        val h = heightPx.coerceAtLeast(2)
        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
        val midY = h / 2f

        // Grosores/radios derivados del alto, todos acotados para no exceder la media altura.
        val stroke = (h * 0.11f).coerceIn(2f, h / 5f)
        val playheadR = (h * 0.18f).coerceIn(stroke, midY - 1f)
        val halo = (playheadR * 1.5f).coerceAtMost(midY - 1f)
        val pad = halo + 1f // padding izq/der = radio del playhead + halo
        val left = pad
        val right = (w - pad).coerceAtLeast(left + 1f)
        val usableW = right - left

        // Amplitud clampeada: onda + media línea del trazo NUNCA pasa de la mitad de la altura.
        val maxAmp = (midY - stroke / 2f - 1f).coerceAtLeast(0f)
        val amplitude = (h * 0.14f).coerceAtMost(maxAmp)

        val f = fraction.coerceIn(0f, 1f)
        val playedX = left + usableW * f

        // Track pendiente (línea plana) de left..right.
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TRACK
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(left, midY, right, midY, trackPaint)

        // Onda reproducida left..playedX.
        if (playedX > left + 0.5f) {
            val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = WAVE
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val wavelength = (h * 2.6f).coerceAtLeast(6f)
            val path = Path()
            path.moveTo(left, midY)
            var x = left
            while (x < playedX) {
                val y = midY + amplitude * sin(((x - left) / wavelength) * 2.0 * Math.PI).toFloat()
                path.lineTo(x, y)
                x += 1.5f
            }
            val yEnd = midY + amplitude * sin(((playedX - left) / wavelength) * 2.0 * Math.PI).toFloat()
            path.lineTo(playedX, yEnd)
            canvas.drawPath(path, wavePaint)
        }

        // Playhead SIEMPRE dentro del área visible (incluye 0 y 1).
        val cx = playedX.coerceIn(left, right)
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PLAYHEAD; alpha = 64 }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PLAYHEAD }
        canvas.drawCircle(cx, midY, halo, haloPaint)
        canvas.drawCircle(cx, midY, playheadR, dotPaint)
        return bitmap
    }
}
