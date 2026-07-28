package com.iblu01.portallauncher.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.PillCandidate
import com.iblu01.portallauncher.PillFamily
import com.iblu01.portallauncher.friendlyEntityState
import com.iblu01.portallauncher.ui.components.PillButton
import com.iblu01.portallauncher.ui.components.SettingsDivider
import com.iblu01.portallauncher.ui.components.SettingsRow
import com.iblu01.portallauncher.ui.components.SettingsSearchField
import com.iblu01.portallauncher.ui.components.SettingsSection
import com.iblu01.portallauncher.ui.components.SettingsSubPageHeader
import com.iblu01.portallauncher.ui.components.SettingsToggleSub
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * « Informations affichées » — pick what the launcher may surface on the home screen.
 * Home: search box + one row per family with an enabled count.
 * Each family opens a sub-page with its entities, live states and a bulk toggle.
 */
@Composable
fun PillsSettingsPage(
    uiState: SettingsUiState,
    onRefresh: () -> Unit,
    onSetEnabled: (List<PillCandidate>, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { onRefresh() }
    var search by remember { mutableStateOf("") }
    var openFamily by remember { mutableStateOf<PillFamily?>(null) }

    val enabledIds = uiState.pillRules.filter { it.enabled }.map { it.entityId }.toSet()

    AnimatedContent(
        targetState = openFamily,
        transitionSpec = {
            val dir = if (targetState != null) 1 else -1
            (slideInHorizontally { it * dir } + fadeIn()) togetherWith
                (slideOutHorizontally { -it * dir } + fadeOut())
        },
        label = "pillsSubPage"
    ) { family ->
        if (family == null) {
            PillsHomePage(
                uiState = uiState,
                enabledIds = enabledIds,
                search = search,
                onSearchChange = { search = it },
                onOpenFamily = { openFamily = it },
                onRefresh = onRefresh,
                onSetEnabled = onSetEnabled,
                onBack = onBack,
            )
        } else {
            PillFamilyPage(
                family = family,
                uiState = uiState,
                enabledIds = enabledIds,
                onSetEnabled = onSetEnabled,
                onBack = { openFamily = null },
            )
        }
    }
}

@Composable
private fun PillsHomePage(
    uiState: SettingsUiState,
    enabledIds: Set<String>,
    search: String,
    onSearchChange: (String) -> Unit,
    onOpenFamily: (PillFamily) -> Unit,
    onRefresh: () -> Unit,
    onSetEnabled: (List<PillCandidate>, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val query = search.trim().lowercase()
    val searching = query.isNotEmpty()
    val results = if (searching) uiState.pillCandidates.filter {
        it.label.lowercase().contains(query) || it.primary.entityId.lowercase().contains(query)
    } else emptyList()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SettingsSubPageHeader(title = "Informations affichées", onBack = onBack)
        Text(
            "Active ce que Portal peut afficher en haut de l'écran. Les informations les plus importantes remontent automatiquement.",
            style = AppleTypography.bodyLarge,
            color = AppleColors.secondary
        )

        when {
            uiState.pillLoading -> Text(
                "Chargement depuis ta maison…",
                color = AppleColors.secondary
            )
            uiState.pillError != null -> SettingsSection(title = "CONNEXION") {
                SettingsRow(
                    label = uiState.pillError ?: "Erreur",
                    value = "Réessayer",
                    onClick = onRefresh
                )
            }
            uiState.pillCandidates.isEmpty() -> Text(
                "Aucun appareil compatible détecté. Vérifie la connexion dans « Ma maison ».",
                color = AppleColors.secondary
            )
            else -> {
                SettingsSearchField(value = search, onValueChange = onSearchChange)

                if (searching) {
                    if (results.isEmpty()) {
                        Text("Aucun résultat pour « $search ».", color = AppleColors.secondary)
                    } else {
                        CandidateSection(
                            title = "RÉSULTATS",
                            candidates = results,
                            enabledIds = enabledIds,
                            onSetEnabled = onSetEnabled,
                        )
                    }
                } else {
                    SettingsSection(title = "CATÉGORIES") {
                        val families = PillFamily.values()
                            .map { f -> f to uiState.pillCandidates.filter { PillFamily.of(it.kind) == f } }
                            .filter { it.second.isNotEmpty() }
                        families.forEachIndexed { index, (family, candidates) ->
                            val enabled = candidates.count { it.primary.entityId in enabledIds }
                            SettingsRow(
                                label = family.label,
                                value = "$enabled sur ${candidates.size}",
                                onClick = { onOpenFamily(family) }
                            )
                            if (index != families.lastIndex) SettingsDivider()
                        }
                    }
                    PillButton(label = "Actualiser la liste", onClick = onRefresh)
                }
            }
        }
    }
}

@Composable
private fun PillFamilyPage(
    family: PillFamily,
    uiState: SettingsUiState,
    enabledIds: Set<String>,
    onSetEnabled: (List<PillCandidate>, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val candidates = uiState.pillCandidates.filter { PillFamily.of(it.kind) == family }
    val allEnabled = candidates.isNotEmpty() && candidates.all { it.primary.entityId in enabledIds }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SettingsSubPageHeader(title = family.label, onBack = onBack)
        CandidateSection(
            title = "ÉLÉMENTS DÉTECTÉS",
            candidates = candidates,
            enabledIds = enabledIds,
            bulkLabel = if (allEnabled) "Tout masquer" else "Tout afficher",
            onBulk = { onSetEnabled(candidates, !allEnabled) },
            onSetEnabled = onSetEnabled,
        )
    }
}

@Composable
private fun CandidateSection(
    title: String,
    candidates: List<PillCandidate>,
    enabledIds: Set<String>,
    onSetEnabled: (List<PillCandidate>, Boolean) -> Unit,
    bulkLabel: String? = null,
    onBulk: (() -> Unit)? = null,
) {
    SettingsSection(title = title, action = bulkLabel, onAction = onBulk) {
        candidates.forEachIndexed { index, candidate ->
            val state = friendlyEntityState(candidate.primary)
            val extras = candidate.related.mapNotNull { e ->
                when {
                    e.deviceClass == "battery" -> "batterie"
                    e.entityId.contains("cycle") -> "cycle"
                    e.entityId.contains("completion") -> "fin prévue"
                    else -> null
                }
            }.distinct()
            val sublabel = (listOf(state) + extras).joinToString(" · ")
            SettingsToggleSub(
                label = candidate.label,
                sublabel = sublabel,
                checked = candidate.primary.entityId in enabledIds,
                onCheckedChange = { onSetEnabled(listOf(candidate), it) }
            )
            if (index != candidates.lastIndex) SettingsDivider()
        }
    }
}
