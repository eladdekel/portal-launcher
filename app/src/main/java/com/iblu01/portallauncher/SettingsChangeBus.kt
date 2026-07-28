package com.iblu01.portallauncher

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Signals that a setting was changed out-of-band (via the web config server), carrying the
 * camelCase JSON field name. Collectors re-read the relevant [Prefs] value and/or reconnect.
 * [Prefs] stays the single source of truth; this bus only says "re-read".
 */
class SettingsChangeBus {
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val changes: SharedFlow<String> = _changes.asSharedFlow()
    fun emit(key: String) { _changes.tryEmit(key) }

    companion object {
        @Volatile private var shared: SettingsChangeBus? = null
        /** Process-wide instance for non-Hilt call sites (services). */
        fun get(): SettingsChangeBus = shared ?: synchronized(this) {
            shared ?: SettingsChangeBus().also { shared = it }
        }
    }
}
