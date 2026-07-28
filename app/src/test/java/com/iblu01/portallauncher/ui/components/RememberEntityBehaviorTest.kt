package com.iblu01.portallauncher.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.ui.LocalHaStates
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Headless Android/Compose validation (Robolectric — no emulator) of the alarm-keypad anti-storm
 * guarantee (Finding 8, the migration's highest regression risk): [rememberEntity] must return the
 * SAME entity instance across unrelated HA pushes (so consumers skip and an open keypad doesn't
 * lag), and a NEW instance only when the observed entity actually changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RememberEntityBehaviorTest {

    @get:Rule val rule = createComposeRule()

    private fun entity(id: String, state: String) = HaEntity(id, state, JSONObject())

    @Test
    fun `rememberEntity freezes identity across unrelated pushes, changes on real change`() {
        val seen = mutableListOf<HaEntity?>()
        var states by mutableStateOf(mapOf("light.a" to entity("light.a", "on")))

        rule.setContent {
            CompositionLocalProvider(LocalHaStates provides states) {
                seen.add(rememberEntity("light.a"))
            }
        }
        rule.waitForIdle()

        // Unrelated entity changes → observed entity's instance must be reused (guard holds).
        states = states + ("light.b" to entity("light.b", "on"))
        rule.waitForIdle()

        // Observed entity changes → a new instance is emitted.
        states = mapOf("light.a" to entity("light.a", "off"))
        rule.waitForIdle()

        val distinct = seen.distinct()
        // Exactly two distinct instances ever observed: the initial "on", then the "off".
        assertEquals("only a real change should yield a new instance", 2, distinct.size)
        assertSame("unrelated push must not swap the instance", seen[0], seen[1])
        assertTrue(seen[0]?.state == "on")
        assertTrue(seen.last()?.state == "off")
    }
}
