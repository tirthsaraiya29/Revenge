package com.revenge.core.mapping

import com.revenge.core.db.entities.Playlist
import com.revenge.core.db.entities.PlaylistEntity
import com.revenge.core.db.entities.Song
import com.revenge.core.db.entities.SongEntity
import com.revenge.domain.models.AlbumRef
import com.revenge.domain.models.ArtistRef
import com.revenge.domain.models.Playlist as DomainPlaylist
import com.revenge.domain.models.Song as DomainSong

private const val LOCAL_ALBUM_PREFIX = "LOCAL_ALBUM_"
private const val LOCAL_ALBUM_ART_URI = "content://media/external/audio/albumart/"

fun Song.toDomain(): DomainSong =
  DomainSong(
    id = id,
    title = title,
    artists = artists.map { ArtistRef(id = it.id, name = it.name) },
    album =
      album?.let { AlbumRef(id = it.id, title = it.title) }
        ?: song.albumId?.let { AlbumRef(id = it, title = song.albumName.orEmpty()) },
    durationSeconds = song.duration,
    thumbnailUrl = thumbnailUrl,
    isExplicit = song.explicit,
    isLocal = song.isLocal,
    liked = song.liked,
    totalPlayTimeMs = song.totalPlayTime,
  )

fun SongEntity.toDomain(): DomainSong =
  DomainSong(
    id = id,
    title = title,
    artists = emptyList(),
    album = albumId?.let { AlbumRef(id = it, title = albumName.orEmpty()) },
    durationSeconds = duration,
    thumbnailUrl = resolvedThumbnail(),
    isExplicit = explicit,
    isLocal = isLocal,
    liked = liked,
    totalPlayTimeMs = totalPlayTime,
  )

private fun SongEntity.resolvedThumbnail(): String? {
  thumbnailUrl?.let {
    return it
  }
  if (!isLocal) return null
  val numericAlbumId =
    albumId
      ?.takeIf { it.startsWith(LOCAL_ALBUM_PREFIX) }
      ?.substring(LOCAL_ALBUM_PREFIX.length)
      ?.toIntOrNull()
  return if (numericAlbumId != null && numericAlbumId > 0) {
    LOCAL_ALBUM_ART_URI + numericAlbumId
  } else {
    id
  }
}

fun Playlist.toDomain(): DomainPlaylist =
  DomainPlaylist(
    id = id,
    name = playlist.name,
    songCount = songCount,
    thumbnailUrl = playlist.thumbnailUrl ?: songThumbnails.firstOrNull { it != null },
  )

fun PlaylistEntity.toDomain(songCount: Int = 0): DomainPlaylist =
  DomainPlaylist(
    id = id,
    name = name,
    songCount = songCount,
    thumbnailUrl = thumbnailUrl,
  )
