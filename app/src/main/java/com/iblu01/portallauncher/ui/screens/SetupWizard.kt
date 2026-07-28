package com.iblu01.portallauncher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.HaInstance
import com.iblu01.portallauncher.HaMdnsDiscovery
import com.iblu01.portallauncher.ui.components.ConnStatus
import com.iblu01.portallauncher.ui.components.PillButton
import com.iblu01.portallauncher.ui.components.SettingsDivider
import com.iblu01.portallauncher.ui.components.SettingsInfoDialog
import com.iblu01.portallauncher.ui.components.SettingsRow
import com.iblu01.portallauncher.ui.components.SettingsSection
import com.iblu01.portallauncher.ui.components.SettingsStatusRow
import com.iblu01.portallauncher.ui.components.SettingsTextField
import com.iblu01.portallauncher.ui.components.SettingsTile
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * First-run wizard shown when no access key is configured yet.
 * Step 0: find Home Assistant on the network (or type the address).
 * Step 1: paste the access key, verify.
 * Step 2: done — saves and lands on the main settings page.
 */
@Composable
fun SetupWizard(
    uiState: SettingsUiState,
    haUrl: String,
    haToken: String,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSelectInstance: (HaInstance) -> Unit,
    onTest: () -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var showKeyHelp by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val discovered = remember { mutableStateListOf<HaInstance>() }
    DisposableEffect(Unit) {
        val discovery = HaMdnsDiscovery(context)
        discovery.start { instances ->
            discovered.clear()
            discovered.addAll(instances)
        }
        onDispose { discovery.stop() }
    }

    // A successful test on step 1 ends the connection part.
    LaunchedEffect(uiState.haTest) {
        if (uiState.haTest == ConnStatus.OK && step == 1) step = 2
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
        Spacer(Modifier.height(24.dp))

        when (step) {
            0 -> {
                Text("Bienvenue", style = AppleTypography.headlineLarge, color = AppleColors.primary)
                Text(
                    "Commençons par trouver ta maison. Assure-toi que Home Assistant est allumé sur le même réseau.",
                    style = AppleTypography.bodyLarge,
                    color = AppleColors.secondary
                )

                discovered.forEach { instance ->
                    SettingsTile(
                        icon = Icons.Outlined.Cloud,
                        title = instance.name,
                        subtitle = instance.url,
                        onClick = {
                            onSelectInstance(instance)
                            step = 1
                        },
                    )
                }
                if (discovered.isEmpty()) {
                    Text(
                        "Recherche sur le réseau…",
                        style = AppleTypography.bodyMedium,
                        color = AppleColors.tertiary
                    )
                }

                SettingsSection(title = "OU SAISIS L'ADRESSE") {
                    SettingsTextField(
                        label = "Adresse",
                        value = haUrl,
                        onValueChange = onUrlChange,
                        placeholder = "http://homeassistant.local:8123"
                    )
                }

                PillButton(label = "Continuer", primary = true, onClick = { step = 1 })
                PillButton(label = "Configurer plus tard", onClick = onSkip)
            }

            1 -> {
                Text("Ta clé d'accès", style = AppleTypography.headlineLarge, color = AppleColors.primary)
                Text(
                    "Pour laisser Portal lire l'état de ta maison, colle une clé d'accès créée dans Home Assistant.",
                    style = AppleTypography.bodyLarge,
                    color = AppleColors.secondary
                )

                SettingsSection(title = "CLÉ D'ACCÈS") {
                    SettingsTextField(
                        label = "Clé d'accès",
                        value = haToken,
                        onValueChange = onTokenChange,
                        placeholder = "Colle ta clé ici",
                        isPassword = true
                    )
                    SettingsDivider()
                    SettingsRow(
                        label = "Où trouver ma clé ?",
                        onClick = { showKeyHelp = true }
                    )
                    SettingsDivider()
                    SettingsStatusRow(
                        label = "Connexion",
                        status = uiState.haTest,
                        detail = uiState.haTestMessage,
                        onClick = onTest,
                    )
                }

                PillButton(label = "Vérifier la connexion", primary = true, onClick = onTest)
                PillButton(label = "Retour", onClick = { step = 0 })
            }

            else -> {
                Spacer(Modifier.height(40.dp))
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Tout est prêt !", style = AppleTypography.headlineLarge, color = AppleColors.primary)
                    Text(
                        "Portal va maintenant afficher l'état de ta maison.",
                        style = AppleTypography.bodyLarge,
                        color = AppleColors.secondary
                    )
                    PillButton(label = "Commencer", primary = true, onClick = onFinish)
                }
            }
        }
    }
}
