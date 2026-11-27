package com.revenge.playback.crossfade

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualPowerCurveTest {

  @Test
  fun endpointsAreUnityAndSilence() {
    assertEquals(1f, EqualPowerCurve.calculateGains(0f).outgoingGain, 1e-3f)
    assertEquals(0f, EqualPowerCurve.calculateGains(0f).incomingGain, 1e-3f)
    assertEquals(0f, EqualPowerCurve.calculateGains(1f).outgoingGain, 1e-3f)
    assertEquals(1f, EqualPowerCurve.calculateGains(1f).incomingGain, 1e-3f)
  }

  @Test
  fun gainsStayOnTheUnitCircle() {
    for (percent in 0..100) {
      val gains = EqualPowerCurve.calculateGains(percent / 100f)
      val power = gains.outgoingGain * gains.outgoingGain + gains.incomingGain * gains.incomingGain
      assertEquals(1f, power, 0.005f)
    }
  }

  @Test
  fun midpointIsMinusThreeDecibels() {
    val gains = EqualPowerCurve.calculateGains(0.5f)

    assertEquals(0.7071f, gains.outgoingGain, 2e-3f)
    assertEquals(0.7071f, gains.incomingGain, 2e-3f)
    assertTrue(abs(gains.outgoingGain - gains.incomingGain) < 1e-6f)
  }

  @Test
  fun outOfRangeAndNonFiniteProgressClampSafely() {
    val negative = EqualPowerCurve.calculateGains(-2f)
    assertEquals(1f, negative.outgoingGain, 1e-3f)
    assertEquals(0f, negative.incomingGain, 1e-3f)

    val large = EqualPowerCurve.calculateGains(4f)
    assertEquals(0f, large.outgoingGain, 1e-3f)
    assertEquals(1f, large.incomingGain, 1e-3f)

    for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
      val gains = EqualPowerCurve.calculateGains(value)
      assertEquals(1f, gains.outgoingGain, 1e-3f)
      assertEquals(0f, gains.incomingGain, 1e-3f)
    }
  }
}
