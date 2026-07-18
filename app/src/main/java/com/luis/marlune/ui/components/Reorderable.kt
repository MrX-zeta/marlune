package com.luis.marlune.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.luis.marlune.ui.theme.LocalReducedMotion
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val DragScale = 1.03f
private val DragElevation = 8.dp

/**
 * Reordenado por arrastre (mantener pulsado) REUTILIZABLE, sobre la librería estándar
 * [sh.calvin.reorderable] (auto-scroll bidireccional y exclusión de ítems no arrastrables incluidos).
 * Se usa igual en el detalle de lista y en la hoja de cola; el gesto se siente idéntico: la fila se
 * eleva (escala + sombra), las demás se acomodan, tick háptico al empezar/soltar y respeto de
 * [LocalReducedMotion].
 *
 * [onMove] reordena la lista LOCAL por CLAVE (de → a) de forma optimista mientras se arrastra; el
 * destino no reordenable (p. ej. la cabecera) simplemente no aparece como clave y se ignora.
 * [scrollThresholdPadding] permite que el auto-scroll respete un inset inferior (bottom bar), para que
 * la fila no quede atrapada tras la barra.
 */
@Composable
fun rememberMarluneReorderState(
    listState: LazyListState,
    scrollThresholdPadding: PaddingValues = PaddingValues(0.dp),
    onMove: (fromKey: Any, toKey: Any) -> Unit,
): ReorderableLazyListState =
    rememberReorderableLazyListState(listState, scrollThresholdPadding = scrollThresholdPadding) { from, to ->
        onMove(from.key, to.key)
    }

/**
 * Fila reordenable: envuelve el contenido con la elevación/escala del arrastre y el asa de press largo.
 * [onStart]/[onStop] notifican inicio/fin (p. ej. marcar "arrastrando" y persistir el nuevo orden).
 */
@Composable
fun LazyItemScope.MarluneReorderableItem(
    state: ReorderableLazyListState,
    key: Any,
    onStart: () -> Unit = {},
    onStop: () -> Unit = {},
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    val haptic = rememberHapticTick()
    ReorderableItem(state, key = key) { isDragging ->
        val scale by animateFloatAsState(
            targetValue = if (isDragging) DragScale else 1f,
            animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
            label = "reorderScale",
        )
        val elevation by animateDpAsState(
            targetValue = if (isDragging) DragElevation else 0.dp,
            animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
            label = "reorderElevation",
        )
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = elevation.toPx()
                }
                .longPressDraggableHandle(
                    onDragStarted = { onStart(); haptic() },
                    onDragStopped = { onStop(); haptic() },
                ),
        ) {
            content(isDragging)
        }
    }
}
