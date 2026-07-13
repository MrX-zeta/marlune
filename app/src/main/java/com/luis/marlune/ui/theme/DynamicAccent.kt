package com.luis.marlune.ui.theme

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extrae el color dominante de una carátula y lo publica como acento.
 *
 * Regla del sistema de diseño: el color dinámico mueve SOLO el acento
 * (play, toggles activos, marea). Fondos y texto se quedan en la escala neutra.
 * El cambio se anima en [MarluneTheme] con `animateColorAsState` (240 ms), nunca corte seco.
 */
@Stable
class MarluneAccentController(initial: Color = MarluneAccent) {

    /** Acento objetivo. Se anima aguas arriba antes de pintarse. */
    var target: Color by mutableStateOf(initial)
        private set

    /** Extrae el acento de la carátula en un hilo de fondo y lo aplica si es válido. */
    suspend fun updateFromArtwork(bitmap: Bitmap) {
        val extracted = withContext(Dispatchers.Default) { accentFromBitmap(bitmap) }
        target = extracted ?: MarluneAccent
    }

    /** Vuelve al acento de marca (p. ej. al no haber carátula). */
    fun reset() {
        target = MarluneAccent
    }
}

/**
 * Elige un swatch expresivo de la carátula y lo normaliza para un fondo oscuro:
 * garantiza saturación y luminosidad suficientes para leerse como acento sobre `#0A0910`.
 * Devuelve `null` si Palette no encuentra nada usable (el llamador cae al acento base).
 */
private fun accentFromBitmap(bitmap: Bitmap): Color? {
    val palette = Palette.from(bitmap).clearFilters().generate()
    val swatch = palette.vibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.dominantSwatch
        ?: return null
    return normalizeForDark(Color(swatch.rgb))
}

/** Sube saturación/luminosidad mínimas para que cualquier carátula rinda como acento. */
private fun normalizeForDark(color: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.45f) // saturación
    hsl[2] = hsl[2].coerceIn(0.62f, 0.78f) // luminosidad legible sobre neutro oscuro
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * Acento estable por pista para teñir miniaturas cuando aún no hay carátula cargada, de modo
 * que la lista se pueda escanear (evita el "muro monocromo"). Reparte tonos de forma
 * determinista según la clave y los normaliza a la banda legible sobre fondo oscuro.
 *
 * Es el mismo sistema del acento dinámico: SOLO tiñe el acento asociado, nunca la escala
 * neutra. Sustituible por extracción vía Palette cuando llegue el bitmap real de la carátula.
 */
fun placeholderAccentFor(key: Long): Color {
    val hue = (((key % 12L) + 12L) % 12L) * 30f // 12 tonos repartidos en la rueda
    val hsl = floatArrayOf(hue, 0.50f, 0.68f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Controlador de acento disponible para las pantallas (p. ej. el reproductor). */
val LocalMarluneAccentController = staticCompositionLocalOf { MarluneAccentController() }

/** Crea y recuerda un controlador de acento ligado a la composición. */
@Composable
fun rememberMarluneAccentController(): MarluneAccentController =
    remember { MarluneAccentController() }
