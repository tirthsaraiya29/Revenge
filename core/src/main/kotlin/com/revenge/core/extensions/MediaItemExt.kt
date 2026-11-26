package com.revenge.core.extensions

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as Media3Metadata
import com.revenge.innertube.models.SongItem
import com.revenge.core.db.entities.Song
import com.revenge.core.models.MediaMetadata
import com.revenge.core.models.toMediaMetadata
import com.revenge.core.ui.utils.resize
import java.util.Locale

private val DIRECT_URI_SCHEMES = setOf("content", "file", "android.resource", "http", "https")
private const val ARTWORK_URI_EXTRA = "artwork_uri"
private const val WATCH_URL_PREFIX = "https://music.youtube.com/watch?v="

private fun seedUri(id: String): String {
    val scheme = Uri.parse(id).scheme?.lowercase(Locale.US)
    return if (scheme != null && scheme in DIRECT_URI_SCHEMES) id else WATCH_URL_PREFIX + Uri.encode(id)
}

private fun assemble(
    metadata: MediaMetadata,
    artwork: String?,
    albumTitle: String?,
    artistText: String,
): MediaItem {
    val mediaMetadata = Media3Metadata.Builder()
        .setTitle(metadata.title)
        .setSubtitle(artistText)
        .setArtist(artistText)
        .setAlbumTitle(albumTitle)
        .setAlbumArtist(metadata.artists.firstOrNull()?.name)
        .setDisplayTitle(metadata.title)
        .setMediaType(Media3Metadata.MEDIA_TYPE_MUSIC)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .apply {
            if (artwork != null) {
                setArtworkUri(Uri.parse(artwork))
                setExtras(Bundle().apply { putString(ARTWORK_URI_EXTRA, artwork) })
            }
        }
        .build()

    return MediaItem.Builder()
        .setMediaId(metadata.id)
        .setUri(seedUri(metadata.id))
        .setCustomCacheKey(metadata.id)
        .setTag(metadata)
        .setMediaMetadata(mediaMetadata)
        .build()
}

val MediaItem.metadata: MediaMetadata?
    get() {
        val tag = localConfiguration?.tag
        if (tag is MediaMetadata) return tag

        val source = mediaMetadata
        val title = (source.title ?: source.displayTitle)?.toString()
        if (title.isNullOrBlank() && mediaId.isBlank()) return null
        val artist = source.artist?.toString()
        return MediaMetadata(
            id = mediaId,
            title = title.orEmpty(),
            artists = if (!artist.isNullOrBlank()) listOf(MediaMetadata.Artist(null, artist)) else emptyList(),
            duration = 0,
            thumbnailUrl = source.artworkUri?.toString() ?: source.extras?.getString(ARTWORK_URI_EXTRA),
        )
    }

fun Song.toMediaItem(): MediaItem {
    val metadata = toMediaMetadata()
    return assemble(
        metadata = metadata,
        artwork = thumbnailUrl,
        albumTitle = song.albumName,
        artistText = artists.joinToString(", ") { it.name },
    )
}

fun SongItem.toMediaItem(): MediaItem {
    val metadata = toMediaMetadata()
    return assemble(
        metadata = metadata,
        artwork = thumbnail.resize(1200, 1200),
        albumTitle = album?.name,
        artistText = artists.joinToString(", ") { it.name },
    )
}

fun MediaMetadata.toMediaItem(): MediaItem = assemble(
    metadata = this,
    artwork = thumbnailUrl,
    albumTitle = album?.title,
    artistText = artists.joinToString(", ") { it.name },
)
