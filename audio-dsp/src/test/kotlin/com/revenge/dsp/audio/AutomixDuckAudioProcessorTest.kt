package com.revenge.dsp.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(UnstableApi::class)
class AutomixDuckAudioProcessorTest {

  private lateinit var processor: AutomixDuckAudioProcessor

  @Before
  fun setUp() {
    processor = AutomixDuckAudioProcessor()
  }

  @Test
  fun `no duck leaves the signal untouched`() {
    processor.configure(format(2))
    processor.setMix(0f)
    processor.resetGain()

    val out = run(shortArrayOf(1000, -1000, 2000, -2000))

    assertEquals(1000, out[0].toInt())
    assertEquals(-1000, out[1].toInt())
    assertEquals(2000, out[2].toInt())
    assertEquals(-2000, out[3].toInt())
  }

  @Test
  fun `full duck attenuates a low tone`() {
    processor.configure(format(1))
    processor.setMix(1f)

    // A DC-ish low signal is inside the 150 Hz shelf, so it must lose level.
    val input = ShortArray(256) { 8000 }
    val output = run(input)

    val last = output.last().toInt()
    assertTrue("expected ducked output, was $last", last < 8000)
    assertTrue("expected ducked output, was $last", last > 0)
  }

  @Test
  fun `mix is clamped and reset releases it`() {
    processor.configure(format(2))
    processor.setMix(5f)
    processor.resetGain()

    val out = run(shortArrayOf(3000, -3000))
    assertEquals(3000, out[0].toInt())
    assertEquals(-3000, out[1].toInt())
  }

  @Test(expected = AudioProcessor.UnhandledAudioFormatException::class)
  fun `rejects more than two channels`() {
    processor.configure(format(6))
  }

  @Test(expected = AudioProcessor.UnhandledAudioFormatException::class)
  fun `rejects non 16 bit pcm`() {
    processor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT))
  }

  @Test
  fun `stream end and reset lifecycle`() {
    processor.configure(format(2))
    assertTrue(processor.isActive)

    processor.queueEndOfStream()
    assertTrue(processor.isEnded)

    processor.reset()
    assertFalse(processor.isActive)
  }

  private fun run(samples: ShortArray): ShortArray {
    val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
    samples.forEach { input.putShort(it) }
    input.flip()

    processor.queueInput(input)
    val output = processor.getOutput()
    return ShortArray(output.remaining() / 2) { output.getShort() }
  }

  private fun format(channels: Int) =
    AudioProcessor.AudioFormat(44100, channels, C.ENCODING_PCM_16BIT)
}
