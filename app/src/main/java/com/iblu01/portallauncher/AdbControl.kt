package com.iblu01.portallauncher

import android.content.Context
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

object AdbControl {
    private const val TAG = "AdbControl"

    fun getWifiIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.name.contains("wlan") || networkInterface.name.contains("eth")) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            return address.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get IP address", e)
        }
        return null
    }

    fun getAdbPort(): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "service.adb.tcp.port"))
            val port = process.inputStream.bufferedReader().use { it.readText() }.trim()
            if (port.isEmpty() || port == "-1" || port == "0") null else port
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get adb port", e)
            null
        }
    }

    fun setAdbPort(port: Int): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop service.adb.tcp.port $port && stop adbd && start adbd"))
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set adb port to $port via su", e)
            false
        }
    }

    fun enableAdb(context: Context, port: Int): Boolean {
        val ok = setAdbPort(port)
        if (ok) {
            val prefs = Prefs(context)
            prefs.adbEnabled = true
            prefs.adbPort = port
        }
        return ok
    }

    fun disableAdb(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop service.adb.tcp.port -1 && stop adbd && start adbd"))
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable adb via su", e)
            false
        }
    }

    fun disableAdb(context: Context): Boolean {
        val ok = disableAdb()
        if (ok) {
            val prefs = Prefs(context)
            prefs.adbEnabled = false
        }
        return ok
    }

    fun restoreOnBoot(context: Context) {
        val prefs = Prefs(context)
        if (prefs.adbEnabled) {
            Log.i(TAG, "Restoring ADB wireless on port ${prefs.adbPort}")
            setAdbPort(prefs.adbPort)
        }
    }

    fun rebootDevice(): Boolean {
        return try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reboot via su", e)
            false
        }
    }
}
