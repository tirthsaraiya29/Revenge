package com.revenge.music.playback

import com.revenge.music.extensions.toMediaItem
import com.revenge.music.models.MediaMetadata
import com.revenge.music.models.PersistQueue
import com.revenge.music.models.QueueData
import com.revenge.music.models.QueueType
import com.revenge.music.playback.queues.ListQueue
import com.revenge.music.playback.queues.LocalAlbumRadio
import com.revenge.music.playback.queues.Queue
import com.revenge.music.playback.queues.YouTubeAlbumRadio
import com.revenge.music.playback.queues.YouTubeQueue

/**
 * Serialises a live queue for persistence. Queues that cannot be resumed from a network identity
 * degrade to a plain list, which is what restores playback anyway.
 */
fun Queue.toPersistQueue(
  title: String?,
  items: List<MediaMetadata>,
  mediaItemIndex: Int,
  position: Long,
): PersistQueue {
  val type =
    when (this) {
      is YouTubeQueue -> QueueType.YOUTUBE
      is YouTubeAlbumRadio -> QueueType.YOUTUBE_ALBUM_RADIO
      is LocalAlbumRadio -> QueueType.LOCAL_ALBUM_RADIO
      else -> QueueType.LIST
    }
  val data =
    when (type) {
      QueueType.YOUTUBE -> QueueData.YouTubeData(endpoint = "youtube_queue")
      QueueType.YOUTUBE_ALBUM_RADIO -> QueueData.YouTubeAlbumRadioData(playlistId = "youtube_album_radio")
      QueueType.LOCAL_ALBUM_RADIO ->
        QueueData.LocalAlbumRadioData(albumId = "local_album_radio", startIndex = 0)
      else -> null
    }
  return PersistQueue(
    title = title,
    items = items,
    mediaItemIndex = mediaItemIndex,
    position = position,
    queueType = type,
    queueData = data,
  )
}

/** Restores a persisted queue as a finite list; continuation queues are re-created by the UI. */
fun PersistQueue.toQueue(): Queue =
  ListQueue(
    title = title,
    items = items.map { it.toMediaItem() },
    startIndex = mediaItemIndex,
    position = position,
  )
