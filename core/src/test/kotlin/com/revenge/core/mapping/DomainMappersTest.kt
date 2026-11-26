package com.revenge.core.mapping

import com.revenge.domain.models.AlbumRef
import com.revenge.domain.models.ArtistRef
import com.revenge.music.db.entities.AlbumEntity
import com.revenge.music.db.entities.ArtistEntity
import com.revenge.music.db.entities.Playlist as PlaylistRow
import com.revenge.music.db.entities.PlaylistEntity
import com.revenge.music.db.entities.Song as SongRow
import com.revenge.music.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMappersTest {

    @Test
    fun relationSongPreservesArtistOrderAndAlbum() {
        val row = SongRow(
            song = SongEntity(id = "s", title = "Title", duration = -1),
            artists = listOf(
                ArtistEntity(id = "a1", name = "First"),
                ArtistEntity(id = "a2", name = "Second"),
            ),
            album = AlbumEntity(id = "al", title = "Album", songCount = 1, duration = 30),
        )

        val domain = row.toDomain()

        assertEquals("s", domain.id)
        assertEquals("Title", domain.title)
        assertEquals(listOf(ArtistRef("a1", "First"), ArtistRef("a2", "Second")), domain.artists)
        assertEquals(AlbumRef("al", "Album"), domain.album)
        assertEquals(-1, domain.durationSeconds)
    }

    @Test
    fun flatSongHasNoArtists() {
        val domain = SongEntity(id = "s", title = "T").toDomain()
        assertTrue(domain.artists.isEmpty())
    }

    @Test
    fun localThumbnailDerivedFromAlbumId() {
        val positive = SongEntity(
            id = "content://song",
            title = "T",
            albumId = "LOCAL_ALBUM_42",
            isLocal = true,
        ).toDomain()
        assertEquals("content://media/external/audio/albumart/42", positive.thumbnailUrl)

        val zero = SongEntity(
            id = "content://song",
            title = "T",
            albumId = "LOCAL_ALBUM_0",
            isLocal = true,
        ).toDomain()
        assertEquals("content://song", zero.thumbnailUrl)

        val nonNumeric = SongEntity(
            id = "content://song",
            title = "T",
            albumId = "LOCAL_ALBUM_nope",
            isLocal = true,
        ).toDomain()
        assertEquals("content://song", nonNumeric.thumbnailUrl)

        val noAlbum = SongEntity(id = "content://song", title = "T", isLocal = true).toDomain()
        assertEquals("content://song", noAlbum.thumbnailUrl)
    }

    @Test
    fun explicitThumbnailWinsEvenForLocalSongs() {
        val domain = SongEntity(
            id = "content://song",
            title = "T",
            thumbnailUrl = "https://img/remote.jpg",
            albumId = "LOCAL_ALBUM_42",
            isLocal = true,
        ).toDomain()
        assertEquals("https://img/remote.jpg", domain.thumbnailUrl)
    }

    @Test
    fun nonLocalSongWithoutThumbnailMapsToNull() {
        val domain = SongEntity(id = "s", title = "T", isLocal = false).toDomain()
        assertNull(domain.thumbnailUrl)
    }

    @Test
    fun albumFallsBackToAlbumIdWhenRelationMissing() {
        val row = SongRow(
            song = SongEntity(id = "s", title = "T", albumId = "al", albumName = null),
            artists = emptyList(),
            album = null,
        )
        assertEquals(AlbumRef("al", ""), row.toDomain().album)
    }

    @Test
    fun playlistEntityCountDefaultsToZero() {
        val entity = PlaylistEntity(id = "p", name = "P", thumbnailUrl = "t")
        assertEquals(0, entity.toDomain().songCount)
        assertEquals(4, entity.toDomain(songCount = 4).songCount)
    }

    @Test
    fun relationPlaylistPrefersExplicitThumbnail() {
        val explicit = PlaylistRow(
            playlist = PlaylistEntity(id = "p", name = "P", thumbnailUrl = "explicit"),
            songCount = 2,
            songThumbnails = listOf(null, "song"),
        ).toDomain()
        assertEquals("explicit", explicit.thumbnailUrl)
        assertEquals(2, explicit.songCount)

        val fromSongs = PlaylistRow(
            playlist = PlaylistEntity(id = "p", name = "P", thumbnailUrl = null),
            songCount = 1,
            songThumbnails = listOf(null, "song"),
        ).toDomain()
        assertEquals("song", fromSongs.thumbnailUrl)
    }
}
