package com.revenge.music.extensions

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import com.revenge.innertube.models.SongItem
import com.revenge.music.db.entities.Song
import com.revenge.music.models.MediaMetadata
import com.revenge.music.models.toMediaMetadata
import com.revenge.music.ui.utils.resize
import java.util.Locale

val MediaItem.metadata: MediaMetadata?
  get() =
    (localConfiguration?.tag as? MediaMetadata)
      ?: run {
        val meta = mediaMetadata
        val title = (meta.title ?: meta.displayTitle)?.toString()
        if (title.isNullOrBlank() && mediaId.isBlank()) {
          null
        } else {
          MediaMetadata(
            id = mediaId,
            title = title.orEmpty(),
            artists =
              listOfNotNull(
                meta.artist
                  ?.toString()
                  ?.takeIf { it.isNotBlank() }
                  ?.let { MediaMetadata.Artist(id = null, name = it) },
              ),
            duration = 0,
            thumbnailUrl = meta.artworkUri?.toString() ?: meta.extras?.getString("artwork_uri"),
          )
        }
      }

/**
 * A media id may already be a playable URI (local files, downloads) or a bare YouTube video id.
 * Bare ids get a YouTube watch URL so ExoPlayer has something resolvable before the real stream
 * URL is selected by the resolver.
 */
private fun playbackSeedUri(mediaId: String): String {
  val scheme = mediaId.toUri().scheme?.lowercase(Locale.US)
  return when (scheme) {
    "content",
    "file",
    "android.resource",
    "http",
    "https" -> mediaId
    else -> "https://music.youtube.com/watch?v=${Uri.encode(mediaId)}"
  }
}

private fun mediaMetadataBuilder(
  title: String,
  subtitle: String,
  artworkUri: String?,
  albumTitle: String?,
  albumArtist: String?,
): androidx.media3.common.MediaMetadata.Builder =
  androidx.media3.common.MediaMetadata.Builder()
    .setTitle(title)
    .setSubtitle(subtitle)
    .setArtist(subtitle)
    .setArtworkUri(artworkUri?.toUri())
    .setAlbumTitle(albumTitle)
    .setAlbumArtist(albumArtist)
    .setDisplayTitle(title)
    .setMediaType(MEDIA_TYPE_MUSIC)
    .setIsBrowsable(false)
    .setIsPlayable(true)
    .setExtras(Bundle().apply { artworkUri?.let { putString("artwork_uri", it) } })

fun Song.toMediaItem() =
  MediaItem.Builder()
    .setMediaId(song.id)
    .setUri(playbackSeedUri(song.id))
    .setCustomCacheKey(song.id)
    .setTag(toMediaMetadata())
    .setMediaMetadata(
      mediaMetadataBuilder(
        title = song.title,
        subtitle = artists.joinToString { it.name },
        artworkUri = song.thumbnailUrl,
        albumTitle = song.albumName,
        albumArtist = artists.firstOrNull()?.name,
      )
        .build(),
    )
    .build()

fun SongItem.toMediaItem(): MediaItem {
  val artwork = thumbnail.resize(1200, 1200)
  return MediaItem.Builder()
    .setMediaId(id)
    .setUri(playbackSeedUri(id))
    .setCustomCacheKey(id)
    .setTag(toMediaMetadata())
    .setMediaMetadata(
      mediaMetadataBuilder(
        title = title,
        subtitle = artists.joinToString { it.name },
        artworkUri = artwork,
        albumTitle = album?.name,
        albumArtist = artists.firstOrNull()?.name,
      )
        .build(),
    )
    .build()
}

fun MediaMetadata.toMediaItem() =
  MediaItem.Builder()
    .setMediaId(id)
    .setUri(playbackSeedUri(id))
    .setCustomCacheKey(id)
    .setTag(this)
    .setMediaMetadata(
      mediaMetadataBuilder(
        title = title,
        subtitle = artists.joinToString { it.name },
        artworkUri = thumbnailUrl,
        albumTitle = album?.title,
        albumArtist = artists.firstOrNull()?.name,
      )
        .build(),
    )
    .build()
