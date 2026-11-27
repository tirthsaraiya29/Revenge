package com.revenge.lyrics.lyrics

import android.content.Context
import com.revenge.core.constants.EnableSimpMusicKey
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get
import com.revenge.simpmusic.SimpMusicLyrics

object SimpMusicLyricsProvider : LyricsProvider {

  override val name: String = "SimpMusic"

  override fun isEnabled(context: Context): Boolean = context.dataStore[EnableSimpMusicKey] ?: true

  override suspend fun getLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
  ): Result<String> = SimpMusicLyrics.getLyrics(id, duration)

  override suspend fun getAllLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
    callback: (String) -> Unit,
  ) {
    SimpMusicLyrics.getAllLyrics(id, duration, callback)
  }
}
