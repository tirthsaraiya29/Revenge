package com.revenge.usbaudio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.revenge.usbaudio.model.DacCapabilities

/**
 * PCM transport for the USB audio path.
 *
 * Android exposes USB host devices but not isochronous URBs from the Java API, so this build
 * renders the stream through an [AudioTrack] owned by the process. Android routes media output to
 * a connected USB audio class device automatically, which makes the option audible; it does not
 * bypass the platform mixer. A native isochronous backend can replace the sink internals later
 * without changing this API.
 *
 * When an [AudioTrack] cannot be created (unit tests, unsupported PCM layout) the driver falls back
 * to a bounded ring buffer so callers and tests keep working.
 */
class UsbAudioDriver {

  companion object {
    private const val DRIVER_VERSION = "1.0.0-usbaudio"
    private const val RING_CAPACITY_BYTES = 128 * 1024
    private const val MICROFRAMES_PER_SECOND = 8000
    private const val BUFFER_DURATION_MS = 100

    /** True only when a native isochronous transport is bundled. This build has none. */
    val nativeLoaded: Boolean = false
  }

  private val ring = ByteRingBuffer(RING_CAPACITY_BYTES)
  private val lock = Any()

  @Volatile private var sampleBytes = 0
  @Volatile private var channels = 0
  @Volatile private var sampleRate = 0
  @Volatile private var softwareMultiplier = 1.0
  @Volatile private var framesPlayed = 0L
  @Volatile private var queuedFrames = 0L

  @Volatile private var track: AudioTrack? = null
  @Volatile private var trackBufferBytes = 0
  @Volatile private var trackFrameBytes = 0

  /** The software transport is always available. */
  fun isLoaded(): Boolean = true

  /** Version string reported by the driver. */
  fun getNativeDriverVersion(): String = DRIVER_VERSION

  // Test hooks keep the ring observable without exposing it in the public API.

  fun testRingBufferWrite(data: ByteArray): Int = ring.write(data, 0, data.size)

  fun testRingBufferRead(size: Int): ByteArray = ring.read(size)

  fun testRingBufferFlush() {
    ring.flush()
  }

  /** Parses raw USB configuration descriptors exposed by a USB device connection. */
  fun parseDescriptors(rawDescriptors: ByteArray): DacCapabilities =
    UsbDescriptorParser.parse(rawDescriptors)

  /** Nominal samples carried per 125 us microframe at [sampleRate]. */
  fun calculateNominalPacketSamples(sampleRate: Int): Int =
    if (sampleRate <= 0) 0 else sampleRate / MICROFRAMES_PER_SECOND

  /**
   * Starts a stream. Returns 0 on success.
   *
   * Only 16-bit mono/stereo is handed to [AudioTrack]; other layouts keep using the ring so the
   * caller never receives silently corrupted audio.
   */
  fun startStream(
    fd: Int,
    interfaceNumber: Int = 0,
    altSetting: Int = 1,
    dataEp: Int,
    syncEp: Int = -1,
    sampleRate: Int,
    bitDepth: Int = 16,
    channels: Int = 2,
  ): Int =
    synchronized(lock) {
      releaseTrack()
      ring.flush()
      sampleBytes = (bitDepth + 7) / 8
      this.channels = if (channels > 0) channels else 1
      this.sampleRate = sampleRate
      framesPlayed = 0L
      queuedFrames = 0L
      trackFrameBytes = this.channels * sampleBytes

      if (bitDepth == 16 && this.channels in 1..2 && sampleRate in 8_000..192_000) {
        track = createTrack(sampleRate, this.channels, trackFrameBytes)
        track?.let {
          trackBufferBytes = it.bufferSizeInFrames * trackFrameBytes
          try {
            it.play()
          } catch (_: IllegalStateException) {
            releaseTrack()
          }
        }
      }
      0
    }

  /** Stops the stream and discards queued audio. */
  fun stopStream(): Int =
    synchronized(lock) {
      releaseTrack()
      ring.flush()
      framesPlayed = 0L
      queuedFrames = 0L
      0
    }

  /**
   * Queues [size] bytes of interleaved PCM, applying the current software attenuation.
   * Returns the number of bytes accepted.
   */
  fun writeAudio(buffer: ByteArray, size: Int = buffer.size): Int {
    val toQueue = minOf(size, buffer.size)
    if (toQueue <= 0) return 0
    val payload = if (softwareMultiplier == 1.0) buffer else applyAttenuation(buffer, toQueue)
    val activeTrack = track
    if (activeTrack != null) {
      val written =
        try {
          activeTrack.write(payload, 0, toQueue, AudioTrack.WRITE_NON_BLOCKING)
        } catch (_: IllegalStateException) {
          -1
        }
      if (written > 0) {
        synchronized(lock) {
          if (trackFrameBytes > 0) queuedFrames += written / trackFrameBytes
        }
      }
      return written.coerceAtLeast(0)
    }
    val written = ring.write(payload, 0, toQueue)
    val frameBytes = sampleBytes * channels
    if (frameBytes > 0) framesPlayed += written / frameBytes
    return written
  }

  /** Bytes that can still be queued before backpressure. */
  fun getAvailableWrite(): Int {
    val activeTrack = track
    if (activeTrack == null) return ring.availableWrite()
    val played = playedFrames(activeTrack)
    val pending = (queuedFrames - played).coerceAtLeast(0L)
    val free = trackBufferBytes - pending * trackFrameBytes
    return free.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
  }

  /** Stores the linear software attenuation multiplier (0.0..1.0). */
  fun setSoftwareVolumeMultiplier(multiplier: Double) {
    softwareMultiplier = multiplier.coerceIn(0.0, 1.0)
    try {
      track?.setVolume(softwareMultiplier.toFloat())
    } catch (_: IllegalStateException) {
      // The track was released between the read and the call.
    }
  }

  /** Discards queued audio and restarts the frame clock. */
  fun flushStream() {
    synchronized(lock) {
      track?.let {
        try {
          it.pause()
          it.flush()
          queuedFrames = 0L
          it.play()
        } catch (_: IllegalStateException) {
          releaseTrack()
        }
      }
      ring.flush()
      framesPlayed = 0L
    }
  }

  /** Frames rendered since the last start or flush. */
  fun getFramesPlayed(): Long {
    val activeTrack = track ?: return framesPlayed
    return playedFrames(activeTrack)
  }

  /** Whether audio is still queued for the sink. */
  fun hasPendingData(): Boolean {
    val activeTrack = track
    if (activeTrack == null) return ring.size() > 0
    return queuedFrames > playedFrames(activeTrack)
  }

  private fun playedFrames(activeTrack: AudioTrack): Long =
    try {
      activeTrack.playbackHeadPosition.toLong() and 0xFFFFFFFFL
    } catch (_: IllegalStateException) {
      0L
    }

  private fun createTrack(rate: Int, channelCount: Int, frameBytes: Int): AudioTrack? =
    try {
      val channelMask =
        if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
      val minimum = AudioTrack.getMinBufferSize(rate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
      val requested = maxOf(minimum, rate / 1000 * BUFFER_DURATION_MS * frameBytes)
      AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(rate)
            .setChannelMask(channelMask)
            .build()
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(requested)
        .build()
    } catch (_: Throwable) {
      // No audio device on the JVM, or the platform rejected the format.
      null
    }

  private fun releaseTrack() {
    track?.let {
      try {
        it.pause()
        it.flush()
        it.stop()
      } catch (_: IllegalStateException) {
        // Already stopped.
      }
      it.release()
    }
    track = null
    trackBufferBytes = 0
  }

  private fun applyAttenuation(source: ByteArray, length: Int): ByteArray {
    val output = source.copyOf(length)
    val bytes = sampleBytes
    if (bytes !in 2..4) return output
    val multiplier = softwareMultiplier
    val usable = length - (length % bytes)
    var offset = 0
    while (offset < usable) {
      when (bytes) {
        2 -> {
          val sample =
            ((output[offset].toInt() and 0xFF) or (output[offset + 1].toInt() shl 8))
              .toShort()
              .toInt()
          val scaled =
            (sample * multiplier)
              .toInt()
              .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
          output[offset] = scaled.toByte()
          output[offset + 1] = (scaled shr 8).toByte()
        }
        3 -> {
          var sample =
            (output[offset].toInt() and 0xFF) or
              ((output[offset + 1].toInt() and 0xFF) shl 8) or
              ((output[offset + 2].toInt() and 0xFF) shl 16)
          if (sample and 0x00800000 != 0) sample = sample or 0xFF000000.toInt()
          val scaled = (sample * multiplier).toInt().coerceIn(-8_388_608, 8_388_607)
          output[offset] = scaled.toByte()
          output[offset + 1] = (scaled shr 8).toByte()
          output[offset + 2] = (scaled shr 16).toByte()
        }
        else -> {
          val sample =
            (output[offset].toInt() and 0xFF) or
              ((output[offset + 1].toInt() and 0xFF) shl 8) or
              ((output[offset + 2].toInt() and 0xFF) shl 16) or
              (output[offset + 3].toInt() shl 24)
          val scaled =
            (sample.toDouble() * multiplier)
              .coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble())
              .toInt()
          output[offset] = scaled.toByte()
          output[offset + 1] = (scaled shr 8).toByte()
          output[offset + 2] = (scaled shr 16).toByte()
          output[offset + 3] = (scaled shr 24).toByte()
        }
      }
      offset += bytes
    }
    return output
  }
}

/** Bounded single-producer/single-consumer byte buffer with wraparound. */
private class ByteRingBuffer(private val capacity: Int) {
  private val data = ByteArray(capacity)
  private var readIndex = 0
  private var writeIndex = 0
  private var count = 0

  @Synchronized
  fun size(): Int = count

  @Synchronized
  fun availableWrite(): Int = capacity - count

  @Synchronized
  fun write(source: ByteArray, offset: Int, length: Int): Int {
    if (length <= 0) return 0
    val toWrite = minOf(length, capacity - count)
    if (toWrite <= 0) return 0
    var written = 0
    while (written < toWrite) {
      val chunk = minOf(toWrite - written, capacity - writeIndex)
      System.arraycopy(source, offset + written, data, writeIndex, chunk)
      writeIndex = (writeIndex + chunk) % capacity
      written += chunk
    }
    count += written
    return written
  }

  @Synchronized
  fun read(length: Int): ByteArray {
    val toRead = minOf(length.coerceAtLeast(0), count)
    val result = ByteArray(toRead)
    var copied = 0
    while (copied < toRead) {
      val chunk = minOf(toRead - copied, capacity - readIndex)
      System.arraycopy(data, readIndex, result, copied, chunk)
      readIndex = (readIndex + chunk) % capacity
      copied += chunk
    }
    count -= toRead
    if (count == 0) {
      readIndex = 0
      writeIndex = 0
    }
    return result
  }

  @Synchronized
  fun flush() {
    readIndex = 0
    writeIndex = 0
    count = 0
  }
}
