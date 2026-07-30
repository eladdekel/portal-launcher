package com.iblu01.portallauncher.ui.apps

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.iblu01.portallauncher.AppPlacement
import com.iblu01.portallauncher.PinnedShortcut
import com.iblu01.portallauncher.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.util.Locale

/** One thing on the app grid: an installed app, a pinned shortcut, or a bound widget. */
data class GridItem(
    val key: String,
    /** What is drawn: the user's override when there is one, otherwise [defaultLabel]. */
    val label: String,
    /** The name the app (or shortcut) declares, kept so a rename can be cleared back to it. */
    val defaultLabel: String,
    val icon: ImageBitmap?,
    val packageName: String,
    /** Launcher activity for apps, empty for shortcuts and widgets. */
    val activityName: String = "",
    /** Shortcut id for pinned shortcuts, empty otherwise. */
    val shortcutId: String = "",
    /** Host-allocated widget id, or [NO_WIDGET]. */
    val widgetId: Int = NO_WIDGET,
    /** Size the item asks for when it has never been placed. Icons are 1x1; widgets vary. */
    val defaultSpan: GridSpan = GridSpan(),
) {
    val isShortcut: Boolean get() = shortcutId.isNotEmpty()
    val isWidget: Boolean get() = widgetId != NO_WIDGET

    companion object {
        /** `AppWidgetManager.INVALID_APPWIDGET_ID`, without dragging the framework in here. */
        const val NO_WIDGET = 0

        fun appKey(packageName: String, activityName: String) = "app:$packageName/$activityName"
        fun shortcutKey(packageName: String, shortcutId: String) = "sc:$packageName#$shortcutId"
        fun widgetKey(widgetId: Int) = "wg:$widgetId"
    }
}

/**
 * Owns what the grid shows, on which page, and in which cell.
 *
 * Placement is **free**: an item keeps the exact cell it was dropped in, holes included, and pages
 * are created by dragging onto the trailing empty one. Nothing is compacted — see [placeItems] for
 * the only two cases that force an item to move (a cell that no longer exists, or two items
 * claiming the same one).
 *
 * Every mutation writes through to [Prefs] immediately, so a drop survives a reboot even though
 * this is a kiosk device that is rarely closed cleanly.
 */
class LauncherLayoutStore(
    private val prefs: Prefs,
    private val icons: ShortcutIconStore,
    private val scope: CoroutineScope,
) {
    private val placements = MutableStateFlow(prefs.appPlacements.toPlacementMap())
    private val hidden = MutableStateFlow(prefs.hiddenApps)
    private val labels = MutableStateFlow(prefs.appLabels)
    private val pinned = MutableStateFlow(prefs.pinnedShortcuts)

    /** Keys hidden from the grid, exposed so the restore list can offer them back. */
    val hiddenKeys: StateFlow<Set<String>> = hidden

    /**
     * The grid contents, before placement. Arranging is cheap, but shortcut icons are read from
     * disk, so the whole combine runs off the main thread.
     */
    fun items(
        apps: Flow<List<LaunchableApp>>,
        widgets: Flow<List<GridItem>>,
    ): StateFlow<List<GridItem>> =
        combine(apps, widgets, hidden, labels, pinned) { installed, widgets, hidden, labels, pinned ->
            val all = installed.map { it.toGridItem() } +
                pinned.map { it.toGridItem(icons.load(it.key())) } +
                widgets
            all.filterNot { it.key in hidden }
                .map { item -> labels[item.key]?.let { item.copy(label = it) } ?: item }
                .sortedBy { it.label.lowercase(Locale.getDefault()) }
        }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Stored placement per item key. Combine with [placeItems] for the resolved arrangement. */
    val storedCells: StateFlow<Map<String, GridPlacement>> = placements

    /**
     * The items currently hidden, resolved to real apps. Hiding is only safe to offer because this
     * exists: without it a hidden app would be unreachable short of clearing the app's data.
     */
    fun hiddenItems(apps: Flow<List<LaunchableApp>>): StateFlow<List<GridItem>> =
        combine(apps, hidden) { installed, hidden ->
            installed.map { it.toGridItem() }
                .filter { it.key in hidden }
                .sortedBy { it.label.lowercase(Locale.getDefault()) }
        }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Drops [key] into [cell]. An occupied cell **swaps** the two items: with free placement there
     * is no dense order to cascade along, and refusing the drop would just lose the gesture.
     */
    fun place(key: String, cell: GridCell, span: GridSpan = placements.value[key]?.span ?: GridSpan()) {
        val current = placements.value
        val target = GridPlacement(cell, span)
        if (current[key] == target) return
        val cells = footprint(cell, span).toSet()
        // Anything whose footprint overlaps the landing area is displaced, not overwritten.
        val displaced = current.filterKeys { it != key }
            .filterValues { footprint(it.cell, it.span).any { c -> c in cells } }
            .keys
        val next = current.toMutableMap()
        val previous = current[key]
        next[key] = target
        for (other in displaced) {
            val otherSpan = current.getValue(other).span
            // A straight swap only works when the two are the same size and the dragged item had a
            // cell of its own; otherwise the displaced item takes the first area that fits it.
            next[other] = if (previous != null && displaced.size == 1 && otherSpan == span) {
                GridPlacement(previous.cell, otherSpan)
            } else {
                GridPlacement(firstFreeAreaFor(next, other, otherSpan), otherSpan)
            }
        }
        commit(next)
    }

    /** Resizes an already-placed item, keeping its origin. Used by the widget resize frame. */
    fun resize(key: String, span: GridSpan) {
        val current = placements.value[key] ?: return
        place(key, current.cell, span)
    }

    private fun firstFreeAreaFor(
        map: Map<String, GridPlacement>,
        exclude: String,
        span: GridSpan,
    ): GridCell {
        val taken = map.filterKeys { it != exclude }
            .values
            .flatMap { footprint(it.cell, it.span) }
            .toSet()
        return firstFreeArea(taken, lastKnownSpec, span)
    }

    /**
     * The spec of the last laid-out page, needed only for the displaced-item fallback above.
     * Updated by the grid; a sane default keeps this usable before the first layout.
     */
    @Volatile
    var lastKnownSpec: GridSpec = GridSpec(4, 3)

    fun hide(key: String) {
        val next = placements.value.toMutableMap()
        next.remove(key)
        commit(next)
        hidden.value = (prefs.hiddenApps + key).also { prefs.hiddenApps = it }
    }

    fun unhide(key: String) {
        hidden.value = (prefs.hiddenApps - key).also { prefs.hiddenApps = it }
    }

    /** A blank or unchanged [label] clears the override rather than storing an empty string. */
    fun rename(key: String, label: String, defaultLabel: String) {
        val trimmed = label.trim()
        val next = prefs.appLabels.toMutableMap()
        if (trimmed.isEmpty() || trimmed == defaultLabel) next.remove(key) else next[key] = trimmed
        labels.value = next.also { prefs.appLabels = it }
    }

    fun pinShortcut(shortcut: PinnedShortcut) {
        if (pinned.value.any { it.key() == shortcut.key() }) return
        pinned.value = (prefs.pinnedShortcuts + shortcut).also { prefs.pinnedShortcuts = it }
    }

    /** Drops an item's placement without hiding it — used when a widget is released. */
    fun forget(key: String) {
        val next = placements.value.toMutableMap()
        if (next.remove(key) != null) commit(next)
    }

    fun removeShortcut(key: String) {
        pinned.value = prefs.pinnedShortcuts.filterNot { it.key() == key }
            .also { prefs.pinnedShortcuts = it }
        val next = placements.value.toMutableMap()
        next.remove(key)
        commit(next)
        icons.delete(key)
    }

    /**
     * Re-reads everything from [Prefs]. Needed because pin requests are accepted by a separate
     * activity (`PinShortcutActivity`) that writes to prefs behind this store's back.
     */
    fun reload() {
        placements.value = prefs.appPlacements.toPlacementMap()
        hidden.value = prefs.hiddenApps
        labels.value = prefs.appLabels
        pinned.value = prefs.pinnedShortcuts
    }

    /**
     * Converts a pre-pages arrangement (a dense ordered list) into cells, once, so upgrading does
     * not scatter an arrangement the user had already made.
     */
    fun seedFromLegacyOrder(spec: GridSpec) {
        if (prefs.appPlacementsSeeded) return
        prefs.appPlacementsSeeded = true
        val order = prefs.appOrder
        if (order.isEmpty() || placements.value.isNotEmpty()) return
        val seeded = order.mapIndexed { index, key ->
            val perPage = spec.cellsPerPage
            key to GridPlacement(
                GridCell(
                    page = index / perPage,
                    col = (index % perPage) % spec.columns,
                    row = (index % perPage) / spec.columns,
                )
            )
        }.toMap()
        commit(seeded)
    }

    private fun commit(cells: Map<String, GridPlacement>) {
        prefs.appPlacements = cells.map { (key, placement) ->
            AppPlacement(
                key = key,
                page = placement.cell.page,
                col = placement.cell.col,
                row = placement.cell.row,
                spanX = placement.span.width,
                spanY = placement.span.height,
            )
        }
        placements.value = cells
    }
}

private fun List<AppPlacement>.toPlacementMap(): Map<String, GridPlacement> =
    associate {
        it.key to GridPlacement(GridCell(it.page, it.col, it.row), GridSpan(it.spanX, it.spanY))
    }

internal fun LaunchableApp.toGridItem() = GridItem(
    key = GridItem.appKey(packageName, activityName),
    label = label,
    defaultLabel = label,
    icon = icon,
    packageName = packageName,
    activityName = activityName,
)

internal fun PinnedShortcut.key() = GridItem.shortcutKey(packageName, shortcutId)

internal fun PinnedShortcut.toGridItem(icon: ImageBitmap?) = GridItem(
    key = key(),
    label = label,
    defaultLabel = label,
    icon = icon,
    packageName = packageName,
    shortcutId = shortcutId,
)

/**
 * Icons of pinned shortcuts on disk.
 *
 * A `ShortcutInfo`'s icon cannot be re-read once the pin request is consumed, so it has to be
 * rasterized and kept at accept time.
 */
class ShortcutIconStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "shortcut_icons")

    private fun file(key: String) = File(dir, "${key.hashCode()}.png")

    fun save(key: String, bitmap: android.graphics.Bitmap) {
        runCatching {
            dir.mkdirs()
            file(key).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    fun load(key: String): ImageBitmap? = runCatching {
        val f = file(key)
        if (!f.exists()) return null
        android.graphics.BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
    }.getOrNull()

    fun delete(key: String) {
        runCatching { file(key).delete() }
    }
}
