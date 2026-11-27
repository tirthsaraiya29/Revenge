package com.revenge.usbaudio

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
import java.nio.ByteOrder

/**
 * Media3 [AudioSink] that forwards decoded PCM to [UsbAudioDriver]. Output uses the platform mixer
 * route; the sink never bypasses it.
 */
@UnstableApi
class UsbDacAudioSink(
  private val driver: UsbAudioDriver,
  var capabilities: DacCapabilities = DacCapabilities(uacVersion = 2),
  private val dacConnectionProvider: (() -> DacDeviceState.Connected?)? = null,
) : AudioSink {

  private var listener: AudioSink.Listener? = null
  private var inputFormat: Format? = null

  private var playing = false
  private var streamingActive = false
  private var endOfStream = false

  private var startMediaTimeUs: Long = AudioSink.CURRENT_POSITION_NOT_SET
  private var framesWritten: Long = 0

  private var containerDepth: Int = 16
  private var activeRate: Int = 0
  private var activeChannels: Int = 0
  private var activeConnection: DacDeviceState.Connected? = null

  private var volume: Float = 1f
  private var audioAttributes: AudioAttributes = AudioAttributes.DEFAULT
  private var playbackParameters: PlaybackParameters = PlaybackParameters.DEFAULT
  private var skipSilenceEnabled: Boolean = false

  override fun setListener(listener: AudioSink.Listener) {
    this.listener = listener
  }

  override fun supportsFormat(format: Format): Boolean {
    if (format.sampleMimeType != MimeTypes.AUDIO_RAW) return false
    val encoding = format.pcmEncoding
    val encodingSupported =
      encoding == Format.NO_VALUE ||
        encoding == C.ENCODING_PCM_16BIT ||
        encoding == C.ENCODING_PCM_24BIT ||
        encoding == C.ENCODING_PCM_32BIT
    if (!encodingSupported) return false
    if (format.sampleRate == Format.NO_VALUE) return true
    val advertised = capabilities.supportedSampleRates
    return if (advertised.isNotEmpty()) {
      advertised.contains(format.sampleRate)
    } else {
      format.sampleRate <= MAX_FALLBACK_RATE
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
    val rate = inputFormat?.sampleRate?.takeIf { it != Format.NO_VALUE && it > 0 } ?: 44100
    val frames = if (streamingActive) driver.getFramesPlayed() else framesWritten
    return startMediaTimeUs + frames * 1_000_000L / rate
  }

  override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
    this.inputFormat = inputFormat
    framesWritten = 0
    endOfStream = false
    startMediaTimeUs = AudioSink.CURRENT_POSITION_NOT_SET
  }

  override fun play() {
    playing = true
    negotiateIfNeeded()
  }

  override fun handleDiscontinuity() {
    // No discontinuity specific state is tracked.
  }

  override fun handleBuffer(
    buffer: ByteBuffer,
    presentationTimeUs: Long,
    encodedAccessUnitCount: Int,
  ): Boolean {
    if (!buffer.hasRemaining()) return true
    if (!playing) return false

    negotiateIfNeeded()
    if (dacConnectionProvider != null && !streamingActive) return false

    if (
      startMediaTimeUs == AudioSink.CURRENT_POSITION_NOT_SET && presentationTimeUs != C.TIME_UNSET
    ) {
      startMediaTimeUs = presentationTimeUs
    }

    val format = inputFormat
    val channelCount = (format?.channelCount ?: 0).takeIf { it > 0 } ?: 2
    val sourceDepth = depthForEncoding(format?.pcmEncoding ?: Format.NO_VALUE)
    val sourceBytes = sourceDepth / 8
    val targetBytes = containerDepth / 8
    val sourceFrameBytes = sourceBytes * channelCount
    val targetFrameBytes = targetBytes * channelCount

    val inputFrames = buffer.remaining() / sourceFrameBytes
    if (inputFrames == 0) return true

    val writableFrames = driver.getAvailableWrite() / targetFrameBytes
    if (writableFrames <= 0) return false

    val framesToWrite = minOf(inputFrames, writableFrames)
    val payload = convertFrames(buffer, framesToWrite, sourceBytes, targetBytes, channelCount)
    val acceptedBytes = driver.writeAudio(payload, payload.size)
    val acceptedFrames = acceptedBytes / targetFrameBytes

    buffer.position(buffer.position() + acceptedFrames * sourceFrameBytes)
    framesWritten += acceptedFrames
    return acceptedFrames == inputFrames
  }

  override fun playToEndOfStream() {
    endOfStream = true
  }

  override fun isEnded(): Boolean = endOfStream && !driver.hasPendingData()

  override fun hasPendingData(): Boolean = driver.hasPendingData()

  override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
    this.playbackParameters = playbackParameters
  }

  override fun getPlaybackParameters(): PlaybackParameters = playbackParameters

  override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
    this.skipSilenceEnabled = skipSilenceEnabled
  }

  override fun getSkipSilenceEnabled(): Boolean = skipSilenceEnabled

  override fun setAudioAttributes(audioAttributes: AudioAttributes) {
    this.audioAttributes = audioAttributes
  }

  override fun getAudioAttributes(): AudioAttributes = audioAttributes

  override fun setAudioSessionId(audioSessionId: Int) {
    // The platform track owns its own session.
  }

  override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
    // No auxiliary effects are attached.
  }

  override fun enableTunnelingV21() {
    // Tunneling is not used.
  }

  override fun disableTunneling() {
    // Tunneling is not used.
  }

  override fun getAudioTrackBufferSizeUs(): Long = 0L

  override fun setVolume(volume: Float) {
    val bounded = volume.coerceIn(0f, 1f)
    this.volume = bounded
    driver.setSoftwareVolumeMultiplier(bounded.toDouble())
  }

  override fun pause() {
    playing = false
  }

  override fun flush() {
    driver.flushStream()
    framesWritten = 0
    endOfStream = false
    startMediaTimeUs = AudioSink.CURRENT_POSITION_NOT_SET
  }

  override fun reset() {
    driver.stopStream()
    driver.flushStream()
    playing = false
    streamingActive = false
    inputFormat = null
    activeConnection = null
    activeRate = 0
    activeChannels = 0
    containerDepth = 16
    framesWritten = 0
    endOfStream = false
    startMediaTimeUs = AudioSink.CURRENT_POSITION_NOT_SET
  }

  private fun negotiateIfNeeded() {
    val provider = dacConnectionProvider ?: return
    val connected = provider()

    if (streamingActive) {
      if (connected == null || activeConnection !== connected) {
        driver.stopStream()
        streamingActive = false
        activeConnection = null
        framesWritten = 0
        startMediaTimeUs = AudioSink.CURRENT_POSITION_NOT_SET
      }
    }
    if (connected == null) return

    activeConnection = connected
    capabilities = connected.capabilities

    val rate = inputFormat?.sampleRate?.takeIf { it != Format.NO_VALUE && it > 0 } ?: 44100
    val channelCount = inputFormat?.channelCount?.takeIf { it != Format.NO_VALUE && it > 0 } ?: 2
    val inputDepth = depthForEncoding(inputFormat?.pcmEncoding ?: Format.NO_VALUE)
    val selected = selectFormat(capabilities.supportedFormats, rate, channelCount, inputDepth)
    val targetDepth =
      if (UsbAudioDriver.nativeLoaded) {
        selected?.bitDepth ?: inputDepth
      } else {
        16
      }

    if (
      streamingActive &&
        (rate != activeRate || channelCount != activeChannels || targetDepth != containerDepth)
    ) {
      driver.stopStream()
      streamingActive = false
      framesWritten = 0
      startMediaTimeUs = AudioSink.CURRENT_POSITION_NOT_SET
    }
    if (streamingActive) return

    val result =
      driver.startStream(
        fd = connected.fileDescriptor,
        interfaceNumber = selected?.interfaceNumber ?: 0,
        altSetting = selected?.altSetting ?: 1,
        dataEp = selected?.endpointAddress ?: DEFAULT_ENDPOINT,
        syncEp = selected?.syncEndpointAddress ?: -1,
        sampleRate = rate,
        bitDepth = targetDepth,
        channels = channelCount,
      )
    if (result == 0) {
      streamingActive = true
      activeRate = rate
      activeChannels = channelCount
      containerDepth = targetDepth
      framesWritten = 0
    } else {
      listener?.onAudioSinkError(
        IllegalStateException("Failed to start USB audio stream: $result"),
      )
      streamingActive = false
    }
  }

  private fun selectFormat(
    formats: List<DacFormat>,
    rate: Int,
    channelCount: Int,
    depth: Int,
  ): DacFormat? {
    if (formats.isEmpty()) return null

    fun matchesRate(format: DacFormat): Boolean =
      format.sampleRates.isEmpty() || format.sampleRates.contains(rate)

    formats
      .firstOrNull {
        matchesRate(it) && it.channels == channelCount && it.bitDepth == depth
      }
      ?.let {
        return it
      }

    formats
      .filter { matchesRate(it) && it.channels == channelCount }
      .maxByOrNull { it.bitDepth }
      ?.let {
        return it
      }

    val rateMatches = formats.filter(::matchesRate)
    rateMatches
      .maxByOrNull { it.bitDepth }
      ?.let {
        return it
      }
    rateMatches.firstOrNull()?.let {
      return it
    }
    return formats.first()
  }

  private fun convertFrames(
    buffer: ByteBuffer,
    frames: Int,
    sourceBytes: Int,
    targetBytes: Int,
    channelCount: Int,
  ): ByteArray {
    val source = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    val output = ByteArray(frames * targetBytes * channelCount)
    var index = 0
    for (frame in 0 until frames) {
      for (channel in 0 until channelCount) {
        val sample =
          when (sourceBytes) {
            2 -> source.getShort().toInt()
            3 -> read24(source)
            else -> source.getInt()
          }
        val converted =
          when {
            targetBytes > sourceBytes -> sample shl ((targetBytes - sourceBytes) * 8)
            targetBytes < sourceBytes -> sample shr ((sourceBytes - targetBytes) * 8)
            else -> sample
          }
        output[index++] = (converted and 0xFF).toByte()
        if (targetBytes >= 2) output[index++] = ((converted ushr 8) and 0xFF).toByte()
        if (targetBytes >= 3) output[index++] = ((converted ushr 16) and 0xFF).toByte()
        if (targetBytes >= 4) output[index++] = ((converted ushr 24) and 0xFF).toByte()
      }
    }
    return output
  }

  private fun read24(buffer: ByteBuffer): Int {
    val first = buffer.get().toInt() and 0xFF
    val second = buffer.get().toInt() and 0xFF
    val third = buffer.get().toInt() and 0xFF
    val raw = first or (second shl 8) or (third shl 16)
    return if (raw and SIGN_BIT_24 != 0) raw or -FULL_RANGE_24 else raw
  }

  private fun depthForEncoding(encoding: Int): Int =
    when (encoding) {
      C.ENCODING_PCM_24BIT -> 24
      C.ENCODING_PCM_32BIT -> 32
      else -> 16
    }

  private companion object {
    const val MAX_FALLBACK_RATE = 192000
    const val DEFAULT_ENDPOINT = 0x01
    const val SIGN_BIT_24 = 0x800000
    const val FULL_RANGE_24 = 0x1000000
  }
}
