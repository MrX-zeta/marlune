package com.luis.marlune.ui.library.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luis.marlune.ui.library.LibraryFilter
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme

private const val ChipAnimMillis = 160
private val ChipHeight = 36.dp
private val ChipRowStartPadding = 12.dp // margen izquierdo de la fila
private val ChipRowEndPadding = 18.dp // margen derecho algo mayor: corre los chips a la izquierda, así el
                                      // último ("Canciones") no queda pegado al borde
private val ChipInnerPadding = 10.dp // padding interno (holgado pero compacto para que quepan los 4)

/**
 * Chips de filtro en una fila FIJA (sin scroll horizontal). Cada chip se ajusta al ANCHO DE SU TEXTO
 * (+padding), sin truncar —"Canciones", el más largo, se ve entero incluso activo, con su fondo
 * abarcándolo—. El espacio sobrante se reparte como separación uniforme ENTRE chips ([Arrangement.SpaceBetween])
 * más un pequeño margen a los lados. Al no ser deslizable, el swipe entre pantallas funciona sobre los chips.
 *
 * Selección instantánea: cada chip pinta su propio fondo ("pill", acento tenue) cuando está seleccionado
 * y el texto pasa de secundario a acento, animando por chip ([animateColorAsState], retargetable). Sin
 * medición, sin auto-scroll y sin ripple. Respeta el movimiento reducido.
 */
@Composable
fun LibraryFilterChips(
    selected: LibraryFilter,
    onSelect: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = ChipRowStartPadding, end = ChipRowEndPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryFilter.entries.forEach { filter ->
            FilterChip(
                label = stringResource(filter.labelRes),
                selected = filter == selected,
                reducedMotion = reducedMotion,
                onClick = { onSelect(filter) },
            )
        }
    }
}

/** Un chip a ancho de contenido: su fondo (pill) y el color del texto animan según [selected]. */
@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
) {
    val pill = MarluneTheme.colors.accentMuted
    val bgColor by animateColorAsState(
        targetValue = if (selected) pill else pill.copy(alpha = 0f), // solo alfa: hue estable, fade limpio
        animationSpec = if (reducedMotion) snap() else tween(ChipAnimMillis),
        label = "chipBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MarluneTheme.colors.accent else MarluneTheme.colors.textSecondary,
        animationSpec = if (reducedMotion) snap() else tween(ChipAnimMillis),
        label = "chipText",
    )

    Box(
        modifier = Modifier
            .height(ChipHeight)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // sin ripple, como estaba
            ) { onClick() }
            .padding(horizontal = ChipInnerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MarluneTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
            softWrap = false, // el objetivo es que quepan holgados; si algo no cupiera, se detecta en pruebas
        )
    }
}
