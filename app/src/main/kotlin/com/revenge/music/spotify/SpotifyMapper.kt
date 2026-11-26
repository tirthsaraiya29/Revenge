package com.revenge.music.spotify

import com.revenge.music.spotify.models.SpotifyImage
import com.revenge.music.spotify.models.SpotifyPlaylist
import com.revenge.music.spotify.models.SpotifyTrack
import java.util.concurrent.ConcurrentHashMap

data class PrecomputedTrack(
  val normalizedTitle: String,
  val titleBigrams: Set<String>,
  val normalizedArtist: String,
  val artistBigrams: Set<String>,
  val durationMs: Int,
)

/** Deterministic query construction and similarity scoring for Spotify to YouTube matching. */
object SpotifyMapper {

  private const val MAX_CACHE = 4096

  private val normalizedCache = ConcurrentHashMap<String, String>()
  private val bigramCache = ConcurrentHashMap<String, Set<String>>()

  fun buildSearchQuery(track: SpotifyTrack): String {
    val artist = track.artists.firstOrNull()?.name?.trim().orEmpty()
    return if (artist.isBlank()) track.name.trim() else "$artist ${track.name.trim()}"
  }

  fun getPlaylistThumbnail(playlist: SpotifyPlaylist): String? =
    preferredImage(playlist.images)?.url

  fun getTrackThumbnail(track: SpotifyTrack): String? =
    preferredImage(track.album?.images.orEmpty())?.url

  fun precompute(title: String, artist: String, durationMs: Int): PrecomputedTrack {
    val normalizedTitle = normalize(title)
    val normalizedArtist = normalize(artist)
    return PrecomputedTrack(
      normalizedTitle = normalizedTitle,
      titleBigrams = bigrams(normalizedTitle),
      normalizedArtist = normalizedArtist,
      artistBigrams = bigrams(normalizedArtist),
      durationMs = durationMs,
    )
  }

  fun matchScore(
    spotifyTitle: String,
    spotifyArtist: String,
    spotifyDurationMs: Int,
    candidateTitle: String,
    candidateArtist: String,
    candidateDurationSec: Int?,
  ): Double =
    matchScorePrecomputed(
      precompute(spotifyTitle, spotifyArtist, spotifyDurationMs),
      candidateTitle,
      candidateArtist,
      candidateDurationSec,
    )

  fun matchScorePrecomputed(
    precomputed: PrecomputedTrack,
    candidateTitle: String,
    candidateArtist: String,
    candidateDurationSec: Int?,
  ): Double {
    val titleScore = similarity(precomputed.normalizedTitle, normalize(candidateTitle))
    val artistScore = similarity(precomputed.normalizedArtist, normalize(candidateArtist))
    val durationScore = durationScore(precomputed.durationMs / 1000, candidateDurationSec)
    return 0.45 * titleScore + 0.35 * artistScore + 0.20 * durationScore
  }

  fun earlyExitThreshold(): Double = 0.95

  fun titleSimilarity(titleA: String, titleB: String): Double {
    val a = normalize(titleA)
    val b = normalize(titleB)
    if (a.isEmpty() || b.isEmpty()) return if (a == b) 1.0 else 0.0
    if (a == b) return 1.0
    val dice = dice(bigrams(a), bigrams(b))
    if (a.contains(b) || b.contains(a)) return maxOf(0.6, dice)
    return dice
  }

  internal fun normalize(value: String): String {
    normalizedCache[value]?.let { return it }
    var text = value.lowercase()
    text = text.replace(Regex("\\([^)]*(feat|ft)\\.?[^)]*\\)"), " ")
    text = text.replace(Regex("\\[[^\\]]*\\]"), " ")
    text = text.replace(Regex("\\([^)]*remaster[^)]*\\)"), " ", ignoreCase = true)
    text = text.replace(Regex("\\([^)]*remix[^)]*\\)"), " ", ignoreCase = true)
    text = text.replace(Regex("[^a-z0-9\\s]"), " ")
    text = text.replace(Regex("\\s+"), " ").trim()
    if (normalizedCache.size > MAX_CACHE) normalizedCache.clear()
    normalizedCache[value] = text
    return text
  }

  private fun similarity(a: String, b: String): Double {
    if (a.isEmpty() || b.isEmpty()) return if (a == b) 1.0 else 0.0
    if (a == b) return 1.0
    return dice(bigrams(a), bigrams(b))
  }

  private fun bigrams(text: String): Set<String> {
    if (text.isEmpty()) return emptySet()
    bigramCache[text]?.let { return it }
    val result =
      if (text.length < 2) setOf(text)
      else buildSet { for (i in 0 until text.length - 1) add(text.substring(i, i + 2)) }
    if (bigramCache.size > MAX_CACHE) bigramCache.clear()
    bigramCache[text] = result
    return result
  }

  private fun dice(a: Set<String>, b: Set<String>): Double {
    if (a.isEmpty() && b.isEmpty()) return 1.0
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val intersection = a.count { it in b }
    return 2.0 * intersection / (a.size + b.size)
  }

  private fun durationScore(spotifySeconds: Int, candidateSeconds: Int?): Double {
    if (candidateSeconds == null || spotifySeconds <= 0) return 0.5
    val difference = kotlin.math.abs(spotifySeconds - candidateSeconds)
    return when {
      difference <= 2 -> 1.0
      difference <= 5 -> 0.8
      difference <= 10 -> 0.5
      difference <= 30 -> 0.2
      else -> 0.0
    }
  }

  private fun preferredImage(images: List<SpotifyImage>): SpotifyImage? =
    images.firstOrNull { it.width != null && it.width in 200..400 } ?: images.firstOrNull()
}
