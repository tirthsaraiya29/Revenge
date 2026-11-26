package com.revenge.music.spotify

import androidx.media3.common.MediaItem
import com.revenge.innertube.models.SongItem
import com.revenge.music.extensions.toMediaItem
import com.revenge.music.models.MediaMetadata
import com.revenge.music.playback.queues.Queue
import com.revenge.music.spotify.models.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A queue over Spotify tracks. Only a window around the selected occurrence is resolved eagerly;
 * the tail is paged in on demand so a large playlist does not stall playback start.
 */
class SpotifyQueue(
  private val title: String? = null,
  private val tracks: List<SpotifyTrack>,
  startIndex: Int = 0,
  override val preloadItem: MediaMetadata? = null,
  private val resolver: SpotifyPlaybackResolver,
) : Queue {

  private companion object {
    const val WINDOW_BEFORE = 10
    const val WINDOW_AFTER = 20
    const val PAGE_SIZE = 25
  }

  private val safeStartIndex: Int =
    if (tracks.isEmpty()) 0 else startIndex.coerceIn(0, tracks.lastIndex)

  private val windowStart = (safeStartIndex - WINDOW_BEFORE).coerceAtLeast(0)
  private val windowEnd = (safeStartIndex + WINDOW_AFTER).coerceAtMost(tracks.size)

  private val pending: ArrayDeque<SpotifyTrack> =
    ArrayDeque(tracks.drop(windowEnd).map { it })

  private var initialized = false

  override suspend fun getInitialStatus(): Queue.Status =
    withContext(Dispatchers.IO) {
      if (tracks.isEmpty()) {
        initialized = true
        return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
      }

      val items = mutableListOf<MediaItem>()
      var selected = -1
      for (index in windowStart until windowEnd) {
        val track = tracks[index]
        val resolved = resolver.resolveTrack(track)
        if (index == safeStartIndex) {
          val selectedItem = resolved?.toMediaItem() ?: preloadItem?.toMediaItem()
          if (selectedItem != null) {
            items += selectedItem
            selected = items.lastIndex
          }
        } else if (resolved != null) {
          items += resolved.toMediaItem()
        }
      }
      initialized = true
      Queue.Status(
        title = title,
        items = items,
        mediaItemIndex = if (items.isEmpty()) 0 else selected.coerceIn(0, items.lastIndex),
      )
    }

  override fun hasNextPage(): Boolean = !initialized || pending.isNotEmpty()

  fun hasPendingSongs(): Boolean = !initialized || pending.isNotEmpty()

  override suspend fun nextPage(): List<MediaItem> =
    withContext(Dispatchers.IO) {
      if (pending.isEmpty()) return@withContext emptyList()
      val page = mutableListOf<SpotifyTrack>()
      while (page.size < PAGE_SIZE && pending.isNotEmpty()) {
        page += pending.removeFirst()
      }
      page.mapNotNull { track: SpotifyTrack -> resolver.resolveTrack(track)?.toMediaItem() }
    }
}
