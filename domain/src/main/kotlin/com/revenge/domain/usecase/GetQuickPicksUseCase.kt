package com.revenge.domain.usecase

import com.revenge.domain.models.Song
import com.revenge.domain.repositories.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Builds the "quick picks" shelf from the liked library and the recent history.
 *
 * Both sources are folded into one candidate list, then each candidate is scored and placed by an
 * explicit (score, arrival) comparator. For an id present twice the recent copy is kept; an equal
 * score leaves the earlier arrival in front, so recent songs precede liked-only songs.
 */
class GetQuickPicksUseCase(
    private val songRepository: SongRepository,
) {

    operator fun invoke(limit: Int = DEFAULT_LIMIT): Flow<List<Song>> {
        val requested = limit.coerceAtLeast(0)
        return combine(
            songRepository.getRecentlyPlayed(historyWindow(requested)),
            songRepository.getLikedSongs(),
        ) { history, favourites ->
            rank(history, favourites, requested)
        }
    }

    private fun rank(history: List<Song>, favourites: List<Song>, requested: Int): List<Song> {
        if (requested == 0) return emptyList()

        val favouriteIds = favourites.mapTo(HashSet<String>()) { it.id }
        return candidates(history, favourites)
            .mapIndexed { arrival, song -> Ranked(song, arrival, score(song, song.id in favouriteIds)) }
            .sortedWith(RANKING)
            .take(requested)
            .map(Ranked::song)
    }

    private fun candidates(history: List<Song>, favourites: List<Song>): List<Song> {
        val recentSongs = history.distinctBy(Song::id)
        val recentIds = recentSongs.mapTo(HashSet<String>()) { it.id }
        val likedOnly = favourites.distinctBy(Song::id).filterNot { it.id in recentIds }
        return recentSongs + likedOnly
    }

    private fun score(song: Song, inFavourites: Boolean): Double {
        val bonus = if (inFavourites) FAVOURITE_BONUS else 0.0
        val minutes = song.totalPlayTimeMs / MILLIS_PER_MINUTE.toDouble()
        return bonus + minutes.coerceAtMost(MAX_MINUTES) * MINUTE_WEIGHT
    }

    private data class Ranked(val song: Song, val arrival: Int, val score: Double)

    private companion object {
        const val DEFAULT_LIMIT = 20
        const val FAVOURITE_BONUS = 50.0
        const val MINUTE_WEIGHT = 2.0
        const val MAX_MINUTES = 60.0
        const val MILLIS_PER_MINUTE = 60_000L

        val RANKING: Comparator<Ranked> =
            compareByDescending<Ranked> { it.score }.thenBy { it.arrival }

        fun historyWindow(requested: Int): Int =
            if (requested > Int.MAX_VALUE / 2) Int.MAX_VALUE else requested * 2
    }
}
