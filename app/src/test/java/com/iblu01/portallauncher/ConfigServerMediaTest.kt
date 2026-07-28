package com.iblu01.portallauncher

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.ui.ConnectionStatus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ConfigServerMediaTest {
    private lateinit var ctx: android.content.Context
    private lateinit var prefs: Prefs
    private lateinit var server: ConfigServer
    private val http = OkHttpClient()
    private lateinit var token: String

    private fun base() = "http://127.0.0.1:${server.listeningPort}"
    private fun wallpaperFile() = File(ctx.filesDir, "wallpaper.jpg")

    @Before fun setUp() {
        // Robolectric's BitmapFactory shadow defaults to "allow invalid image data" (legacy
        // compat), fabricating a 100x100 bitmap for any bytes. Disable that so undecodable/
        // non-image payloads actually fail to decode, matching real Android BitmapFactory.
        ShadowBitmapFactory.setAllowInvalidImageData(false)
        ctx = ApplicationProvider.getApplicationContext()
        prefs = Prefs(ctx)
        token = prefs.webConfigToken
        server = ConfigServer(prefs, SettingsChangeBus(), ctx, 0)
        server.start()
    }

    @After fun tearDown() { server.stop(); wallpaperFile().delete() }

    private fun pngBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun uploadWallpaper(bytes: ByteArray, contentType: String): okhttp3.Response {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "w.png", bytes.toRequestBody(contentType.toMediaType()))
            .build()
        return http.newCall(
            Request.Builder().url(base() + "/api/wallpaper")
                .header("Authorization", "Bearer $token").post(body).build()
        ).execute()
    }

    @Test fun status_reports_fields() {
        ConnectionStatus.connected = true
        val resp = http.newCall(
            Request.Builder().url(base() + "/api/status")
                .header("Authorization", "Bearer $token").build()
        ).execute()
        resp.use {
            assertEquals(200, it.code)
            val o = JSONObject(it.body!!.string())
            assertEquals(true, o.getBoolean("mqttConnected"))
            assertTrue(o.has("haConnected"))
            assertTrue(o.has("ip"))
            assertTrue(o.has("version"))
        }
    }

    @Test fun status_requires_auth() {
        http.newCall(Request.Builder().url(base() + "/api/status").build()).execute()
            .use { assertEquals(401, it.code) }
    }

    @Test fun valid_png_is_accepted_and_sets_mode() {
        uploadWallpaper(pngBytes(), "image/png").use { assertEquals(200, it.code) }
        assertTrue(wallpaperFile().exists())
        assertEquals("custom", prefs.backgroundMode)
    }

    @Test fun non_image_is_rejected() {
        uploadWallpaper("hello".toByteArray(), "text/plain").use { assertEquals(400, it.code) }
        assertFalse(wallpaperFile().exists())
    }

    @Test fun undecodable_image_is_rejected() {
        uploadWallpaper("notreallyapng".toByteArray(), "image/png").use { assertEquals(400, it.code) }
        assertFalse(wallpaperFile().exists())
    }

    @Test fun delete_reverts_mode() {
        uploadWallpaper(pngBytes(), "image/png").use { assertEquals(200, it.code) }
        http.newCall(
            Request.Builder().url(base() + "/api/wallpaper")
                .header("Authorization", "Bearer $token").delete().build()
        ).execute().use { assertEquals(200, it.code) }
        assertFalse(wallpaperFile().exists())
        assertEquals("neutral", prefs.backgroundMode)
    }
}
