package com.revenge.music.lyrics

import android.content.Context
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.WatchEndpoint

object YouTubeLyricsProvider : LyricsProvider {
  override val name = "YouTube Music"

  override fun isEnabled(context: Context) = true

  override suspend fun getLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
  ): Result<String> = runCatching {
    val next = YouTube.next(WatchEndpoint(videoId = id)).getOrThrow()
    YouTube.lyrics(
        endpoint =
          next.lyricsEndpoint ?: throw IllegalStateException("Lyrics endpoint not found"),
      )
      .getOrThrow() ?: throw IllegalStateException("Lyrics unavailable")
  }
}
