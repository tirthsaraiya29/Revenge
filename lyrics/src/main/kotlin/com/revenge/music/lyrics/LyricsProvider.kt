package com.revenge.music.lyrics

import android.content.Context

interface LyricsProvider {
    val name: String

    fun isEnabled(context: Context): Boolean

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<String>

    suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) {
        getLyrics(id, title, artist, duration, album).onSuccess(callback)
    }
}

enum class ProviderOutcome { SYNCED, UNSYNCED, FAILED }

sealed interface ProviderChoice {
    data object Pending : ProviderChoice
    data class Selected(val name: String) : ProviderChoice
    data object None : ProviderChoice
}
