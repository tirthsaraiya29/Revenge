@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.revenge.dsp.controller

import androidx.media3.common.audio.AudioProcessor
import com.revenge.dsp.audio.AutomixDuckAudioProcessor
import com.revenge.dsp.audio.CustomEqualizerAudioProcessor
import com.revenge.dsp.audio.StereoWidenerAudioProcessor
import com.revenge.dsp.core.models.SavedEQProfile
import kotlinx.coroutines.flow.StateFlow

data class PlayerDspProcessors(
  val equalizer: CustomEqualizerAudioProcessor,
  val duckProcessor: AutomixDuckAudioProcessor,
  val stereoWidener: StereoWidenerAudioProcessor,
) {
  fun asList(): List<AudioProcessor> = listOf(equalizer, duckProcessor, stereoWidener)
}

interface DspController {
  val activeProfile: StateFlow<SavedEQProfile?>
  val isEnabled: StateFlow<Boolean>
  val stereoWidth: StateFlow<Float>

  fun getAudioProcessors(): List<AudioProcessor>

  fun createProcessors(): PlayerDspProcessors

  fun releaseProcessors(processors: PlayerDspProcessors)

  fun addAudioProcessor(processor: CustomEqualizerAudioProcessor)

  fun removeAudioProcessor(processor: CustomEqualizerAudioProcessor)

  fun addStereoWidener(processor: StereoWidenerAudioProcessor)

  fun removeStereoWidener(processor: StereoWidenerAudioProcessor)

  fun addDuckProcessor(processor: AutomixDuckAudioProcessor)

  fun removeDuckProcessor(processor: AutomixDuckAudioProcessor)

  fun applyProfile(profile: SavedEQProfile): Result<Unit>

  fun disable()

  fun disableEqualizer()

  fun setStereoWidth(width: Float)

  fun setAutoMixDuck(duckFactor: Float)

  fun isInitialized(): Boolean

  fun release()
}
