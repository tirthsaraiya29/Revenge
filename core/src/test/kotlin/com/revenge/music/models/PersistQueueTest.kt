package com.revenge.music.models

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistQueueTest {

    private fun <T : Serializable> roundTrip(value: T): T {
        val bytes = ByteArrayOutputStream().use { buffer ->
            ObjectOutputStream(buffer).use { it.writeObject(value) }
            buffer.toByteArray()
        }
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as T }
    }

    private fun sampleMetadata(id: String) = MediaMetadata(
        id = id,
        title = "Track $id",
        artists = listOf(
            MediaMetadata.Artist(id = "a1", name = "Artist One"),
            MediaMetadata.Artist(id = null, name = "Local Artist"),
        ),
        duration = 245,
        thumbnailUrl = "https://img/$id.jpg",
        album = MediaMetadata.Album(id = "al", title = "Album"),
        setVideoId = "set-$id",
        musicVideoType = "MUSIC_VIDEO_TYPE_ATV",
        explicit = true,
        liked = true,
        source = QueueItemSource.USER,
    )

    @Test
    fun queueRoundTripPreservesEveryField() {
        val original = PersistQueue(
            title = "Queue",
            items = listOf(sampleMetadata("one"), sampleMetadata("two")),
            mediaItemIndex = 1,
            position = 12_345L,
            queueType = QueueType.YOUTUBE_ALBUM_RADIO,
            queueData = QueueData.YouTubeAlbumRadioData(playlistId = "pl", albumSongCount = 7),
            shuffledIndices = listOf(1, 0),
        )

        val copy = roundTrip(original)

        assertEquals(original, copy)
        assertEquals(2, copy.items.size)
        assertEquals("pl", (copy.queueData as QueueData.YouTubeAlbumRadioData).playlistId)
        assertEquals(listOf(1, 0), copy.shuffledIndices)
    }

    @Test
    fun playerStateRoundTripAndDefaultTimestamp() {
        val before = System.currentTimeMillis()
        val state = PersistPlayerState(
            playWhenReady = true,
            repeatMode = 2,
            shuffleModeEnabled = true,
            volume = 0.7f,
            currentPosition = 99L,
            currentMediaItemIndex = 3,
            playbackState = 3,
        )
        val after = System.currentTimeMillis()

        val copy = roundTrip(state)
        assertEquals(state, copy)
        assertTrue(copy.timestamp in before..after)

        val explicit = state.copy(timestamp = 42L)
        assertEquals(42L, roundTrip(explicit).timestamp)
    }

    @Test
    fun queueTypeObjectsKeepIdentity() {
        listOf(
            QueueType.LIST,
            QueueType.YOUTUBE,
            QueueType.YOUTUBE_ALBUM_RADIO,
            QueueType.LOCAL_ALBUM_RADIO,
        ).forEach { type ->
            val copy = roundTrip(type)
            assertSame(type, copy)
        }
    }

    @Test
    fun queueDataSubtypesSurvive() {
        val youtube = QueueData.YouTubeData(endpoint = "endpoint")
        val albumRadio = QueueData.YouTubeAlbumRadioData(playlistId = "pl", albumSongCount = 2)
        val localRadio = QueueData.LocalAlbumRadioData(albumId = "album", startIndex = 4)

        assertEquals(youtube, roundTrip(youtube))
        assertEquals(albumRadio, roundTrip(albumRadio))
        assertEquals(localRadio, roundTrip(localRadio))
        assertNotNull(roundTrip(youtube) as? QueueData.YouTubeData)
        assertTrue(roundTrip(localRadio) is QueueData.LocalAlbumRadioData)
    }
}
