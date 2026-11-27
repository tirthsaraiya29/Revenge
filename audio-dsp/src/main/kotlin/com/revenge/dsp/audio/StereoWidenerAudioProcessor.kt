package com.revenge.dsp.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Mid/side stereo widener. Widening is applied jointly to both channels so the stereo image is
 * preserved even when the result is scaled to stay inside the PCM16 range.
 */
@UnstableApi
class StereoWidenerAudioProcessor : AudioProcessor {

  @Volatile
  var width: Float = DEFAULT_WIDTH
    set(value) {
      field = value.coerceIn(MIN_WIDTH, MAX_WIDTH)
    }

  @Volatile private var configuredRate: Int = 0

  @Volatile private var configuredChannels: Int = 0

  private var produced: ByteBuffer = blankBuffer()
  private var ended: Boolean = false

  override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount > 2) {
      throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
    }
    configuredRate = inputAudioFormat.sampleRate
    configuredChannels = inputAudioFormat.channelCount
    return inputAudioFormat
  }

  override fun isActive(): Boolean = configuredRate > 0

  override fun queueInput(inputBuffer: ByteBuffer) {
    val available = inputBuffer.remaining()
    if (available == 0) return

    val destination = ByteBuffer.allocateDirect(available).order(ByteOrder.nativeOrder())
    val source = inputBuffer.duplicate().order(inputBuffer.order())

    if (configuredRate <= 0 || configuredChannels != 2) {
      val untouched = ByteArray(available)
      source.get(untouched)
      destination.put(untouched)
    } else {
      val factor = width.toDouble()
      while (source.remaining() >= 2 * SAMPLE_BYTES) {
        val left = source.getShort().toDouble()
        val right = source.getShort().toDouble()
        val mid = (left + right) * 0.5
        val side = (left - right) * 0.5 * factor
        var outLeft = mid + side
        var outRight = mid - side
        val peak = max(abs(outLeft), abs(outRight))
        if (peak > PCM_MAX) {
          val scale = PCM_MAX / peak
          outLeft *= scale
          outRight *= scale
        }
        destination.putShort(quantise(outLeft))
        destination.putShort(quantise(outRight))
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
  }

  override fun reset() {
    produced = blankBuffer()
    ended = false
    configuredRate = 0
    configuredChannels = 0
  }

  private fun quantise(value: Double): Short {
    val bounded = value.coerceIn(PCM_MIN, PCM_MAX)
    return bounded.toInt().toShort()
  }

  private fun blankBuffer(): ByteBuffer =
    ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

  private companion object {
    const val SAMPLE_BYTES = 2
    const val PCM_MIN = -32768.0
    const val PCM_MAX = 32767.0
    const val DEFAULT_WIDTH = 1f
    const val MIN_WIDTH = 1f
    const val MAX_WIDTH = 2f
  }
}
