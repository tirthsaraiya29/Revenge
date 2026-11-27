package com.revenge.lyrics.lyrics

import android.content.Context
import com.revenge.core.constants.EnablePaxsenixKey
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get
import com.revenge.paxsenix.Paxsenix
import kotlinx.coroutines.CancellationException

object PaxSenixLyricsProvider : LyricsProvider {

  override val name: String = "Paxsenix"

  override fun isEnabled(context: Context): Boolean {
    val enabled = context.dataStore[EnablePaxsenixKey] ?: true
    if (enabled) {
      runCatching { Paxsenix.init(context) }
    }
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
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (error: Exception) {
      Result.failure(error)
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
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (_: Exception) {}
  }
}
