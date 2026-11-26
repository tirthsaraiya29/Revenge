package com.revenge.music.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.music.extensions.toMediaItem
import com.revenge.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Queues a YouTube Music album and then its radio continuation. */
class YouTubeAlbumRadio(private var playlistId: String) : Queue {
  override val preloadItem: MediaMetadata? = null

  private var albumSongCount = 0
  private var continuation: String? = null
  private var loadedOnce = false

  private val endpoint: WatchEndpoint
    get() = WatchEndpoint(playlistId = playlistId)

  override suspend fun getInitialStatus(): Queue.Status =
    withContext(Dispatchers.IO) {
      val songs = YouTube.albumSongs(playlistId).getOrThrow()
      albumSongCount = songs.size
      Queue.Status(
        title = songs.firstOrNull()?.album?.name.orEmpty(),
        items = songs.map { it.toMediaItem() },
        mediaItemIndex = 0,
      )
    }

  override fun hasNextPage(): Boolean = !loadedOnce || continuation != null

  override suspend fun nextPage(): List<MediaItem> =
    withContext(Dispatchers.IO) {
      val page = YouTube.next(endpoint, continuation).getOrThrow()
      continuation = page.continuation
      if (!loadedOnce) {
        loadedOnce = true
        // The first next-page repeats the album tracks; only the tail is new.
        page.items.drop(albumSongCount).map { it.toMediaItem() }
      } else {
        page.items.map { it.toMediaItem() }
      }
    }
}
