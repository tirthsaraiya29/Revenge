package com.revenge.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.core.db.MusicDatabase
import com.revenge.core.db.entities.Song
import com.revenge.core.extensions.toMediaItem
import com.revenge.core.models.MediaMetadata
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.WatchEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class LocalMixQueue(
  private val database: MusicDatabase,
  private val playlistId: String,
  private val maxMixSize: Int = 500,
) : Queue {

  override val preloadItem: MediaMetadata? = null

  override suspend fun getInitialStatus(): Queue.Status =
    withContext(Dispatchers.IO) {
      val playlist = database.playlistSongs(playlistId).first()
      val playlistIds = playlist.mapTo(HashSet()) { it.song.id }

      val localMix = buildLocalMix(playlist, playlistIds)
      if (localMix.isNotEmpty()) {
        return@withContext Queue.Status(
          title = MIX_TITLE,
          items = localMix.map { it.toMediaItem() },
          mediaItemIndex = 0,
        )
      }
      if (playlist.isEmpty()) {
        return@withContext Queue.Status(title = MIX_TITLE, items = emptyList(), mediaItemIndex = 0)
      }

      val seedId = playlist.first().song.id
      val remote = buildRemoteMix(seedId, playlistIds)
      Queue.Status(
        title = MIX_TITLE,
        items = remote.map { it.toMediaItem() },
        mediaItemIndex = 0,
      )
    }

  override fun hasNextPage(): Boolean = false

  override suspend fun nextPage(): List<MediaItem> = emptyList()

  private fun buildLocalMix(
    playlist: List<com.revenge.core.db.entities.PlaylistSong>,
    playlistIds: Set<String>,
  ): List<Song> {
    val seen = HashSet<String>()
    val result = ArrayList<Song>()
    for (entry in playlist) {
      for (related in database.relatedSongs(entry.song.id)) {
        if (related.id in playlistIds) continue
        if (!seen.add(related.id)) continue
        result.add(related)
        if (result.size >= maxMixSize) return result
      }
    }
    return result
  }

  private suspend fun buildRemoteMix(seedId: String, playlistIds: Set<String>): List<SongItem> {
    val songs =
      try {
        val next = YouTube.next(WatchEndpoint(videoId = seedId)).getOrThrow()
        val related = next.relatedEndpoint?.let { YouTube.related(it).getOrThrow().songs }.orEmpty()
        next.items + related
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        return emptyList()
      }

    val seen = HashSet<String>()
    val result = ArrayList<SongItem>()
    for (song in songs) {
      if (song.id in playlistIds) continue
      if (!seen.add(song.id)) continue
      result.add(song)
      if (result.size >= maxMixSize) break
    }
    return result
  }

  private companion object {
    const val MIX_TITLE = "Mix from Playlist"
  }
}
