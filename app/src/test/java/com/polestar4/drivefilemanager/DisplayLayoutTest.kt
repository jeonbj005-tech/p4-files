package com.polestar4.drivefilemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayLayoutTest {
    @Test fun windowMetricsWidthUsesCurrentDensity() {
        assertEquals(1200, DisplayLayout.widthDp(1920, 1.6f, 0))
    }

    @Test fun missingMetricsUseConfigurationWidth() {
        assertEquals(720, DisplayLayout.widthDp(0, 1f, 720))
        assertEquals(720, DisplayLayout.widthDp(1080, 0f, 720))
    }

    @Test fun narrowWindowsDoNotUseWideLayout() {
        assertTrue(DisplayLayout.isWideLayout(true, 700))
        assertFalse(DisplayLayout.isWideLayout(true, 699))
        assertFalse(DisplayLayout.isWideLayout(false, 1200))
    }

    @Test fun automotiveFeatureKeepsVehicleTouchLayout() {
        assertTrue(DisplayLayout.isVehicleLayout(true, false, 400))
        assertFalse(DisplayLayout.isVehicleLayout(false, false, 800))
        assertTrue(DisplayLayout.isVehicleLayout(false, true, 600))
    }
}
