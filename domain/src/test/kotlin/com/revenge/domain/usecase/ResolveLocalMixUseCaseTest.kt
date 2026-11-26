package com.revenge.domain.usecase

import com.revenge.domain.models.ArtistRef
import com.revenge.domain.models.Song
import com.revenge.domain.repositories.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveLocalMixUseCaseTest {

    @Test
    fun `a shared artist outranks a liked song and the seed is excluded`() = runTest {
        val seed = song("seed", artists = listOf(ArtistRef("radiohead-id", "Radiohead")))
        val repository = FakeSongRepository(
            seed = seed,
            liked = listOf(
                song("related", artists = listOf(ArtistRef("radiohead-id", "Radiohead"))),
                song("unrelated", liked = true, artists = listOf(ArtistRef("daft-punk-id", "Daft Punk"))),
            ),
        )

        val mix = ResolveLocalMixUseCase(repository).invoke("seed")

        assertEquals(listOf("related", "unrelated"), mix.map(Song::id))
        assertTrue(mix.none { it.id == "seed" })
    }

    @Test
    fun `an unknown seed yields an empty list`() = runTest {
        val repository = FakeSongRepository(
            seed = null,
            liked = listOf(song("liked", liked = true)),
        )

        assertTrue(ResolveLocalMixUseCase(repository).invoke("missing").isEmpty())
    }

    @Test
    fun `artist names match after trimming and lowercasing`() = runTest {
        val seed = song("seed", artists = listOf(ArtistRef("seed-id", "  The Beatles ")))
        val repository = FakeSongRepository(
            seed = seed,
            liked = listOf(
                song("name-match", artists = listOf(ArtistRef("other-id", "the beatles"))),
                song("liked", liked = true),
            ),
        )

        val mix = ResolveLocalMixUseCase(repository).invoke("seed", limit = 10)

        assertEquals(listOf("name-match", "liked"), mix.map(Song::id))
    }

    @Test
    fun `play time beats a liked song when no artist matches`() = runTest {
        val seed = song("seed", artists = listOf(ArtistRef("seed-artist-id", "Seed")))
        val repository = FakeSongRepository(
            seed = seed,
            liked = listOf(
                song("liked", liked = true),
                song("heavy", playMinutes = 90.0),
            ),
        )

        val mix = ResolveLocalMixUseCase(repository).invoke("seed")

        assertEquals(listOf("heavy", "liked"), mix.map(Song::id))
    }

    @Test
    fun `the limit is respected and the seed stays out`() = runTest {
        val repository = FakeSongRepository(
            seed = song("seed"),
            liked = (1..6).map { song("s$it") },
        )

        val mix = ResolveLocalMixUseCase(repository).invoke("seed", limit = 4)

        assertEquals(4, mix.size)
        assertTrue(mix.none { it.id == "seed" })
    }

    @Test
    fun `zero and negative limits return empty without consulting the repository`() = runTest {
        val repository = FakeSongRepository(
            seed = song("seed"),
            liked = listOf(song("liked", liked = true)),
        )

        assertTrue(ResolveLocalMixUseCase(repository).invoke("seed", limit = 0).isEmpty())
        assertTrue(ResolveLocalMixUseCase(repository).invoke("seed", limit = -2).isEmpty())
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun `history is requested at three times the limit`() = runTest {
        val repository = FakeSongRepository(
            seed = song("seed"),
            recent = listOf(song("r1")),
        )

        ResolveLocalMixUseCase(repository).invoke("seed", limit = 4)

        assertEquals(listOf(12), repository.recentLimits)
    }

    @Test
    fun `a song liked and recently played is kept once with the liked copy`() = runTest {
        val repository = FakeSongRepository(
            seed = song("seed"),
            liked = listOf(song("shared", liked = true)),
            recent = listOf(song("shared", playMinutes = 5.0), song("recent-only")),
        )

        val mix = ResolveLocalMixUseCase(repository).invoke("seed")

        assertEquals(2, mix.size)
        assertEquals(1, mix.count { it.id == "shared" })
        assertTrue(mix.single { it.id == "shared" }.liked)
        assertEquals(listOf("shared", "recent-only"), mix.map(Song::id))
    }

    @Test
    fun `a seed present in both sources never appears in the mix`() = runTest {
        val seed = song("seed")
        val repository = FakeSongRepository(
            seed = seed,
            liked = listOf(seed, song("other")),
            recent = listOf(seed),
        )

        val mix = ResolveLocalMixUseCase(repository).invoke("seed")

        assertEquals(listOf("other"), mix.map(Song::id))
    }

    @Test
    fun `blank artist ids never match by id`() = runTest {
        val seed = song("seed", artists = listOf(ArtistRef("", "unique")))
        val repository = FakeSongRepository(
            seed = seed,
            liked = listOf(
                song("blank-id-other", artists = listOf(ArtistRef("", "other"))),
                song("liked", liked = true),
            ),
        )

        val mix = ResolveLocalMixUseCase(repository).invoke("seed")

        assertEquals(listOf("liked", "blank-id-other"), mix.map(Song::id))
    }

    @Test
    fun `equal scores keep liked-source order before recent-only songs`() = runTest {
        val repository = FakeSongRepository(
            seed = song("seed"),
            liked = listOf(song("liked-a"), song("liked-b")),
            recent = listOf(song("recent-a")),
        )

        val mix = ResolveLocalMixUseCase(repository).invoke("seed")

        assertEquals(listOf("liked-a", "liked-b", "recent-a"), mix.map(Song::id))
    }

    private fun song(
        id: String,
        liked: Boolean = false,
        playMinutes: Double = 0.0,
        artists: List<ArtistRef> = emptyList(),
    ): Song = Song(
        id = id,
        title = id,
        artists = artists,
        album = null,
        durationSeconds = 0,
        thumbnailUrl = null,
        liked = liked,
        totalPlayTimeMs = (playMinutes * 60_000).toLong(),
    )

    private class FakeSongRepository(
        private val seed: Song? = null,
        private val liked: List<Song> = emptyList(),
        private val recent: List<Song> = emptyList(),
    ) : SongRepository {
        val recentLimits = mutableListOf<Int>()
        val calls = mutableListOf<String>()

        override fun getSong(id: String): Flow<Song?> {
            calls += "getSong"
            return flowOf(seed)
        }

        override fun getLikedSongs(): Flow<List<Song>> {
            calls += "getLikedSongs"
            return flowOf(liked)
        }

        override fun getRecentlyPlayed(limit: Int): Flow<List<Song>> {
            calls += "getRecentlyPlayed"
            recentLimits += limit
            return flowOf(if (limit <= 0) emptyList() else recent.take(limit))
        }

        override suspend fun updateLiked(id: String, isLiked: Boolean) = Unit

        override suspend fun incrementPlayTime(id: String, durationMs: Long) = Unit
    }
}
