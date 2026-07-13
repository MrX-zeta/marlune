package com.luis.marlune.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens de marca que Material 3 no modela de forma directa (tercer nivel de texto,
 * superficie elevada, divisor, variantes de acento y marea).
 *
 * `accent` es el ÚNICO campo que muta con el color dinámico; el resto es escala neutra
 * fija para garantizar contraste. Se anima aguas arriba en [MarluneTheme].
 */
@Immutable
data class MarluneColors(
    val accent: Color,
    val accentVivid: Color,
    val accentMuted: Color,
    val marea: Color,
    val surfaceElevated: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
)

/** Paleta base de Marlune con el acento de marca como valor inicial. */
val DefaultMarluneColors = MarluneColors(
    accent = MarluneAccent,
    accentVivid = MarluneAccentVivid,
    accentMuted = MarluneAccentMuted,
    marea = MarluneMarea,
    surfaceElevated = MarluneSurfaceElevated,
    divider = MarluneDivider,
    textPrimary = MarluneTextPrimary,
    textSecondary = MarluneTextSecondary,
    textTertiary = MarluneTextTertiary,
)

/**
 * Acceso a los tokens de marca desde cualquier composable.
 * Uso: `MarluneTheme.colors.textTertiary`.
 */
val LocalMarluneColors = staticCompositionLocalOf { DefaultMarluneColors }
