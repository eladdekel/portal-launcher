package com.iblu01.portallauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Owns the [ConfigServer] lifecycle. Foreground service (mirrors MqttBridgeService) so it
 * survives on the always-on kiosk. Started by the Settings toggle and by BootReceiver when
 * [Prefs.webConfigEnabled].
 */
class ConfigServerService : Service() {
    private var server: ConfigServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = Prefs(this)
        if (server == null) {
            runCatching {
                ConfigServer(prefs, SettingsChangeBus.get(), applicationContext, prefs.webConfigPort)
                    .also { it.start(); server = it }
                Log.i(TAG, "web config server on :${prefs.webConfigPort}")
            }.onFailure { Log.e(TAG, "server start failed", it) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { server?.stop() }
        server = null
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Web config", NotificationManager.IMPORTANCE_MIN)
            )
        }
    }

    private fun notification(): Notification =
        Notification.Builder(this, CHANNEL)
            .setContentTitle("Configuration web active")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build()

    companion object {
        private const val TAG = "PortalWebConfig"
        private const val CHANNEL = "portal_launcher_webconfig"
        private const val NOTIF_ID = 2

        fun start(context: Context) {
            val intent = Intent(context, ConfigServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConfigServerService::class.java))
        }
    }
}
