package com.revenge.music.lyrics

import android.content.Context
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.WatchEndpoint
import kotlinx.coroutines.CancellationException

object YouTubeLyricsProvider : LyricsProvider {

    override val name: String = "YouTube Music"

    override fun isEnabled(context: Context): Boolean = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = try {
        val next = YouTube.next(WatchEndpoint(videoId = id)).getOrNull()
            ?: throw IllegalStateException("Lyrics endpoint not found")
        val endpoint = next.lyricsEndpoint
            ?: throw IllegalStateException("Lyrics endpoint not found")
        val lyrics = YouTube.lyrics(endpoint).getOrNull()
            ?: throw IllegalStateException("Lyrics unavailable")
        Result.success(lyrics)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }
}
