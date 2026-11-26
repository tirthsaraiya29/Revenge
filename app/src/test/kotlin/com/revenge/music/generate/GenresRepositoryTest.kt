package com.revenge.music.generate

import androidx.room.Room
import com.revenge.music.db.InternalDatabase
import com.revenge.music.db.MusicDatabase
import com.revenge.music.db.entities.SongPlayStatsEntity
import com.revenge.music.db.entities.TasteProfileEntity
import com.revenge.music.utils.lastfm.LastFmTasteApi
import com.revenge.music.utils.lastfm.SimilarArtist
import com.revenge.music.utils.lastfm.SimilarTrack
import com.revenge.music.utils.lastfm.Tag
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private class FakeGenresTasteApi : LastFmTasteApi {
  var topTagsCallCount = 0
  var shouldFail = false
  val tagsByArtist = mutableMapOf<String, List<Tag>>()

  override suspend fun getSimilarTracks(artist: String, track: String): Result<List<SimilarTrack>> =
    Result.success(emptyList())

  override suspend fun getSimilarArtists(artist: String): Result<List<SimilarArtist>> =
    Result.success(emptyList())

  override suspend fun getTopTags(artist: String): Result<List<Tag>> {
    topTagsCallCount++
    if (shouldFail) return Result.failure(RuntimeException("Network error"))
    return Result.success(
      tagsByArtist[artist.lowercase()] ?: listOf(Tag("rock", 100), Tag("alternative", 80)),
    )
  }

  override suspend fun getUserTopArtists(user: String, limit: Int): Result<List<String>> =
    Result.success(emptyList())

  override suspend fun getUserTopTracks(user: String, limit: Int): Result<List<SimilarTrack>> =
    Result.success(emptyList())

  override suspend fun getUserTopTags(user: String, limit: Int): Result<List<Tag>> =
    Result.success(emptyList())
}

@RunWith(RobolectricTestRunner::class)
class GenresRepositoryTest {

  private val context get() = RuntimeEnvironment.getApplication()
  private lateinit var internalDatabase: InternalDatabase
  private lateinit var database: MusicDatabase
  private lateinit var fakeApi: FakeGenresTasteApi
  private lateinit var repository: GenresRepository

  @Before
  fun setup() = runBlocking {
    internalDatabase =
      Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    database = MusicDatabase(internalDatabase)
    fakeApi = FakeGenresTasteApi()
    database.tasteProfileDao.upsert(
      TasteProfileEntity(
        id = 1,
        topArtistsJson = "[\"radiohead\"]",
        topTracksJson = "[\"creep|radiohead\"]",
        topGenresJson = "[\"rock\",\"indie\"]",
        confidence = 1.0f,
        updatedAtMillis = System.currentTimeMillis(),
      ),
    )
    val provider = TasteProfileProvider(database, fakeApi, context)
    repository = GenresRepository(database, fakeApi, provider)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun testGenresForTrackFetchesAndCachesWithinTTL() = runTest {
    fakeApi.tagsByArtist["radiohead"] = listOf(Tag("art rock", 100), Tag("experimental", 90))

    // 1. First fetch hits API
    val firstResult = repository.genresForTrack("creep|radiohead")
    assertEquals(1, fakeApi.topTagsCallCount)
    assertTrue(firstResult.contains("art rock"))
    assertTrue(firstResult.contains("experimental"))

    // 2. Second fetch within TTL hits memory cache without extra API call
    val secondResult = repository.genresForTrack("creep|radiohead")
    assertEquals(1, fakeApi.topTagsCallCount)
    assertEquals(firstResult, secondResult)
  }

  @Test
  fun testTopGenresAggregatesWeightsFromTasteProfileAndPlayStats() = runTest {
    database.songPlayStatsDao.upsert(
      SongPlayStatsEntity(
        trackKey = "karma police|radiohead",
        title = "Karma Police",
        artist = "Radiohead",
        totalPlayTimeMs = 120_000L,
        playCount = 10,
        skipCount = 0,
        lastPlayedAtMillis = System.currentTimeMillis(),
      ),
    )

    val top = repository.topGenres(5)
    assertTrue("Should return non-empty top genres", top.isNotEmpty())
    assertTrue("Should contain rock from taste profile", top.contains("rock"))
    assertTrue("Should contain indie from taste profile", top.contains("indie"))
  }

  @Test
  fun testGenresForTrackGracefulFallbackOnFailure() = runTest {
    fakeApi.shouldFail = true
    val result = repository.genresForTrack("song|failing_artist")
    assertTrue("Should return empty list on failure without crashing", result.isEmpty())

    // Ensure failed lookup was NOT cached: subsequent call after API recovers should succeed
    fakeApi.shouldFail = false
    fakeApi.tagsByArtist["failing_artist"] = listOf(Tag("jazz", 100))
    val retryResult = repository.genresForTrack("song|failing_artist")
    assertTrue("Should fetch tags after recovery if not cached", retryResult.contains("jazz"))
  }
}
