package com.iblu01.portallauncher

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfigServerTest {
    private lateinit var prefs: Prefs
    private lateinit var bus: SettingsChangeBus
    private lateinit var server: ConfigServer
    private val http = OkHttpClient()
    private lateinit var token: String
    private val json = "application/json".toMediaType()

    private fun base() = "http://127.0.0.1:${server.listeningPort}"

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        prefs = Prefs(ctx)
        token = prefs.webConfigToken
        bus = SettingsChangeBus()
        server = ConfigServer(prefs, bus, ctx, 0)
        server.start()
    }

    @After fun tearDown() { server.stop() }

    private fun get(path: String, auth: Boolean = true): okhttp3.Response {
        val b = Request.Builder().url(base() + path)
        if (auth) b.header("Authorization", "Bearer $token")
        return http.newCall(b.build()).execute()
    }

    private fun post(path: String, body: String): okhttp3.Response =
        http.newCall(
            Request.Builder().url(base() + path)
                .header("Authorization", "Bearer $token")
                .post(body.toRequestBody(json)).build()
        ).execute()

    @Test fun missing_token_is_401() {
        get("/api/settings", auth = false).use { assertEquals(401, it.code) }
    }

    @Test fun wrong_token_is_401() {
        http.newCall(
            Request.Builder().url(base() + "/api/settings")
                .header("Authorization", "Bearer nope").build()
        ).execute().use { assertEquals(401, it.code) }
    }

    @Test fun get_settings_masks_secrets() {
        prefs.haToken = "supersecret"
        get("/api/settings").use { resp ->
            val o = JSONObject(resp.body!!.string())
            assertEquals(ConfigServer.MASK, o.getString("haToken"))
            assertNotEquals("supersecret", o.getString("haToken"))
        }
    }

    @Test fun post_updates_scalar_and_coerces() {
        post("/api/settings", """{"deviceName":"Salon","brokerPort":70000}""").use {
            assertEquals(200, it.code)
        }
        assertEquals("Salon", prefs.deviceName)
        assertEquals(65535, prefs.brokerPort)
    }

    @Test fun post_mask_leaves_secret_unchanged() {
        prefs.haToken = "keepme"
        post("/api/settings", """{"haToken":"${ConfigServer.MASK}"}""").use {
            assertEquals(200, it.code)
        }
        assertEquals("keepme", prefs.haToken)
    }

    @Test fun post_real_secret_replaces_it() {
        prefs.haToken = "old"
        post("/api/settings", """{"haToken":"newtoken"}""").use {
            assertEquals(200, it.code)
        }
        assertEquals("newtoken", prefs.haToken)
    }

    @Test fun post_malformed_field_type_is_400_not_500() {
        post("/api/settings", """{"brokerPort":"abc"}""").use {
            assertEquals(400, it.code)
        }
    }

    @Test fun post_malformed_boolean_field_type_is_400_not_500() {
        post("/api/settings", """{"screenTimeoutMinutes":true}""").use {
            assertEquals(400, it.code)
        }
    }

    @Test fun post_unchanged_scalars_do_not_emit_but_changed_field_does() = runTest {
        prefs.brokerHost = "existing.host"
        prefs.brokerPort = 1883
        bus.changes.test {
            post(
                "/api/settings",
                """{"deviceName":"NewName","brokerHost":"existing.host","brokerPort":1883}"""
            ).use { assertEquals(200, it.code) }
            assertEquals("deviceName", awaitItem())
            expectNoEvents()
        }
        assertEquals("existing.host", prefs.brokerHost)
        assertEquals(1883, prefs.brokerPort)
        assertEquals("NewName", prefs.deviceName)
    }

    @Test fun post_changed_broker_port_does_emit() = runTest {
        prefs.brokerPort = 1883
        bus.changes.test {
            post("/api/settings", """{"brokerPort":1884}""").use { assertEquals(200, it.code) }
            assertEquals("brokerPort", awaitItem())
        }
        assertEquals(1884, prefs.brokerPort)
    }
}
