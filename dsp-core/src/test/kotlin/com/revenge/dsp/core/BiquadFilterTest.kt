package com.revenge.dsp.core

import com.revenge.dsp.core.models.FilterType
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiquadFilterTest {

    @Test
    fun zeroGainPeakingIsTransparentAtImpulse() {
        val filter = BiquadFilter(44100, 1000.0, 0.0, 1.41, FilterType.PK)
        assertEquals(1.0, filter.processSample(1.0), 1e-3)
    }

    @Test
    fun positivePeakingGainBoostsFirstSample() {
        val filter = BiquadFilter(44100, 1000.0, 6.0, 1.41, FilterType.PK)
        val output = filter.processSample(1.0)
        assertTrue(output > 1.0)
        assertTrue(output.isFinite())
    }

    @Test
    fun stereoIsSymmetricWithSymmetricHistory() {
        val filter = BiquadFilter(48000, 800.0, 4.0, 1.0, FilterType.PK)
        repeat(10) {
            val (left, right) = filter.processStereo(0.3, 0.3)
            assertEquals(left, right, 1e-4)
        }
    }

    @Test
    fun resetReproducesOutputs() {
        val filter = BiquadFilter(44100, 2500.0, -5.0, 2.0, FilterType.PK)
        val first = filter.processStereo(0.7, -0.2)
        filter.reset()
        val second = filter.processStereo(0.7, -0.2)
        assertEquals(first.first, second.first, 1e-4)
        assertEquals(first.second, second.second, 1e-4)
    }

    @Test
    fun nyquistFrequencyProducesFiniteOutput() {
        val sampleRate = 44100
        val filter = BiquadFilter(sampleRate, sampleRate / 2.0, 3.0, 1.0, FilterType.HSC)
        val output = filter.processSample(1.0)
        assertTrue(output.isFinite())
        assertFalse(output.isNaN())
    }

    @Test
    fun invalidSampleRateProducesFiniteOutput() {
        val filter = BiquadFilter(0, 1000.0, 3.0, 1.0, FilterType.PK)
        val output = filter.processSample(1.0)
        assertTrue(output.isFinite())
        assertFalse(output.isNaN())
    }

    @Test
    fun lowShelfReachesExpectedDcGain() {
        val filter = BiquadFilter(48000, 500.0, -6.0, 1.0, FilterType.LSC)
        var output = 0.0
        repeat(20000) { output = filter.processSample(1.0) }
        assertEquals(10.0.pow(-6.0 / 20.0), output, 0.01)
    }

    @Test
    fun highShelfKeepsDcGainAtUnity() {
        val filter = BiquadFilter(48000, 4000.0, 6.0, 0.71, FilterType.HSC)
        var output = 0.0
        repeat(20000) { output = filter.processSample(1.0) }
        assertEquals(1.0, output, 0.02)
    }

    @Test
    fun negativePeakingGainReducesSineEnergy() {
        val sampleRate = 44100
        val filter = BiquadFilter(sampleRate, 1000.0, -12.0, 1.41, FilterType.PK)
        var outputEnergy = 0.0
        var comparedSamples = 0
        for (n in 0 until 44100) {
            val input = sin(2.0 * PI * 1000.0 * n / sampleRate)
            val output = filter.processSample(input)
            if (n >= 22050) {
                outputEnergy += output * output
                comparedSamples++
            }
        }
        val outputRms = kotlin.math.sqrt(outputEnergy / comparedSamples)
        val inputRms = kotlin.math.sqrt(0.5)
        assertTrue(outputRms.isFinite())
        assertTrue(outputRms < inputRms * 0.5)
    }

    @Test
    fun lpqAndHpqMatchPeakingResponse() {
        val pk = BiquadFilter(44100, 1500.0, 5.0, 1.2, FilterType.PK)
        val lpq = BiquadFilter(44100, 1500.0, 5.0, 1.2, FilterType.LPQ)
        val hpq = BiquadFilter(44100, 1500.0, 5.0, 1.2, FilterType.HPQ)
        repeat(32) {
            val input = if (it == 0) 1.0 else 0.0
            val pkOut = pk.processSample(input)
            assertEquals(pkOut, lpq.processSample(input), 0.0)
            assertEquals(pkOut, hpq.processSample(input), 0.0)
        }
    }
}
