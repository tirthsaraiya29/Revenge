package com.revenge.lyrics.lyrics

import android.content.Context
import com.revenge.core.constants.EnableKugouKey
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get
import com.revenge.kugou.KuGou

object KuGouLyricsProvider : LyricsProvider {

  override val name: String = "Kugou"

  override fun isEnabled(context: Context): Boolean = context.dataStore[EnableKugouKey] ?: true

  override suspend fun getLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
  ): Result<String> = KuGou.getLyrics(title, artist, duration, album)

  override suspend fun getAllLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
    callback: (String) -> Unit,
  ) {
    KuGou.getAllPossibleLyricsOptions(title, artist, duration, album, callback)
  }
}
