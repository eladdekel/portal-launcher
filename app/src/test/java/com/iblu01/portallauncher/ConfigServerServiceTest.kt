package com.iblu01.portallauncher

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfigServerServiceTest {
    @Test fun service_starts_server_when_enabled() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Prefs(ctx).apply { webConfigEnabled = true; webConfigPort = 0 }
        val controller = Robolectric.buildService(ConfigServerService::class.java).create()
        controller.get().onStartCommand(Intent(), 0, 1)
        assertNotNull(controller.get())
        controller.destroy()
    }
}
