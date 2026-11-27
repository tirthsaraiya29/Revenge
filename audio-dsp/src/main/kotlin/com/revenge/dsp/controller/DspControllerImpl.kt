@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.revenge.dsp.controller

import androidx.media3.common.audio.AudioProcessor
import com.revenge.dsp.audio.AutomixDuckAudioProcessor
import com.revenge.dsp.audio.CustomEqualizerAudioProcessor
import com.revenge.dsp.audio.StereoWidenerAudioProcessor
import com.revenge.dsp.core.models.SavedEQProfile
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

@Singleton
class DspControllerImpl @Inject constructor() : DspController {

  private val equalizers = CopyOnWriteArrayList<CustomEqualizerAudioProcessor>()
  private val ducks = CopyOnWriteArrayList<AutomixDuckAudioProcessor>()
  private val wideners = CopyOnWriteArrayList<StereoWidenerAudioProcessor>()

  private val activeProfileState = MutableStateFlow<SavedEQProfile?>(null)
  override val activeProfile: StateFlow<SavedEQProfile?> = activeProfileState.asStateFlow()

  private val enabledState = MutableStateFlow(false)
  override val isEnabled: StateFlow<Boolean> = enabledState.asStateFlow()

  private val widthState = MutableStateFlow(1f)
  override val stereoWidth: StateFlow<Float> = widthState.asStateFlow()

  private var retainedProfile: SavedEQProfile? = null
  private var disableRetained: Boolean = false

  override fun getAudioProcessors(): List<AudioProcessor> {
    val chain = ArrayList<AudioProcessor>(3)
    equalizers.firstOrNull()?.let { chain.add(it) }
    ducks.firstOrNull()?.let { chain.add(it) }
    wideners.firstOrNull()?.let { chain.add(it) }
    return chain
  }

  override fun createProcessors(): PlayerDspProcessors {
    val equalizer = CustomEqualizerAudioProcessor()
    val duck = AutomixDuckAudioProcessor()
    val widener = StereoWidenerAudioProcessor()
    addAudioProcessor(equalizer)
    addDuckProcessor(duck)
    addStereoWidener(widener)
    return PlayerDspProcessors(equalizer, duck, widener)
  }

  override fun releaseProcessors(processors: PlayerDspProcessors) {
    equalizers.remove(processors.equalizer)
    ducks.remove(processors.duckProcessor)
    wideners.remove(processors.stereoWidener)
  }

  @Synchronized
  override fun addAudioProcessor(processor: CustomEqualizerAudioProcessor) {
    if (!equalizers.contains(processor)) {
      equalizers.add(processor)
    }
    when {
      disableRetained -> {
        runCatching { processor.disable() }.onFailure { Timber.e(it, "Failed to disable EQ") }
      }
      retainedProfile != null -> {
        val profile = retainedProfile!!
        runCatching { processor.applyProfile(profile.parametricEQ) }
          .onFailure { Timber.e(it, "Failed to restore EQ profile") }
      }
    }
  }

  override fun removeAudioProcessor(processor: CustomEqualizerAudioProcessor) {
    equalizers.remove(processor)
  }

  @Synchronized
  override fun addStereoWidener(processor: StereoWidenerAudioProcessor) {
    if (!wideners.contains(processor)) {
      wideners.add(processor)
    }
    processor.width = widthState.value
  }

  override fun removeStereoWidener(processor: StereoWidenerAudioProcessor) {
    wideners.remove(processor)
  }

  @Synchronized
  override fun addDuckProcessor(processor: AutomixDuckAudioProcessor) {
    if (!ducks.contains(processor)) {
      ducks.add(processor)
    }
  }

  override fun removeDuckProcessor(processor: AutomixDuckAudioProcessor) {
    ducks.remove(processor)
  }

  @Synchronized
  override fun applyProfile(profile: SavedEQProfile): Result<Unit> {
    retainedProfile = profile
    disableRetained = false
    activeProfileState.value = profile
    enabledState.value = true

    var firstFailure: Throwable? = null
    for (processor in equalizers) {
      runCatching { processor.applyProfile(profile.parametricEQ) }
        .onFailure { error ->
          Timber.e(error, "Failed to apply EQ profile")
          if (firstFailure == null) firstFailure = error
        }
    }
    val failure = firstFailure
    return if (failure != null) {
      Result.failure(failure)
    } else {
      Result.success(Unit)
    }
  }

  override fun disable() {
    disableEqualizer()
  }

  @Synchronized
  override fun disableEqualizer() {
    disableRetained = true
    retainedProfile = null
    activeProfileState.value = null
    enabledState.value = false
    for (processor in equalizers) {
      runCatching { processor.disable() }.onFailure { Timber.e(it, "Failed to disable EQ") }
    }
  }

  @Synchronized
  override fun setStereoWidth(width: Float) {
    val bounded = width.coerceIn(1f, 2f)
    widthState.value = bounded
    for (processor in wideners) {
      processor.width = bounded
    }
  }

  @Synchronized
  override fun setAutoMixDuck(duckFactor: Float) {
    val bounded = duckFactor.coerceIn(0f, 1f)
    for (processor in ducks) {
      processor.setMix(bounded)
    }
  }

  override fun isInitialized(): Boolean = equalizers.isNotEmpty()

  override fun release() {
    equalizers.clear()
    ducks.clear()
    wideners.clear()
  }
}
