package com.luis.marlune.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme

/** Acción de un menú contextual: etiqueta (recurso) + callback. */
data class ContextMenuItem(
    val labelRes: Int,
    val onClick: () -> Unit,
)

private const val EnterMillis = 120
private const val ExitMillis = 90
private const val MenuInitialScale = 0.92f

/**
 * Menú contextual anclado a la esquina superior derecha de su ancla (p. ej. el botón de
 * "3 puntos"). Entra con el patrón Material correcto: escala 0.92 → 1 + fade **desde la
 * esquina** (`transformOrigin` arriba-derecha). Con movimiento reducido aparece al instante.
 *
 * Colócalo dentro del `Box` que envuelve el botón ancla.
 */
@Composable
fun ContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<ContextMenuItem>,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = expanded

    if (visibleState.currentState || visibleState.targetState) {
        val density = LocalDensity.current
        val yOffset = with(density) { 44.dp.roundToPx() }

        Popup(
            alignment = Alignment.TopEnd,
            offset = IntOffset(x = 0, y = yOffset),
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true),
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(tween(EnterMillis)) + scaleIn(
                    animationSpec = tween(EnterMillis),
                    initialScale = if (reducedMotion) 1f else MenuInitialScale,
                    transformOrigin = TransformOrigin(1f, 0f), // esquina superior derecha
                ),
                exit = fadeOut(tween(ExitMillis)) + scaleOut(
                    animationSpec = tween(ExitMillis),
                    targetScale = MenuInitialScale,
                    transformOrigin = TransformOrigin(1f, 0f),
                ),
            ) {
                Surface(
                    modifier = modifier.wrapContentSize(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = MarluneTheme.colors.surfaceElevated,
                    shadowElevation = 8.dp,
                ) {
                    Column(modifier = Modifier.width(220.dp)) {
                        items.forEach { item ->
                            Text(
                                text = stringResource(item.labelRes),
                                style = MarluneTheme.typography.bodyLarge,
                                color = MarluneTheme.colors.textPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        item.onClick()
                                        onDismissRequest()
                                    }
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
