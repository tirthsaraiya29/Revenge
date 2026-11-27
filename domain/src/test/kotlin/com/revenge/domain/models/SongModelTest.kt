package com.revenge.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SongModelTest {

  private fun minimalSong(vararg artists: ArtistRef) =
    Song(
      id = "s1",
      title = "Title",
      artists = artists.toList(),
      album = null,
      durationSeconds = 180,
      thumbnailUrl = null,
    )

  @Test
  fun `A1 required-only construction applies documented defaults`() {
    val song = minimalSong(ArtistRef("a1", "Artist"))

    assertEquals(false, song.isExplicit)
    assertEquals(false, song.isLocal)
    assertEquals(false, song.liked)
    assertEquals(0L, song.totalPlayTimeMs)
    assertNull(song.album)
    assertNull(song.thumbnailUrl)
  }

  @Test
  fun `A1 nullable fields round-trip as supplied`() {
    val album = AlbumRef("al1", "Album")
    val song =
      Song(
        id = "s1",
        title = "Title",
        artists = emptyList(),
        album = album,
        durationSeconds = 10,
        thumbnailUrl = "content://thumb",
      )

    assertEquals(album, song.album)
    assertEquals("content://thumb", song.thumbnailUrl)
  }

  @Test
  fun `A2 fully populated song preserves artists album playtime and liked`() {
    val artists = listOf(ArtistRef("a1", "One"), ArtistRef("a2", "Two"))
    val song =
      Song(
        id = "s2",
        title = "Full",
        artists = artists,
        album = AlbumRef("al2", "The Album"),
        durationSeconds = 200,
        thumbnailUrl = "https://example.test/art.png",
        isExplicit = true,
        isLocal = true,
        liked = true,
        totalPlayTimeMs = 12_345L,
      )

    assertSame(artists, song.artists)
    assertEquals(listOf("One", "Two"), song.artists.map { it.name })
    assertEquals("The Album", song.album?.title)
    assertEquals(12_345L, song.totalPlayTimeMs)
    assertEquals(true, song.liked)
  }

  @Test
  fun `A3 playlist defaults copy and targeted copy`() {
    val playlist = Playlist(id = "p1", name = "Mix")

    assertEquals(0, playlist.songCount)
    assertNull(playlist.thumbnailUrl)
    assertEquals(playlist, playlist.copy())

    val renamed = playlist.copy(name = "Renamed")
    assertEquals("Renamed", renamed.name)
    assertEquals(playlist.id, renamed.id)
    assertEquals(playlist.songCount, renamed.songCount)
    assertEquals(playlist.thumbnailUrl, renamed.thumbnailUrl)
  }

  @Test
  fun `A4 data class structural equality and hashCode`() {
    val a = minimalSong(ArtistRef("a1", "Artist"))
    val b = minimalSong(ArtistRef("a1", "Artist"))

    assertEquals(a, b)
    assertEquals(a.hashCode(), b.hashCode())
    assertNotEquals(a, a.copy(title = "Other"))
  }
}
