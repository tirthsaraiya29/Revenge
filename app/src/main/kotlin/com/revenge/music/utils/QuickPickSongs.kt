package com.revenge.music.utils

import com.revenge.innertube.models.filterBlockedArtists
import com.revenge.innertube.models.filterExplicit
import com.revenge.innertube.models.filterVideoSongs
import com.revenge.music.db.entities.Song

fun filterQuickPickSongs(
  songs: List<Song>,
  excludedSongIds: Set<String>,
  blockedArtists: Set<String>,
  hideExplicit: Boolean,
  hideVideoSongs: Boolean,
): List<Song> {
  val visible = songs.filterNot { it.song.hideFromQuickPicks || it.id in excludedSongIds }
  val allowedIds =
    visible
      .map { it.toCarouselSong() }
      .filterBlockedArtists(blockedArtists)
      .filterExplicit(hideExplicit)
      .filterVideoSongs(hideVideoSongs)
      .mapTo(mutableSetOf()) { it.id }
  return visible.filter { it.id in allowedIds }.distinctBy { it.id }
}
