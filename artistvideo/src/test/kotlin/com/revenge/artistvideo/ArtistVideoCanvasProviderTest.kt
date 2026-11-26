package com.revenge.artistvideo

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException

class ArtistVideoCanvasProviderTest {
    private data class Request(val target: String, val params: Map<String, String>, val headers: Map<String, String>)

    private class FakeTransport : ArtistVideoTransport {
        var response = ArtistVideoHttpResponse(200, "{}")
        var failure: Throwable? = null
        val requests = mutableListOf<Request>()

        override suspend fun get(
            target: String,
            params: List<Pair<String, String>>,
            headers: Map<String, String>,
        ): ArtistVideoHttpResponse {
            requests += Request(target, params.toMap(), headers.toMap())
            failure?.let { throw it }
            return response
        }
    }

    private lateinit var transport: FakeTransport
    private var now = 1_000_000L

    @Before
    fun setUp() {
        ArtistVideoCanvasProvider.resetForTests()
        transport = FakeTransport()
        ArtistVideoCanvasProvider.transport = transport
        ArtistVideoCanvasProvider.clock = { now }
    }

    @Test
    fun ap1_songArtistOnlySendsRequiredParams() = runBlocking {
        ArtistVideoCanvasProvider.getBySongArtist("Song", "Artist")

        val params = transport.requests.single().params
        assertEquals("Song", params["s"])
        assertEquals("Artist", params["a"])
        assertFalse(params.containsKey("al"))
        assertFalse(params.containsKey("d"))
    }

    @Test
    fun ap2_albumAndPositiveDurationAreSent() = runBlocking {
        ArtistVideoCanvasProvider.getBySongArtist("Song", "Artist", album = "Album", duration = 215)

        val params = transport.requests.single().params
        assertEquals("Album", params["al"])
        assertEquals("215", params["d"])
    }

    @Test
    fun ap3_zeroOrNegativeDurationIsOmitted() = runBlocking {
        ArtistVideoCanvasProvider.getBySongArtist("Song", "Artist", duration = 0)
        ArtistVideoCanvasProvider.getBySongArtist("Song", "Artist", duration = -5)

        assertTrue(transport.requests.all { !it.params.containsKey("d") })
    }

    @Test
    fun ap4_decodesSuccessfulResponse() = runBlocking {
        transport.response = ArtistVideoHttpResponse(
            200,
            """{"name":"N","artist":"A","albumId":"42","animated":"https://cdn/a.m3u8"}""",
        )

        val response = ArtistVideoCanvasProvider.getBySongArtist("Song", "Artist")

        assertEquals("N", response?.name)
        assertEquals("A", response?.artist)
        assertEquals("42", response?.albumId)
        assertEquals("https://cdn/a.m3u8", response?.preferredAnimationUrl)
    }

    @Test
    fun ap5_malformedJsonReturnsNullAndCachesMiss() = runBlocking {
        transport.response = ArtistVideoHttpResponse(200, "not-json")

        assertNull(ArtistVideoCanvasProvider.getBySongArtist("Song", "Artist"))
        assertNull(ArtistVideoCanvasProvider.getBySongArtist("Song", "Artist"))

        assertEquals(1, transport.requests.size)
    }

    @Test
    fun ap6_transportAndHttpFailuresAreCachedAsNull() = runBlocking {
        transport.response = ArtistVideoHttpResponse(500, "")
        assertNull(ArtistVideoCanvasProvider.getBySongArtist("Song", "Artist"))
        assertNull(ArtistVideoCanvasProvider.getBySongArtist("Song", "Artist"))
        assertEquals(1, transport.requests.size)

        transport.failure = IOException("boom")
        assertNull(ArtistVideoCanvasProvider.getBySongArtist("Other Song", "Other Artist"))
    }

    @Test
    fun ap7_secondCallWithinTtlUsesCache() = runBlocking {
        transport.response = ArtistVideoHttpResponse(200, """{"name":"N"}""")

        val first = ArtistVideoCanvasProvider.getBySongArtist("Song", "Artist")
        val second = ArtistVideoCanvasProvider.getBySongArtist("Song", "Artist")

        assertEquals(first, second)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun ap8_expiredEntryRefetches() = runBlocking {
        transport.response = ArtistVideoHttpResponse(200, """{"name":"N"}""")

        ArtistVideoCanvasProvider.getBySongArtist("Song", "Artist")
        now += 60_001L
        ArtistVideoCanvasProvider.getBySongArtist("Song", "Artist")

        assertEquals(2, transport.requests.size)
    }

    @Test
    fun ap9_cancellationIsRethrown() = runBlocking {
        val cancelled = CancellationException("stop")
        transport.failure = cancelled

        var caught: Throwable? = null
        try {
            ArtistVideoCanvasProvider.getBySongArtist("Song", "Artist")
        } catch (e: CancellationException) {
            caught = e
        }
        assertSame(cancelled, caught)
    }

    @Test
    fun ap10_blankInputsStillRequest() = runBlocking {
        val response = ArtistVideoCanvasProvider.getBySongArtist("", "")

        assertNotNull(response)
        assertEquals(1, transport.requests.size)
    }
}
