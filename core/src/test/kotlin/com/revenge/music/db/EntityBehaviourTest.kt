package com.revenge.music.db

import com.revenge.music.db.entities.PlaylistEntity
import com.revenge.music.db.entities.Song
import com.revenge.music.db.entities.SongEntity
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityBehaviourTest {
    private fun songEntity(albumId: String? = null, thumbnailUrl: String? = null): SongEntity =
        SongEntity(id = "song-1", title = "Title", albumId = albumId, thumbnailUrl = thumbnailUrl)

    private fun song(entity: SongEntity): Song = Song(song = entity, artists = emptyList())

    @Test
    fun localSongDerivesAlbumArtProviderUri() {
        val song = song(songEntity(albumId = "LOCAL_ALBUM_42").copy(isLocal = true))
        assertEquals("content://media/external/audio/albumart/42", song.thumbnailUrl)
    }

    @Test
    fun localSongFallsBackWhenAlbumIdIsNotNumeric() {
        val song = song(
            songEntity(albumId = "LOCAL_ALBUM_not-a-number", thumbnailUrl = "fallback")
                .copy(isLocal = true),
        )
        assertEquals("fallback", song.thumbnailUrl)
    }

    @Test
    fun localSongFallsBackToIdWithoutThumbnail() {
        val song = song(songEntity().copy(isLocal = true))
        assertEquals("song-1", song.thumbnailUrl)
    }

    @Test
    fun remoteSongKeepsThumbnail() {
        val song = song(songEntity(thumbnailUrl = "remote"))
        assertEquals("remote", song.thumbnailUrl)
    }

    @Test
    fun localToggleLikeFlipsAndClearsDate() {
        val liked = songEntity().localToggleLike()
        assertTrue(liked.liked)
        assertNotNull(liked.likedDate)

        val unliked = liked.localToggleLike()
        assertFalse(unliked.liked)
        assertNull(unliked.likedDate)
    }

    @Test
    fun toggleLibraryClearsLikeWhenLeavingLibrary() {
        val fixed = LocalDateTime.of(2023, 1, 2, 3, 4, 5)
        val initial = songEntity().copy(liked = true, likedDate = fixed, inLibrary = fixed)

        val removed = initial.toggleLibrary(syncToYouTube = false)
        assertNull(removed.inLibrary)
        assertFalse(removed.liked)
        assertNull(removed.likedDate)
    }

    @Test
    fun toggleLibraryAddsLibraryDateWhenEntering() {
        val entered = songEntity().toggleLibrary(syncToYouTube = false)
        assertNotNull(entered.inLibrary)
        assertFalse(entered.liked)
    }

    @Test
    fun playlistShareLinkFollowsBrowseId() {
        assertEquals(
            "https://music.youtube.com/playlist?list=MPREb_abc",
            PlaylistEntity(id = "LP1", name = "Mix", browseId = "MPREb_abc").shareLink,
        )
        assertNull(PlaylistEntity(id = "LP1", name = "Mix").shareLink)
    }

    @Test
    fun playlistThumbnailsPreferStoredThumbnail() {
        val playlist = com.revenge.music.db.entities.Playlist(
            playlist = PlaylistEntity(id = "LP1", name = "Mix", thumbnailUrl = "big"),
            songCount = 0,
            songThumbnails = listOf("a", null, "b"),
        )
        assertEquals(listOf("big"), playlist.thumbnails)
        assertNull(playlist.thumbnailUrl)
    }

    @Test
    fun playlistThumbnailsFallBackToSongThumbnails() {
        val playlist = com.revenge.music.db.entities.Playlist(
            playlist = PlaylistEntity(id = "LP1", name = "Mix"),
            songCount = 3,
            songThumbnails = listOf("a", null, "b"),
        )
        assertEquals(listOf("a", "b"), playlist.thumbnails)
    }
}
