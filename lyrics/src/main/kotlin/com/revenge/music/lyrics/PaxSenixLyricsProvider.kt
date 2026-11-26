package com.revenge.music.lyrics

import android.content.Context
import com.revenge.music.constants.EnablePaxsenixKey
import com.revenge.music.utils.dataStore
import com.revenge.music.utils.get
import com.revenge.paxsenix.Paxsenix
import kotlinx.coroutines.CancellationException
import timber.log.Timber

object PaxSenixLyricsProvider : LyricsProvider {
  private const val TAG = "PaxSenixProvider"

  override val name = "Paxsenix"

  override fun isEnabled(context: Context): Boolean {
    val enabled = context.dataStore[EnablePaxsenixKey] ?: true
    if (enabled) Paxsenix.init(context)
    return enabled
  }

  override suspend fun getLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
  ): Result<String> =
    try {
      Paxsenix.getLyrics(title, artist, duration, album)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Timber.tag(TAG).e(e, "getLyrics failed")
      Result.failure(e)
    }

  override suspend fun getAllLyrics(
    id: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
    callback: (String) -> Unit,
  ) {
    try {
      Paxsenix.getAllLyrics(title, artist, duration, album, callback)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Timber.tag(TAG).e(e, "getAllLyrics failed")
    }
  }
}
