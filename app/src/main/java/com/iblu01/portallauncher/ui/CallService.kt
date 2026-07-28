package com.iblu01.portallauncher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.iblu01.portallauncher.HaEntity

/**
 * Injected Home Assistant service caller (design §8). Composables read it from the composition
 * instead of touching the `PillHub` singleton directly — the only path from UI to the data layer.
 * The default `data` arg keeps the ~40 call sites textually unchanged (3-arg calls stay valid).
 */
interface CallService {
    operator fun invoke(domain: String, service: String, entityId: String?, data: Map<String, Any>? = null)
}

/** Provided once at the launcher root as `vm::callService`; `error` guards accidental use outside. */
val LocalCallService = staticCompositionLocalOf<CallService> {
    error("LocalCallService not provided")
}

/**
 * Live raw HA states provided to the panel subtree (design §8), fed from the VM's
 * `uiState.latestStates`. Composables read this instead of `PillHub.latestStates`. A new map
 * instance arrives on every HA push; per-entity change-guarding lives in `rememberEntity`.
 */
val LocalHaStates = staticCompositionLocalOf<Map<String, HaEntity>> {
    error("LocalHaStates not provided")
}

/** entity_id -> area display name, provided at the launcher root (from the VM's snapshot).
 *  Read by the light-rooms grouping instead of touching the repository singleton. */
val LocalAreas = staticCompositionLocalOf<Map<String, String>> { emptyMap() }
