package com.revenge.core.models

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersistQueueTest {

  private fun <T : Serializable> roundTrip(value: T): T {
    val bytes =
      ByteArrayOutputStream()
        .also { buffer ->
          ObjectOutputStream(buffer).use { it.writeObject(value) }
        }
        .toByteArray()
    return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() } as T
  }

  private fun metadata(id: String) =
    MediaMetadata(
      id = id,
      title = "Title $id",
      artists =
        listOf(
          MediaMetadata.Artist(id = "artist", name = "Artist"),
          MediaMetadata.Artist(id = null, name = "Local Artist"),
        ),
      duration = 42,
      thumbnailUrl = "https://img/$id.jpg",
      album = MediaMetadata.Album(id = "album", title = "Album"),
      liked = true,
      likedDate = LocalDateTime.of(2024, 5, 6, 7, 8, 9),
      source = QueueItemSource.USER,
    )

  @Test
  fun `queue survives a java serialization round trip`() {
    val original =
      PersistQueue(
        title = "Mix",
        items = listOf(metadata("a"), metadata("b")),
        mediaItemIndex = 1,
        position = 12_345L,
        queueType = QueueType.YOUTUBE_ALBUM_RADIO,
        queueData =
          QueueData.YouTubeAlbumRadioData(
            playlistId = "PL1",
            albumSongCount = 9,
            continuation = "cont",
            firstTimeLoaded = true,
          ),
        shuffledIndices = listOf(1, 0),
      )

    assertEquals(original, roundTrip(original))
  }

  @Test
  fun `all queue types and data variants round trip`() {
    for (type in
      listOf(
        QueueType.LIST,
        QueueType.YOUTUBE,
        QueueType.YOUTUBE_ALBUM_RADIO,
        QueueType.LOCAL_ALBUM_RADIO,
      )) {
      val queue =
        PersistQueue(
          title = null,
          items = emptyList(),
          mediaItemIndex = 0,
          position = 0L,
          queueType = type,
        )
      assertEquals(type, roundTrip(queue).queueType)
      assertNull(roundTrip(queue).queueData)
    }

    val youtube = QueueData.YouTubeData(endpoint = "endpoint")
    assertEquals(youtube, roundTrip(youtube))
    assertEquals(
      QueueData.LocalAlbumRadioData(albumId = "alb", startIndex = 3),
      roundTrip(QueueData.LocalAlbumRadioData(albumId = "alb", startIndex = 3)),
    )
  }

  @Test
  fun `out of range index survives serialization`() {
    val queue =
      PersistQueue(
        title = "Broken",
        items = listOf(metadata("only")),
        mediaItemIndex = 7,
        position = 99L,
      )
    assertEquals(7, roundTrip(queue).mediaItemIndex)
  }

  @Test
  fun `player state survives a java serialization round trip`() {
    val state =
      PersistPlayerState(
        playWhenReady = true,
        repeatMode = 2,
        shuffleModeEnabled = true,
        volume = 0.5f,
        currentPosition = 1234L,
        currentMediaItemIndex = 3,
        playbackState = 4,
        timestamp = 777L,
      )
    assertEquals(state, roundTrip(state))
  }
}
