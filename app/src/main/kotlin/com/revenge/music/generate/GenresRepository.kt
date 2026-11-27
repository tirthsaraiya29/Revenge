package com.revenge.music.generate

import com.revenge.core.db.MusicDatabase
import com.revenge.music.utils.lastfm.LastFmTasteApi
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
open class GenresRepository
@Inject
constructor(
  private val database: MusicDatabase,
  private val lastFmTasteApi: LastFmTasteApi,
  private val tasteProfileProvider: TasteProfileProvider,
) {
  private data class CacheEntry(val genres: List<String>, val timestamp: Long)

  private val cache = ConcurrentHashMap<String, CacheEntry>()

  suspend fun genresForTrack(trackKey: String): List<String> {
    val now = System.currentTimeMillis()
    cache[trackKey]
      ?.takeIf { now - it.timestamp < GENRE_CACHE_TTL_MILLIS }
      ?.let {
        return it.genres
      }
    val artist = trackKey.split('|').getOrNull(1)?.trim().orEmpty()
    if (artist.isBlank()) return emptyList()
    val result = lastFmTasteApi.getTopTags(artist)
    if (result.isFailure) return emptyList()
    val genres =
      result.getOrThrow().map { it.name.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
    cache[trackKey] = CacheEntry(genres, now)
    return genres
  }

  suspend fun topGenres(limit: Int = 10): List<String> {
    val weights = HashMap<String, Double>()
    val profile = tasteProfileProvider.get()
    if (profile != null) {
      val count = profile.topGenres.size
      profile.topGenres.forEachIndexed { index, genre ->
        weights.merge(genre.lowercase(), 1000.0 * (count - index), Double::plus)
      }
    }

    val stats = runCatching {
      database.songPlayStatsDao.mostPlayedSince(0L, 15)
    }
      .getOrDefault(emptyList())

    val perTrack = coroutineScope {
      stats
        .map { stat ->
          async {
            val genres =
              withTimeoutOrNull(2_000) {
                  genresForTrack(stat.trackKey)
                }
                .orEmpty()
            stat.totalPlayTimeMs.coerceAtLeast(10L) to genres
          }
        }
        .awaitAll()
    }
    perTrack.forEach { (weight, genres) ->
      genres.forEach { genre -> weights.merge(genre, weight.toDouble(), Double::plus) }
    }
    return weights.entries.sortedByDescending { it.value }.take(limit).map { it.key }
  }

  fun clearCache() {
    cache.clear()
  }

  companion object {
    const val GENRE_CACHE_TTL_MILLIS = 3_600_000L
  }
}
