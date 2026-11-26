package com.revenge.lrclib

import com.revenge.metadata.cleaner.MetadataCleaner
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(InternalAPI::class)
private class FakeEngine(
  private val handler: suspend (HttpRequestData) -> HttpResponseData,
) : HttpClientEngineBase("lrclib-fake") {
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
    Job(),
  )

private fun trackJson(
  id: Int,
  duration: Double,
  synced: String? = null,
  plain: String? = null,
  name: String = "Song",
  artist: String = "Artist",
): String {
  fun value(raw: String?): String = if (raw == null) "null" else "\"$raw\""
  return "{\"id\":$id,\"trackName\":\"$name\",\"artistName\":\"$artist\"," +
    "\"duration\":$duration,\"plainLyrics\":${value(plain)},\"syncedLyrics\":${value(synced)}}"
}

class LrcLibLyricsTest {
  @Before
  fun setUp() {
    LrcLib.testClient = null
  }

  @After
  fun tearDown() {
    LrcLib.testClient = null
  }

  @Test
  fun `parses two timestamped lines`() {
    val sentences = LrcLib.Lyrics("[00:01.00]first line\n[01:23.45]second line").sentences
    assertEquals("first line", sentences?.get(1_000L))
    assertEquals("second line", sentences?.get(83_450L))
  }

  @Test
  fun `always includes the zero entry`() {
    val sentences = LrcLib.Lyrics("[00:10.00]hello").sentences
    assertEquals(mapOf(0L to "", 10_000L to "hello"), sentences)
  }

  @Test
  fun `scales fractional seconds`() {
    val sentences = LrcLib.Lyrics("[00:00.5]a\n[00:01.50]b\n[00:02.500]c").sentences
    assertEquals("a", sentences?.get(500L))
    assertEquals("b", sentences?.get(1_500L))
    assertEquals("c", sentences?.get(2_500L))
  }

  @Test
  fun `shares content between multiple timestamps`() {
    val sentences = LrcLib.Lyrics("[00:01.00][00:02.50]shared").sentences
    assertEquals("shared", sentences?.get(1_000L))
    assertEquals("shared", sentences?.get(2_500L))
  }

  @Test
  fun `ignores lines without timestamps`() {
    val sentences = LrcLib.Lyrics("not a timestamp\n[00:03.00]text").sentences
    assertEquals(2, sentences?.size)
    assertEquals("text", sentences?.get(3_000L))
  }

  @Test
  fun `empty input yields the zero entry`() {
    assertEquals(mapOf(0L to ""), LrcLib.Lyrics("").sentences)
  }

  @Test
  fun `accepts colon as fraction separator`() {
    val sentences = LrcLib.Lyrics("[01:05:25]dash").sentences
    assertEquals("dash", sentences?.get(65_250L))
  }

  @Test
  fun `malformed input never throws`() {
    val sentences = LrcLib.Lyrics("[xx:yy]text\n[00:01.00 text").sentences
    assertNotNull(sentences)
    assertEquals(mapOf(0L to ""), sentences)
  }

  @Test
  fun `later duplicate timestamp overwrites the earlier content`() {
    val sentences = LrcLib.Lyrics("[00:01.00]first\n[00:01.00]second").sentences
    assertEquals("second", sentences?.get(1_000L))
  }

  @Test
  fun `first widened query with a usable track wins`() = runBlocking {
    val engine = FakeEngine { data ->
      reply(data, HttpStatusCode.OK, "[${trackJson(1, 100.0, plain = "plain text")}]")
    }
    LrcLib.testClient = HttpClient(engine) { expectSuccess = true }

    val result = LrcLib.getLyrics("Song", "Artist", 100)

    assertTrue(result.isSuccess)
    assertEquals("plain text", result.getOrNull())
    assertEquals(1, engine.requests.size)
  }

  @Test
  fun `identical track prefers synced over plain`() = runBlocking {
    val engine = FakeEngine { data ->
      reply(
        data,
        HttpStatusCode.OK,
        "[${trackJson(1, 100.0, synced = "[00:01.00]synced", plain = "plain")}]",
      )
    }
    LrcLib.testClient = HttpClient(engine) { expectSuccess = true }

    val result = LrcLib.getLyrics("Song", "Artist", 100)

    assertEquals("[00:01.00]synced", result.getOrNull())
  }

  @Test
  fun `query without a usable track falls through to the next query`() = runBlocking {
    val engine = FakeEngine { data ->
      val params = data.url.parameters
      val body = if (params["track_name"] != null && params["artist_name"] != null) {
        "[${trackJson(1, 100.0)}]"
      } else if (params["track_name"] != null) {
        "[${trackJson(2, 100.0, synced = "[00:01.00]from third query")}]"
      } else {
        "[${trackJson(3, 100.0)}]"
      }
      reply(data, HttpStatusCode.OK, body)
    }
    LrcLib.testClient = HttpClient(engine) { expectSuccess = true }

    val result = LrcLib.getLyrics("Song", "Artist", 100)

    assertTrue(result.isSuccess)
    assertEquals("[00:01.00]from third query", result.getOrNull())
    assertTrue(engine.requests.size >= 2)
  }

  @Test
  fun `every failing query surfaces one failure and one request per query`() = runBlocking {
    val engine = FakeEngine { data -> reply(data, HttpStatusCode.InternalServerError, "") }
    LrcLib.testClient = HttpClient(engine) { expectSuccess = true }

    val expectedQueries = expectedQueryCount("Song", "Artist")

    val result = LrcLib.getLyrics("Song", "Artist", 100)

    assertTrue(result.isFailure)
    assertEquals("Lyrics unavailable", result.exceptionOrNull()?.message)
    assertEquals(expectedQueries, engine.requests.size)

    val list = LrcLib.lyrics("Artist", "Song")
    assertTrue(list.isSuccess)
    assertTrue(list.getOrThrow().isEmpty())
    assertEquals(expectedQueries * 2, engine.requests.size)
  }

  @Test
  fun `getAllLyrics orders by duration distance and emits one plain document`() = runBlocking {
    val tracks = listOf(
      trackJson(1, 101.0, synced = "S101"),
      trackJson(2, 104.0, synced = "S104", plain = "P104"),
      trackJson(3, 99.0, synced = "S99"),
      trackJson(4, 120.0, synced = "S120"),
      trackJson(5, 102.0, synced = "S102", plain = "P102"),
    )
    val engine = FakeEngine { data -> reply(data, HttpStatusCode.OK, tracks.joinToString(",", "[", "]")) }
    LrcLib.testClient = HttpClient(engine) { expectSuccess = true }

    val delivered = ArrayList<String>()
    LrcLib.getAllLyrics("Song", "Artist", 100) { delivered.add(it) }

    assertEquals(listOf("S101", "S99", "S102", "P102", "S104"), delivered)
  }

  @Test
  fun `getAllLyrics caps deliveries at five documents`() = runBlocking {
    val tracks = (1..8).map { index -> trackJson(index, 100.0, synced = "S$index") }
    val engine = FakeEngine { data -> reply(data, HttpStatusCode.OK, tracks.joinToString(",", "[", "]")) }
    LrcLib.testClient = HttpClient(engine) { expectSuccess = true }

    val delivered = ArrayList<String>()
    LrcLib.getAllLyrics("Song", "Artist", 100) { delivered.add(it) }

    assertEquals(5, delivered.size)
    assertEquals(listOf("S1", "S2", "S3", "S4", "S5"), delivered)
  }

  @Test
  fun `getAllLyrics with unknown duration ranks synced tracks ahead of plain`() = runBlocking {
    val tracks = listOf(
      trackJson(1, 10.0, plain = "plain only"),
      trackJson(2, 20.0, synced = "synced"),
    )
    val engine = FakeEngine { data -> reply(data, HttpStatusCode.OK, tracks.joinToString(",", "[", "]")) }
    LrcLib.testClient = HttpClient(engine) { expectSuccess = true }

    val delivered = ArrayList<String>()
    LrcLib.getAllLyrics("Song", "Artist", -1) { delivered.add(it) }

    assertEquals(listOf("synced"), delivered)
  }

  private fun expectedQueryCount(title: String, artist: String): Int {
    val cleaned = MetadataCleaner.clean(title, artist)
    val (searchTitle, searchArtist) = cleaned.toSearchQuery()
    var count = 1
    if (cleaned.primaryArtist != searchArtist) count += 1
    count += 3
    if (searchTitle != title.trim()) count += 1
    return count
  }
}
