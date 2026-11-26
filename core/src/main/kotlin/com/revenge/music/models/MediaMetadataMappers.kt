package com.revenge.music.models

import com.revenge.innertube.models.SongItem
import com.revenge.music.db.entities.Song
import com.revenge.music.db.entities.SongEntity
import com.revenge.music.ui.utils.resize

private const val VIDEO_SONG_TYPE = "MUSIC_VIDEO_TYPE_OMV"

fun Song.toMediaMetadata(): MediaMetadata = MediaMetadata(
    id = id,
    title = title,
    artists = artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
    duration = song.duration,
    thumbnailUrl = thumbnailUrl,
    album = album?.let { MediaMetadata.Album(id = it.id, title = it.title) }
        ?: song.albumId?.let { MediaMetadata.Album(id = it, title = song.albumName.orEmpty()) },
    musicVideoType = if (song.isVideo) VIDEO_SONG_TYPE else null,
    explicit = song.explicit,
)

fun SongEntity.toMediaMetadata(): MediaMetadata = MediaMetadata(
    id = id,
    title = title,
    artists = emptyList(),
    duration = duration,
    thumbnailUrl = thumbnailUrl,
    album = albumId?.let { MediaMetadata.Album(id = it, title = albumName.orEmpty()) },
    musicVideoType = if (isVideo) VIDEO_SONG_TYPE else null,
    explicit = explicit,
)

fun SongItem.toMediaMetadata(): MediaMetadata = MediaMetadata(
    id = id,
    title = title,
    artists = artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
    duration = duration ?: -1,
    thumbnailUrl = thumbnail.resize(1200, 1200),
    album = album?.let { MediaMetadata.Album(id = it.id, title = it.name) },
    setVideoId = setVideoId,
    musicVideoType = musicVideoType,
    explicit = explicit,
    libraryAddToken = libraryAddToken,
    libraryRemoveToken = libraryRemoveToken,
)

fun MediaMetadata.toSongEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    duration = duration,
    thumbnailUrl = thumbnailUrl,
    albumId = album?.id,
    albumName = album?.title,
    explicit = explicit,
    liked = liked,
    likedDate = likedDate,
    inLibrary = inLibrary,
    isVideo = isVideoSong,
    libraryAddToken = libraryAddToken,
    libraryRemoveToken = libraryRemoveToken,
)
