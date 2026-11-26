package com.revenge.usbaudio

import com.revenge.usbaudio.model.VolumeMode
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeControllerTest {

    @Test
    fun sliderEndpointsAndMidpoint() {
        val controller = VolumeController(hasHardwareVolume = false)

        assertEquals(0f, controller.calculateDbForSlider(1f), 0.001f)
        assertEquals(-60f, controller.calculateDbForSlider(0f), 0.001f)

        val midpoint = controller.calculateDbForSlider(0.5f)
        assertTrue(midpoint in -22f..-15f)

        assertEquals(-60f, controller.calculateDbForSlider(-3f), 0.001f)
        assertEquals(0f, controller.calculateDbForSlider(4f), 0.001f)
    }

    @Test
    fun decibelsConvertToUacMillibelsAndClamp() {
        val controller = VolumeController(hasHardwareVolume = false)

        assertEquals((-4608).toShort(), controller.dbToUacMillibel(-18f))
        assertEquals(Short.MIN_VALUE, controller.dbToUacMillibel(-500f))
        assertEquals(Short.MAX_VALUE, controller.dbToUacMillibel(500f))
    }

    @Test
    fun bitPerfectAndHardwareModesLeaveSamplesUntouched() {
        val pure = VolumeController(hasHardwareVolume = false)
        pure.setVolume(0.5f)
        assertEquals(1.0, pure.getEffectiveSoftwareMultiplier(), 0.0)

        val hardware = VolumeController(hasHardwareVolume = true)
        hardware.setVolume(0.5f)
        assertEquals(1.0, hardware.getEffectiveSoftwareMultiplier(), 0.0)
    }

    @Test
    fun softwareModeAppliesSliderCurve() {
        val controller = VolumeController(hasHardwareVolume = false)
        controller.volumeMode = VolumeMode.SOFTWARE_64BIT

        controller.setVolume(1f)
        assertEquals(1.0, controller.getEffectiveSoftwareMultiplier(), 1e-9)

        controller.setVolume(0.5f)
        val expected = 10.0.pow(controller.calculateDbForSlider(0.5f) / 20.0)
        assertEquals(expected, controller.getEffectiveSoftwareMultiplier(), 1e-9)
        assertTrue(controller.getEffectiveSoftwareMultiplier() < 1.0)
    }

    @Test
    fun defaultModeFollowsHardwarePresence() {
        assertEquals(VolumeMode.HARDWARE, VolumeController(hasHardwareVolume = true).volumeMode)
        assertEquals(
            VolumeMode.PURE_BIT_PERFECT,
            VolumeController(hasHardwareVolume = false).volumeMode,
        )
    }
}
