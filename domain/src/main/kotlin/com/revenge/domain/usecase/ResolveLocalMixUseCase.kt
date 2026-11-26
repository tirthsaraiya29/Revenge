package com.revenge.domain.usecase

import com.revenge.domain.models.Song
import com.revenge.domain.repositories.SongRepository
import kotlinx.coroutines.flow.first

/**
 * Expands a seed song into an endless-mix style queue.
 *
 * Candidates come from liked and recently played tracks. Tracks sharing an artist with the seed
 * rank first, followed by liked tracks and then heavily played ones. The seed itself is never
 * included in the result.
 */
class ResolveLocalMixUseCase(
  private val songRepository: SongRepository,
) {

  suspend fun invoke(seedSongId: String, limit: Int = 25): List<Song> {
    val requested = limit.coerceAtLeast(0)
    if (requested == 0) return emptyList()

    val seed = songRepository.getSong(seedSongId).first() ?: return emptyList()

    val seedArtistIds = seed.artists.map { it.id }.filter { it.isNotBlank() }.toSet()
    val seedArtistNames =
      seed.artists.map { it.name.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    val likedSongs = songRepository.getLikedSongs().first()
    val recentSongs = songRepository.getRecentlyPlayed(historyWindow(requested)).first()

    val candidates = LinkedHashMap<String, Song>()
    for (song in likedSongs) {
      if (song.id != seedSongId) candidates.putIfAbsent(song.id, song)
    }
    for (song in recentSongs) {
      if (song.id != seedSongId) candidates.putIfAbsent(song.id, song)
    }

    return candidates.values
      .sortedByDescending { song -> score(song, seedArtistIds, seedArtistNames) }
      .take(requested)
  }

  private fun score(
    song: Song,
    seedArtistIds: Set<String>,
    seedArtistNames: Set<String>,
  ): Double {
    val sharesArtist =
      song.artists.any { artist ->
        (artist.id.isNotBlank() && artist.id in seedArtistIds) ||
          artist.name.trim().lowercase() in seedArtistNames
      }
    val artistBonus = if (sharesArtist) ARTIST_BONUS else 0.0
    val likedBonus = if (song.liked) LIKED_BONUS else 0.0
    val minutesPlayed = (song.totalPlayTimeMs / MILLIS_PER_MINUTE).coerceAtMost(MAX_MINUTES)
    return artistBonus + likedBonus + minutesPlayed
  }

  private fun historyWindow(limit: Int): Int =
    if (limit > Int.MAX_VALUE / 3) Int.MAX_VALUE else limit * 3

  private companion object {
    const val ARTIST_BONUS = 100.0
    const val LIKED_BONUS = 30.0
    const val MAX_MINUTES = 60.0
    const val MILLIS_PER_MINUTE = 60_000.0
  }
}
