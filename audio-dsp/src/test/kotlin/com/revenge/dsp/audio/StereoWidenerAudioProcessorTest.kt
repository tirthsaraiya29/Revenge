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
class StereoWidenerAudioProcessorTest {

  private lateinit var processor: StereoWidenerAudioProcessor

  @Before
  fun setUp() {
    processor = StereoWidenerAudioProcessor()
  }

  @Test
  fun `width is clamped to the supported range`() {
    processor.width = 0.25f
    assertEquals(1f, processor.width, 0.0001f)

    processor.width = 1.5f
    assertEquals(1.5f, processor.width, 0.0001f)

    processor.width = 9f
    assertEquals(2f, processor.width, 0.0001f)
  }

  @Test
  fun `width one recombines losslessly`() {
    processor.configure(format(2))
    processor.width = 1f

    val out = run(shortArrayOf(1000, -500, -3000, 2500))

    assertEquals(1000, out[0].toInt())
    assertEquals(-500, out[1].toInt())
    assertEquals(-3000, out[2].toInt())
    assertEquals(2500, out[3].toInt())
  }

  @Test
  fun `widening pushes channels apart`() {
    processor.configure(format(2))
    processor.width = 1.5f

    val out = run(shortArrayOf(1000, 500))

    assertEquals(1125, out[0].toInt())
    assertEquals(375, out[1].toInt())
  }

  @Test
  fun `peaks beyond full scale are scaled down together`() {
    processor.configure(format(2))
    processor.width = 2f

    val out = run(shortArrayOf(32767, -32768))

    val left = out[0].toInt()
    val right = out[1].toInt()
    assertTrue(left <= 32767 && left >= -32768)
    assertTrue(right <= 32767 && right >= -32768)
    // Mid/side recombine around the original centre; nothing should flip sign.
    assertTrue(left > 0 && right < 0)
  }

  @Test
  fun `mono is passed through`() {
    processor.configure(format(1))
    processor.width = 2f

    val out = run(shortArrayOf(1234, -4321))

    assertEquals(1234, out[0].toInt())
    assertEquals(-4321, out[1].toInt())
  }

  @Test(expected = AudioProcessor.UnhandledAudioFormatException::class)
  fun `rejects more than two channels`() {
    processor.configure(format(6))
  }

  @Test
  fun `active only after configure and reset clears it`() {
    assertFalse(processor.isActive)
    processor.configure(format(2))
    assertTrue(processor.isActive)

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
