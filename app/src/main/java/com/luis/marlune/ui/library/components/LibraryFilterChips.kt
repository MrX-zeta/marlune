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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luis.marlune.ui.library.LibraryFilter
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme

private const val ChipAnimMillis = 160
private val ChipHeight = 36.dp
private val ChipGap = 6.dp // hueco entre chips (compacto para que quepan los 4)
private val ChipRowSidePadding = 12.dp // respiro a los lados: "Listas"/"Canciones" no quedan pegados al borde
private val ChipInnerPadding = 6.dp // padding interno mínimo del texto dentro de su pastilla

/**
 * Chips de filtro en una fila FIJA (sin scroll horizontal): los 4 se reparten el ancho con `weight`
 * igual, así siempre caben completos —también en pantallas estrechas— sin recortar texto ni desbordar.
 * Al no ser deslizable, no atrapa el gesto: el swipe entre pantallas funciona también sobre los chips.
 *
 * Selección instantánea y robusta: cada chip pinta SU PROPIO fondo ("pill") cuando está seleccionado
 * (acento tenue, animando color/alfa por chip con `animateColorAsState`, retargetable) y el texto pasa
 * de secundario a acento. Sin medición, sin auto-scroll y sin ripple. Respeta el movimiento reducido.
 */
@Composable
fun LibraryFilterChips(
    selected: LibraryFilter,
    onSelect: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = ChipRowSidePadding),
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
    ) {
        LibraryFilter.entries.forEach { filter ->
            FilterChip(
                label = stringResource(filter.labelRes),
                selected = filter == selected,
                reducedMotion = reducedMotion,
                onClick = { onSelect(filter) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Un chip (celda de ancho igual): su fondo (pill) y el color del texto animan según [selected]. */
@Composable
private fun RowScope.FilterChip(
    label: String,
    selected: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        modifier = modifier
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
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
