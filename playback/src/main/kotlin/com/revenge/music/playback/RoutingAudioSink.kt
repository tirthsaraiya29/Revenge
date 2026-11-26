package com.revenge.music.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer

/**
 * An [AudioSink] that forwards to either the system sink or a USB DAC sink. The switch can happen
 * at runtime (when bit-perfect output is toggled); an already configured format is replayed into
 * the newly selected sink so playback continues without a gap.
 */
class RoutingAudioSink(
  val defaultSink: AudioSink,
  val usbSink: AudioSink,
  private val isBitPerfectActive: () -> Boolean,
) : AudioSink {

  @Volatile
  var activeSink: AudioSink = defaultSink
    private set

  private data class Configuration(
    val format: Format,
    val bufferSize: Int,
    val outputChannels: IntArray?,
  )

  private var lastConfiguration: Configuration? = null
  private var playing = false

  init {
    reselect()
  }

  fun reselect() {
    val wanted =
      if (isBitPerfectActive()) {
        val config = lastConfiguration
        if (config != null && !usbSink.supportsFormat(config.format)) defaultSink else usbSink
      } else {
        defaultSink
      }
    if (wanted === activeSink) return

    val previous = activeSink
    activeSink = wanted
    previous.pause()
    previous.flush()
    lastConfiguration?.let { config ->
      if (wanted.supportsFormat(config.format)) {
        wanted.configure(config.format, config.bufferSize, config.outputChannels)
      }
    }
    if (playing) wanted.play()
  }

  override fun setListener(listener: AudioSink.Listener) {
    defaultSink.setListener(listener)
    usbSink.setListener(listener)
  }

  override fun supportsFormat(format: Format): Boolean = activeSink.supportsFormat(format)

  override fun getFormatSupport(format: Format): Int = activeSink.getFormatSupport(format)

  override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
    activeSink.getCurrentPositionUs(sourceEnded)

  override fun configure(
    inputFormat: Format,
    specifiedBufferSize: Int,
    outputChannels: IntArray?,
  ) {
    lastConfiguration = Configuration(inputFormat, specifiedBufferSize, outputChannels)
    reselect()
    activeSink.configure(inputFormat, specifiedBufferSize, outputChannels)
  }

  override fun play() {
    reselect()
    playing = true
    activeSink.play()
  }

  override fun handleDiscontinuity() {
    activeSink.handleDiscontinuity()
  }

  override fun handleBuffer(
    buffer: ByteBuffer,
    presentationTimeUs: Long,
    encodedAccessUnitCount: Int,
  ): Boolean = activeSink.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)

  override fun playToEndOfStream() {
    activeSink.playToEndOfStream()
  }

  override fun isEnded(): Boolean = activeSink.isEnded()

  override fun hasPendingData(): Boolean = activeSink.hasPendingData()

  override fun getAudioTrackBufferSizeUs(): Long = activeSink.getAudioTrackBufferSizeUs()

  override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
    defaultSink.setPlaybackParameters(playbackParameters)
    usbSink.setPlaybackParameters(playbackParameters)
  }

  override fun getPlaybackParameters(): PlaybackParameters = activeSink.playbackParameters

  override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
    defaultSink.setSkipSilenceEnabled(skipSilenceEnabled)
    usbSink.setSkipSilenceEnabled(skipSilenceEnabled)
  }

  override fun getSkipSilenceEnabled(): Boolean = activeSink.skipSilenceEnabled

  override fun setAudioAttributes(audioAttributes: AudioAttributes) {
    defaultSink.setAudioAttributes(audioAttributes)
    usbSink.setAudioAttributes(audioAttributes)
  }

  override fun getAudioAttributes(): AudioAttributes =
    activeSink.audioAttributes ?: AudioAttributes.DEFAULT

  override fun setAudioSessionId(audioSessionId: Int) {
    defaultSink.setAudioSessionId(audioSessionId)
    usbSink.setAudioSessionId(audioSessionId)
  }

  override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
    defaultSink.setAuxEffectInfo(auxEffectInfo)
    usbSink.setAuxEffectInfo(auxEffectInfo)
  }

  override fun enableTunnelingV21() {
    defaultSink.enableTunnelingV21()
    usbSink.enableTunnelingV21()
  }

  override fun disableTunneling() {
    defaultSink.disableTunneling()
    usbSink.disableTunneling()
  }

  override fun setVolume(volume: Float) {
    defaultSink.setVolume(volume)
    usbSink.setVolume(volume)
  }

  override fun pause() {
    playing = false
    defaultSink.pause()
    usbSink.pause()
  }

  override fun flush() {
    reselect()
    defaultSink.flush()
    usbSink.flush()
  }

  override fun reset() {
    playing = false
    defaultSink.reset()
    usbSink.reset()
    lastConfiguration = null
    reselect()
  }
}
