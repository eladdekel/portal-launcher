package com.iblu01.portallauncher

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Arming semantics. The bug: `dispatchTouchEvent` calls [AutoReturnTimer.onInteraction] on every
 * touch, and that used to *start* the countdown — so a stray touch resurrected the timer right
 * after `stop()` had disarmed it (AUTO media panel showing), and the timeout then dismissed the
 * media panel while music was still playing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AutoReturnTimerTest {

    private fun prefs(delaySeconds: Int = 10, enabled: Boolean = true): Prefs =
        Prefs(ApplicationProvider.getApplicationContext()).apply {
            autoReturnEnabled = enabled
            autoReturnDelaySeconds = delaySeconds
        }

    /** delay (10 s) + the 5 s countdown, plus slack for the 16 ms ticks. */
    private val pastCountdownMs = 20_000L

    @Test fun `interaction alone never arms the countdown`() = runTest {
        val timer = AutoReturnTimer(backgroundScope, prefs(), nowMs = { testScheduler.currentTime })
        timer.onInteraction()
        advanceTimeBy(pastCountdownMs)
        assertFalse(timer.state.value.shouldReturn)
    }

    @Test fun `start arms and the countdown completes`() = runTest {
        var returned = false
        val timer = AutoReturnTimer(backgroundScope, prefs(), { returned = true }, { testScheduler.currentTime })
        timer.start()
        advanceTimeBy(pastCountdownMs)
        assertTrue(timer.state.value.shouldReturn)
        assertTrue(returned)
    }

    @Test fun `stop disarms - a later touch cannot resurrect it`() = runTest {
        val timer = AutoReturnTimer(backgroundScope, prefs(), nowMs = { testScheduler.currentTime })
        timer.start()
        timer.stop()
        timer.onInteraction()
        advanceTimeBy(pastCountdownMs)
        assertFalse(timer.state.value.shouldReturn)
    }

    @Test fun `interaction resets a running countdown`() = runTest {
        val timer = AutoReturnTimer(backgroundScope, prefs(), nowMs = { testScheduler.currentTime })
        timer.start()
        advanceTimeBy(9_000)               // inside the pre-countdown delay
        timer.onInteraction()              // reset -> the 10 s delay starts over
        advanceTimeBy(9_000)
        assertFalse(timer.state.value.shouldReturn)
        advanceTimeBy(pastCountdownMs)
        assertTrue(timer.state.value.shouldReturn)
    }

    @Test fun `disabled timer never returns`() = runTest {
        val timer = AutoReturnTimer(backgroundScope, prefs(enabled = false), nowMs = { testScheduler.currentTime })
        timer.start()
        advanceTimeBy(pastCountdownMs)
        assertFalse(timer.state.value.shouldReturn)
    }
}
