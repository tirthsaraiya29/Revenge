package com.revenge.lyrics.lyrics

import android.content.Context
import com.revenge.core.constants.EnableLrcLibKey
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get
import com.revenge.lrclib.LrcLib

object LrcLibLyricsProvider : LyricsProvider {

  override val name: String = "LrcLib"

  override fun isEnabled(context: Context): Boolean = context.dataStore[EnableLrcLibKey] ?: true

  override suspend fun getLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
  ): Result<String> = LrcLib.getLyrics(title, artist, duration, album)

  override suspend fun getAllLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
    callback: (String) -> Unit,
  ) {
    LrcLib.getAllLyrics(title, artist, duration, album, callback)
  }
}
