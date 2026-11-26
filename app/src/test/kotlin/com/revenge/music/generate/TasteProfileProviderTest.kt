package com.revenge.music.generate

import androidx.datastore.preferences.core.edit
import androidx.room.Room
import com.revenge.core.constants.LastFMUsernameKey
import com.revenge.core.db.InternalDatabase
import com.revenge.core.db.MusicDatabase
import com.revenge.core.db.entities.TasteProfileEntity
import com.revenge.core.utils.dataStore
import com.revenge.music.utils.lastfm.LastFmTasteApi
import com.revenge.music.utils.lastfm.SimilarArtist
import com.revenge.music.utils.lastfm.SimilarTrack
import com.revenge.music.utils.lastfm.Tag
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

class FakeLastFmTasteApi : LastFmTasteApi {
  var userTopArtistsCallCount = 0
  var userTopTracksCallCount = 0
  var userTopTagsCallCount = 0
  var shouldFail = false

  override suspend fun getSimilarTracks(artist: String, track: String): Result<List<SimilarTrack>> {
    if (shouldFail) return Result.failure(RuntimeException("Network error"))
    return Result.success(listOf(SimilarTrack("Similar Song", "Similar Artist", 0.9)))
  }

  override suspend fun getSimilarArtists(artist: String): Result<List<SimilarArtist>> {
    if (shouldFail) return Result.failure(RuntimeException("Network error"))
    return Result.success(listOf(SimilarArtist("Similar Artist", 0.8)))
  }

  override suspend fun getTopTags(artist: String): Result<List<Tag>> {
    if (shouldFail) return Result.failure(RuntimeException("Network error"))
    return Result.success(listOf(Tag("synthpop", 100)))
  }

  override suspend fun getUserTopArtists(user: String, limit: Int): Result<List<String>> {
    userTopArtistsCallCount++
    if (shouldFail) return Result.failure(RuntimeException("Network error"))
    return Result.success(listOf("The Weeknd", "Daft Punk", "Kavinsky"))
  }

  override suspend fun getUserTopTracks(user: String, limit: Int): Result<List<SimilarTrack>> {
    userTopTracksCallCount++
    if (shouldFail) return Result.failure(RuntimeException("Network error"))
    return Result.success(
      listOf(SimilarTrack("Starboy", "The Weeknd"), SimilarTrack("Nightcall", "Kavinsky")),
    )
  }

  override suspend fun getUserTopTags(user: String, limit: Int): Result<List<Tag>> {
    userTopTagsCallCount++
    if (shouldFail) return Result.failure(RuntimeException("Network error"))
    return Result.success(listOf(Tag("synthwave", 80), Tag("electronic", 60)))
  }
}

@RunWith(RobolectricTestRunner::class)
class TasteProfileProviderTest {
  private val context get() = RuntimeEnvironment.getApplication()
  private lateinit var internalDatabase: InternalDatabase
  private lateinit var database: MusicDatabase
  private lateinit var fakeApi: FakeLastFmTasteApi
  private lateinit var provider: TasteProfileProvider

  @Before
  fun setup() = runBlocking {
    internalDatabase =
      Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    database = MusicDatabase(internalDatabase)
    fakeApi = FakeLastFmTasteApi()
    context.dataStore.edit { it[LastFMUsernameKey] = "audioscrobbler_user" }
    provider = TasteProfileProvider(database, fakeApi, context)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun testCacheHitWithinOneHourDoesNotFetchAgain() = runTest {
    // 1. First call fetches from API
    val firstProfile = provider.get()
    assertNotNull("First profile should not be null", firstProfile)
    assertEquals(1, fakeApi.userTopArtistsCallCount)
    assertEquals(1, fakeApi.userTopTracksCallCount)
    assertEquals(1, fakeApi.userTopTagsCallCount)

    // 2. Second call within TTL returns cached instance without network call
    val secondProfile = provider.get()
    assertNotNull(secondProfile)
    assertEquals(1, fakeApi.userTopArtistsCallCount) // Call count must remain 1
    assertEquals(firstProfile?.fetchedAtMillis, secondProfile?.fetchedAtMillis)
  }

  @Test
  fun testForceRefreshBypassesCache() = runTest {
    provider.get()
    assertEquals(1, fakeApi.userTopArtistsCallCount)

    provider.get(forceRefresh = true)
    assertEquals(2, fakeApi.userTopArtistsCallCount)
  }

  @Test
  fun testNetworkFailureReturnsStaleProfileFromDatabase() = runTest {
    // Pre-populate database with stale entry
    database.tasteProfileDao.upsert(
      TasteProfileEntity(
        id = 1,
        topArtistsJson = "[\"Radiohead\"]",
        topTracksJson = "[\"creep|radiohead\"]",
        topGenresJson = "[\"alt-rock\"]",
        confidence = 1.0f,
        updatedAtMillis = System.currentTimeMillis() - 7200_000L, // 2 hours old
      ),
    )

    // Simulate API failure
    fakeApi.shouldFail = true

    val profile = provider.get(forceRefresh = true)
    assertNotNull("Should return stale profile on API failure", profile)
    assertTrue(
      "Should contain Radiohead from stale record",
      profile!!.topArtistNames.contains("radiohead"),
    )
  }
}
