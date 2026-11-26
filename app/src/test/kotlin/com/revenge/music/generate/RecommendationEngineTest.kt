package com.revenge.music.generate

import androidx.room.Room
import com.revenge.core.db.InternalDatabase
import com.revenge.core.db.MusicDatabase
import com.revenge.core.db.entities.RecommendationExclusionEntity
import com.revenge.core.db.entities.SongPlayStatsEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RecommendationEngineTest {
  private lateinit var internalDatabase: InternalDatabase
  private lateinit var database: MusicDatabase
  private lateinit var fakeApi: FakeLastFmTasteApi
  private lateinit var tasteProfileProvider: TasteProfileProvider
  private lateinit var engine: RecommendationEngine

  @Before
  fun setup() {
    internalDatabase =
      Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), InternalDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    database = MusicDatabase(internalDatabase)
    fakeApi = FakeLastFmTasteApi()
    tasteProfileProvider =
      TasteProfileProvider(database, fakeApi, RuntimeEnvironment.getApplication())
    val genresRepository = GenresRepository(database, fakeApi, tasteProfileProvider)
    engine = RecommendationEngine(database, genresRepository, tasteProfileProvider, fakeApi)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun testEmptyHistoryFailsGracefully() = runTest {
    val result = engine.generate(20)
    assertTrue("Empty history should fail", result.isFailure)
    assertTrue(result.exceptionOrNull()?.message?.contains("No listening history") == true)
  }

  @Test
  fun testAllExcludedFailsGracefully() = runTest {
    database.songPlayStatsDao.upsert(
      SongPlayStatsEntity(
        trackKey = "bad song|bad artist",
        title = "Bad Song",
        artist = "Bad Artist",
        videoId = "vid1",
        totalPlayTimeMs = 120_000L,
        playCount = 5,
        skipCount = 0,
        lastPlayedAtMillis = System.currentTimeMillis(),
      ),
    )
    database.recommendationExclusionDao.upsert(
      RecommendationExclusionEntity(
        trackKey = "bad song|bad artist",
        excludedAtMillis = System.currentTimeMillis(),
        trackName = "Bad Song",
        artistName = "Bad Artist",
      ),
    )

    val result = engine.generate(20)
    assertTrue("All excluded should fail", result.isFailure)
    assertTrue(
      result.exceptionOrNull()?.message?.contains("All candidate tracks have been excluded") ==
        true,
    )
  }

  @Test
  fun testRecommendationSucceedsWithLocalSeedsAndLastFmSignals() = runTest {
    database.songPlayStatsDao.upsert(
      SongPlayStatsEntity(
        trackKey = "blinding lights|the weeknd",
        title = "Blinding Lights",
        artist = "The Weeknd",
        videoId = "seed_vid",
        totalPlayTimeMs = 300_000L,
        playCount = 10,
        skipCount = 0,
        lastPlayedAtMillis = System.currentTimeMillis(),
      ),
    )

    val result = engine.generate(10)
    assertTrue("Generation should succeed", result.isSuccess)
    val playlistId = result.getOrNull()
    assertTrue(
      "PlaylistId should start with local_taste_",
      playlistId?.startsWith("local_taste_") == true,
    )
  }

  @Test
  fun testRecommendationDegradesToLocalOnlyOnLastFmFailure() = runTest {
    database.songPlayStatsDao.upsert(
      SongPlayStatsEntity(
        trackKey = "blinding lights|the weeknd",
        title = "Blinding Lights",
        artist = "The Weeknd",
        videoId = "seed_vid",
        totalPlayTimeMs = 300_000L,
        playCount = 10,
        skipCount = 0,
        lastPlayedAtMillis = System.currentTimeMillis(),
      ),
    )

    fakeApi.shouldFail = true

    val result = engine.generate(10)
    assertTrue("Generation should succeed even when Last.fm fails", result.isSuccess)
    val playlistId = result.getOrNull()
    assertTrue(
      "PlaylistId should start with local_taste_",
      playlistId?.startsWith("local_taste_") == true,
    )
  }
}
