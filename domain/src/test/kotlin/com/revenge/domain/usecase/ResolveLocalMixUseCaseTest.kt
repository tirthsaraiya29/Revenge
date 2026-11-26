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
    private val songs: Map<String, Song> = emptyMap(),
  ) : SongRepository {
    override fun getSong(id: String): Flow<Song?> = flowOf(songs[id])

    override fun getLikedSongs(): Flow<List<Song>> =
      flowOf(songs.values.filter { it.liked })

    override fun getRecentlyPlayed(limit: Int): Flow<List<Song>> =
      flowOf(songs.values.take(limit))

    override suspend fun updateLiked(id: String, isLiked: Boolean) = Unit

    override suspend fun incrementPlayTime(id: String, durationMs: Long) = Unit
  }

  private fun song(
    id: String,
    artists: List<ArtistRef>,
    liked: Boolean = false,
    playMinutes: Double = 0.0,
  ): Song =
    Song(
      id = id,
      title = "Song $id",
      artists = artists,
      album = null,
      durationSeconds = 200,
      thumbnailUrl = null,
      liked = liked,
      totalPlayTimeMs = (playMinutes * 60_000).toLong(),
    )

  @Test
  fun prioritizesSharedArtistAndExcludesSeed() = runTest {
    val radiohead = ArtistRef("artist-radiohead", "Radiohead")
    val daftPunk = ArtistRef("artist-daftpunk", "Daft Punk")

    val seed = song("seed", listOf(radiohead), liked = true)
    val related = song("related", listOf(radiohead), liked = false, playMinutes = 0.0)
    val unrelated = song("unrelated", listOf(daftPunk), liked = true, playMinutes = 0.0)

    val repository =
      FakeSongRepository(
        linkedMapOf(seed.id to seed, unrelated.id to unrelated, related.id to related),
      )

    val mix = ResolveLocalMixUseCase(repository).invoke("seed", limit = 10)

    assertEquals(listOf("related", "unrelated"), mix.map { it.id })
  }

  @Test
  fun unknownSeedProducesEmptyMix() = runTest {
    val repository = FakeSongRepository(mapOf("other" to song("other", emptyList())))

    val mix = ResolveLocalMixUseCase(repository).invoke("missing")

    assertTrue(mix.isEmpty())
  }

  @Test
  fun matchesArtistByNormalizedNameWhenIdsDiffer() = runTest {
    val seed = song("seed", listOf(ArtistRef("seed-id", "  The Beatles ")))
    val sameNameDifferentId = song("name-match", listOf(ArtistRef("other-id", "the beatles")))
    val unrelatedLiked = song("liked", listOf(ArtistRef("x", "Someone Else")), liked = true)

    val repository =
      FakeSongRepository(
        linkedMapOf(seed.id to seed, unrelatedLiked.id to unrelatedLiked, sameNameDifferentId.id to sameNameDifferentId),
      )

    val mix = ResolveLocalMixUseCase(repository).invoke("seed", limit = 10)

    assertEquals(listOf("name-match", "liked"), mix.map { it.id })
  }

  @Test
  fun playTimeBreaksTiesWhenArtistsDoNotMatch() = runTest {
    val seed = song("seed", listOf(ArtistRef("seed-artist", "Seed")))
    val liked = song("liked", emptyList(), liked = true, playMinutes = 0.0)
    val heavilyPlayed = song("heavy", emptyList(), playMinutes = 90.0)

    val repository =
      FakeSongRepository(
        linkedMapOf(seed.id to seed, liked.id to liked, heavilyPlayed.id to heavilyPlayed),
      )

    val mix = ResolveLocalMixUseCase(repository).invoke("seed", limit = 10)

    // Heavily played songs cap out at 60 points, ahead of a liked song's 30.
    assertEquals(listOf("heavy", "liked"), mix.map { it.id })
  }

  @Test
  fun respectsLimit() = runTest {
    val seed = song("seed", emptyList())
    val songs = linkedMapOf(seed.id to seed)
    (1..6).forEach { songs["s$it"] = song("s$it", emptyList()) }

    val mix = ResolveLocalMixUseCase(FakeSongRepository(songs)).invoke("seed", limit = 4)

    assertEquals(4, mix.size)
  }

  @Test
  fun nonPositiveLimitProducesEmptyMix() = runTest {
    val repository = FakeSongRepository(mapOf("seed" to song("seed", emptyList())))

    assertTrue(ResolveLocalMixUseCase(repository).invoke("seed", limit = 0).isEmpty())
    assertTrue(ResolveLocalMixUseCase(repository).invoke("seed", limit = -2).isEmpty())
  }
}
