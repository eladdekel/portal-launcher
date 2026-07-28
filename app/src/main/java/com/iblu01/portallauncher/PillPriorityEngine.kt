package com.iblu01.portallauncher
import com.iblu01.portallauncher.domain.model.PillDetail

import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.roundToInt

object PillPriorityEngine {
    private val inactive = setOf("off", "closed", "locked", "idle", "docked", "standby", "stop", "stopped", "unavailable", "unknown", "clear", "disarmed")
    private val activeAppliance = setOf("on", "run", "running", "cleaning", "washing", "drying", "printing", "active", "returning", "paused", "prepare", "slicing", "init")
    private val done = setOf("done", "finished", "complete", "completed", "finish")

    private fun parseHaInstant(value: String): Instant? =
        runCatching { Instant.parse(value) }.getOrElse {
            runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        }

    fun select(rules: List<PillRule>, states: Map<String, HaEntity>, nowMs: Long = System.currentTimeMillis()): List<LauncherChip> {
        val enabled = rules.filter { it.enabled }
        val groupedKinds = setOf(PillKind.OPENING, PillKind.CLIMATE, PillKind.LIGHTS, PillKind.MEDIA, PillKind.PURIFIER, PillKind.AIR, PillKind.SCENE, PillKind.PRESENCE, PillKind.ENERGY)
        val individual = enabled.asSequence().filter { it.kind !in groupedKinds }
            .mapNotNull { rule -> states[rule.entityId]?.let { toChip(rule, it, states, nowMs) } }.toList()
        val openings = openingGroup(enabled.filter { it.kind == PillKind.OPENING }, states, nowMs)
        val temperatures = temperatureGroup(enabled.filter { it.kind == PillKind.CLIMATE }, states)
        val batteries = relatedBatteryAlert(enabled, states)
        val lights = lightsGroup(enabled.filter { it.kind == PillKind.LIGHTS }, states)
        val media = mediaGroup(enabled.filter { it.kind == PillKind.MEDIA }, states, nowMs)
        val purifier = purifierGroup(enabled.filter { it.kind == PillKind.PURIFIER }, states)
        val air = airGroup(enabled.filter { it.kind == PillKind.AIR }, states)
        val scenes = scenesGroup(enabled.filter { it.kind == PillKind.SCENE }, states)
        val presence = presenceGroup(enabled.filter { it.kind == PillKind.PRESENCE }, states)
        val energy = energyGroup(enabled.filter { it.kind == PillKind.ENERGY }, states)
        return (individual + listOfNotNull(openings, batteries, temperatures, lights, media, purifier, air, scenes, presence, energy))
            .sortedWith(compareByDescending<LauncherChip> { it.priority }.thenBy { it.label }).take(9)
    }

    /** All person.* entities in one pill; panel shows avatars + who is home. */
    private fun presenceGroup(rules: List<PillRule>, states: Map<String, HaEntity>): LauncherChip? {
        val people = rules.mapNotNull { rule -> states[rule.entityId]?.let { rule to it } }
            .filter { it.second.state.lowercase() !in setOf("unavailable", "unknown") }
        if (people.isEmpty()) return null
        val home = people.filter { it.second.state.equals("home", true) }
        val details = people.sortedByDescending { it in home }.map { (rule, entity) ->
            val status = when (entity.state.lowercase()) {
                "home" -> "À la maison"
                "not_home" -> "Absent"
                else -> "Absent · ${entity.state.replaceFirstChar { it.uppercase() }}"
            }
            PillDetail(rule.label, status, entityId = entity.entityId, active = entity.state.equals("home", true))
        }
        return LauncherChip(
            id = "presence_group", icon = "presence", label = "Présence",
            value = if (home.isEmpty()) "Personne" else "${home.size} à la maison",
            state = "info", priority = if (home.isEmpty()) 6 else 9,
            details = details, kind = PillKind.PRESENCE,
        )
    }

    /** Main power / daily-energy sensors in one pill; panel shows live watts + today's kWh. */
    private fun energyGroup(rules: List<PillRule>, states: Map<String, HaEntity>): LauncherChip? {
        val entities = rules.mapNotNull { rule -> states[rule.entityId] }
            .filter { it.state.lowercase() !in setOf("unavailable", "unknown") }
        if (entities.isEmpty()) return null
        val powerName = Regex("maison|total|grid|global|home|conso", RegexOption.IGNORE_CASE)
        val power = entities.filter { it.deviceClass == "power" }.let { p -> p.firstOrNull { powerName.containsMatchIn(it.entityId) } ?: p.firstOrNull() }
        val watts = power?.state?.toDoubleOrNull()
        val details = buildList {
            power?.let { add(PillDetail("Puissance", "${watts?.roundToInt() ?: 0} W", entityId = it.entityId)) }
            entities.filter { it.deviceClass == "energy" }.firstOrNull()?.let {
                add(PillDetail("Aujourd'hui", it.state + (it.attributes.optString("unit_of_measurement").ifBlank { " kWh" }.let { u -> if (u.startsWith(" ")) u else " $u" }), entityId = it.entityId))
            }
        }
        return LauncherChip(
            id = "energy_group", icon = "energy", label = "Énergie",
            value = watts?.let { "${it.roundToInt()} W" } ?: "—",
            state = "info", priority = 8,
            details = details, kind = PillKind.ENERGY,
        )
    }

    /** All enabled scenes and scripts grouped into one pill; the panel renders one button per entity. */
    private fun scenesGroup(rules: List<PillRule>, states: Map<String, HaEntity>): LauncherChip? {
        val entities = rules.mapNotNull { rule -> states[rule.entityId]?.let { rule to it } }
        if (entities.isEmpty()) return null
        return LauncherChip(
            id = "scenes_group", icon = "scene", label = "Scènes",
            value = if (entities.size == 1) "1 raccourci" else "${entities.size} raccourcis",
            state = "info", priority = 10,
            details = entities.map { (rule, entity) -> PillDetail(rule.label, "", entityId = entity.entityId) },
            kind = PillKind.SCENE,
        )
    }

    private fun lightsGroup(rules: List<PillRule>, states: Map<String, HaEntity>): LauncherChip? {
        val entities = rules.mapNotNull { rule -> states[rule.entityId]?.let { rule to it } }
            .filter { it.second.state.lowercase() !in setOf("unknown", "unavailable") }
        if (entities.isEmpty()) return null
        val on = entities.filter { it.second.state.equals("on", true) }
        return LauncherChip(
            id = "lights_group", icon = "light", label = "Lumières",
            value = when (on.size) { 0 -> "Toutes éteintes"; 1 -> "1 allumée"; else -> "${on.size} allumées" },
            state = if (on.isEmpty()) "ok" else "warning", priority = if (on.isEmpty()) 7 else 16,
            details = entities.sortedByDescending { it in on }.map { (rule, entity) ->
                PillDetail(rule.label, if (entity.state == "on") "Allumée" else "Éteinte", entityId = entity.entityId, active = entity.state.equals("on", true))
            },
        )
    }

    private fun mediaGroup(rules: List<PillRule>, states: Map<String, HaEntity>, nowMs: Long): LauncherChip? {
        val entities = rules.mapNotNull { rule -> states[rule.entityId]?.let { rule to it } }
            .filter { it.second.state.lowercase() !in setOf("unknown", "unavailable") }
        if (entities.isEmpty()) return null

        fun isRecentPaused(entity: HaEntity): Boolean {
            if (!entity.state.equals("paused", true)) return false
            val ageMs = runCatching { nowMs - (parseHaInstant(entity.lastChanged)?.toEpochMilli() ?: nowMs) }.getOrDefault(0L)
            return ageMs < 30_000
        }

        val playing = entities.filter { it.second.state.lowercase() in setOf("playing", "buffering") }
        val paused = entities.filter { isRecentPaused(it.second) }
        val value = when { playing.isNotEmpty() -> if (playing.size == 1) "En lecture" else "${playing.size} en lecture"; paused.isNotEmpty() -> "En pause"; else -> "Aucun média" }
        return LauncherChip(
            id = "media_group", icon = "media", label = "Médias", value = value,
            state = if (playing.isNotEmpty()) "active" else "ok", priority = if (playing.isNotEmpty()) 22 else 3,
            details = entities.filter { 
                val state = it.second.state.lowercase()
                state !in setOf("off", "idle", "standby") && (state != "paused" || isRecentPaused(it.second))
            }.map { (rule, entity) ->
                PillDetail(rule.label, entity.attributes.optString("media_title").ifBlank { entity.state.replaceFirstChar { it.uppercase() } })
            },
        )
    }

    private fun purifierGroup(rules: List<PillRule>, states: Map<String, HaEntity>): LauncherChip? {
        val rule = rules.firstOrNull() ?: return null
        val entity = states[rule.entityId] ?: return null
        if (entity.state.lowercase() in setOf("unknown", "unavailable")) return null
        val running = entity.state.equals("on", true)
        val related = rule.relatedEntityIds.mapNotNull(states::get).filter { it.state !in setOf("unknown", "unavailable") }
        return LauncherChip(
            id = "purifier_group", icon = "air", label = "Purificateur",
            value = if (running) entity.attributes.optString("preset_mode").takeIf { it.isNotBlank() }?.let { "En marche · $it" } ?: "En marche" else "Arrêté",
            state = if (running) "active" else "ok", priority = if (running) 15 else 5,
            entityId = entity.entityId,
            details = related.filter { 
                it.deviceClass in setOf("pm25", "pm10", "volatile_organic_compounds", "aqi") || 
                it.entityId.contains("filtre") || 
                it.entityId.contains("filter") || 
                it.entityId.contains("tvoc") || 
                it.entityId.contains("qualite_d_air") ||
                it.entityId.contains("pm2_5")
            }
                .map { PillDetail(it.name.replace(Regex("^${Regex.escape(rule.label)} ", RegexOption.IGNORE_CASE), ""), it.state + it.attributes.optString("unit_of_measurement")) },
        )
    }

    private fun airGroup(rules: List<PillRule>, states: Map<String, HaEntity>): LauncherChip? {
        val sensors = rules.mapNotNull { rule -> states[rule.entityId]?.let { rule to it } }
            .filter { it.second.state.lowercase() !in setOf("unknown", "unavailable") }
        if (sensors.isEmpty()) return null
        val warnings = sensors.filter { (rule, entity) ->
            val reading = entity.state.toFloatOrNull() ?: return@filter false
            when (entity.deviceClass) {
                "carbon_dioxide" -> reading > 1_000
                "pm25" -> reading > 25
                "pm10" -> reading > 50
                "aqi" -> reading > 50
                else -> reading > 800
            }
        }
        val qualitative = when {
            warnings.isNotEmpty() && sensors.size == warnings.size -> "Mauvaise"
            warnings.isNotEmpty() -> "Moyenne"
            else -> "Bonne"
        }
        val details = sensors.map { (rule, entity) ->
            val unit = entity.attributes.optString("unit_of_measurement", "")
            PillDetail(rule.label, entity.state + unit, entityId = entity.entityId, active = false)
        }
        return LauncherChip(
            id = "air_group", icon = "air", label = "Qualité de l'air",
            value = qualitative,
            state = if (warnings.isNotEmpty()) "warning" else "active",
            priority = if (warnings.isEmpty()) 8 else 35,
            details = details,
        )
    }

    private fun temperatureGroup(rules: List<PillRule>, states: Map<String, HaEntity>): LauncherChip? {
        val readings = rules.mapNotNull { rule ->
            val entity = states[rule.entityId] ?: return@mapNotNull null
            if (entity.deviceClass != "temperature") return@mapNotNull null
            entity.state.toDoubleOrNull()?.let { Triple(rule, entity, it) }
        }
        if (readings.isEmpty()) return null
        val min = readings.minOf { it.third }
        val max = readings.maxOf { it.third }
        fun fmt(value: Double) = if (value % 1.0 == 0.0) "${value.toInt()}°" else "%.1f°".format(value)
        val abnormal = min < 16 || max > 28
        val details = readings.sortedBy { it.third }.map { (rule, entity, value) ->
            val humidity = rule.relatedEntityIds.mapNotNull(states::get).firstOrNull { it.deviceClass == "humidity" }?.state?.toDoubleOrNull()
            val suffix = humidity?.let { " · ${it.toInt()}%" }.orEmpty()
            PillDetail(rule.label.replace(Regex(" (Température|Temperature)$", RegexOption.IGNORE_CASE), ""), fmt(value) + suffix)
        }
        return LauncherChip(
            id = "temperature_group", icon = "temperature", label = "Températures",
            value = if (readings.size == 1) fmt(min) else "Min ${fmt(min)} · Max ${fmt(max)}",
            state = if (abnormal) "warning" else "ok", priority = if (abnormal) 35 else 2,
            details = details,
        )
    }

    private fun relatedBatteryAlert(rules: List<PillRule>, states: Map<String, HaEntity>): LauncherChip? {
        val low = rules.flatMap { it.relatedEntityIds }.distinct().mapNotNull(states::get)
            .filter { it.deviceClass == "battery" }.mapNotNull { e -> e.state.toFloatOrNull()?.let { e to it } }
            .filter { it.second <= 30 }
        if (low.isEmpty()) return null
        val minimum = low.minOf { it.second }
        return LauncherChip(
            id = "battery_group", icon = "battery", label = "Batteries faibles",
            value = if (low.size == 1) "${minimum.toInt()}%" else "${low.size} appareils · min ${minimum.toInt()}%",
            state = if (minimum <= 10) "critical" else "warning",
            priority = if (minimum <= 10) 82 else 6,
            details = low.sortedBy { it.second }.map { (entity, level) -> PillDetail(entity.name.replace(Regex(" (Batterie|Battery)$", RegexOption.IGNORE_CASE), ""), "${level.toInt()}%") },
        )
    }

    private fun openingGroup(rules: List<PillRule>, states: Map<String, HaEntity>, nowMs: Long): LauncherChip? {
        val entities = rules.mapNotNull { states[it.entityId] }.filter { it.state.lowercase() !in setOf("unknown", "unavailable", "") }
        if (entities.isEmpty()) return null
        val opened = entities.filter { it.state.lowercase() in setOf("on", "open", "opening") }
        val closedCount = entities.size - opened.size
        fun shortName(entity: HaEntity): String {
            val ruleLabel = rules.firstOrNull { it.entityId == entity.entityId }?.label ?: entity.name
            return ruleLabel.replace(Regex("^(Capteur )?(porte|fenêtre) (de |du |des |d')?", RegexOption.IGNORE_CASE), "")
                .replace(Regex(" Porte$", RegexOption.IGNORE_CASE), "").replaceFirstChar { it.uppercase() }
        }
        val value = when {
            opened.isEmpty() -> "Toutes fermées"
            opened.size == 1 -> "1 ouverte"
            else -> "${opened.size} ouvertes"
        }
        val ageBonus = opened.maxOfOrNull { entity ->
            runCatching { ((nowMs - Instant.parse(entity.lastChanged).toEpochMilli()) / 60_000).coerceIn(0, 30).toInt() }.getOrDefault(0)
        } ?: 0
        return LauncherChip(
            id = "openings_group", icon = "door", label = "Portes & fenêtres", value = value,
            state = if (opened.isEmpty()) "ok" else "warning", entityId = rules.joinToString(",") { it.entityId },
            priority = if (opened.isEmpty()) 5 else 55 + ageBonus,
            details = entities.sortedByDescending { it in opened }.map { PillDetail(shortName(it), if (it in opened) "Ouverte" else "Fermée") },
        )
    }

    fun toChip(rule: PillRule, e: HaEntity, states: Map<String, HaEntity> = emptyMap(), nowMs: Long = System.currentTimeMillis()): LauncherChip? {
        val s = e.state.lowercase()
        if (s in setOf("unavailable", "unknown", "none", "")) return null
        val ageMinutes = runCatching { (nowMs - Instant.parse(e.lastChanged).toEpochMilli()) / 60_000 }.getOrDefault(0)
        var score = rule.kind.basePriority + rule.priorityBoost
        var visual = "info"
        var visible = true
        when (rule.kind) {
            PillKind.SAFETY -> if (e.domain == "alarm_control_panel") {
                when (s) {
                    "triggered" -> { score = 100; visual = "critical" }
                    "pending", "arming" -> { score = 70; visual = "warning" }
                    "armed_away", "armed_home", "armed_night", "armed_vacation", "armed_custom_bypass" -> { score = 18; visual = "active" }
                    "disarmed" -> { score = 8; visual = "ok" }
                    else -> { score = 12; visual = "info" }
                }
            } else if (s in inactive) visible = false else { score = 100; visual = "critical" }
            PillKind.LOCK -> if (s == "unlocked" || s == "jammed" || s == "open") { score = 88; visual = "critical" } else { score = 9; visual = "active" }
            PillKind.OPENING -> if (s in setOf("on", "open", "opening")) { score = 55 + ageMinutes.coerceAtMost(30).toInt(); visual = if (ageMinutes >= 5) "warning" else "active" } else visible = false
            PillKind.APPLIANCE, PillKind.VACUUM -> when {
                s in done -> {
                    val ageMs = runCatching { nowMs - (parseHaInstant(e.lastChanged)?.toEpochMilli() ?: nowMs) }.getOrDefault(0L)
                    if (ageMs < 600_000) { score = 76; visual = "ok" } else visible = false
                }
                s in activeAppliance -> visual = "active"
                else -> visible = false
            }
            PillKind.BATTERY -> { val value = s.toFloatOrNull() ?: return null; visible = value <= 30; score = if (value <= 10) 82 else 45; visual = if (value <= 10) "critical" else "warning" }
            PillKind.AIR -> {
                val reading = s.toFloatOrNull()
                val warning = when (e.deviceClass) {
                    "carbon_dioxide" -> reading != null && reading > 1_000
                    "pm25" -> reading != null && reading > 25
                    "pm10" -> reading != null && reading > 50
                    "aqi" -> reading != null && reading > 50
                    else -> reading != null && reading > 800
                }
                score = if (warning) 48 else 14; visual = if (warning) "warning" else "active"
            }
            PillKind.ENERGY -> {
                val reading = s.toFloatOrNull()
                visible = reading == null || reading > 0.1f
                score = 4; visual = "info"
            }
            PillKind.TIMER -> { visible = s !in inactive; score = 62; visual = "active" }
            PillKind.THERMOSTAT -> {
                val heating = s in setOf("heat", "cool", "heat_cool", "auto", "dry", "fan_only")
                score = rule.kind.basePriority; visual = if (heating) "active" else "ok"
            }
            PillKind.COVER -> {
                val opened = s in setOf("open", "opening", "closing")
                score = if (opened) rule.kind.basePriority + 10 else 6; visual = if (opened) "active" else "ok"
            }
            PillKind.SWITCH -> { val on = s == "on"; score = if (on) rule.kind.basePriority else 6; visual = if (on) "active" else "ok" }
            PillKind.FAN -> { val on = s == "on"; score = if (on) rule.kind.basePriority else 5; visual = if (on) "active" else "ok" }
            PillKind.LIGHTS, PillKind.MEDIA, PillKind.PURIFIER, PillKind.CLIMATE, PillKind.SCENE, PillKind.PRESENCE -> visible = false
            PillKind.GENERIC -> visible = s !in inactive
        }
        if (!visible) return null
        val related = rule.relatedEntityIds.mapNotNull(states::get)
        
        val logicalBase = e.entityId.substringAfter('.').replace("_machine_state", "").replace("_etat_de_la_machine", "").replace("_etat_de_l_impression", "")
        val dynamicCompletion = states.values.firstOrNull { 
            (it.entityId.contains("completion_time") || it.entityId.contains("end_time") || it.entityId.contains("heure_de_fin")) && it.entityId.contains(logicalBase)
        }
        val completionEntity = related.firstOrNull { it.entityId.contains("completion_time") || it.entityId.contains("end_time") || it.entityId.contains("heure_de_fin") } ?: dynamicCompletion
        
        val progressValue = listOf("percentage", "progress", "battery_level").firstNotNullOfOrNull { key ->
            if (e.attributes.has(key)) e.attributes.optDouble(key).takeIf { !it.isNaN() } else null
        } ?: related.firstOrNull { it.entityId.contains("progress") || it.entityId.contains("pourcentage") || it.entityId.contains("progression") }?.state?.toDoubleOrNull()
        ?: run {
            if (completionEntity != null && completionEntity.state !in setOf("unknown", "unavailable", "none", "")) {
                val endInstant = parseHaInstant(completionEntity.state)
                val startInstant = parseHaInstant(completionEntity.lastChanged)
                if (endInstant != null && startInstant != null) {
                    val totalDuration = endInstant.toEpochMilli() - startInstant.toEpochMilli()
                    val elapsed = nowMs - startInstant.toEpochMilli()
                    if (totalDuration > 0 && elapsed >= 0) {
                        (elapsed.toDouble() / totalDuration.toDouble() * 100.0).coerceIn(0.0, 100.0)
                    } else null
                } else null
            } else null
        }
        
        val unit = e.attributes.optString("unit_of_measurement")
        val phase = related.firstOrNull { it.entityId.contains("cycle") || it.entityId.contains("task_state") || it.entityId.contains("etat_du_cycle") }?.state
        val battery = related.firstOrNull { it.deviceClass == "battery" && it.state.toFloatOrNull() != null }?.state
        val completionTimeStr = completionEntity?.state
        
        val formattedCompletion = runCatching {
            if (completionTimeStr != null && completionTimeStr !in setOf("unknown", "unavailable", "none", "")) {
                val instant = parseHaInstant(completionTimeStr)
                    ?: error("Unsupported HA timestamp: $completionTimeStr")
                val remainingMs = instant.toEpochMilli() - nowMs
                val remainingMins = Math.ceil(remainingMs / 60000.0).toLong()
                
                if (remainingMins > 0) {
                    if (remainingMins >= 60) {
                        val hours = remainingMins / 60
                        val mins = remainingMins % 60
                        val minStr = mins.toString().padStart(2, '0')
                        "Reste ${hours}h${minStr}"
                    } else {
                        "Reste $remainingMins min"
                    }
                } else {
                    val zdt = instant.atZone(java.time.ZoneId.systemDefault())
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                    "Fin à " + zdt.format(formatter)
                }
            } else null
        }.onFailure { err -> android.util.Log.e("PillPriorityEngine", "Failed to parse completion time", err) }.getOrNull()
        
        val translatedPhase = when (phase?.lowercase()) {
            "air_wash" -> "Désodorisation"
            "ai_rinse" -> "Rinçage IA"
            "ai_spin" -> "Essorage IA"
            "ai_wash" -> "Lavage IA"
            "cooling" -> "Refroidissement"
            "delay_wash" -> "Départ différé"
            "drying" -> "Séchage"
            "finish" -> "Terminé"
            "none" -> "Aucun"
            "pre_wash" -> "Prélavage"
            "rinse" -> "Rinçage"
            "spin" -> "Essorage"
            "wash" -> "Lavage"
            "weight_sensing" -> "Pesée"
            "wrinkle_prevent" -> "Anti-froissement"
            "freeze_protection" -> "Protection gel"
            else -> phase
        }

        val rawDisplay = formattedCompletion 
            ?: e.attributes.optString("remaining_time").takeIf { it.isNotBlank() }
            ?: e.attributes.optString("phase").takeIf { it.isNotBlank() }
            ?: e.attributes.optString("status").takeIf { it.isNotBlank() }
            ?: translatedPhase?.takeIf { it !in setOf("none", "unknown", "unavailable", "Aucun") }
            ?: (e.state + unit.takeIf { it.isNotBlank() }.orEmpty())
            
        if (e.entityId.contains("machine_a_laver")) {
            runCatching {
                android.util.Log.i(
                    "PortalWasher",
                    "state=${e.state} phase=${phase.orEmpty()} completion=${completionTimeStr.orEmpty()} display=$rawDisplay progress=${progressValue?.roundToInt() ?: 0}%"
                )
            }
        }
            
        val display = when (rule.kind) {
            PillKind.OPENING -> when (s) { "on", "open", "opening" -> "Ouverte"; else -> rawDisplay }
            PillKind.LOCK -> when (s) { "locked" -> "Verrouillée"; "unlocked" -> "Déverrouillée"; "jammed" -> "Bloquée"; else -> rawDisplay }
            PillKind.SAFETY -> if (e.domain == "alarm_control_panel") when (s) {
                "disarmed" -> "Désarmée"
                "armed_away" -> "Armée · absence"
                "armed_home" -> "Armée · présence"
                "armed_night" -> "Armée · nuit"
                "triggered" -> "Alarme déclenchée"
                "pending" -> "Déclenchement imminent"
                "arming" -> "Armement…"
                else -> rawDisplay
            } else if (s !in inactive) "Alerte" else rawDisplay
            PillKind.THERMOSTAT -> {
                val target = e.attributes.optDouble("temperature").takeIf { !it.isNaN() }
                target?.let { if (it % 1.0 == 0.0) "${it.toInt()}°" else "%.1f°".format(it) } ?: rawDisplay
            }
            PillKind.COVER -> when (s) {
                "open" -> e.attributes.optInt("current_position", -1).let { if (it in 0..100) "Ouvert · $it%" else "Ouvert" }
                "closed" -> "Fermé"
                "opening" -> "Ouverture…"
                "closing" -> "Fermeture…"
                else -> rawDisplay
            }
            PillKind.SWITCH -> when (s) { "on" -> "Allumé"; "off" -> "Éteint"; else -> rawDisplay }
            PillKind.FAN -> when (s) {
                "on" -> e.attributes.optInt("percentage", -1).let { if (it in 0..100) "Marche · $it%" else "En marche" }
                "off" -> "Arrêté"
                else -> rawDisplay
            }
            else -> rawDisplay
        }
        val humidity = related.firstOrNull { it.deviceClass == "humidity" && it.state.toFloatOrNull() != null }
        val value = when {
            humidity != null && e.deviceClass == "temperature" -> "$display · ${humidity.state}%"
            battery != null && rule.kind in setOf(PillKind.LOCK, PillKind.VACUUM, PillKind.OPENING) -> "$display · $battery%"
            else -> display
        }
        val label = rule.label
            .replace(Regex(" (État de la machine|État de la tâche|Porte)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^Capteur ", RegexOption.IGNORE_CASE), "")
            .replaceFirstChar { it.uppercase() }
        val qualitativeAir = if (rule.kind == PillKind.AIR) when {
            visual == "warning" && score >= 70 -> "Mauvaise"
            visual == "warning" -> "Moyenne"
            else -> "Bonne"
        } else value
        val finalLabel = when {
            rule.kind == PillKind.AIR -> "Qualité de l'air"
            rule.kind == PillKind.SAFETY && e.domain == "alarm_control_panel" -> "Sécurité"
            else -> label
        }
        val details = if (rule.kind == PillKind.AIR) listOf(PillDetail(e.name, e.state + e.attributes.optString("unit_of_measurement"))) else emptyList()
        return LauncherChip(rule.entityId, rule.kind.icon, finalLabel, qualitativeAir, visual,
            ((progressValue ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f), e.entityId, score, false, details, kind = rule.kind)
    }
}
