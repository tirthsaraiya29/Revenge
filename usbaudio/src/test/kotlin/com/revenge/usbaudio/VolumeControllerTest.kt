package com.revenge.usbaudio

import com.revenge.usbaudio.model.VolumeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeControllerTest {

  @Test
  fun logarithmicCurveHitsEndpointsAndMidpoint() {
    val controller = VolumeController(hasHardwareVolume = true, minDb = -60.0f, maxDb = 0.0f)

    assertEquals(0.0f, controller.calculateDbForSlider(1.0f), 0.01f)
    assertEquals(-60.0f, controller.calculateDbForSlider(0.0f), 0.01f)
    assertTrue(controller.calculateDbForSlider(0.5f) in -22.0f..-15.0f)
    assertEquals(-60.0f, controller.calculateDbForSlider(-1.0f), 0.01f)
    assertEquals(0.0f, controller.calculateDbForSlider(2.0f), 0.01f)
  }

  @Test
  fun decibelsConvertToUacUnitsAndClamp() {
    val controller = VolumeController(hasHardwareVolume = true)

    assertEquals((-18 * 256), controller.dbToUacMillibel(-18.0f).toInt())
    assertEquals(Short.MAX_VALUE.toInt(), controller.dbToUacMillibel(500.0f).toInt())
    assertEquals(Short.MIN_VALUE.toInt(), controller.dbToUacMillibel(-500.0f).toInt())
  }

  @Test
  fun bitPerfectAndHardwareModesLeaveSamplesUntouched() {
    val controller = VolumeController(hasHardwareVolume = false)
    controller.setVolume(0.25f)

    controller.volumeMode = VolumeMode.PURE_BIT_PERFECT
    assertEquals(1.0, controller.getEffectiveSoftwareMultiplier(), 0.0001)

    controller.volumeMode = VolumeMode.HARDWARE
    assertEquals(1.0, controller.getEffectiveSoftwareMultiplier(), 0.0001)
  }

  @Test
  fun softwareModeAppliesLogarithmicAttenuation() {
    val controller = VolumeController(hasHardwareVolume = false)
    controller.volumeMode = VolumeMode.SOFTWARE_64BIT

    controller.setVolume(1.0f)
    assertEquals(1.0, controller.getEffectiveSoftwareMultiplier(), 0.0001)

    controller.setVolume(0.5f)
    val expected = Math.pow(10.0, controller.calculateDbForSlider(0.5f).toDouble() / 20.0)
    assertEquals(expected, controller.getEffectiveSoftwareMultiplier(), 0.0001)
    assertTrue(controller.getEffectiveSoftwareMultiplier() < 1.0)
  }

  @Test
  fun defaultModeFollowsHardwareAvailability() {
    assertEquals(
      VolumeMode.HARDWARE,
      VolumeController(hasHardwareVolume = true).volumeMode,
    )
    assertEquals(
      VolumeMode.PURE_BIT_PERFECT,
      VolumeController(hasHardwareVolume = false).volumeMode,
    )
  }
}
