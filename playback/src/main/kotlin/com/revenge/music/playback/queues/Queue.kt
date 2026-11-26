package com.revenge.music.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_PODCAST_EPISODE
import com.revenge.music.extensions.metadata
import com.revenge.music.models.MediaMetadata

/** A source of playable pages: an initial window plus optional continuation pages. */
interface Queue {
  /** Item that may be played immediately while [getInitialStatus] is still loading. */
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
    fun filterExplicit(enabled: Boolean = true): Status =
      if (enabled) repositioned(items.filterExplicit(true, selectedMediaId)) else this

    fun filterVideoSongs(disableVideos: Boolean = false): Status =
      if (disableVideos) repositioned(items.filterVideoSongs(true, selectedMediaId)) else this

    private val selectedMediaId: String?
      get() = items.getOrNull(mediaItemIndex)?.mediaId

    /**
     * Keeps the selection on the very same occurrence of the previously selected media id after
     * entries were removed. A raw index would point at a different song once the list shrinks.
     */
    private fun repositioned(filtered: List<MediaItem>): Status {
      val selected = items.getOrNull(mediaItemIndex)
      val newIndex =
        if (selected == null) {
          -1
        } else {
          val occurrence = items.take(mediaItemIndex + 1).count { it.mediaId == selected.mediaId } - 1
          occurrenceIn(filtered, selected.mediaId, occurrence)
        }
      return copy(
        items = filtered,
        mediaItemIndex =
          if (newIndex >= 0) newIndex
          else mediaItemIndex.coerceIn(0, (filtered.size - 1).coerceAtLeast(0)),
      )
    }

    private fun occurrenceIn(list: List<MediaItem>, mediaId: String, occurrence: Int): Int {
      var seen = 0
      for (i in list.indices) {
        if (list[i].mediaId == mediaId) {
          if (seen == occurrence) return i
          seen++
        }
      }
      return -1
    }
  }
}

/** Drops explicit tracks while always keeping the selected [keepMediaId]. */
fun List<MediaItem>.filterExplicit(
  enabled: Boolean = true,
  keepMediaId: String? = null,
): List<MediaItem> =
  if (!enabled) this
  else filterNot { it.mediaId != keepMediaId && it.metadata?.explicit == true }

/**
 * Drops video songs while keeping [keepMediaId] and podcast episodes, which are audio content even
 * though YouTube tags them as video.
 */
fun List<MediaItem>.filterVideoSongs(
  disableVideos: Boolean = false,
  keepMediaId: String? = null,
): List<MediaItem> =
  if (!disableVideos) this
  else
    filterNot { item ->
      val meta = item.metadata
      item.mediaId != keepMediaId &&
        meta?.isVideoSong == true &&
        meta.musicVideoType != MUSIC_VIDEO_TYPE_PODCAST_EPISODE
    }
