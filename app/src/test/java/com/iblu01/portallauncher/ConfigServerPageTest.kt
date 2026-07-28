package com.iblu01.portallauncher

import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfigServerPageTest {
    private lateinit var server: ConfigServer
    private val http = OkHttpClient()
    private lateinit var token: String
    private fun base() = "http://127.0.0.1:${server.listeningPort}"

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = Prefs(ctx)
        token = prefs.webConfigToken
        server = ConfigServer(prefs, SettingsChangeBus(), ctx, 0)
        server.start()
    }
    @After fun tearDown() { server.stop() }

    @Test fun root_serves_html_with_token() {
        http.newCall(Request.Builder().url("${base()}/?token=$token").build()).execute().use {
            assertEquals(200, it.code)
            assertTrue(it.header("Content-Type")!!.startsWith("text/html"))
            assertTrue(it.body!!.string().contains("Portal Launcher"))
        }
    }
}
