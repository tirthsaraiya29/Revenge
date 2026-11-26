package com.revenge.music.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.music.db.entities.AlbumWithSongs
import com.revenge.music.extensions.toMediaItem
import com.revenge.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Plays a locally stored album and continues with its YouTube radio after the last song. */
class LocalAlbumRadio(
  private val albumWithSongs: AlbumWithSongs,
  private val startIndex: Int = 0,
) : Queue {
  override val preloadItem: MediaMetadata? = null

  private var playlistId: String? = null
  private var continuation: String? = null
  private var loadedOnce = false

  private val endpoint: WatchEndpoint
    get() = WatchEndpoint(playlistId = playlistId)

  override suspend fun getInitialStatus(): Queue.Status =
    withContext(Dispatchers.IO) {
      Queue.Status(
        title = albumWithSongs.album.title,
        items = albumWithSongs.songs.map { it.toMediaItem() },
        mediaItemIndex = startIndex,
      )
    }

  override fun hasNextPage(): Boolean = !loadedOnce || continuation != null

  override suspend fun nextPage(): List<MediaItem> =
    withContext(Dispatchers.IO) {
      if (!loadedOnce) {
        val resolvedPlaylistId = YouTube.album(albumWithSongs.album.id).getOrThrow().album.playlistId
        playlistId = resolvedPlaylistId
        val page = YouTube.next(endpoint, continuation).getOrThrow()
        continuation = page.continuation
        loadedOnce = true
        // The album itself is served locally; only the radio tail is queued remotely.
        page.items.drop(albumWithSongs.songs.size).map { it.toMediaItem() }
      } else {
        val page = YouTube.next(endpoint, continuation).getOrThrow()
        continuation = page.continuation
        page.items.map { it.toMediaItem() }
      }
    }
}
