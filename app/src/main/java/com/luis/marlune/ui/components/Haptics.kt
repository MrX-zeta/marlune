package com.luis.marlune.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Feedback háptico ligero (`CLOCK_TICK`) consolidado en un único punto.
 *
 * Úsalo solo para acciones puntuales y premium —play/pausa y cambio de pista—, nunca en scroll
 * ni en gestos de alta frecuencia. Devuelve una lambda estable que puede llamarse desde cualquier
 * manejador de click.
 */
@Composable
fun rememberHapticTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    }
}
