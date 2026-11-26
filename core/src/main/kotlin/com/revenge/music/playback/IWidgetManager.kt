package com.revenge.music.playback

/** Pushes a playback snapshot to home-screen widgets. */
interface IWidgetManager {
  suspend fun updateWidgets(
    title: String,
    artist: String,
    artworkUri: String?,
    isPlaying: Boolean,
    isLiked: Boolean,
    duration: Long = 0,
    currentPosition: Long = 0,
  )
}
