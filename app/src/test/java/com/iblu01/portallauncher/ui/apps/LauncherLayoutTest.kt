package com.iblu01.portallauncher.ui.apps

import com.iblu01.portallauncher.PinnedShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid's placement rules, as pure functions (no Android): what a stored arrangement means once
 * apps come and go, how many pages that implies, and how a point maps to a cell.
 */
class LauncherLayoutTest {

    private val spec = GridSpec(columns = 4, rows = 3)

    private fun app(label: String, pkg: String) =
        LaunchableApp(label = label, packageName = pkg, activityName = "$pkg.Main", icon = null)

    private fun keyOf(pkg: String) = GridItem.appKey(pkg, "$pkg.Main")

    private fun place(
        apps: List<LaunchableApp>,
        stored: Map<String, GridCell> = emptyMap(),
        spec: GridSpec = this.spec,
    ) = placeItems(apps.map { it.toGridItem() }, stored.mapValues { GridPlacement(it.value) }, spec)

    private fun cellOf(result: List<PlacedItem>, pkg: String): GridCell =
        result.first { it.item.key == keyOf(pkg) }.cell

    @Test
    fun `an item keeps the exact cell it was dropped in, holes included`() {
        val result = place(
            apps = listOf(app("Alpha", "a"), app("Beta", "b")),
            stored = mapOf(
                keyOf("a") to GridCell(page = 0, col = 3, row = 2),
                keyOf("b") to GridCell(page = 1, col = 0, row = 0),
            ),
        )

        // Free placement: nothing is compacted towards the top-left.
        assertEquals(GridCell(0, 3, 2), cellOf(result, "a"))
        assertEquals(GridCell(1, 0, 0), cellOf(result, "b"))
    }

    @Test
    fun `a newly installed app takes the first free cell and disturbs nothing`() {
        val result = place(
            apps = listOf(app("Alpha", "a"), app("Nouveau", "n")),
            stored = mapOf(keyOf("a") to GridCell(0, 0, 0)),
        )

        assertEquals(GridCell(0, 0, 0), cellOf(result, "a"))
        assertEquals(GridCell(0, 1, 0), cellOf(result, "n"))
    }

    @Test
    fun `a cell that no longer exists is re-homed instead of vanishing`() {
        // The grid shrank (rotation): col 5 is gone.
        val result = place(
            apps = listOf(app("Alpha", "a")),
            stored = mapOf(keyOf("a") to GridCell(0, 5, 0)),
            spec = GridSpec(columns = 4, rows = 3),
        )

        assertEquals(1, result.size)
        assertTrue("the item must land inside the grid", cellOf(result, "a").isWithin(spec))
    }

    @Test
    fun `two items claiming one cell never overlap`() {
        val shared = GridCell(0, 2, 1)
        val result = place(
            apps = listOf(app("Alpha", "a"), app("Beta", "b")),
            stored = mapOf(keyOf("a") to shared, keyOf("b") to shared),
        )

        assertEquals("both items are still shown", 2, result.size)
        assertEquals("distinct cells", 2, result.map { it.cell }.distinct().size)
        assertTrue("one of them keeps the contested cell", result.any { it.cell == shared })
    }

    @Test
    fun `pinned shortcuts are placed like any other item`() {
        val shortcut = PinnedShortcut("com.x", "compose", "Nouveau message").toGridItem(icon = null)
        val result = placeItems(
            items = listOf(shortcut),
            stored = mapOf(shortcut.key to GridPlacement(GridCell(0, 1, 1))),
            spec = spec,
        )

        assertEquals(GridCell(0, 1, 1), result.single().cell)
        assertTrue(result.single().item.isShortcut)
    }

    @Test
    fun `the empty growth page exists only while an icon is in hand`() {
        assertEquals("nothing placed: a single page", 1, appPageCount(emptyList()))
        assertEquals("dragging: one page to drop onto", 2, appPageCount(emptyList(), spare = true))

        val onPageTwo = place(
            apps = listOf(app("Alpha", "a")),
            stored = mapOf(keyOf("a") to GridCell(page = 2, col = 0, row = 0)),
        )
        // Pages 0..2 exist because page 2 is occupied — no dead page at rest.
        assertEquals(3, appPageCount(onPageTwo))
        assertEquals(4, appPageCount(onPageTwo, spare = true))
    }

    @Test
    fun `a point maps to the cell it is over, clamped to the grid`() {
        // 400 x 300 page, 4 columns x 3 rows: cells are 100 x 100.
        assertEquals(GridCell(0, 0, 0), cellAt(5f, 5f, 400f, 300f, spec, page = 0))
        assertEquals(GridCell(0, 2, 1), cellAt(250f, 150f, 400f, 300f, spec, page = 0))
        assertEquals(GridCell(1, 3, 2), cellAt(399f, 299f, 400f, 300f, spec, page = 1))
        // Past the edges (a finger in the page padding) clamps rather than resolving to nothing.
        assertEquals(GridCell(0, 3, 2), cellAt(9_999f, 9_999f, 400f, 300f, spec, page = 0))
        assertEquals(GridCell(0, 0, 0), cellAt(-40f, -40f, 400f, 300f, spec, page = 0))
    }

    @Test
    fun `the grid keeps a usable minimum however small the page is`() {
        val tiny = gridSpecFor(widthDp = 40f, heightDp = 30f)

        assertTrue(tiny.columns >= 3)
        assertTrue(tiny.rows >= 2)
    }

    @Test
    fun `cell origins tile the page without gaps`() {
        val (x, y) = cellOrigin(GridCell(0, 2, 1), pageWidth = 400f, pageHeight = 300f, spec = spec)

        assertEquals(200f, x, 0.01f)
        assertEquals(100f, y, 0.01f)
    }
}
