package com.revenge.music.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.music.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueFilterSelectedTrackTest {

  private fun item(id: String, explicit: Boolean = false, video: Boolean = false): MediaItem {
    val metadata =
      MediaMetadata(
        id = id,
        title = "Song $id",
        artists = emptyList(),
        duration = 100,
        explicit = explicit,
        musicVideoType = if (video) "MUSIC_VIDEO_TYPE_OMV" else null,
      )
    // A local configuration (and therefore the tag) only exists when a uri is present.
    return MediaItem.Builder().setMediaId(id).setUri("https://example.test/$id").setTag(metadata).build()
  }

  @Test
  fun selectedExplicitSongSurvivesWhileOtherExplicitSongsAreDropped() {
    val status =
      Queue.Status(
        title = "test",
        items = listOf(item("a"), item("b", explicit = true), item("c", explicit = true)),
        mediaItemIndex = 1,
      )

    val filtered = status.filterExplicit(true)

    assertEquals(listOf("a", "b"), filtered.items.map { it.mediaId })
    assertEquals("b", filtered.items[filtered.mediaItemIndex].mediaId)
  }

  @Test
  fun selectedVideoSurvivesWhileOtherVideosAreDropped() {
    val status =
      Queue.Status(
        title = "test",
        items = listOf(item("a"), item("b", video = true), item("c", video = true)),
        mediaItemIndex = 1,
      )

    val filtered = status.filterVideoSongs(true)

    assertEquals(listOf("a", "b"), filtered.items.map { it.mediaId })
    assertEquals("b", filtered.items[filtered.mediaItemIndex].mediaId)
  }

  @Test
  fun duplicateMediaIdsReselectTheSameOccurrence() {
    val status =
      Queue.Status(
        title = "test",
        items = listOf(item("x"), item("a"), item("x"), item("b")),
        mediaItemIndex = 2,
      )

    val filtered = status.filterExplicit(true)

    assertEquals(listOf("x", "a", "x", "b"), filtered.items.map { it.mediaId })
    assertEquals(2, filtered.mediaItemIndex)
  }

  @Test
  fun disabledFiltersLeaveTheStatusAlone() {
    val status = Queue.Status("test", listOf(item("a", explicit = true)), 0)
    assertEquals(status, status.filterExplicit(false))
    assertEquals(status, status.filterVideoSongs(false))
  }
}
