package com.revenge.playback

import androidx.media3.common.Player

fun shouldStopPlaybackOnTaskRemoval(
  stopOnTaskClear: Boolean,
  playWhenReady: Boolean,
  playbackState: Int,
): Boolean {
  if (stopOnTaskClear) return true
  if (!playWhenReady) return true
  return playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED
}
