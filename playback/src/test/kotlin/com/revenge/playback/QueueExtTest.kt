package com.revenge.playback

import androidx.media3.common.MediaItem
import com.revenge.core.db.entities.AlbumEntity
import com.revenge.core.db.entities.AlbumWithSongs
import com.revenge.core.extensions.toMediaItem
import com.revenge.core.models.MediaMetadata
import com.revenge.core.models.PersistPlayerState
import com.revenge.core.models.PersistQueue
import com.revenge.core.models.QueueData
import com.revenge.core.models.QueueType
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.playback.queues.EmptyQueue
import com.revenge.playback.queues.ListQueue
import com.revenge.playback.queues.LocalAlbumRadio
import com.revenge.playback.queues.LocalMixQueue
import com.revenge.playback.queues.Queue
import com.revenge.playback.queues.YouTubeAlbumRadio
import com.revenge.playback.queues.YouTubeQueue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueExtTest {

  private val items = listOf(metadata("a"), metadata("b"), metadata("c"))
  private val mediaItems = items.map { it.toMediaItem() }

  @Test
  fun youtubeQueueSnapshotsAsYoutubeWithPlaceholderEndpoint() {
    val persist =
      YouTubeQueue(WatchEndpoint(videoId = "video"))
        .toPersistQueue(
          title = "t",
          items = items,
          mediaItemIndex = 1,
          position = 42L,
        )

    assertEquals(QueueType.YOUTUBE, persist.queueType)
    assertEquals("youtube_queue", (persist.queueData as QueueData.YouTubeData).endpoint)
  }

  @Test
  fun albumRadioSnapshotsWithPlaceholderPlaylistId() {
    val persist = YouTubeAlbumRadio("PL1").toPersistQueue("t", items, 0, 0L)

    assertEquals(QueueType.YOUTUBE_ALBUM_RADIO, persist.queueType)
    assertEquals(
      "youtube_album_radio",
      (persist.queueData as QueueData.YouTubeAlbumRadioData).playlistId,
    )
  }

  @Test
  fun localAlbumRadioSnapshotsWithPlaceholderAlbumId() {
    val album = AlbumEntity(id = "album", title = "Album", songCount = 0, duration = 0)
    val queue = LocalAlbumRadio(AlbumWithSongs(album, emptyList(), emptyList()), startIndex = 3)

    val persist = queue.toPersistQueue("t", items, 0, 0L)
    val data = persist.queueData as QueueData.LocalAlbumRadioData

    assertEquals(QueueType.LOCAL_ALBUM_RADIO, persist.queueType)
    assertEquals("local_album_radio", data.albumId)
    assertEquals(0, data.startIndex)
  }

  @Test
  fun finiteAndUnknownQueuesSnapshotAsList() {
    val queues =
      listOf<Queue>(
        ListQueue(items = mediaItems),
        EmptyQueue,
        allocate(LocalMixQueue::class.java),
        object : Queue {
          override val preloadItem: MediaMetadata? = null

          override suspend fun getInitialStatus(): Queue.Status = Queue.Status(null, mediaItems, 0)

          override fun hasNextPage(): Boolean = false

          override suspend fun nextPage(): List<MediaItem> = emptyList()
        },
      )

    for (queue in queues) {
      val persist = queue.toPersistQueue("t", items, 0, 0L)
      assertEquals(QueueType.LIST, persist.queueType)
      assertNull(persist.queueData)
    }
  }

  @Test
  fun snapshotPreservesFieldsAndShuffleIndices() {
    val persist =
      ListQueue(items = mediaItems)
        .toPersistQueue(
          title = "queue title",
          items = items,
          mediaItemIndex = 2,
          position = 987L,
        )
    val shuffled = persist.copy(shuffledIndices = listOf(2, 0, 1))

    assertEquals("queue title", persist.title)
    assertEquals(items, persist.items)
    assertEquals(2, persist.mediaItemIndex)
    assertEquals(987L, persist.position)
    assertEquals(listOf(2, 0, 1), shuffled.shuffledIndices)
  }

  @Test
  fun restoreYieldsListQueueWithMappedItems() {
    val persist =
      PersistQueue(
        title = "restored",
        items = items,
        mediaItemIndex = 2,
        position = 55L,
        queueType = QueueType.YOUTUBE,
        queueData = QueueData.YouTubeData("youtube_queue"),
      )

    val queue = persist.toQueue() as ListQueue

    assertEquals("restored", queue.title)
    assertEquals(listOf("a", "b", "c"), queue.items.map { it.mediaId })
    assertEquals(2, queue.startIndex)
    assertEquals(55L, queue.position)
  }

  @Test
  fun restoreToleratesEmptyItemsAndNegativePosition() {
    val persist =
      PersistQueue(
        title = null,
        items = emptyList(),
        mediaItemIndex = -5,
        position = -10L,
      )

    val queue = persist.toQueue() as ListQueue

    assertTrue(queue.items.isEmpty())
    assertNull(queue.title)
    assertEquals(-5, queue.startIndex)
    assertEquals(-10L, queue.position)
  }

  @Test
  fun javaSerializationRoundTripsPersistenceModels() {
    val persist =
      PersistQueue(
        title = "serialized",
        items = items,
        mediaItemIndex = 1,
        position = 7L,
        queueType = QueueType.YOUTUBE_ALBUM_RADIO,
        queueData = QueueData.YouTubeAlbumRadioData("PL", 3, "cont", true),
        shuffledIndices = listOf(1, 0, 2),
      )
    assertEquals(persist, roundTrip(persist))
    assertEquals(QueueType.LOCAL_ALBUM_RADIO, roundTrip(QueueType.LOCAL_ALBUM_RADIO))
    assertEquals(
      QueueData.LocalAlbumRadioData("album", 2, "PL", "cont"),
      roundTrip(QueueData.LocalAlbumRadioData("album", 2, "PL", "cont")),
    )
    val state =
      PersistPlayerState(
        playWhenReady = true,
        repeatMode = 1,
        shuffleModeEnabled = false,
        volume = 0.5f,
        currentPosition = 123L,
        currentMediaItemIndex = 2,
        playbackState = 3,
        timestamp = 99L,
      )
    assertEquals(state, roundTrip(state))
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> allocate(type: Class<T>): T {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val field = unsafeClass.getDeclaredField("theUnsafe")
    field.isAccessible = true
    val unsafe = field.get(null)
    val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
    return allocate.invoke(unsafe, type) as T
  }

  private fun roundTrip(value: Any): Any {
    val bytes = ByteArrayOutputStream()
    ObjectOutputStream(bytes).use { it.writeObject(value) }
    return ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
  }

  private fun metadata(id: String): MediaMetadata =
    MediaMetadata(
      id = id,
      title = id,
      artists = emptyList(),
      duration = 100,
    )
}
