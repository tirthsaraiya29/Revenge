package com.revenge.music.playback

import androidx.media3.common.Player

/**
 * Decides whether removing the app task should stop playback. Requested background playback is
 * kept while buffering or playing; an explicitly enabled "stop on task clear" always wins.
 */
fun shouldStopPlaybackOnTaskRemoval(
  stopOnTaskClear: Boolean,
  playWhenReady: Boolean,
  playbackState: Int,
): Boolean =
  stopOnTaskClear ||
    !playWhenReady ||
    playbackState == Player.STATE_IDLE ||
    playbackState == Player.STATE_ENDED
