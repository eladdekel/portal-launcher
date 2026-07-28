package com.iblu01.portallauncher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Compose-observable MQTT connection flag. Set by [com.iblu01.portallauncher.MqttBridgeService];
 * read by the ambient clock's connection dot. Purely a status mirror — it drives no logic.
 */
object ConnectionStatus {
    var connected by mutableStateOf(false)
    var haConnected by mutableStateOf(false)
}
