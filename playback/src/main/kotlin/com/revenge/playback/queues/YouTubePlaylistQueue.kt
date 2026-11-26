package com.revenge.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.pages.PlaylistContinuationPage
import com.revenge.innertube.pages.PlaylistPage
import com.revenge.music.extensions.toMediaItem
import com.revenge.music.models.MediaMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubePlaylistQueue(
    private val playlistId: String,
    private val playlistTitle: String? = null,
    private val initialSongs: List<SongItem> = emptyList(),
    private val initialContinuation: String? = null,
    private val startIndex: Int = 0,
    override val preloadItem: MediaMetadata? = null,
) : Queue {

    internal var playlistLoader: suspend (String) -> Result<PlaylistPage> = { YouTube.playlist(it) }
    internal var continuationLoader: suspend (String) -> Result<PlaylistContinuationPage> = {
        YouTube.playlistContinuation(it)
    }

    private var pendingSongs: MutableList<SongItem> = mutableListOf()
    private var continuation: String? = null
    private var consecutiveFailures: Int = 0

    override suspend fun getInitialStatus(): Queue.Status {
        if (initialSongs.isEmpty()) {
            val page = withContext(Dispatchers.IO) { playlistLoader(playlistId).getOrThrow() }
            continuation = page.songsContinuation
            pendingSongs = mutableListOf()
            return Queue.Status(
                title = page.playlist.title,
                items = page.songs.map { it.toMediaItem() },
                mediaItemIndex = startIndex,
            )
        }

        val first = (startIndex - WINDOW_BEFORE).coerceAtLeast(0)
        val end = (startIndex + WINDOW_AFTER).coerceAtMost(initialSongs.size)
        pendingSongs = initialSongs.subList(end, initialSongs.size).toMutableList()
        continuation = initialContinuation
        return Queue.Status(
            title = playlistTitle,
            items = initialSongs.subList(first, end).map { it.toMediaItem() },
            mediaItemIndex = startIndex - first,
        )
    }

    override fun hasNextPage(): Boolean = pendingSongs.isNotEmpty() || continuation != null

    fun hasPendingSongs(): Boolean = pendingSongs.isNotEmpty()

    override suspend fun nextPage(): List<MediaItem> {
        if (pendingSongs.isNotEmpty()) {
            val count = minOf(PAGE_SIZE, pendingSongs.size)
            val chunk = pendingSongs.take(count)
            pendingSongs.subList(0, count).clear()
            return chunk.map { it.toMediaItem() }
        }

        val token = continuation ?: return emptyList()
        return withContext(Dispatchers.IO) {
            var lastError: Throwable? = null
            for (attempt in 0 until MAX_RETRIES + 1) {
                val result = try {
                    continuationLoader(token)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Result.failure<PlaylistContinuationPage>(e)
                }

                if (result.isSuccess) {
                    val page = result.getOrThrow()
                    continuation = page.continuation
                    consecutiveFailures = 0
                    return@withContext page.songs.map { it.toMediaItem() }
                }

                val error = result.exceptionOrNull() ?: RuntimeException("Continuation failed")
                if (error is CancellationException) throw error
                lastError = error
                consecutiveFailures++
                if (consecutiveFailures >= MAX_RETRIES) continuation = null
            }
            throw lastError ?: IllegalStateException("Playlist continuation failed")
        }
    }

    companion object {
        const val WINDOW_BEFORE = 20
        const val WINDOW_AFTER = 100
        const val PAGE_SIZE = 50
        const val MAX_RETRIES = 3
    }
}
