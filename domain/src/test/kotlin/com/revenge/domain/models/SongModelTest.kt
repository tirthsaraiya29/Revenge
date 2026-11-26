package com.revenge.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongModelTest {

  @Test
  fun songDefaultsAreSafe() {
    val song =
      Song(
        id = "song-1",
        title = "Nightfall",
        artists = emptyList(),
        album = null,
        durationSeconds = 210,
        thumbnailUrl = null,
      )

    assertFalse(song.isExplicit)
    assertFalse(song.isLocal)
    assertFalse(song.liked)
    assertEquals(0L, song.totalPlayTimeMs)
    assertNull(song.album)
  }

  @Test
  fun songHoldsNestedReferences() {
    val artists = listOf(ArtistRef("artist-1", "Aurora"))
    val album = AlbumRef("album-1", "Northern Lights")
    val song =
      Song(
        id = "song-1",
        title = "Nightfall",
        artists = artists,
        album = album,
        durationSeconds = 210,
        thumbnailUrl = "https://cdn.example.com/art.jpg",
        isExplicit = true,
        isLocal = true,
        liked = true,
        totalPlayTimeMs = 42_000L,
      )

    assertEquals(artists, song.artists)
    assertEquals("Northern Lights", song.album?.title)
    assertEquals(42_000L, song.totalPlayTimeMs)
    assertTrue(song.liked)
  }

  @Test
  fun playlistDefaultsToOneSidedCopy() {
    val playlist = Playlist(id = "playlist-1", name = "Commute")

    assertEquals(0, playlist.songCount)
    assertNull(playlist.thumbnailUrl)
    assertEquals(playlist, playlist.copy())
    assertEquals("Road Trip", playlist.copy(name = "Road Trip").name)
  }
}
