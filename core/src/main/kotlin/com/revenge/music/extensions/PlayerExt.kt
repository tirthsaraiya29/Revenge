package com.revenge.music.extensions

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import com.revenge.music.models.MediaMetadata
import java.util.ArrayDeque

fun Player.togglePlayPause() {
  if (!playWhenReady && playbackState == Player.STATE_IDLE) {
    prepare()
  }
  playWhenReady = !playWhenReady
}

fun Player.toggleRepeatMode() {
  repeatMode =
    when (repeatMode) {
      REPEAT_MODE_OFF -> REPEAT_MODE_ALL
      REPEAT_MODE_ALL -> REPEAT_MODE_ONE
      REPEAT_MODE_ONE -> REPEAT_MODE_OFF
      else -> throw IllegalStateException()
    }
}

/** Timeline windows ordered around the current item (previous items first, then next ones). */
fun Player.getQueueWindows(): List<Timeline.Window> {
  val timeline = currentTimeline
  val index = currentMediaItemIndex
  if (
    timeline.isEmpty ||
      index == C.INDEX_UNSET ||
      index < 0 ||
      index >= timeline.windowCount
  ) {
    return emptyList()
  }

  val windows = ArrayDeque<Timeline.Window>()
  val windowCount = timeline.windowCount
  windows.add(timeline.getWindow(index, Timeline.Window()))

  var firstIndex = index
  var lastIndex = index
  while (
    (firstIndex != C.INDEX_UNSET || lastIndex != C.INDEX_UNSET) && windows.size < windowCount
  ) {
    if (lastIndex != C.INDEX_UNSET) {
      lastIndex = timeline.getNextWindowIndex(lastIndex, REPEAT_MODE_OFF, shuffleModeEnabled)
      if (lastIndex != C.INDEX_UNSET) {
        windows.add(timeline.getWindow(lastIndex, Timeline.Window()))
      }
    }
    if (firstIndex != C.INDEX_UNSET && windows.size < windowCount) {
      firstIndex = timeline.getPreviousWindowIndex(firstIndex, REPEAT_MODE_OFF, shuffleModeEnabled)
      if (firstIndex != C.INDEX_UNSET) {
        windows.addFirst(timeline.getWindow(firstIndex, Timeline.Window()))
      }
    }
  }
  return windows.toList()
}

/** Position of the current item within the playback order, or -1 when nothing is playing. */
fun Player.getCurrentQueueIndex(): Int {
  if (currentTimeline.isEmpty) return -1
  var index = currentMediaItemIndex
  if (
    index == C.INDEX_UNSET ||
      index < 0 ||
      index >= currentTimeline.windowCount
  ) {
    return -1
  }

  var queueIndex = 0
  while (index != C.INDEX_UNSET) {
    index = currentTimeline.getPreviousWindowIndex(index, REPEAT_MODE_OFF, shuffleModeEnabled)
    if (index != C.INDEX_UNSET) queueIndex++
  }
  return queueIndex
}

val Player.currentMetadata: MediaMetadata?
  get() = currentMediaItem?.metadata

val Player.mediaItems: List<MediaItem>
  get() =
    object : AbstractList<MediaItem>() {
      override val size: Int
        get() = mediaItemCount

      override fun get(index: Int): MediaItem = getMediaItemAt(index)
    }

fun Player.findNextMediaItemById(mediaId: String): MediaItem? {
  for (i in currentMediaItemIndex until mediaItemCount) {
    val item = getMediaItemAt(i)
    if (item.mediaId == mediaId) return item
  }
  return null
}

fun Player.setOffloadEnabled(enabled: Boolean) {
  trackSelectionParameters =
    trackSelectionParameters
      .buildUpon()
      .setAudioOffloadPreferences(
        TrackSelectionParameters.AudioOffloadPreferences.Builder()
          .setAudioOffloadMode(
            if (enabled) {
              TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
            } else {
              TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
            },
          )
          .build(),
      )
      .build()
}
