package com.revenge.domain.usecase

import com.revenge.domain.models.Song
import com.revenge.domain.repositories.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Produces a "quick picks" shelf from liked songs and recent listening history.
 *
 * Liked songs get a flat bonus; every song then earns two points per minute played, capped at
 * one hour of listening time so a single heavily-replayed track cannot dominate forever.
 */
class GetQuickPicksUseCase(
  private val songRepository: SongRepository,
) {

  operator fun invoke(limit: Int = 20): Flow<List<Song>> {
    val requested = limit.coerceAtLeast(0)
    val historyWindow =
      if (requested > Int.MAX_VALUE / 2) Int.MAX_VALUE else requested * 2

    return combine(
      songRepository.getLikedSongs(),
      songRepository.getRecentlyPlayed(historyWindow),
    ) { likedSongs, recentSongs ->
      val likedIds = likedSongs.mapTo(HashSet()) { it.id }

      // Recent tracks seed the order; liked tracks missing from history are appended.
      val candidates = LinkedHashMap<String, Song>()
      for (song in recentSongs) candidates.putIfAbsent(song.id, song)
      for (song in likedSongs) candidates.putIfAbsent(song.id, song)

      candidates.values
        .sortedByDescending { song -> score(song, liked = song.id in likedIds) }
        .take(requested)
    }
  }

  private fun score(song: Song, liked: Boolean): Double {
    val likedBonus = if (liked) LIKED_BONUS else 0.0
    val minutesPlayed = (song.totalPlayTimeMs / MILLIS_PER_MINUTE).coerceAtMost(MAX_MINUTES)
    return likedBonus + minutesPlayed * POINTS_PER_MINUTE
  }

  private companion object {
    const val LIKED_BONUS = 50.0
    const val POINTS_PER_MINUTE = 2.0
    const val MAX_MINUTES = 60.0
    const val MILLIS_PER_MINUTE = 60_000.0
  }
}
