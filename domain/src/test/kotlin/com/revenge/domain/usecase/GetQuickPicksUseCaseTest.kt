package com.revenge.domain.usecase

import com.revenge.domain.models.ArtistRef
import com.revenge.domain.models.Song
import com.revenge.domain.repositories.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetQuickPicksUseCaseTest {

  @Test
  fun `ranking places the heftiest history first, then liked, then light history`() = runTest {
    val repository =
      FakeSongRepository(
        liked = listOf(song("liked", liked = true, playMinutes = 2.0)),
        recent = listOf(song("rarely", playMinutes = 0.5), song("heavy", playMinutes = 45.0)),
      )

    val picks = GetQuickPicksUseCase(repository).invoke(limit = 3).first()

    assertEquals(listOf("heavy", "liked", "rarely"), picks.map(Song::id))
  }

  @Test
  fun `a song in both sources is emitted once`() = runTest {
    val repository =
      FakeSongRepository(
        liked = listOf(song("shared", liked = true)),
        recent = listOf(song("shared"), song("extra")),
      )

    val picks = GetQuickPicksUseCase(repository).invoke(limit = 10).first()

    assertEquals(2, picks.size)
    assertEquals(setOf("shared", "extra"), picks.map(Song::id).toSet())
    assertEquals(1, picks.count { it.id == "shared" })
  }

  @Test
  fun `history is requested at twice the limit`() = runTest {
    val repository =
      FakeSongRepository(
        recent = (1..20).map { song("r$it") },
        liked = emptyList(),
      )

    GetQuickPicksUseCase(repository).invoke(limit = 4).first()

    assertEquals(listOf(8), repository.recentLimits)
  }

  @Test
  fun `at most limit items are emitted`() = runTest {
    val repository =
      FakeSongRepository(
        recent = (1..10).map { song("r$it") },
      )

    val picks = GetQuickPicksUseCase(repository).invoke(limit = 3).first()

    assertEquals(3, picks.size)
  }

  @Test
  fun `play time saturates at one hour and equal scores keep arrival order`() = runTest {
    val repository =
      FakeSongRepository(
        recent =
          listOf(song("one-hour", playMinutes = 60.0), song("ten-hours", playMinutes = 600.0)),
      )

    val picks = GetQuickPicksUseCase(repository).invoke(limit = 2).first()

    assertEquals(listOf("one-hour", "ten-hours"), picks.map(Song::id))
  }

  @Test
  fun `empty sources yield an empty list`() = runTest {
    val picks = GetQuickPicksUseCase(FakeSongRepository()).invoke().first()

    assertTrue(picks.isEmpty())
  }

  @Test
  fun `zero and negative limits yield empty lists`() = runTest {
    val repository = FakeSongRepository(recent = listOf(song("r1")))

    assertTrue(GetQuickPicksUseCase(repository).invoke(limit = 0).first().isEmpty())
    assertTrue(GetQuickPicksUseCase(repository).invoke(limit = -3).first().isEmpty())
  }

  @Test
  fun `liked source membership scores the bonus even when the entity field is false`() = runTest {
    val repository =
      FakeSongRepository(
        liked = listOf(song("shared", liked = true)),
        recent = listOf(song("shared", playMinutes = 0.0), song("plain", playMinutes = 0.0)),
      )

    val picks = GetQuickPicksUseCase(repository).invoke(limit = 5).first()

    assertEquals("shared", picks.first().id)
    assertFalse(picks.first().liked)
  }

  @Test
  fun `an enormous limit saturates without throwing and returns the available candidates`() =
    runTest {
      val repository =
        FakeSongRepository(
          liked = listOf(song("c")),
          recent = listOf(song("a"), song("b")),
        )

      val picks = GetQuickPicksUseCase(repository).invoke(limit = Int.MAX_VALUE).first()

      assertEquals(setOf("a", "b", "c"), picks.map(Song::id).toSet())
      assertEquals(Int.MAX_VALUE, repository.recentLimits.single())
    }

  private fun song(
    id: String,
    liked: Boolean = false,
    playMinutes: Double = 0.0,
    artists: List<ArtistRef> = emptyList(),
  ): Song =
    Song(
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
    private val single: Song? = null,
    private val liked: List<Song> = emptyList(),
    private val recent: List<Song> = emptyList(),
  ) : SongRepository {
    val recentLimits = mutableListOf<Int>()

    override fun getSong(id: String): Flow<Song?> = flowOf(single)

    override fun getLikedSongs(): Flow<List<Song>> = flowOf(liked)

    override fun getRecentlyPlayed(limit: Int): Flow<List<Song>> {
      recentLimits += limit
      return flowOf(if (limit <= 0) emptyList() else recent.take(limit))
    }

    override suspend fun updateLiked(id: String, isLiked: Boolean) = Unit

    override suspend fun incrementPlayTime(id: String, durationMs: Long) = Unit
  }
}
