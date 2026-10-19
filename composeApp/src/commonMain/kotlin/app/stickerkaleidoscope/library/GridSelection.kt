package app.stickerkaleidoscope.library

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.floor

/** Uniform content geometry includes rows which LazyVerticalGrid has disposed offscreen. */
internal data class SelectionGridGeometry(
    val columns: Int,
    val viewportWidth: Int,
    val itemHeight: Int,
    val spacing: Int,
    val padding: Int
) {
    val rowStride: Int get() = itemHeight + spacing

    fun scrollOffset(firstVisibleIndex: Int, firstVisibleOffset: Int): Float =
        (firstVisibleIndex / columns * rowStride + firstVisibleOffset).toFloat()

    fun bounds(index: Int): Rect {
        val available = (viewportWidth - padding * 2 - spacing * (columns - 1)).coerceAtLeast(0)
        val width = available / columns
        val remainder = available % columns
        val column = index % columns
        val left = padding + column * (width + spacing) + minOf(column, remainder)
        val top = padding + index / columns * rowStride
        return Rect(left.toFloat(), top.toFloat(), (left + width + if (column < remainder) 1 else 0).toFloat(), (top + itemHeight).toFloat())
    }

    fun select(order: List<String>, startInContent: Offset, endInViewport: Offset, scrollY: Float, previous: Set<String>, additive: Boolean): Set<String> {
        if (order.isEmpty()) return if (additive) previous else emptySet()
        val area = selectionRectangle(startInContent, endInViewport + Offset(0f, scrollY))
        val lastRow = (order.size - 1) / columns
        val first = floor((area.top - padding) / rowStride).toInt().coerceIn(0, lastRow)
        val last = floor((area.bottom - padding) / rowStride).toInt().coerceIn(0, lastRow)
        val selected = buildSet {
            for (row in first..last) for (column in 0 until columns) {
                val index = row * columns + column
                if (index < order.size && bounds(index).overlaps(area)) add(order[index])
            }
        }
        return if (additive) previous + selected else selected
    }
}

internal fun selectionRectangle(start: Offset, end: Offset) = Rect(
    minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y)
)

internal fun boxSelection(bounds: List<Pair<String, Rect>>, area: Rect, previous: Set<String>, additive: Boolean): Set<String> {
    val hits = bounds.filter { (_, rect) -> rect.overlaps(area) }.map { it.first }.toSet()
    return if (additive) previous + hits else hits
}

internal fun clickSelection(order: List<String>, previous: Set<String>, anchor: String?, clicked: String, control: Boolean, shift: Boolean): Set<String> {
    if (clicked !in order) return previous
    if (shift && anchor in order) {
        val range = listOf(order.indexOf(anchor), order.indexOf(clicked)).sorted()
        val selected = order.subList(range[0], range[1] + 1).toSet()
        return if (control) previous + selected else selected
    }
    return if (control) { if (clicked in previous) previous - clicked else previous + clicked } else setOf(clicked)
}
