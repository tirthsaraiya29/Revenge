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

    private class FakeSongRepository(
        private val seed: Song? = null,
        private val likedSongs: List<Song> = emptyList(),
        private val recentSongs: List<Song> = emptyList(),
        val recentLimits: MutableList<Int> = mutableListOf(),
        val calls: MutableList<String> = mutableListOf(),
    ) : SongRepository {
        override fun getSong(id: String): Flow<Song?> {
            calls += "getSong"
            return flowOf(seed)
        }

        override fun getLikedSongs(): Flow<List<Song>> {
            calls += "getLikedSongs"
            return flowOf(likedSongs)
        }

        override fun getRecentlyPlayed(limit: Int): Flow<List<Song>> {
            calls += "getRecentlyPlayed"
            recentLimits += limit
            return flowOf(recentSongs.take(limit))
        }

        override suspend fun updateLiked(id: String, isLiked: Boolean) = Unit

        override suspend fun incrementPlayTime(id: String, durationMs: Long) = Unit
    }

    private fun song(
        id: String,
        liked: Boolean = false,
        playMinutes: Double = 0.0,
        artists: List<ArtistRef> = emptyList(),
    ) = Song(
        id = id,
        title = id,
        artists = artists,
        album = null,
        durationSeconds = 0,
        thumbnailUrl = null,
        liked = liked,
        totalPlayTimeMs = (playMinutes * 60_000).toLong(),
    )

    private fun artist(id: String, name: String) = ArtistRef(id, name)

    @Test
    fun `C1 shared artist ranks first and seed is excluded`() = runTest {
        val seed = song("seed", artists = listOf(artist("r1", "Radiohead")))
        val related = song("related", artists = listOf(artist("r2", "Radiohead")))
        val unrelated = song("unrelated", liked = true, artists = listOf(artist("d1", "Daft Punk")))
        val useCase = ResolveLocalMixUseCase(
            FakeSongRepository(seed = seed, likedSongs = listOf(unrelated), recentSongs = listOf(related, seed)),
        )

        val result = useCase.invoke("seed", 10).map { it.id }

        assertEquals(listOf("related", "unrelated"), result)
    }

    @Test
    fun `C2 unknown seed yields an empty list`() = runTest {
        val useCase = ResolveLocalMixUseCase(FakeSongRepository(seed = null))

        assertTrue(useCase.invoke("missing", 10).isEmpty())
    }

    @Test
    fun `C3 artist names are matched after trimming and case folding`() = runTest {
        val seed = song("seed", artists = listOf(artist("seed-id", "  The Beatles ")))
        val nameMatch = song("name-match", artists = listOf(artist("other-id", "the beatles")))
        val liked = song("liked", liked = true, artists = listOf(artist("x", "Someone")))
        val useCase = ResolveLocalMixUseCase(
            FakeSongRepository(seed = seed, likedSongs = listOf(liked), recentSongs = listOf(nameMatch)),
        )

        assertEquals(listOf("name-match", "liked"), useCase.invoke("seed", 10).map { it.id })
    }

    @Test
    fun `C4 play time beats liked when no artist matches`() = runTest {
        val seed = song("seed", artists = listOf(artist("s", "Seed")))
        val liked = song("liked", liked = true, playMinutes = 0.0, artists = listOf(artist("l", "Other")))
        val heavy = song("heavy", liked = false, playMinutes = 90.0, artists = listOf(artist("h", "Other2")))
        val useCase = ResolveLocalMixUseCase(
            FakeSongRepository(seed = seed, likedSongs = listOf(liked), recentSongs = listOf(heavy)),
        )

        assertEquals(listOf("heavy", "liked"), useCase.invoke("seed", 2).map { it.id })
    }

    @Test
    fun `C5 limit is respected and seed is never included`() = runTest {
        val seed = song("seed", artists = listOf(artist("s", "Seed")))
        val others = (1..6).map { song("o$it") }
        val useCase = ResolveLocalMixUseCase(
            FakeSongRepository(seed = seed, recentSongs = others + seed),
        )

        val result = useCase.invoke("seed", 4).map { it.id }

        assertEquals(4, result.size)
        assertTrue(result.none { it == "seed" })
    }

    @Test
    fun `C6 non-positive limit returns empty without consulting repositories`() = runTest {
        val repo = FakeSongRepository(seed = song("seed"))
        val useCase = ResolveLocalMixUseCase(repo)

        assertTrue(useCase.invoke("seed", 0).isEmpty())
        assertTrue(useCase.invoke("seed", -2).isEmpty())
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `C7 history window is three times the requested limit`() = runTest {
        val repo = FakeSongRepository(seed = song("seed"))
        val useCase = ResolveLocalMixUseCase(repo)

        useCase.invoke("seed", 4)

        assertEquals(listOf(12), repo.recentLimits)
    }

    @Test
    fun `C8 liked and recent overlap keeps the liked entry`() = runTest {
        val seed = song("seed")
        val likedCopy = song("a", liked = true, playMinutes = 10.0)
        val recentCopy = song("a", liked = false, playMinutes = 0.0)
        val useCase = ResolveLocalMixUseCase(
            FakeSongRepository(seed = seed, likedSongs = listOf(likedCopy), recentSongs = listOf(recentCopy)),
        )

        val result = useCase.invoke("seed", 10)

        assertEquals(1, result.size)
        assertEquals(600_000L, result.single().totalPlayTimeMs)
    }

    @Test
    fun `C9 seed appearing in liked and recent is excluded`() = runTest {
        val seed = song("seed")
        val useCase = ResolveLocalMixUseCase(
            FakeSongRepository(
                seed = seed,
                likedSongs = listOf(seed, song("other")),
                recentSongs = listOf(seed),
            ),
        )

        val result = useCase.invoke("seed", 10).map { it.id }

        assertEquals(listOf("other"), result)
    }

    @Test
    fun `C10 blank seed artist ids never match by id`() = runTest {
        val seed = song("seed", artists = listOf(artist("", "X")))
        val noMatch = song("no-match", liked = true, artists = listOf(artist("", "Y")))
        val nameMatch = song("name-match", artists = listOf(artist("n", "X")))
        val useCase = ResolveLocalMixUseCase(
            FakeSongRepository(seed = seed, recentSongs = listOf(noMatch, nameMatch)),
        )

        assertEquals(listOf("name-match", "no-match"), useCase.invoke("seed", 10).map { it.id })
    }

    @Test
    fun `C11 equal scores keep liked order before recent-only songs`() = runTest {
        val seed = song("seed")
        val l1 = song("l1")
        val l2 = song("l2")
        val r1 = song("r1")
        val useCase = ResolveLocalMixUseCase(
            FakeSongRepository(seed = seed, likedSongs = listOf(l1, l2), recentSongs = listOf(r1)),
        )

        assertEquals(listOf("l1", "l2", "r1"), useCase.invoke("seed", 3).map { it.id })
    }
}
