package com.revenge.playback.queues

import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.pages.PlaylistContinuationPage
import com.revenge.innertube.pages.PlaylistPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class YouTubePlaylistQueueTest {

    @Test
    fun windowAroundSelectedSong() = runBlocking {
        val queue = queueOf(300, startIndex = 150)

        val status = queue.getInitialStatus()

        assertEquals("song130", status.items.first().mediaId)
        assertEquals("song249", status.items.last().mediaId)
        assertEquals(120, status.items.size)
        assertEquals(20, status.mediaItemIndex)
        assertEquals(50, pendingCount(queue))
        assertTrue(queue.hasNextPage())
    }

    @Test
    fun windowAtStartOfPlaylist() = runBlocking {
        val queue = queueOf(300, startIndex = 0)

        val status = queue.getInitialStatus()

        assertEquals("song0", status.items.first().mediaId)
        assertEquals("song99", status.items.last().mediaId)
        assertEquals(0, status.mediaItemIndex)
        assertEquals(200, pendingCount(queue))
    }

    @Test
    fun windowAtEndOfPlaylistHasNoContinuation() = runBlocking {
        val queue = queueOf(300, startIndex = 295)

        val status = queue.getInitialStatus()

        assertEquals(25, status.items.size)
        assertEquals(20, status.mediaItemIndex)
        assertEquals(0, pendingCount(queue))
        assertFalse(queue.hasNextPage())
    }

    @Test
    fun pendingPagesAreServedInFiftySongChunks() = runBlocking {
        val queue = queueOf(300, startIndex = 50)
        queue.getInitialStatus()
        assertEquals(150, pendingCount(queue))

        val first = queue.nextPage()
        assertEquals(50, first.size)
        assertEquals("song150", first.first().mediaId)
        assertEquals(100, pendingCount(queue))
        assertTrue(queue.hasPendingSongs())

        queue.nextPage()
        queue.nextPage()
        assertFalse(queue.hasPendingSongs())
        assertFalse(queue.hasNextPage())
    }

    @Test
    fun filteringKeepsSelectedSong() = runBlocking {
        val songs = listOf(
            song("explicit", explicit = true),
            song("video", musicVideoType = "MUSIC_VIDEO_TYPE_OMV"),
            song("selected"),
        )
        val queue = YouTubePlaylistQueue(
            playlistId = "PL",
            playlistTitle = "list",
            initialSongs = songs,
            startIndex = 2,
        )
        val status = queue.getInitialStatus()

        val explicitFiltered = status.filterExplicit(true)
        assertEquals(listOf("video", "selected"), explicitFiltered.items.map { it.mediaId })
        assertEquals("selected", explicitFiltered.items[explicitFiltered.mediaItemIndex].mediaId)

        val videoFiltered = status.filterVideoSongs(true)
        assertEquals("selected", videoFiltered.items[videoFiltered.mediaItemIndex].mediaId)
    }

    @Test
    fun networkInitialPageIsMapped() = runBlocking {
        val queue = YouTubePlaylistQueue(playlistId = "PL", startIndex = 1)
        queue.playlistLoader = {
            Result.success(
                PlaylistPage(
                    playlist = playlistItem("Remote playlist"),
                    songs = listOf(song("a"), song("b"), song("c")),
                    songsContinuation = "next-token",
                    continuation = null,
                ),
            )
        }

        val status = queue.getInitialStatus()

        assertEquals("Remote playlist", status.title)
        assertEquals(listOf("a", "b", "c"), status.items.map { it.mediaId })
        assertEquals(1, status.mediaItemIndex)
        assertTrue(queue.hasNextPage())
    }

    @Test
    fun networkInitialFailurePropagates() {
        val queue = YouTubePlaylistQueue(playlistId = "PL")
        queue.playlistLoader = { Result.failure(IOException("boom")) }

        assertThrows(IOException::class.java) {
            runBlocking { queue.getInitialStatus() }
        }
    }

    @Test
    fun continuationRetryBudgetExhaustsQueue() = runBlocking {
        val queue = queueOf(300, startIndex = 295, initialContinuation = "token")
        queue.getInitialStatus()
        queue.continuationLoader = { Result.failure(IOException("continuation failed")) }

        assertThrows(IOException::class.java) {
            runBlocking { queue.nextPage() }
        }
        assertFalse(queue.hasNextPage())
    }

    @Test
    fun cancellationPropagatesFromContinuation() = runBlocking {
        val queue = queueOf(300, startIndex = 295, initialContinuation = "token")
        queue.getInitialStatus()
        queue.continuationLoader = { throw CancellationException("cancelled") }

        assertThrows(CancellationException::class.java) {
            runBlocking { queue.nextPage() }
        }
        Unit
    }

    @Test
    fun threeFailuresThenSuccessResetsFailureCounter() = runBlocking {
        val queue = queueOf(300, startIndex = 295, initialContinuation = "token")
        queue.getInitialStatus()

        var calls = 0
        queue.continuationLoader = {
            calls++
            if (calls < 4) {
                Result.failure(IOException("failure $calls"))
            } else {
                Result.success(PlaylistContinuationPage(listOf(song("late")), "next"))
            }
        }
        val page = queue.nextPage()
        assertEquals(listOf("late"), page.map { it.mediaId })
        assertEquals("next", continuationValue(queue))

        var laterCalls = 0
        queue.continuationLoader = {
            laterCalls++
            if (laterCalls < 3) {
                Result.failure(IOException("later failure $laterCalls"))
            } else {
                Result.success(PlaylistContinuationPage(listOf(song("recovered")), null))
            }
        }
        val recovered = queue.nextPage()
        assertEquals(listOf("recovered"), recovered.map { it.mediaId })
    }

    private fun queueOf(
        size: Int,
        startIndex: Int,
        initialContinuation: String? = null,
    ): YouTubePlaylistQueue = YouTubePlaylistQueue(
        playlistId = "PL",
        playlistTitle = "list",
        initialSongs = (0 until size).map { song("song$it") },
        initialContinuation = initialContinuation,
        startIndex = startIndex,
    )

    private fun pendingCount(queue: YouTubePlaylistQueue): Int {
        val field = YouTubePlaylistQueue::class.java.getDeclaredField("pendingSongs")
        field.isAccessible = true
        return (field.get(queue) as List<*>).size
    }

    private fun continuationValue(queue: YouTubePlaylistQueue): String? {
        val field = YouTubePlaylistQueue::class.java.getDeclaredField("continuation")
        field.isAccessible = true
        return field.get(queue) as String?
    }

    private fun playlistItem(title: String): PlaylistItem = PlaylistItem(
        id = "PL",
        title = title,
        author = null,
        songCountText = null,
        thumbnail = "",
        playEndpoint = null,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )

    private fun song(
        id: String,
        explicit: Boolean = false,
        musicVideoType: String? = null,
    ): SongItem = SongItem(
        id = id,
        title = id,
        artists = emptyList(),
        thumbnail = "",
        explicit = explicit,
        musicVideoType = musicVideoType,
    )
}
