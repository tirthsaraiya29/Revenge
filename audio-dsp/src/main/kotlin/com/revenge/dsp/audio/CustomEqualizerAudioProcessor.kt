package com.revenge.dsp.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.revenge.dsp.core.BiquadFilter
import com.revenge.dsp.core.models.ParametricEQ
import com.revenge.dsp.core.models.ParametricEQBand
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import timber.log.Timber

/**
 * Parametric equaliser stage for the Media3 audio chain.
 *
 * The profile can arrive before the pipeline is configured (the controller may apply it while no
 * player exists), so the last requested profile is kept and (re)installed whenever a format is
 * known. Only 16-bit PCM with one or two channels is handled; anything else is rejected at
 * configure time so Media3 can pick another sink.
 */
@UnstableApi
class CustomEqualizerAudioProcessor : AudioProcessor {

  private var sampleRate = 0
  private var channelCount = 0
  private var active = false
  private var enabled = false

  private var outputBuffer: ByteBuffer = EMPTY_BUFFER
  private var streamEnded = false

  private var filters: List<BiquadFilter> = emptyList()
  private var preampLinear = 1.0

  /** Last profile requested through [applyProfile]; null after [disable]. */
  private var requestedProfile: ParametricEQ? = null

  @Synchronized
  fun applyProfile(parametricEQ: ParametricEQ) {
    requestedProfile = parametricEQ
    if (sampleRate == 0) {
      Timber.tag(TAG)
        .d("Holding EQ profile until configure: ${parametricEQ.bands.size} bands")
      return
    }
    install(parametricEQ)
  }

  @Synchronized
  fun disable() {
    requestedProfile = null
    enabled = false
    filters = emptyList()
    preampLinear = 1.0
    Timber.tag(TAG).d("Equalizer disabled")
  }

  fun isEnabled(): Boolean = enabled

  private fun install(profile: ParametricEQ) {
    preampLinear = 10.0.pow(profile.preamp / 20.0)
    filters = buildFilters(profile.bands)
    enabled = true
    Timber.tag(TAG)
      .d("Installed EQ: ${filters.size}/${profile.bands.size} bands, preamp ${profile.preamp} dB")
  }

  private fun buildFilters(bands: List<ParametricEQBand>): List<BiquadFilter> {
    val nyquist = sampleRate / 2.0
    return bands
      .filter { it.enabled && it.frequency > 0.0 && it.frequency < nyquist }
      .map { band ->
        BiquadFilter(
          sampleRate = sampleRate,
          frequency = band.frequency,
          gain = band.gain,
          q = band.q,
          filterType = band.filterType,
        )
      }
  }

  override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount > 2) {
      throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
    }

    sampleRate = inputAudioFormat.sampleRate
    channelCount = inputAudioFormat.channelCount
    active = true

    requestedProfile?.let { install(it) }
    return inputAudioFormat
  }

  override fun isActive(): Boolean = active

  override fun queueInput(inputBuffer: ByteBuffer) {
    val size = inputBuffer.remaining()
    if (size == 0) return

    prepareOutput(size)
    val bypass = !enabled || (filters.isEmpty() && preampLinear == 1.0) || channelCount == 0
    if (bypass) {
      outputBuffer.put(inputBuffer)
    } else {
      processPcm16(inputBuffer, outputBuffer)
    }
    outputBuffer.flip()
  }

  private fun prepareOutput(size: Int) {
    if (outputBuffer.capacity() < size) {
      outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
    } else {
      outputBuffer.clear()
    }
  }

  private fun processPcm16(input: ByteBuffer, output: ByteBuffer) {
    val frameBytes = 2 * channelCount
    repeat(input.remaining() / frameBytes) {
      if (channelCount == 1) {
        var sample = input.getShort().toDouble() / PCM_SCALE
        for (filter in filters) sample = filter.processSample(sample)
        output.putShort(toPcm16(sample * preampLinear))
      } else {
        var left = input.getShort().toDouble() / PCM_SCALE
        var right = input.getShort().toDouble() / PCM_SCALE
        for (filter in filters) {
          val processed = filter.processStereo(left, right)
          left = processed.first
          right = processed.second
        }
        output.putShort(toPcm16(left * preampLinear))
        output.putShort(toPcm16(right * preampLinear))
      }
    }
  }

  override fun getOutput(): ByteBuffer {
    val buffer = outputBuffer
    outputBuffer = EMPTY_BUFFER
    return buffer
  }

  override fun isEnded(): Boolean = streamEnded && outputBuffer.remaining() == 0

  @Deprecated("Deprecated in Java")
  override fun flush() {
    outputBuffer = EMPTY_BUFFER
    streamEnded = false
    filters.forEach { it.reset() }
  }

  override fun reset() {
    @Suppress("DEPRECATION") flush()
    sampleRate = 0
    channelCount = 0
    active = false
    enabled = false
    filters = emptyList()
    preampLinear = 1.0
    // requestedProfile intentionally survives so a later configure restores the EQ.
  }

  override fun queueEndOfStream() {
    streamEnded = true
  }

  private companion object {
    const val TAG = "CustomEQProcessor"
    const val PCM_SCALE = 32768.0
    val EMPTY_BUFFER: ByteBuffer =
      ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

    fun toPcm16(value: Double): Short =
      (value * PCM_SCALE).coerceIn(-32768.0, 32767.0).toInt().toShort()
  }
}
