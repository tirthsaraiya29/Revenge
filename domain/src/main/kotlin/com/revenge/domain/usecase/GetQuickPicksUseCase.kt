package com.revenge.domain.usecase

import com.revenge.domain.models.Song
import com.revenge.domain.repositories.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.math.max
import kotlin.math.min

class GetQuickPicksUseCase(private val songRepository: SongRepository) {

    operator fun invoke(limit: Int = 20): Flow<List<Song>> {
        val requested = max(limit, 0)
        val historyWindow =
            if (requested > Int.MAX_VALUE / 2) Int.MAX_VALUE else requested * 2

        return combine(
            songRepository.getLikedSongs(),
            songRepository.getRecentlyPlayed(historyWindow),
        ) { liked, recent ->
            val likedIds = liked.mapTo(HashSet()) { it.id }
            val candidates = LinkedHashMap<String, Song>()
            for (song in recent) {
                if (!candidates.containsKey(song.id)) candidates[song.id] = song
            }
            for (song in liked) {
                if (!candidates.containsKey(song.id)) candidates[song.id] = song
            }
            candidates.values
                .sortedByDescending { score(it, likedIds) }
                .take(requested)
        }
    }

    private fun score(song: Song, likedIds: Set<String>): Double {
        val likedBonus = if (song.id in likedIds) 50.0 else 0.0
        val minutes = min(song.totalPlayTimeMs / 60_000.0, 60.0)
        return likedBonus + minutes * 2.0
    }
}
