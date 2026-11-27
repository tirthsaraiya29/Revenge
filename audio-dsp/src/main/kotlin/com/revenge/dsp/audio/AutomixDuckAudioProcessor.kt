package com.revenge.dsp.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.revenge.dsp.core.BiquadFilter
import com.revenge.dsp.core.models.FilterType
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Low-shelf bass duck for AutoMix blends. Only the dry/wet blend moves while audio flows; the shelf
 * coefficients are fixed at configure time.
 */
@UnstableApi
class AutomixDuckAudioProcessor(
  private val shelfFrequencyHz: Double = 150.0,
  private val maxDuckDb: Double = -10.0,
) : AudioProcessor {

  private var monoFilter: BiquadFilter? = null
  private var leftFilter: BiquadFilter? = null
  private var rightFilter: BiquadFilter? = null

  @Volatile private var configuredRate: Int = 0

  @Volatile private var configuredChannels: Int = 0

  @Volatile private var targetMix: Float = 0f

  private var appliedMix: Double = 0.0
  private var smoothingStep: Double = 0.0

  private var produced: ByteBuffer = blankBuffer()
  private var ended: Boolean = false

  fun setMix(fraction: Float) {
    targetMix = fraction.coerceIn(0f, 1f)
  }

  fun resetGain() {
    targetMix = 0f
  }

  override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount > 2) {
      throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
    }
    configuredRate = inputAudioFormat.sampleRate
    configuredChannels = inputAudioFormat.channelCount
    val rate = inputAudioFormat.sampleRate
    monoFilter = BiquadFilter(rate, shelfFrequencyHz, maxDuckDb, DEFAULT_Q, FilterType.LSC)
    leftFilter = BiquadFilter(rate, shelfFrequencyHz, maxDuckDb, DEFAULT_Q, FilterType.LSC)
    rightFilter = BiquadFilter(rate, shelfFrequencyHz, maxDuckDb, DEFAULT_Q, FilterType.LSC)
    appliedMix = 0.0
    smoothingStep =
      if (rate > 0) {
        (1000.0 / (SMOOTHING_MS * rate)).coerceIn(0.0, 1.0)
      } else {
        0.0
      }
    return inputAudioFormat
  }

  override fun isActive(): Boolean = configuredRate > 0

  override fun queueInput(inputBuffer: ByteBuffer) {
    val available = inputBuffer.remaining()
    if (available == 0) return

    val rate = configuredRate
    val channels = configuredChannels
    val mono = monoFilter
    val left = leftFilter
    val right = rightFilter
    val destination = ByteBuffer.allocateDirect(available).order(ByteOrder.nativeOrder())
    val source = inputBuffer.duplicate().order(inputBuffer.order())

    if (rate <= 0 || mono == null || left == null || right == null || channels <= 0) {
      val untouched = ByteArray(available)
      source.get(untouched)
      destination.put(untouched)
    } else if (channels == 1) {
      while (source.remaining() >= SAMPLE_BYTES) {
        val dry = source.getShort() / PCM_SCALE
        val wet = mono.processSample(dry)
        advanceMix()
        destination.putShort(toPcm16(dry + (wet - dry) * appliedMix))
      }
    } else {
      while (source.remaining() >= 2 * SAMPLE_BYTES) {
        val dryLeft = source.getShort() / PCM_SCALE
        val dryRight = source.getShort() / PCM_SCALE
        val wetLeft = left.processSample(dryLeft)
        val wetRight = right.processSample(dryRight)
        advanceMix()
        destination.putShort(toPcm16(dryLeft + (wetLeft - dryLeft) * appliedMix))
        destination.putShort(toPcm16(dryRight + (wetRight - dryRight) * appliedMix))
      }
    }

    inputBuffer.position(inputBuffer.limit())
    destination.flip()
    produced = destination
  }

  override fun getOutput(): ByteBuffer {
    val delivered = produced
    produced = blankBuffer()
    return delivered
  }

  override fun isEnded(): Boolean = ended && !produced.hasRemaining()

  override fun queueEndOfStream() {
    ended = true
  }

  override fun flush() {
    produced = blankBuffer()
    ended = false
    monoFilter?.reset()
    leftFilter?.reset()
    rightFilter?.reset()
  }

  override fun reset() {
    produced = blankBuffer()
    ended = false
    configuredRate = 0
    configuredChannels = 0
    monoFilter = null
    leftFilter = null
    rightFilter = null
    targetMix = 0f
    appliedMix = 0.0
    smoothingStep = 0.0
  }

  private fun advanceMix() {
    appliedMix += (targetMix - appliedMix) * smoothingStep
  }

  private fun toPcm16(normalised: Double): Short {
    val bounded = (normalised * PCM_SCALE).coerceIn(PCM_MIN, PCM_MAX)
    return bounded.toInt().toShort()
  }

  private fun blankBuffer(): ByteBuffer =
    ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

  private companion object {
    const val SAMPLE_BYTES = 2
    const val PCM_SCALE = 32768.0
    const val PCM_MIN = -32768.0
    const val PCM_MAX = 32767.0
    const val DEFAULT_Q = 1.41
    const val SMOOTHING_MS = 15.0
  }
}
