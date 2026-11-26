package com.revenge.music.lyrics

import android.content.Context
import com.revenge.betterlyrics.BetterLyrics
import com.revenge.music.constants.EnableBetterLyricsKey
import com.revenge.music.utils.dataStore
import com.revenge.music.utils.get

object BetterLyricsProvider : LyricsProvider {

    override val name: String = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableBetterLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = BetterLyrics.getLyrics(title, artist, duration, album)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        BetterLyrics.getAllLyrics(title, artist, duration, album, callback)
    }
}
