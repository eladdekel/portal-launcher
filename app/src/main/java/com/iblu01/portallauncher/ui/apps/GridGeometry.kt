package com.iblu01.portallauncher.ui.apps

import java.util.Locale
import kotlin.math.floor

/** How many cells a launcher page holds. Derived from the page size, like a device profile. */
data class GridSpec(val columns: Int, val rows: Int) {
    val cellsPerPage: Int get() = columns * rows
}

/** A cell of the launcher grid. [page] 0 is the first app page (the clock is not a grid page). */
data class GridCell(val page: Int, val col: Int, val row: Int)

/** How many cells an item covers. Icons are 1x1; widgets are whatever they ask for. */
data class GridSpan(val width: Int = 1, val height: Int = 1)

/** Where an item sits and how big it is. */
data class GridPlacement(val cell: GridCell, val span: GridSpan = GridSpan())

/** An item, resolved to a cell and a size. */
data class PlacedItem(val item: GridItem, val cell: GridCell, val span: GridSpan = GridSpan())

private const val MIN_COLUMNS = 3
private const val MIN_ROWS = 2

/**
 * Cells that fit in a page of [widthDp] x [heightDp].
 *
 * Kept as plain numbers (no Compose types) so the grid's arithmetic stays unit-testable.
 */
fun gridSpecFor(
    widthDp: Float,
    heightDp: Float,
    cellWidthDp: Float = 112f,
    cellHeightDp: Float = 116f,
): GridSpec = GridSpec(
    columns = floor(widthDp / cellWidthDp).toInt().coerceAtLeast(MIN_COLUMNS),
    rows = floor(heightDp / cellHeightDp).toInt().coerceAtLeast(MIN_ROWS),
)

/** Clamps a span to something that can exist on this grid at all. */
fun GridSpan.fitTo(spec: GridSpec): GridSpan = GridSpan(
    width = width.coerceIn(1, spec.columns),
    height = height.coerceIn(1, spec.rows),
)

/** Every cell an item at [cell] spanning [span] covers. */
fun footprint(cell: GridCell, span: GridSpan): List<GridCell> =
    (0 until span.height).flatMap { dy ->
        (0 until span.width).map { dx -> GridCell(cell.page, cell.col + dx, cell.row + dy) }
    }

/** True when the whole footprint is on the grid. */
fun fitsIn(cell: GridCell, span: GridSpan, spec: GridSpec): Boolean =
    cell.page >= 0 &&
        cell.col >= 0 && cell.row >= 0 &&
        cell.col + span.width <= spec.columns &&
        cell.row + span.height <= spec.rows

fun GridCell.isWithin(spec: GridSpec): Boolean = fitsIn(this, GridSpan(), spec)

/**
 * Resolves stored placements against what is actually installed and the grid that currently fits.
 *
 * Free placement means holes are legitimate, so nothing is compacted. What *cannot* survive is a
 * footprint that no longer fits (the grid shrank, e.g. on rotation) or two items overlapping: those
 * items are re-homed into the first free area of their size instead of being dropped or drawn on top
 * of each other. Items with no stored placement — a freshly installed app — sort last, so an install
 * never disturbs the existing arrangement.
 */
fun placeItems(
    items: List<GridItem>,
    stored: Map<String, GridPlacement>,
    spec: GridSpec,
): List<PlacedItem> {
    val taken = HashSet<GridCell>()
    val placed = ArrayList<PlacedItem>(items.size)
    val homeless = ArrayList<Pair<GridItem, GridSpan>>()

    // Reading order (page, row, col) so a conflict always resolves in favour of the same item,
    // whatever order the item list arrived in.
    val ordered = items.sortedWith(
        compareBy(
            { stored[it.key]?.cell?.page ?: Int.MAX_VALUE },
            { stored[it.key]?.cell?.row ?: 0 },
            { stored[it.key]?.cell?.col ?: 0 },
            { it.label.lowercase(Locale.getDefault()) },
        )
    )
    for (item in ordered) {
        val span = (stored[item.key]?.span ?: item.defaultSpan).fitTo(spec)
        val cell = stored[item.key]?.cell
        val cells = cell?.let { footprint(it, span) }
        if (cell != null && fitsIn(cell, span, spec) && cells!!.none { it in taken }) {
            taken += cells
            placed += PlacedItem(item, cell, span)
        } else {
            homeless += item to span
        }
    }
    for ((item, span) in homeless) {
        val free = firstFreeArea(taken, spec, span)
        taken += footprint(free, span)
        placed += PlacedItem(item, free, span)
    }
    return placed.sortedWith(compareBy({ it.cell.page }, { it.cell.row }, { it.cell.col }))
}

/**
 * Scans pages in reading order for the first place a [span]-sized item fits. Always terminates:
 * pages are free, and a span is clamped to the grid before it gets here.
 */
fun firstFreeArea(
    taken: Set<GridCell>,
    spec: GridSpec,
    span: GridSpan = GridSpan(),
    fromPage: Int = 0,
): GridCell {
    val fitted = span.fitTo(spec)
    var page = fromPage
    while (true) {
        for (row in 0..spec.rows - fitted.height) {
            for (col in 0..spec.columns - fitted.width) {
                val origin = GridCell(page, col, row)
                if (footprint(origin, fitted).none { it in taken }) return origin
            }
        }
        page++
    }
}

/** The item covering [cell], if any. */
fun List<PlacedItem>.coveringCell(cell: GridCell): PlacedItem? =
    firstOrNull { placed ->
        placed.cell.page == cell.page && footprint(placed.cell, placed.span).contains(cell)
    }

/**
 * How many app pages to show: every occupied page, plus a trailing empty one **only while an icon
 * is in hand** ([spare]).
 *
 * That empty page is what makes new pages reachable — dragging an icon onto it is how you create
 * one — but it is dead weight the rest of the time: an extra page dot and an extra swipe into
 * nothing. It appears with the drag and goes away with it.
 */
fun appPageCount(placed: List<PlacedItem>, spare: Boolean = false): Int {
    val occupied = (placed.maxOfOrNull { it.cell.page } ?: 0) + 1
    return if (spare) occupied + 1 else occupied
}

/**
 * Which cell a point inside a page falls in. Clamped, so a finger in the page's padding still
 * resolves to the nearest cell rather than to nothing.
 */
fun cellAt(
    x: Float,
    y: Float,
    pageWidth: Float,
    pageHeight: Float,
    spec: GridSpec,
    page: Int,
): GridCell {
    if (pageWidth <= 0f || pageHeight <= 0f) return GridCell(page, 0, 0)
    val cellWidth = pageWidth / spec.columns
    val cellHeight = pageHeight / spec.rows
    return GridCell(
        page = page,
        col = floor(x / cellWidth).toInt().coerceIn(0, spec.columns - 1),
        row = floor(y / cellHeight).toInt().coerceIn(0, spec.rows - 1),
    )
}

/** Top-left corner of [cell] inside a page of the given size, in the same unit as the inputs. */
fun cellOrigin(
    cell: GridCell,
    pageWidth: Float,
    pageHeight: Float,
    spec: GridSpec,
): Pair<Float, Float> {
    val cellWidth = pageWidth / spec.columns
    val cellHeight = pageHeight / spec.rows
    return cell.col * cellWidth to cell.row * cellHeight
}

/**
 * Where a dragged item of [span] should land so that its *centre* stays under the finger: the cell
 * under the centre is the middle of the footprint, not its corner, and the result is nudged back
 * onto the grid so a wide widget dropped near an edge still fits.
 */
fun originForCentre(centreCell: GridCell, span: GridSpan, spec: GridSpec): GridCell {
    val fitted = span.fitTo(spec)
    return GridCell(
        page = centreCell.page,
        col = (centreCell.col - (fitted.width - 1) / 2).coerceIn(0, spec.columns - fitted.width),
        row = (centreCell.row - (fitted.height - 1) / 2).coerceIn(0, spec.rows - fitted.height),
    )
}
