package com.revenge.music.utils

import com.revenge.core.db.entities.Song
import com.revenge.innertube.models.filterBlockedArtists
import com.revenge.innertube.models.filterExplicit
import com.revenge.innertube.models.filterVideoSongs

fun filterQuickPickSongs(
    songs: List<Song>,
    excludedSongIds: Set<String>,
    blockedArtists: Set<String>,
    hideExplicit: Boolean,
    hideVideoSongs: Boolean,
): List<Song> {
    val allowedIds = songs
        .asSequence()
        .filterNot { it.song.hideFromQuickPicks }
        .filterNot { it.id in excludedSongIds }
        .map { it.toCarouselSong() }
        .toList()
        .filterBlockedArtists(blockedArtists)
        .filterExplicit(hideExplicit)
        .filterVideoSongs(hideVideoSongs)
        .map { it.id }
        .toHashSet()

    return songs
        .asSequence()
        .filterNot { it.song.hideFromQuickPicks }
        .filterNot { it.id in excludedSongIds }
        .filter { it.id in allowedIds }
        .distinctBy { it.id }
        .toList()
}
