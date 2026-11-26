package com.revenge.canvas

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException

class AppleMusicArtistBackgroundProviderTest {
    private class FakeTransport : CanvasTransport {
        var handler: suspend (String) -> CanvasHttpResponse = { CanvasHttpResponse(404, "") }
        val targets = mutableListOf<String>()

        override suspend fun get(
            target: String,
            params: List<Pair<String, String>>,
            headers: Map<String, String>,
        ): CanvasHttpResponse {
            targets += target
            return handler(target)
        }
    }

    private lateinit var transport: FakeTransport
    private var now = 1_000_000L
    private var invalidations = 0

    @Before
    fun setUp() {
        AppleMusicArtistBackgroundProvider.resetForTests()
        transport = FakeTransport()
        invalidations = 0
        AppleMusicArtistBackgroundProvider.transport = transport
        AppleMusicArtistBackgroundProvider.clock = { now }
        AppleMusicArtistBackgroundProvider.tokenSource = { "test-token" }
        AppleMusicArtistBackgroundProvider.tokenInvalidator = { invalidations++ }
    }

    private fun parsed(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    private fun searchResponse(vararg artists: Pair<String, String>): String {
        val data = artists.joinToString(",") { (id, name) ->
            """{"id":"$id","attributes":{"name":"$name"}}"""
        }
        return """{"results":{"artists":{"data":[$data]}}}"""
    }

    private fun detailResponse(attributes: String, extras: String = ""): String =
        """{"data":[{"attributes":$attributes$extras}]}"""

    @Test
    fun ab1_nameScoring() {
        assertEquals(10, nameScore("The Weeknd", "The Weeknd"))
        assertEquals(10, nameScore("the weeknd", "The Weeknd"))
        assertEquals(5, nameScore("Weeknd", "The Weeknd"))
        assertEquals(0, nameScore("The Weeknd", "Daft Punk"))
        assertEquals(0, nameScore("The Weeknd", "   "))
    }

    @Test
    fun ab2_fixedMotionKeyPrecedence() {
        val editorial = parsed(
            """{"motionArtistFullscreen16x9":{"video":"fullscreen"},
                "motionArtistWide16x9":{"video":"wide"}}"""
        )
        assertEquals("wide", firstMotionUrl(editorial))
    }

    @Test
    fun ab3_unknownObjectFallbackScansValues() {
        val editorial = parsed("""{"someCustomMotion":{"video":"custom"}}""")
        assertEquals("custom", firstMotionUrl(editorial))
    }

    @Test
    fun ab4_editorialArtworkIsUsedWhenVideoAbsent() = runBlocking {
        transport.handler = { target ->
            if (target.contains("/search")) {
                CanvasHttpResponse(200, searchResponse("1" to "The Weeknd"))
            } else {
                CanvasHttpResponse(
                    200,
                    detailResponse("""{"editorialArtwork":{"motionDetailTall":{"video":"https://cdn/art.mp4"}}}"""),
                )
            }
        }

        assertEquals("https://cdn/art.mp4", AppleMusicArtistBackgroundProvider.getByArtistName("The Weeknd"))
    }

    @Test
    fun ab5_featuredAlbumMotionIsUsed() = runBlocking {
        transport.handler = { target ->
            if (target.contains("/search")) {
                CanvasHttpResponse(200, searchResponse("1" to "The Weeknd"))
            } else {
                CanvasHttpResponse(
                    200,
                    detailResponse(
                        attributes = """{"name":"The Weeknd"}""",
                        extras = ""","views":{"featured-albums":{"data":[{"attributes":{"editorialVideo":{"motionDetailTall":{"video":"https://cdn/album.mp4"}}}}]}}""",
                    ),
                )
            }
        }

        assertEquals("https://cdn/album.mp4", AppleMusicArtistBackgroundProvider.getByArtistName("The Weeknd"))
    }

    @Test
    fun ab6_noMotionReturnsNullAndCachesMiss() = runBlocking {
        transport.handler = { target ->
            if (target.contains("/search")) {
                CanvasHttpResponse(200, searchResponse("1" to "The Weeknd"))
            } else {
                CanvasHttpResponse(200, detailResponse("""{"name":"The Weeknd"}"""))
            }
        }

        assertNull(AppleMusicArtistBackgroundProvider.getByArtistName("The Weeknd"))
        assertNull(AppleMusicArtistBackgroundProvider.getByArtistName("The Weeknd"))
        assertEquals(2, transport.targets.size)
    }

    @Test
    fun ab7_negativeCacheSkipsSecondRequest() = runBlocking {
        transport.handler = { target ->
            if (target.contains("/search")) {
                CanvasHttpResponse(200, searchResponse("1" to "The Weeknd"))
            } else {
                CanvasHttpResponse(200, detailResponse("""{"name":"The Weeknd"}"""))
            }
        }

        AppleMusicArtistBackgroundProvider.getByArtistName("The Weeknd")
        AppleMusicArtistBackgroundProvider.getByArtistName("The Weeknd")
        assertEquals(1, transport.targets.count { it.contains("/search") })
    }

    @Test
    fun ab8_unauthorizedSearchInvalidatesToken() = runBlocking {
        transport.handler = { CanvasHttpResponse(401, "") }

        assertNull(AppleMusicArtistBackgroundProvider.getByArtistName("The Weeknd"))
        assertEquals(1, invalidations)
    }

    @Test
    fun ab9_failuresReturnNullWithoutThrowing() = runBlocking {
        transport.handler = { CanvasHttpResponse(500, "") }
        assertNull(AppleMusicArtistBackgroundProvider.getByArtistName("The Weeknd"))

        transport.handler = { throw IOException("boom") }
        assertNull(AppleMusicArtistBackgroundProvider.getByArtistName("Other Artist"))

        transport.handler = { CanvasHttpResponse(200, "not-json") }
        assertNull(AppleMusicArtistBackgroundProvider.getByArtistName("Third Artist"))
    }

    @Test
    fun ab10_blankNameMakesNoRequest() = runBlocking {
        assertNull(AppleMusicArtistBackgroundProvider.getByArtistName("   "))
        assertTrue(transport.targets.isEmpty())
    }

    @Test
    fun ab11_substringCandidateIsFetched() = runBlocking {
        transport.handler = { target ->
            if (target.contains("/search")) {
                CanvasHttpResponse(200, searchResponse("1" to "The Weeknd"))
            } else {
                CanvasHttpResponse(200, detailResponse("""{"editorialVideo":{"motionArtistSquare1x1":{"video":"https://cdn/x.mp4"}}}"""))
            }
        }

        assertNotNull(AppleMusicArtistBackgroundProvider.getByArtistName("Weeknd"))
    }

    @Test
    fun ab12_cancellationIsRethrown() = runBlocking {
        val cancelled = CancellationException("stop")
        transport.handler = { throw cancelled }

        var caught: Throwable? = null
        try {
            AppleMusicArtistBackgroundProvider.getByArtistName("The Weeknd")
        } catch (e: CancellationException) {
            caught = e
        }
        assertSame(cancelled, caught)
    }
}
