package com.iblu01.portallauncher.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Multi-cell items: how a span becomes cells, fits (or does not), and lands under the finger. */
class WidgetPlacementTest {

    private val spec = GridSpec(columns = 4, rows = 3)

    private fun widget(id: Int, span: GridSpan) = GridItem(
        key = GridItem.widgetKey(id),
        label = "Widget $id",
        defaultLabel = "Widget $id",
        icon = null,
        packageName = "com.w",
        widgetId = id,
        defaultSpan = span,
    )

    private fun icon(pkg: String) = GridItem(
        key = GridItem.appKey(pkg, "$pkg.Main"),
        label = pkg,
        defaultLabel = pkg,
        icon = null,
        packageName = pkg,
        activityName = "$pkg.Main",
    )

    @Test
    fun `a declared minimum size is rounded up to whole cells`() {
        // 180 dp wide on 112 dp cells needs two: a widget that gets fewer cells than it asks for
        // renders clipped.
        assertEquals(GridSpan(2, 1), spanForMinSize(180f, 90f, cellWidthDp = 112f, cellHeightDp = 116f))
        assertEquals(GridSpan(1, 1), spanForMinSize(40f, 40f, cellWidthDp = 112f, cellHeightDp = 116f))
        assertEquals(GridSpan(3, 2), spanForMinSize(250f, 150f, cellWidthDp = 112f, cellHeightDp = 116f))
        // Degenerate cell size must not produce a zero or negative span.
        assertEquals(GridSpan(1, 1), spanForMinSize(250f, 150f, cellWidthDp = 0f, cellHeightDp = 0f))
    }

    @Test
    fun `a footprint covers every cell it spans, and only fits inside the grid`() {
        assertEquals(
            listOf(
                GridCell(0, 1, 1), GridCell(0, 2, 1),
                GridCell(0, 1, 2), GridCell(0, 2, 2),
            ),
            footprint(GridCell(0, 1, 1), GridSpan(2, 2)),
        )
        assertTrue(fitsIn(GridCell(0, 2, 1), GridSpan(2, 2), spec))
        assertFalse("a 2-wide widget cannot start on the last column", fitsIn(GridCell(0, 3, 0), GridSpan(2, 1), spec))
        assertFalse("nor a 2-tall one on the last row", fitsIn(GridCell(0, 0, 2), GridSpan(1, 2), spec))
    }

    @Test
    fun `a span wider than the grid is clamped rather than dropped`() {
        val placed = placeItems(
            items = listOf(widget(7, GridSpan(9, 9))),
            stored = emptyMap(),
            spec = spec,
        )

        assertEquals(GridSpan(4, 3), placed.single().span)
        assertEquals(GridCell(0, 0, 0), placed.single().cell)
    }

    @Test
    fun `icons never land inside a widget's footprint`() {
        val placed = placeItems(
            items = listOf(widget(7, GridSpan(2, 2)), icon("a"), icon("b"), icon("c")),
            stored = mapOf(GridItem.widgetKey(7) to GridPlacement(GridCell(0, 0, 0), GridSpan(2, 2))),
            spec = spec,
        )

        val widgetCells = footprint(GridCell(0, 0, 0), GridSpan(2, 2)).toSet()
        val iconCells = placed.filterNot { it.item.isWidget }.map { it.cell }
        assertTrue("icons must go around the widget", iconCells.none { it in widgetCells })
        assertEquals("nothing was dropped", 4, placed.size)
    }

    @Test
    fun `a widget whose footprint no longer fits is re-homed`() {
        // Stored against a wider grid: col 3 + span 2 runs off a 4-column page.
        val placed = placeItems(
            items = listOf(widget(7, GridSpan(2, 1))),
            stored = mapOf(GridItem.widgetKey(7) to GridPlacement(GridCell(0, 3, 0), GridSpan(2, 1))),
            spec = spec,
        )

        assertTrue(fitsIn(placed.single().cell, placed.single().span, spec))
    }

    @Test
    fun `the first free area skips anything a smaller item could have squeezed into`() {
        val taken = footprint(GridCell(0, 0, 0), GridSpan(1, 1)).toSet()

        assertEquals("a 1x1 takes the next cell", GridCell(0, 1, 0), firstFreeArea(taken, spec, GridSpan(1, 1)))
        // A 2-wide item cannot start at col 0 (taken) but fits from col 1.
        assertEquals(GridCell(0, 1, 0), firstFreeArea(taken, spec, GridSpan(2, 1)))
        // Fill the top row: a 2x2 has to move down.
        val topRow = (0 until 4).map { GridCell(0, it, 0) }.toSet()
        assertEquals(GridCell(0, 0, 1), firstFreeArea(topRow, spec, GridSpan(2, 2)))
    }

    @Test
    fun `a dragged widget lands centred on the finger, nudged back onto the page`() {
        // The cell under the centre is the middle of the footprint, not its corner.
        assertEquals(GridCell(0, 1, 0), originForCentre(GridCell(0, 1, 0), GridSpan(2, 1), spec))
        assertEquals(GridCell(0, 1, 1), originForCentre(GridCell(0, 2, 2), GridSpan(3, 2), spec))
        // Dropped against the right edge: pulled back so the whole widget still fits.
        assertEquals(GridCell(0, 2, 0), originForCentre(GridCell(0, 3, 0), GridSpan(2, 1), spec))
    }

    @Test
    fun `the item covering a cell is found through its whole footprint`() {
        val placed = listOf(PlacedItem(widget(7, GridSpan(2, 2)), GridCell(0, 1, 0), GridSpan(2, 2)))

        assertEquals(7, placed.coveringCell(GridCell(0, 2, 1))?.item?.widgetId)
        assertEquals(null, placed.coveringCell(GridCell(0, 0, 0)))
        assertEquals("footprints do not cross pages", null, placed.coveringCell(GridCell(1, 1, 0)))
    }
}
