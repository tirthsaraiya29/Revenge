package com.revenge.music.musicflow

import kotlinx.coroutines.flow.StateFlow

/** Port implemented by the player layer; the coordinator never touches Media3 directly. */
interface MusicFlowPlayer {
    val currentMediaId: String?
    val queueMediaIds: List<String>
    val isPlaying: StateFlow<Boolean>
    val isAnyFlowQueueActive: Boolean
    val takeoverBlock: TakeoverBlock?

    fun ownsQueue(handle: Any): Boolean
    fun seekTo(queueIndex: Int, playWhenReady: Boolean)
    suspend fun startQueue(
        items: List<MusicFlowItem>,
        startIndex: Int,
        playWhenReady: Boolean,
    ): Any
    fun append(handle: Any, items: List<MusicFlowItem>)
    fun setPauseAtEnd(handle: Any, pause: Boolean)
    fun snapshot(): Any?
    fun restore(snapshot: Any, playWhenReady: Boolean)
}

/**
 * Arbitrates feed selections against the player. Requests are numbered so a late answer to an old
 * selection can never win over a newer one.
 */
class MusicFlowCoordinator(
    private val session: MusicFlowSession,
    private val player: MusicFlowPlayer,
) {
    private var newestRequestId = 0L
    private var handle: Any? = null
    private var previousSnapshot: Any? = null

    fun nextRequestId(): Long {
        newestRequestId += 1
        return newestRequestId
    }

    suspend fun select(
        requestId: Long,
        item: MusicFlowItem,
        autoNext: Boolean = true,
        playWhenReady: Boolean = true,
    ): MusicFlowPlaybackStatus {
        if (requestId < newestRequestId) return MusicFlowPlaybackStatus.Switching
        if (item.songId == player.currentMediaId) return MusicFlowPlaybackStatus.Synced

        val owned = handle?.let(player::ownsQueue) == true
        if (owned) {
            val index = player.queueMediaIds.indexOf(item.songId)
            if (index >= 0) {
                player.seekTo(index, playWhenReady)
                return MusicFlowPlaybackStatus.Switching
            }
        }

        player.takeoverBlock?.let { return MusicFlowPlaybackStatus.Blocked(it) }

        if (!player.isAnyFlowQueueActive) {
            previousSnapshot = player.snapshot()
        }

        val items = session.queueHandle().items
        val startIndex = items.indexOfFirst { it.songId == item.songId }.coerceAtLeast(0)
        val newHandle = player.startQueue(items, startIndex, playWhenReady)
        handle = newHandle
        player.setPauseAtEnd(newHandle, !autoNext)
        return MusicFlowPlaybackStatus.Switching
    }

    fun applyAutoNext(autoNext: Boolean) {
        handle?.let { player.setPauseAtEnd(it, !autoNext) }
    }

    fun appendIfOwned(items: List<MusicFlowItem>) {
        val active = handle ?: return
        if (player.ownsQueue(active)) player.append(active, items)
    }

    val canRestorePreviousQueue: Boolean
        get() {
            val snapshot = previousSnapshot ?: return false
            val active = handle ?: return false
            return player.ownsQueue(active)
        }

    fun restorePreviousQueue(playWhenReady: Boolean): Boolean {
        if (!canRestorePreviousQueue) {
            previousSnapshot = null
            handle = null
            return false
        }
        val snapshot = previousSnapshot ?: return false
        player.restore(snapshot, playWhenReady)
        previousSnapshot = null
        handle = null
        return true
    }

    fun invalidateIfSuperseded() {
        val active = handle ?: return
        if (!player.ownsQueue(active)) {
            handle = null
            previousSnapshot = null
        }
    }
}
