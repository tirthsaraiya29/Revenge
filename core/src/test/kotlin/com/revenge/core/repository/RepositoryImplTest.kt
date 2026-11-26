package com.revenge.core.repository

import androidx.room.Room
import com.revenge.music.db.InternalDatabase
import com.revenge.music.db.MusicDatabase
import com.revenge.music.db.entities.Event
import com.revenge.music.db.entities.PlaylistEntity
import com.revenge.music.db.entities.PlaylistSongMap
import com.revenge.music.db.entities.SongEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class RepositoryImplTest {

    private lateinit var internalDatabase: InternalDatabase
    private lateinit var database: MusicDatabase
    private lateinit var songRepository: SongRepositoryImpl
    private lateinit var playlistRepository: PlaylistRepositoryImpl

    @Before
    fun setUp() {
        internalDatabase = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            InternalDatabase::class.java,
        ).allowMainThreadQueries().build()
        database = MusicDatabase(internalDatabase)
        songRepository = SongRepositoryImpl(database)
        playlistRepository = PlaylistRepositoryImpl(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun insertSong(id: String, title: String = id, liked: Boolean = false, playTime: Long = 0L) {
        database.insert(SongEntity(id = id, title = title, liked = liked, totalPlayTime = playTime))
    }

    private fun insertEvent(songId: String) {
        database.insert(Event(songId = songId, timestamp = LocalDateTime.now(), playTime = 1_000L))
    }

    private fun <T : Any> eventually(block: suspend () -> T?): T = runBlocking {
        withTimeout(5_000) {
            var value = block()
            while (value == null) {
                delay(25)
                value = block()
            }
            value!!
        }
    }

    @Test
    fun `recently played de-duplicates newest first and honours the limit`() {
        insertSong("a")
        insertSong("b")
        insertEvent("a")
        insertEvent("b")
        insertEvent("a")

        assertEquals(listOf("a", "b"), runBlocking { songRepository.getRecentlyPlayed(2).first() }.map { it.id })
        assertEquals(listOf("a"), runBlocking { songRepository.getRecentlyPlayed(1).first() }.map { it.id })
        assertTrue(runBlocking { songRepository.getRecentlyPlayed(0).first() }.isEmpty())
        assertTrue(runBlocking { songRepository.getRecentlyPlayed(-3).first() }.isEmpty())
    }

    @Test
    fun `unknown song emits null`() {
        assertNull(runBlocking { songRepository.getSong("missing").first() })
    }

    @Test
    fun `liked songs preserve database order`() {
        insertSong("first", liked = true)
        insertSong("plain")
        insertSong("second", liked = true)

        assertEquals(
            listOf("first", "second"),
            runBlocking { songRepository.getLikedSongs().first() }.map { it.id },
        )
    }

    @Test
    fun `update liked toggles the flag and the date`() {
        insertSong("song")

        runBlocking { songRepository.updateLiked("song", true) }
        val liked = eventually {
            database.song("song").first()?.takeIf { it.song.liked }
        }
        assertTrue(liked.song.likedDate != null)

        runBlocking { songRepository.updateLiked("song", false) }
        val unliked = eventually {
            database.song("song").first()?.takeIf { !it.song.liked }
        }
        assertNull(unliked.song.likedDate)
    }

    @Test
    fun `play time increments add to the existing total`() {
        insertSong("song", playTime = 100L)

        runBlocking { songRepository.incrementPlayTime("song", 50L) }
        val updated = eventually {
            database.song("song").first()?.takeIf { it.song.totalPlayTime == 150L }
        }
        assertEquals(150L, updated.song.totalPlayTime)
    }

    @Test
    fun `saved playlists only are ordered by name`() {
        database.insert(PlaylistEntity(id = "p1", name = "Bravo", bookmarkedAt = LocalDateTime.now()))
        database.insert(PlaylistEntity(id = "p2", name = "Alpha", bookmarkedAt = LocalDateTime.now()))
        database.insert(PlaylistEntity(id = "p3", name = "Zulu"))

        assertEquals(
            listOf("Alpha", "Bravo"),
            runBlocking { playlistRepository.getAllPlaylists().first() }.map { it.name },
        )
    }

    @Test
    fun `playlist songs keep position order and unknown playlists are empty`() {
        insertSong("s0")
        insertSong("s1")
        insertSong("s2")
        database.insert(PlaylistEntity(id = "p", name = "P"))
        database.insert(PlaylistSongMap(playlistId = "p", songId = "s2", position = 2))
        database.insert(PlaylistSongMap(playlistId = "p", songId = "s0", position = 0))
        database.insert(PlaylistSongMap(playlistId = "p", songId = "s1", position = 1))

        assertEquals(
            listOf("s0", "s1", "s2"),
            runBlocking { playlistRepository.getPlaylistSongs("p").first() }.map { it.id },
        )
        assertTrue(runBlocking { playlistRepository.getPlaylistSongs("nope").first() }.isEmpty())
    }

    @Test
    fun `adding a song appends after the maximum position`() {
        insertSong("new")
        insertSong("old")
        database.insert(PlaylistEntity(id = "p", name = "P"))
        database.insert(PlaylistSongMap(playlistId = "p", songId = "old", position = 4))

        runBlocking { playlistRepository.addSongToPlaylist("p", "new") }
        val maps = eventually {
            database.playlistSongMaps("p", 0).takeIf { list -> list.any { it.songId == "new" } }
        }
        assertEquals(5, maps.first { it.songId == "new" }.position)
    }

    @Test
    fun `adding to an empty playlist starts at zero`() {
        insertSong("new")
        database.insert(PlaylistEntity(id = "p", name = "P"))

        runBlocking { playlistRepository.addSongToPlaylist("p", "new") }
        val maps = eventually {
            database.playlistSongMaps("p", 0).takeIf { it.isNotEmpty() }
        }
        assertEquals(0, maps.single().position)
    }

    @Test
    fun `duplicate and invalid adds never throw`() {
        insertSong("new")
        database.insert(PlaylistEntity(id = "p", name = "P"))

        runBlocking { playlistRepository.addSongToPlaylist("p", "new") }
        runBlocking { playlistRepository.addSongToPlaylist("p", "new") }
        runBlocking { playlistRepository.addSongToPlaylist("unknown-playlist", "new") }
        runBlocking { playlistRepository.addSongToPlaylist("p", "unknown-song") }

        eventually { database.playlistSongMaps("p", 0).takeIf { it.isNotEmpty() } }
    }

    @Test
    fun `removing a song drops every membership and absent removals are no-ops`() {
        insertSong("song")
        database.insert(PlaylistEntity(id = "p", name = "P"))
        database.insert(PlaylistSongMap(playlistId = "p", songId = "song", position = 0))
        database.insert(PlaylistSongMap(playlistId = "p", songId = "song", position = 1))

        runBlocking { playlistRepository.removeSongFromPlaylist("p", "song") }
        eventually {
            database.playlistSongMaps("p", 0).takeIf { it.none { map -> map.songId == "song" } }
                ?.let { Unit }
        }

        runBlocking { playlistRepository.removeSongFromPlaylist("p", "song") }
        assertEquals(emptyList<PlaylistSongMap>(), database.playlistSongMaps("p", 0))
    }
}
