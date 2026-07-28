package com.iblu01.portallauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.domain.model.PillDetail
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Headless proof that a chip-consuming composable SKIPS recomposition when handed an equal
 * `LauncherChip` — even though `LauncherChip` carries a `List<PillDetail>`. This is what makes
 * the `@Immutable` bandaid unnecessary under Compose strong-skipping (Kotlin 2.0.20): equal
 * chips (data-class `equals`) don't recompose an open panel, so the alarm keypad stays smooth.
 * If this passes, removing `@Immutable` is safe; it guards against regressing that removal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChipSkippabilityTest {

    @get:Rule val rule = createComposeRule()

    private fun chip(value: String, details: List<PillDetail>) =
        LauncherChip(id = "a", icon = "i", label = "l", value = value, entityId = "light.a",
            kind = PillKind.GENERIC, details = details)

    @Composable
    private fun ChipConsumer(chip: LauncherChip, bodyRuns: AtomicInteger) {
        bodyRuns.incrementAndGet()   // runs once per non-skipped (re)composition
        androidx.compose.material3.Text(chip.value + chip.details.size)
    }

    @Test
    fun `equal chip skips recomposition and changed chip recomposes`() {
        val runs = AtomicInteger(0)
        val details = listOf(PillDetail("x", "1"))
        var chip by mutableStateOf(chip("on", details))
        // an unrelated state, toggled to force the parent scope to recompose each time
        var tick by mutableStateOf(0)

        rule.setContent {
            tick // read so the content scope is invalidated when tick changes
            ChipConsumer(chip, runs)
        }
        rule.waitForIdle()
        val afterInitial = runs.get()

        // Parent recomposes (tick changes) but the chip is equal → consumer must SKIP.
        tick = 1
        rule.waitForIdle()
        assertEquals("equal chip must not recompose the consumer", afterInitial, runs.get())

        // A new equal-by-value instance (fresh list) → still equal → still skips.
        chip = chip("on", listOf(PillDetail("x", "1")))
        tick = 2
        rule.waitForIdle()
        assertEquals("equal-by-value chip must still skip", afterInitial, runs.get())

        // A real change → consumer MUST recompose.
        chip = chip("off", details)
        rule.waitForIdle()
        assertEquals(afterInitial + 1, runs.get())
    }
}
