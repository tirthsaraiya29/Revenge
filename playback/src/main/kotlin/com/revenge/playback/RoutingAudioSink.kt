@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.revenge.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer

@UnstableApi
class RoutingAudioSink(
    val defaultSink: AudioSink,
    val usbSink: AudioSink,
    private val isBitPerfectActive: () -> Boolean,
) : AudioSink {

    @Volatile
    var activeSink: AudioSink = defaultSink
        private set

    private var configuredFormat: Format? = null
    private var configuredBufferSize: Int = 0
    private var configuredOutputChannels: IntArray? = null
    private var playing: Boolean = false

    init {
        reselect()
    }

    fun reselect() {
        val previous = activeSink
        val target = chooseTarget()
        if (target === previous) return

        activeSink = target
        previous.pause()
        previous.flush()

        val format = configuredFormat
        if (format != null && target.supportsFormat(format)) {
            target.configure(format, configuredBufferSize, configuredOutputChannels)
        }
        if (playing) target.play()
    }

    private fun chooseTarget(): AudioSink {
        if (!isBitPerfectActive()) return defaultSink
        val format = configuredFormat
        if (format != null && !usbSink.supportsFormat(format)) return defaultSink
        return usbSink
    }

    override fun setListener(listener: AudioSink.Listener) {
        defaultSink.setListener(listener)
        usbSink.setListener(listener)
    }

    override fun supportsFormat(format: Format): Boolean = activeSink.supportsFormat(format)

    override fun getFormatSupport(format: Format): Int = activeSink.getFormatSupport(format)

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
        activeSink.getCurrentPositionUs(sourceEnded)

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        configuredFormat = inputFormat
        configuredBufferSize = specifiedBufferSize
        configuredOutputChannels = outputChannels
        activeSink.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun play() {
        reselect()
        playing = true
        activeSink.play()
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
        configuredFormat = null
        configuredBufferSize = 0
        configuredOutputChannels = null
        reselect()
    }

    override fun handleDiscontinuity() = activeSink.handleDiscontinuity()

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean = activeSink.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)

    override fun playToEndOfStream() = activeSink.playToEndOfStream()

    override fun isEnded(): Boolean = activeSink.isEnded()

    override fun hasPendingData(): Boolean = activeSink.hasPendingData()

    override fun getAudioTrackBufferSizeUs(): Long = activeSink.getAudioTrackBufferSizeUs()

    override fun getPlaybackParameters(): PlaybackParameters = activeSink.getPlaybackParameters()

    override fun getSkipSilenceEnabled(): Boolean = activeSink.getSkipSilenceEnabled()

    override fun getAudioAttributes(): AudioAttributes =
        activeSink.getAudioAttributes() ?: AudioAttributes.DEFAULT

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        defaultSink.setPlaybackParameters(playbackParameters)
        usbSink.setPlaybackParameters(playbackParameters)
    }

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        defaultSink.setSkipSilenceEnabled(skipSilenceEnabled)
        usbSink.setSkipSilenceEnabled(skipSilenceEnabled)
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        defaultSink.setAudioAttributes(audioAttributes)
        usbSink.setAudioAttributes(audioAttributes)
    }

    override fun setAudioSessionId(audioSessionId: Int) {
        defaultSink.setAudioSessionId(audioSessionId)
        usbSink.setAudioSessionId(audioSessionId)
    }

    override fun setAuxEffectInfo(auxEffectInfo: androidx.media3.common.AuxEffectInfo) {
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
}
