package com.revenge.usbaudio

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import com.revenge.usbaudio.model.DacCapabilities
import com.revenge.usbaudio.model.DacFormat
import java.nio.ByteBuffer

/**
 * Media3 [AudioSink] that hands decoded PCM to [UsbAudioDriver]. The driver negotiates the stream
 * from the DAC's advertised formats, keeps the sample clock in sync with its frame counter, and
 * applies backpressure when the transport is full. In this build the driver renders through the
 * platform AudioTrack routed to the connected USB device; a native isochronous backend can replace
 * it without changing this sink.
 */
@OptIn(UnstableApi::class)
class UsbDacAudioSink(
  private val driver: UsbAudioDriver,
  var capabilities: DacCapabilities = DacCapabilities(uacVersion = 2),
  private val dacConnectionProvider: (() -> DacDeviceState.Connected?)? = null,
) : AudioSink {

  private var listener: AudioSink.Listener? = null

  private var playing = false
  private var streamEnded = false
  private var streaming = false

  private var activeConnection: DacDeviceState.Connected? = null
  private var streamFd = -1
  private var streamSampleRate = 0
  private var streamChannels = 0
  private var streamContainerBits = 16

  private var startMediaTimeUs = AudioSink.CURRENT_POSITION_NOT_SET
  private var inputFormat: Format? = null
  private var volume = 1.0f
  private var framesWritten = 0L

  private var inputScratch = ByteArray(0)
  private var conversionScratch = ByteArray(0)

  override fun setListener(listener: AudioSink.Listener) {
    this.listener = listener
  }

  override fun supportsFormat(format: Format): Boolean {
    if (format.sampleMimeType != MimeTypes.AUDIO_RAW) return false
    when (format.pcmEncoding) {
      Format.NO_VALUE,
      C.ENCODING_PCM_16BIT,
      C.ENCODING_PCM_24BIT,
      C.ENCODING_PCM_32BIT -> Unit
      else -> return false
    }
    val sampleRate = format.sampleRate
    if (sampleRate == Format.NO_VALUE) return true
    return if (capabilities.supportedSampleRates.isNotEmpty()) {
      capabilities.supportedSampleRates.contains(sampleRate)
    } else {
      sampleRate <= MAX_UNALISTED_RATE
    }
  }

  override fun getFormatSupport(format: Format): Int =
    if (supportsFormat(format)) {
      AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
    } else {
      AudioSink.SINK_FORMAT_UNSUPPORTED
    }

  override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
    if (startMediaTimeUs == AudioSink.CURRENT_POSITION_NOT_SET) {
      return AudioSink.CURRENT_POSITION_NOT_SET
    }
    val sampleRate = inputFormat?.sampleRate?.takeIf { it > 0 } ?: 44_100
    val frames = if (driver.isLoaded() && streaming) driver.getFramesPlayed() else framesWritten
    return startMediaTimeUs + frames * 1_000_000L / sampleRate
  }

  override fun configure(
    inputFormat: Format,
    specifiedBufferSize: Int,
    outputChannels: IntArray?,
  ) {
    this.inputFormat = inputFormat
    framesWritten = 0L
    streamEnded = false
    startMediaTimeUs = AudioSink.CURRENT_POSITION_NOT_SET
  }

  override fun play() {
    playing = true
    ensureStream()
  }

  override fun handleDiscontinuity() {}

  override fun handleBuffer(
    buffer: ByteBuffer,
    presentationTimeUs: Long,
    encodedAccessUnitCount: Int,
  ): Boolean {
    if (!buffer.hasRemaining()) return true
    if (!playing) return false

    ensureStream()
    if (dacConnectionProvider != null && !streaming) {
      // The DAC is not ready yet; ask the player to retry this buffer later.
      return false
    }

    if (
      startMediaTimeUs == AudioSink.CURRENT_POSITION_NOT_SET &&
        presentationTimeUs != C.TIME_UNSET
    ) {
      startMediaTimeUs = presentationTimeUs
    }

    val remaining = buffer.remaining()
    val startPosition = buffer.position()
    if (inputScratch.size < remaining) inputScratch = ByteArray(remaining)
    buffer.get(inputScratch, 0, remaining)

    val channels = configuredChannels()
    val inputBits = inputBitDepth()
    val targetBits = streamContainerBits

    val payload: ByteArray
    val payloadBytes: Int
    if (inputBits == targetBits) {
      payload = inputScratch
      payloadBytes = remaining
    } else {
      payload = convertPcm(inputScratch, 0, remaining, inputBits, targetBits)
      val inputFrameBytes = maxOf(1, channels * (inputBits / 8))
      val frames = remaining / inputFrameBytes
      payloadBytes = frames * channels * (targetBits / 8)
    }

    val targetFrameBytes = maxOf(1, channels * (targetBits / 8))
    val writable = (driver.getAvailableWrite() / targetFrameBytes) * targetFrameBytes
    if (writable <= 0) {
      buffer.position(startPosition)
      return false
    }

    val toSend = minOf(payloadBytes, writable)
    val written = driver.writeAudio(payload, toSend)
    val frames = maxOf(0, written) / targetFrameBytes
    val inputFrameBytes = maxOf(1, channels * (inputBits / 8))
    val consumed = frames * inputFrameBytes

    buffer.position(startPosition + consumed)
    framesWritten += frames
    return consumed >= remaining
  }

  override fun playToEndOfStream() {
    streamEnded = true
  }

  override fun isEnded(): Boolean = streamEnded && !hasPendingData()

  override fun hasPendingData(): Boolean = driver.hasPendingData()

  override fun getAudioTrackBufferSizeUs(): Long = 0L

  override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {}

  override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT

  override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {}

  override fun getSkipSilenceEnabled(): Boolean = false

  override fun setAudioAttributes(audioAttributes: AudioAttributes) {}

  override fun getAudioAttributes(): AudioAttributes = AudioAttributes.DEFAULT

  override fun setAudioSessionId(audioSessionId: Int) {}

  override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {}

  override fun enableTunnelingV21() {}

  override fun disableTunneling() {}

  override fun setVolume(volume: Float) {
    val clamped = volume.coerceIn(0.0f, 1.0f)
    this.volume = clamped
    driver.setSoftwareVolumeMultiplier(clamped.toDouble())
  }

  override fun pause() {
    playing = false
  }

  override fun flush() {
    driver.flushStream()
    framesWritten = 0L
    streamEnded = false
    startMediaTimeUs = AudioSink.CURRENT_POSITION_NOT_SET
  }

  override fun reset() {
    if (streaming) {
      driver.stopStream()
      streaming = false
    }
    flush()
    playing = false
    streamEnded = false
    inputFormat = null
    activeConnection = null
    streamFd = -1
    streamSampleRate = 0
    streamChannels = 0
    streamContainerBits = 16
  }

  // ── Stream negotiation ───────────────────────────────────────────────────

  private fun ensureStream() {
    val provider = dacConnectionProvider ?: return
    val connected = provider()

    if (streaming && (connected == null || connected !== activeConnection)) {
      driver.stopStream()
      streaming = false
      activeConnection = null
      streamFd = -1
    }
    if (connected == null) return

    capabilities = connected.capabilities
    val sampleRate = inputFormat?.sampleRate?.takeIf { it > 0 } ?: 44_100
    val channels = configuredChannels()
    val inputBits = inputBitDepth()
    val chosen = chooseFormat(sampleRate, channels, inputBits)

    // The platform AudioTrack path accepts 16-bit PCM only; the native backend can do better.
    val streamBits =
      if (!UsbAudioDriver.nativeLoaded) {
        16
      } else {
        maxOf(
          ((chosen?.subslotBytes ?: ((chosen?.bitDepth ?: inputBits) + 7) / 8) * 8),
          ((inputBits + 7) / 8) * 8,
        )
      }
    val interfaceNumber = chosen?.interfaceNumber ?: 0
    val altSetting = chosen?.altSetting ?: 1
    val dataEndpoint = chosen?.endpointAddress ?: 0x01
    val syncEndpoint = chosen?.syncEndpointAddress ?: -1

    if (
      streaming &&
        (sampleRate != streamSampleRate ||
          channels != streamChannels ||
          streamBits != streamContainerBits)
    ) {
      driver.stopStream()
      streaming = false
      framesWritten = 0L
      startMediaTimeUs = AudioSink.CURRENT_POSITION_NOT_SET
    }
    if (streaming) return

    val result =
      driver.startStream(
        fd = connected.fileDescriptor,
        interfaceNumber = interfaceNumber,
        altSetting = altSetting,
        dataEp = dataEndpoint,
        syncEp = syncEndpoint,
        sampleRate = sampleRate,
        bitDepth = streamBits,
        channels = channels,
      )
    if (result == 0) {
      streaming = true
      activeConnection = connected
      streamFd = connected.fileDescriptor
      streamSampleRate = sampleRate
      streamChannels = channels
      streamContainerBits = streamBits
    } else {
      listener?.onAudioSinkError(IllegalStateException("USB audio stream start failed: $result"))
    }
  }

  private fun chooseFormat(rate: Int, channels: Int, inputBits: Int): DacFormat? {
    val formats = capabilities.supportedFormats
    if (formats.isEmpty()) return null

    fun rateMatches(format: DacFormat): Boolean =
      format.sampleRates.isEmpty() || format.sampleRates.contains(rate)

    fun channelsMatch(format: DacFormat): Boolean = format.channels == channels

    return formats.firstOrNull {
      rateMatches(it) && channelsMatch(it) && it.bitDepth == inputBits
    }
      ?: formats
        .filter { rateMatches(it) && channelsMatch(it) }
        .maxByOrNull { it.bitDepth }
      ?: formats.filter { rateMatches(it) }.maxByOrNull { it.bitDepth }
      ?: formats.firstOrNull { rateMatches(it) }
      ?: formats.first()
  }

  private fun configuredChannels(): Int =
    inputFormat?.channelCount?.takeIf { it != Format.NO_VALUE && it > 0 } ?: 2

  private fun inputBitDepth(): Int =
    when (inputFormat?.pcmEncoding) {
      C.ENCODING_PCM_24BIT -> 24
      C.ENCODING_PCM_32BIT -> 32
      else -> 16
    }

  // ── PCM conversion ───────────────────────────────────────────────────────

  /**
   * Re-containerises little-endian integer PCM. Raising the depth left-aligns samples (exact);
   * lowering it drops the least significant bytes. The result is valid until the next call.
   */
  private fun convertPcm(
    source: ByteArray,
    sourceOffset: Int,
    sourceLength: Int,
    sourceBits: Int,
    targetBits: Int,
  ): ByteArray {
    if (sourceBits == targetBits) {
      return if (sourceOffset == 0 && sourceLength == source.size) {
        source
      } else {
        source.copyOfRange(sourceOffset, sourceOffset + sourceLength)
      }
    }

    val sourceBytes = sourceBits / 8
    val targetBytes = targetBits / 8
    val sampleCount = sourceLength / sourceBytes
    val required = sampleCount * targetBytes
    if (conversionScratch.size < required) conversionScratch = ByteArray(required)

    var read = sourceOffset
    var write = 0
    repeat(sampleCount) {
      val sample = readSigned(source, read, sourceBytes)
      val converted =
        if (targetBits > sourceBits) {
          sample shl (targetBits - sourceBits)
        } else {
          sample shr (sourceBits - targetBits)
        }
      writeSigned(conversionScratch, write, converted, targetBytes)
      read += sourceBytes
      write += targetBytes
    }
    return conversionScratch
  }

  private fun readSigned(source: ByteArray, offset: Int, bytes: Int): Int =
    when (bytes) {
      2 ->
        ((source[offset].toInt() and 0xFF) or (source[offset + 1].toInt() shl 8))
          .toShort()
          .toInt()
      3 -> {
        var sample =
          (source[offset].toInt() and 0xFF) or
            ((source[offset + 1].toInt() and 0xFF) shl 8) or
            ((source[offset + 2].toInt() and 0xFF) shl 16)
        if (sample and 0x00800000 != 0) sample = sample or 0xFF000000.toInt()
        sample
      }
      else ->
        (source[offset].toInt() and 0xFF) or
          ((source[offset + 1].toInt() and 0xFF) shl 8) or
          ((source[offset + 2].toInt() and 0xFF) shl 16) or
          (source[offset + 3].toInt() shl 24)
    }

  private fun writeSigned(target: ByteArray, offset: Int, sample: Int, bytes: Int) {
    target[offset] = sample.toByte()
    if (bytes >= 2) target[offset + 1] = (sample shr 8).toByte()
    if (bytes >= 3) target[offset + 2] = (sample shr 16).toByte()
    if (bytes >= 4) target[offset + 3] = (sample shr 24).toByte()
  }

  private companion object {
    const val MAX_UNALISTED_RATE = 192_000
  }
}
