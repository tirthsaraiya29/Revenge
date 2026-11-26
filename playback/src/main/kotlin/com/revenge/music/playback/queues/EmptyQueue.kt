package com.revenge.music.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.music.models.MediaMetadata

/** Placeholder queue used before anything has been requested. */
object EmptyQueue : Queue {
  override val preloadItem: MediaMetadata? = null

  override suspend fun getInitialStatus() = Queue.Status(title = null, items = emptyList(), mediaItemIndex = -1)

  override fun hasNextPage() = false

  override suspend fun nextPage(): List<MediaItem> = emptyList()
}
