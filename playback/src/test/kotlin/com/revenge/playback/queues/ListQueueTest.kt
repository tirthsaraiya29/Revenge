package com.revenge.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.music.models.MediaMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ListQueueTest {

    @Test
    fun emptyListQueueReportsEmptyStatus() = runBlocking {
        val queue = ListQueue(title = null, items = emptyList())

        val status = queue.getInitialStatus()

        assertNull(status.title)
        assertTrue(status.items.isEmpty())
        assertEquals(0, status.mediaItemIndex)
        assertEquals(0L, status.position)
        assertFalse(queue.hasNextPage())
        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking { queue.nextPage() }
        }
        Unit
    }

    @Test
    fun constructorValuesArePassedThroughUnchanged() = runBlocking {
        val items = listOf(item("a"), item("b"))
        val queue = ListQueue(
            title = "title",
            items = items,
            startIndex = -3,
            position = -17L,
        )

        val status = queue.getInitialStatus()

        assertEquals("title", status.title)
        assertEquals(items, status.items)
        assertEquals(-3, status.mediaItemIndex)
        assertEquals(-17L, status.position)
    }

    @Test
    fun emptyQueueHasNoPages() = runBlocking {
        val status = EmptyQueue.getInitialStatus()

        assertNull(status.title)
        assertTrue(status.items.isEmpty())
        assertEquals(-1, status.mediaItemIndex)
        assertNull(EmptyQueue.preloadItem)
        assertFalse(EmptyQueue.hasNextPage())
        assertTrue(EmptyQueue.nextPage().isEmpty())
    }

    @Test
    fun customQueueNeedsOnlyTheInterfaceMembers() = runBlocking {
        val queue: Queue = MinimalQueue()

        assertEquals("minimal", queue.getInitialStatus().title)
        assertFalse(queue.hasNextPage())
        assertTrue(queue.nextPage().isEmpty())
        assertNull(queue.preloadItem)
    }

    private class MinimalQueue : Queue {
        override val preloadItem: MediaMetadata? = null

        override suspend fun getInitialStatus(): Queue.Status =
            Queue.Status(title = "minimal", items = emptyList(), mediaItemIndex = 0)

        override fun hasNextPage(): Boolean = false

        override suspend fun nextPage(): List<MediaItem> = emptyList()
    }

    private fun item(id: String): MediaItem = MediaItem.Builder().setMediaId(id).build()
}
