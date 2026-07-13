package com.luis.marlune.ui.library.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luis.marlune.ui.library.LibraryFilter
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme

private const val ChipAnimMillis = 180
private val ChipHeight = 36.dp

/**
 * Chips de filtro con indicador que se desliza entre ellos.
 *
 * Al cambiar la selección, un "pill" tenue se desplaza (posición y ancho animados 180 ms) hasta
 * el chip activo, y el color del texto pasa de secundario a acento (180 ms). El indicador se
 * dibuja con `drawBehind` (solo pintura, sin relayout por frame) en lugar de animar `width`. El
 * cambio real de CONTENIDO entre categorías lo hace la pantalla con un fade rápido (150 ms), sin
 * slide —es una acción frecuente—. Respeta el movimiento reducido.
 */
@Composable
fun LibraryFilterChips(
    selected: LibraryFilter,
    onSelect: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val density = LocalDensity.current
    val filters = LibraryFilter.entries

    // Bordes (izquierda y ancho, en px) de cada chip, medidos al posicionarse.
    val lefts = remember { mutableStateListOf(*Array(filters.size) { 0f }) }
    val widths = remember { mutableStateListOf(*Array(filters.size) { 0f }) }

    val selectedIndex = filters.indexOf(selected)
    val targetLeft = lefts.getOrElse(selectedIndex) { 0f }
    val targetWidth = widths.getOrElse(selectedIndex) { 0f }

    val spec = if (reducedMotion) snap<Float>() else tween<Float>(ChipAnimMillis, easing = FastOutSlowInEasing)
    val indicatorLeft by animateFloatAsState(targetLeft, spec, label = "chipIndicatorX")
    val indicatorWidth by animateFloatAsState(targetWidth, spec, label = "chipIndicatorW")

    val indicatorColor = MarluneTheme.colors.accentMuted
    val gapPx = with(density) { 8.dp.toPx() }

    Row(
        modifier = modifier.drawBehind {
            // Pill deslizante dibujado, no medido: no dispara layout por frame.
            // El Row se ajusta a la altura del chip, así que el pill ocupa todo el alto.
            if (indicatorWidth > 0f) {
                drawRoundRect(
                    color = indicatorColor,
                    topLeft = Offset(indicatorLeft, 0f),
                    size = Size(indicatorWidth, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
        },
    ) {
        filters.forEachIndexed { index, filter ->
            val isSelected = filter == selected
            val textColor by animateColorAsState(
                targetValue = if (isSelected) MarluneTheme.colors.accent else MarluneTheme.colors.textSecondary,
                animationSpec = if (reducedMotion) snap() else tween(ChipAnimMillis),
                label = "chipTextColor",
            )

            Box(
                // Se mide el nodo externo respecto al Row (posición fiable); se descuenta
                // el hueco final para que el indicador coincida con el pill visible.
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        lefts[index] = coords.positionInParent().x
                        widths[index] = (coords.size.width.toFloat() - gapPx).coerceAtLeast(0f)
                    }
                    .padding(end = 8.dp)
                    .height(ChipHeight)
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(filter) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(filter.labelRes),
                    style = MarluneTheme.typography.labelLarge,
                    color = textColor,
                )
            }
        }
    }
}
