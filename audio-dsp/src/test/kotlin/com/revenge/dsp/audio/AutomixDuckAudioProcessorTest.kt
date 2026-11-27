@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.revenge.dsp.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomixDuckAudioProcessorTest {

  private fun format(rate: Int = 48000, channels: Int = 1, encoding: Int = C.ENCODING_PCM_16BIT) =
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

  private fun constantBuffer(sample: Short, count: Int): ByteBuffer {
    val buffer = ByteBuffer.allocateDirect(count * 2).order(ByteOrder.nativeOrder())
    repeat(count) { buffer.putShort(sample) }
    buffer.flip()
    return buffer
  }

  @Test
  fun mixZeroLeavesSamplesUnchanged() {
    val processor = AutomixDuckAudioProcessor()
    processor.configure(format())
    processor.setMix(0f)
    processor.resetGain()

    processor.queueInput(pcmBuffer(1000, -2000))
    assertArrayEquals(shortArrayOf(1000, -2000), readPcm(processor.getOutput()))
  }

  @Test
  fun fullMixAttenuatesLowToneWithoutFlippingSign() {
    val processor = AutomixDuckAudioProcessor()
    processor.configure(format())
    processor.setMix(1f)

    processor.queueInput(constantBuffer(8000, 20_000))
    val output = readPcm(processor.getOutput())
    val last = output.last().toInt()
    assertTrue(last in 1 until 8000)
  }

  @Test
  fun clampedMixThenResetGainLeavesSamplesUnchanged() {
    val processor = AutomixDuckAudioProcessor()
    processor.configure(format())
    processor.setMix(5f)
    processor.resetGain()

    processor.queueInput(pcmBuffer(500, -500))
    assertArrayEquals(shortArrayOf(500, -500), readPcm(processor.getOutput()))
  }

  @Test
  fun unsupportedFormatsAreRejected() {
    val processor = AutomixDuckAudioProcessor()
    assertThrows(AudioProcessor.UnhandledAudioFormatException::class.java) {
      processor.configure(format(channels = 6))
    }
    assertThrows(AudioProcessor.UnhandledAudioFormatException::class.java) {
      processor.configure(format(encoding = C.ENCODING_PCM_FLOAT))
    }
  }

  @Test
  fun endOfStreamAndResetStates() {
    val processor = AutomixDuckAudioProcessor()
    processor.configure(format())
    processor.queueEndOfStream()
    assertTrue(processor.isEnded())

    processor.reset()
    assertFalse(processor.isActive())
  }
}
