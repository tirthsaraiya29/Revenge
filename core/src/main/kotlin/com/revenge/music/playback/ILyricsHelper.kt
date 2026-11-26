package com.revenge.music.playback

import com.revenge.music.models.MediaMetadata

/** Result of a lyrics lookup: raw text (null means not found) plus the provider that supplied it. */
data class LyricsWithProvider(val lyrics: String?, val providerName: String)

/** Extension point implemented by the lyrics module; lookups may hit the network. */
interface ILyricsHelper {
  suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider
}
