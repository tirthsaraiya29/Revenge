package com.revenge.music.utils

import com.revenge.core.db.entities.Song
import com.revenge.innertube.models.Album
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.filterBlockedArtists
import com.revenge.innertube.models.filterExplicit
import com.revenge.innertube.models.filterVideoSongs

private const val VIDEO_SONG_TYPE = "MUSIC_VIDEO_TYPE_OMV"

fun Song.toCarouselSong(): SongItem = SongItem(
    id = id,
    title = title,
    artists = artists.map { Artist(id = it.id, name = it.name) },
    album = album?.let { Album(name = it.title, id = it.id) },
    duration = song.duration,
    musicVideoType = if (song.isVideo) VIDEO_SONG_TYPE else null,
    thumbnail = thumbnailUrl ?: "",
    explicit = song.explicit,
)

fun selectHeroCarouselSongs(
    recentlyPlayed: List<SongItem>,
    library: List<SongItem>,
    recommendations: List<SongItem>,
    account: List<SongItem> = emptyList(),
    excludedSongIds: Set<String> = emptySet(),
    blockedArtists: Set<String> = emptySet(),
    hideExplicit: Boolean = false,
    hideVideoSongs: Boolean = false,
    limit: Int = 20,
): List<SongItem> {
    if (limit <= 0) return emptyList()

    fun filter(source: List<SongItem>): List<SongItem> = source
        .filterNot { it.id in excludedSongIds }
        .filterBlockedArtists(blockedArtists)
        .filterExplicit(hideExplicit)
        .filterVideoSongs(hideVideoSongs)
        .distinctBy { it.id }

    val recent = filter(recentlyPlayed)
    val local = filter(library)
    val extra = filter(account)
    val suggested = filter(recommendations)

    val merged = ArrayList<SongItem>()
    val seenIds = HashSet<String>()
    fun addAll(source: List<SongItem>) {
        for (song in source) {
            if (seenIds.add(song.id)) merged.add(song)
        }
    }

    addAll(recent)
    // Library and account songs alternate so a large library cannot hide account songs.
    var index = 0
    while (index < local.size || index < extra.size) {
        local.getOrNull(index)?.let { addAll(listOf(it)) }
        extra.getOrNull(index)?.let { addAll(listOf(it)) }
        index++
    }
    addAll(suggested)

    val accepted = ArrayList<SongItem>()
    val deferred = ArrayList<SongItem>()
    val usedAlbumIds = HashSet<String>()
    val usedThumbnails = HashSet<String>()

    for (song in merged) {
        val albumId = song.album?.id
        val thumbnailKey = song.thumbnail.substringBefore('?').substringBefore('=')
        val repeatsArtwork = (albumId != null && albumId in usedAlbumIds) ||
            thumbnailKey in usedThumbnails
        if (repeatsArtwork) {
            deferred.add(song)
        } else {
            accepted.add(song)
            albumId?.let(usedAlbumIds::add)
            if (thumbnailKey.isNotEmpty()) usedThumbnails.add(thumbnailKey)
        }
    }

    return (accepted + deferred).take(limit)
}
