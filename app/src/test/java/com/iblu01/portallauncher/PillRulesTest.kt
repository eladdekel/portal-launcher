package com.iblu01.portallauncher

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PillRulesTest {

    private fun entity(id: String, state: String, deviceClass: String = "", unit: String = "") =
        HaEntity(
            id,
            state,
            JSONObject()
                .put("friendly_name", id.substringAfter('.'))
                .put("device_class", deviceClass)
                .put("unit_of_measurement", unit),
        )

    // --- PillFamily ---

    @Test fun `every kind belongs to exactly one family`() {
        PillKind.values().forEach { kind ->
            val families = PillFamily.values().filter { kind in it.kinds }
            assertEquals("kind $kind should be in exactly one family", 1, families.size)
        }
    }

    @Test fun `family grouping matches plain-language buckets`() {
        assertEquals(PillFamily.SECURITY, PillFamily.of(PillKind.SAFETY))
        assertEquals(PillFamily.SECURITY, PillFamily.of(PillKind.OPENING))
        assertEquals(PillFamily.COMFORT, PillFamily.of(PillKind.THERMOSTAT))
        assertEquals(PillFamily.APPLIANCES, PillFamily.of(PillKind.VACUUM))
        assertEquals(PillFamily.LIGHTS_SCENES, PillFamily.of(PillKind.LIGHTS))
        assertEquals(PillFamily.MEDIA, PillFamily.of(PillKind.MEDIA))
        assertEquals(PillFamily.HOME, PillFamily.of(PillKind.PRESENCE))
    }

    // --- friendlyEntityState ---

    @Test fun `binary sensors use plain words`() {
        assertEquals("Ouverte", friendlyEntityState(entity("binary_sensor.front_door", "on", "door")))
        assertEquals("Fermée", friendlyEntityState(entity("binary_sensor.front_door", "off", "door")))
        assertEquals("Mouvement", friendlyEntityState(entity("binary_sensor.hall", "on", "motion")))
        assertEquals("Alerte", friendlyEntityState(entity("binary_sensor.smoke", "on", "smoke")))
    }

    @Test fun `sensors show their value and unit`() {
        assertEquals("21 °C", friendlyEntityState(entity("sensor.living_temp", "21.0", "temperature", "°C")))
        assertEquals("45 %", friendlyEntityState(entity("sensor.living_hum", "45", "humidity", "%")))
    }

    @Test fun `people and locks use everyday words`() {
        assertEquals("À la maison", friendlyEntityState(entity("person.marie", "home")))
        assertEquals("Absente", friendlyEntityState(entity("person.marie", "not_home")))
        assertEquals("Verrouillée", friendlyEntityState(entity("lock.door", "locked")))
        assertEquals("Déverrouillée", friendlyEntityState(entity("lock.door", "unlocked")))
    }

    @Test fun `common on off and media states`() {
        assertEquals("Allumé", friendlyEntityState(entity("light.salon", "on")))
        assertEquals("Éteint", friendlyEntityState(entity("light.salon", "off")))
        assertEquals("En lecture", friendlyEntityState(entity("media_player.tv", "playing")))
        assertEquals("En pause", friendlyEntityState(entity("media_player.tv", "paused")))
    }

    @Test fun `degraded states are readable`() {
        assertEquals("Indisponible", friendlyEntityState(entity("sensor.x", "unavailable")))
        assertEquals("—", friendlyEntityState(entity("sensor.x", "unknown")))
    }

    // --- deriveHaUrl (mDNS discovery) ---

    @Test fun `deriveHaUrl prefers the advertised internal url`() {
        val url = deriveHaUrl("192.168.1.10", 8123, mapOf("internal_url" to "http://home.local:8123/"))
        assertEquals("http://home.local:8123", url)
    }

    @Test fun `deriveHaUrl falls back to host and port`() {
        assertEquals("http://192.168.1.10:8123", deriveHaUrl("192.168.1.10", 8123, emptyMap()))
        assertEquals("http://192.168.1.10:8123", deriveHaUrl("192.168.1.10", 8123, mapOf("internal_url" to "  ")))
    }

    @Test fun `deriveHaUrl rejects unusable input`() {
        assertEquals(null, deriveHaUrl(null, 8123, emptyMap()))
        assertEquals(null, deriveHaUrl("", 0, emptyMap()))
    }

    @Test fun `candidates are unaffected by the family mapping`() {
        // Guard: introducing PillFamily must not change which entities become candidates.
        assertTrue(PillSupport.isSupported(entity("light.salon", "on")))
        assertEquals(PillKind.LIGHTS, PillSupport.kind(entity("light.salon", "on")))
    }
}
