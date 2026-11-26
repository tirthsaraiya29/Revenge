package com.revenge.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.music.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueFilterSelectedTrackTest {

    private val audioType = "MUSIC_VIDEO_TYPE_ATV"
    private val videoType = "MUSIC_VIDEO_TYPE_OMV"
    private val podcastType = "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"

    @Test
    fun explicitFilterKeepsSelectedTrackAndFollowsSelection() {
        val items = listOf(
            item("a"),
            item("selected", explicit = true),
            item("other-explicit", explicit = true),
            item("b"),
        )
        val status = Queue.Status(title = null, items = items, mediaItemIndex = 1, position = 123L)

        val filtered = status.filterExplicit(true)

        assertEquals(listOf("a", "selected", "b"), filtered.items.map { it.mediaId })
        assertEquals(1, filtered.mediaItemIndex)
        assertEquals(123L, filtered.position)
    }

    @Test
    fun videoFilterKeepsSelectedVideo() {
        val items = listOf(
            item("audio", musicVideoType = audioType),
            item("selected-video", musicVideoType = videoType),
            item("other-video", musicVideoType = videoType),
            item("b"),
        )
        val status = Queue.Status(null, items, 1)

        val filtered = status.filterVideoSongs(true)

        assertEquals(listOf("audio", "selected-video", "b"), filtered.items.map { it.mediaId })
        assertEquals(1, filtered.mediaItemIndex)
    }

    @Test
    fun podcastEpisodeIsNeverDroppedByVideoFilter() {
        val items = listOf(
            item("podcast", musicVideoType = podcastType),
            item("video", musicVideoType = videoType),
            item("audio", musicVideoType = audioType),
        )
        val status = Queue.Status(null, items, 2)

        val filtered = status.filterVideoSongs(true)

        assertEquals(listOf("podcast", "audio"), filtered.items.map { it.mediaId })
        assertEquals(1, filtered.mediaItemIndex)
    }

    @Test
    fun duplicateIdsWithoutRemovalsKeepTheSameIndex() {
        val items = listOf(item("x"), item("a"), item("x"), item("b"))
        val status = Queue.Status(null, items, 2)

        val filtered = status.filterExplicit(true)

        assertEquals(listOf("x", "a", "x", "b"), filtered.items.map { it.mediaId })
        assertEquals(2, filtered.mediaItemIndex)
    }

    @Test
    fun fallbackIndexIsClampedWhenSelectionIsNotAddressable() {
        val items = listOf(item("x", explicit = true), item("b"))
        val status = Queue.Status(null, items, 5)

        val filtered = status.filterExplicit(true)

        assertEquals(listOf("b"), filtered.items.map { it.mediaId })
        assertEquals(0, filtered.mediaItemIndex)
    }

    @Test
    fun emptyFilteredListUsesIndexZero() {
        val items = listOf(item("x", explicit = true), item("y", explicit = true))
        val status = Queue.Status(null, items, -1)

        val filtered = status.filterExplicit(true)

        assertTrue(filtered.items.isEmpty())
        assertEquals(0, filtered.mediaItemIndex)
    }

    @Test
    fun disabledFiltersReturnSameInstance() {
        val status = Queue.Status(null, listOf(item("x", explicit = true)), 0)

        assertSame(status, status.filterExplicit(false))
        assertSame(status, status.filterVideoSongs(false))
    }

    @Test
    fun listExtensionsKeepNamedMediaId() {
        val items = listOf(
            item("selected", explicit = true, musicVideoType = videoType),
            item("other", explicit = true, musicVideoType = videoType),
        )

        val explicit = items.filterExplicit(enabled = true, keepMediaId = "selected")
        assertEquals(listOf("selected"), explicit.map { it.mediaId })

        val videos = items.filterVideoSongs(disableVideos = true, keepMediaId = "selected")
        assertEquals(listOf("selected"), videos.map { it.mediaId })

        assertSame(items, items.filterExplicit(enabled = false))
        assertSame(items, items.filterVideoSongs(disableVideos = false))
    }

    @Test
    fun itemsWithoutTagsAreTreatedAsPlainAudio() {
        val tagged = item("tagged", explicit = true)
        val untagged = MediaItem.Builder().setMediaId("untagged").build()
        val status = Queue.Status(null, listOf(tagged, untagged), 1)

        val filtered = status.filterExplicit(true)

        assertEquals(listOf("untagged"), filtered.items.map { it.mediaId })
        assertEquals(0, filtered.mediaItemIndex)
    }

    private fun item(
        id: String,
        explicit: Boolean = false,
        musicVideoType: String? = null,
    ): MediaItem {
        val metadata = MediaMetadata(
            id = id,
            title = id,
            artists = emptyList(),
            duration = 100,
            musicVideoType = musicVideoType,
            explicit = explicit,
        )
        return MediaItem.Builder()
            .setMediaId(id)
            .setTag(metadata)
            .build()
    }
}
