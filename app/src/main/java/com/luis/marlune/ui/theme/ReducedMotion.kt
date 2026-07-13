package com.luis.marlune.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * `true` cuando el usuario ha desactivado las animaciones del sistema
 * (Ajustes → Opciones de desarrollador / Accesibilidad → escala de animación = 0).
 *
 * Equivalente Android de `prefers-reduced-motion`. Las animaciones NO esenciales
 * (staggers, marea, crossfades) deben acortarse a instantáneo cuando es `true`;
 * el feedback esencial (press) puede mantenerse mínimo.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Lee la preferencia de movimiento reducido del sistema una sola vez por composición. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
