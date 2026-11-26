package com.revenge.dsp.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Low-shelf ducking used by AutoMix DJ blends so the outgoing and incoming basslines do not pile
 * up during the overlap. This is separate from the user's parametric EQ chain.
 *
 * The shelf coefficients are designed once in [configure] and never changed: retuning an IIR that
 * still carries delay-line state from the previous coefficients produces zipper noise. Instead the
 * shelf runs continuously and [setMix] only moves a dry/wet blend, which is eased per sample so
 * the 15 ms-ish crossfade updates never step audibly.
 */
@UnstableApi
class AutomixDuckAudioProcessor(
  private val shelfFrequencyHz: Double = 150.0,
  private val maxDuckDb: Double = -10.0,
) : AudioProcessor {

  private var sampleRate = 0
  private var channelCount = 0
  private var active = false

  private var outputBuffer: ByteBuffer = EMPTY_BUFFER
  private var streamEnded = false

  /** 0 = dry, 1 = fully ducked by [maxDuckDb]. Written by the crossfade loop. */
  @Volatile private var targetMix = 0f
  private var appliedMix = 0f

  private var b0 = 1.0
  private var b1 = 0.0
  private var b2 = 0.0
  private var a1 = 0.0
  private var a2 = 0.0

  private val left = ChannelState()
  private val right = ChannelState()

  /** Blend toward [fraction] of the maximum duck, clamped to 0..1. */
  fun setMix(fraction: Float) {
    targetMix = fraction.coerceIn(0f, 1f)
  }

  /** Release the duck (blend back to dry). */
  fun resetGain() {
    targetMix = 0f
  }

  private fun designShelf() {
    // RBJ low shelf with slope S = 1. aVal is the shelf's amplitude (not power) at DC.
    val aVal = sqrt(10.0.pow(maxDuckDb / 20.0))
    val omega = 2.0 * PI * shelfFrequencyHz / sampleRate
    val sinOmega = sin(omega)
    val cosOmega = cos(omega)
    val alpha = sinOmega / 2.0 * sqrt(2.0)

    val rootA = sqrt(aVal)
    val sum = aVal + 1.0
    val diff = aVal - 1.0
    val alphaTerm = 2.0 * rootA * alpha

    val denom0 = sum + diff * cosOmega + alphaTerm
    val denom1 = -2.0 * (diff + sum * cosOmega)
    val denom2 = sum + diff * cosOmega - alphaTerm

    b0 = aVal * (sum - diff * cosOmega + alphaTerm) / denom0
    b1 = 2.0 * aVal * (diff - sum * cosOmega) / denom0
    b2 = aVal * (sum - diff * cosOmega - alphaTerm) / denom0
    a1 = denom1 / denom0
    a2 = denom2 / denom0
  }

  override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount > 2) {
      throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
    }
    sampleRate = inputAudioFormat.sampleRate
    channelCount = inputAudioFormat.channelCount
    designShelf()
    active = true
    return inputAudioFormat
  }

  override fun isActive(): Boolean = active

  override fun queueInput(inputBuffer: ByteBuffer) {
    val size = inputBuffer.remaining()
    if (size == 0) return

    if (outputBuffer.capacity() < size) {
      outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
    } else {
      outputBuffer.clear()
    }

    if (sampleRate <= 0 || (channelCount != 1 && channelCount != 2)) {
      outputBuffer.put(inputBuffer)
    } else {
      processPcm16(inputBuffer, outputBuffer)
    }
    outputBuffer.flip()
  }

  private fun processPcm16(input: ByteBuffer, output: ByteBuffer) {
    val sampleCount = input.remaining() / 2
    val ease = (1000.0 / (MIX_EASE_MS * sampleRate)).toFloat().coerceIn(0f, 1f)

    if (channelCount == 1) {
      repeat(sampleCount) {
        appliedMix += (targetMix - appliedMix) * ease
        val dry = input.getShort().toDouble() / PCM_SCALE
        val wet = left.process(dry)
        output.putShort(toPcm16(dry + (wet - dry) * appliedMix))
      }
    } else {
      repeat(sampleCount / 2) {
        appliedMix += (targetMix - appliedMix) * ease
        val dryLeft = input.getShort().toDouble() / PCM_SCALE
        val dryRight = input.getShort().toDouble() / PCM_SCALE
        val wetLeft = left.process(dryLeft)
        val wetRight = right.process(dryRight)
        output.putShort(toPcm16(dryLeft + (wetLeft - dryLeft) * appliedMix))
        output.putShort(toPcm16(dryRight + (wetRight - dryRight) * appliedMix))
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
    left.reset()
    right.reset()
  }

  override fun reset() {
    @Suppress("DEPRECATION") flush()
    sampleRate = 0
    channelCount = 0
    active = false
    appliedMix = 0f
    targetMix = 0f
  }

  override fun queueEndOfStream() {
    streamEnded = true
  }

  private inner class ChannelState {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(input: Double): Double {
      val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
      x2 = x1
      x1 = input
      y2 = y1
      y1 = output
      return output
    }

    fun reset() {
      x1 = 0.0
      x2 = 0.0
      y1 = 0.0
      y2 = 0.0
    }
  }

  private companion object {
    const val MIX_EASE_MS = 15.0
    const val PCM_SCALE = 32768.0
    val EMPTY_BUFFER: ByteBuffer =
      ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

    fun toPcm16(value: Double): Short =
      (value * PCM_SCALE).coerceIn(-32768.0, 32767.0).toInt().toShort()
  }
}
