package com.revenge.dsp.controller

import androidx.media3.common.audio.AudioProcessor
import com.revenge.dsp.audio.AutomixDuckAudioProcessor
import com.revenge.dsp.audio.CustomEqualizerAudioProcessor
import com.revenge.dsp.audio.StereoWidenerAudioProcessor
import com.revenge.dsp.core.models.SavedEQProfile
import kotlinx.coroutines.flow.StateFlow

/** The three processors that belong to one ExoPlayer instance. */
data class PlayerDspProcessors(
  val equalizer: CustomEqualizerAudioProcessor,
  val duckProcessor: AutomixDuckAudioProcessor,
  val stereoWidener: StereoWidenerAudioProcessor,
) {
  fun asList(): List<AudioProcessor> = listOf(equalizer, duckProcessor, stereoWidener)
}

/**
 * Single owner of the DSP state shared by every player in the service. Profile and width changes
 * are fanned out to all currently registered processors.
 */
interface DspController {
  val activeProfile: StateFlow<SavedEQProfile?>
  val isEnabled: StateFlow<Boolean>
  val stereoWidth: StateFlow<Float>

  /** All registered processors in chain order: EQ, duck, widener. */
  fun getAudioProcessors(): List<AudioProcessor>

  /** Creates and registers a matching trio for one player's audio sink. */
  fun createProcessors(): PlayerDspProcessors

  /** Unregisters a trio previously returned by [createProcessors]. */
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
