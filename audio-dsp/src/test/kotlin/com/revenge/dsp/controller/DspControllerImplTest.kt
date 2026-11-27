@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.revenge.dsp.controller

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.revenge.dsp.core.models.ParametricEQBand
import com.revenge.dsp.core.models.SavedEQProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DspControllerImplTest {

  private fun format(rate: Int = 48000, channels: Int = 2) =
    AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT)

  private fun profile(id: String) =
    SavedEQProfile(
      id = id,
      name = id,
      bands = listOf(ParametricEQBand(frequency = 1000.0, gain = 3.0)),
    )

  @Test
  fun initialStateIsEmptyAndDisabled() {
    val controller = DspControllerImpl()

    assertNull(controller.activeProfile.value)
    assertFalse(controller.isEnabled.value)
    assertEquals(1f, controller.stereoWidth.value, 0f)
    assertTrue(controller.getAudioProcessors().isEmpty())
    assertFalse(controller.isInitialized())
  }

  @Test
  fun createProcessorsRegistersOrderedChainAndReleaseClearsIt() {
    val controller = DspControllerImpl()
    val trio = controller.createProcessors()

    val chain = controller.getAudioProcessors()
    assertEquals(3, chain.size)
    assertSame(trio.equalizer, chain[0])
    assertSame(trio.duckProcessor, chain[1])
    assertSame(trio.stereoWidener, chain[2])
    assertTrue(controller.isInitialized())

    controller.releaseProcessors(trio)
    assertTrue(controller.getAudioProcessors().isEmpty())
    assertFalse(controller.isInitialized())
  }

  @Test
  fun applyProfileUpdatesFlowsAndProcessorThenDisableClears() {
    val controller = DspControllerImpl()
    val trio = controller.createProcessors()
    trio.equalizer.configure(format())

    val result = controller.applyProfile(profile("rock"))
    assertTrue(result.isSuccess)
    assertEquals("rock", controller.activeProfile.value?.id)
    assertTrue(controller.isEnabled.value)
    assertTrue(trio.equalizer.isEnabled())

    controller.disableEqualizer()
    assertNull(controller.activeProfile.value)
    assertFalse(controller.isEnabled.value)
    assertFalse(trio.equalizer.isEnabled())
  }

  @Test
  fun profileRetainedBeforeRegistrationIsRestoredOnConfigure() {
    val controller = DspControllerImpl()
    val result = controller.applyProfile(profile("held"))
    assertTrue(result.isSuccess)
    assertTrue(controller.getAudioProcessors().isEmpty())

    val trio = controller.createProcessors()
    trio.equalizer.configure(format())
    assertTrue(trio.equalizer.isEnabled())
    assertEquals("held", controller.activeProfile.value?.id)
  }

  @Test
  fun stereoWidthIsClampedAndForwarded() {
    val controller = DspControllerImpl()
    val trio = controller.createProcessors()

    controller.setStereoWidth(1.6f)
    assertEquals(1.6f, controller.stereoWidth.value, 0f)
    assertEquals(1.6f, trio.stereoWidener.width, 0f)

    controller.setStereoWidth(7f)
    assertEquals(2f, controller.stereoWidth.value, 0f)
    assertEquals(2f, trio.stereoWidener.width, 0f)
  }

  @Test
  fun releaseEmptiesRegistries() {
    val controller = DspControllerImpl()
    controller.createProcessors()

    controller.release()
    assertTrue(controller.getAudioProcessors().isEmpty())
    assertFalse(controller.isInitialized())
  }
}
