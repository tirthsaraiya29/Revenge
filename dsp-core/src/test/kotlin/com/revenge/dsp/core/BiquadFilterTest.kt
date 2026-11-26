package com.revenge.dsp.core

import com.revenge.dsp.core.models.FilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiquadFilterTest {

    private val rate = 48000

    @Test
    fun zeroGainPeakingPassesUnitImpulse() {
        val filter = BiquadFilter(rate, 1000.0, 0.0)
        assertEquals(1.0, filter.processSample(1.0), 0.001)
    }

    @Test
    fun peakingBoostRaisesFirstSampleAndStaysFinite() {
        val filter = BiquadFilter(rate, 1000.0, 6.0)
        val first = filter.processSample(1.0)
        assertTrue(first > 1.0)
        assertTrue(first.isFinite())
        var value = first
        repeat(100) { value = filter.processSample(0.0) }
        assertTrue(value.isFinite())
    }

    @Test
    fun stereoChannelsMatchAndResetRestoresResponse() {
        val filter = BiquadFilter(rate, 500.0, -6.0, 1.41, FilterType.LSC)
        val first = filter.processStereo(0.5, 0.5)
        assertEquals(first.first, first.second, 1e-12)

        filter.reset()
        val repeated = filter.processStereo(0.5, 0.5)
        assertEquals(first.first, repeated.first, 1e-12)
        assertEquals(first.second, repeated.second, 1e-12)
    }

    @Test
    fun nyquistFrequencyStaysFinite() {
        val filter = BiquadFilter(rate, rate / 2.0, 3.0, 1.41, FilterType.HSC)
        var value = filter.processSample(1.0)
        repeat(500) { value = filter.processSample(0.0) }
        assertTrue(value.isFinite())
    }

    @Test
    fun lowShelfConvergesToGainOnDc() {
        val filter = BiquadFilter(rate, 500.0, -6.0, 1.41, FilterType.LSC)
        var value = 0.0
        repeat(20_000) { value = filter.processSample(1.0) }
        assertEquals(Math.pow(10.0, -6.0 / 20.0), value, 0.01)
    }

    @Test
    fun invalidSampleRateStaysFinite() {
        val filter = BiquadFilter(0, 1000.0, 3.0)
        var value = filter.processSample(1.0)
        repeat(50) { value = filter.processSample(0.0) }
        assertTrue(value.isFinite())
    }

    @Test
    fun nonFiniteParametersStayFinite() {
        val filter = BiquadFilter(rate, Double.NaN, Double.NaN, Double.NaN)
        var value = filter.processSample(1.0)
        repeat(50) { value = filter.processSample(0.0) }
        assertTrue(value.isFinite())
    }

    @Test
    fun monoProcessingDoesNotDisturbRightHistory() {
        val interleaved = BiquadFilter(rate, 1000.0, 6.0)
        interleaved.processStereo(0.25, 0.75)
        interleaved.processSample(0.5)
        val rightAfterMono = interleaved.processStereo(0.0, 0.0).second

        val reference = BiquadFilter(rate, 1000.0, 6.0)
        reference.processStereo(0.25, 0.75)
        val rightReference = reference.processStereo(123.0, 0.0).second

        assertEquals(rightReference, rightAfterMono, 1e-12)
    }
}
