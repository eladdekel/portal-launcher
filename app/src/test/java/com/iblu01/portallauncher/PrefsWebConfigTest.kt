package com.iblu01.portallauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrefsWebConfigTest {
    private fun clearCache() {
        // Clear both cached and secured SharedPreferences
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("portal_launcher", Context.MODE_PRIVATE).edit().clear().apply()
        ctx.getSharedPreferences("portal_launcher_secure", Context.MODE_PRIVATE).edit().clear().apply()
        // Clear the Prefs static cache using reflection
        val cachedPlain = Prefs::class.java.getDeclaredField("cachedPlain").apply { isAccessible = true }
        val cachedSecure = Prefs::class.java.getDeclaredField("cachedSecure").apply { isAccessible = true }
        val companion = Prefs::class.java.getDeclaredField("Companion").apply { isAccessible = true }
        cachedPlain.set(null, null)
        cachedSecure.set(null, null)
    }

    private fun prefs(): Prefs {
        clearCache()
        return Prefs(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        clearCache()
    }

    @Test fun defaults() {
        val p = prefs()
        assertEquals(false, p.webConfigEnabled)
        assertEquals(8080, p.webConfigPort)
    }

    @Test fun port_is_coerced() {
        val p = prefs()
        p.webConfigPort = 80
        assertEquals(1024, p.webConfigPort)
        p.webConfigPort = 70000
        assertEquals(65535, p.webConfigPort)
    }

    @Test fun token_is_generated_once_and_stable() {
        val p = prefs()
        val first = p.webConfigToken
        assertTrue(first.length >= 16)
        assertEquals(first, p.webConfigToken)
    }

    @Test fun regenerate_changes_token() {
        val p = prefs()
        val old = p.webConfigToken
        val fresh = p.regenerateWebConfigToken()
        assertNotEquals(old, fresh)
        assertEquals(fresh, p.webConfigToken)
    }
}
