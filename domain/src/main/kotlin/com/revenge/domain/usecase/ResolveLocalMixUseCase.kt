package com.revenge.domain.usecase

import com.revenge.domain.models.Song
import com.revenge.domain.repositories.SongRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

/**
 * Expands a seed song into a local mix.
 *
 * A small [SeedTaste] value captures the seed's artist keys; every candidate is scored against it
 * and ordered by an explicit (score, arrival) comparator. Liked-source songs are merged first, so
 * an equal score keeps them ahead of recent-only songs.
 */
class ResolveLocalMixUseCase(
  private val songRepository: SongRepository,
) {

  suspend fun invoke(seedSongId: String, limit: Int = DEFAULT_LIMIT): List<Song> {
    val requested = limit.coerceAtLeast(0)
    if (requested == 0) return emptyList()

    val seed = songRepository.getSong(seedSongId).firstOrNull() ?: return emptyList()
    val taste = SeedTaste.from(seed)

    val liked = songRepository.getLikedSongs().first()
    val recent = songRepository.getRecentlyPlayed(historyWindow(requested)).first()

    return candidates(liked, recent, seedSongId)
      .mapIndexed { arrival, song -> Ranked(song, arrival, taste.score(song)) }
      .sortedWith(RANKING)
      .take(requested)
      .map(Ranked::song)
  }

  private fun candidates(liked: List<Song>, recent: List<Song>, seedId: String): List<Song> {
    val likedSongs = liked.distinctBy(Song::id).filterNot { it.id == seedId }
    val likedIds = likedSongs.mapTo(HashSet<String>()) { it.id }
    val recentOnly = recent.distinctBy(Song::id).filterNot { it.id == seedId || it.id in likedIds }
    return likedSongs + recentOnly
  }

  private class SeedTaste(
    private val artistIds: Set<String>,
    private val artistNames: Set<String>,
  ) {
    fun score(candidate: Song): Double {
      val artist = if (sharesArtist(candidate)) ARTIST_BONUS else 0.0
      val liked = if (candidate.liked) LIKED_BONUS else 0.0
      val minutes = candidate.totalPlayTimeMs / MILLIS_PER_MINUTE.toDouble()
      return artist + liked + minutes.coerceAtMost(MAX_MINUTES) * MINUTE_WEIGHT
    }

    private fun sharesArtist(candidate: Song): Boolean =
      candidate.artists.any { artist ->
        (artist.id.isNotBlank() && artist.id in artistIds) ||
          artist.name.trim().lowercase() in artistNames
      }

    companion object {
      fun from(seed: Song): SeedTaste =
        SeedTaste(
          artistIds = seed.artists.asSequence().map { it.id }.filter { it.isNotBlank() }.toSet(),
          artistNames =
            seed.artists
              .asSequence()
              .map { it.name.trim().lowercase() }
              .filter { it.isNotEmpty() }
              .toSet(),
        )
    }
  }

  private data class Ranked(val song: Song, val arrival: Int, val score: Double)

  private companion object {
    const val DEFAULT_LIMIT = 25
    const val ARTIST_BONUS = 100.0
    const val LIKED_BONUS = 30.0
    const val MINUTE_WEIGHT = 1.0
    const val MAX_MINUTES = 60.0
    const val MILLIS_PER_MINUTE = 60_000L

    val RANKING: Comparator<Ranked> = compareByDescending<Ranked> { it.score }.thenBy { it.arrival }

    fun historyWindow(requested: Int): Int =
      if (requested > Int.MAX_VALUE / 3) Int.MAX_VALUE else requested * 3
  }
}
