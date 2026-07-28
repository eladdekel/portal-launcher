package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.domain.model.PillDetail
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * Vacuum control: start / pause / stop / dock / locate, gated by the entity's
 * `supported_features`. Battery and status shown as read-only rows.
 */
@Composable
fun VacuumControl(chip: LauncherChip) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val cleaning = entity.state.lowercase() in setOf("cleaning", "returning")

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PanelModeButton("Démarrer", Icons.Outlined.PlayArrow, cleaning) {
                callService("vacuum", "start", chip.entityId)
            }
            if (entity.supports(VacuumFeature.PAUSE)) {
                PanelModeButton("Pause", Icons.Outlined.Pause, entity.state.equals("paused", true)) {
                    callService("vacuum", "pause", chip.entityId)
                }
            }
            if (entity.supports(VacuumFeature.STOP)) {
                PanelModeButton("Stop", Icons.Filled.Stop, false) {
                    callService("vacuum", "stop", chip.entityId)
                }
            }
            if (entity.supports(VacuumFeature.RETURN_HOME)) {
                PanelModeButton("Base", Icons.Outlined.Home, entity.state.equals("returning", true)) {
                    callService("vacuum", "return_to_base", chip.entityId)
                }
            }
            if (entity.supports(VacuumFeature.LOCATE)) {
                PanelModeButton("Localiser", Icons.Outlined.MyLocation, false) {
                    callService("vacuum", "locate", chip.entityId)
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        val battery = entity.attributes.optInt("battery_level", -1)
        val status = entity.attributes.optString("status").ifBlank { chip.value }
        if (status.isNotBlank()) PanelDetailRow(PillDetail("État", status))
        if (battery in 0..100) {
            Spacer(Modifier.height(8.dp))
            PanelDetailRow(PillDetail("Batterie", "$battery %"))
        }
        chip.details.forEach {
            Spacer(Modifier.height(8.dp))
            PanelDetailRow(it)
        }
    }
}
