package com.luis.marlune.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens de marca que Material 3 no modela de forma directa (tercer nivel de texto,
 * superficie elevada, divisor, variantes de acento y marea).
 *
 * `accent` es el ÚNICO campo que muta con el color dinámico y por eso está respaldado por
 * `mutableStateOf`: la INSTANCIA provista por [MarluneTheme] es estable y única, y al animar el
 * acento solo recomponen los composables que LEEN `accent` (invalidación fina del snapshot), no el
 * árbol entero — que es lo que pasaba cuando cada frame de la animación creaba un MarluneColors
 * nuevo a través del `staticCompositionLocalOf` (recomposición global 29 veces por cambio de
 * pista: el origen del jank del slide). El resto es escala neutra fija para garantizar contraste.
 */
@Stable
class MarluneColors(
    accent: Color = MarluneAccent,
    val accentVivid: Color = MarluneAccentVivid,
    val accentMuted: Color = MarluneAccentMuted,
    val marea: Color = MarluneMarea,
    val surfaceElevated: Color = MarluneSurfaceElevated,
    val divider: Color = MarluneDivider,
    val textPrimary: Color = MarluneTextPrimary,
    val textSecondary: Color = MarluneTextSecondary,
    val textTertiary: Color = MarluneTextTertiary,
) {
    /** Acento dinámico (animado aguas arriba en [MarluneTheme]); solo sus lectores recomponen. */
    var accent: Color by mutableStateOf(accent)
        internal set
}

/**
 * Acceso a los tokens de marca desde cualquier composable.
 * Uso: `MarluneTheme.colors.textTertiary`.
 */
val LocalMarluneColors = staticCompositionLocalOf { MarluneColors() }
