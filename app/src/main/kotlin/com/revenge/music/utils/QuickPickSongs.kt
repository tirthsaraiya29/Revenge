package com.revenge.music.utils

import com.revenge.innertube.models.SongItem

object QuickPickSongs {
  fun filterQuickPickSongs(
    songs: List<SongItem>,
    excludedSongIds: Set<String>,
    blockedArtists: Set<String>,
    hideExplicit: Boolean,
    hideVideoSongs: Boolean,
  ): List<SongItem> {
    val seen = HashSet<String>()
    return songs.filter { song ->
      if (song.id.isBlank()) return@filter false
      if (song.id in excludedSongIds) return@filter false
      if (!seen.add(song.id)) return@filter false
      if (isBlocked(song, blockedArtists)) return@filter false
      if (hideExplicit && song.explicit) return@filter false
      if (hideVideoSongs && song.isVideoSong && !song.isPodcastEpisode) return@filter false
      true
    }
  }

  private fun isBlocked(song: SongItem, blockedArtists: Set<String>): Boolean {
    if (blockedArtists.isEmpty()) return false
    return song.artists.any { artist ->
      blockedArtists.any { entry ->
        val id = entry.substringBefore("||")
        val name = entry.substringAfter("||", "")
        artist.id == id || (name.isNotBlank() && artist.name.equals(name, ignoreCase = true))
      }
    }
  }
}
