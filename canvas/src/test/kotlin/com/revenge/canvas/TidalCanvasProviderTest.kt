package com.revenge.canvas

import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TidalCanvasProviderTest {
  private data class Request(
    val target: String,
    val params: Map<String, String>,
    val headers: Map<String, String>,
  )

  private class FakeTransport : CanvasTransport {
    var response = CanvasHttpResponse(200, "{}")
    var failure: Throwable? = null
    val requests = mutableListOf<Request>()

    override suspend fun get(
      target: String,
      params: List<Pair<String, String>>,
      headers: Map<String, String>,
    ): CanvasHttpResponse {
      requests += Request(target, params.toMap(), headers.toMap())
      failure?.let { throw it }
      return response
    }
  }

  private lateinit var transport: FakeTransport
  private var now = 1_000_000L

  @Before
  fun setUp() {
    TidalCanvasProvider.resetForTests()
    transport = FakeTransport()
    TidalCanvasProvider.transport = transport
    TidalCanvasProvider.clock = { now }
  }

  private fun tracksBody(vararg items: String): String =
    """{"tracks":{"items":[${items.joinToString(",")}]}}"""

  private fun albumBody(vararg items: String): String =
    """{"albums":{"items":[${items.joinToString(",")}]}}"""

  private fun trackItem(
    title: String = "Blinding Lights",
    artist: String = "The Weeknd",
    albumTitle: String = "After Hours",
    cover: String = "aa-bb-cc-dd-ee",
  ): String =
    """
        {"title":"$title","artist":{"name":"$artist"},
         "album":{"title":"$albumTitle","videoCover":"$cover"}}
    """
      .trimIndent()

  @Test
  fun td1_formatsFiveSegmentCoverId() {
    assertEquals(
      "https://resources.tidal.com/videos/aa/bb/cc/dd/ee/1280x1280.mp4",
      tidalCoverUrl("aa-bb-cc-dd-ee"),
    )
  }

  @Test
  fun td2_rejectsMalformedCoverIds() {
    assertNull(tidalCoverUrl(""))
    assertNull(tidalCoverUrl("aa-bb-cc"))
    assertNull(tidalCoverUrl("aa-bb-cc-dd-ee-ff"))
    assertNull(tidalCoverUrl("aa-bb--dd-ee"))
  }

  @Test
  fun td3_artistMatchIsCaseInsensitive() {
    assertTrue(artistsMatch("The Weeknd", "The Weeknd"))
    assertTrue(artistsMatch("the weeknd", "THE WEEKND"))
  }

  @Test
  fun td4_artistSeparatorsMatchIndividualArtists() {
    assertTrue(artistsMatch("The Weeknd feat. Daft Punk", "The Weeknd"))
    assertTrue(artistsMatch("Calvin Harris & Dua Lipa", "Dua Lipa"))
    assertTrue(artistsMatch("Drake, Future", "Future"))
    assertTrue(artistsMatch("Charli xcx", "Charli XCX"))
  }

  @Test
  fun td5_unrelatedOrBlankArtistsDoNotMatch() {
    assertFalse(artistsMatch("The Weeknd", "Daft Punk"))
    assertFalse(artistsMatch("", "Daft Punk"))
  }

  @Test
  fun td6_blankInputsMakeNoRequest() = runBlocking {
    assertNull(TidalCanvasProvider.getBySongArtist("", "Artist"))
    assertNull(TidalCanvasProvider.getBySongArtist("Song", " "))
    assertTrue(transport.requests.isEmpty())
  }

  @Test
  fun td7_mapsMatchingTrackResult() = runBlocking {
    transport.response = CanvasHttpResponse(200, tracksBody(trackItem()))

    val artwork = TidalCanvasProvider.getBySongArtist("Blinding Lights", "The Weeknd")

    assertEquals("Blinding Lights", artwork?.name)
    assertEquals("The Weeknd", artwork?.artist)
    assertEquals("After Hours", artwork?.albumName)
    assertEquals(
      "https://resources.tidal.com/videos/aa/bb/cc/dd/ee/1280x1280.mp4",
      artwork?.videoUrl,
    )
    assertNull(artwork?.animated)
    assertNull(artwork?.static)
    assertNull(artwork?.clipStartMs)
    assertEquals("TRACKS", transport.requests.single().params["types"])
  }

  @Test
  fun td8_skipsMismatchedCandidates() = runBlocking {
    transport.response =
      CanvasHttpResponse(
        200,
        tracksBody(
          trackItem(title = "Different Song"),
          trackItem(artist = "Other Artist"),
          trackItem(title = "Blinding Lights", artist = "The Weeknd"),
        ),
      )

    val artwork = TidalCanvasProvider.getBySongArtist("Blinding Lights", "The Weeknd")

    assertEquals("Blinding Lights", artwork?.name)
    assertEquals("The Weeknd", artwork?.artist)
  }

  @Test
  fun td9_failuresReturnNull() = runBlocking {
    transport.response = CanvasHttpResponse(500, "")
    assertNull(TidalCanvasProvider.getBySongArtist("Song", "Artist"))

    transport.response = CanvasHttpResponse(200, "not-json")
    assertNull(TidalCanvasProvider.getBySongArtist("Song", "Artist"))

    transport.failure = IOException("boom")
    assertNull(TidalCanvasProvider.getBySongArtist("Song", "Artist"))
  }

  @Test
  fun td10_successIsCachedWithinTtl() = runBlocking {
    transport.response = CanvasHttpResponse(200, tracksBody(trackItem()))

    TidalCanvasProvider.getBySongArtist("Blinding Lights", "The Weeknd")
    TidalCanvasProvider.getBySongArtist("Blinding Lights", "The Weeknd")

    assertEquals(1, transport.requests.size)
  }

  @Test
  fun td11_failuresAreNotCached() = runBlocking {
    transport.response = CanvasHttpResponse(500, "")

    TidalCanvasProvider.getBySongArtist("Song", "Artist")
    TidalCanvasProvider.getBySongArtist("Song", "Artist")

    assertEquals(2, transport.requests.size)
  }

  @Test
  fun td12_cancellationIsRethrown() = runBlocking {
    val cancelled = CancellationException("stop")
    transport.failure = cancelled

    var caught: Throwable? = null
    try {
      TidalCanvasProvider.getBySongArtist("Song", "Artist")
    } catch (e: CancellationException) {
      caught = e
    }
    assertSame(cancelled, caught)
  }
}
