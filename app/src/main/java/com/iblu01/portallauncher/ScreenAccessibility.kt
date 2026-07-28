package com.iblu01.portallauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class ScreenAccessibility : AccessibilityService() {
    companion object {
        @Volatile var instance: ScreenAccessibility? = null
        private const val TAG = "PortalLauncher"
    }

    override fun onServiceConnected() {
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            notificationTimeout = 250
        }
        Log.i(TAG, "ScreenAccessibility connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Log.i(TAG, "ScreenAccessibility unbound")
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        DeviceStateHub.onForegroundPackage(event?.packageName?.toString(), this)
    }
    override fun onInterrupt() = Unit

    fun sleepNow() {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }
}
