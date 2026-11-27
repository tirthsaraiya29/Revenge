package com.revenge.lyrics.lyrics

import android.content.Context
import com.revenge.core.constants.EnableYouLyPlusKey
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get
import com.revenge.youlyplus.YouLyPlus

object YouLyPlusLyricsProvider : LyricsProvider {

  override val name: String = "YouLyPlus"

  override fun isEnabled(context: Context): Boolean = context.dataStore[EnableYouLyPlusKey] ?: true

  override suspend fun getLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
  ): Result<String> = YouLyPlus.getLyrics(title, artist, duration, album, id, null)

  override suspend fun getAllLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
    callback: (String) -> Unit,
  ) {
    YouLyPlus.getAllLyrics(title, artist, duration, album, id, null, callback)
  }
}
