package com.iblu01.portallauncher

import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log

object ScreenControl {
    private const val TAG = "PortalLauncher"

    fun wake(context: Context) {
        @Suppress("DEPRECATION")
        val wl = context.getSystemService(PowerManager::class.java)
            .newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "PortalLauncher:Wake"
            )
        wl.acquire(3_000L)
        Log.i(TAG, "wake: FULL_WAKE_LOCK acquired")
        SleepScheduler.apply(context)
    }

    fun sleep(context: Context? = null) {
        val svc = ScreenAccessibility.instance
        if (svc != null) {
            svc.sleepNow()
            context?.let {
                DeviceStateHub.onScreenSleepRequested(it)
                SleepScheduler.cancel(it)
            }
        } else {
            Log.w(TAG, "sleep: accessibility service not running — is it enabled?")
        }
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = "${context.packageName}/${ScreenAccessibility::class.java.name}"
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(flat)
        for (component in splitter) {
            if (component.equals(target, ignoreCase = true)) return true
        }
        return false
    }

    /**
     * Auto-enables our AccessibilityService via WRITE_SECURE_SETTINGS (granted
     * by `adb shell pm grant com.iblu01.portallauncher android.permission.WRITE_SECURE_SETTINGS`).
     * Returns true if successful.
     */
    fun enableAccessibility(context: Context): Boolean {
        if (context.packageManager.checkPermission(
                "android.permission.WRITE_SECURE_SETTINGS",
                context.packageName
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "enableAccessibility: WRITE_SECURE_SETTINGS not granted")
            return false
        }
        return try {
            val target = "${context.packageName}/${ScreenAccessibility::class.java.name}"
            val current = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            if (!current.contains(target)) {
                val updated = if (current.isEmpty()) target else "$current:$target"
                Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    updated
                )
            }
            Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1
            )
            Log.i(TAG, "enableAccessibility: enabled $target")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "enableAccessibility: write failed", e)
            false
        }
    }

    fun hasWriteSecureSettings(context: Context) =
        context.packageManager.checkPermission(
            "android.permission.WRITE_SECURE_SETTINGS",
            context.packageName
        ) == PackageManager.PERMISSION_GRANTED
}
