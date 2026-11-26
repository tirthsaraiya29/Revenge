package com.revenge.music.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.innertube.pages.PodcastPage
import com.revenge.music.extensions.toMediaItem
import com.revenge.music.models.MediaMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A YouTube watch/radio queue. The endpoint evolves as pages are fetched, and radio pages are
 * deduplicated across the whole session so continuations can never replay a track.
 */
class YouTubeQueue(
  private var endpoint: WatchEndpoint,
  override val preloadItem: MediaMetadata? = null,
) : Queue {
  private var continuation: String? = null
  private val radioMode = endpoint.playlistId?.startsWith("RD") != false
  private val radioFilter = RadioQueuePageFilter()
  private var episodeRadio = false

  override suspend fun getInitialStatus(): Queue.Status =
    withContext(Dispatchers.IO) {
      continuation = null
      radioFilter.reset()

      val addon = endpoint.videoId?.startsWith("addon_") == true
      val addonItem = if (addon) preloadItem else null
      if (addon) {
        resolveAddonEndpoint()
      }

      var lastError: Throwable? = null
      for (attempt in 0..MAX_RETRIES) {
        try {
          val page = YouTube.next(endpoint, continuation).getOrThrow()
          endpoint = page.endpoint
          continuation = page.continuation

          val current = page.items.getOrNull(page.currentIndex ?: 0)
          episodeRadio = current?.isPodcastEpisode == true
          if (
            episodeRadio &&
              preloadItem?.artists?.any { PodcastPage.isShowBrowseId(it.id) } == true
          ) {
            // A known show continues with its next episode, never with a music radio.
            continuation = null
            return@withContext Queue.Status(
              title = page.title,
              items = listOfNotNull(preloadItem.toMediaItem()),
              mediaItemIndex = 0,
            )
          }

          val deduped = if (radioMode) radioFilter.filter(page.items) else page.items
          val songs = if (episodeRadio) deduped.filter { it.isPodcastEpisode } else deduped
          val items = songs.map { it.toMediaItem() }.toMutableList()
          if (addonItem != null) {
            if (items.isEmpty()) items.add(addonItem.toMediaItem())
            else items[0] = addonItem.toMediaItem()
          }

          return@withContext Queue.Status(
            title = page.title,
            items = items,
            mediaItemIndex =
              if (addon) 0 else songs.indexOfFirst { it.id == current?.id }.coerceAtLeast(0),
          )
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          lastError = e
          // A bare video id may not expose a radio; retry once as an RDAMVM mix.
          if (attempt == 0 && endpoint.videoId != null && endpoint.playlistId == null) {
            endpoint =
              WatchEndpoint(videoId = endpoint.videoId, playlistId = "RDAMVM${endpoint.videoId}")
          }
        }
      }

      if (addonItem != null) {
        return@withContext Queue.Status(
          title = addonItem.title,
          items = listOf(addonItem.toMediaItem()),
          mediaItemIndex = 0,
        )
      }
      throw lastError ?: IllegalStateException("Failed to get initial status")
    }

  override fun hasNextPage(): Boolean = continuation != null

  override suspend fun nextPage(): List<MediaItem> =
    withContext(Dispatchers.IO) {
      val visited = HashSet<String>()

      pageLoop@ for (hop in 0 until MAX_PAGE_HOPS) {
        val active = continuation ?: return@withContext emptyList()
        if (!visited.add(active)) {
          continuation = null
          return@withContext emptyList()
        }

        for (attempt in 0..MAX_RETRIES) {
          try {
            val page = YouTube.next(endpoint, active).getOrThrow()
            endpoint = page.endpoint
            // A page that points back at itself would loop forever.
            continuation = page.continuation?.takeUnless { it == active }

            val deduped = if (radioMode) radioFilter.filter(page.items) else page.items
            val songs = if (episodeRadio) deduped.filter { it.isPodcastEpisode } else deduped
            if (songs.isNotEmpty()) {
              return@withContext songs.map { it.toMediaItem() }
            }
            continue@pageLoop // Everything on this page was already seen; ask for the next one.
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            if (attempt == MAX_RETRIES) throw e
          }
        }
      }
      emptyList()
    }

  private suspend fun resolveAddonEndpoint() {
    try {
      val query =
        listOfNotNull(preloadItem?.title, preloadItem?.artists?.firstOrNull()?.name)
          .filter { it.isNotBlank() }
          .joinToString(" ")
      val match =
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
          .getOrNull()
          ?.items
          ?.filterIsInstance<SongItem>()
          ?.firstOrNull()
      if (match != null) {
        endpoint = WatchEndpoint(videoId = match.id, playlistId = "RDAMVM${match.id}")
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  companion object {
    private const val MAX_RETRIES = 3
    private const val MAX_PAGE_HOPS = 5

    fun radio(song: MediaMetadata): YouTubeQueue =
      YouTubeQueue(WatchEndpoint(videoId = song.id), song)
  }
}
