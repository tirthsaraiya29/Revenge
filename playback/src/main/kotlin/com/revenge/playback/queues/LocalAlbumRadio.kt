package com.revenge.playback.queues

import androidx.media3.common.MediaItem
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.music.db.AlbumWithSongs
import com.revenge.music.extensions.toMediaItem
import com.revenge.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAlbumRadio(
    private val albumWithSongs: AlbumWithSongs,
    private val startIndex: Int = 0,
) : Queue {

    private var continuation: String? = null
    private var loadedOnce: Boolean = false
    private var albumPlaylistId: String? = null

    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus(): Queue.Status = withContext(Dispatchers.IO) {
        Queue.Status(
            title = albumWithSongs.album.title,
            items = albumWithSongs.songs.map { it.toMediaItem() },
            mediaItemIndex = startIndex,
        )
    }

    override fun hasNextPage(): Boolean = !loadedOnce || continuation != null

    override suspend fun nextPage(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!loadedOnce) {
            val albumPage = YouTube.album(albumWithSongs.album.id).getOrThrow()
            albumPlaylistId = albumPage.album.playlistId
            val page = YouTube.next(
                WatchEndpoint(playlistId = albumPage.album.playlistId),
                continuation,
            ).getOrThrow()
            continuation = page.continuation
            loadedOnce = true
            page.items.drop(albumWithSongs.songs.size).map { it.toMediaItem() }
        } else {
            val page = YouTube.next(
                WatchEndpoint(playlistId = albumPlaylistId),
                continuation,
            ).getOrThrow()
            continuation = page.continuation
            page.items.map { it.toMediaItem() }
        }
    }
}
