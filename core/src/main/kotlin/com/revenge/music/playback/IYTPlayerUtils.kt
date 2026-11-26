package com.revenge.music.playback

/** Opaque player-data access used by the app player implementation. */
interface IYTPlayerUtils {
  suspend fun getStreamInfo(videoId: String, context: android.content.Context): Any?

  suspend fun getAudioConfig(videoId: String): Any?
}
