package com.morningsteps.kids.ui.parent

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drag-and-drop reordering for a LazyColumn, built only on Compose pointer input and list state.
 *
 * - Items are identified by stable keys; [onMove] receives the dragged key and the key it passed.
 * - The dragged item follows the finger; others shift as soon as the middle of the dragged item
 *   crosses them (that live shift is the insertion feedback).
 * - Near the top/bottom edge the list scrolls automatically, faster the closer the finger is.
 */
class ReorderState internal constructor(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val edgePx: Float,
    private val maxSpeedPx: Float,
    private val canMove: (key: Any) -> Boolean,
    private val onMove: (fromKey: Any, toKey: Any) -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    private var draggedDelta by mutableFloatStateOf(0f)
    private var initialOffset = 0
    private var autoScrollJob: Job? = null

    private val draggingInfo: LazyListItemInfo?
        get() = draggingKey?.let { key -> listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } }

    /** Vertical translation for the dragged item so it stays under the finger. */
    val draggingOffset: Float
        get() = draggingInfo?.let { initialOffset + draggedDelta - it.offset } ?: 0f

    fun isDragging(key: Any) = draggingKey == key

    fun onDragStart(key: Any) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingKey = key
        initialOffset = info.offset
        draggedDelta = 0f
    }

    fun onDrag(dy: Float) {
        if (draggingKey == null) return
        draggedDelta += dy
        swapIfNeeded()
        ensureAutoScroll()
    }

    fun onDragEnd() {
        draggingKey = null
        draggedDelta = 0f
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    private fun swapIfNeeded() {
        val item = draggingInfo ?: return
        val top = item.offset + draggingOffset
        val middle = top + item.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != item.key && canMove(it.key) && middle >= it.offset && middle <= it.offset + it.size
        } ?: return
        val first = listState.firstVisibleItemIndex
        if (item.index == first || target.index == first) {
            // Keep the viewport anchored while the first visible item changes position.
            val offset = listState.firstVisibleItemScrollOffset
            scope.launch { listState.scrollToItem(first, offset) }
        }
        onMove(item.key, target.key)
    }

    private fun scrollSpeed(): Float {
        val item = draggingInfo ?: return 0f
        val top = item.offset + draggingOffset
        val bottom = top + item.size
        val info = listState.layoutInfo
        val start = info.viewportStartOffset.toFloat()
        val end = info.viewportEndOffset.toFloat()
        return when {
            bottom > end - edgePx && listState.canScrollForward ->
                ((bottom - (end - edgePx)) / edgePx).coerceIn(0f, 1f) * maxSpeedPx
            top < start + edgePx && listState.canScrollBackward ->
                -((start + edgePx - top) / edgePx).coerceIn(0f, 1f) * maxSpeedPx
            else -> 0f
        }
    }

    private fun ensureAutoScroll() {
        if (autoScrollJob?.isActive == true || scrollSpeed() == 0f) return
        autoScrollJob = scope.launch {
            while (isActive && draggingKey != null) {
                val speed = scrollSpeed()
                if (speed == 0f) break
                listState.scrollBy(speed)
                swapIfNeeded()
                delay(16)
            }
        }
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    canMove: (Any) -> Boolean,
    onMove: (Any, Any) -> Unit,
): ReorderState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val currentCanMove by rememberUpdatedState(canMove)
    val currentOnMove by rememberUpdatedState(onMove)
    return remember(listState, scope, density) {
        ReorderState(
            listState = listState,
            scope = scope,
            edgePx = with(density) { 72.dp.toPx() },
            maxSpeedPx = with(density) { 14.dp.toPx() },
            canMove = { currentCanMove(it) },
            onMove = { a, b -> currentOnMove(a, b) },
        )
    }
}

/** Attach to the visible drag handle of the item with [key]. */
fun Modifier.reorderHandle(state: ReorderState, key: Any): Modifier = pointerInput(state, key) {
    detectDragGestures(
        onDragStart = { state.onDragStart(key) },
        onDragEnd = { state.onDragEnd() },
        onDragCancel = { state.onDragEnd() },
        onDrag = { change, amount ->
            change.consume()
            state.onDrag(amount.y)
        },
    )
}
