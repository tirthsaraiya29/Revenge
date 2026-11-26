package com.revenge.playback

import androidx.media3.common.C

sealed interface CutoffDecision {
    data object AllowEnd : CutoffDecision

    data class RecoverPrematureCutoff(
        val resumePositionMs: Long,
        val retryAttempt: Int,
    ) : CutoffDecision
}

class PlaybackCutoffGuard(
    private val thresholdMs: Long = 3000L,
    private val maxRetriesPerTrack: Int = 2,
) {

    private var retries: Int = 0

    fun onTrackChanged(mediaId: String?) {
        retries = 0
    }

    fun verifyTrackCompletion(
        currentPositionMs: Long,
        canonicalDurationMs: Long,
        playerDurationMs: Long = -1L,
    ): CutoffDecision {
        val position = currentPositionMs.coerceAtLeast(0L)

        if (playerDurationMs > 0L && playerDurationMs - position <= thresholdMs) {
            return CutoffDecision.AllowEnd
        }
        if (canonicalDurationMs <= 0L) return CutoffDecision.AllowEnd
        if (canonicalDurationMs - position <= thresholdMs) return CutoffDecision.AllowEnd
        if (retries >= maxRetriesPerTrack) return CutoffDecision.AllowEnd

        retries++
        return CutoffDecision.RecoverPrematureCutoff(position, retries)
    }

    fun getRetryCount(): Int = retries

    companion object {
        fun computeEffectiveDuration(
            canonicalDurationSeconds: Int?,
            playerDurationMs: Long,
        ): Long {
            val canonicalMs = (canonicalDurationSeconds ?: 0).coerceAtLeast(0).toLong() * 1000L
            if (canonicalMs > 0L) return canonicalMs
            if (playerDurationMs > 0L && playerDurationMs != C.TIME_UNSET) return playerDurationMs
            return 0L
        }
    }
}
