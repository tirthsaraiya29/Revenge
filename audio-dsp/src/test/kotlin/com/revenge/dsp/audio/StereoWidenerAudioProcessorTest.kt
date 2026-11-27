@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.revenge.dsp.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StereoWidenerAudioProcessorTest {

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

  @Test
  fun widthSetterClampsToDocumentedRange() {
    val processor = StereoWidenerAudioProcessor()
    assertEquals(1f, processor.width, 0f)

    processor.width = 0.25f
    assertEquals(1f, processor.width, 0f)

    processor.width = 1.5f
    assertEquals(1.5f, processor.width, 0f)

    processor.width = 9f
    assertEquals(2f, processor.width, 0f)
  }

  @Test
  fun widthOneRecombinesStereoImageLosslessly() {
    val processor = StereoWidenerAudioProcessor()
    processor.width = 1f
    processor.configure(format())

    processor.queueInput(pcmBuffer(1000, -500, -3000, 2500))
    assertArrayEquals(shortArrayOf(1000, -500, -3000, 2500), readPcm(processor.getOutput()))
  }

  @Test
  fun widthOnePointFiveMapsMidSideExample() {
    val processor = StereoWidenerAudioProcessor()
    processor.width = 1.5f
    processor.configure(format())

    processor.queueInput(pcmBuffer(1000, 500))
    assertArrayEquals(shortArrayOf(1125, 375), readPcm(processor.getOutput()))
  }

  @Test
  fun widthTwoKeepsExtremesInsideRangeAndPreservesSigns() {
    val processor = StereoWidenerAudioProcessor()
    processor.width = 2f
    processor.configure(format())

    processor.queueInput(pcmBuffer(32767, -32768))
    val output = readPcm(processor.getOutput())
    assertTrue(output[0] > 0)
    assertTrue(output[1] < 0)
    assertTrue(output[0] in 1..32767)
    assertTrue(output[1] in -32768..-1)
  }

  @Test
  fun monoPassesThroughUntouched() {
    val processor = StereoWidenerAudioProcessor()
    processor.width = 2f
    processor.configure(format(channels = 1))

    processor.queueInput(pcmBuffer(1000, -2000))
    assertArrayEquals(shortArrayOf(1000, -2000), readPcm(processor.getOutput()))
  }

  @Test
  fun configurationLifecycleAndRejection() {
    val processor = StereoWidenerAudioProcessor()
    assertFalse(processor.isActive())

    assertThrows(AudioProcessor.UnhandledAudioFormatException::class.java) {
      processor.configure(format(channels = 6))
    }

    processor.configure(format())
    assertTrue(processor.isActive())

    processor.reset()
    assertFalse(processor.isActive())
  }
}
