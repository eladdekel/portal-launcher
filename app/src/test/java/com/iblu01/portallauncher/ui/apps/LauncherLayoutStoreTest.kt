package com.iblu01.portallauncher.ui.apps

import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Placement mutations and their persistence, against a real (Robolectric) SharedPreferences. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LauncherLayoutStoreTest {

    private lateinit var prefs: Prefs
    private lateinit var store: LauncherLayoutStore

    private val a = GridItem.appKey("com.a", "com.a.Main")
    private val b = GridItem.appKey("com.b", "com.b.Main")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("portal_launcher", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = Prefs(context)
        prefs.appPlacements = emptyList()
        prefs.appPlacementsSeeded = false
        store = LauncherLayoutStore(prefs, ShortcutIconStore(context), CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun `a drop is persisted immediately`() {
        store.place(a, GridCell(page = 1, col = 2, row = 1))

        assertEquals(GridPlacement(GridCell(1, 2, 1)), store.storedCells.value[a])
        // Written through, not kept in memory: this is a kiosk that rarely shuts down cleanly.
        assertEquals(1, prefs.appPlacements.single().page)
        assertEquals(2, prefs.appPlacements.single().col)
    }

    @Test
    fun `dropping onto an occupied cell swaps the two items`() {
        store.place(a, GridCell(0, 0, 0))
        store.place(b, GridCell(0, 1, 0))

        store.place(a, GridCell(0, 1, 0))

        // Free placement has no dense order to cascade along, so the occupant takes the vacated cell.
        assertEquals(GridPlacement(GridCell(0, 1, 0)), store.storedCells.value[a])
        assertEquals(GridPlacement(GridCell(0, 0, 0)), store.storedCells.value[b])
    }

    @Test
    fun `an item with no cell yet displaces the occupant to a free cell`() {
        store.place(b, GridCell(0, 0, 0))
        store.lastKnownSpec = GridSpec(4, 3)

        // `a` was never placed (a freshly installed app being arranged for the first time).
        store.place(a, GridCell(0, 0, 0))

        assertEquals(GridPlacement(GridCell(0, 0, 0)), store.storedCells.value[a])
        val moved = store.storedCells.value.getValue(b).cell
        assertEquals("the occupant must not be left overlapping", true, moved != GridCell(0, 0, 0))
    }

    @Test
    fun `hiding an item frees its cell, unhiding brings it back`() {
        store.place(a, GridCell(0, 2, 2))

        store.hide(a)
        assertEquals(null, store.storedCells.value[a])
        assertEquals(setOf(a), store.hiddenKeys.value)

        store.unhide(a)
        assertEquals(emptySet<String>(), store.hiddenKeys.value)
    }

    @Test
    fun `a pre-pages arrangement is converted to cells once`() {
        prefs.appOrder = listOf(a, b)

        store.seedFromLegacyOrder(GridSpec(columns = 2, rows = 2))

        assertEquals(GridCell(0, 0, 0), store.storedCells.value[a]?.cell)
        assertEquals(GridCell(0, 1, 0), store.storedCells.value[b]?.cell)

        // Never replayed: it would undo any arrangement made since.
        store.place(a, GridCell(1, 1, 1))
        store.seedFromLegacyOrder(GridSpec(columns = 2, rows = 2))
        assertEquals(GridCell(1, 1, 1), store.storedCells.value[a]?.cell)
    }

    @Test
    fun `resizing a widget keeps its origin and displaces what it now covers`() {
        val widget = GridItem.widgetKey(7)
        store.lastKnownSpec = GridSpec(4, 3)
        store.place(widget, GridCell(0, 0, 0), GridSpan(1, 1))
        store.place(a, GridCell(0, 1, 0))

        store.resize(widget, GridSpan(2, 1))

        assertEquals(GridPlacement(GridCell(0, 0, 0), GridSpan(2, 1)), store.storedCells.value[widget])
        // The icon sat where the widget just grew into: it moves, it is not covered over.
        val moved = store.storedCells.value.getValue(a).cell
        assertEquals("displaced out of the footprint", true, moved !in footprint(GridCell(0, 0, 0), GridSpan(2, 1)))
    }

    @Test
    fun `spans survive a round trip through preferences`() {
        val widget = GridItem.widgetKey(9)
        store.place(widget, GridCell(1, 2, 0), GridSpan(2, 2))

        // A new store on the same prefs is what a reboot looks like.
        val reopened = LauncherLayoutStore(
            prefs,
            ShortcutIconStore(ApplicationProvider.getApplicationContext()),
            CoroutineScope(Dispatchers.Unconfined),
        )

        assertEquals(GridPlacement(GridCell(1, 2, 0), GridSpan(2, 2)), reopened.storedCells.value[widget])
    }

    @Test
    fun `releasing a widget forgets its placement without hiding it`() {
        val widget = GridItem.widgetKey(7)
        store.place(widget, GridCell(0, 1, 1))

        store.forget(widget)

        assertEquals(null, store.storedCells.value[widget])
        assertEquals("a released widget is not a hidden app", emptySet<String>(), store.hiddenKeys.value)
    }

    @Test
    fun `a rename is stored, and clearing it falls back to the app's own name`() {
        store.rename(a, "Réveil", defaultLabel = "Alarme")
        assertEquals(mapOf(a to "Réveil"), prefs.appLabels)

        store.rename(a, "  ", defaultLabel = "Alarme")
        assertEquals(emptyMap<String, String>(), prefs.appLabels)

        store.rename(a, "Alarme", defaultLabel = "Alarme")
        assertEquals("storing the default as an override is pointless", emptyMap<String, String>(), prefs.appLabels)
    }
}
