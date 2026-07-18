package com.luis.marlune.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.luis.marlune.ui.theme.LocalReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val DragScale = 1.03f
private val DragElevation = 8.dp

// Auto-scroll al arrastrar cerca de un borde: zona sensible desde el borde y paso máximo por frame en él
// (proporcional a lo dentro de la zona que esté la fila). Así se puede llevar una canción de punta a punta
// sin soltar, como en otros reproductores.
private val AutoScrollEdge = 72.dp
private val AutoScrollMaxStep = 14.dp

/**
 * Reordenado por arrastre (mantener pulsado) REUTILIZABLE: la fila se eleva y sigue al dedo, las demás
 * se acomodan con `animateItem` y al soltar asienta con spring sin rebote (≤300 ms); tick háptico al
 * empezar y al soltar; respeta el movimiento reducido. Cerca de la cima/fondo la lista AUTO-SCROLLEA
 * mientras se mantenga ahí. Se usa igual en el detalle de lista (persiste en Room) y en la hoja de cola
 * (mueve la reproducción con `moveMediaItem`); el gesto se siente idéntico.
 */
class DragReorderState internal constructor(
    internal val listState: LazyListState,
    internal val reducedMotion: Boolean,
    internal val haptic: () -> Unit,
    internal val scope: CoroutineScope,
    internal val density: Density,
) {
    var draggedKey by mutableStateOf<Any?>(null)
        internal set
    internal var offsetY by mutableFloatStateOf(0f)
    internal var startIndex = -1

    // Velocidad de auto-scroll (px/frame, 0 = ninguna) y el bucle que la aplica mientras dura el arrastre.
    internal var autoScrollSpeed = 0f
    internal var autoScrollJob: Job? = null
    // Re-evaluación de intercambios con los parámetros del arrastre actual (para usarla dentro del bucle).
    internal var evaluate: (() -> Unit)? = null

    fun isDragging(key: Any): Boolean = draggedKey == key
}

@Composable
fun rememberDragReorderState(listState: LazyListState): DragReorderState {
    val reduced = LocalReducedMotion.current
    val haptic = rememberHapticTick()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    return remember(listState) { DragReorderState(listState, reduced, haptic, scope, density) }
}

internal fun <T> DragReorderState.startDrag(key: Any, items: SnapshotStateList<T>, keyOf: (T) -> Any) {
    draggedKey = key
    offsetY = 0f
    startIndex = items.indexOfFirst { keyOf(it) == key }
    haptic()
    // Bucle de auto-scroll: mientras dure el arrastre, cada frame desplaza la lista por la velocidad
    // vigente (0 si la fila no está en un borde) y reacomoda los intercambios con el contenido movido.
    autoScrollJob?.cancel()
    autoScrollSpeed = 0f
    autoScrollJob = scope.launch {
        while (isActive) {
            val speed = autoScrollSpeed
            if (speed != 0f) {
                // Salvaguarda: si la fila arrastrada ya no está en pantalla, no sigas desplazando la
                // lista sola (evita que se vaya hasta la cima sin que el usuario suelte).
                if (listState.layoutInfo.visibleItemsInfo.none { it.key == draggedKey }) {
                    autoScrollSpeed = 0f
                } else {
                    val consumed = listState.scrollBy(speed)
                    if (consumed != 0f) {
                        offsetY += consumed // mantiene la fila bajo el dedo pese al desplazamiento
                        evaluate?.invoke()
                        if (speed > 0f) clampBottomOffset() else clampTopOffset()
                    }
                }
            }
            withFrameNanos { }
        }
    }
}

/** Sigue el dedo y, al cruzar otra fila del MISMO grupo, la intercambia en [items] compensando el desfase. */
internal fun <T> DragReorderState.onDrag(
    deltaY: Float,
    items: SnapshotStateList<T>,
    keyOf: (T) -> Any,
    sameGroup: (T, T) -> Boolean,
) {
    offsetY += deltaY
    evaluate = { evaluateSwap(items, keyOf, sameGroup) }
    evaluate?.invoke()
    // Limita la fila DENTRO del viewport: al bajar, sobre la bottom bar; al subir, sobre el borde superior
    // (sin esto, arrastrar arriba más rápido de lo que intercambia sacaba la fila del viewport, la
    // descomponía y el gesto moría solo). No cambia la bajada normal.
    if (deltaY > 0f) clampBottomOffset() else if (deltaY < 0f) clampTopOffset()
    updateAutoScrollSpeed(items, keyOf)
}

/**
 * Impide que la fila arrastrada baje MÁS ALLÁ del fondo real del contenido (por encima de la bottom bar,
 * ya que el detalle se dibuja borde a borde y la lista se extiende bajo la barra). Sin este tope la fila
 * quedaba detrás de la barra y el gesto se cortaba. Solo limita hacia ABAJO: el gesto hacia arriba no se toca.
 */
private fun DragReorderState.clampBottomOffset() {
    val key = draggedKey ?: return
    val info = listState.layoutInfo
    val fromInfo = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
    val maxOffset = (info.viewportEndOffset - info.afterContentPadding - fromInfo.size - fromInfo.offset).toFloat()
    if (offsetY > maxOffset) offsetY = maxOffset
}

/**
 * Impide que la fila arrastrada suba por ENCIMA del borde superior del contenido. Sin este tope, subir
 * más rápido de lo que la lista intercambia sacaba la fila del viewport (se descomponía y el gesto moría
 * solo, llevándose la fila a la cima). La fila se queda en el borde mientras la lista auto-scrollea.
 */
private fun DragReorderState.clampTopOffset() {
    val key = draggedKey ?: return
    val info = listState.layoutInfo
    val fromInfo = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
    // Por CENTRO (no por borde): mantiene el centro de la fila dentro del contenido, así SIEMPRE hay
    // una fila bajo él con la que intercambiar. Evita que un arrastre rápido saque la fila del viewport
    // (deriva → se descompone → el gesto muere). El borde llegaba a empujar la fila hacia abajo.
    val minOffset = (info.viewportStartOffset + info.beforeContentPadding).toFloat() - (fromInfo.offset + fromInfo.size / 2f)
    if (offsetY < minOffset) offsetY = minOffset
}

/** Intercambio: si el centro de la fila arrastrada cae dentro de otra del mismo grupo, las permuta. */
private fun <T> DragReorderState.evaluateSwap(
    items: SnapshotStateList<T>,
    keyOf: (T) -> Any,
    sameGroup: (T, T) -> Boolean,
) {
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

/**
 * Ajusta la velocidad de auto-scroll según lo cerca que esté la fila arrastrada de la cima/fondo. Solo
 * actúa mientras HAYA a dónde reordenar: hacia arriba, si la fila no es ya la primera y la lista puede
 * subir; simétrico hacia abajo. Sin esto, al tocar la cima seguía empujando contra una cabecera fija y
 * el gesto se cancelaba (la fila se soltaba sola).
 */
private fun <T> DragReorderState.updateAutoScrollSpeed(items: SnapshotStateList<T>, keyOf: (T) -> Any) {
    val key = draggedKey
    if (key == null) { autoScrollSpeed = 0f; return }
    val info = listState.layoutInfo
    val fromInfo = info.visibleItemsInfo.firstOrNull { it.key == key }
    if (fromInfo == null) { autoScrollSpeed = 0f; return }
    val index = items.indexOfFirst { keyOf(it) == key }
    // Espejo exacto de la bajada: se auto-scrollea hacia arriba si no es ya la primera y la lista puede
    // subir; hacia abajo si no es la última y puede bajar.
    val canUp = index > 0 && listState.canScrollBackward
    val canDown = index in 0 until (items.size - 1) && listState.canScrollForward
    val edge = with(density) { AutoScrollEdge.toPx() }
    val maxStep = with(density) { AutoScrollMaxStep.toPx() }
    val top = fromInfo.offset + offsetY
    val bottom = top + fromInfo.size
    // Bordes EFECTIVOS del contenido: el fondo descuenta el padding inferior (bottom bar), así el
    // auto-scroll actúa ANTES de que la fila se meta detrás de la barra. Arriba no hay barra (=0).
    val vStart = (info.viewportStartOffset + info.beforeContentPadding).toFloat()
    val vEnd = (info.viewportEndOffset - info.afterContentPadding).toFloat()
    autoScrollSpeed = when {
        canUp && top < vStart + edge -> -maxStep * (((vStart + edge) - top) / edge).coerceIn(0f, 1f)
        canDown && bottom > vEnd - edge -> maxStep * ((bottom - (vEnd - edge)) / edge).coerceIn(0f, 1f)
        else -> 0f
    }
}

/** Al soltar: persiste vía [onSettle] (clave, índice inicial, índice final), tick háptico y asienta con spring. */
internal fun <T> DragReorderState.endDrag(
    items: SnapshotStateList<T>,
    keyOf: (T) -> Any,
    onSettle: (key: Any, from: Int, to: Int) -> Unit,
) {
    val key = draggedKey ?: return
    // Detiene el auto-scroll del arrastre.
    autoScrollJob?.cancel()
    autoScrollJob = null
    autoScrollSpeed = 0f
    evaluate = null
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
