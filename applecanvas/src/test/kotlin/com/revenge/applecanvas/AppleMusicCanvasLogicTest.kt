package com.revenge.applecanvas

import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppleMusicCanvasLogicTest {
  private data class Request(
    val target: String,
    val params: Map<String, String>,
    val headers: Map<String, String>,
  )

  private class FakeTransport : AmpTransport {
    var handler: suspend (Request) -> AmpHttpResponse = { AmpHttpResponse(404, "") }
    val requests = mutableListOf<Request>()

    override suspend fun get(
      target: String,
      params: List<Pair<String, String>>,
      headers: Map<String, String>,
    ): AmpHttpResponse {
      val request = Request(target, params.toMap(), headers.toMap())
      requests += request
      return handler(request)
    }
  }

  private lateinit var transport: FakeTransport
  private var now = 1_000_000L
  private var invalidations = 0

  @Before
  fun setUp() {
    AppleMusicCanvasProvider.resetForTests()
    transport = FakeTransport()
    invalidations = 0
    AppleMusicCanvasProvider.transport = transport
    AppleMusicCanvasProvider.clock = { now }
    AppleMusicCanvasProvider.tokenSource = { "test-token" }
    AppleMusicCanvasProvider.tokenInvalidator = { invalidations++ }
  }

  private fun obj(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

  private fun attributes(
    name: String,
    collection: String? = null,
    artist: String = "The Beatles",
  ): JsonObject {
    val builder = StringBuilder()
    builder
      .append("{\"name\":\"")
      .append(name)
      .append("\",\"artistName\":\"")
      .append(artist)
      .append('"')
    if (collection != null) {
      builder.append(",\"collectionName\":\"").append(collection).append('"')
    }
    builder.append('}')
    return obj(builder.toString())
  }

  private fun directSongItem(url: String = "https://cdn/direct.mp4"): String =
    """
        {"id":"item1","type":"songs","attributes":{"name":"Come Together","artistName":"The Beatles",
         "collectionName":"Abbey Road","collectionId":"alb1",
         "editorialVideo":{"motionSquareVideo1x1":{"video":"$url"}}}}
    """
      .trimIndent()

  private fun plainSongItem(): String =
    """
    {"id":"item1","type":"songs","attributes":{"name":"Come Together","artistName":"The Beatles",
     "collectionName":"Abbey Road","collectionId":"alb1"}}
    """
      .trimIndent()

  private fun searchBody(types: String, item: String): String =
    """{"results":{"$types":{"data":[$item]}}}"""

  private fun albumBody(url: String = "https://cdn/album.mp4"): String =
    """
        {"data":[{"attributes":{"name":"Abbey Road","artistName":"The Beatles",
         "editorialVideo":{"motionSquareVideo1x1":{"video":"$url"}}}}]}
    """
      .trimIndent()

  @Test
  fun am1_queryDoesNotDuplicatePresentArtist() {
    assertEquals(
      "The Beatles Abbey Road",
      buildQuery("The Beatles Abbey Road", "The Beatles", null),
    )
  }

  @Test
  fun am2_queryPrependsArtistAndAppendsAlbum() {
    assertEquals(
      "The Beatles Come Together Abbey Road",
      buildQuery("Come Together", "The Beatles", "Abbey Road"),
    )
  }

  @Test
  fun am3_queryDoesNotRepeatAlbumAlreadyPresent() {
    assertEquals("Adele 25", buildQuery("25", "Adele", "25"))
  }

  @Test
  fun am4_exactAlbumScoresTwentyFive() {
    val score =
      candidateScore("Abbey Road", "The Beatles", null, attributes("Abbey Road", "Abbey Road"))
    assertEquals(25, score)
    assertTrue(score!! >= 12)
  }

  @Test
  fun am5_exactSongOnMatchingAlbumScoresFortyFive() {
    assertEquals(
      45,
      candidateScore(
        "Come Together",
        "The Beatles",
        "Abbey Road",
        attributes("Come Together", "Abbey Road"),
      ),
    )
  }

  @Test
  fun am6_wrongArtistIsRejected() {
    assertNull(
      candidateScore("Abbey Road", "The Beatles", null, attributes("Abbey Road", artist = "Adele")),
    )
  }

  @Test
  fun am7_playlistNamesAreRejected() {
    assertNull(
      candidateScore(
        "Today's Hits",
        "Various",
        null,
        attributes("Today's Hits", artist = "Various"),
      ),
    )
    assertNull(
      candidateScore(
        "Abbey Road",
        "The Beatles",
        null,
        attributes("Abbey Road", "The Beatles Essentials"),
      ),
    )
  }

  @Test
  fun am8_deluxeMarkerScoresLowerThanPlain() {
    val plain = candidateScore("Abbey Road", "The Beatles", null, attributes("Abbey Road"))
    val deluxe =
      candidateScore("Abbey Road", "The Beatles", null, attributes("Abbey Road (Deluxe)"))
    assertTrue(deluxe!! < plain!!)
  }

  @Test
  fun am9_songAlbumResolutionPrefersRelationship() {
    val item =
      obj("""{"id":"root","type":"songs","relationships":{"albums":{"data":[{"id":"rel1"}]}}}""")
    val attrs =
      obj("""{"collectionId":"coll1","url":"https://music.apple.com/us/album/slug/999?i=1"}""")
    assertEquals("rel1", resolveAlbumId(item, attrs))
  }

  @Test
  fun am10_songAlbumResolutionFallsBackToCollectionThenUrl() {
    val song = obj("""{"id":"root","type":"songs"}""")
    assertEquals("coll1", resolveAlbumId(song, obj("""{"collectionId":"coll1"}""")))
    assertEquals(
      "123456",
      resolveAlbumId(
        song,
        obj("""{"url":"https://music.apple.com/us/album/slug/123456?i=1"}"""),
      ),
    )
  }

  @Test
  fun am11_playlistItemIdIsRejected() {
    assertNull(resolveAlbumId(obj("""{"id":"pl.abc","type":"albums"}"""), obj("{}")))
  }

  @Test
  fun am12_catalogUrlIdExtraction() {
    assertEquals(
      "1441164426",
      albumIdFromUrl("https://music.apple.com/us/album/abbey-road/1441164426?i=1"),
    )
    assertNull(albumIdFromUrl("https://music.apple.com/us/album/abbey-road"))
    assertNull(albumIdFromUrl(null))
  }

  @Test
  fun am13_editorialPrefersKeyOrderOverInsertionOrder() {
    val editorial =
      obj(
        """{"motionDetailSquare":{"video":"detail"},
                "motionSquareVideo1x1":{"hlsUrl":"square-hls"}}""",
      )
    assertEquals("square-hls", extractEditorialUrl(editorial))
  }

  @Test
  fun am14_editorialFallsBackToUnknownObject() {
    assertEquals(
      "unknown-url",
      extractEditorialUrl(obj("""{"custom":{"videoUrl":"unknown-url"}}""")),
    )
  }

  @Test
  fun am15_editorialWithoutUrlFieldsIsNull() {
    assertNull(extractEditorialUrl(obj("""{"motionDetailSquare":{"width":100,"height":100}}""")))
  }

  @Test
  fun am16_albumResponseMapsAllFields() {
    val artwork =
      mapAlbumResponse(
        Json.parseToJsonElement(albumBody("https://cdn/album.mp4")),
        albumId = "alb1",
        fallbackArtist = "",
        titleOverride = null,
        artistOverride = null,
      )
    assertEquals("Abbey Road", artwork?.name)
    assertEquals("The Beatles", artwork?.artist)
    assertEquals("alb1", artwork?.albumId)
    assertEquals("Abbey Road", artwork?.albumName)
    assertEquals("https://cdn/album.mp4", artwork?.animated)
  }

  @Test
  fun am17_songOverridesApplyToMappedResponse() {
    val artwork =
      mapAlbumResponse(
        Json.parseToJsonElement(albumBody()),
        albumId = "alb1",
        fallbackArtist = "",
        titleOverride = "Come Together",
        artistOverride = "The Beatles",
      )
    assertEquals("Come Together", artwork?.name)
    assertEquals("The Beatles", artwork?.artist)
    assertEquals("Abbey Road", artwork?.albumName)
  }

  @Test
  fun am18_blacklistedAlbumResponseIsRejected() {
    val root =
      obj(
        """{"data":[{"attributes":{"name":"Today's Hits",
                "editorialVideo":{"motionSquareVideo1x1":{"video":"https://cdn/x.mp4"}}}}]}""",
      )
    assertNull(mapAlbumResponse(root, "alb1", "", null, null))
  }

  @Test
  fun am19_albumWithoutEditorialIsNull() {
    val root = obj("""{"data":[{"attributes":{"name":"Abbey Road"}}]}""")
    assertNull(mapAlbumResponse(root, "alb1", "", null, null))
  }

  @Test
  fun am20_cacheIgnoresCaseAndSurroundingWhitespace() = runBlocking {
    transport.handler = { AmpHttpResponse(200, searchBody("songs", directSongItem())) }

    val first =
      AppleMusicCanvasProvider.getBySongArtist("Come Together", "The Beatles", "Abbey Road")
    val second =
      AppleMusicCanvasProvider.getBySongArtist("  come together ", "THE BEATLES", "abbey road ")

    assertEquals(first, second)
    assertEquals(1, transport.requests.size)
  }

  @Test
  fun am21_blankInputsMakeNoRequest() = runBlocking {
    assertNull(AppleMusicCanvasProvider.getByAlbumArtist("", "The Beatles"))
    assertNull(AppleMusicCanvasProvider.getBySongArtist("Come Together", " "))
    assertTrue(transport.requests.isEmpty())
  }

  @Test
  fun am22_unauthorizedSearchInvalidatesToken() = runBlocking {
    transport.handler = { AmpHttpResponse(401, "") }

    assertNull(AppleMusicCanvasProvider.getBySongArtist("Come Together", "The Beatles"))
    assertEquals(1, invalidations)
  }

  @Test
  fun am23_failuresReturnNull() = runBlocking {
    transport.handler = { AmpHttpResponse(500, "") }
    assertNull(AppleMusicCanvasProvider.getBySongArtist("Come Together", "The Beatles"))

    transport.handler = { throw IOException("boom") }
    assertNull(AppleMusicCanvasProvider.getBySongArtist("Come Together", "The Beatles"))

    transport.handler = { AmpHttpResponse(200, "not-json") }
    assertNull(AppleMusicCanvasProvider.getBySongArtist("Come Together", "The Beatles"))
  }

  @Test
  fun am24_searchMissesAreNotCached() = runBlocking {
    transport.handler = { AmpHttpResponse(200, """{"results":{"songs":{"data":[]}}}""") }

    assertNull(AppleMusicCanvasProvider.getBySongArtist("Come Together", "The Beatles"))
    assertNull(AppleMusicCanvasProvider.getBySongArtist("Come Together", "The Beatles"))

    assertEquals(2, transport.requests.size)
  }

  @Test
  fun am25_albumIdMissesAreCached() = runBlocking {
    transport.handler = { AmpHttpResponse(404, "") }

    assertNull(AppleMusicCanvasProvider.getByAlbumId("123"))
    assertNull(AppleMusicCanvasProvider.getByAlbumId("123"))
    assertEquals(1, transport.requests.size)

    transport.requests.clear()
    assertNull(AppleMusicCanvasProvider.getByAlbumId(""))
    assertNull(AppleMusicCanvasProvider.getByAlbumId("pl.abc"))
    assertTrue(transport.requests.isEmpty())
  }

  @Test
  fun am26_directEditorialSkipsAlbumEndpoint() = runBlocking {
    transport.handler = { AmpHttpResponse(200, searchBody("songs", directSongItem())) }

    val artwork =
      AppleMusicCanvasProvider.getBySongArtist("Come Together", "The Beatles", "Abbey Road")

    assertEquals("https://cdn/direct.mp4", artwork?.animated)
    assertEquals(1, transport.requests.size)
    assertTrue(transport.requests.none { it.target.contains("/albums/") })
  }

  @Test
  fun am27_albumEndpointFillsMissingEditorial() = runBlocking {
    transport.handler = { request ->
      if (request.target.contains("/search")) {
        AmpHttpResponse(200, searchBody("songs", plainSongItem()))
      } else {
        AmpHttpResponse(200, albumBody())
      }
    }

    val artwork =
      AppleMusicCanvasProvider.getBySongArtist("Come Together", "The Beatles", "Abbey Road")

    assertEquals("https://cdn/album.mp4", artwork?.animated)
    assertEquals("Come Together", artwork?.name)
    assertEquals("Abbey Road", artwork?.albumName)
    assertEquals(2, transport.requests.size)
  }

  @Test
  fun am28_cancellationIsRethrownFromSearchAndAlbumPaths() = runBlocking {
    val cancelled = CancellationException("stop")
    transport.handler = { throw cancelled }

    var searchCaught: Throwable? = null
    try {
      AppleMusicCanvasProvider.getBySongArtist("Come Together", "The Beatles")
    } catch (e: CancellationException) {
      searchCaught = e
    }
    assertSame(cancelled, searchCaught)

    AppleMusicCanvasProvider.resetForTests()
    AppleMusicCanvasProvider.transport = transport
    AppleMusicCanvasProvider.clock = { now }
    AppleMusicCanvasProvider.tokenSource = { "test-token" }
    AppleMusicCanvasProvider.tokenInvalidator = { invalidations++ }

    transport.handler = { request ->
      if (request.target.contains("/search")) {
        AmpHttpResponse(200, searchBody("songs", plainSongItem()))
      } else {
        throw cancelled
      }
    }
    var albumCaught: Throwable? = null
    try {
      AppleMusicCanvasProvider.getBySongArtist("Come Together", "The Beatles")
    } catch (e: CancellationException) {
      albumCaught = e
    }
    assertSame(cancelled, albumCaught)
  }

  @Test
  fun am29_positiveResultIsCachedUntilTtl() = runBlocking {
    transport.handler = { AmpHttpResponse(200, searchBody("songs", directSongItem())) }

    AppleMusicCanvasProvider.getBySongArtist("Come Together", "The Beatles")
    AppleMusicCanvasProvider.getBySongArtist("Come Together", "The Beatles")
    assertEquals(1, transport.requests.size)

    now += 86_400_001L
    AppleMusicCanvasProvider.getBySongArtist("Come Together", "The Beatles")
    assertEquals(2, transport.requests.size)
  }
}
