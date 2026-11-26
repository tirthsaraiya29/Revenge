package com.revenge.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Pass-through [AudioProcessor] that watches PCM for long silent stretches. When enabled it counts
 * consecutive near-silent frames and fires [onLongSilence] once per silence, so the service can
 * skip ahead without polling the audio output.
 */
@UnstableApi
@Suppress("DEPRECATION")
class SilenceDetectorAudioProcessor(
  private val minSilenceDurationUs: Long = 2_000_000L,
  private val silenceThreshold: Int = 256,
  private val onLongSilence: () -> Unit,
) : AudioProcessor {

  private var sampleRate = 0
  private var channelCount = 0

  private var pendingOutput: ByteBuffer = EMPTY
  private var streamEnded = false

  @Volatile var instantModeEnabled: Boolean = false

  @Volatile private var silentFrames = 0L

  @Volatile private var silent = false

  private var silenceAlreadyReported = false

  override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
      throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
    }
    sampleRate = inputAudioFormat.sampleRate
    channelCount = inputAudioFormat.channelCount
    return inputAudioFormat
  }

  override fun isActive(): Boolean = true

  override fun queueInput(inputBuffer: ByteBuffer) {
    if (!inputBuffer.hasRemaining()) {
      pendingOutput = EMPTY
      return
    }

    if (instantModeEnabled && sampleRate > 0 && channelCount > 0) {
      scanForSilence(inputBuffer)
    } else {
      resetSilenceState()
    }

    val output = outputSizedFor(inputBuffer.remaining())
    output.put(inputBuffer)
    output.flip()
  }

  private fun scanForSilence(inputBuffer: ByteBuffer) {
    inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
    val base = inputBuffer.position()
    val frames = inputBuffer.remaining() / (BYTES_PER_SAMPLE * channelCount)
    val frameBytes = BYTES_PER_SAMPLE * channelCount

    repeat(frames) { frame ->
      var peak = 0
      for (channel in 0 until channelCount) {
        val offset = base + frame * frameBytes + channel * BYTES_PER_SAMPLE
        val amplitude = abs(inputBuffer.getShort(offset).toInt())
        if (amplitude > peak) peak = amplitude
      }

      if (peak < silenceThreshold) {
        silentFrames++
        if (silentFrames * 1_000_000L / sampleRate >= minSilenceDurationUs) {
          silent = true
          if (!silenceAlreadyReported) {
            silenceAlreadyReported = true
            onLongSilence()
          }
        }
      } else {
        resetSilenceState()
      }
    }
  }

  private fun resetSilenceState() {
    silentFrames = 0L
    silent = false
    silenceAlreadyReported = false
  }

  fun resetTracking() {
    resetSilenceState()
  }

  fun isCurrentlySilent(): Boolean = silent

  override fun queueEndOfStream() {
    streamEnded = true
  }

  override fun getOutput(): ByteBuffer {
    val output = pendingOutput
    pendingOutput = EMPTY
    return output
  }

  override fun isEnded(): Boolean = streamEnded && pendingOutput === EMPTY

  @Deprecated("Deprecated in AudioProcessor")
  override fun flush() {
    pendingOutput = EMPTY
    streamEnded = false
    resetSilenceState()
  }

  @Deprecated("Deprecated in AudioProcessor")
  override fun reset() {
    flush()
    sampleRate = 0
    channelCount = 0
  }

  private fun outputSizedFor(bytes: Int): ByteBuffer {
    if (pendingOutput.capacity() < bytes) {
      pendingOutput = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
    } else {
      pendingOutput.clear()
    }
    return pendingOutput
  }

  private companion object {
    const val BYTES_PER_SAMPLE = 2

    val EMPTY: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
  }
}
