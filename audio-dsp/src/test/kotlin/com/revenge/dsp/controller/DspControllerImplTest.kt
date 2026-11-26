package com.revenge.dsp.controller

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.revenge.dsp.core.models.SavedEQProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(UnstableApi::class)
class DspControllerImplTest {

  private lateinit var controller: DspControllerImpl

  @Before
  fun setUp() {
    controller = DspControllerImpl()
  }

  @Test
  fun `initial state is disabled and neutral`() {
    assertNull(controller.activeProfile.value)
    assertFalse(controller.isEnabled.value)
    assertEquals(1f, controller.stereoWidth.value, 0.0001f)
    assertTrue(controller.getAudioProcessors().isEmpty())
    assertFalse(controller.isInitialized())
  }

  @Test
  fun `created processors are registered, ordered and released`() {
    val trio = controller.createProcessors()

    assertTrue(controller.isInitialized())
    assertEquals(3, controller.getAudioProcessors().size)
    assertEquals(trio.equalizer, controller.getAudioProcessors()[0])
    assertEquals(trio.duckProcessor, controller.getAudioProcessors()[1])
    assertEquals(trio.stereoWidener, controller.getAudioProcessors()[2])

    controller.releaseProcessors(trio)
    assertFalse(controller.isInitialized())
    assertTrue(controller.getAudioProcessors().isEmpty())
  }

  @Test
  fun `apply profile reaches a configured processor`() {
    val trio = controller.createProcessors()
    trio.equalizer.configure(AudioFormat(44100, 2))

    val profile =
      SavedEQProfile(
        id = "flat",
        name = "Flat",
        preamp = -2.0,
        bands = emptyList(),
      )

    val result = controller.applyProfile(profile)

    assertTrue(result.isSuccess)
    assertEquals("flat", controller.activeProfile.value?.id)
    assertTrue(controller.isEnabled.value)
    assertTrue(trio.equalizer.isEnabled())

    controller.disableEqualizer()

    assertNull(controller.activeProfile.value)
    assertFalse(controller.isEnabled.value)
    assertFalse(trio.equalizer.isEnabled())
  }

  @Test
  fun `profile applied before registration is restored on configure`() {
    controller.applyProfile(SavedEQProfile(id = "early", name = "Early", preamp = 1.0))
    assertTrue(controller.isEnabled.value)

    val trio = controller.createProcessors()
    assertFalse(trio.equalizer.isEnabled())

    trio.equalizer.configure(AudioFormat(48000, 2))
    assertTrue(trio.equalizer.isEnabled())
  }

  @Test
  fun `stereo width fans out to registered wideners`() {
    val trio = controller.createProcessors()

    controller.setStereoWidth(1.6f)

    assertEquals(1.6f, controller.stereoWidth.value, 0.0001f)
    assertEquals(1.6f, trio.stereoWidener.width, 0.0001f)

    controller.setStereoWidth(7f)
    assertEquals(2f, trio.stereoWidener.width, 0.0001f)
  }

  @Test
  fun `release clears all processors`() {
    controller.createProcessors()
    controller.release()

    assertTrue(controller.getAudioProcessors().isEmpty())
    assertFalse(controller.isInitialized())
  }

  private fun AudioFormat(rate: Int, channels: Int) =
    AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT)
}
