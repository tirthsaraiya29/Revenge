package com.revenge.dsp.controller

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
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
@OptIn(UnstableApi::class)
class DspControllerImpl @Inject constructor() : DspController {

  private val eqProcessors = CopyOnWriteArrayList<CustomEqualizerAudioProcessor>()
  private val duckProcessors = CopyOnWriteArrayList<AutomixDuckAudioProcessor>()
  private val widenerProcessors = CopyOnWriteArrayList<StereoWidenerAudioProcessor>()

  private val _activeProfile = MutableStateFlow<SavedEQProfile?>(null)
  override val activeProfile: StateFlow<SavedEQProfile?> = _activeProfile.asStateFlow()

  private val _isEnabled = MutableStateFlow(false)
  override val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

  private val _stereoWidth = MutableStateFlow(1f)
  override val stereoWidth: StateFlow<Float> = _stereoWidth.asStateFlow()

  /** Latest profile so processors registered later can be brought up to date immediately. */
  @Volatile private var pendingProfile: SavedEQProfile? = null
  @Volatile private var pendingDisable = false

  override fun getAudioProcessors(): List<AudioProcessor> {
    val processors = mutableListOf<AudioProcessor>()
    eqProcessors.firstOrNull()?.let(processors::add)
    duckProcessors.firstOrNull()?.let(processors::add)
    widenerProcessors.firstOrNull()?.let(processors::add)
    return processors
  }

  override fun createProcessors(): PlayerDspProcessors {
    val equalizer = CustomEqualizerAudioProcessor()
    val duck = AutomixDuckAudioProcessor()
    val widener = StereoWidenerAudioProcessor()

    addAudioProcessor(equalizer)
    addDuckProcessor(duck)
    addStereoWidener(widener)

    return PlayerDspProcessors(
      equalizer = equalizer,
      duckProcessor = duck,
      stereoWidener = widener,
    )
  }

  override fun releaseProcessors(processors: PlayerDspProcessors) {
    removeAudioProcessor(processors.equalizer)
    removeDuckProcessor(processors.duckProcessor)
    removeStereoWidener(processors.stereoWidener)
  }

  override fun addAudioProcessor(processor: CustomEqualizerAudioProcessor) {
    eqProcessors.add(processor)
    when {
      pendingDisable -> processor.disable()
      else -> pendingProfile?.let { processor.applyProfile(it.parametricEQ) }
    }
    Timber.tag(TAG).d("EQ processor registered (${eqProcessors.size} total)")
  }

  override fun removeAudioProcessor(processor: CustomEqualizerAudioProcessor) {
    eqProcessors.remove(processor)
  }

  override fun addStereoWidener(processor: StereoWidenerAudioProcessor) {
    processor.width = _stereoWidth.value
    widenerProcessors.add(processor)
  }

  override fun removeStereoWidener(processor: StereoWidenerAudioProcessor) {
    widenerProcessors.remove(processor)
  }

  override fun addDuckProcessor(processor: AutomixDuckAudioProcessor) {
    duckProcessors.add(processor)
  }

  override fun removeDuckProcessor(processor: AutomixDuckAudioProcessor) {
    duckProcessors.remove(processor)
  }

  override fun applyProfile(profile: SavedEQProfile): Result<Unit> {
    pendingProfile = profile
    pendingDisable = false
    _activeProfile.value = profile
    _isEnabled.value = true

    if (eqProcessors.isEmpty()) {
      Timber.tag(TAG).w("No EQ processor yet; profile stored for later: ${profile.name}")
      return Result.success(Unit)
    }

    var failure: Exception? = null
    eqProcessors.forEach { processor ->
      try {
        processor.applyProfile(profile.parametricEQ)
      } catch (e: Exception) {
        if (failure == null) failure = e
        Timber.tag(TAG).e(e, "EQ processor rejected profile ${profile.id}")
      }
    }

    return failure?.let { Result.failure<Unit>(it) } ?: Result.success(Unit)
  }

  override fun disable() = disableEqualizer()

  override fun disableEqualizer() {
    pendingDisable = true
    pendingProfile = null
    _activeProfile.value = null
    _isEnabled.value = false

    eqProcessors.forEach { processor ->
      runCatching { processor.disable() }
        .onFailure { Timber.tag(TAG).e(it, "Failed to disable EQ processor") }
    }
  }

  override fun setStereoWidth(width: Float) {
    val clamped = width.coerceIn(1f, 2f)
    _stereoWidth.value = clamped
    widenerProcessors.forEach { it.width = clamped }
  }

  override fun setAutoMixDuck(duckFactor: Float) {
    val clamped = duckFactor.coerceIn(0f, 1f)
    duckProcessors.forEach { it.setMix(clamped) }
  }

  override fun isInitialized(): Boolean = eqProcessors.isNotEmpty()

  override fun release() {
    eqProcessors.clear()
    duckProcessors.clear()
    widenerProcessors.clear()
    Timber.tag(TAG).d("DSP processors released")
  }

  private companion object {
    const val TAG = "DspController"
  }
}
