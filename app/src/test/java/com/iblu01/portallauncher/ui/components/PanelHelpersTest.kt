package com.iblu01.portallauncher.ui.components

import com.iblu01.portallauncher.HaEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelHelpersTest {
    private fun alarm(codeFormat: String?, armRequired: Boolean?): HaEntity {
        val o = JSONObject()
        if (codeFormat != null) o.put("code_format", codeFormat)
        if (armRequired != null) o.put("code_arm_required", armRequired)
        return HaEntity("alarm_control_panel.home", "disarmed", o)
    }

    @Test fun `no code_format means no code ever required`() {
        val e = alarm(codeFormat = null, armRequired = true)
        assertFalse(e.alarmCodeRequired(arming = true))
        assertFalse(e.alarmCodeRequired(arming = false))
        assertEquals(null, e.alarmCodeFormat())
    }

    @Test fun `disarm always needs code when a format is set`() {
        val e = alarm(codeFormat = "number", armRequired = false)
        assertTrue(e.alarmCodeRequired(arming = false))
        assertFalse(e.alarmCodeRequired(arming = true))   // arming not required here
        assertEquals("number", e.alarmCodeFormat())
    }

    @Test fun `arming needs code only when code_arm_required`() {
        assertTrue(alarm("number", true).alarmCodeRequired(arming = true))
        assertFalse(alarm("number", false).alarmCodeRequired(arming = true))
        // default for code_arm_required is true when absent
        assertTrue(alarm("number", null).alarmCodeRequired(arming = true))
    }

    @Test fun `supported_features bit test`() {
        val e = HaEntity("cover.x", "open", JSONObject().put("supported_features", CoverFeature.OPEN or CoverFeature.SET_POSITION))
        assertTrue(e.supports(CoverFeature.OPEN))
        assertTrue(e.supports(CoverFeature.SET_POSITION))
        assertFalse(e.supports(CoverFeature.STOP))
    }
}
