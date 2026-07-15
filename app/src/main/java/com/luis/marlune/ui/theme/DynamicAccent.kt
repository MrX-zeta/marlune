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

// Por debajo de esta saturación real, la carátula es efectivamente monocroma (gris/blanco/negro):
// su hue no significa nada, así que NO se inventa color (se cae al acento de marca).
private const val MonochromeSaturation = 0.15f
// Mezcla ligera del color extraído con la marca para que todo mantenga aire "Marlune" y no salten
// tonos chillones.
private const val BrandBlend = 0.18f

/**
 * Elige un swatch expresivo de la carátula y lo normaliza para un fondo oscuro:
 * garantiza saturación y luminosidad suficientes para leerse como acento sobre `#0A0910`.
 *
 * Usa los filtros por defecto de Palette (descartan casi-negros y casi-blancos), y SOLO devuelve un
 * color si el swatch elegido tiene saturación real: una carátula en blanco y negro o blanca da un
 * swatch gris (saturación ~0, hue basura) → `null`, para que el llamador use el acento de marca en
 * vez de forzar saturación sobre un hue sin sentido (lo que producía rojos/amarillos inventados).
 */
private fun accentFromBitmap(bitmap: Bitmap): Color? {
    val palette = Palette.from(bitmap).generate() // filtros por defecto: sin near-black/near-white
    val swatch = palette.vibrantSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.darkMutedSwatch
        ?: palette.lightMutedSwatch
        ?: palette.dominantSwatch
        ?: return null

    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(swatch.rgb, hsl)
    if (hsl[1] < MonochromeSaturation) return null // carátula monocroma → acento de marca

    return normalizeForDark(Color(swatch.rgb))
}

/**
 * Sube saturación/luminosidad mínimas para que un color con saturación REAL rinda como acento sobre
 * el neutro oscuro, y lo mezcla un poco con la marca para mantener la coherencia. NO se llama con
 * colores monocromos (esos se filtran antes, en [accentFromBitmap]).
 */
private fun normalizeForDark(color: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.45f) // saturación
    hsl[2] = hsl[2].coerceIn(0.62f, 0.78f) // luminosidad legible sobre neutro oscuro
    val normalized = ColorUtils.HSLToColor(hsl)
    return Color(ColorUtils.blendARGB(normalized, MarluneAccent.toArgb(), BrandBlend))
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
