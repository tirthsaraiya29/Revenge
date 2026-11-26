package com.revenge.music.musicflow

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.revenge.music.listentogether.ListenTogetherManager
import com.revenge.music.extensions.currentMetadata
import com.revenge.music.models.MediaMetadata
import com.revenge.music.playback.MusicService
import com.revenge.music.playback.PlayerConnection
import com.revenge.music.playback.queues.Queue
import kotlinx.coroutines.flow.StateFlow

/** Adapts [PlayerConnection] to the coordinator port. */
class PlayerConnectionMusicFlowPlayer(
    private val connection: PlayerConnection,
    private val listenTogetherManager: ListenTogetherManager? = null,
) : MusicFlowPlayer {

    override val currentMediaId: String?
        get() = connection.player.currentMetadata?.id

    override val queueMediaIds: List<String>
        get() = connection.queueWindows.value.map { it.mediaItem.mediaId }

    override val isPlaying: StateFlow<Boolean> get() = connection.isPlaying

    override val isAnyFlowQueueActive: Boolean
        get() = connection.service.activeQueue.value is MusicFlowOwnedQueue

    override val takeoverBlock: TakeoverBlock?
        get() {
            if (listenTogetherManager?.isGuestPlaybackRestricted == true) {
                return TakeoverBlock.LISTEN_TOGETHER_GUEST
            }
            if (connection.service.castConnectionHandler?.isCasting?.value == true) {
                return TakeoverBlock.CASTING
            }
            return null
        }

    override fun ownsQueue(handle: Any): Boolean =
        handle is Queue && connection.service.activeQueue.value === handle

    override fun seekTo(queueIndex: Int, playWhenReady: Boolean) {
        val player = connection.player
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
            player.prepare()
        }
        player.seekTo(queueIndex, 0L)
        if (playWhenReady) player.play() else player.pause()
    }

    override suspend fun startQueue(
        items: List<MusicFlowItem>,
        startIndex: Int,
        playWhenReady: Boolean,
    ): Any {
        val queue = StaticFlowQueue(items, startIndex)
        connection.service.playQueue(queue, playWhenReady)
        return queue
    }

    override fun append(handle: Any, items: List<MusicFlowItem>) {
        if (!ownsQueue(handle)) return
        connection.service.addToQueue(items.map { it.toMediaItem() })
    }

    override fun setPauseAtEnd(handle: Any, pause: Boolean) {
        if (handle !is Queue) return
        connection.service.setPauseAtEndOfItems(handle, pause)
    }

    override fun snapshot(): Any? = connection.service.snapshotQueue()

    override fun restore(snapshot: Any, playWhenReady: Boolean) {
        val queueSnapshot = snapshot as? MusicService.QueueSnapshot ?: return
        connection.service.restoreQueueSnapshot(queueSnapshot, playWhenReady)
    }
}

private class StaticFlowQueue(
    private val items: List<MusicFlowItem>,
    private val startIndex: Int,
) : MusicFlowOwnedQueue {
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus(): Queue.Status {
        val mediaItems: List<MediaItem> = items.map { it.toMediaItem() }
        return Queue.Status(
            title = "Music Flow",
            items = mediaItems,
            mediaItemIndex = startIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0)),
        )
    }

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<MediaItem> = emptyList()
}
