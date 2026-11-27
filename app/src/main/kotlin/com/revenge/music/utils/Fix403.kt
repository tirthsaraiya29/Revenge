package com.revenge.music.utils

import com.revenge.innertube.YouTube
import timber.log.Timber

/**
 * Recovery helpers for HTTP 403 / bot-check playback failures. The transport layer only reports
 * the server's reason text; this object decides whether a retry with a refreshed visitor identity
 * can help.
 */
object Fix403 {

    private val BOT_MARKERS = listOf(
        "403",
        "Error 2000",
        "Sign in to confirm",
    )

    fun isBotDetectionError(error: Throwable?): Boolean {
        val message = error?.message ?: return false
        return BOT_MARKERS.any { message.contains(it, ignoreCase = true) }
    }

    fun isBotDetectionMessage(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        return BOT_MARKERS.any { message.contains(it, ignoreCase = true) }
    }

    /** Refreshes the guest visitor identity so the next request is signed with a fresh session. */
    suspend fun refreshVisitorData(): Boolean = runCatching {
        YouTube.refreshVisitorData().isSuccess
    }.onFailure { Timber.w(it, "Visitor data refresh failed") }
        .getOrDefault(false)
}
