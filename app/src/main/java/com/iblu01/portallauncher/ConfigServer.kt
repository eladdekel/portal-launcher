package com.iblu01.portallauncher

import android.content.Context
import android.graphics.BitmapFactory
import com.iblu01.portallauncher.ui.ConnectionStatus
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.File
import java.net.NetworkInterface
import java.security.MessageDigest

/**
 * Embedded HTTP config server. Delegates all persistence to [Prefs]; emits changed field names
 * on [SettingsChangeBus] so the running app re-reads/reconnects. Trusted-LAN only: HTTP, single
 * bearer token. Secrets are write-only — never returned.
 */
class ConfigServer(
    private val prefs: Prefs,
    private val bus: SettingsChangeBus,
    private val context: Context,
    port: Int,
) : NanoHTTPD(port) {

    private fun authOk(session: IHTTPSession): Boolean {
        val header = session.headers["authorization"]?.removePrefix("Bearer ")?.trim()
        val query = session.parameters["token"]?.firstOrNull()
        val provided = header ?: query ?: return false
        val expected = prefs.webConfigToken
        return MessageDigest.isEqual(
            provided.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8)
        )
    }

    private fun json(status: Response.Status, body: String): Response =
        newFixedLengthResponse(status, "application/json", body)

    private fun unauthorized(): Response =
        json(Response.Status.UNAUTHORIZED, """{"error":"unauthorized"}""")

    override fun serve(session: IHTTPSession): Response {
        if (!authOk(session)) return unauthorized()
        return when (session.uri) {
            "/api/settings" -> when (session.method) {
                Method.GET -> getSettings()
                Method.POST -> postSettings(session)
                else -> json(Response.Status.METHOD_NOT_ALLOWED, """{"error":"method"}""")
            }
            "/api/status" -> if (session.method == Method.GET) getStatus()
                else json(Response.Status.METHOD_NOT_ALLOWED, """{"error":"method"}""")
            "/api/wallpaper" -> when (session.method) {
                Method.POST -> postWallpaper(session)
                Method.DELETE -> deleteWallpaper()
                else -> json(Response.Status.METHOD_NOT_ALLOWED, """{"error":"method"}""")
            }
            "/", "/index.html" -> if (session.method == Method.GET) servePage()
                else json(Response.Status.METHOD_NOT_ALLOWED, """{"error":"method"}""")
            else -> json(Response.Status.NOT_FOUND, """{"error":"not_found"}""")
        }
    }

    private fun getSettings(): Response {
        val o = JSONObject()
            .put("deviceName", prefs.deviceName)
            .put("haUrl", prefs.haUrl)
            .put("haToken", if (prefs.haToken.isEmpty()) "" else MASK)
            .put("brokerHost", prefs.brokerHost)
            .put("brokerPort", prefs.brokerPort)
            .put("username", prefs.username)
            .put("password", if (prefs.password.isEmpty()) "" else MASK)
            .put("screenTimeoutEnabled", prefs.screenTimeoutEnabled)
            .put("screenTimeoutMinutes", prefs.screenTimeoutMinutes)
            .put("powerMode", prefs.powerMode.name)
            .put("tempOffset", prefs.tempOffset.toDouble())
            .put("tapThreshold", prefs.tapThreshold.toDouble())
            .put("backgroundMode", prefs.backgroundMode)
            .put("bgOverlayOpacity", prefs.bgOverlayOpacity.toDouble())
            .put("adbEnabled", prefs.adbEnabled)
            .put("adbPort", prefs.adbPort)
            .put("autoReturnEnabled", prefs.autoReturnEnabled)
            .put("autoReturnDelaySeconds", prefs.autoReturnDelaySeconds)
        return json(Response.Status.OK, o.toString())
    }

    private fun postSettings(session: IHTTPSession): Response {
        val map = HashMap<String, String>()
        session.parseBody(map)
        val raw = map["postData"] ?: return json(Response.Status.BAD_REQUEST, """{"error":"body"}""")
        val o = runCatching { JSONObject(raw) }.getOrNull()
            ?: return json(Response.Status.BAD_REQUEST, """{"error":"json"}""")

        // Persists + emits only when the incoming value actually differs from the current one,
        // so a save that merely repeats existing fields (e.g. the web UI's full-form submit)
        // doesn't trigger downstream reconnects (MQTT/HA) for untouched settings.
        fun <T> changed(key: String, current: T, decode: (JSONObject) -> T, apply: (T) -> Unit) {
            if (!o.has(key)) return
            val incoming = decode(o)
            if (incoming != current) { apply(incoming); bus.emit(key) }
        }
        fun changedSecret(key: String, current: () -> String, apply: (String) -> Unit) {
            if (!o.has(key)) return
            val v = o.getString(key)
            if (v == MASK || v.isEmpty() || v == current()) return
            apply(v)
            bus.emit(key)
        }
        val applied = runCatching {
            // scalars
            changed("deviceName", prefs.deviceName, { it.getString("deviceName") }) { prefs.deviceName = it }
            changed("haUrl", prefs.haUrl, { it.getString("haUrl") }) { prefs.haUrl = it }
            changed("brokerHost", prefs.brokerHost, { it.getString("brokerHost") }) { prefs.brokerHost = it }
            changed("brokerPort", prefs.brokerPort, { it.getInt("brokerPort") }) { prefs.brokerPort = it }
            changed("username", prefs.username, { it.getString("username") }) { prefs.username = it }
            changed("screenTimeoutEnabled", prefs.screenTimeoutEnabled, { it.getBoolean("screenTimeoutEnabled") }) { prefs.screenTimeoutEnabled = it }
            changed("screenTimeoutMinutes", prefs.screenTimeoutMinutes, { it.getInt("screenTimeoutMinutes") }) { prefs.screenTimeoutMinutes = it }
            changed("powerMode", prefs.powerMode, { PowerMode.from(it.getString("powerMode")) }) { prefs.powerMode = it }
            changed("tempOffset", prefs.tempOffset, { it.getDouble("tempOffset").toFloat() }) { prefs.tempOffset = it }
            changed("tapThreshold", prefs.tapThreshold, { it.getDouble("tapThreshold").toFloat() }) { prefs.tapThreshold = it }
            changed("backgroundMode", prefs.backgroundMode, { it.getString("backgroundMode") }) { prefs.backgroundMode = it }
            changed("bgOverlayOpacity", prefs.bgOverlayOpacity, { it.getDouble("bgOverlayOpacity").toFloat() }) { prefs.bgOverlayOpacity = it }
            changed("adbEnabled", prefs.adbEnabled, { it.getBoolean("adbEnabled") }) { prefs.adbEnabled = it }
            changed("adbPort", prefs.adbPort, { it.getInt("adbPort") }) { prefs.adbPort = it }
            changed("autoReturnEnabled", prefs.autoReturnEnabled, { it.getBoolean("autoReturnEnabled") }) { prefs.autoReturnEnabled = it }
            changed("autoReturnDelaySeconds", prefs.autoReturnDelaySeconds, { it.getInt("autoReturnDelaySeconds") }) { prefs.autoReturnDelaySeconds = it }
            // secrets: skip the mask sentinel and no-op values, otherwise replace
            changedSecret("haToken", { prefs.haToken }) { prefs.haToken = it }
            changedSecret("password", { prefs.password }) { prefs.password = it }
        }
        if (applied.isFailure) {
            return json(Response.Status.BAD_REQUEST, """{"error":"invalid_field_type"}""")
        }
        return json(Response.Status.OK, """{"ok":true}""")
    }

    private fun wallpaperFile() = File(context.filesDir, "wallpaper.jpg")

    private fun getStatus(): Response {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val o = JSONObject()
            .put("haConnected", ConnectionStatus.haConnected)
            .put("mqttConnected", ConnectionStatus.connected)
            .put("ip", localIp() ?: "")
            .put("deviceName", prefs.deviceName)
            .put("presence", DeviceStateHub.current.presence.name)
            .put("version", version)
        return json(Response.Status.OK, o.toString())
    }

    private fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress && it.hostAddress?.contains('.') == true }
            ?.hostAddress
    }.getOrNull()

    private fun postWallpaper(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val contentType = session.headers["content-type"] ?: ""
        if (!contentType.startsWith("multipart/", ignoreCase = true))
            return json(Response.Status.BAD_REQUEST, """{"error":"multipart_required"}""")
        val tmpPath = files["file"] ?: files.values.firstOrNull()
            ?: return json(Response.Status.BAD_REQUEST, """{"error":"no_file"}""")
        val tmp = File(tmpPath)
        if (tmp.length() > MAX_WALLPAPER_BYTES)
            return json(Response.Status.BAD_REQUEST, """{"error":"too_large"}""")
        // decode-verify it is a real bitmap
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tmp.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
            return json(Response.Status.BAD_REQUEST, """{"error":"not_an_image"}""")
        tmp.copyTo(wallpaperFile(), overwrite = true)
        prefs.backgroundMode = "custom"
        bus.emit("backgroundMode")
        return json(Response.Status.OK, """{"ok":true}""")
    }

    private fun deleteWallpaper(): Response {
        wallpaperFile().delete()
        if (prefs.backgroundMode == "custom") { prefs.backgroundMode = "neutral"; bus.emit("backgroundMode") }
        return json(Response.Status.OK, """{"ok":true}""")
    }

    private fun servePage(): Response {
        val html = context.assets.open("webconfig/index.html").bufferedReader().use { it.readText() }
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    companion object {
        const val MASK = "***set***"
        const val MAX_WALLPAPER_BYTES = 5L * 1024 * 1024
    }
}
