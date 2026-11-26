@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.revenge.dsp.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.revenge.dsp.core.models.ParametricEQ
import com.revenge.dsp.core.models.ParametricEQBand
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomEqualizerAudioProcessorTest {

    private fun format(rate: Int = 48000, channels: Int = 2, encoding: Int = C.ENCODING_PCM_16BIT) =
        AudioProcessor.AudioFormat(rate, channels, encoding)

    private fun pcmBuffer(vararg samples: Short): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach(buffer::putShort)
        buffer.flip()
        return buffer
    }

    private fun readPcm(buffer: ByteBuffer): ShortArray {
        val view = buffer.duplicate().order(ByteOrder.nativeOrder())
        val values = ShortArray(view.remaining() / 2)
        for (index in values.indices) values[index] = view.getShort()
        return values
    }

    private fun profile(preamp: Double, vararg bands: ParametricEQBand) =
        ParametricEQ(preamp = preamp, bands = bands.toList())

    @Test
    fun profileAppliedBeforeConfigureIsHeldThenInstalled() {
        val processor = CustomEqualizerAudioProcessor()
        processor.applyProfile(profile(0.0, ParametricEQBand(frequency = 1000.0, gain = 3.0)))
        assertFalse(processor.isEnabled())

        processor.configure(format(channels = 2))
        assertTrue(processor.isEnabled())
        assertTrue(processor.isActive())
    }

    @Test
    fun disabledProcessorCopiesSamplesByteForByte() {
        val processor = CustomEqualizerAudioProcessor()
        processor.configure(format(channels = 1))

        processor.queueInput(pcmBuffer(1234, -5678))
        val output = readPcm(processor.getOutput())
        assertArrayEquals(shortArrayOf(1234, -5678), output)
    }

    @Test
    fun preampBoostScalesSamples() {
        val processor = CustomEqualizerAudioProcessor()
        processor.applyProfile(profile(6.0206))
        processor.configure(format(channels = 1))

        processor.queueInput(pcmBuffer(1000))
        val value = readPcm(processor.getOutput()).first().toInt()
        assertTrue(value in 1990..2010)
    }

    @Test
    fun disableRestoresBypassAfterPreampAttenuation() {
        val processor = CustomEqualizerAudioProcessor()
        processor.applyProfile(profile(-6.0))
        processor.configure(format(channels = 1))

        processor.queueInput(pcmBuffer(3000))
        val attenuated = readPcm(processor.getOutput()).first().toInt()
        assertTrue(attenuated < 3000)

        processor.disable()
        processor.queueInput(pcmBuffer(3000))
        assertEquals(3000, readPcm(processor.getOutput()).first().toInt())
    }

    @Test
    fun peakingBoostRaisesEnergyOfMatchingSine() {
        val processor = CustomEqualizerAudioProcessor()
        processor.applyProfile(
            profile(0.0, ParametricEQBand(frequency = 1000.0, gain = 12.0, q = 1.41))
        )
        processor.configure(format(channels = 1))

        val frames = 4800
        val input = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder())
        var inputEnergy = 0.0
        for (index in 0 until frames) {
            val angle = 2.0 * PI * 1000.0 * index.toDouble() / 48000.0
            val sample = (8000.0 * sin(angle)).toInt().toShort()
            inputEnergy += sample.toDouble() * sample.toDouble()
            input.putShort(sample)
        }
        input.flip()

        processor.queueInput(input)
        val output = readPcm(processor.getOutput())
        var outputEnergy = 0.0
        for (sample in output) outputEnergy += sample.toDouble() * sample.toDouble()

        assertTrue(outputEnergy > inputEnergy)
    }

    @Test
    fun unsupportedFormatsAreRejected() {
        val processor = CustomEqualizerAudioProcessor()
        assertThrows(AudioProcessor.UnhandledAudioFormatException::class.java) {
            processor.configure(format(channels = 4))
        }
        assertThrows(AudioProcessor.UnhandledAudioFormatException::class.java) {
            processor.configure(format(encoding = C.ENCODING_PCM_FLOAT))
        }
    }

    @Test
    fun deprecatedFlushLeavesStageUsable() {
        val processor = CustomEqualizerAudioProcessor()
        processor.configure(format(channels = 1))
        processor.flush()

        assertFalse(processor.isEnded())
        processor.queueInput(pcmBuffer(777))
        assertEquals(777, readPcm(processor.getOutput()).first().toInt())
    }

    @Test
    fun secondGetOutputReturnsEmptyBuffer() {
        val processor = CustomEqualizerAudioProcessor()
        processor.configure(format(channels = 1))
        processor.queueInput(pcmBuffer(10))

        val first = processor.getOutput()
        assertTrue(first.hasRemaining())
        val second = processor.getOutput()
        assertEquals(0, second.remaining())
    }
}
