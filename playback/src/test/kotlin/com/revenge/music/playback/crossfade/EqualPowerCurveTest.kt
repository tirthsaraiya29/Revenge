package com.revenge.music.playback.crossfade

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualPowerCurveTest {

  @Test
  fun endpointsMuteTheOppositeSide() {
    val start = EqualPowerCurve.calculateGains(0f)
    assertEquals(1f, start.outgoingGain, 1e-3f)
    assertEquals(0f, start.incomingGain, 1e-3f)

    val end = EqualPowerCurve.calculateGains(1f)
    assertEquals(0f, end.outgoingGain, 1e-3f)
    assertEquals(1f, end.incomingGain, 1e-3f)
  }

  @Test
  fun powerStaysConstantAcrossTheFade() {
    for (percent in 0..100) {
      val gains = EqualPowerCurve.calculateGains(percent / 100f)
      val power = gains.outgoingGain * gains.outgoingGain + gains.incomingGain * gains.incomingGain
      assertTrue("power=$power at $percent", abs(power - 1f) < 0.005f)
    }
  }

  @Test
  fun midpointIsEqualAndAboutMinusThreeDb() {
    val middle = EqualPowerCurve.calculateGains(0.5f)
    assertEquals(middle.outgoingGain, middle.incomingGain, 1e-3f)
    assertEquals(0.7071f, middle.outgoingGain, 2e-3f)
  }

  @Test
  fun outOfRangeAndNonFiniteProgressAreClamped() {
    assertEquals(1f, EqualPowerCurve.calculateGains(-2f).outgoingGain, 1e-3f)
    assertEquals(1f, EqualPowerCurve.calculateGains(4f).incomingGain, 1e-3f)
    assertEquals(1f, EqualPowerCurve.calculateGains(Float.NaN).outgoingGain, 1e-3f)
  }
}
