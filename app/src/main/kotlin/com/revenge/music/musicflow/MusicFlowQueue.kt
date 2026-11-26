package com.revenge.music.musicflow

import androidx.media3.common.MediaItem
import com.revenge.music.extensions.toMediaItem
import com.revenge.music.models.MediaMetadata
import com.revenge.music.playback.queues.Queue

/** Marker for queues owned by the Music Flow subsystem. */
interface MusicFlowOwnedQueue : Queue

class MusicFlowQueue(
    private val title: String?,
    private val items: List<MusicFlowItem>,
    private val startIndex: Int,
    private val session: MusicFlowSession,
) : MusicFlowOwnedQueue {
    companion object {
        const val BROWSE_SKIP_THRESHOLD_MS = 10_000L
    }

    private val generation = session.currentGeneration

    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus(): Queue.Status {
        val mediaItems = items.map { it.toMediaItem() }
        return Queue.Status(
            title = title,
            items = mediaItems,
            mediaItemIndex = startIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0)),
        )
    }

    override fun hasNextPage(): Boolean =
        session.isLiveGeneration(generation) && session.hasMore

    override suspend fun nextPage(): List<MediaItem> {
        if (!session.isLiveGeneration(generation)) return emptyList()
        return session.loadMore().map { it.toMediaItem() }
    }
}

internal fun MusicFlowItem.toMediaItem(): MediaItem {
    val song = song
    val metadata = MediaMetadata(
        id = song.id,
        title = song.title,
        artists = song.artists.map { MediaMetadata.Artist(it.id, it.name) },
        duration = song.duration ?: -1,
        thumbnailUrl = song.thumbnail,
        album = song.album?.let { MediaMetadata.Album(it.id, it.name) },
        musicVideoType = song.musicVideoType,
        explicit = song.explicit,
    )
    return metadata.toMediaItem()
}
