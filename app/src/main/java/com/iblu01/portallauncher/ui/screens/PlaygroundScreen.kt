package com.iblu01.portallauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Bathtub
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Blinds
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.ui.components.appleClickable
import com.iblu01.portallauncher.ui.components.controls.AccessoryGrid
import com.iblu01.portallauncher.ui.components.controls.AccessoryItem
import com.iblu01.portallauncher.ui.components.controls.ControlContentLayout
import com.iblu01.portallauncher.ui.components.controls.FillOrigin
import com.iblu01.portallauncher.ui.components.controls.PinKeypad
import com.iblu01.portallauncher.ui.components.controls.ThermostatArc
import com.iblu01.portallauncher.ui.components.controls.ThermostatMode
import com.iblu01.portallauncher.ui.components.controls.VacuumMode
import com.iblu01.portallauncher.ui.components.controls.VacuumRoom
import com.iblu01.portallauncher.ui.components.controls.VacuumRoomChips
import com.iblu01.portallauncher.ui.components.controls.VacuumRunButton
import com.iblu01.portallauncher.ui.components.controls.VacuumStatusChip
import com.iblu01.portallauncher.ui.components.controls.frLabel
import com.iblu01.portallauncher.ui.components.controls.VerticalColorTempSlider
import com.iblu01.portallauncher.ui.components.controls.VerticalFillSlider
import com.iblu01.portallauncher.ui.components.controls.WheelPicker
import com.iblu01.portallauncher.ui.components.controls.controlSize
import com.iblu01.portallauncher.ui.components.controls.VerticalSegmentedSelector
import com.iblu01.portallauncher.ui.components.controls.VerticalSwitch
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

/** Named accents so adaptivity is obvious at a glance. */
private val accentSwatches = listOf(
    "Bleu" to AppleColors.accent,
    "Vert" to Color(0xFF30D158),
    "Menthe" to Color(0xFF63E6BE),
    "Jaune" to AppleColors.warning,
    "Rouge" to AppleColors.error,
    "Violet" to Color(0xFFAF52DE),
    "Gris" to Color(0xFFD8D8DA),
)

private enum class Presence { HOME, AWAY, OFF }

/**
 * Dev-only gallery: every reusable control from
 * [com.iblu01.portallauncher.ui.components.controls], driven live by a single accent so the
 * theme-adaptivity is visible. Reached from the home-screen long-press menu.
 */
@Composable
fun PlaygroundScreen(onBack: () -> Unit) {
    var accent by remember { mutableStateOf(accentSwatches.first().second) }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppleColors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AppleColors.frostedFill)
                    .border(0.5.dp, AppleColors.frostedBorder, CircleShape)
                    .appleClickable(onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = AppleColors.primary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Composants", style = AppleTypography.headlineLarge, color = AppleColors.primary)
                Text("Banc d'essai des contrôles", style = AppleTypography.bodySmall, color = AppleColors.secondary)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Accent picker — the whole point: everything below re-skins instantly.
        Text("Couleur d'accent", style = AppleTypography.bodySmall, color = AppleColors.secondary)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            accentSwatches.forEach { (name, color) ->
                val active = color == accent
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                if (active) 3.dp else 0.5.dp,
                                if (active) AppleColors.primary else AppleColors.frostedBorder,
                                CircleShape,
                            )
                            .appleClickable { accent = color },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(name, style = AppleTypography.labelSmall, color = if (active) AppleColors.primary else AppleColors.tertiary)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // 1 · Fill sliders
        SectionTitle("Curseur vertical")
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            var bottom by remember { mutableFloatStateOf(0.45f) }
            var top by remember { mutableFloatStateOf(0.45f) }
            var disabled by remember { mutableFloatStateOf(0.7f) }
            LabeledControl("Depuis le bas") {
                VerticalFillSlider(
                    value = bottom, onValueChange = { bottom = it },
                    origin = FillOrigin.BOTTOM, accent = accent,
                    icon = Icons.Filled.WbSunny,
                    modifier = Modifier.controlSize(),
                )
            }
            LabeledControl("Depuis le haut") {
                VerticalFillSlider(
                    value = top, onValueChange = { top = it },
                    origin = FillOrigin.TOP, accent = accent,
                    modifier = Modifier.controlSize(),
                )
            }
            LabeledControl("Désactivé") {
                VerticalFillSlider(
                    value = disabled, onValueChange = { disabled = it },
                    accent = accent, enabled = false,
                    modifier = Modifier.controlSize(),
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // 2 · Gradient sliders
        SectionTitle("Curseur dégradé")
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            var kelvin by remember { mutableIntStateOf(4000) }
            var disabledKelvin by remember { mutableIntStateOf(3000) }
            LabeledControl("Température") {
                VerticalColorTempSlider(
                    kelvin = kelvin, onKelvinChange = { kelvin = it },
                    minKelvin = 2200, maxKelvin = 6500,
                    modifier = Modifier.controlSize(),
                )
            }
            LabeledControl("Désactivé") {
                VerticalColorTempSlider(
                    kelvin = disabledKelvin, onKelvinChange = { disabledKelvin = it },
                    minKelvin = 2200, maxKelvin = 6500, enabled = false,
                    modifier = Modifier.controlSize(),
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // 3 · Segmented selectors (2 … 6 options — the control grows taller with the count)
        SectionTitle("Sélecteur")
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
            var two by remember { mutableStateOf(true) }
            var three by remember { mutableStateOf(Presence.HOME) }
            var six by remember { mutableIntStateOf(2) }
            LabeledControl("2 options") {
                VerticalSegmentedSelector(
                    options = listOf(true, false),
                    selected = two, onSelect = { two = it },
                    label = { if (it) "Auto" else "Manuel" },
                    accent = accent,
                    modifier = Modifier.width(88.dp),
                )
            }
            LabeledControl("Icône empilée") {
                VerticalSegmentedSelector(
                    options = Presence.entries.toList(),
                    selected = three, onSelect = { three = it },
                    label = {
                        when (it) {
                            Presence.HOME -> "Au domicile"; Presence.AWAY -> "Absent"; Presence.OFF -> "Désactivée"
                        }
                    },
                    icon = {
                        when (it) {
                            Presence.HOME -> Icons.Filled.Home; Presence.AWAY -> Icons.Filled.DirectionsRun; Presence.OFF -> Icons.Filled.Block
                        }
                    },
                    accent = accent,
                    isNeutral = { it == Presence.OFF },
                    modifier = Modifier.width(88.dp),
                )
            }
            LabeledControl("6 options") {
                VerticalSegmentedSelector(
                    options = listOf(1, 2, 3, 4, 5, 6),
                    selected = six, onSelect = { six = it },
                    label = { "Niv. $it" },
                    icon = { Icons.Filled.Star },
                    contentLayout = ControlContentLayout.Horizontal,
                    accent = accent,
                    modifier = Modifier.width(88.dp),
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // 4 · Vertical switches
        SectionTitle("Interrupteur")
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            var on by remember { mutableStateOf(true) }
            var off by remember { mutableStateOf(false) }
            LabeledControl(if (on) "Activé" else "Désactivé") {
                VerticalSwitch(
                    checked = on, onCheckedChange = { on = it }, accent = accent,
                    modifier = Modifier.controlSize(),
                )
            }
            LabeledControl("Icône + texte") {
                VerticalSwitch(
                    checked = off, onCheckedChange = { off = it }, accent = accent,
                    icon = { Icons.Filled.PowerSettingsNew },
                    label = { if (it) "ON" else "OFF" },
                    modifier = Modifier.controlSize(),
                )
            }
            LabeledControl("Désactivé") {
                VerticalSwitch(
                    checked = true, onCheckedChange = {}, accent = accent, enabled = false,
                    modifier = Modifier.controlSize(),
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // 5 · Keypad
        SectionTitle("Clavier")
        var pinError by remember { mutableStateOf(false) }
        var unlocked by remember { mutableStateOf(false) }
        var keypadEnabled by remember { mutableStateOf(true) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Clavier actif", style = AppleTypography.bodyLarge, color = AppleColors.secondary)
            Spacer(Modifier.width(12.dp))
            VerticalSwitch(
                checked = keypadEnabled, onCheckedChange = { keypadEnabled = it }, accent = accent,
                modifier = Modifier.width(44.dp).height(72.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        PinKeypad(
            codeLength = 4,
            title = if (unlocked) "Déverrouillé ✓" else "Code : 1234",
            subtitle = "Un mauvais code fait trembler les points",
            accent = accent,
            error = pinError,
            onErrorConsumed = { pinError = false },
            enabled = keypadEnabled,
            onSubmit = { entered ->
                if (entered == "1234") unlocked = true else pinError = true
            },
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(28.dp))

        // 6 · Thermostat arc + cylinder mode picker
        SectionTitle("Thermostat")
        var tMode by remember { mutableStateOf(ThermostatMode.HEAT_COOL) }
        var low by remember { mutableFloatStateOf(63f) }
        var high by remember { mutableFloatStateOf(70f) }
        var single by remember { mutableFloatStateOf(68f) }
        ThermostatArc(
            mode = tMode,
            target = single, onTargetChange = { single = it },
            lowTarget = low, highTarget = high, onRangeChange = { l, h -> low = l; high = h },
            current = 66f,
            modifier = Modifier.fillMaxWidth().height(300.dp),
        )
        Spacer(Modifier.height(8.dp))
        val modes = listOf(ThermostatMode.OFF, ThermostatMode.COOL, ThermostatMode.HEAT, ThermostatMode.HEAT_COOL)
        WheelPicker(
            options = modes,
            selected = tMode,
            onSelect = { tMode = it },
            label = {
                when (it) {
                    ThermostatMode.OFF -> "Éteint"; ThermostatMode.COOL -> "Refroidir"
                    ThermostatMode.HEAT -> "Chauffer"; ThermostatMode.HEAT_COOL -> "Auto"
                }
            },
            accent = accent,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(28.dp))

        // 7 · Robot vacuum — Apple-simple layout
        SectionTitle("Aspirateur robot")
        val rooms = remember {
            listOf(
                VacuumRoom("living", "Salon", Icons.Outlined.Weekend),
                VacuumRoom("kitchen", "Cuisine", Icons.Outlined.Kitchen),
                VacuumRoom("bedroom", "Chambre", Icons.Outlined.Bed),
                VacuumRoom("bath", "Salle de bain", Icons.Outlined.Bathtub),
            )
        }
        var running by remember { mutableStateOf(true) }
        var mode by remember { mutableStateOf(VacuumMode.VACUUM) }
        var vacRooms by remember { mutableStateOf(setOf<String>()) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("emmy", style = AppleTypography.headlineLarge, color = AppleColors.primary)
            Text(
                if (running) "Nettoyage" else "En pause",
                style = AppleTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
            )
            Spacer(Modifier.height(28.dp))
            VacuumRunButton(running = running, onToggle = { running = it })
            Spacer(Modifier.height(28.dp))
            val modes = listOf(
                VacuumMode.VACUUM, VacuumMode.VACUUM_AND_MOP,
                VacuumMode.VACUUM_THEN_MOP, VacuumMode.MOP,
            )
            WheelPicker(
                options = modes,
                selected = mode,
                onSelect = { mode = it },
                label = { it.frLabel() },
                accent = AppleColors.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            VacuumStatusChip(if (running) "Préparation" else "En pause", prominent = true)
            Spacer(Modifier.height(20.dp))
            VacuumRoomChips(
                rooms = rooms,
                selected = vacRooms,
                onToggle = { id ->
                    vacRooms = if (id in vacRooms) vacRooms - id else vacRooms + id
                },
                currentRoomId = "living",
                roomState = { if (running) "En cours" else "En file" },
                accent = accent,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(28.dp))

        // 8 · HomeKit accessory grid
        SectionTitle("Accessoires (HomeKit)")
        var deskOn by remember { mutableStateOf(true) }
        var lampOn by remember { mutableStateOf(false) }
        var plugOn by remember { mutableStateOf(true) }
        val accessories = listOf(
            AccessoryItem(
                "desk", "Bureau", Icons.Outlined.Lightbulb, deskOn,
                subtitle = if (deskOn) "36 %" else "Éteinte",
                accent = AppleColors.warning, onToggle = { deskOn = it },
            ),
            AccessoryItem(
                "blinds", "Volet", Icons.Outlined.Blinds, false,
                subtitle = "Mise à jour…", accent = accent, warning = true,
            ),
            AccessoryItem(
                "lamp", "Lampe salon", Icons.Outlined.Lightbulb, lampOn,
                subtitle = if (lampOn) "Allumée" else "Éteinte",
                accent = AppleColors.warning, onToggle = { lampOn = it },
            ),
            AccessoryItem(
                "plug", "Prise TV", Icons.Outlined.Power, plugOn,
                subtitle = if (plugOn) "Activée" else "Désactivée",
                accent = accent, onToggle = { plugOn = it },
            ),
            AccessoryItem(
                "plug2", "Prise 2", Icons.Outlined.Power, false,
                subtitle = "Pas de réponse", warning = true,
            ),
        )
        AccessoryGrid(items = accessories)

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = AppleTypography.titleLarge, color = AppleColors.primary)
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun LabeledControl(caption: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Spacer(Modifier.height(8.dp))
        Text(caption, style = AppleTypography.labelSmall, color = AppleColors.tertiary)
    }
}
