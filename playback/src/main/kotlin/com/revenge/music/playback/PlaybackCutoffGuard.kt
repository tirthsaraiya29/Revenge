package com.revenge.music.playback

import androidx.media3.common.C

sealed interface CutoffDecision {
  data object AllowEnd : CutoffDecision

  data class RecoverPrematureCutoff(val resumePositionMs: Long, val retryAttempt: Int) :
    CutoffDecision
}

/**
 * Guards against a stream reporting STATE_ENDED before it actually reached the song's canonical
 * duration. A genuinely short decoder duration wins over metadata, so only a real discrepancy is
 * recovered, and only a bounded number of times per track.
 */
class PlaybackCutoffGuard(
  private val thresholdMs: Long = 3000L,
  private val maxRetriesPerTrack: Int = 2,
) {
  private var retries = 0

  @Suppress("UNUSED_PARAMETER")
  fun onTrackChanged(mediaId: String?) {
    retries = 0
  }

  fun verifyTrackCompletion(
    currentPositionMs: Long,
    canonicalDurationMs: Long,
    playerDurationMs: Long = -1L,
  ): CutoffDecision {
    val position = currentPositionMs.coerceAtLeast(0L)

    // The demuxer's own duration is authoritative when it is known and close to the position.
    if (playerDurationMs > 0L && playerDurationMs - position <= thresholdMs) {
      return CutoffDecision.AllowEnd
    }

    if (canonicalDurationMs <= 0L) return CutoffDecision.AllowEnd

    val missingMs = canonicalDurationMs - position
    if (missingMs <= thresholdMs || retries >= maxRetriesPerTrack) {
      return CutoffDecision.AllowEnd
    }

    retries++
    return CutoffDecision.RecoverPrematureCutoff(resumePositionMs = position, retryAttempt = retries)
  }

  fun getRetryCount(): Int = retries

  companion object {
    /** Prefers the canonical (metadata) duration and falls back to a real player duration. */
    fun computeEffectiveDuration(canonicalDurationSeconds: Int?, playerDurationMs: Long): Long {
      val canonicalMs = (canonicalDurationSeconds ?: 0).coerceAtLeast(0) * 1000L
      if (canonicalMs > 0L) return canonicalMs
      return if (playerDurationMs != C.TIME_UNSET && playerDurationMs > 0L) playerDurationMs else 0L
    }
  }
}
