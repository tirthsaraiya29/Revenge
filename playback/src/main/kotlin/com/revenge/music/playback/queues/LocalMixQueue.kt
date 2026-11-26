package com.revenge.music.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.music.db.MusicDatabase
import com.revenge.music.extensions.toMediaItem
import com.revenge.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Builds an endless-feeling mix seeded from the related songs of a local playlist. */
class LocalMixQueue(
  private val database: MusicDatabase,
  private val playlistId: String,
  private val maxMixSize: Int = 500,
) : Queue {
  override val preloadItem: MediaMetadata? = null

  override suspend fun getInitialStatus(): Queue.Status =
    withContext(Dispatchers.IO) {
      val playlistSongIds = database.playlistSongs(playlistId).first().map { it.map.songId }

      // First choice: songs already known to be related to this playlist, offline.
      val localMix =
        playlistSongIds
          .flatMap { database.relatedSongs(it) }
          .filter { song -> song.id !in playlistSongIds }
          .distinctBy { it.id }
          .take(maxMixSize)

      if (localMix.isNotEmpty()) {
        return@withContext Queue.Status(
          title = MIX_TITLE,
          items = localMix.map { it.toMediaItem() },
          mediaItemIndex = 0,
        )
      }

      val seedSongId = playlistSongIds.firstOrNull()
      if (seedSongId == null) {
        return@withContext Queue.Status(title = MIX_TITLE, items = emptyList(), mediaItemIndex = 0)
      }

      // Nothing local: ask YouTube for a watch-next page and its related shelf.
      val next = YouTube.next(WatchEndpoint(videoId = seedSongId)).getOrNull()
      val fromNext =
        next
          ?.items
          ?.map { it.toMediaItem() }
          ?.filter { it.mediaId !in playlistSongIds }
          .orEmpty()
      val fromRelated =
        next
          ?.relatedEndpoint
          ?.let { endpoint ->
            YouTube.related(endpoint)
              .getOrNull()
              ?.songs
              ?.map { it.toMediaItem() }
              ?.filter { it.mediaId !in playlistSongIds }
          }
          .orEmpty()

      Queue.Status(
        title = MIX_TITLE,
        items = (fromNext + fromRelated).distinctBy { it.mediaId }.take(maxMixSize),
        mediaItemIndex = 0,
      )
    }

  override fun hasNextPage(): Boolean = false

  override suspend fun nextPage(): List<MediaItem> = emptyList()

  private companion object {
    const val MIX_TITLE = "Mix from Playlist"
  }
}
