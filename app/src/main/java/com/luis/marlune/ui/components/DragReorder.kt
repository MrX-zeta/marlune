package com.luis.marlune.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.luis.marlune.ui.theme.LocalReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val DragScale = 1.03f
private val DragElevation = 8.dp

/**
 * Reordenado por arrastre (mantener pulsado) REUTILIZABLE: la fila se eleva y sigue al dedo, las demás
 * se acomodan con `animateItem` y al soltar asienta con spring sin rebote (≤300 ms); tick háptico al
 * empezar y al soltar; respeta el movimiento reducido. Se usa igual en el detalle de lista (persiste en
 * Room) y en la hoja de cola (mueve la reproducción con `moveMediaItem`); el gesto se siente idéntico.
 */
class DragReorderState internal constructor(
    internal val listState: LazyListState,
    internal val reducedMotion: Boolean,
    internal val haptic: () -> Unit,
    internal val scope: CoroutineScope,
) {
    var draggedKey by mutableStateOf<Any?>(null)
        internal set
    internal var offsetY by mutableFloatStateOf(0f)
    internal var startIndex = -1

    fun isDragging(key: Any): Boolean = draggedKey == key
}

@Composable
fun rememberDragReorderState(listState: LazyListState): DragReorderState {
    val reduced = LocalReducedMotion.current
    val haptic = rememberHapticTick()
    val scope = rememberCoroutineScope()
    return remember(listState) { DragReorderState(listState, reduced, haptic, scope) }
}

internal fun <T> DragReorderState.startDrag(key: Any, items: SnapshotStateList<T>, keyOf: (T) -> Any) {
    draggedKey = key
    offsetY = 0f
    startIndex = items.indexOfFirst { keyOf(it) == key }
    haptic()
}

/** Sigue el dedo y, al cruzar otra fila del MISMO grupo, la intercambia en [items] compensando el desfase. */
internal fun <T> DragReorderState.onDrag(
    deltaY: Float,
    items: SnapshotStateList<T>,
    keyOf: (T) -> Any,
    sameGroup: (T, T) -> Boolean,
) {
    offsetY += deltaY
    val key = draggedKey ?: return
    val fromInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
    val dragged = items.firstOrNull { keyOf(it) == key } ?: return
    val center = fromInfo.offset + fromInfo.size / 2 + offsetY
    val targetInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
        info.key != key &&
            items.firstOrNull { keyOf(it) == info.key }?.let { sameGroup(dragged, it) } == true &&
            center.toInt() in info.offset until (info.offset + info.size)
    } ?: return
    val from = items.indexOfFirst { keyOf(it) == key }
    val to = items.indexOfFirst { keyOf(it) == targetInfo.key }
    if (from in items.indices && to in items.indices && from != to) {
        items.add(to, items.removeAt(from))
        offsetY += fromInfo.offset - targetInfo.offset
    }
}

/** Al soltar: persiste vía [onSettle] (clave, índice inicial, índice final), tick háptico y asienta con spring. */
internal fun <T> DragReorderState.endDrag(
    items: SnapshotStateList<T>,
    keyOf: (T) -> Any,
    onSettle: (key: Any, from: Int, to: Int) -> Unit,
) {
    val key = draggedKey ?: return
    val to = items.indexOfFirst { keyOf(it) == key }
    if (startIndex >= 0 && to >= 0) onSettle(key, startIndex, to)
    haptic()
    val settleFrom = offsetY
    scope.launch {
        if (reducedMotion) {
            offsetY = 0f
        } else {
            animate(settleFrom, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) { v, _ ->
                offsetY = v
            }
        }
        draggedKey = null
    }
}

/**
 * Modificador de una fila reordenable (aplicar en `LazyItemScope`). [enabled] activa el gesto; [sameGroup]
 * restringe con qué otras filas se puede intercambiar (p. ej. misma sección de la cola).
 */
@Composable
fun <T> LazyItemScope.reorderableItem(
    state: DragReorderState,
    item: T,
    items: SnapshotStateList<T>,
    keyOf: (T) -> Any,
    enabled: Boolean,
    onSettle: (key: Any, from: Int, to: Int) -> Unit,
    sameGroup: (T, T) -> Boolean = { _, _ -> true },
): Modifier {
    val key = keyOf(item)
    val isDragged = enabled && state.isDragging(key)
    return Modifier
        .then(if (isDragged) Modifier.zIndex(1f) else Modifier)
        .then(if (enabled && !isDragged && !state.reducedMotion) Modifier.animateItem() else Modifier)
        .graphicsLayer {
            if (isDragged) {
                translationY = state.offsetY
                scaleX = DragScale
                scaleY = DragScale
                shadowElevation = DragElevation.toPx()
            }
        }
        .then(
            if (enabled) {
                Modifier.pointerInput(key) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { state.startDrag(key, items, keyOf) },
                        onDrag = { change, drag -> change.consume(); state.onDrag(drag.y, items, keyOf, sameGroup) },
                        onDragEnd = { state.endDrag(items, keyOf, onSettle) },
                        onDragCancel = { state.endDrag(items, keyOf, onSettle) },
                    )
                }
            } else {
                Modifier
            },
        )
}
