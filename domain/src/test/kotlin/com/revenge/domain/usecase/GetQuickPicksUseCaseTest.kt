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
    private val liked: List<Song> = emptyList(),
    private val recent: List<Song> = emptyList(),
  ) : SongRepository {
    var requestedLimit: Int? = null
      private set

    override fun getSong(id: String): Flow<Song?> = flowOf(null)

    override fun getLikedSongs(): Flow<List<Song>> = flowOf(liked)

    override fun getRecentlyPlayed(limit: Int): Flow<List<Song>> {
      requestedLimit = limit
      return flowOf(recent.take(limit))
    }

    override suspend fun updateLiked(id: String, isLiked: Boolean) = Unit

    override suspend fun incrementPlayTime(id: String, durationMs: Long) = Unit
  }

  private fun song(
    id: String,
    liked: Boolean = false,
    playMinutes: Double = 0.0,
    artists: List<ArtistRef> = emptyList(),
  ): Song =
    Song(
      id = id,
      title = "Song $id",
      artists = artists,
      album = null,
      durationSeconds = 180,
      thumbnailUrl = null,
      liked = liked,
      totalPlayTimeMs = (playMinutes * 60_000).toLong(),
    )

  @Test
  fun ranksLikedAndFrequentlyPlayedSongsFirst() = runTest {
    val rarelyPlayed = song("rarely", playMinutes = 0.5)
    val heavilyPlayed = song("heavy", playMinutes = 45.0)
    val liked = song("liked", liked = true, playMinutes = 2.0)

    val repository = FakeSongRepository(liked = listOf(liked), recent = listOf(rarelyPlayed, heavilyPlayed))

    val result = GetQuickPicksUseCase(repository)(limit = 3).first()

    assertEquals(listOf("heavy", "liked", "rarely"), result.map { it.id })
  }

  @Test
  fun emitsSongAppearingInBothSourcesOnce() = runTest {
    val shared = song("shared", liked = true, playMinutes = 5.0)
    val extra = song("extra")

    val repository = FakeSongRepository(liked = listOf(shared), recent = listOf(shared, extra))

    val result = GetQuickPicksUseCase(repository)(limit = 10).first()

    assertEquals(2, result.size)
    assertEquals(1, result.count { it.id == "shared" })
  }

  @Test
  fun requestsTwiceTheLimitFromHistory() = runTest {
    val repository = FakeSongRepository(recent = (1..10).map { song("$it") })

    GetQuickPicksUseCase(repository)(limit = 4).first()

    assertEquals(8, repository.requestedLimit)
  }

  @Test
  fun respectsLimit() = runTest {
    val repository = FakeSongRepository(recent = (1..10).map { song("$it") })

    val result = GetQuickPicksUseCase(repository)(limit = 3).first()

    assertEquals(3, result.size)
  }

  @Test
  fun capsListeningBonusAtOneHourSoOrderFollowsStableInputOrder() = runTest {
    val oneHour = song("one-hour", playMinutes = 60.0)
    val tenHours = song("ten-hours", playMinutes = 600.0)

    // Both hit the 60-minute cap, so the earlier candidate keeps its position.
    val repository = FakeSongRepository(recent = listOf(oneHour, tenHours))

    val result = GetQuickPicksUseCase(repository)(limit = 2).first()

    assertEquals(listOf("one-hour", "ten-hours"), result.map { it.id })
  }

  @Test
  fun emptySourcesProduceEmptyResult() = runTest {
    val result = GetQuickPicksUseCase(FakeSongRepository())().first()

    assertTrue(result.isEmpty())
  }

  @Test
  fun nonPositiveLimitProducesEmptyResult() = runTest {
    val repository = FakeSongRepository(recent = listOf(song("1")))

    assertTrue(GetQuickPicksUseCase(repository)(limit = 0).first().isEmpty())
    assertTrue(GetQuickPicksUseCase(repository)(limit = -3).first().isEmpty())
  }
}
