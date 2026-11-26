package com.revenge.dsp.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.revenge.dsp.core.models.FilterType
import com.revenge.dsp.core.models.ParametricEQ
import com.revenge.dsp.core.models.ParametricEQBand
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(UnstableApi::class)
class CustomEqualizerAudioProcessorTest {

  private lateinit var processor: CustomEqualizerAudioProcessor

  @Before
  fun setUp() {
    processor = CustomEqualizerAudioProcessor()
  }

  @Test
  fun `unconfigured profile is held and installed on configure`() {
    processor.applyProfile(ParametricEQ(preamp = 0.0, bands = emptyList()))
    assertFalse(processor.isEnabled())

    processor.configure(AudioFormat(48000, 2))

    assertTrue(processor.isEnabled())
    assertTrue(processor.isActive)
  }

  @Test
  fun `disabled processor copies input unchanged`() {
    processor.configure(AudioFormat(44100, 2))
    assertFalse(processor.isEnabled())

    val out = run(shortArrayOf(1234, -5678))

    assertEquals(1234, out[0].toInt())
    assertEquals(-5678, out[1].toInt())
  }

  @Test
  fun `preamp scales samples`() {
    processor.configure(AudioFormat(44100, 1))
    processor.applyProfile(ParametricEQ(preamp = 6.0206, bands = emptyList()))

    val out = run(shortArrayOf(1000))

    assertTrue("expected about 2000, was ${out[0]}", out[0].toInt() in 1990..2010)
  }

  @Test
  fun `disable after apply restores bypass`() {
    processor.configure(AudioFormat(44100, 2))
    processor.applyProfile(ParametricEQ(preamp = -6.0, bands = emptyList()))
    processor.disable()

    val out = run(shortArrayOf(3000, 3000))

    assertEquals(3000, out[0].toInt())
    assertEquals(3000, out[1].toInt())
  }

  @Test
  fun `enabled band alters a matching tone`() {
    processor.configure(AudioFormat(48000, 1))
    processor.applyProfile(
      ParametricEQ(
        preamp = 0.0,
        bands =
          listOf(
            ParametricEQBand(
              frequency = 1000.0,
              gain = 12.0,
              q = 1.41,
              filterType = FilterType.PK,
              enabled = true,
            ),
          ),
      ),
    )

    val input = ShortArray(512) { (kotlin.math.sin(it * 0.13) * 2000).toInt().toShort() }
    val out = run(input)

    val energyIn = input.sumOf { it.toDouble() * it }
    val energyOut = out.sumOf { it.toDouble() * it }
    assertTrue("expected gain, in=$energyIn out=$energyOut", energyOut > energyIn)
  }

  @Test(expected = AudioProcessor.UnhandledAudioFormatException::class)
  fun `rejects more than two channels`() {
    processor.configure(AudioFormat(44100, 6))
  }

  @Test(expected = AudioProcessor.UnhandledAudioFormatException::class)
  fun `rejects non 16 bit pcm`() {
    processor.configure(
      AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT),
    )
  }

  @Test
  fun `flush keeps configuration usable`() {
    processor.configure(AudioFormat(44100, 2))
    @Suppress("DEPRECATION") processor.flush()
    assertFalse(processor.isEnded)

    val out = run(shortArrayOf(500, -500))
    assertEquals(500, out[0].toInt())
    assertEquals(-500, out[1].toInt())
  }

  private fun run(samples: ShortArray): ShortArray {
    val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
    samples.forEach { input.putShort(it) }
    input.flip()

    processor.queueInput(input)
    val output = processor.getOutput()
    val result = ShortArray(output.remaining() / 2) { output.getShort() }
    assertTrue(processor.getOutput().remaining() == 0)
    return result
  }

  private fun AudioFormat(sampleRate: Int, channels: Int) =
    AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_16BIT)
}
