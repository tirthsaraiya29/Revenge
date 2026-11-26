package com.revenge.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.core.models.MediaMetadata

class ListQueue(
    val title: String? = null,
    val items: List<MediaItem>,
    val startIndex: Int = 0,
    val position: Long = 0L,
) : Queue {

    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus(): Queue.Status =
        Queue.Status(title = title, items = items, mediaItemIndex = startIndex, position = position)

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<MediaItem> =
        throw UnsupportedOperationException("A list queue has no further pages")
}
