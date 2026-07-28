package com.iblu01.portallauncher

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutoReturnTimer(
    private val scope: CoroutineScope,
    private val prefs: Prefs,
    private val onAutoReturn: (() -> Unit)? = null,
    /** Injectable so the countdown can be driven by virtual time in tests. */
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(AutoReturnUiState())
    val state: StateFlow<AutoReturnUiState> = _state.asStateFlow()

    private var job: Job? = null
    /** Set by [start], cleared by [stop] — gates [onInteraction] so a touch can only *reset*. */
    private var armed = false
    private val countdownDurationMs = 5_000L

    val enabled: Boolean get() = prefs.autoReturnEnabled

    /**
     * Restarts a countdown that is already armed. It must NOT arm one: `dispatchTouchEvent` calls
     * this on every touch, and arming from there resurrected the timer right after [stop] had
     * disarmed it — which then auto-dismissed the AUTO media panel while music was still playing
     * (and, via `PanelEvent.Dismiss`, poisoned `dismissedAutoKey` so it never reopened).
     */
    fun onInteraction() {
        if (!enabled || !armed) return
        restart()
    }

    private fun restart() {
        job?.cancel()
        job = scope.launch {
            _state.update { AutoReturnUiState() }
            delay(prefs.autoReturnDelaySeconds * 1_000L)
            _state.update { it.copy(pillVisible = true) }
            val startMs = nowMs()
            while (isActive) {
                val elapsed = nowMs() - startMs
                val progress = (1f - elapsed.toFloat() / countdownDurationMs).coerceIn(0f, 1f)
                _state.update { it.copy(progress = progress) }
                if (progress <= 0f) break
                delay(16L)
            }
            _state.update { it.copy(shouldReturn = true, pillVisible = false) }
            onAutoReturn?.invoke()
        }
    }

    fun start() {
        if (!enabled) return
        armed = true
        restart()
    }

    fun stop() {
        armed = false
        job?.cancel()
        _state.update { AutoReturnUiState() }
    }
}

data class AutoReturnUiState(
    val pillVisible: Boolean = false,
    val progress: Float = 1f,
    val shouldReturn: Boolean = false,
)
