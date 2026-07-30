package com.iblu01.portallauncher.ui.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap

/** One app shortcut (manifest, dynamic or pinned) as surfaced in the long-press menu. */
data class AppShortcut(
    val id: String,
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

/**
 * Everything a launcher needs from the platform that the PackageManager cannot give: app shortcuts,
 * package-change callbacks, and the app-info / uninstall intents.
 *
 * **App shortcuts require being the device's default home.** `getShortcuts()` throws
 * `SecurityException` otherwise, so every call is gated on [isDefaultHome]
 * (`hasShortcutHostPermission()`), which is also what the UI uses to explain to the user why the
 * menu is short.
 */
class LauncherAppsFacade(private val context: Context) {

    private val launcherApps =
        context.applicationContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    private val user: UserHandle get() = Process.myUserHandle()

    /** True when Portal Launcher is the selected home app, the only state where shortcuts work. */
    val isDefaultHome: Boolean
        get() = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    /** Blocking + touches resources: call from [kotlinx.coroutines.Dispatchers.IO]. */
    fun shortcutsFor(packageName: String): List<AppShortcut> {
        if (!isDefaultHome) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
            .setPackage(packageName)
        val infos = runCatching { launcherApps.getShortcuts(query, user) }
            .onFailure { Log.w(TAG, "getShortcuts failed for $packageName", it) }
            .getOrNull()
            .orEmpty()
            .filterNotNull()
        val density = context.resources.displayMetrics.densityDpi
        return infos
            .filter { it.isEnabled }
            .map { info ->
                AppShortcut(
                    id = info.id,
                    packageName = info.`package`,
                    label = (info.longLabel ?: info.shortLabel)?.toString().orEmpty(),
                    icon = runCatching {
                        launcherApps.getShortcutIconDrawable(info, density)?.toImageBitmap()
                    }.getOrNull(),
                )
            }
            .filter { it.label.isNotBlank() }
    }

    fun startShortcut(packageName: String, shortcutId: String) {
        runCatching { launcherApps.startShortcut(packageName, shortcutId, null, null, user) }
            .onFailure { Log.w(TAG, "startShortcut failed for $packageName/$shortcutId", it) }
    }

    /** System apps have no uninstall flow, so the entry is hidden rather than failing on tap. */
    fun canUninstall(packageName: String): Boolean = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && packageName != context.packageName
    }.getOrDefault(false)

    /**
     * Package add/remove/change callbacks. Preferred over `ACTION_PACKAGE_*` broadcasts for a
     * launcher: it is the API meant for this, it covers managed profiles, and it also reports
     * availability changes on external storage.
     */
    fun registerPackageCallback(onChanged: () -> Unit): LauncherApps.Callback {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = onChanged()
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = onChanged()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = onChanged()
            override fun onPackagesAvailable(
                packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean,
            ) = onChanged()
            override fun onPackagesUnavailable(
                packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean,
            ) = onChanged()
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        return callback
    }

    fun unregisterPackageCallback(callback: LauncherApps.Callback) {
        runCatching { launcherApps.unregisterCallback(callback) }
    }

    companion object {
        private const val TAG = "LauncherApps"

        /** The system app-details screen — the launcher's "Infos de l'application". */
        fun appInfoIntent(packageName: String): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))

        fun uninstallIntent(packageName: String): Intent =
            Intent(Intent.ACTION_DELETE).setData(Uri.fromParts("package", packageName, null))

        /**
         * Launch intent for a specific launcher activity. Explicit component first (an app can
         * publish several launcher entries), falling back to the package's default entry point.
         */
        fun launchIntent(context: Context, packageName: String, activityName: String): Intent? {
            if (activityName.isNotEmpty()) {
                return Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName(packageName, activityName))
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            return context.packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
    }
}
