package com.revenge.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.core.models.MediaMetadata

object EmptyQueue : Queue {

    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus(): Queue.Status =
        Queue.Status(title = null, items = emptyList(), mediaItemIndex = -1)

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<MediaItem> = emptyList()
}
