package com.revenge.lrclib.models

import kotlin.math.abs
import kotlinx.serialization.Serializable

/** A single song entry returned by the LrcLib search API. */
@Serializable
data class Track(
  val id: Int,
  val trackName: String,
  val artistName: String,
  val duration: Double,
  val plainLyrics: String? = null,
  val syncedLyrics: String? = null,
)

/** Sentinel duration used when the caller does not know the track length. */
internal const val DURATION_UNKNOWN = -1

private const val STRICT_TOLERANCE_SECONDS = 2
private const val RELAXED_TOLERANCE_SECONDS = 5
private const val MIN_SIMILARITY = 0.6

private val Track.durationSeconds: Int
  get() = duration.toInt()

/**
 * Picks the closest track when the duration is known, requiring it to be within a strict
 * [STRICT_TOLERANCE_SECONDS] window. With an unknown duration the first synchronised result is
 * preferred, falling back to the first entry.
 */
internal fun List<Track>.bestMatchingFor(duration: Int): Track? {
  if (isEmpty()) return null
  if (duration == DURATION_UNKNOWN) {
    return firstOrNull { it.syncedLyrics != null } ?: first()
  }
  return minByOrNull { abs(it.durationSeconds - duration) }
    ?.takeIf { abs(it.durationSeconds - duration) <= STRICT_TOLERANCE_SECONDS }
}

/**
 * Like [bestMatchingFor] but tolerates a wider [RELAXED_TOLERANCE_SECONDS] window and prefers a
 * synchronised result over a plain one at the same distance.
 */
internal fun List<Track>.bestMatchingForRelaxed(duration: Int): Track? {
  if (isEmpty()) return null
  if (duration == DURATION_UNKNOWN) {
    return firstOrNull { it.syncedLyrics != null } ?: first()
  }
  val withinRange = filter { abs(it.durationSeconds - duration) <= RELAXED_TOLERANCE_SECONDS }
  return withinRange.filter { it.syncedLyrics != null }
    .minByOrNull { abs(it.durationSeconds - duration) }
    ?: withinRange.minByOrNull { abs(it.durationSeconds - duration) }
}

/**
 * Duration-less matching: when the duration is unknown the best title/artist match above
 * [MIN_SIMILARITY] wins, otherwise the relaxed duration match is used.
 */
internal fun List<Track>.bestMatchingFor(
  duration: Int,
  trackName: String? = null,
  artistName: String? = null,
): Track? {
  if (isEmpty()) return null
  if (duration == DURATION_UNKNOWN) {
    if (trackName != null && artistName != null) {
      return findBestMatch(trackName, artistName)
    }
    return firstOrNull { it.syncedLyrics != null } ?: first()
  }
  return bestMatchingForRelaxed(duration)
}

private fun List<Track>.findBestMatch(trackName: String, artistName: String): Track? =
  map { track ->
    val titleScore = similarity(trackName, track.trackName)
    val artistScore = similarity(artistName, track.artistName)
    val average = (titleScore + artistScore) / 2.0
    val total = average + if (track.syncedLyrics != null) 0.1 else 0.0
    ScoredTrack(track, average, total)
  }
    .filter { it.average > MIN_SIMILARITY }
    .maxByOrNull { it.total }
    ?.track

private data class ScoredTrack(val track: Track, val average: Double, val total: Double)

/**
 * Case-insensitive similarity in the range 0..1. An exact match is 1, an empty side is 0, and
 * otherwise the better of a substring bonus and the normalised Levenshtein distance is used.
 */
internal fun similarity(first: String, second: String): Double {
  val left = first.trim().lowercase()
  val right = second.trim().lowercase()
  if (left == right) return 1.0
  if (left.isEmpty() || right.isEmpty()) return 0.0

  val containment = if (left.contains(right) || right.contains(left)) 0.8 else 0.0
  val maxLength = maxOf(left.length, right.length)
  val distanceScore = 1.0 - levenshtein(left, right).toDouble() / maxLength
  return maxOf(containment, distanceScore)
}

private fun levenshtein(first: String, second: String): Int {
  var previous = IntArray(second.length + 1) { it }
  var current = IntArray(second.length + 1)

  for (i in 1..first.length) {
    current[0] = i
    for (j in 1..second.length) {
      val substitution = previous[j - 1] + if (first[i - 1] == second[j - 1]) 0 else 1
      current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
    }
    val swap = previous
    previous = current
    current = swap
  }
  return previous[second.length]
}
