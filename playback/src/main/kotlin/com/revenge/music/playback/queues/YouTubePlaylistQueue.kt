package com.revenge.music.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.SongItem
import com.revenge.music.extensions.toMediaItem
import com.revenge.music.models.MediaMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A YouTube playlist that starts from items the playlist screen already loaded, handing the rest out
 * in chunks and then following the continuation. Keeping only a window around the selected song
 * avoids materialising thousands of items for endless personal mixes.
 */
class YouTubePlaylistQueue(
  private val playlistId: String,
  private val playlistTitle: String? = null,
  private val initialSongs: List<SongItem> = emptyList(),
  private val initialContinuation: String? = null,
  private val startIndex: Int = 0,
  override val preloadItem: MediaMetadata? = null,
) : Queue {
  private var continuation: String? = initialContinuation
  private var failedAttempts = 0
  private var pendingSongs: List<SongItem> = emptyList()

  override suspend fun getInitialStatus(): Queue.Status =
    withContext(Dispatchers.IO) {
      if (initialSongs.isEmpty()) {
        val page = YouTube.playlist(playlistId).getOrThrow()
        continuation = page.songsContinuation
        Queue.Status(
          title = page.playlist.title,
          items = page.songs.map { it.toMediaItem() },
          mediaItemIndex = startIndex,
        )
      } else {
        val first = (startIndex - WINDOW_BEFORE).coerceAtLeast(0)
        val end = (startIndex + WINDOW_AFTER).coerceAtMost(initialSongs.size)
        pendingSongs = initialSongs.subList(end, initialSongs.size)
        Queue.Status(
          title = playlistTitle,
          items = initialSongs.subList(first, end).map { it.toMediaItem() },
          mediaItemIndex = startIndex - first,
        )
      }
    }

  override fun hasNextPage(): Boolean = pendingSongs.isNotEmpty() || continuation != null

  /** Whether locally loaded playlist songs still have to be queued, even with auto-load off. */
  fun hasPendingSongs(): Boolean = pendingSongs.isNotEmpty()

  override suspend fun nextPage(): List<MediaItem> {
    if (pendingSongs.isNotEmpty()) {
      val chunk = pendingSongs.take(PAGE_SIZE)
      pendingSongs = pendingSongs.drop(PAGE_SIZE)
      return chunk.map { it.toMediaItem() }
    }

    return withContext(Dispatchers.IO) {
      val active = continuation ?: return@withContext emptyList()
      var lastError: Throwable? = null

      repeat(MAX_RETRIES + 1) {
        try {
          val page = YouTube.playlistContinuation(active).getOrThrow()
          continuation = page.continuation
          failedAttempts = 0
          return@withContext page.songs.map { it.toMediaItem() }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          lastError = e
          failedAttempts++
          if (failedAttempts >= MAX_RETRIES) continuation = null
        }
      }
      throw lastError ?: IllegalStateException("Playlist continuation failed")
    }
  }

  private companion object {
    /** Songs kept before the selected one so "previous" still has something to play. */
    const val WINDOW_BEFORE = 20

    /** Songs queued up front after the selected one. */
    const val WINDOW_AFTER = 100

    /** Songs appended per load-more step from the already loaded list. */
    const val PAGE_SIZE = 50

    const val MAX_RETRIES = 3
  }
}
