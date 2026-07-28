package com.iblu01.portallauncher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.HaInstance
import com.iblu01.portallauncher.HaMdnsDiscovery
import com.iblu01.portallauncher.ui.components.HaDiscoveryDialog
import com.iblu01.portallauncher.ui.components.SettingsDivider
import com.iblu01.portallauncher.ui.components.SettingsInfoDialog
import com.iblu01.portallauncher.ui.components.SettingsRow
import com.iblu01.portallauncher.ui.components.SettingsSection
import com.iblu01.portallauncher.ui.components.SettingsStatusRow
import com.iblu01.portallauncher.ui.components.SettingsSubPageHeader
import com.iblu01.portallauncher.ui.components.SettingsTextField
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

/** Step-by-step help shown next to the access-key field. */
internal val accessKeyHelpLines = listOf(
    "1. Ouvre Home Assistant dans un navigateur (même adresse qu'ici).",
    "2. Clique sur ton nom d'utilisateur en bas à gauche.",
    "3. Va dans l'onglet « Sécurité ».",
    "4. Tout en bas : « Jetons d'accès de longue durée » → « Créer un jeton ».",
    "5. Donne-lui un nom (ex. Portal), copie le jeton et colle-le ici.",
)

/**
 * « Ma maison » — single friendly page for everything connection-related:
 * HA address + access key with a live status row, network auto-discovery,
 * and the MQTT broker folded under an "Avancé" section (pre-filled from the address).
 */
@Composable
fun HomeConnectionPage(
    uiState: SettingsUiState,
    haUrl: String,
    haToken: String,
    mqttHost: String,
    mqttPort: String,
    mqttUsername: String,
    mqttPassword: String,
    deviceName: String,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSelectInstance: (HaInstance) -> Unit,
    onMqttHostChange: (String) -> Unit,
    onMqttPortChange: (String) -> Unit,
    onMqttUsernameChange: (String) -> Unit,
    onMqttPasswordChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onTestHa: () -> Unit,
    onTestMqtt: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val discovered = remember { mutableStateListOf<HaInstance>() }
    var showDiscovery by remember { mutableStateOf(false) }
    var showKeyHelp by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    // Auto-scan the LAN while this page is on screen; always stop on dispose.
    DisposableEffect(Unit) {
        val discovery = HaMdnsDiscovery(context)
        discovery.start { instances ->
            discovered.clear()
            discovered.addAll(instances)
        }
        onDispose { discovery.stop() }
    }

    if (showDiscovery) {
        HaDiscoveryDialog(
            instances = discovered.toList(),
            onDismiss = { showDiscovery = false },
            onSelect = { instance ->
                onSelectInstance(instance)
                showDiscovery = false
            },
        )
    }

    if (showKeyHelp) {
        SettingsInfoDialog(
            title = "Où trouver ma clé ?",
            lines = accessKeyHelpLines,
            onDismiss = { showKeyHelp = false },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SettingsSubPageHeader(title = "Ma maison", onBack = onBack)
        Text(
            "Portal se connecte à ton serveur Home Assistant pour afficher l'état de ta maison.",
            style = AppleTypography.bodyLarge,
            color = AppleColors.secondary
        )

        SettingsSection(title = "HOME ASSISTANT") {
            val suggestion = discovered.firstOrNull()
            if (suggestion != null) {
                SettingsRow(
                    label = "Home Assistant détecté",
                    value = suggestion.name,
                    onClick = {
                        if (discovered.size == 1) onSelectInstance(suggestion) else showDiscovery = true
                    },
                )
                SettingsDivider()
            }
            SettingsTextField(
                label = "Adresse",
                value = haUrl,
                onValueChange = onUrlChange,
                placeholder = "http://homeassistant.local:8123"
            )
            SettingsDivider()
            SettingsRow(
                label = "Chercher sur le réseau",
                value = if (discovered.isEmpty()) "Recherche…" else "${discovered.size} trouvé(s)",
                onClick = { showDiscovery = true },
            )
            SettingsDivider()
            SettingsTextField(
                label = "Clé d'accès",
                value = haToken,
                onValueChange = onTokenChange,
                placeholder = "Colle ta clé ici",
                isPassword = true
            )
            SettingsDivider()
            SettingsRow(label = "Où trouver ma clé ?", onClick = { showKeyHelp = true })
            SettingsDivider()
            SettingsStatusRow(
                label = "Connexion",
                status = uiState.haTest,
                detail = uiState.haTestMessage,
                onClick = onTestHa,
            )
        }

        SettingsSection(title = "AVANCÉ") {
            SettingsRow(
                label = "Notifications entre appareils",
                value = if (showAdvanced) "Masquer" else "Afficher",
                onClick = { showAdvanced = !showAdvanced },
            )
        }

        AnimatedVisibility(visible = showAdvanced) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Pré-rempli depuis l'adresse de ta maison. Ne touche à ces réglages que si ton broker MQTT est sur une autre machine.",
                    style = AppleTypography.bodyMedium,
                    color = AppleColors.secondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                SettingsSection(title = "MQTT") {
                    SettingsTextField(
                        label = "Adresse du serveur",
                        value = mqttHost,
                        onValueChange = onMqttHostChange,
                        placeholder = "homeassistant.local"
                    )
                    SettingsDivider()
                    SettingsTextField(
                        label = "Port",
                        value = mqttPort,
                        onValueChange = onMqttPortChange,
                        placeholder = "1883",
                        keyboardType = KeyboardType.Number
                    )
                    SettingsDivider()
                    SettingsTextField(
                        label = "Identifiant",
                        value = mqttUsername,
                        onValueChange = onMqttUsernameChange,
                        placeholder = "optionnel"
                    )
                    SettingsDivider()
                    SettingsTextField(
                        label = "Mot de passe",
                        value = mqttPassword,
                        onValueChange = onMqttPasswordChange,
                        placeholder = "optionnel",
                        isPassword = true
                    )
                    SettingsDivider()
                    SettingsTextField(
                        label = "Nom de cet appareil",
                        value = deviceName,
                        onValueChange = onDeviceNameChange,
                        placeholder = "Portal"
                    )
                    SettingsDivider()
                    SettingsStatusRow(
                        label = "Connexion MQTT",
                        status = uiState.mqttTest,
                        detail = uiState.mqttTestMessage,
                        onClick = onTestMqtt,
                    )
                }
            }
        }
    }
}
