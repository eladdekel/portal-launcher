package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.ui.apps.GridCell
import com.iblu01.portallauncher.ui.apps.GridItem
import com.iblu01.portallauncher.ui.apps.GridPlacement
import com.iblu01.portallauncher.ui.apps.GridSpan
import com.iblu01.portallauncher.ui.apps.GridSpec
import com.iblu01.portallauncher.ui.apps.PlacedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** One app page: launching, the item menu, and dropping an icon into a chosen cell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppGridPageTest {

    @get:Rule val rule = createComposeRule()

    private val spec = GridSpec(columns = 4, rows = 3)

    private fun item(label: String, pkg: String) = GridItem(
        key = GridItem.appKey(pkg, "$pkg.Main"),
        label = label,
        defaultLabel = label,
        icon = null,
        packageName = pkg,
        activityName = "$pkg.Main",
    )

    private fun placed(label: String, pkg: String, cell: GridCell) =
        PlacedItem(item(label, pkg), cell)

    @Test
    fun `tapping an icon launches it`() {
        val launched = mutableListOf<String>()
        rule.setContent {
            AppGridPage(
                page = 0,
                items = listOf(placed("Spotify", "com.spotify", GridCell(0, 0, 0))),
                spec = spec,
                drag = GridDragState(),
                onLaunch = { launched.add(it.packageName) },
                topInset = 0.dp,
            )
        }

        rule.onNodeWithText("Spotify").performClick()
        rule.waitForIdle()

        assertEquals(listOf("com.spotify"), launched)
    }

    @Test
    fun `only the items of this page are drawn`() {
        rule.setContent {
            AppGridPage(
                page = 0,
                items = listOf(
                    placed("Ici", "com.here", GridCell(page = 0, col = 0, row = 0)),
                    placed("Ailleurs", "com.there", GridCell(page = 1, col = 0, row = 0)),
                ),
                spec = spec,
                drag = GridDragState(),
                onLaunch = {},
                topInset = 0.dp,
            )
        }

        rule.onNodeWithText("Ici").assertExists()
        rule.onNodeWithText("Ailleurs").assertDoesNotExist()
    }

    @Test
    fun `long-pressing an icon opens its menu, anchored on its cell`() {
        var target: Triple<GridItem, GridSpan, IntRect>? = null
        val spotify = placed("Spotify", "com.spotify", GridCell(0, 0, 0))
        rule.setContent {
            AppGridPage(
                page = 0,
                items = listOf(spotify),
                spec = spec,
                drag = GridDragState(),
                onLaunch = {},
                onLongPress = { item, span, anchor -> target = Triple(item, span, anchor) },
                topInset = 0.dp,
            )
        }

        rule.onNodeWithText("Spotify").performTouchInput { longClick() }
        rule.waitForIdle()

        assertNotNull("a long-press must raise the item menu", target)
        assertEquals(spotify.item.key, target?.first?.key)
        assertTrue("the anchor must be the cell's rect", (target?.third?.height ?: 0) > 0)
    }

    @Test
    fun `long-pressing an empty cell opens the surface menu, not an item menu`() {
        var itemMenus = 0
        var surfaceMenus = 0
        rule.setContent {
            AppGridPage(
                page = 0,
                items = listOf(placed("Spotify", "com.spotify", GridCell(0, 0, 0))),
                spec = spec,
                drag = GridDragState(),
                onLaunch = {},
                onLongPress = { _, _, _ -> itemMenus++ },
                onLongPressEmpty = { surfaceMenus++ },
                topInset = 0.dp,
            )
        }

        // Bottom-right corner: nothing is placed there.
        rule.onNodeWithTag("appGridPage0").performTouchInput {
            longClick(Offset(width * 0.9f, height * 0.9f))
        }
        rule.waitForIdle()

        assertEquals("an empty cell has no item menu", 0, itemMenus)
        assertEquals("but it does own the surface menu", 1, surfaceMenus)
    }

    @Test
    fun `dragging an icon drops it in the cell it was released over`() {
        var dropped: Pair<String, GridPlacement?>? = null
        var pickedUp = 0
        val drag = GridDragState()
        val spotify = placed("Spotify", "com.spotify", GridCell(0, 0, 0))
        rule.setContent {
            AppGridPage(
                page = 0,
                items = listOf(spotify),
                spec = spec,
                drag = drag,
                onLaunch = {},
                onPickUp = { pickedUp++ },
                onDrop = { key, placement -> dropped = key to placement },
                topInset = 0.dp,
            )
        }

        rule.onNodeWithTag("appGridPage0").performTouchInput {
            val cellWidth = width / spec.columns.toFloat()
            val cellHeight = height / spec.rows.toFloat()
            // Start on the icon's cell, then hold two cells right and one down.
            val start = Offset(cellWidth / 2f, cellHeight / 2f)
            down(start)
            advanceEventTime(700) // past the long-press timeout
            moveTo(start + Offset(cellWidth * 2, cellHeight))
            advanceEventTime(16)
            moveTo(start + Offset(cellWidth * 2, cellHeight))
            up()
        }
        rule.waitForIdle()

        assertEquals(1, pickedUp)
        assertEquals(spotify.item.key, dropped?.first)
        assertEquals(
            "free placement: the icon lands exactly where released",
            GridCell(0, 2, 1),
            dropped?.second?.cell,
        )
    }

    @Test
    fun `the page sliding under the finger does not end the drag`() {
        var dropped: Pair<String, GridPlacement?>? = null
        var shift by mutableStateOf(0.dp)
        val spotify = placed("Spotify", "com.spotify", GridCell(0, 0, 0))
        rule.setContent {
            Box(Modifier.fillMaxSize().padding(start = shift)) {
                AppGridPage(
                    page = 0,
                    items = listOf(spotify),
                    spec = spec,
                    drag = GridDragState(),
                    onLaunch = {},
                    onDrop = { key, placement -> dropped = key to placement },
                    topInset = 0.dp,
                )
            }
        }

        val node = rule.onNodeWithTag("appGridPage0")
        var cellWidth = 0f
        var cellHeight = 0f
        node.performTouchInput {
            cellWidth = width / spec.columns.toFloat()
            cellHeight = height / spec.rows.toFloat()
            down(Offset(cellWidth / 2f, cellHeight / 2f))
            advanceEventTime(700) // long-press: the icon is armed
            // Past the drag slop, but still over its own cell.
            moveTo(Offset(cellWidth / 2f + 40f, cellHeight / 2f))
            advanceEventTime(16)
        }

        // A page flip slides the page while the finger stays down. Anything that rebuilt the
        // pointer-input node here used to cancel the drag mid-air.
        rule.runOnIdle { shift = 4.dp }

        node.performTouchInput {
            moveTo(Offset(cellWidth * 2.5f, cellHeight * 1.5f))
            advanceEventTime(16)
            up()
        }
        rule.waitForIdle()

        assertEquals(
            "the drag must survive the page moving and land where it was finally released",
            GridCell(0, 2, 1),
            dropped?.second?.cell,
        )
    }

    @Test
    fun `a page reports the cell grid its size affords`() {
        var reported: GridSpec? = null
        rule.setContent {
            AppGridPage(
                page = 0,
                items = emptyList(),
                spec = spec,
                drag = GridDragState(),
                onLaunch = {},
                onSpec = { reported = it },
                topInset = 0.dp,
            )
        }
        rule.waitForIdle()

        // Placement cannot be resolved without this: it is what turns a page size into cells.
        assertNotNull(reported)
        assertTrue(reported!!.columns >= 3)
        assertTrue(reported!!.rows >= 2)
    }

    @Test
    fun `an unregistered page is not offered as a drop target`() {
        val drag = GridDragState()
        assertNull(drag.geometry(0))
        assertNull("no page, no hover cell", drag.hoveredPlacement())
    }
}
