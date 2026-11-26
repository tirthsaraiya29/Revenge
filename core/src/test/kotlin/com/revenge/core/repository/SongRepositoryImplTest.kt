package com.revenge.core.repository

import androidx.room.Room
import com.revenge.music.db.InternalDatabase
import com.revenge.music.db.MusicDatabase
import com.revenge.music.db.entities.Event
import com.revenge.music.db.entities.SongEntity
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SongRepositoryImplTest {

    private lateinit var internal: InternalDatabase
    private lateinit var database: MusicDatabase
    private lateinit var repository: SongRepositoryImpl
    private val queryExecutor = Executors.newSingleThreadExecutor()
    private val transactionExecutor = Executors.newSingleThreadExecutor()

    @Before
    fun setUp() {
        internal = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            InternalDatabase::class.java,
        )
            .allowMainThreadQueries()
            .setQueryExecutor(queryExecutor)
            .setTransactionExecutor(transactionExecutor)
            .build()
        database = MusicDatabase(internal)
        repository = SongRepositoryImpl(database)
    }

    @After
    fun tearDown() {
        database.close()
        queryExecutor.shutdownNow()
        transactionExecutor.shutdownNow()
    }

    private fun flush() {
        val latch = CountDownLatch(1)
        database.query { latch.countDown() }
        assertTrue("query executor did not drain", latch.await(5, TimeUnit.SECONDS))
    }

    private fun insertSong(id: String, liked: Boolean = false) {
        database.insert(SongEntity(id = id, title = "Song $id", liked = liked))
    }

    @Test
    fun getSongEmitsNullThenRow() = runBlocking {
        assertNull(repository.getSong("missing").first())
        insertSong("s1")
        assertEquals("Song s1", repository.getSong("s1").first()?.title)
    }

    @Test
    fun likedSongsFollowRowIdOrder() = runBlocking {
        insertSong("a", liked = true)
        insertSong("b", liked = true)
        insertSong("c", liked = true)
        insertSong("u", liked = false)

        assertEquals(listOf("a", "b", "c"), repository.getLikedSongs().first().map { it.id })

        repository.updateLiked("a", false)
        flush()
        assertEquals(listOf("b", "c"), repository.getLikedSongs().first().map { it.id })
    }

    @Test
    fun recentlyPlayedDeduplicatesKeepingNewestPosition() = runBlocking {
        insertSong("s1")
        insertSong("s2")
        insertSong("s3")
        val base = LocalDateTime.of(2024, 1, 1, 12, 0)
        database.insert(Event(songId = "s3", timestamp = base, playTime = 1))
        database.insert(Event(songId = "s1", timestamp = base.plusMinutes(1), playTime = 1))
        database.insert(Event(songId = "s2", timestamp = base.plusMinutes(2), playTime = 1))
        database.insert(Event(songId = "s1", timestamp = base.plusMinutes(3), playTime = 1))

        assertEquals(
            listOf("s1", "s2", "s3"),
            repository.getRecentlyPlayed(10).first().map { it.id },
        )
        assertEquals(
            listOf("s1", "s2"),
            repository.getRecentlyPlayed(2).first().map { it.id },
        )
    }

    @Test
    fun recentlyPlayedRejectsNonPositiveLimit() = runBlocking {
        insertSong("s1")
        database.insert(Event(songId = "s1", timestamp = LocalDateTime.of(2024, 1, 1, 0, 0), playTime = 1))

        assertTrue(repository.getRecentlyPlayed(0).first().isEmpty())
        assertTrue(repository.getRecentlyPlayed(-5).first().isEmpty())
    }

    @Test
    fun updateLikedTogglesRowAndDate() = runBlocking {
        insertSong("s1")

        repository.updateLiked("s1", true)
        flush()
        val liked = database.getSongByIdBlocking("s1")
        assertNotNull(liked)
        assertTrue(liked!!.song.liked)
        assertNotNull(liked.song.likedDate)

        repository.updateLiked("s1", false)
        flush()
        val unliked = database.getSongByIdBlocking("s1")
        assertNotNull(unliked)
        assertFalse(unliked!!.song.liked)
        assertNull(unliked.song.likedDate)
    }

    @Test
    fun incrementPlayTimeAccumulatesAndIgnoresUnknown() = runBlocking {
        insertSong("s1")

        repository.incrementPlayTime("s1", 1_000)
        flush()
        assertEquals(1_000L, database.getSongByIdBlocking("s1")!!.song.totalPlayTime)

        repository.incrementPlayTime("s1", 2_500)
        flush()
        assertEquals(3_500L, database.getSongByIdBlocking("s1")!!.song.totalPlayTime)

        repository.incrementPlayTime("s1", -500)
        flush()
        assertEquals(3_000L, database.getSongByIdBlocking("s1")!!.song.totalPlayTime)

        repository.incrementPlayTime("ghost", 100)
        flush()
        assertNull(database.getSongByIdBlocking("ghost"))
    }
}
