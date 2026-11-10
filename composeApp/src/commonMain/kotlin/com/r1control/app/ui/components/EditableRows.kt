package com.r1control.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.r1control.app.domain.model.moved
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.Radius
import com.r1control.app.ui.theme.Tokens
import kotlinx.coroutines.delay

// The one drag-and-delete list, ported from mytube's queue (QueueDrag.kt and
// castQueueItems). The search results, a playlist's songs and the news
// categories use it; nothing else in the app drags rows.
//
// What differs from mytube, each for an R1 fact:
// - It is generic over the row type (Track and NewsCategory).
// - Rows need not share a height: a song row grows by the failed-play line,
//   and every row grows with the font scale. A step is taken when the finger
//   passes half of the neighbour's own measured height ([dropStep]).
// - The top edge of the auto-scroll is the visible top, content padding included.

/** Where the held row is after the finger moved it: the slot it is in now, and how far it is from that slot. */
data class DropStep(val index: Int, val offsetPx: Float)

/**
 * The held row sits at [heldIndex] and is [offsetPx] from that slot. It takes
 * one slot further for each neighbour whose half height it has passed, as many
 * as it passed in one move. [sizeAt] is a row's measured height; an answer of
 * zero or less (a row not laid out) stops the step, and the next move, after
 * the auto-scroll has brought that row into view, takes it.
 */
fun dropStep(heldIndex: Int, offsetPx: Float, count: Int, sizeAt: (Int) -> Float): DropStep {
    var index = heldIndex
    var offset = offsetPx
    while (index < count - 1) {
        val below = sizeAt(index + 1)
        if (below <= 0f || offset <= below / 2) break
        offset -= below
        index++
    }
    while (index > 0) {
        val above = sizeAt(index - 1)
        if (above <= 0f || offset >= -above / 2) break
        offset += above
        index--
    }
    return DropStep(index, offset)
}

/**
 * How far to scroll the list this frame while a row is held: nothing until the
 * row is within [edgePx] of an edge, then faster the further in, up to
 * [maxStepPx], as every list that is reordered by dragging does.
 */
fun edgeScrollStep(
    itemTop: Float,
    itemBottom: Float,
    viewportStart: Float,
    viewportEnd: Float,
    edgePx: Float,
    maxStepPx: Float,
): Float {
    val intoTop = viewportStart + edgePx - itemTop
    val intoBottom = itemBottom - (viewportEnd - edgePx)
    return when {
        intoTop > 0f -> -maxStepPx * (intoTop / edgePx).coerceAtMost(1f)
        intoBottom > 0f -> maxStepPx * (intoBottom / edgePx).coerceAtMost(1f)
        else -> 0f
    }
}

/**
 * One row held by its handle. Hoisted above the list ([rememberListDrag]),
 * because a `remember` inside a lazy item is a cache that dies when the row
 * scrolls away, and the held row does scroll.
 *
 * [order] is the list as it is being rearranged. While it is set it is what
 * the rows draw, so a poll landing mid-drag cannot move a row under the finger.
 * The edit reaches its owner once, on release, and [order] is kept until the
 * owner's list shows it ([settling]), so the row does not jump back and forward.
 */
class ListDrag<T> {
    var order by mutableStateOf<List<T>?>(null)
        private set
    var heldIndex by mutableIntStateOf(-1)
        private set
    var offsetPx by mutableFloatStateOf(0f)
        private set
    var settling by mutableStateOf(false)
        private set
    private var startIndex = -1

    /** A finger is on a handle. */
    val holding: Boolean get() = heldIndex >= 0

    fun start(index: Int, items: List<T>) {
        order = items
        startIndex = index
        heldIndex = index
        offsetPx = 0f
        settling = false
    }

    /**
     * The finger, or the list under it, moved by [dy]. The row takes its new
     * place in [order] as it goes: it must stay the row under the finger, or a
     * lazy list disposes it and the gesture with it. True when it moved.
     */
    fun dragBy(dy: Float, sizeAt: (Int) -> Float): Boolean {
        val current = order ?: return false
        if (heldIndex < 0) return false
        offsetPx += dy
        val step = dropStep(heldIndex, offsetPx, current.size, sizeAt)
        if (step.index == heldIndex) return false
        order = current.moved(heldIndex, step.index)
        heldIndex = step.index
        offsetPx = step.offsetPx
        return true
    }

    /** Let go: where the row started and where it ended, or null when it is back where it was. */
    fun end(): Pair<Int, Int>? {
        val moved = (startIndex to heldIndex).takeIf { heldIndex >= 0 && it.first != it.second }
        heldIndex = -1
        offsetPx = 0f
        startIndex = -1
        if (moved == null) {
            order = null
        } else {
            settling = true
        }
        return moved
    }

    fun cancel() {
        order = null
        heldIndex = -1
        offsetPx = 0f
        startIndex = -1
        settling = false
    }

    /** The owner's list now shows the move, or it never will. */
    fun settled() {
        if (heldIndex >= 0) return
        order = null
        settling = false
    }
}

/** A [ListDrag] that lets go of its order once [items] show the move it made, or after [SETTLE_MILLIS] if they never do. */
@Composable
fun <T> rememberListDrag(items: List<T>): ListDrag<T> {
    val drag = remember { ListDrag<T>() }
    LaunchedEffect(drag.settling, items) {
        if (!drag.settling) return@LaunchedEffect
        if (drag.order != items) delay(SETTLE_MILLIS)
        drag.settled()
    }
    return drag
}

private const val SETTLE_MILLIS = 300L

/** How near an edge a held row starts the list scrolling, and how fast at most per frame. */
private val EDGE = 72.dp
private val STEP = 18.dp

/** The handle's size: wide enough for a thumb, 48 dp tall like every target. */
val HandleWidth = 40.dp
val HandleHeight = 48.dp

/**
 * Rows that reorder by a handle at their start and, when [onRemove] is given,
 * delete by an X at their end: mytube's cast queue, for any list.
 *
 * - The handle ([HandleWidth] x [HandleHeight], `R1Icons.DragHandle`) carries
 *   the only drag gesture (`detectDragGestures`, after touch slop, no long
 *   press), so the page scrolls from anywhere else on the row. `pointerInput`
 *   is keyed on the row's key and the list goes through `rememberUpdatedState`,
 *   so the gesture never restarts mid-drag.
 * - The held row follows the finger in the draw phase (`graphicsLayer
 *   { translationY }`, `zIndex(1f)`); the others `animateItem()` into place.
 * - While held, a `withFrameNanos` loop in the held item scrolls the list near
 *   an edge and feeds the distance actually scrolled back into [ListDrag.dragBy].
 * - [onMove] is called once, on release, with the indexes into [items] as they
 *   were when the row was picked up.
 * - The X is disabled, not hidden, when [canRemove] is false (one row left);
 *   [onRemove] null draws no X: the news categories have none.
 * - [enabled] false: the handle is dimmed and has no gesture, the X likewise.
 * - The handle carries "Move up" and "Move down" for a screen reader, which
 *   cannot drag; they call the same [onMove] by one place.
 *
 * [key] must be unique across [items] and across every other key in the list
 * it is placed in (songs use `songKey`, news `"news:<key>"`).
 */
fun <T> LazyListScope.editableItems(
    items: List<T>,
    key: (T) -> String,
    drag: ListDrag<T>,
    list: LazyListState,
    enabled: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: ((T) -> Unit)?,
    canRemove: Boolean,
    handleLabel: (T) -> String,
    removeLabel: (T) -> String,
    /** Applied to every row: the side margin of the page the list is in. */
    rowModifier: Modifier = Modifier,
    /** False draws no handle at all, for a list whose order cannot be changed by a drag. */
    handle: Boolean = true,
    row: @Composable RowScope.(T) -> Unit,
) {
    val shown = drag.order ?: items
    items(shown.size, key = { key(shown[it]) }) { i ->
        val item = shown[i]
        val itemKey = key(item)
        val held = drag.holding && drag.heldIndex == i
        val index by rememberUpdatedState(i)
        val latest by rememberUpdatedState(items)
        val density = LocalDensity.current
        // The row the finger holds takes its new place in [order] at once, and a lazy list keeps the item
        // it has at the top by key: when the held row or the one it swaps with is the first on screen, the
        // list would follow it and scroll a row away under the finger. The position is put back.
        val dragHeld = { dy: Float ->
            val first = list.firstVisibleItemIndex
            val offset = list.firstVisibleItemScrollOffset
            val moved = drag.dragBy(dy) { at -> rowHeight(list, drag, key, at) }
            if (moved) list.requestScrollToItem(first, offset)
            moved
        }
        if (held) {
            LaunchedEffect(Unit) {
                val edge = with(density) { EDGE.toPx() }
                val most = with(density) { STEP.toPx() }
                while (true) {
                    withFrameNanos {}
                    val info = list.layoutInfo
                    val heldRow = info.visibleItemsInfo.firstOrNull { it.key == itemKey } ?: continue
                    val top = heldRow.offset + drag.offsetPx
                    val step = edgeScrollStep(
                        top, top + heldRow.size,
                        viewportStart = info.viewportStartOffset.toFloat(),
                        viewportEnd = info.viewportEndOffset.toFloat(),
                        edgePx = edge, maxStepPx = most,
                    )
                    // The list moves under a finger that has not: the row keeps up by the distance actually scrolled.
                    if (step != 0f) dragHeld(list.scrollBy(step))
                }
            }
        }
        Row(
            // The held row is placed by the finger, not animated to its slot; the rest slide into theirs.
            (if (held) Modifier.zIndex(1f) else Modifier.animateItem())
                .then(rowModifier)
                .fillMaxWidth()
                // Read while drawing, so the row follows the finger without recomposing the list on every move.
                .graphicsLayer { translationY = if (drag.holding && drag.heldIndex == index) drag.offsetPx else 0f }
                .background(if (held) Tokens.CardRaised else Color.Transparent, RoundedCornerShape(Radius.Row)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val strings = LocalStrings.current
            if (handle) DragHandle(
                label = handleLabel(item),
                enabled = enabled,
                steps = if (!enabled) emptyList() else {
                    val at = items.indexOf(item)
                    listOfNotNull(
                        if (at > 0) CustomAccessibilityAction(strings.moveUp) { onMove(at, at - 1); true } else null,
                        if (at in 0 until items.size - 1) CustomAccessibilityAction(strings.moveDown) { onMove(at, at + 1); true } else null,
                    )
                },
                modifier = Modifier.pointerInput(itemKey, enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { drag.start(index, latest) },
                        onDragEnd = { drag.end()?.let { (from, to) -> onMove(from, to) } },
                        onDragCancel = { drag.cancel() },
                    ) { change, amount ->
                        change.consume()
                        dragHeld(amount.y)
                    }
                },
            )
            row(item)
            if (onRemove != null) {
                IconTap(R1Icons.Close, removeLabel(item), { onRemove(item) }, enabled = enabled && canRemove, tint = Tokens.OnGroundMuted)
            }
        }
    }
}

/**
 * The measured height of the row now at [at] in the order being dragged. A row
 * that is not laid out answers 0, which stops the step until the auto-scroll
 * has brought it into view.
 */
private fun <T> rowHeight(list: LazyListState, drag: ListDrag<T>, key: (T) -> String, at: Int): Float {
    val row = drag.order?.getOrNull(at) ?: return 0f
    val wanted = key(row)
    return list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == wanted }?.size?.toFloat() ?: 0f
}

@Composable
private fun DragHandle(label: String, enabled: Boolean, steps: List<CustomAccessibilityAction>, modifier: Modifier) {
    Box(
        modifier
            .size(HandleWidth, HandleHeight)
            .semantics {
                contentDescription = label
                role = Role.Button
                customActions = steps
            },
        contentAlignment = Alignment.Center,
    ) {
        R1Icon(
            R1Icons.DragHandle, null,
            tint = if (enabled) Tokens.OnGroundMuted else Tokens.OnGroundMuted.copy(alpha = 0.38f),
        )
    }
}
