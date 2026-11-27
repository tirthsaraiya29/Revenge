package com.revenge.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.core.extensions.toMediaItem
import com.revenge.core.models.MediaMetadata
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubeAlbumRadio(
  private var playlistId: String,
) : Queue {

  private var continuation: String? = null
  private var loadedOnce: Boolean = false
  private var albumSongCount: Int = 0

  override val preloadItem: MediaMetadata? = null

  override suspend fun getInitialStatus(): Queue.Status {
    val songs =
      withContext(Dispatchers.IO) {
        YouTube.albumSongs(playlistId).getOrThrow()
      }
    albumSongCount = songs.size
    continuation = null
    loadedOnce = false
    return Queue.Status(
      title = songs.firstOrNull()?.album?.name.orEmpty(),
      items = songs.map { it.toMediaItem() },
      mediaItemIndex = 0,
    )
  }

  override fun hasNextPage(): Boolean = !loadedOnce || continuation != null

  override suspend fun nextPage(): List<MediaItem> =
    withContext(Dispatchers.IO) {
      val page =
        YouTube.next(
            WatchEndpoint(playlistId = playlistId),
            continuation,
          )
          .getOrThrow()
      continuation = page.continuation
      val songs =
        if (!loadedOnce) {
          loadedOnce = true
          page.items.drop(albumSongCount)
        } else {
          page.items
        }
      songs.map { it.toMediaItem() }
    }
}
