package com.revenge.playback

import com.revenge.core.extensions.toMediaItem
import com.revenge.core.models.MediaMetadata
import com.revenge.core.models.PersistQueue
import com.revenge.core.models.QueueData
import com.revenge.core.models.QueueType
import com.revenge.playback.queues.ListQueue
import com.revenge.playback.queues.LocalAlbumRadio
import com.revenge.playback.queues.Queue
import com.revenge.playback.queues.YouTubeAlbumRadio
import com.revenge.playback.queues.YouTubeQueue

fun Queue.toPersistQueue(
  title: String?,
  items: List<MediaMetadata>,
  mediaItemIndex: Int,
  position: Long,
): PersistQueue {
  val (queueType, queueData) =
    when (this) {
      is YouTubeQueue -> QueueType.YOUTUBE to QueueData.YouTubeData(endpoint = "youtube_queue")
      is YouTubeAlbumRadio ->
        QueueType.YOUTUBE_ALBUM_RADIO to
          QueueData.YouTubeAlbumRadioData(playlistId = "youtube_album_radio")
      is LocalAlbumRadio ->
        QueueType.LOCAL_ALBUM_RADIO to
          QueueData.LocalAlbumRadioData(albumId = "local_album_radio", startIndex = 0)
      else -> QueueType.LIST to null
    }
  return PersistQueue(
    title = title,
    items = items,
    mediaItemIndex = mediaItemIndex,
    position = position,
    queueType = queueType,
    queueData = queueData,
  )
}

fun PersistQueue.toQueue(): Queue =
  ListQueue(
    title = title,
    items = items.map { it.toMediaItem() },
    startIndex = mediaItemIndex,
    position = position,
  )
