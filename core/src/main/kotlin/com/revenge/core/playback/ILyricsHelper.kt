package com.revenge.core.playback

import com.revenge.core.models.MediaMetadata

data class LyricsWithProvider(
  val lyrics: String?,
  val providerName: String,
)

interface ILyricsHelper {
  suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider
}
