package com.revenge.core.models

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as Media3Metadata
import com.revenge.innertube.models.Album
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.SongItem
import com.revenge.core.db.entities.AlbumEntity
import com.revenge.core.db.entities.ArtistEntity
import com.revenge.core.db.entities.Song
import com.revenge.core.db.entities.SongEntity
import com.revenge.core.extensions.metadata
import com.revenge.core.extensions.toMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaMetadataMappersTest {

    private fun baseSong(
        explicit: Boolean = false,
        video: Boolean = false,
        albumId: String? = null,
        albumName: String? = null,
        thumbnail: String? = "https://img/thumb.jpg",
    ) = Song(
        song = SongEntity(
            id = "song-1",
            title = "Title",
            duration = 213,
            thumbnailUrl = thumbnail,
            albumId = albumId,
            albumName = albumName,
            explicit = explicit,
            isVideo = video,
        ),
        artists = listOf(
            ArtistEntity(id = "a1", name = "First"),
            ArtistEntity(id = "a2", name = "Second"),
        ),
    )

    private fun baseMetadata(
        videoType: String? = null,
        album: MediaMetadata.Album? = null,
    ) = MediaMetadata(
        id = "track",
        title = "Track",
        artists = listOf(MediaMetadata.Artist("x", "X")),
        duration = 10,
        thumbnailUrl = "https://img/a.jpg",
        album = album,
        musicVideoType = videoType,
        explicit = true,
        liked = true,
        inLibrary = java.time.LocalDateTime.of(2024, 1, 2, 3, 4),
        libraryAddToken = "add",
        libraryRemoveToken = "remove",
        likedDate = java.time.LocalDateTime.of(2024, 1, 1, 0, 0),
    )

    @Test
    fun `isVideoSong ignores audio-only atv type`() {
        assertFalse(baseMetadata(videoType = null).isVideoSong)
        assertFalse(baseMetadata(videoType = "MUSIC_VIDEO_TYPE_ATV").isVideoSong)
        assertTrue(baseMetadata(videoType = "MUSIC_VIDEO_TYPE_OMV").isVideoSong)
        assertTrue(baseMetadata(videoType = "MUSIC_VIDEO_TYPE_PODCAST_EPISODE").isVideoSong)
    }

    @Test
    fun `Song toMediaMetadata maps identity relations and explicit only`() {
        val relationAlbum = AlbumEntity(id = "alb", title = "Album", songCount = 1, duration = 100)
        val song = Song(
            song = SongEntity(
                id = "song-1",
                title = "Title",
                duration = 213,
                thumbnailUrl = "https://img/thumb.jpg",
                albumId = "alb",
                albumName = "Album",
                explicit = true,
                isVideo = true,
                liked = true,
            ),
            artists = listOf(ArtistEntity("a1", "First"), ArtistEntity("a2", "Second")),
            album = relationAlbum,
        )

        val metadata = song.toMediaMetadata()
        assertEquals("song-1", metadata.id)
        assertEquals("Title", metadata.title)
        assertEquals(213, metadata.duration)
        assertEquals("https://img/thumb.jpg", metadata.thumbnailUrl)
        assertEquals(listOf("First", "Second"), metadata.artists.map { it.name })
        assertEquals(listOf("a1", "a2"), metadata.artists.map { it.id })
        assertEquals(MediaMetadata.Album("alb", "Album"), metadata.album)
        assertEquals("MUSIC_VIDEO_TYPE_OMV", metadata.musicVideoType)
        assertTrue(metadata.explicit)
        assertEquals(QueueItemSource.USER, metadata.source)
        assertNull(metadata.suggestedBy)
        assertFalse(metadata.liked)
        assertNull(metadata.likedDate)
        assertNull(metadata.inLibrary)
    }

    @Test
    fun `Song toMediaMetadata falls back to album id and name`() {
        val metadata = baseSong(albumId = "only-id", albumName = "Name").toMediaMetadata()
        assertEquals(MediaMetadata.Album("only-id", "Name"), metadata.album)
        assertNull(metadata.musicVideoType)
    }

    @Test
    fun `MediaMetadata toSongEntity copies the fields it owns`() {
        val entity = baseMetadata(album = MediaMetadata.Album("alb", "Album")).toSongEntity()
        assertEquals("track", entity.id)
        assertEquals("Track", entity.title)
        assertEquals(10, entity.duration)
        assertEquals("https://img/a.jpg", entity.thumbnailUrl)
        assertEquals("alb", entity.albumId)
        assertEquals("Album", entity.albumName)
        assertTrue(entity.explicit)
        assertTrue(entity.liked)
        assertNotNull(entity.likedDate)
        assertNotNull(entity.inLibrary)
        assertEquals("add", entity.libraryAddToken)
        assertEquals("remove", entity.libraryRemoveToken)
        assertFalse(entity.isVideo)
    }

    @Test
    fun `SongItem toMediaMetadata defaults duration and resizes artwork`() {
        val item = SongItem(
            id = "item",
            title = "Item",
            artists = listOf(Artist(name = "One", id = "i1")),
            album = Album(name = "Album Name", id = "alb"),
            duration = null,
            thumbnail = "https://i.ytimg.com/vi/item/default.jpg",
            setVideoId = "sv",
            libraryAddToken = "add",
            libraryRemoveToken = "remove",
        )
        val metadata = item.toMediaMetadata()
        assertEquals(-1, metadata.duration)
        assertEquals("https://i.ytimg.com/vi/item/maxresdefault.jpg", metadata.thumbnailUrl)
        assertEquals(MediaMetadata.Album("alb", "Album Name"), metadata.album)
        assertEquals("sv", metadata.setVideoId)
        assertEquals("add", metadata.libraryAddToken)
        assertEquals("remove", metadata.libraryRemoveToken)
    }

    @Test
    fun `SongItem toMediaItem seeds playback and tags the model`() {
        val item = SongItem(
            id = "item",
            title = "Item",
            artists = listOf(Artist(name = "One", id = "i1"), Artist(name = "Two", id = "i2")),
            album = Album(name = "Album Name", id = "alb"),
            thumbnail = "https://i.ytimg.com/vi/item/default.jpg",
        )
        val mediaItem = item.toMediaItem()
        assertEquals("item", mediaItem.mediaId)
        assertEquals("https://music.youtube.com/watch?v=item", mediaItem.localConfiguration?.uri?.toString())
        assertEquals("item", mediaItem.localConfiguration?.customCacheKey)
        assertEquals("item", (mediaItem.localConfiguration?.tag as MediaMetadata).id)
        assertEquals(Media3Metadata.MEDIA_TYPE_MUSIC, mediaItem.mediaMetadata.mediaType)
        assertTrue(mediaItem.mediaMetadata.isPlayable == true)
        assertFalse(mediaItem.mediaMetadata.isBrowsable == true)
        assertEquals("One, Two", mediaItem.mediaMetadata.artist?.toString())
        assertEquals(
            "https://i.ytimg.com/vi/item/maxresdefault.jpg",
            mediaItem.mediaMetadata.extras?.getString("artwork_uri"),
        )
        assertEquals(
            "https://i.ytimg.com/vi/item/maxresdefault.jpg",
            mediaItem.mediaMetadata.artworkUri?.toString(),
        )
    }

    @Test
    fun `playback seed keeps direct schemes and encodes bare ids`() {
        fun uriFor(id: String) = com.revenge.innertube.models.SongItem(
            id = id,
            title = "t",
            artists = emptyList(),
            thumbnail = "https://i.ytimg.com/vi/x/default.jpg",
        ).toMediaItem().localConfiguration?.uri?.toString()

        assertEquals("content://media/external/1", uriFor("content://media/external/1"))
        assertEquals("file:///tmp/a.mp3", uriFor("file:///tmp/a.mp3"))
        assertEquals("https://host/a", uriFor("https://host/a"))
        assertEquals("HTTPS://host/a", uriFor("HTTPS://host/a"))
        assertEquals("android.resource://pkg/1", uriFor("android.resource://pkg/1"))
        assertEquals(
            "https://music.youtube.com/watch?v=a%20b%3F%26",
            uriFor("a b?&"),
        )
    }

    @Test
    fun `MediaItem metadata falls back to media3 metadata`() {
        val tagged = MediaMetadata(
            id = "tagged",
            title = "Tagged",
            artists = emptyList(),
            duration = 0,
        )
        val taggedItem = MediaItem.Builder()
            .setMediaId("tagged")
            .setUri("https://example.com/tagged")
            .setTag(tagged)
            .build()
        assertSame(tagged, taggedItem.metadata)

        assertNull(MediaItem.Builder().build().metadata)

        val idOnly = MediaItem.Builder().setMediaId("only-id").build().metadata
        assertNotNull(idOnly)
        assertEquals("only-id", idOnly!!.id)
        assertEquals("", idOnly.title)
        assertTrue(idOnly.artists.isEmpty())

        val withArtist = MediaItem.Builder()
            .setMediaId("m")
            .setMediaMetadata(
                Media3Metadata.Builder()
                    .setTitle("Song")
                    .setArtist("The Band")
                    .setArtworkUri(android.net.Uri.parse("https://img/x.jpg"))
                    .build()
            )
            .build()
            .metadata
        assertNotNull(withArtist)
        assertEquals(listOf(MediaMetadata.Artist(null, "The Band")), withArtist!!.artists)
        assertEquals(0, withArtist.duration)
        assertEquals("https://img/x.jpg", withArtist.thumbnailUrl)
    }
}
