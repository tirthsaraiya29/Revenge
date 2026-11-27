package com.revenge.music.utils

import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickPickSongsTest {
  private fun song(id: String) =
    SongItem(id = id, title = id, artists = emptyList(), thumbnail = "")

  @Test
  fun fallbackSongsCannotRestoreHiddenSongs() {
    val result =
      QuickPickSongs.filterQuickPickSongs(
        listOf(song("hidden"), song("stale"), song("visible"), song("visible")),
        excludedSongIds = setOf("stale", "hidden"),
        blockedArtists = emptySet(),
        hideExplicit = false,
        hideVideoSongs = false,
      )

    assertEquals(listOf("visible"), result.map { it.id })
  }

  @Test
  fun changingHiddenSongIdsRemovesSongsFromTheAlreadyLoadedFeed() {
    val loaded = listOf(song("first"), song("second"))
    val initial = QuickPickSongs.filterQuickPickSongs(loaded, emptySet(), emptySet(), false, false)
    val updated =
      QuickPickSongs.filterQuickPickSongs(loaded, setOf("first"), emptySet(), false, false)

    assertEquals(listOf("first", "second"), initial.map { it.id })
    assertEquals(listOf("second"), updated.map { it.id })
  }

  @Test
  fun blockedArtistsAreExcludedFromLocalRecommendations() {
    val blocked =
      SongItem(
        id = "blocked",
        title = "Blocked",
        artists = listOf(Artist(name = "Blocked Artist", id = "artist-id")),
        thumbnail = "",
      )
    val result =
      QuickPickSongs.filterQuickPickSongs(
        listOf(blocked, song("visible")),
        excludedSongIds = emptySet(),
        blockedArtists = setOf("artist-id||Blocked Artist"),
        hideExplicit = false,
        hideVideoSongs = false,
      )

    assertEquals(listOf("visible"), result.map { it.id })
  }
}
