package com.revenge.dsp.core

import com.revenge.dsp.core.models.FilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class BiquadFilterTest {

  @Test
  fun peakingWithZeroGainLeavesUnitImpulseUnchanged() {
    val filter =
      BiquadFilter(
        sampleRate = 44100,
        frequency = 1000.0,
        gain = 0.0,
        q = 1.41,
        filterType = FilterType.PK,
      )

    assertEquals(1.0, filter.processSample(1.0), 0.001)
  }

  @Test
  fun peakingBoostRaisesFirstSampleAndStaysFinite() {
    val filter =
      BiquadFilter(
        sampleRate = 44100,
        frequency = 1000.0,
        gain = 6.0,
        q = 1.41,
        filterType = FilterType.PK,
      )

    val output = filter.processSample(1.0)
    assertTrue("expected boost above unity", output > 1.0)
    assertFalse(output.isNaN())
    assertFalse(output.isInfinite())
  }

  @Test
  fun stereoChannelsMatchAndResetRestoresInitialState() {
    val filter =
      BiquadFilter(
        sampleRate = 48000,
        frequency = 500.0,
        gain = -3.0,
        q = 1.0,
        filterType = FilterType.LSC,
      )

    val (left, right) = filter.processStereo(0.5, 0.5)
    assertEquals(left, right, 0.0001)

    filter.reset()
    val (afterReset, _) = filter.processStereo(0.5, 0.5)
    assertEquals(left, afterReset, 0.0001)
  }

  @Test
  fun frequencyAtNyquistProducesFiniteOutput() {
    val filter =
      BiquadFilter(
        sampleRate = 44100,
        frequency = 22050.0,
        gain = 3.0,
        q = 1.0,
        filterType = FilterType.HSC,
      )

    val output = filter.processSample(0.5)
    assertFalse(output.isNaN())
    assertFalse(output.isInfinite())
  }

  @Test
  fun lowShelfConvergesToExpectedDcGain() {
    val filter =
      BiquadFilter(
        sampleRate = 48000,
        frequency = 500.0,
        gain = -6.0,
        q = 1.0,
        filterType = FilterType.LSC,
      )

    var output = 0.0
    repeat(20_000) { output = filter.processSample(1.0) }

    assertEquals(10.0.pow(-6.0 / 20.0), output, 0.01)
  }

  @Test
  fun invalidSampleRateFallsBackToFiniteCoefficients() {
    val filter =
      BiquadFilter(
        sampleRate = 0,
        frequency = 1000.0,
        gain = 3.0,
        q = 1.0,
        filterType = FilterType.PK,
      )

    val output = filter.processSample(1.0)
    assertFalse(output.isNaN())
    assertFalse(output.isInfinite())
  }
}
