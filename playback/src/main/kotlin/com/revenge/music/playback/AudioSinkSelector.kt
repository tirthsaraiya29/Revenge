package com.revenge.music.playback

import androidx.media3.exoplayer.audio.AudioSink

/** Chooses between the system AudioTrack sink and the USB DAC sink. */
object AudioSinkSelector {
  fun selectSink(
    isBitPerfectActive: Boolean,
    usbDacAudioSink: AudioSink?,
    defaultAudioSink: AudioSink,
  ): AudioSink = if (isBitPerfectActive && usbDacAudioSink != null) usbDacAudioSink else defaultAudioSink

  fun createRoutingSink(
    defaultAudioSink: AudioSink,
    usbDacAudioSink: AudioSink,
    isBitPerfectActive: () -> Boolean,
  ): AudioSink =
    RoutingAudioSink(
      defaultSink = defaultAudioSink,
      usbSink = usbDacAudioSink,
      isBitPerfectActive = isBitPerfectActive,
    )
}
