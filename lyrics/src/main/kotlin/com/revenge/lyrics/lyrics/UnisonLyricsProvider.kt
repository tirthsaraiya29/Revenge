package com.revenge.lyrics.lyrics

import android.content.Context
import com.revenge.betterlyrics.TTMLParser
import com.revenge.core.constants.UnisonLyricsEnabledKey
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get
import com.revenge.unison.Unison

object UnisonLyricsProvider : LyricsProvider {

  override val name: String = "Unison"

  override fun isEnabled(context: Context): Boolean =
    context.dataStore[UnisonLyricsEnabledKey] ?: true

  override suspend fun getLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
  ): Result<String> =
    Unison.getLyrics(
        videoId = id,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = duration,
      )
      .map(::toLrcIfTtml)

  override suspend fun getAllLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
    callback: (String) -> Unit,
  ) {
    Unison.getAllLyrics(
      videoId = id,
      title = title,
      artist = artist,
      album = album,
      durationSeconds = duration,
      callback = { callback(toLrcIfTtml(it)) },
    )
  }

  private fun toLrcIfTtml(content: String): String =
    if (content.trimStart().startsWith("<tt", ignoreCase = true)) {
      TTMLParser.toLRC(TTMLParser.parseTTML(content))
    } else {
      content
    }
}
