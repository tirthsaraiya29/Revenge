package com.revenge.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.core.extensions.toMediaItem
import com.revenge.core.models.MediaMetadata
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.innertube.pages.NextResult
import com.revenge.innertube.pages.PodcastPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubeQueue(
  private var endpoint: WatchEndpoint,
  override val preloadItem: MediaMetadata? = null,
) : Queue {

  private var continuation: String? = null
  private val pageFilter = RadioQueuePageFilter()

  private val radioMode: Boolean
    get() = endpoint.playlistId?.startsWith("RD") != false

  override suspend fun getInitialStatus(): Queue.Status {
    continuation = null
    pageFilter.reset()

    val addonRequest = preloadItem != null && endpoint.videoId?.startsWith("addon_") == true
    if (addonRequest) resolveAddonEndpoint()

    val page =
      try {
        fetchInitialPage()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        if (!addonRequest) throw e
        val preload = preloadItem ?: throw e
        return Queue.Status(
          title = preload.title,
          items = listOf(preload.toMediaItem()),
          mediaItemIndex = 0,
        )
      }
    return buildStatus(page, addonRequest)
  }

  override fun hasNextPage(): Boolean = continuation != null

  override suspend fun nextPage(): List<MediaItem> {
    var token = continuation ?: return emptyList()
    val used = HashSet<String>()
    var fetches = 0

    while (fetches < MAX_PAGE_FETCHES) {
      if (!used.add(token)) {
        continuation = null
        return emptyList()
      }
      fetches++

      val page = fetchPage(token)
      val nextToken = page.continuation
      continuation = nextToken
      if (nextToken != null && nextToken in used) {
        continuation = null
        return emptyList()
      }

      val items = mapPageItems(page)
      if (items.isNotEmpty()) return items
      token = nextToken ?: return emptyList()
    }
    return emptyList()
  }

  private suspend fun resolveAddonEndpoint() {
    val preload = preloadItem ?: return
    val query = buildString {
      append(preload.title)
      preload.artists.firstOrNull()?.name?.let {
        if (it.isNotBlank()) append(' ').append(it)
      }
    }
    val song = queryFirstSong(query) ?: return
    endpoint = song.endpoint ?: WatchEndpoint(videoId = song.id)
  }

  private suspend fun queryFirstSong(query: String): SongItem? =
    withContext(Dispatchers.IO) {
      try {
        val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
        result.getOrNull()?.items?.firstOrNull { it is SongItem } as? SongItem
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        null
      }
    }

  private suspend fun fetchInitialPage(): NextResult =
    withContext(Dispatchers.IO) {
      var lastError: Throwable? = null
      var rewritten = false
      for (attempt in 0 until MAX_FETCH_ATTEMPTS) {
        val result =
          try {
            YouTube.next(endpoint, null)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Throwable) {
            Result.failure<NextResult>(e)
          }

        if (result.isSuccess) {
          val page = result.getOrThrow()
          endpoint = page.endpoint
          continuation = page.continuation
          return@withContext page
        }

        val error = result.exceptionOrNull() ?: RuntimeException("Initial page failed")
        if (error is CancellationException) throw error
        lastError = error

        if (attempt == 0 && !rewritten) {
          val videoId = endpoint.videoId
          if (videoId != null && endpoint.playlistId == null) {
            endpoint = WatchEndpoint(videoId = videoId, playlistId = "RDAMVM$videoId")
            rewritten = true
          }
        }
      }
      throw lastError ?: IllegalStateException("Failed to get initial status")
    }

  private suspend fun fetchPage(token: String): NextResult =
    withContext(Dispatchers.IO) {
      var lastError: Throwable? = null
      for (attempt in 0 until MAX_FETCH_ATTEMPTS) {
        val result =
          try {
            YouTube.next(endpoint, token)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Throwable) {
            Result.failure<NextResult>(e)
          }

        if (result.isSuccess) {
          val page = result.getOrThrow()
          endpoint = page.endpoint
          return@withContext page
        }

        val error = result.exceptionOrNull() ?: RuntimeException("Next page failed")
        if (error is CancellationException) throw error
        lastError = error
      }
      throw lastError ?: IllegalStateException("Failed to get next page")
    }

  private fun buildStatus(page: NextResult, addonRequest: Boolean): Queue.Status {
    val current = page.currentIndex?.let { page.items.getOrNull(it) }
    val currentId = current?.id

    if (current?.isPodcastEpisode == true && hasPodcastShowInPreload()) {
      continuation = null
      val preload = preloadItem!!
      return Queue.Status(
        title = page.title,
        items = listOf(preload.toMediaItem()),
        mediaItemIndex = 0,
      )
    }

    val songs = filteredSongs(page, current?.isPodcastEpisode == true)
    val mapped = songs.map { it.toMediaItem() }

    if (addonRequest) {
      val preload = preloadItem!!
      val items =
        if (mapped.isEmpty()) {
          listOf(preload.toMediaItem())
        } else {
          listOf(preload.toMediaItem()) + mapped
        }
      return Queue.Status(title = page.title, items = items, mediaItemIndex = 0)
    }

    val selection =
      if (currentId == null) {
        0
      } else {
        mapped.indexOfFirst { it.mediaId == currentId }.coerceAtLeast(0)
      }
    return Queue.Status(title = page.title, items = mapped, mediaItemIndex = selection)
  }

  private fun mapPageItems(page: NextResult): List<MediaItem> {
    val current = page.currentIndex?.let { page.items.getOrNull(it) }
    val songs = filteredSongs(page, current?.isPodcastEpisode == true)
    return songs.map { it.toMediaItem() }
  }

  private fun filteredSongs(page: NextResult, podcastEpisode: Boolean): List<SongItem> {
    if (!radioMode) return page.items
    var songs = pageFilter.filter(page.items)
    if (podcastEpisode) songs = songs.filter { it.isPodcastEpisode }
    return songs
  }

  private fun hasPodcastShowInPreload(): Boolean =
    preloadItem?.artists?.any { PodcastPage.isShowBrowseId(it.id) } == true

  companion object {
    private const val MAX_FETCH_ATTEMPTS = 4
    private const val MAX_PAGE_FETCHES = 5

    fun radio(song: MediaMetadata): YouTubeQueue =
      YouTubeQueue(WatchEndpoint(videoId = song.id), song)
  }
}
