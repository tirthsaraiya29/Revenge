package com.revenge.music.extensions

import com.revenge.music.db.entities.Album
import com.revenge.music.db.entities.AlbumEntity
import com.revenge.music.db.entities.ArtistEntity
import com.revenge.music.db.entities.Playlist
import com.revenge.music.db.entities.PlaylistEntity
import com.revenge.music.db.entities.Song
import com.revenge.music.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private enum class SampleEnum { ALPHA, BETA }

class CollectionExtTest {

    private fun song(id: String, explicit: Boolean = false, video: Boolean = false) = Song(
        song = SongEntity(id = id, title = id, explicit = explicit, isVideo = video),
        artists = listOf(ArtistEntity(id = "artist-$id", name = "Artist")),
    )

    private fun album(id: String, explicit: Boolean) = Album(
        album = AlbumEntity(id = id, title = id, explicit = explicit, songCount = 1, duration = 100),
    )

    private fun playlist(id: String, browseId: String?) = Playlist(
        playlist = PlaylistEntity(id = id, name = id, browseId = browseId),
        songCount = 0,
        songThumbnails = emptyList(),
    )

    @Test
    fun `reversed true is a live view and false returns the receiver`() {
        val backing = mutableListOf(1, 2, 3)
        val view = backing.reversed(true)
        assertEquals(listOf(3, 2, 1), view)
        backing.add(4)
        assertEquals(listOf(4, 3, 2, 1), view)

        val plain = mutableListOf(1, 2)
        assertSame(plain, plain.reversed(false))
    }

    @Test
    fun `move repositions in place and returns the same instance`() {
        val one = mutableListOf(1, 2, 3)
        assertSame(one, one.move(1, 2))
        assertEquals(listOf(1, 3, 2), one)

        val two = mutableListOf(1, 2, 3)
        two.move(2, 0)
        assertEquals(listOf(3, 1, 2), two)
    }

    @Test
    fun `mergeNearbyElements collapses adjacent runs only`() {
        val merged = listOf("a1", "a2", "b1", "a3").mergeNearbyElements(
            key = { it.first() },
            merge = { first, second -> first + second.substring(1) },
        )
        assertEquals(listOf("a12", "b1", "a3"), merged)

        assertEquals(emptyList<String>(), emptyList<String>().mergeNearbyElements())
        assertEquals(listOf("a1", "b1", "a3"), listOf("a1", "b1", "a3").mergeNearbyElements())
        assertEquals(listOf("a", "a"), listOf("a", "b", "a").mergeNearbyElements(key = { "x" }))
    }

    @Test
    fun `song filters remove only matching rows unless disabled`() {
        val songs = listOf(song("plain"), song("explicit", explicit = true), song("video", video = true))

        assertEquals(listOf("plain", "video"), songs.filterExplicit(true).map { it.id })
        assertSame(songs, songs.filterExplicit(false))

        assertEquals(listOf("plain", "explicit"), songs.filterVideoSongs(true).map { it.id })
        assertSame(songs, songs.filterVideoSongs(false))
    }

    @Test
    fun `album and playlist filters respect their flags`() {
        val albums = listOf(album("clean", false), album("dirty", true))
        assertEquals(listOf("clean"), albums.filterExplicitAlbums(true).map { it.id })
        assertSame(albums, albums.filterExplicitAlbums(false))

        val playlists = listOf(playlist("short", "SSabc"), playlist("normal", "PL123"), playlist("none", null))
        assertEquals(listOf("normal", "none"), playlists.filterYoutubeShorts(true).map { it.id })
        assertSame(playlists, playlists.filterYoutubeShorts())
    }

    @Test
    fun `toEnum matches exact names and falls back`() {
        assertEquals(SampleEnum.BETA, "BETA".toEnum(SampleEnum.ALPHA))
        assertEquals(SampleEnum.ALPHA, "nope".toEnum(SampleEnum.ALPHA))
        assertEquals(SampleEnum.ALPHA, (null as String?).toEnum(SampleEnum.ALPHA))
        assertEquals(SampleEnum.ALPHA, "beta".toEnum(SampleEnum.ALPHA))
    }

    @Test
    fun `tryOrNull returns value or null for exceptions`() {
        assertNull(tryOrNull<Int> { error("boom") })
        assertEquals(2, tryOrNull { 2 } ?: 0)
    }

    @Test
    fun `toInetSocketAddress splits without resolving`() {
        val address = "example.org:8080".toInetSocketAddress()
        assertTrue(address.isUnresolved)
        assertEquals("example.org", address.hostString)
        assertEquals(8080, address.port)

        assertThrows<IndexOutOfBoundsException> { "host".toInetSocketAddress() }
        assertThrows<NumberFormatException> { "host:abc".toInetSocketAddress() }
    }

    private inline fun <reified T : Throwable> assertThrows(crossinline body: () -> Unit) {
        try {
            body()
        } catch (thrown: Throwable) {
            if (thrown is T) return
            throw AssertionError("expected ${T::class.java.simpleName} but got $thrown", thrown)
        }
        throw AssertionError("expected ${T::class.java.simpleName} to be thrown")
    }
}
