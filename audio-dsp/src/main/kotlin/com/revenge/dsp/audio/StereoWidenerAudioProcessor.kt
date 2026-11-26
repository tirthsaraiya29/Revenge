package com.revenge.dsp.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Mid/side stereo widener behind the "spatial audio" switch.
 *
 * Boosting the side signal moves the L/R image outward while the mono centre stays put. Both
 * channels are scaled together when the recombined peak overflows, so the image the widening
 * created is preserved instead of independently clipping one side.
 */
@UnstableApi
class StereoWidenerAudioProcessor : AudioProcessor {

  /** 1 keeps the image unchanged; values up to 2 widen. */
  @Volatile
  var width: Float = 1f
    set(value) {
      field = value.coerceIn(MIN_WIDTH, MAX_WIDTH)
    }

  private var channelCount = 0
  private var active = false
  private var outputBuffer: ByteBuffer = EMPTY_BUFFER
  private var streamEnded = false

  override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount > 2) {
      throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
    }
    channelCount = inputAudioFormat.channelCount
    active = true
    return inputAudioFormat
  }

  // Stays active even at width 1: the mid/side sum is lossless there and keeping it in the chain
  // avoids a reconfiguration when the user toggles the feature.
  override fun isActive(): Boolean = active

  override fun queueInput(inputBuffer: ByteBuffer) {
    val size = inputBuffer.remaining()
    if (size == 0) return

    if (outputBuffer.capacity() < size) {
      outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
    } else {
      outputBuffer.clear()
    }

    if (channelCount == 2) {
      widen(inputBuffer, outputBuffer)
    } else {
      // Mono has no side component; unconfigured buffers are also passed through untouched.
      outputBuffer.put(inputBuffer)
    }
    outputBuffer.flip()
  }

  private fun widen(input: ByteBuffer, output: ByteBuffer) {
    val currentWidth = width
    repeat(input.remaining() / 4) {
      val left = input.getShort().toFloat()
      val right = input.getShort().toFloat()

      val mid = (left + right) * 0.5f
      val side = (left - right) * 0.5f * currentWidth

      var outLeft = mid + side
      var outRight = mid - side

      val peak = maxOf(abs(outLeft), abs(outRight))
      if (peak > PCM_MAX) {
        val scale = PCM_MAX / peak
        outLeft *= scale
        outRight *= scale
      }

      output.putShort(outLeft.toInt().toShort())
      output.putShort(outRight.toInt().toShort())
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
  }

  override fun reset() {
    @Suppress("DEPRECATION") flush()
    channelCount = 0
    active = false
  }

  override fun queueEndOfStream() {
    streamEnded = true
  }

  private companion object {
    const val MIN_WIDTH = 1f
    const val MAX_WIDTH = 2f
    const val PCM_MAX = 32767f
    val EMPTY_BUFFER: ByteBuffer =
      ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
  }
}
