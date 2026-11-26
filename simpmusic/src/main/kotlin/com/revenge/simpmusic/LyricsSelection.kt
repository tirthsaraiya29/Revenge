package com.revenge.simpmusic

import com.revenge.simpmusic.models.LyricsData
import kotlin.math.abs

internal const val DURATION_TOLERANCE_SECONDS = 10
internal const val MAX_ALTERNATE_LYRICS = 5

internal fun LyricsData.durationDistance(targetSeconds: Int): Int =
  abs((duration ?: 0) - targetSeconds)

internal fun LyricsData.matchesDuration(targetSeconds: Int): Boolean =
  targetSeconds <= 0 || durationDistance(targetSeconds) <= DURATION_TOLERANCE_SECONDS

internal fun LyricsData.bestLyricsText(): String? =
  sequenceOf(richSyncLyrics, syncedLyrics, plainLyrics)
    .firstOrNull { !it.isNullOrBlank() }

internal fun selectLyrics(tracks: List<LyricsData>, durationSeconds: Int): String? {
  val candidates = tracks.filter { it.matchesDuration(durationSeconds) }
  if (candidates.isEmpty()) return null
  val chosen = if (durationSeconds > 0) {
    candidates.minByOrNull { it.durationDistance(durationSeconds) }
  } else {
    candidates.first()
  }
  return chosen?.bestLyricsText()
}

internal fun collectAlternativeLyrics(tracks: List<LyricsData>, durationSeconds: Int): List<String> {
  val ordered = if (durationSeconds > 0) {
    tracks.sortedBy { it.durationDistance(durationSeconds) }
  } else {
    tracks
  }
  val emitted = ArrayList<String>()
  var plainEmitted = false
  for (track in ordered) {
    if (!track.matchesDuration(durationSeconds)) continue
    val synced = sequenceOf(track.richSyncLyrics, track.syncedLyrics)
      .firstOrNull { !it.isNullOrBlank() }
    if (synced != null) {
      emitted.add(synced)
      if (emitted.size >= MAX_ALTERNATE_LYRICS) break
    }
    if (!plainEmitted) {
      val plain = track.plainLyrics
      if (!plain.isNullOrBlank()) {
        plainEmitted = true
        emitted.add(plain)
        if (emitted.size >= MAX_ALTERNATE_LYRICS) break
      }
    }
  }
  return emitted
}
