package com.iblu01.portallauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import com.iblu01.portallauncher.ui.apps.GridCell
import com.iblu01.portallauncher.ui.apps.GridPlacement
import com.iblu01.portallauncher.ui.apps.GridSpan
import com.iblu01.portallauncher.ui.apps.GridSpec
import com.iblu01.portallauncher.ui.apps.cellAt
import com.iblu01.portallauncher.ui.apps.originForCentre

/** A laid-out app page: where its cell area sits in the root, and how many cells it holds. */
data class PageGeometry(val contentRect: Rect, val spec: GridSpec)

/**
 * The drag in flight, hoisted above the pager.
 *
 * It has to live above the pages because a drag can cross them: the icon is drawn by an overlay in
 * root coordinates, the page under the finger decides the target cell, and the pager may flip pages
 * mid-gesture. A page-local state could not survive any of that.
 */
class GridDragState {
    var draggedKey by mutableStateOf<String?>(null)
        private set
    var draggedIcon by mutableStateOf<ImageBitmap?>(null)
        private set
    var draggedLabel by mutableStateOf("")
        private set

    /** Footprint of what is being dragged: a widget covers several cells. */
    var draggedSpan by mutableStateOf(GridSpan())
        private set

    /** Current finger position, in root coordinates. */
    var pointer by mutableStateOf(Offset.Zero)

    /** Finger position relative to the dragged tile's top-left at pick-up. */
    private var grab = Offset.Zero
    private var tileSize = Size.Zero

    /** Page rectangles, written during layout. Plain map: only gestures and draw read it. */
    private val pages = HashMap<Int, PageGeometry>()

    /**
     * The pager's own bounds in the root. The edges that flip pages are *these*, not a page's:
     * pages slide, so an already-scrolled page's rectangle sits off-screen and its edges are
     * meaningless — reading one made the backward flip unreachable and the forward one fire
     * unconditionally.
     */
    var viewportRect: Rect = Rect.Zero

    val isDragging: Boolean get() = draggedKey != null

    fun registerPage(page: Int, geometry: PageGeometry) {
        pages[page] = geometry
    }

    fun unregisterPage(page: Int) {
        pages.remove(page)
    }

    fun geometry(page: Int): PageGeometry? = pages[page]

    fun start(
        key: String,
        icon: ImageBitmap?,
        label: String,
        tileRect: Rect,
        pointerRoot: Offset,
        span: GridSpan = GridSpan(),
    ) {
        draggedKey = key
        draggedIcon = icon
        draggedLabel = label
        draggedSpan = span
        tileSize = tileRect.size
        grab = pointerRoot - tileRect.topLeft
        pointer = pointerRoot
    }

    fun stop() {
        draggedKey = null
        draggedIcon = null
        draggedLabel = ""
    }

    /** Where the dragged tile is drawn, in root coordinates. */
    fun tileTopLeft(): Offset = pointer - grab

    fun tileSize(): Size = tileSize

    /** The tile's centre decides the target cell — the finger alone feels off by half a tile. */
    private fun tileCentre(): Offset = tileTopLeft() + Offset(tileSize.width / 2f, tileSize.height / 2f)

    /**
     * Where the dragged item would land, or null when it is over no page at all (between pages
     * mid-flip, or over the clock page).
     *
     * The cell under the item's centre is the *middle* of a multi-cell footprint, so the origin is
     * derived from it and nudged back onto the grid — otherwise a wide widget dropped near an edge
     * would hang off the page.
     */
    fun hoveredPlacement(): GridPlacement? {
        val centre = tileCentre()
        val (page, geometry) = pages.entries
            .firstOrNull { it.value.contentRect.contains(centre) }
            ?.let { it.key to it.value }
            ?: return null
        val local = centre - geometry.contentRect.topLeft
        val centreCell = cellAt(
            x = local.x,
            y = local.y,
            pageWidth = geometry.contentRect.width,
            pageHeight = geometry.contentRect.height,
            spec = geometry.spec,
            page = page,
        )
        return GridPlacement(originForCentre(centreCell, draggedSpan, geometry.spec), draggedSpan)
    }

    /** The cell under the dragged item's centre, ignoring its footprint. */
    fun hoveredCell(): GridCell? = hoveredPlacement()?.let { placement ->
        val geometry = pages[placement.cell.page] ?: return null
        val centre = tileCentre()
        val local = centre - geometry.contentRect.topLeft
        cellAt(
            x = local.x,
            y = local.y,
            pageWidth = geometry.contentRect.width,
            pageHeight = geometry.contentRect.height,
            spec = geometry.spec,
            page = placement.cell.page,
        )
    }

    /**
     * -1 / +1 when the tile is held against a page edge, 0 otherwise. Drives the page flip that
     * makes cross-page drags (and therefore new pages) possible.
     */
    fun edgeDirection(edgePx: Float): Int {
        if (viewportRect.width <= 0f) return 0
        val centre = tileCentre()
        return when {
            centre.x < viewportRect.left + edgePx -> -1
            centre.x > viewportRect.right - edgePx -> 1
            else -> 0
        }
    }
}

@Composable
fun rememberGridDragState(): GridDragState = remember { GridDragState() }
