package com.iblu01.portallauncher.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.iblu01.portallauncher.ui.apps.GridCell
import com.iblu01.portallauncher.ui.apps.GridSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The drag coordinator's arithmetic: which cell an icon is over, and when holding it at an edge
 * should flip the page. Pure state, no Compose needed.
 */
class GridDragStateTest {

    private val spec = GridSpec(columns = 4, rows = 3)
    private val edge = 40f

    /** 400 x 300 viewport, cells 100 x 100. */
    private fun stateOverPage(page: Int = 0): GridDragState = GridDragState().apply {
        viewportRect = Rect(Offset.Zero, Size(400f, 300f))
        registerPage(page, PageGeometry(Rect(Offset.Zero, Size(400f, 300f)), spec))
    }

    /** Grabs a 100x100 tile by its centre, then puts that centre at [centre]. */
    private fun GridDragState.grabAt(centre: Offset) {
        val tile = Rect(centre - Offset(50f, 50f), Size(100f, 100f))
        start(key = "app:x/y", icon = null, label = "X", tileRect = tile, pointerRoot = centre)
        pointer = centre
    }

    @Test
    fun `the cell under the icon follows its centre, not the finger`() {
        val state = stateOverPage()
        state.grabAt(Offset(250f, 150f))

        assertEquals(GridCell(0, 2, 1), state.hoveredCell())
    }

    @Test
    fun `holding the icon at either edge flips that way`() {
        val state = stateOverPage()

        state.grabAt(Offset(10f, 150f))
        assertEquals("held against the left edge", -1, state.edgeDirection(edge))

        state.grabAt(Offset(390f, 150f))
        assertEquals("held against the right edge", 1, state.edgeDirection(edge))

        state.grabAt(Offset(200f, 150f))
        assertEquals("mid-page: no flip", 0, state.edgeDirection(edge))
    }

    @Test
    fun `an already-scrolled page cannot hijack the edge test`() {
        val state = stateOverPage(page = 1)
        // Page 0 has been scrolled off to the left, as it is mid-flip: its rectangle is entirely
        // negative. Measuring edges against a page rather than the viewport made every position
        // look like "against the right edge" (so forward flips fired constantly) and none like the
        // left one (so going back to an earlier page was impossible).
        state.registerPage(0, PageGeometry(Rect(Offset(-400f, 0f), Size(400f, 300f)), spec))

        state.grabAt(Offset(10f, 150f))
        assertEquals(-1, state.edgeDirection(edge))

        state.grabAt(Offset(200f, 150f))
        assertEquals(0, state.edgeDirection(edge))
    }

    @Test
    fun `no viewport yet means no flip`() {
        val state = GridDragState()
        state.grabAt(Offset(10f, 150f))

        assertEquals(0, state.edgeDirection(edge))
    }

    @Test
    fun `an icon over no page has no target cell`() {
        val state = stateOverPage()
        // Between pages mid-flip, or over the clock page.
        state.grabAt(Offset(-500f, 150f))

        assertNull(state.hoveredCell())
    }

    @Test
    fun `the icon is drawn where it was grabbed, not snapped to the finger`() {
        val state = stateOverPage()
        state.grabAt(Offset(250f, 150f))

        assertEquals(Offset(200f, 100f), state.tileTopLeft())

        state.pointer = Offset(260f, 160f)
        assertEquals("the grab offset is kept for the whole drag", Offset(210f, 110f), state.tileTopLeft())
    }
}
