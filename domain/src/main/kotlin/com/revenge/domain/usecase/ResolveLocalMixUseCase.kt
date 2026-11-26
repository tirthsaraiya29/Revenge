package com.revenge.domain.usecase

import com.revenge.domain.models.Song
import com.revenge.domain.repositories.SongRepository
import kotlinx.coroutines.flow.first
import kotlin.math.max
import kotlin.math.min

class ResolveLocalMixUseCase(private val songRepository: SongRepository) {

    suspend fun invoke(seedSongId: String, limit: Int = 25): List<Song> {
        val requested = max(limit, 0)
        if (requested == 0) return emptyList()

        val seed = songRepository.getSong(seedSongId).first() ?: return emptyList()

        val seedArtistIds = seed.artists
            .map { it.id }
            .filter { it.isNotBlank() }
            .toSet()
        val seedArtistNames = seed.artists
            .map { it.name.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        val liked = songRepository.getLikedSongs().first()
        val window =
            if (requested > Int.MAX_VALUE / 3) Int.MAX_VALUE else requested * 3
        val recent = songRepository.getRecentlyPlayed(window).first()

        val candidates = LinkedHashMap<String, Song>()
        for (song in liked) {
            if (song.id != seedSongId && !candidates.containsKey(song.id)) {
                candidates[song.id] = song
            }
        }
        for (song in recent) {
            if (song.id != seedSongId && !candidates.containsKey(song.id)) {
                candidates[song.id] = song
            }
        }

        return candidates.values
            .sortedByDescending { score(it, seedArtistIds, seedArtistNames) }
            .take(requested)
    }

    private fun score(
        song: Song,
        seedArtistIds: Set<String>,
        seedArtistNames: Set<String>,
    ): Double {
        val sharesArtist = song.artists.any { artist ->
            (artist.id.isNotBlank() && artist.id in seedArtistIds) ||
                artist.name.trim().lowercase() in seedArtistNames
        }
        val artistBonus = if (sharesArtist) 100.0 else 0.0
        val likedBonus = if (song.liked) 30.0 else 0.0
        val minutes = min(song.totalPlayTimeMs / 60_000.0, 60.0)
        return artistBonus + likedBonus + minutes
    }
}
