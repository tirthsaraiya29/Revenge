package com.revenge.simpmusic

import com.revenge.simpmusic.models.LyricsData
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(InternalAPI::class)
private class FakeEngine(
  private val handler: suspend (HttpRequestData) -> HttpResponseData,
) : HttpClientEngineBase("simpmusic-fake") {
  val requests = CopyOnWriteArrayList<String>()

  override val config: HttpClientEngineConfig = HttpClientEngineConfig()

  override suspend fun execute(data: HttpRequestData): HttpResponseData {
    requests.add(data.url.toString())
    return handler(data)
  }
}

private fun reply(data: HttpRequestData, status: HttpStatusCode, body: String): HttpResponseData =
  HttpResponseData(
    status,
    GMTDate(),
    headersOf(HttpHeaders.ContentType, "application/json"),
    HttpProtocolVersion.HTTP_1_1,
    ByteReadChannel(body),
    data.executionContext,
  )

private fun data(
  duration: Int? = 100,
  synced: String? = null,
  plain: String? = null,
  rich: String? = null,
): LyricsData = LyricsData(
  duration = duration,
  syncedLyrics = synced,
  plainLyrics = plain,
  richSyncLyrics = rich,
)

class LyricsSelectionTest {
  @Before
  fun setUp() {
    SimpMusicLyrics.testClient = null
  }

  @After
  fun tearDown() {
    SimpMusicLyrics.testClient = null
  }

  @Test
  fun `duration distance treats a missing duration as zero`() {
    assertEquals(120, data(duration = null).durationDistance(120))
    assertEquals(20, data(duration = 100).durationDistance(120))
  }

  @Test
  fun `duration matching allows ten seconds of slack`() {
    assertTrue(data(duration = 500).matchesDuration(0))
    assertTrue(data(duration = 500).matchesDuration(-5))
    assertTrue(data(duration = 110).matchesDuration(120))
    assertTrue(data(duration = 130).matchesDuration(120))
    assertFalse(data(duration = 109).matchesDuration(120))
    assertFalse(data(duration = 131).matchesDuration(120))
  }

  @Test
  fun `lyrics preference is rich then synced then plain`() {
    assertEquals("rich", data(rich = "rich", synced = "synced", plain = "plain").bestLyricsText())
    assertEquals("synced", data(rich = " ", synced = "synced", plain = "plain").bestLyricsText())
    assertEquals("plain", data(synced = "", plain = "plain").bestLyricsText())
    assertNull(data(synced = " ", plain = "").bestLyricsText())
  }

  @Test
  fun `selection uses the first result for an unknown duration`() {
    val tracks = listOf(data(duration = 300, plain = "first"), data(duration = 100, plain = "second"))
    assertEquals("first", selectLyrics(tracks, 0))
  }

  @Test
  fun `selection picks the closest matching duration`() {
    val tracks = listOf(
      data(duration = 240, plain = "far"),
      data(duration = 202, plain = "closest"),
      data(duration = 215, plain = "mid"),
    )
    assertEquals("closest", selectLyrics(tracks, 200))
    assertNull(selectLyrics(listOf(data(duration = 300, plain = "none")), 200))
    assertNull(selectLyrics(emptyList(), 200))
  }

  @Test
  fun `alternative collection orders by distance and emits one plain document`() {
    val tracks = listOf(
      data(duration = 104, synced = "S104", plain = "P104"),
      data(duration = 101, synced = "S101"),
      data(duration = 150, synced = "S150"),
      data(duration = 102, synced = "S102", plain = "P102"),
    )

    assertEquals(listOf("S101", "S102", "P102", "S104"), collectAlternativeLyrics(tracks, 100))
  }

  @Test
  fun `alternative collection is capped at five documents`() {
    val tracks = (1..8).map { index -> data(duration = 100, synced = "S$index") }
    assertEquals(5, collectAlternativeLyrics(tracks, 100).size)
  }

  @Test
  fun `primary failure falls through to the fallback`() = runBlocking {
    val engine = FakeEngine { request ->
      val host = request.url.host
      if (host == "api-lyrics.simpmusic.org") {
        reply(request, HttpStatusCode.InternalServerError, "")
      } else {
        reply(request, HttpStatusCode.OK, """{"type":"success","data":[{"songTitle":"x","durationSeconds":100,"plainLyric":"fallback"}]}""")
      }
    }
    SimpMusicLyrics.testClient = HttpClient(engine)

    val tracks = SimpMusicLyrics.getLyricsByVideoId("v1")

    assertEquals(1, tracks.size)
    assertEquals("fallback", tracks.single().plainLyrics)
    assertEquals(2, engine.requests.size)
  }

  @Test
  fun `unsuccessful primary payload does not trigger the fallback`() = runBlocking {
    val engine = FakeEngine { request ->
      reply(request, HttpStatusCode.OK, """{"type":"error","data":[{"songTitle":"x"}]}""")
    }
    SimpMusicLyrics.testClient = HttpClient(engine)

    val tracks = SimpMusicLyrics.getLyricsByVideoId("v1")

    assertTrue(tracks.isEmpty())
    assertEquals(1, engine.requests.size)
  }

  @Test
  fun `transport failures on both endpoints yield an empty list`() = runBlocking {
    val engine = FakeEngine { request -> reply(request, HttpStatusCode.InternalServerError, "") }
    SimpMusicLyrics.testClient = HttpClient(engine)

    val tracks = SimpMusicLyrics.getLyricsByVideoId("v1")

    assertTrue(tracks.isEmpty())
    assertEquals(2, engine.requests.size)
  }

  @Test
  fun `getLyrics fails when no candidate matches the duration`() = runBlocking {
    val engine = FakeEngine { request ->
      reply(request, HttpStatusCode.OK, """{"type":"success","data":[{"songTitle":"x","durationSeconds":500,"plainLyric":"far"}]}""")
    }
    SimpMusicLyrics.testClient = HttpClient(engine)

    val result = SimpMusicLyrics.getLyrics("v1", 100)

    assertTrue(result.isFailure)
    assertEquals("Lyrics unavailable", result.exceptionOrNull()?.message)
  }

  @Test
  fun `getLyrics succeeds with an unsynced document`() = runBlocking {
    val engine = FakeEngine { request ->
      reply(request, HttpStatusCode.OK, """{"type":"success","data":[{"songTitle":"x","durationSeconds":100,"plainLyric":"plain text"}]}""")
    }
    SimpMusicLyrics.testClient = HttpClient(engine)

    val result = SimpMusicLyrics.getLyrics("v1", 100)

    assertEquals("plain text", result.getOrNull())
  }
}
