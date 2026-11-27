package com.revenge.core.mapping

import com.revenge.core.db.entities.AlbumEntity
import com.revenge.core.db.entities.ArtistEntity
import com.revenge.core.db.entities.Playlist
import com.revenge.core.db.entities.PlaylistEntity
import com.revenge.core.db.entities.Song
import com.revenge.core.db.entities.SongEntity
import com.revenge.domain.models.AlbumRef
import com.revenge.domain.models.ArtistRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMappersTest {

  @Test
  fun `Song relation maps ordered artists and album`() {
    val song =
      Song(
        song =
          SongEntity(
            id = "s1",
            title = "Title",
            duration = 200,
            thumbnailUrl = "https://img/s1.jpg",
            albumId = "alb",
            albumName = "Album",
            explicit = true,
            liked = true,
            isLocal = true,
            totalPlayTime = 5_000L,
          ),
        artists =
          listOf(
            ArtistEntity(id = "a1", name = "One"),
            ArtistEntity(id = "a2", name = "Two"),
          ),
        album = AlbumEntity(id = "alb", title = "Album", songCount = 1, duration = 200),
      )

    val domain = song.toDomain()
    assertEquals("s1", domain.id)
    assertEquals("Title", domain.title)
    assertEquals(listOf(ArtistRef("a1", "One"), ArtistRef("a2", "Two")), domain.artists)
    assertEquals(AlbumRef("alb", "Album"), domain.album)
    assertEquals(200, domain.durationSeconds)
    assertEquals("https://img/s1.jpg", domain.thumbnailUrl)
    assertTrue(domain.isExplicit)
    assertTrue(domain.isLocal)
    assertTrue(domain.liked)
    assertEquals(5_000L, domain.totalPlayTimeMs)
  }

  @Test
  fun `Song relation falls back to album id and name`() {
    val song =
      Song(
        song = SongEntity(id = "s2", title = "T", albumId = "fallback", albumName = "Fallback"),
        artists = emptyList(),
      )
    assertEquals(AlbumRef("fallback", "Fallback"), song.toDomain().album)
    assertTrue(song.toDomain().artists.isEmpty())
  }

  @Test
  fun `local entity resolves the media store album art`() {
    val entity =
      SongEntity(
        id = "local-1",
        title = "Local",
        isLocal = true,
        albumId = "LOCAL_ALBUM_42",
      )
    val domain = entity.toDomain()
    assertEquals("content://media/external/audio/albumart/42", domain.thumbnailUrl)
    assertTrue(domain.artists.isEmpty())
    assertEquals(AlbumRef("LOCAL_ALBUM_42", ""), domain.album)
  }

  @Test
  fun `local entity falls back to its id for unusable album ids`() {
    assertEquals(
      "local-2",
      SongEntity(id = "local-2", title = "T", isLocal = true, albumId = "LOCAL_ALBUM_abc")
        .toDomain()
        .thumbnailUrl,
    )
    assertEquals(
      "local-3",
      SongEntity(id = "local-3", title = "T", isLocal = true, albumId = "LOCAL_ALBUM_0")
        .toDomain()
        .thumbnailUrl,
    )
    assertEquals(
      "local-4",
      SongEntity(id = "local-4", title = "T", isLocal = true, albumId = null)
        .toDomain()
        .thumbnailUrl,
    )
  }

  @Test
  fun `remote entity keeps a supplied thumbnail or null`() {
    assertEquals(
      "https://img/x.jpg",
      SongEntity(id = "r1", title = "T", thumbnailUrl = "https://img/x.jpg")
        .toDomain()
        .thumbnailUrl,
    )
    assertNull(SongEntity(id = "r2", title = "T").toDomain().thumbnailUrl)
  }

  @Test
  fun `Playlist relation prefers the explicit cover then song thumbnails`() {
    val explicit =
      Playlist(
        playlist = PlaylistEntity(id = "p1", name = "P1", thumbnailUrl = "https://cover"),
        songCount = 3,
        songThumbnails = listOf("https://song", null),
      )
    assertEquals("https://cover", explicit.toDomain().thumbnailUrl)
    assertEquals(3, explicit.toDomain().songCount)

    val derived =
      Playlist(
        playlist = PlaylistEntity(id = "p2", name = "P2"),
        songCount = 1,
        songThumbnails = listOf(null, "https://first"),
      )
    assertEquals("https://first", derived.toDomain().thumbnailUrl)

    val empty =
      Playlist(
        playlist = PlaylistEntity(id = "p3", name = "P3"),
        songCount = 0,
        songThumbnails = emptyList(),
      )
    assertNull(empty.toDomain().thumbnailUrl)
  }

  @Test
  fun `Playlist entity uses the caller supplied count`() {
    val domain = PlaylistEntity(id = "p4", name = "Name", thumbnailUrl = "https://t").toDomain(7)
    assertEquals("p4", domain.id)
    assertEquals("Name", domain.name)
    assertEquals(7, domain.songCount)
    assertEquals("https://t", domain.thumbnailUrl)
    assertEquals(0, PlaylistEntity(id = "p5", name = "N").toDomain().songCount)
  }
}
