package com.revenge.music.spotify

import com.revenge.innertube.YouTube
import com.revenge.innertube.YouTube.SearchFilter
import com.revenge.innertube.models.SongItem
import com.revenge.music.spotify.models.SpotifyTrack
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber

/**
 * Resolves Spotify tracks to YouTube search results. The service is process-wide, caches per
 * Spotify id, deduplicates concurrent lookups and never throws to callers.
 */
@Singleton
open class SpotifyPlaybackResolver @Inject constructor() {

  private companion object {
    const val MAX_CONCURRENCY = 6
    const val MAX_CACHE = 2_000
    const val MIN_TITLE_SIMILARITY = 0.25
    const val EARLY_EXIT = 0.85
    const val QUERY_EXIT = 0.55
    const val FINAL_THRESHOLD = 0.35
  }

  private val cache = ConcurrentHashMap<String, SongItem>()
  private val inFlight = ConcurrentHashMap<String, CompletableDeferred<SongItem?>>()
  private val semaphore = Semaphore(MAX_CONCURRENCY)

  fun clearCache() {
    cache.clear()
  }

  open fun getCached(trackId: String): SongItem? = cache[trackId]

  open suspend fun resolveTrack(track: SpotifyTrack, hideExplicit: Boolean = false): SongItem? {
    val id = track.id
    if (id.isNotBlank())
      cache[id]?.let {
        return it
      }
    if (id.isNotBlank()) {
      val mine = CompletableDeferred<SongItem?>()
      val running = inFlight.putIfAbsent(id, mine)
      if (running != null) return running.await()
      return runResolution(track, hideExplicit, id, mine)
    }
    return performResolution(track, hideExplicit)
  }

  private suspend fun runResolution(
    track: SpotifyTrack,
    hideExplicit: Boolean,
    id: String,
    mine: CompletableDeferred<SongItem?>,
  ): SongItem? {
    try {
      val result = performResolution(track, hideExplicit)
      if (result != null) {
        if (cache.size > MAX_CACHE) cache.clear()
        cache[id] = result
      }
      mine.complete(result)
      return result
    } catch (e: CancellationException) {
      mine.cancel(e)
      throw e
    } catch (e: Throwable) {
      Timber.tag("SpotifyResolver").w(e, "Resolution failed for $id")
      mine.complete(null)
      return null
    } finally {
      inFlight.remove(id, mine)
    }
  }

  private suspend fun performResolution(track: SpotifyTrack, hideExplicit: Boolean): SongItem? =
    try {
      semaphore.withPermit {
        val queries = buildQueries(track)
        var best: SongItem? = null
        var bestScore = -1.0
        for (query in queries) {
          val candidates = search(query, track, hideExplicit)
          for (candidate in candidates) {
            val score =
              SpotifyMapper.matchScore(
                spotifyTitle = track.name,
                spotifyArtist = track.artists.firstOrNull()?.name.orEmpty(),
                spotifyDurationMs = track.durationMs,
                candidateTitle = candidate.title,
                candidateArtist = candidate.artists.joinToString(", ") { it.name },
                candidateDurationSec = candidate.duration,
              )
            if (score > bestScore) {
              best = candidate
              bestScore = score
            }
            if (score >= EARLY_EXIT) return@withPermit candidate
          }
          if (bestScore >= QUERY_EXIT) break
        }
        if (bestScore >= FINAL_THRESHOLD) best else null
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      Timber.tag("SpotifyResolver").w(e, "Resolution failed for ${track.id}")
      null
    }

  private suspend fun search(
    query: String,
    track: SpotifyTrack,
    hideExplicit: Boolean,
  ): List<SongItem> {
    val anonymous =
      YouTube.search(query, SearchFilter.FILTER_SONG, setLogin = false)
        .getOrNull()
        ?.items
        ?.filterIsInstance<SongItem>()
        .orEmpty()
    val items =
      if (anonymous.isEmpty()) {
        YouTube.search(query, SearchFilter.FILTER_SONG)
          .getOrNull()
          ?.items
          ?.filterIsInstance<SongItem>()
          .orEmpty()
      } else {
        anonymous
      }
    return items
      .filter { !hideExplicit || !it.explicit }
      .filter { SpotifyMapper.titleSimilarity(track.name, it.title) >= MIN_TITLE_SIMILARITY }
  }

  private fun buildQueries(track: SpotifyTrack): List<String> {
    val artist = track.artists.firstOrNull()?.name?.trim().orEmpty()
    val title = track.name.trim()
    return listOf(
        if (artist.isBlank()) title else "$artist $title",
        if (artist.isBlank()) title else "$title $artist",
        title,
      )
      .map(String::trim)
      .filter(String::isNotBlank)
      .distinct()
  }

  open suspend fun resolveTracks(
    tracks: List<SpotifyTrack>,
    hideExplicit: Boolean = false,
  ): Map<String, SongItem> {
    if (tracks.isEmpty()) return emptyMap()
    val unique = tracks.associateBy { it.id }.filterKeys { it.isNotBlank() }
    return coroutineScope {
      unique.values
        .map { track -> async { track.id to resolveTrack(track, hideExplicit) } }
        .awaitAll()
        .mapNotNull { (id, item) -> item?.let { id to it } }
        .toMap()
    }
  }

  open suspend fun resolveAndFilter(
    tracks: List<SpotifyTrack>,
    hideExplicit: Boolean,
  ): List<SongItem> {
    if (tracks.isEmpty()) return emptyList()
    return coroutineScope {
      val resolved = tracks.map { track -> async { resolveTrack(track, hideExplicit) } }.awaitAll()
      tracks.indices.mapNotNull { index ->
        val item = resolved[index] ?: return@mapNotNull null
        if (hideExplicit && item.explicit) null else item
      }
    }
  }

  open fun findBestMatch(
    candidates: List<SongItem>,
    track: SpotifyTrack,
    hideExplicit: Boolean = false,
  ): SongItem? {
    val allowed = candidates.filterNot { hideExplicit && it.explicit }
    if (allowed.isEmpty()) return null

    val sourceTitle = SpotifyMapper.normalize(track.name)
    val sourceArtist = SpotifyMapper.normalize(track.artists.joinToString(" ") { it.name })
    val sourceSeconds = track.durationMs / 1000

    var best: SongItem? = null
    var bestScore = Int.MIN_VALUE
    for (candidate in allowed) {
      val candidateTitle = SpotifyMapper.normalize(candidate.title)
      var score = 0
      if (candidateTitle == sourceTitle) score += 30
      else if (
        sourceTitle.isNotEmpty() &&
          (candidateTitle.contains(sourceTitle) || sourceTitle.contains(candidateTitle))
      ) {
        score += 15
      }

      val candidateArtists = candidate.artists.map { SpotifyMapper.normalize(it.name) }
      if (
        candidateArtists.any { artist ->
          artist.isNotEmpty() &&
            (artist == sourceArtist ||
              sourceArtist.contains(artist) ||
              artist.contains(sourceArtist))
        }
      ) {
        score += 25
      }

      val candidateSeconds = candidate.duration
      if (candidateSeconds != null && sourceSeconds > 0) {
        val difference = kotlin.math.abs(candidateSeconds - sourceSeconds)
        score +=
          when {
            difference <= 3 -> 20
            difference <= 8 -> 12
            difference <= 20 -> 5
            difference > 60 -> -25
            else -> 0
          }
      }

      listOf("karaoke", "instrumental", "cover", "tribute", "parody").forEach { keyword ->
        if (candidateTitle.contains(keyword) && !sourceTitle.contains(keyword)) score -= 30
      }
      val candidateLive = candidateTitle.contains("live")
      val sourceLive = sourceTitle.contains("live")
      if (candidateLive != sourceLive) score -= 15

      if (score > bestScore) {
        bestScore = score
        best = candidate
      }
    }
    return if (bestScore > 0) best else allowed.first()
  }
}
