package com.revenge.core.mapping

import com.revenge.domain.models.AlbumRef
import com.revenge.domain.models.ArtistRef
import com.revenge.domain.models.Playlist as DomainPlaylist
import com.revenge.domain.models.Song as DomainSong
import com.revenge.music.db.entities.Playlist as RelationPlaylist
import com.revenge.music.db.entities.PlaylistEntity
import com.revenge.music.db.entities.Song as RelationSong
import com.revenge.music.db.entities.SongEntity

/** Maps the Room relation model (with artists/album populated) to the pure domain model. */
fun RelationSong.toDomain(): DomainSong {
  val albumRef =
    album?.let { AlbumRef(id = it.id, title = it.title) }
      ?: song.albumId?.let { AlbumRef(id = it, title = song.albumName.orEmpty()) }
  return DomainSong(
    id = song.id,
    title = song.title,
    artists = artists.map { ArtistRef(id = it.id, name = it.name) },
    album = albumRef,
    durationSeconds = song.duration,
    thumbnailUrl = thumbnailUrl,
    isExplicit = song.explicit,
    isLocal = song.isLocal,
    liked = song.liked,
    totalPlayTimeMs = song.totalPlayTime,
  )
}

/** Maps the flat entity, which carries no relation rows, to the pure domain model. */
fun SongEntity.toDomain(): DomainSong {
  val resolvedThumbnail =
    thumbnailUrl
      ?: if (isLocal) {
        val mediaStoreAlbumId = albumId?.removePrefix("LOCAL_ALBUM_")?.toLongOrNull()
        if (mediaStoreAlbumId != null && mediaStoreAlbumId > 0) {
          "content://media/external/audio/albumart/$mediaStoreAlbumId"
        } else {
          id
        }
      } else {
        null
      }
  return DomainSong(
    id = id,
    title = title,
    artists = emptyList(),
    album = albumId?.let { AlbumRef(id = it, title = albumName.orEmpty()) },
    durationSeconds = duration,
    thumbnailUrl = resolvedThumbnail,
    isExplicit = explicit,
    isLocal = isLocal,
    liked = liked,
    totalPlayTimeMs = totalPlayTime,
  )
}

/** Maps a playlist relation (with its computed count and preview thumbnails) to the domain model. */
fun RelationPlaylist.toDomain(): DomainPlaylist =
  DomainPlaylist(
    id = playlist.id,
    name = playlist.name,
    songCount = songCount,
    thumbnailUrl = thumbnails.firstOrNull() ?: playlist.thumbnailUrl,
  )

/** Maps a flat playlist row; the caller supplies the membership count because it is not on the row. */
fun PlaylistEntity.toDomain(songCount: Int = 0): DomainPlaylist =
  DomainPlaylist(id = id, name = name, songCount = songCount, thumbnailUrl = thumbnailUrl)
