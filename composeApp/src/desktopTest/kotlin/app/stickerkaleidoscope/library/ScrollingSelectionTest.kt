package app.stickerkaleidoscope.library

import androidx.compose.ui.geometry.Offset
import org.junit.Test
import kotlin.test.*

class ScrollingSelectionTest {
    private val geometry = SelectionGridGeometry(columns = 3, viewportWidth = 480, itemHeight = 156, spacing = 12, padding = 24)
    private val order = (0 until 36).map { "item-$it" }

    @Test fun `marquee can start in left right or top empty padding`() {
        assertEquals(setOf("item-0"), geometry.select(order, Offset(0f, 50f), Offset(150f, 150f), 0f, emptySet(), false))
        assertEquals(setOf("item-2"), geometry.select(order, Offset(480f, 50f), Offset(330f, 150f), 0f, emptySet(), false))
        assertEquals(setOf("item-0", "item-1", "item-2"), geometry.select(order, Offset(0f, 0f), Offset(470f, 170f), 0f, emptySet(), false))
    }

    @Test fun `wheel scrolling keeps original offscreen rows inside the content anchored rectangle`() {
        val start = Offset(0f, 0f)
        val end = Offset(470f, 340f)
        val initial = geometry.select(order, start, end, 0f, emptySet(), false)
        assertEquals(order.take(6).toSet(), initial)
        val afterWheel = geometry.select(order, start, end, geometry.scrollOffset(9, 0), emptySet(), false)
        assertEquals(order.take(15).toSet(), afterWheel)
        assertTrue(afterWheel.containsAll(initial))
        assertEquals(initial, geometry.select(order, start, end, 0f, emptySet(), false))
    }

    @Test fun `drag starting partway down the library is anchored to its initial content position`() {
        val initialScroll = geometry.scrollOffset(12, 0)
        val startInContent = Offset(0f, 40f + initialScroll)
        assertEquals(order.slice(12..17).toSet(), geometry.select(order, startInContent, Offset(470f, 300f), initialScroll, emptySet(), false))
        val scrolledUp = geometry.select(order, startInContent, Offset(470f, 40f), geometry.scrollOffset(6, 0), emptySet(), false)
        assertEquals(order.slice(6..14).toSet(), scrolledUp)
    }

    @Test fun `ctrl selection preserves its initial snapshot while a shrinking rectangle removes newly selected rows`() {
        val original = setOf("item-35")
        val expanded = geometry.select(order, Offset.Zero, Offset(470f, 340f), 504f, original, true)
        assertEquals(original + order.take(15), expanded)
        val shrunk = geometry.select(order, Offset.Zero, Offset(150f, 160f), 0f, original, true)
        assertEquals(original + "item-0", shrunk)
    }

    @Test fun `rounding remainders and partially filled final rows match fixed grid placement`() {
        val rounded = geometry.copy(viewportWidth = 481)
        assertEquals(457f, rounded.bounds(2).right)
        assertEquals(12f, rounded.bounds(1).left - rounded.bounds(0).right)
        assertEquals(12f, rounded.bounds(2).left - rounded.bounds(1).right)
        val partial = order.take(10)
        assertEquals(partial.toSet(), rounded.select(partial, Offset.Zero, Offset(481f, 2000f), 0f, emptySet(), false))
        assertTrue(rounded.select(partial, Offset(0f, 2000f), Offset(481f, 2100f), 0f, emptySet(), false).isEmpty())
    }
}
