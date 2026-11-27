@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.revenge.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@UnstableApi
class SilenceDetectorAudioProcessor(
  private val minSilenceDurationUs: Long = 2_000_000L,
  private val silenceThreshold: Int = 256,
  private val onLongSilence: () -> Unit,
) : AudioProcessor {

  @Volatile var instantModeEnabled: Boolean = false

  @Volatile private var silentFrames: Long = 0L

  @Volatile private var currentlySilent: Boolean = false

  @Volatile private var silenceReported: Boolean = false

  private var sampleRate: Int = 0
  private var channelCount: Int = 0

  private var pendingOutput: ByteBuffer? = null
  private var endOfStream: Boolean = false

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
    pendingOutput = null
    val byteCount = inputBuffer.remaining()
    if (byteCount == 0) {
      pendingOutput = AudioProcessor.EMPTY_BUFFER
      return
    }

    val start = inputBuffer.position()
    val copy = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder())
    // duplicate() resets byte order to BIG_ENDIAN; samples must be read in the caller's order.
    val view = inputBuffer.duplicate().order(inputBuffer.order())
    copy.put(view)
    copy.flip()
    inputBuffer.position(inputBuffer.limit())
    pendingOutput = copy

    if (instantModeEnabled && sampleRate > 0 && channelCount > 0) {
      scan(view, start, byteCount)
    } else {
      resetSilenceState()
    }
  }

  override fun queueEndOfStream() {
    endOfStream = true
  }

  override fun getOutput(): ByteBuffer {
    val output = pendingOutput ?: AudioProcessor.EMPTY_BUFFER
    pendingOutput = null
    return output
  }

  override fun isEnded(): Boolean = endOfStream && pendingOutput == null

  override fun flush() {
    pendingOutput = null
    endOfStream = false
    resetSilenceState()
  }

  override fun reset() {
    flush()
    sampleRate = 0
    channelCount = 0
  }

  fun resetTracking() {
    resetSilenceState()
  }

  fun isCurrentlySilent(): Boolean = currentlySilent

  private fun scan(buffer: ByteBuffer, start: Int, byteCount: Int) {
    val frameSize = channelCount * 2
    val frames = byteCount / frameSize
    for (frame in 0 until frames) {
      val base = start + frame * frameSize
      var peak = 0
      for (channel in 0 until channelCount) {
        val sample = buffer.getShort(base + channel * 2).toInt()
        val magnitude = abs(sample)
        if (magnitude > peak) peak = magnitude
      }
      processFrame(peak)
    }
  }

  private fun processFrame(peak: Int) {
    if (peak < silenceThreshold) {
      silentFrames++
      val durationUs = silentFrames * 1_000_000L / sampleRate
      if (durationUs >= minSilenceDurationUs) {
        currentlySilent = true
        if (!silenceReported) {
          silenceReported = true
          onLongSilence()
        }
      }
    } else {
      resetSilenceState()
    }
  }

  private fun resetSilenceState() {
    silentFrames = 0L
    currentlySilent = false
    silenceReported = false
  }
}
