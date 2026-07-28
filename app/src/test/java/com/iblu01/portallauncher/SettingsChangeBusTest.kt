package com.iblu01.portallauncher

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsChangeBusTest {
    @Test fun emit_is_observed() = runTest {
        val bus = SettingsChangeBus()
        bus.changes.test {
            bus.emit("haUrl")
            assertEquals("haUrl", awaitItem())
        }
    }
}
