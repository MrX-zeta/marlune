package com.luis.marlune.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme

private val CardShape = RoundedCornerShape(20.dp)

/**
 * Superficie de tarjeta con feedback de press (escala 0.97 + state layer). Lenguaje visual común
 * para el grid de Inicio y demás tarjetas, de modo que no parezcan dos estéticas.
 *
 * Con [onClick] no nulo se comporta como tarjeta clicable estándar (tap → ripple + acción).
 * Con [onClick] nulo NO captura el tap: el state layer (ripple de presionado) lo controla quien
 * maneje [interactionSource]; así el mini-player solo lo ilumina cuando su detector unificado
 * confirma un tap, y no al deslizar.
 */
@Composable
fun PressableCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    color: Color = MarluneTheme.colors.surfaceElevated,
    shadowElevation: Dp = 0.dp,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "cardPressScale",
    )
    val scaledModifier = modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = scaledModifier,
            shape = shape,
            color = color,
            shadowElevation = shadowElevation,
            interactionSource = interactionSource,
            content = content,
        )
    } else {
        Surface(
            modifier = scaledModifier,
            shape = shape,
            color = color,
            shadowElevation = shadowElevation,
        ) {
            // El state layer se recorta a la forma de la card (Surface recorta su contenido).
            Box(Modifier.indication(interactionSource, ripple())) {
                content()
            }
        }
    }
}
