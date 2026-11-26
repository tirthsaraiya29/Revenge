package com.revenge.lrclib.models

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max

@Serializable
data class Track(
  val id: Int,
  val trackName: String,
  val artistName: String,
  val duration: Double,
  val plainLyrics: String? = null,
  val syncedLyrics: String? = null,
)

internal const val DURATION_UNKNOWN = -1

internal fun similarity(first: String, second: String): Double {
  val a = first.trim().lowercase()
  val b = second.trim().lowercase()
  if (a == b) return 1.0
  if (a.isEmpty() || b.isEmpty()) return 0.0
  val containment = if (a.contains(b) || b.contains(a)) 0.8 else 0.0
  val maxLength = max(a.length, b.length)
  val distanceScore = 1.0 - levenshtein(a, b).toDouble() / maxLength
  return max(containment, distanceScore)
}

private fun levenshtein(a: String, b: String): Int {
  var previous = IntArray(b.length + 1) { it }
  var current = IntArray(b.length + 1)
  for (i in 1..a.length) {
    current[0] = i
    for (j in 1..b.length) {
      val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
      current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
    }
    val swap = previous
    previous = current
    current = swap
  }
  return previous[b.length]
}

internal fun List<Track>.bestMatchingFor(duration: Int): Track? {
  if (isEmpty()) return null
  if (duration == DURATION_UNKNOWN) {
    return firstOrNull { it.syncedLyrics != null } ?: first()
  }
  val closest = minByOrNull { abs(it.duration.toInt() - duration) } ?: return null
  return closest.takeIf { abs(it.duration.toInt() - duration) <= 2 }
}

internal fun List<Track>.bestMatchingForRelaxed(duration: Int): Track? {
  if (isEmpty()) return null
  if (duration == DURATION_UNKNOWN) {
    return firstOrNull { it.syncedLyrics != null } ?: first()
  }
  val inRange = filter { abs(it.duration.toInt() - duration) <= 5 }
  if (inRange.isEmpty()) return null
  return inRange.filter { it.syncedLyrics != null }
    .minByOrNull { abs(it.duration.toInt() - duration) }
    ?: inRange.minByOrNull { abs(it.duration.toInt() - duration) }
}

internal fun List<Track>.bestMatchingFor(
  duration: Int,
  trackName: String? = null,
  artistName: String? = null,
): Track? {
  if (isEmpty()) return null
  if (duration == DURATION_UNKNOWN) {
    if (trackName != null && artistName != null) {
      return findBestNamedMatch(trackName, artistName)
    }
    return firstOrNull { it.syncedLyrics != null } ?: first()
  }
  return bestMatchingForRelaxed(duration)
}

private fun List<Track>.findBestNamedMatch(trackName: String, artistName: String): Track? {
  var best: Track? = null
  var bestScore = Double.NEGATIVE_INFINITY
  for (track in this) {
    val average = (similarity(trackName, track.trackName) + similarity(artistName, track.artistName)) / 2.0
    if (average <= 0.6) continue
    val total = average + if (track.syncedLyrics != null) 0.1 else 0.0
    if (total > bestScore) {
      bestScore = total
      best = track
    }
  }
  return best
}
