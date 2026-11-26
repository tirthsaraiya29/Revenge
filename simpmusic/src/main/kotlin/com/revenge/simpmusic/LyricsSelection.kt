package com.revenge.simpmusic

import com.revenge.simpmusic.models.LyricsData
import kotlin.math.abs

/** How far (in seconds) a candidate's duration may stray from the track's duration. */
internal const val DURATION_TOLERANCE_SECONDS = 10

/** Number of candidates [SimpMusicLyrics.getAllLyrics] is willing to emit. */
internal const val MAX_ALTERNATE_LYRICS = 5

internal fun LyricsData.durationDistance(targetSeconds: Int): Int =
  abs((duration ?: 0) - targetSeconds)

internal fun LyricsData.matchesDuration(targetSeconds: Int): Boolean =
  targetSeconds <= 0 || durationDistance(targetSeconds) <= DURATION_TOLERANCE_SECONDS

/**
 * Picks the richest non-blank text of a candidate: word-by-word sync first, then line sync, then
 * plain text.
 */
internal fun LyricsData.bestLyricsText(): String? =
  richSyncLyrics?.takeIf { it.isNotBlank() }
    ?: syncedLyrics?.takeIf { it.isNotBlank() }
    ?: plainLyrics?.takeIf { it.isNotBlank() }

/**
 * Selects one lyrics text for the given duration. With a positive duration the closest candidate
 * within tolerance wins; otherwise the first API result is used.
 */
internal fun selectLyrics(tracks: List<LyricsData>, durationSeconds: Int): String? {
  val candidates = tracks.filter { it.matchesDuration(durationSeconds) }
  val best =
    if (durationSeconds > 0) {
      candidates.minByOrNull { it.durationDistance(durationSeconds) }
    } else {
      candidates.firstOrNull()
    }
  return best?.bestLyricsText()
}

/**
 * Builds the ordered alternatives exposed to the lyrics picker. Duration-mismatched candidates are
 * skipped and plain text is included at most once.
 */
internal fun collectAlternativeLyrics(
  tracks: List<LyricsData>,
  durationSeconds: Int,
): List<String> {
  val ordered =
    if (durationSeconds > 0) {
      tracks.sortedBy { it.durationDistance(durationSeconds) }
    } else {
      tracks
    }

  val emitted = mutableListOf<String>()
  var plainUsed = false

  for (track in ordered) {
    if (emitted.size > MAX_ALTERNATE_LYRICS - 1) break
    if (!track.matchesDuration(durationSeconds)) continue

    val synced =
      track.richSyncLyrics?.takeIf { it.isNotBlank() }
        ?: track.syncedLyrics?.takeIf { it.isNotBlank() }
    if (synced != null) emitted.add(synced)

    val plain = track.plainLyrics?.takeIf { it.isNotBlank() }
    if (!plainUsed && plain != null) {
      plainUsed = true
      emitted.add(plain)
    }
  }

  return emitted
}
