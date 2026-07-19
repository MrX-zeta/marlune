package com.luis.marlune.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

/**
 * Esquema oscuro de Marlune, construido íntegramente desde los tokens de [Color.kt].
 * Es la única fuente de color: no se usan `dynamicDarkColorScheme` ni tema claro, porque
 * el color dinámico mueve solo el acento (ver [MarluneAccentController]) y la app es dark-only.
 *
 * `primary` es el acento y se sobrescribe con el valor animado en tiempo de ejecución.
 */
// Margen para que el salto del esquema M3 (recomposición global única) caiga FUERA de la ventana
// del slide de cambio de pista (~600 ms en total).
private const val SchemeAccentDeferMillis = 600L

private val MarluneDarkColorScheme: ColorScheme = darkColorScheme(
    primary = MarluneAccent,
    onPrimary = MarluneBackground,
    primaryContainer = MarluneAccentMuted,
    onPrimaryContainer = MarluneTextPrimary,
    inversePrimary = MarluneAccentVivid,

    secondary = MarluneMarea,
    onSecondary = MarluneBackground,
    secondaryContainer = MarluneSurfaceElevated,
    onSecondaryContainer = MarluneTextPrimary,

    tertiary = MarluneAccentVivid,
    onTertiary = MarluneBackground,
    tertiaryContainer = MarluneAccentMuted,
    onTertiaryContainer = MarluneTextPrimary,

    background = MarluneBackground,
    onBackground = MarluneTextPrimary,

    surface = MarluneSurface,
    onSurface = MarluneTextPrimary,
    surfaceVariant = MarluneSurfaceElevated,
    onSurfaceVariant = MarluneTextSecondary, // subtítulos/artista con contraste correcto
    surfaceContainerLowest = MarluneBackground,
    surfaceContainerLow = MarluneSurface,
    surfaceContainer = MarluneSurfaceElevated,
    surfaceContainerHigh = MarluneSurfaceElevated,
    surfaceContainerHighest = MarluneSurfaceElevated,

    outline = MarluneDivider,
    outlineVariant = MarluneDivider,

    scrim = Color.Black,
    error = Color(0xFFF2B8B5),
    onError = MarluneBackground,
)

/**
 * Tema raíz de Marlune.
 *
 * Provee:
 *  - `MaterialTheme` con el esquema oscuro y la tipografía de marca.
 *  - [LocalMarluneColors] con los tokens extra (texto terciario, elevada, marea…).
 *  - [LocalMarluneAccentController] para que las pantallas empujen el acento de la carátula.
 *  - [LocalReducedMotion] leído del sistema.
 *
 * El acento se anima con `animateColorAsState` (240 ms) y fluye por `MarluneTheme.colors.accent`
 * con invalidación FINA (solo recomponen sus lectores). El `primary` de Material salta al objetivo
 * sin tween (una actualización por cambio, no por frame). Con movimiento reducido el cambio es
 * instantáneo. Fondos y texto permanecen fijos en la escala neutra.
 */
@Composable
fun MarluneTheme(
    accentController: MarluneAccentController = rememberMarluneAccentController(),
    content: @Composable () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()

    val animatedAccent by animateColorAsState(
        targetValue = accentController.target,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 240),
        label = "accent",
    )

    // El esquema de Material se actualiza UNA vez por cambio de acento objetivo (no por frame de la
    // animación): en M3 moderno ColorScheme es inmutable y viaja por un local ESTÁTICO, así que cada
    // valor nuevo recompone el árbol entero — hacerlo 29 veces por animación era el origen del jank
    // del slide. Los componentes M3 que beben de `primary` (pestaña activa, chips) saltan al acento
    // nuevo sin tween; el fundido suave vive en MarluneTheme.colors.accent (lectores finos).
    // Además, ese salto único se APLAZA a REPOSO: Palette termina con el slide aún corriendo, y la
    // recomposición global aterrizaba dentro de la ventana de la animación (un frame muerto por
    // swipe). Encadenar cambios reinicia el margen → una sola actualización al final de la racha.
    var schemeAccent by remember { mutableStateOf(accentController.target) }
    LaunchedEffect(accentController.target) {
        if (accentController.target != schemeAccent) {
            delay(SchemeAccentDeferMillis)
            schemeAccent = accentController.target
        }
    }
    val colorScheme = remember(schemeAccent) {
        MarluneDarkColorScheme.copy(primary = schemeAccent)
    }
    // Instancia ÚNICA y estable: la animación escribe `accent` (mutableStateOf interno) y solo
    // recomponen sus lectores. Nunca se re-crea el objeto (eso invalidaría el árbol entero).
    val marluneColors = remember { MarluneColors() }
    marluneColors.accent = animatedAccent

    CompositionLocalProvider(
        LocalMarluneColors provides marluneColors,
        LocalMarluneAccentController provides accentController,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MarluneTypography,
            content = content,
        )
    }
}

/** Accesos de conveniencia: `MarluneTheme.colors.textTertiary`, etc. */
object MarluneTheme {
    val colors: MarluneColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMarluneColors.current

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography
}
