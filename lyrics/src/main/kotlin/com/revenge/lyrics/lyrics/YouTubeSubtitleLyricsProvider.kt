package com.revenge.lyrics.lyrics

import android.content.Context
import com.revenge.innertube.YouTube

object YouTubeSubtitleLyricsProvider : LyricsProvider {

  override val name: String = "YouTube Subtitle"

  override fun isEnabled(context: Context): Boolean = true

  override suspend fun getLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
  ): Result<String> = YouTube.transcript(id)
}
