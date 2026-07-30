package com.iblu01.portallauncher.ui.apps

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import com.iblu01.portallauncher.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import kotlin.math.ceil

/** One widget a provider offers, as shown in the picker. */
data class WidgetOffer(
    val provider: ComponentName,
    val label: String,
    val appLabel: String,
    val preview: ImageBitmap?,
    val minSpan: GridSpan,
) {
    val key: String get() = provider.flattenToShortString()
}

/**
 * Turns a widget provider's declared minimum size (in dp) into cells, rounding up: a widget that
 * does not get the cells it asks for renders clipped or blank.
 */
fun spanForMinSize(minWidthDp: Float, minHeightDp: Float, cellWidthDp: Float, cellHeightDp: Float): GridSpan {
    if (cellWidthDp <= 0f || cellHeightDp <= 0f) return GridSpan()
    return GridSpan(
        width = ceil(minWidthDp / cellWidthDp).toInt().coerceAtLeast(1),
        height = ceil(minHeightDp / cellHeightDp).toInt().coerceAtLeast(1),
    )
}

/**
 * Owns the launcher's `AppWidgetHost`: which widgets are bound, their views, and the add/remove
 * flow.
 *
 * Widgets are the one grid item the launcher does not own the content of — the host is a live
 * connection to another process, so it must be listening while we are visible and it must be told
 * when an id is thrown away, or the id leaks in the system for good.
 */
class WidgetHostController(
    private val context: Context,
    private val prefs: Prefs,
    scope: CoroutineScope,
) {
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val host = AppWidgetHost(context.applicationContext, HOST_ID)
    private val ids = MutableStateFlow(prefs.widgetIds)
    private var listening = false

    /**
     * Cell size of the current grid, in dp. Set by the grid; a widget's span can only be computed
     * against it. Defaults are harmless placeholders for the first layout.
     */
    @Volatile var cellWidthDp: Float = 112f
    @Volatile var cellHeightDp: Float = 116f

    /**
     * Bound widgets as grid items. An id whose provider is gone (app uninstalled) is dropped and
     * released, otherwise it would occupy a cell forever with nothing to draw.
     */
    val items: StateFlow<List<GridItem>> = ids
        .map { current ->
            val alive = ArrayList<GridItem>(current.size)
            val dead = ArrayList<Int>()
            for (id in current) {
                val info = infoFor(id)
                if (info == null) {
                    dead += id
                    continue
                }
                alive += info.toGridItem(id)
            }
            if (dead.isNotEmpty()) release(dead)
            alive
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun startListening() {
        if (listening) return
        runCatching { host.startListening() }
            .onSuccess { listening = true }
            .onFailure { Log.w(TAG, "startListening failed", it) }
    }

    fun stopListening() {
        if (!listening) return
        runCatching { host.stopListening() }
        listening = false
    }

    fun infoFor(widgetId: Int): AppWidgetProviderInfo? =
        runCatching { manager.getAppWidgetInfo(widgetId) }.getOrNull()

    /** Creates the widget's view. Never cache this across ids: the host owns its lifecycle. */
    fun createView(widgetId: Int): AppWidgetHostView? {
        val info = infoFor(widgetId) ?: return null
        return runCatching { host.createView(context, widgetId, info) }
            .onFailure { Log.w(TAG, "createView failed for $widgetId", it) }
            .getOrNull()
    }

    /** Everything installed that can be added, sorted by app then widget label. Blocking: use IO. */
    fun offers(): List<WidgetOffer> = runCatching {
        manager.installedProviders.mapNotNull { info ->
            val label = info.loadLabel(context.packageManager)?.toString().orEmpty()
            if (label.isBlank()) return@mapNotNull null
            WidgetOffer(
                provider = info.provider,
                label = label,
                appLabel = appLabelOf(info.provider.packageName),
                preview = runCatching {
                    info.loadPreviewImage(context, 0)?.toImageBitmap(PREVIEW_SIZE_PX)
                }.getOrNull(),
                minSpan = spanFor(info),
            )
        }.sortedWith(
            compareBy(
                { it.appLabel.lowercase(Locale.getDefault()) },
                { it.label.lowercase(Locale.getDefault()) },
            )
        )
    }.getOrDefault(emptyList())

    fun spanFor(info: AppWidgetProviderInfo): GridSpan =
        spanForMinSize(
            minWidthDp = info.minWidth / context.resources.displayMetrics.density,
            minHeightDp = info.minHeight / context.resources.displayMetrics.density,
            cellWidthDp = cellWidthDp,
            cellHeightDp = cellHeightDp,
        )

    fun allocateId(): Int = host.allocateAppWidgetId()

    /**
     * Binds without asking. Fails when the user has not granted the launcher the right to bind
     * widgets, which is not a permission an app can hold — only the user can allow it, through
     * [bindConsentIntent].
     */
    fun bindIfAllowed(widgetId: Int, provider: ComponentName): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(widgetId, provider) }.getOrDefault(false)

    fun bindConsentIntent(widgetId: Int, provider: ComponentName): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)

    /** Providers can demand a configuration screen before they are usable. */
    fun needsConfigure(widgetId: Int): Boolean = infoFor(widgetId)?.configure != null

    fun startConfigure(activity: android.app.Activity, widgetId: Int, requestCode: Int) {
        runCatching { host.startAppWidgetConfigureActivityForResult(activity, widgetId, 0, requestCode, null) }
            .onFailure { Log.w(TAG, "configure activity failed for $widgetId", it) }
    }

    /** Remembers a bound, configured widget. */
    fun keep(widgetId: Int) {
        if (widgetId == GridItem.NO_WIDGET || widgetId in ids.value) return
        ids.value = (prefs.widgetIds + widgetId).also { prefs.widgetIds = it }
    }

    /** Gives an id back to the system. Forgetting this leaks the id and the provider keeps updating. */
    fun release(widgetIds: List<Int>) {
        if (widgetIds.isEmpty()) return
        widgetIds.forEach { runCatching { host.deleteAppWidgetId(it) } }
        ids.value = (prefs.widgetIds - widgetIds.toSet()).also { prefs.widgetIds = it }
    }

    fun release(widgetId: Int) = release(listOf(widgetId))

    fun reload() {
        ids.value = prefs.widgetIds
    }

    private fun appLabelOf(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun AppWidgetProviderInfo.toGridItem(widgetId: Int): GridItem {
        val label = loadLabel(context.packageManager)?.toString().orEmpty()
        return GridItem(
            key = GridItem.widgetKey(widgetId),
            label = label,
            defaultLabel = label,
            icon = null,
            packageName = provider.packageName,
            widgetId = widgetId,
            defaultSpan = spanFor(this),
        )
    }

    companion object {
        private const val TAG = "WidgetHost"
        /** Any stable non-zero value; ids are scoped to it. */
        const val HOST_ID = 0x504F52 // "POR"
        private const val PREVIEW_SIZE_PX = 320
    }
}
