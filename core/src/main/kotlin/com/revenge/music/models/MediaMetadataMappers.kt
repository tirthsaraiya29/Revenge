package com.revenge.music.models

import com.revenge.innertube.models.SongItem
import com.revenge.music.db.entities.Song
import com.revenge.music.db.entities.SongEntity
import com.revenge.music.ui.utils.resize

private const val MUSIC_VIDEO_TYPE_OMV = "MUSIC_VIDEO_TYPE_OMV"

fun Song.toMediaMetadata(): MediaMetadata = MediaMetadata(
    id = id,
    title = title,
    artists = artists.map { MediaMetadata.Artist(it.id, it.name) },
    duration = song.duration,
    thumbnailUrl = thumbnailUrl,
    album = album?.let { MediaMetadata.Album(it.id, it.title) }
        ?: song.albumId?.let { MediaMetadata.Album(it, song.albumName.orEmpty()) },
    musicVideoType = if (song.isVideo) MUSIC_VIDEO_TYPE_OMV else null,
    explicit = song.explicit,
    source = QueueItemSource.USER,
    suggestedBy = null,
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
    libraryAddToken = libraryAddToken,
    libraryRemoveToken = libraryRemoveToken,
    isVideo = isVideoSong,
)

fun SongItem.toMediaMetadata(): MediaMetadata = MediaMetadata(
    id = id,
    title = title,
    artists = artists.map { MediaMetadata.Artist(it.id, it.name) },
    duration = duration ?: -1,
    thumbnailUrl = thumbnail.resize(1200, 1200),
    album = album?.let { MediaMetadata.Album(it.id, it.name) },
    setVideoId = setVideoId,
    musicVideoType = musicVideoType,
    explicit = explicit,
    libraryAddToken = libraryAddToken,
    libraryRemoveToken = libraryRemoveToken,
    source = QueueItemSource.USER,
    suggestedBy = null,
)
