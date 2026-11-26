package com.revenge.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_PODCAST_EPISODE
import com.revenge.music.extensions.metadata
import com.revenge.music.models.MediaMetadata

interface Queue {

    val preloadItem: MediaMetadata?

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {

        fun filterExplicit(enabled: Boolean = true): Status {
            if (!enabled) return this
            return withFilter { it.metadata?.explicit == true }
        }

        fun filterVideoSongs(disableVideos: Boolean = false): Status {
            if (!disableVideos) return this
            return withFilter { item ->
                val meta = item.metadata
                meta != null && meta.isVideoSong && meta.musicVideoType != MUSIC_VIDEO_TYPE_PODCAST_EPISODE
            }
        }

        private inline fun withFilter(drop: (MediaItem) -> Boolean): Status {
            val kept = ArrayList<MediaItem>(items.size)
            var keptSelection = -1
            for (index in items.indices) {
                val item = items[index]
                val remove = index != mediaItemIndex && drop(item)
                if (!remove) {
                    if (index == mediaItemIndex) keptSelection = kept.size
                    kept.add(item)
                }
            }
            val newIndex = if (keptSelection >= 0) {
                keptSelection
            } else {
                mediaItemIndex.coerceIn(0, (kept.size - 1).coerceAtLeast(0))
            }
            return Status(title, kept, newIndex, position)
        }
    }
}

fun List<MediaItem>.filterExplicit(
    enabled: Boolean = true,
    keepMediaId: String? = null,
): List<MediaItem> {
    if (!enabled) return this
    return filterNot { it.metadata?.explicit == true && it.mediaId != keepMediaId }
}

fun List<MediaItem>.filterVideoSongs(
    disableVideos: Boolean = false,
    keepMediaId: String? = null,
): List<MediaItem> {
    if (!disableVideos) return this
    return filterNot { item ->
        val meta = item.metadata
        meta != null &&
            meta.isVideoSong &&
            meta.musicVideoType != MUSIC_VIDEO_TYPE_PODCAST_EPISODE &&
            item.mediaId != keepMediaId
    }
}
