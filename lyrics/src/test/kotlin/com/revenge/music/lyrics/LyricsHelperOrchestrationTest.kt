package com.revenge.music.lyrics

import android.content.Context
import com.revenge.music.db.entities.LyricsEntity
import com.revenge.music.models.MediaMetadata
import com.revenge.music.utils.NetworkConnectivityObserver
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LyricsHelperOrchestrationTest {

    private lateinit var helper: LyricsHelper

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        helper = LyricsHelper(context, NetworkConnectivityObserver(context))
        helper.connectivityOverride = { true }
    }

    private fun metadata(id: String = "song-id") = MediaMetadata(
        id = id,
        title = "Title",
        artists = listOf(MediaMetadata.Artist(id = null, name = "Artist")),
        duration = 200,
    )

    private class FakeProvider(
        override val name: String,
        private val pages: List<String> = emptyList(),
        private val pageDelayMs: Long = 0,
        private val lyrics: suspend () -> Result<String> = { Result.success("[00:01.00]line") },
    ) : LyricsProvider {

        var lyricCalls = 0
        var allCalls = 0

        override fun isEnabled(context: Context): Boolean = true

        override suspend fun getLyrics(
            id: String,
            title: String,
            artist: String,
            duration: Int,
            album: String?,
        ): Result<String> {
            lyricCalls++
            return lyrics()
        }

        override suspend fun getAllLyrics(
            id: String,
            title: String,
            artist: String,
            duration: Int,
            album: String?,
            callback: (String) -> Unit,
        ) {
            allCalls++
            if (pageDelayMs > 0) delay(pageDelayMs)
            pages.forEach(callback)
        }
    }

    @Test
    fun `blank higher priority provider falls through to the next`() = runTest {
        helper.fetchScope = this
        val blank = FakeProvider("A") { Result.success("   ") }
        val synced = FakeProvider("B") { Result.success("[00:01.00]synced") }
        helper.providersOverride = listOf(blank, synced)

        val result = helper.getLyrics(metadata())

        assertEquals("B", result.providerName)
        assertEquals(1, blank.lyricCalls)
        assertEquals("[00:01.00]synced", result.lyrics)
        assertNotNull(helper.cache.get("song-id"))
    }

    @Test
    fun `priority dominates synchronisation quality`() = runTest {
        helper.fetchScope = this
        val unsynced = FakeProvider("A") { Result.success("plain line") }
        val synced = FakeProvider("B") { Result.success("[00:01.00]synced") }
        helper.providersOverride = listOf(unsynced, synced)

        val result = helper.getLyrics(metadata())

        assertEquals("A", result.providerName)
        assertEquals("plain line", result.lyrics)
    }

    @Test
    fun `slow top provider delays selection until its timeout`() = runTest {
        helper.fetchScope = this
        helper.providerTimeoutMs = 5_000L
        val slow = FakeProvider("A") {
            delay(6_000)
            Result.success("[00:01.00]slow")
        }
        val fast = FakeProvider("B") { Result.success("[00:01.00]fast") }
        helper.providersOverride = listOf(slow, fast)

        val result = helper.getLyrics(metadata())

        assertEquals("B", result.providerName)
        assertEquals(5_000L, testScheduler.currentTime)
    }

    @Test
    fun `all providers failing returns the not found shape`() = runTest {
        helper.fetchScope = this
        val failure = FakeProvider("A") { Result.failure(IllegalStateException("boom")) }
        val throwing = FakeProvider("B") { throw IllegalStateException("boom") }
        helper.providersOverride = listOf(failure, throwing)

        val result = helper.getLyrics(metadata())

        assertEquals(LyricsEntity.LYRICS_NOT_FOUND, result.lyrics)
        assertEquals("Unknown", result.providerName)
        assertTrue(helper.cache.get("song-id") == null)
    }

    @Test
    fun `offline skips all provider work`() = runTest {
        helper.fetchScope = this
        helper.connectivityOverride = { false }
        val provider = FakeProvider("A")
        helper.providersOverride = listOf(provider)

        val result = helper.getLyrics(metadata())

        assertEquals(LyricsEntity.LYRICS_NOT_FOUND, result.lyrics)
        assertEquals("Unknown", result.providerName)
        assertEquals(0, provider.lyricCalls)
    }

    @Test
    fun `empty provider set returns not found`() = runTest {
        helper.fetchScope = this
        helper.providersOverride = emptyList()

        val result = helper.getLyrics(metadata())

        assertEquals(LyricsEntity.LYRICS_NOT_FOUND, result.lyrics)
        assertEquals("Unknown", result.providerName)
    }

    @Test
    fun `concurrent requests share a single provider fetch`() = runTest {
        helper.fetchScope = this
        val provider = FakeProvider("A") {
            delay(10)
            Result.success("[00:01.00]line")
        }
        helper.providersOverride = listOf(provider)

        val first = async { helper.getLyrics(metadata()) }
        val second = async { helper.getLyrics(metadata()) }
        advanceUntilIdle()

        assertEquals(1, provider.lyricCalls)
        assertEquals(first.await(), second.await())
    }

    @Test
    fun `cache hit is served offline without provider calls`() = runTest {
        helper.fetchScope = this
        val provider = FakeProvider("A")
        helper.providersOverride = listOf(provider)

        val first = helper.getLyrics(metadata())
        helper.connectivityOverride = { false }
        val second = helper.getLyrics(metadata())

        assertEquals(first, second)
        assertEquals(1, provider.lyricCalls)
    }

    @Test
    fun `alternate enumeration delivers every provider page and caches them`() = runTest {
        helper.fetchScope = this
        val a = FakeProvider("A", pages = listOf("a1", "a2"))
        val b = FakeProvider("B", pages = listOf("b1", "b2", "b3"))
        helper.providersOverride = listOf(a, b)

        val collected = mutableListOf<LyricsResult>()
        helper.getAllLyrics(
            mediaId = "id",
            songTitle = "Title",
            songArtists = "Artist",
            duration = 200,
            album = null,
        ) { collected.add(it) }

        assertEquals(5, collected.size)
        assertEquals(setOf("a1", "a2", "b1", "b2", "b3"), collected.map { it.lyrics }.toSet())
        val cached = helper.cache.get("Artist-Title")
        assertNotNull(cached)
        assertEquals(collected.map { it.lyrics }, cached!!.map { it.lyrics })
    }

    @Test
    fun `alternate cache hit replays synchronously`() = runTest {
        helper.fetchScope = this
        val provider = FakeProvider("A", pages = listOf("a1", "a2"))
        helper.providersOverride = listOf(provider)

        val first = mutableListOf<LyricsResult>()
        helper.getAllLyrics("id", "Title", "Artist", 200, null) { first.add(it) }
        val second = mutableListOf<LyricsResult>()
        helper.getAllLyrics("id", "Title", "Artist", 200, null) { second.add(it) }

        assertEquals(first.map { it.lyrics }, second.map { it.lyrics })
        assertEquals(1, provider.allCalls)
    }

    @Test
    fun `alternate cache key removes literal spaces`() = runTest {
        helper.fetchScope = this
        val provider = FakeProvider("A", pages = listOf("a1"))
        helper.providersOverride = listOf(provider)

        helper.getAllLyrics("id", "My Song", "The Artist", 200, null) {}
        helper.getAllLyrics("id", "MySong", "TheArtist", 200, null) {}

        assertEquals(1, provider.allCalls)
        assertNotNull(helper.cache.get("TheArtist-MySong"))
    }

    @Test
    fun `alternate enumeration offline emits nothing`() = runTest {
        helper.fetchScope = this
        helper.connectivityOverride = { false }
        val provider = FakeProvider("A", pages = listOf("a1"))
        helper.providersOverride = listOf(provider)

        val collected = mutableListOf<LyricsResult>()
        helper.getAllLyrics("id", "Title", "Artist", 200, null) { collected.add(it) }

        assertTrue(collected.isEmpty())
        assertTrue(helper.cache.get("Artist-Title") == null)
        assertEquals(0, provider.allCalls)
    }

    @Test
    fun `provider failures during enumeration are isolated`() = runTest {
        helper.fetchScope = this
        val failing = FakeProvider("A") { throw IllegalStateException("boom") }
        val healthy = FakeProvider("B", pages = listOf("b1"))
        helper.providersOverride = listOf(failing, healthy)

        val collected = mutableListOf<LyricsResult>()
        helper.getAllLyrics("id", "Title", "Artist", 200, null) { collected.add(it) }

        assertEquals(listOf("b1"), collected.map { it.lyrics })
    }

    @Test
    fun `cancel current lyrics job is idempotent`() = runTest {
        helper.fetchScope = this
        helper.cancelCurrentLyricsJob()
        helper.cancelCurrentLyricsJob()
    }

    @Test
    fun `lru cache evicts the oldest of twenty one keys`() = runTest {
        helper.fetchScope = this
        helper.cache = LruResultCache(20)
        val provider = FakeProvider("A")
        helper.providersOverride = listOf(provider)

        for (index in 0..20) {
            helper.getLyrics(metadata("id-$index"))
        }

        assertNull(helper.cache.get("id-0"))
        assertNotNull(helper.cache.get("id-20"))
    }
}
