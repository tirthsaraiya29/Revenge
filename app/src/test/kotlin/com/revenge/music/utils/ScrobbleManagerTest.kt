package com.revenge.music.utils

import com.revenge.core.models.MediaMetadata
import com.revenge.music.utils.lastfm.LastFM
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScrobbleManagerTest {

  @Before
  fun setUp() {
    LastFM.initialize("test-key", "test-secret")
  }

  private fun metadata(id: String, durationSeconds: Int = 200): MediaMetadata =
    MediaMetadata(
      id = id,
      title = "Title $id",
      artists = listOf(MediaMetadata.Artist(null, "Artist")),
      duration = durationSeconds,
      album = MediaMetadata.Album("album", "Album"),
    )

  @Test
  fun firesAtPercentThresholdCappedByAbsoluteDelay() = runTest {
    val manager = ScrobbleManager(this)
    val scrobbles = mutableListOf<Triple<String, Long, Int>>()
    manager.submitScrobble = { _, _, timestamp, _, duration ->
      scrobbles += Triple("t", timestamp, duration)
    }
    manager.submitNowPlaying = { _, _, _, _ -> }
    manager.enableScrobbling = true
    manager.scrobbleDelayPercent = 0.5f
    manager.scrobbleDelaySeconds = 50
    manager.nowSeconds = { 1_000_000L }
    val track = metadata("t", 200)
    manager.onSongStart(track, 200_000L)
    manager.onPlayerStateChanged(true, track, 200_000L)

    advanceTimeBy(49_000)
    runCurrent()
    assertTrue(scrobbles.isEmpty())

    advanceTimeBy(1_000)
    runCurrent()
    assertEquals(1, scrobbles.size)
    assertEquals(1_000_000L - 50, scrobbles.first().second)
    manager.destroy()
  }

  @Test
  fun shorterTrackUsesHalfItsDuration() = runTest {
    val manager = ScrobbleManager(this)
    val scrobbles = mutableListOf<Long>()
    manager.submitScrobble = { _, _, timestamp, _, _ -> scrobbles += timestamp }
    manager.submitNowPlaying = { _, _, _, _ -> }
    manager.enableScrobbling = true
    manager.nowSeconds = { 5_000L }
    val track = metadata("short", 40)
    manager.onSongStart(track, 40_000L)
    manager.onPlayerStateChanged(true, track, 40_000L)

    advanceTimeBy(19_000)
    runCurrent()
    assertTrue(scrobbles.isEmpty())
    advanceTimeBy(1_000)
    runCurrent()
    assertEquals(listOf(5_000L - 20), scrobbles)
    manager.destroy()
  }

  @Test
  fun tooShortTrackIsNeverScrobbled() = runTest {
    val manager = ScrobbleManager(this)
    var count = 0
    manager.submitScrobble = { _, _, _, _, _ -> count++ }
    manager.submitNowPlaying = { _, _, _, _ -> }
    manager.enableScrobbling = true
    manager.minSongDuration = 30
    val track = metadata("tiny", 10)
    manager.onSongStart(track, 10_000L)
    manager.onPlayerStateChanged(true, track, 10_000L)

    advanceTimeBy(120_000)
    runCurrent()
    assertEquals(0, count)
    manager.destroy()
  }

  @Test
  fun unknownDurationUsesThreeHundredSecondPercentButKeepsCap() = runTest {
    val manager = ScrobbleManager(this)
    var count = 0
    manager.submitScrobble = { _, _, _, _, _ -> count++ }
    manager.submitNowPlaying = { _, _, _, _ -> }
    manager.enableScrobbling = true
    manager.scrobbleDelaySeconds = 50
    val track = metadata("unknown", 0)
    manager.onSongStart(track, 0)
    manager.onPlayerStateChanged(true, track, 0)

    advanceTimeBy(49_000)
    runCurrent()
    assertEquals(0, count)
    advanceTimeBy(1_000)
    runCurrent()
    assertEquals(1, count)
    manager.destroy()
  }

  @Test
  fun pauseStopsAccumulationAndResumeContinues() = runTest {
    val manager = ScrobbleManager(this)
    var count = 0
    manager.submitScrobble = { _, _, _, _, _ -> count++ }
    manager.submitNowPlaying = { _, _, _, _ -> }
    manager.enableScrobbling = true
    manager.scrobbleDelaySeconds = 50
    val track = metadata("resume", 200)
    manager.onSongStart(track, 200_000L)
    manager.onPlayerStateChanged(true, track, 200_000L)

    advanceTimeBy(20_000)
    runCurrent()
    manager.onSongPause()
    advanceTimeBy(10_000)
    runCurrent()
    assertEquals(0, count)
    manager.onSongResume(track)
    advanceTimeBy(30_000)
    runCurrent()
    assertEquals(1, count)
    manager.destroy()
  }

  @Test
  fun scrobblesOnlyOncePerTrack() = runTest {
    val manager = ScrobbleManager(this)
    var count = 0
    manager.submitScrobble = { _, _, _, _, _ -> count++ }
    manager.submitNowPlaying = { _, _, _, _ -> }
    manager.enableScrobbling = true
    manager.scrobbleDelaySeconds = 50
    val track = metadata("once", 200)
    manager.onSongStart(track, 200_000L)
    manager.onPlayerStateChanged(true, track, 200_000L)

    advanceTimeBy(200_000)
    runCurrent()
    assertEquals(1, count)
    manager.destroy()
  }

  @Test
  fun transitionWhilePlayingResetsCounter() = runTest {
    val manager = ScrobbleManager(this)
    var count = 0
    manager.submitScrobble = { _, _, _, _, _ -> count++ }
    manager.submitNowPlaying = { _, _, _, _ -> }
    manager.enableScrobbling = true
    manager.scrobbleDelaySeconds = 50
    val first = metadata("first", 200)
    manager.onSongStart(first, 200)
    manager.onPlayerStateChanged(true, first, 200)
    advanceTimeBy(40_000)
    runCurrent()

    val second = metadata("second", 200)
    manager.onPlayerStateChanged(true, second, 200)
    advanceTimeBy(49_000)
    runCurrent()
    assertEquals(0, count)
    advanceTimeBy(1_000)
    runCurrent()
    assertEquals(1, count)
    manager.destroy()
  }

  @Test
  fun destroyStopsAllSubmissions() = runTest {
    val manager = ScrobbleManager(this)
    var scrobbles = 0
    var nowPlaying = 0
    manager.submitScrobble = { _, _, _, _, _ -> scrobbles++ }
    manager.submitNowPlaying = { _, _, _, _ -> nowPlaying++ }
    manager.enableScrobbling = true
    manager.useNowPlaying = true
    val track = metadata("destroy", 200)
    manager.onSongStart(track, 200_000L)
    manager.onPlayerStateChanged(true, track, 200_000L)
    runCurrent()
    assertEquals(1, nowPlaying)

    manager.destroy()
    advanceTimeBy(200_000)
    runCurrent()
    assertEquals(0, scrobbles)
    assertEquals(1, nowPlaying)
  }
}
