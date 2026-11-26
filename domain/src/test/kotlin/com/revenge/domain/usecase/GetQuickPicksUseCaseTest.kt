package com.revenge.domain.usecase

import com.revenge.domain.models.ArtistRef
import com.revenge.domain.models.Song
import com.revenge.domain.repositories.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetQuickPicksUseCaseTest {

    private class FakeSongRepository(
        private val likedSongs: List<Song> = emptyList(),
        private val recentSongs: List<Song> = emptyList(),
        val recentLimits: MutableList<Int> = mutableListOf(),
    ) : SongRepository {
        override fun getSong(id: String): Flow<Song?> = flowOf(null)

        override fun getLikedSongs(): Flow<List<Song>> = flowOf(likedSongs)

        override fun getRecentlyPlayed(limit: Int): Flow<List<Song>> {
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

    private suspend fun idsOf(useCase: GetQuickPicksUseCase, limit: Int): List<String> =
        useCase(limit).first().map { it.id }

    @Test
    fun `B1 ranking puts heavy playtime first then liked then rare`() = runTest {
        val liked = listOf(song("liked", liked = true, playMinutes = 2.0))
        val recent = listOf(song("rarely", playMinutes = 0.5), song("heavy", playMinutes = 45.0))
        val useCase = GetQuickPicksUseCase(FakeSongRepository(likedSongs = liked, recentSongs = recent))

        assertEquals(listOf("heavy", "liked", "rarely"), idsOf(useCase, 3))
    }

    @Test
    fun `B2 shared songs are de-duplicated`() = runTest {
        val shared = song("shared")
        val useCase = GetQuickPicksUseCase(
            FakeSongRepository(likedSongs = listOf(shared), recentSongs = listOf(shared, song("extra"))),
        )

        val result = useCase(10).first()
        assertEquals(2, result.size)
        assertEquals(1, result.count { it.id == "shared" })
    }

    @Test
    fun `B3 history window is twice the requested limit`() = runTest {
        val repo = FakeSongRepository()
        val useCase = GetQuickPicksUseCase(repo)

        useCase(4).first()

        assertEquals(listOf(8), repo.recentLimits)
    }

    @Test
    fun `B4 limit caps the result size`() = runTest {
        val recent = (1..10).map { song("s$it", playMinutes = it.toDouble()) }
        val useCase = GetQuickPicksUseCase(FakeSongRepository(recentSongs = recent))

        assertEquals(3, useCase(3).first().size)
    }

    @Test
    fun `B5 play time is capped and ties keep insertion order`() = runTest {
        val recent = listOf(song("one-hour", playMinutes = 60.0), song("ten-hours", playMinutes = 600.0))
        val useCase = GetQuickPicksUseCase(FakeSongRepository(recentSongs = recent))

        assertEquals(listOf("one-hour", "ten-hours"), idsOf(useCase, 2))
    }

    @Test
    fun `B6 empty sources yield an empty list`() = runTest {
        val useCase = GetQuickPicksUseCase(FakeSongRepository())

        assertTrue(useCase(5).first().isEmpty())
    }

    @Test
    fun `B7 non-positive limits yield an empty list`() = runTest {
        val recent = listOf(song("a", playMinutes = 10.0))
        val useCase = GetQuickPicksUseCase(FakeSongRepository(recentSongs = recent))

        assertTrue(useCase(0).first().isEmpty())
        assertTrue(useCase(-3).first().isEmpty())
    }

    @Test
    fun `B8 liked-source membership drives the bonus over the entity field`() = runTest {
        // "shared" is in the liked source but its recent copy has liked == false.
        val shared = song("shared", liked = false, playMinutes = 0.0)
        val longer = song("longer", liked = false, playMinutes = 20.0)
        val useCase = GetQuickPicksUseCase(
            FakeSongRepository(likedSongs = listOf(shared), recentSongs = listOf(shared, longer)),
        )

        assertEquals(listOf("shared", "longer"), idsOf(useCase, 2))
    }

    @Test
    fun `B9 large limit does not overflow and is bounded by candidates`() = runTest {
        val recent = (1..3).map { song("s$it") }
        val useCase = GetQuickPicksUseCase(FakeSongRepository(recentSongs = recent))

        val result = useCase(Int.MAX_VALUE).first()
        assertEquals(3, result.size)
    }
}
