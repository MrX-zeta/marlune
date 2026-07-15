package com.luis.marlune.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import kotlin.math.sin

/**
 * La MAREA del widget, pintada a mano en un [Bitmap] (Glance no tiene onda ni progreso). Es una FOTO
 * FIJA de la marea de Now Playing ([com.luis.marlune.ui.components.Marea]): usa EXACTAMENTE sus mismas
 * constantes (longitud de onda, amplitud, trazos, playhead) convertidas a px con la densidad real, y su
 * misma fórmula de onda (fase 0: sin animación). Parte reproducida = onda aqua; pendiente = línea plana
 * #3A3357 al 30 % de alpha (el mismo track de la app); playhead = halo + anillo de fondo + punto.
 */
internal object WidgetTide {

    // Mismas constantes que ui/components/Marea.kt (en dp; se pasan a px con la densidad).
    private const val AMPLITUDE_DP = 2f
    private const val WAVELENGTH_DP = 22f
    private const val WAVE_STROKE_DP = 2f
    private const val TRACK_STROKE_DP = 1.5f
    private const val PLAYHEAD_RADIUS_DP = 4.5f
    private const val PLAYHEAD_RING_DP = 6.5f
    private const val PLAYHEAD_HALO_DP = 10f
    private const val STEP_DP = 2f

    private const val WAVE = 0xFF6FD8C6.toInt()
    private const val PLAYHEAD = 0xFFA99BFF.toInt()
    private const val TRACK = 0xFF3A3357.toInt()
    private const val BACKGROUND = 0xFF0A0910.toInt()
    private const val TRACK_ALPHA = 77 // 0.30 * 255 (mismo alpha del track de la app)
    private const val HALO_ALPHA = 56 // 0.22 * 255

    /**
     * @param widthPx ancho REAL del lienzo en píxeles.
     * @param heightPx alto del lienzo en píxeles (usar ~24 dp en px, como la marea de la app).
     * @param density densidad real del dispositivo, para convertir las constantes dp a px.
     * @param fraction progreso reproducido en 0f..1f.
     */
    fun render(widthPx: Int, heightPx: Int, density: Float, fraction: Float): Bitmap {
        val w = widthPx.coerceAtLeast(2)
        val h = heightPx.coerceAtLeast(2)
        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
        val centerY = h / 2f

        val amplitudePx = AMPLITUDE_DP * density
        val wavelengthPx = WAVELENGTH_DP * density
        val angularStep = (2.0 * Math.PI / wavelengthPx).toFloat()
        val haloR = PLAYHEAD_HALO_DP * density
        val pad = haloR // reserva para que el halo no se recorte en los extremos (0 % y 100 %)
        val left = pad
        val right = (w - pad).coerceAtLeast(left + 1f)
        val usableW = right - left
        val f = fraction.coerceIn(0f, 1f)
        val playedX = left + usableW * f

        fun waveY(x: Float): Float = centerY + amplitudePx * sin((x - left) * angularStep)

        // 1) Pendiente: línea plana #3A3357 al 30 % de alpha, de playedX a right (igual que la app).
        if (playedX < right) {
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TRACK
                alpha = TRACK_ALPHA
                strokeWidth = TRACK_STROKE_DP * density
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(playedX, centerY, right, centerY, trackPaint)
        }

        // 2) Reproducido: onda sinusoidal aqua de left a playedX (misma fórmula que la app, fase 0).
        if (playedX > left) {
            val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = WAVE
                style = Paint.Style.STROKE
                strokeWidth = WAVE_STROKE_DP * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val step = (STEP_DP * density).coerceAtLeast(1f)
            val path = Path()
            path.moveTo(left, waveY(left))
            var x = left + step
            while (x < playedX) {
                path.lineTo(x, waveY(x))
                x += step
            }
            path.lineTo(playedX, waveY(playedX))
            canvas.drawPath(path, wavePaint)
        }

        // 3) Playhead en la frontera: halo (alpha) + anillo de separación (fondo) + punto sólido.
        val headY = waveY(playedX)
        canvas.drawCircle(playedX, headY, haloR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PLAYHEAD; alpha = HALO_ALPHA })
        canvas.drawCircle(playedX, headY, PLAYHEAD_RING_DP * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BACKGROUND })
        canvas.drawCircle(playedX, headY, PLAYHEAD_RADIUS_DP * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PLAYHEAD })
        return bitmap
    }
}
