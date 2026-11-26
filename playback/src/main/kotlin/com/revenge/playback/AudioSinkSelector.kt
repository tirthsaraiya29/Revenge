@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.revenge.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink

@UnstableApi
object AudioSinkSelector {

    fun selectSink(
        isBitPerfectActive: Boolean,
        usbDacAudioSink: AudioSink?,
        defaultAudioSink: AudioSink,
    ): AudioSink {
        if (!isBitPerfectActive) return defaultAudioSink
        return usbDacAudioSink ?: defaultAudioSink
    }

    fun createRoutingSink(
        defaultAudioSink: AudioSink,
        usbDacAudioSink: AudioSink,
        isBitPerfectActive: () -> Boolean,
    ): AudioSink = RoutingAudioSink(
        defaultSink = defaultAudioSink,
        usbSink = usbDacAudioSink,
        isBitPerfectActive = isBitPerfectActive,
    )
}
