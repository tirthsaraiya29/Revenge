package com.revenge.music.extensions

import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.revenge.music.models.MediaMetadata

fun Player.togglePlayPause() {
    if (!playWhenReady && playbackState == Player.STATE_IDLE) {
        prepare()
    }
    playWhenReady = !playWhenReady
}

fun Player.toggleRepeatMode() {
    val next = when (repeatMode) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
        else -> throw IllegalStateException("Unexpected repeat mode: $repeatMode")
    }
    repeatMode = next
}

fun Player.getQueueWindows(): List<Timeline.Window> {
    val timeline = currentTimeline
    val windowCount = timeline.windowCount
    val currentIndex = currentMediaItemIndex
    if (windowCount <= 0 || currentIndex < 0 || currentIndex >= windowCount) return emptyList()

    val shuffled = shuffleModeEnabled
    val ordering = ArrayList<Int>(windowCount)
    var cursor = timeline.getFirstWindowIndex(shuffled)
    while (cursor != C.INDEX_UNSET && ordering.size < windowCount) {
        if (ordering.contains(cursor)) break
        ordering.add(cursor)
        cursor = timeline.getNextWindowIndex(cursor, Player.REPEAT_MODE_OFF, shuffled)
    }

    return ordering.map { timeline.getWindow(it, Timeline.Window()) }
}

fun Player.getCurrentQueueIndex(): Int {
    val index = currentMediaItemIndex
    if (index < 0 || index >= currentTimeline.windowCount) return -1
    return index
}

val Player.currentMetadata: MediaMetadata?
    get() = currentMediaItem?.metadata

val Player.mediaItems: List<MediaItem>
    get() = object : AbstractList<MediaItem>() {
        override val size: Int
            get() = mediaItemCount

        override fun get(index: Int): MediaItem = getMediaItemAt(index)
    }

fun Player.findNextMediaItemById(mediaId: String): MediaItem? {
    val start = currentMediaItemIndex.takeIf { it >= 0 } ?: 0
    for (index in start until mediaItemCount) {
        val item = getMediaItemAt(index)
        if (item.mediaId == mediaId) return item
    }
    return null
}

fun Player.setOffloadEnabled(enabled: Boolean) {
    val mode = if (enabled) {
        AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
    } else {
        AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
    }
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setAudioOffloadPreferences(
            AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(mode)
                .build()
        )
        .build()
}
