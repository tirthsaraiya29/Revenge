package com.revenge.music.spotify

import androidx.media3.common.MediaItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.SongItem
import com.revenge.core.models.MediaMetadata
import com.revenge.music.spotify.models.SpotifySimpleArtist
import com.revenge.music.spotify.models.SpotifyTrack
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpotifyQueueTest {

  private class FakeResolver(
    private val resolve: (String) -> SongItem?,
  ) : SpotifyPlaybackResolver() {
    override suspend fun resolveTrack(track: SpotifyTrack, hideExplicit: Boolean): SongItem? =
      resolve(track.id)

    override fun getCached(trackId: String): SongItem? = null
  }

  private fun track(id: String): SpotifyTrack =
    SpotifyTrack(
      id = id,
      name = "Song $id",
      artists = listOf(SpotifySimpleArtist(name = "Artist")),
      durationMs = 180_000,
      uri = "spotify:track:$id",
    )

  private fun song(id: String): SongItem =
    SongItem(
      id = id,
      title = "Song $id",
      artists = listOf(Artist(name = "Artist", id = "a")),
      duration = 180,
      thumbnail = "https://example.test/$id.jpg",
    )

  private fun ids(items: List<MediaItem>): List<String> = items.map { it.mediaId }

  @Test
  fun preservesOrderDuplicatesAndSelectedOccurrence() = runTest {
    val tracks = listOf(track("a"), track("a"), track("b"))
    val resolver = FakeResolver { id -> song(id) }
    val queue = SpotifyQueue(title = "Mix", tracks = tracks, startIndex = 1, resolver = resolver)

    val status = queue.getInitialStatus()

    assertEquals("Mix", status.title)
    assertEquals(listOf("a", "a", "b"), ids(status.items))
    assertEquals(1, status.mediaItemIndex)
  }

  @Test
  fun skipsUnresolvedWindowTracksAndAdjustsIndex() = runTest {
    val tracks = listOf(track("a"), track("b"), track("c"))
    val resolver = FakeResolver { id -> if (id == "b") null else song(id) }
    val queue = SpotifyQueue(tracks = tracks, startIndex = 0, resolver = resolver)

    val status = queue.getInitialStatus()

    assertEquals(listOf("a", "c"), ids(status.items))
    assertEquals(0, status.mediaItemIndex)
  }

  @Test
  fun selectedTrackFallsBackToPreloadItem() = runTest {
    val tracks = listOf(track("a"), track("b"), track("c"))
    val resolver = FakeResolver { id -> if (id == "b") null else song(id) }
    val preload =
      MediaMetadata(
        id = "preload",
        title = "Preload",
        artists = listOf(MediaMetadata.Artist(null, "Artist")),
        duration = 180,
      )
    val queue =
      SpotifyQueue(tracks = tracks, startIndex = 1, preloadItem = preload, resolver = resolver)

    val status = queue.getInitialStatus()

    assertEquals(listOf("a", "preload", "c"), ids(status.items))
    assertEquals(1, status.mediaItemIndex)
  }

  @Test
  fun emptyInputProducesEmptyStatus() = runTest {
    val queue = SpotifyQueue(title = "Empty", tracks = emptyList(), resolver = FakeResolver { null })

    val status = queue.getInitialStatus()

    assertEquals("Empty", status.title)
    assertTrue(status.items.isEmpty())
    assertEquals(0, status.mediaItemIndex)
  }

  @Test
  fun pagesPendingTracksInChunksOfTwentyFive() = runTest {
    val tracks = (0 until 60).map { track("t$it") }
    val resolver = FakeResolver { id -> song(id) }
    val queue = SpotifyQueue(tracks = tracks, startIndex = 10, resolver = resolver)

    val status = queue.getInitialStatus()
    assertEquals(30, status.items.size)
    assertTrue(queue.hasPendingSongs())
    assertTrue(queue.hasNextPage())

    val firstPage = queue.nextPage()
    assertEquals(25, firstPage.size)
    assertEquals(listOf("t30"), ids(firstPage).take(1))

    val secondPage = queue.nextPage()
    assertEquals(5, secondPage.size)
    assertFalse(queue.hasPendingSongs())
    assertFalse(queue.hasNextPage())
  }
}
