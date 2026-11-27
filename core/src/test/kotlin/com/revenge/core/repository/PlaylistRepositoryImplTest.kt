package com.revenge.core.repository

import androidx.room.Room
import com.revenge.core.db.InternalDatabase
import com.revenge.core.db.MusicDatabase
import com.revenge.core.db.entities.PlaylistEntity
import com.revenge.core.db.entities.PlaylistSongMap
import com.revenge.core.db.entities.SongEntity
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlaylistRepositoryImplTest {

  private lateinit var internalDb: InternalDatabase
  private lateinit var database: MusicDatabase
  private lateinit var repository: PlaylistRepositoryImpl
  private val queryExecutor = Executors.newSingleThreadExecutor()
  private val transactionExecutor = Executors.newSingleThreadExecutor()

  @Before
  fun setUp() {
    internalDb =
      Room.inMemoryDatabaseBuilder(
          RuntimeEnvironment.getApplication(),
          InternalDatabase::class.java,
        )
        .allowMainThreadQueries()
        .setQueryExecutor(queryExecutor)
        .setTransactionExecutor(transactionExecutor)
        .build()
    database = MusicDatabase(internalDb)
    repository = PlaylistRepositoryImpl(database)
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

  private fun addPlaylist(
    id: String,
    name: String,
    bookmarked: Boolean = true,
    thumbnailUrl: String? = null,
  ) {
    database.insert(
      PlaylistEntity(
        id = id,
        name = name,
        bookmarkedAt = if (bookmarked) LocalDateTime.of(2024, 1, 1, 0, 0) else null,
        thumbnailUrl = thumbnailUrl,
      ),
    )
  }

  private fun addSong(id: String, thumbnailUrl: String? = null) {
    database.insert(SongEntity(id = id, title = "Song $id", thumbnailUrl = thumbnailUrl))
  }

  private fun addMembership(playlistId: String, songId: String, position: Int) {
    database.insert(
      PlaylistSongMap(playlistId = playlistId, songId = songId, position = position),
    )
  }

  @Test
  fun allPlaylistsFiltersBookmarkedAndSortsByName() = runBlocking {
    addPlaylist("p1", "Zeta")
    addPlaylist("p2", "Alpha")
    addPlaylist("p3", "Beta", bookmarked = false)

    val ids = repository.getAllPlaylists().first().map { it.id }
    assertEquals(listOf("p2", "p1"), ids)
  }

  @Test
  fun allPlaylistsPopulatesCountAndThumbnail() = runBlocking {
    addPlaylist("p", "Playlist")
    addSong("s0", thumbnailUrl = null)
    addSong("s1", thumbnailUrl = "https://img/one.jpg")
    addSong("s2", thumbnailUrl = "https://img/two.jpg")
    addMembership("p", "s0", 0)
    addMembership("p", "s1", 1)
    addMembership("p", "s2", 2)

    val playlist = repository.getAllPlaylists().first().single { it.id == "p" }
    assertEquals(3, playlist.songCount)
    assertEquals("https://img/one.jpg", playlist.thumbnailUrl)

    addPlaylist("q", "Explicit", thumbnailUrl = "https://img/explicit.jpg")
    addMembership("q", "s2", 0)
    val explicit = repository.getAllPlaylists().first().single { it.id == "q" }
    assertEquals("https://img/explicit.jpg", explicit.thumbnailUrl)
  }

  @Test
  fun playlistSongsFollowPositionAndKeepDuplicates() = runBlocking {
    addPlaylist("p", "P")
    addSong("s0")
    addSong("s1")
    addSong("s2")
    addMembership("p", "s2", 2)
    addMembership("p", "s0", 0)
    addMembership("p", "s1", 1)
    addMembership("p", "s0", 3)

    assertEquals(
      listOf("s0", "s1", "s2", "s0"),
      repository.getPlaylistSongs("p").first().map { it.id },
    )
    assertTrue(repository.getPlaylistSongs("unknown").first().isEmpty())
  }

  @Test
  fun addSongAppendsAfterCurrentMaximum() = runBlocking {
    addPlaylist("p", "P")
    addSong("s0")
    addSong("s1")
    addSong("s2")

    repository.addSongToPlaylist("p", "s0")
    flush()
    assertEquals(listOf(0), database.playlistSongMaps("p", 0).map { it.position })

    addMembership("p", "s1", 4)
    repository.addSongToPlaylist("p", "s2")
    flush()
    assertEquals(
      listOf(0, 4, 5),
      database.playlistSongMaps("p", 0).map { it.position }.sorted(),
    )

    repository.addSongToPlaylist("missing", "missing")
    flush()
    assertTrue(database.playlistSongMaps("missing", 0).isEmpty())
  }

  @Test
  fun removeSongDeletesAllDuplicatesWithoutRenumbering() = runBlocking {
    addPlaylist("p1", "One")
    addPlaylist("p2", "Two")
    addSong("s0")
    addSong("s1")
    addMembership("p1", "s0", 0)
    addMembership("p1", "s0", 1)
    addMembership("p1", "s1", 2)
    addMembership("p2", "s0", 0)

    repository.removeSongFromPlaylist("p1", "s0")
    flush()

    val remaining = database.playlistSongMaps("p1", 0)
    assertEquals(listOf("s1"), remaining.map { it.songId })
    assertEquals(listOf(2), remaining.map { it.position })
    assertEquals(1, database.playlistSongMaps("p2", 0).size)

    repository.removeSongFromPlaylist("p1", "absent")
    flush()
    assertEquals(1, database.playlistSongMaps("p1", 0).size)
  }
}
