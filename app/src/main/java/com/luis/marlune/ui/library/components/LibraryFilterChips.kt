package com.luis.marlune.ui.library.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luis.marlune.ui.library.LibraryFilter
import com.luis.marlune.ui.theme.LocalReducedMotion
import com.luis.marlune.ui.theme.MarluneTheme

private const val ChipAnimMillis = 160
private val ChipHeight = 36.dp
private val ChipGap = 4.dp // hueco entre chips (ajustado para que quepan mejor)
private val ChipRowSidePadding = 16.dp // respiro en ambos extremos: el 1.º/último chip no se recorta

/**
 * Chips de filtro con selección instantánea y robusta.
 *
 * Sin medición de posiciones ni indicador global: cada chip pinta SU PROPIO fondo ("pill") cuando
 * está seleccionado y lo apaga cuando no, animando color/alfa por chip con `animateColorAsState`
 * (retargetable), más el texto de secundario → acento. Así, en toques rápidos, cada chip refleja su
 * estado al instante y el anterior se apaga; nada "persigue" ni se encola, y no hay bucle
 * scroll↔medición↔recomposición.
 *
 * Los chips viven en un `LazyRow`; para traer el seleccionado a la vista se usa
 * `animateScrollToItem(selectedIndex)` en un efecto keyed SOLO en el índice (nunca en un valor
 * medido). La etiqueta va en una sola línea y el chip se dimensiona a su contenido. Sin ripple.
 * Respeta el movimiento reducido (todo instantáneo).
 */
@Composable
fun LibraryFilterChips(
    selected: LibraryFilter,
    onSelect: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val filters = LibraryFilter.entries
    val selectedIndex = filters.indexOf(selected)

    val listState = rememberLazyListState()
    // Scroll-into-view por ÍNDICE (desacoplado de cualquier medición): sin bucle de realimentación.
    LaunchedEffect(selectedIndex) {
        if (reducedMotion) listState.scrollToItem(selectedIndex) else listState.animateScrollToItem(selectedIndex)
    }

    // Fila FIJA (userScrollEnabled = false): al no consumir el arrastre horizontal, el swipe entre
    // pestañas (HorizontalPager) gana el gesto también cuando el dedo pasa sobre los chips. El
    // contentPadding lateral + la separación ajustada evitan que "Listas"/"Canciones" se recorten.
    LazyRow(
        state = listState,
        modifier = modifier,
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
        contentPadding = PaddingValues(horizontal = ChipRowSidePadding),
    ) {
        items(filters, key = { it }, contentType = { "filterChip" }) { filter ->
            FilterChip(
                label = stringResource(filter.labelRes),
                selected = filter == selected,
                reducedMotion = reducedMotion,
                onClick = { onSelect(filter) },
            )
        }
    }
}

/** Un chip: su fondo (pill) y el color del texto animan por sí mismos según [selected]. Sin medir nada. */
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
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MarluneTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}
