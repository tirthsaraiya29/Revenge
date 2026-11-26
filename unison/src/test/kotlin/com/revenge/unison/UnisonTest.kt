package com.revenge.unison

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
import kotlinx.coroutines.launch
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
) : HttpClientEngineBase("unison-fake") {
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

private fun entryJson(id: Long, lyrics: String, confidence: String = "\"high\""): String =
  """{"id":$id,"videoId":"v$id","song":"Song $id","artist":"Artist","lyrics":"$lyrics",""" +
    """"format":"lrc","syncType":"synced","confidence":$confidence}"""

private fun singleBody(entry: String): String = """{"success":true,"data":$entry}"""

class UnisonTest {
  @Before
  fun setUp() {
    Unison.testClient = null
    Unison.logger = null
  }

  @After
  fun tearDown() {
    Unison.testClient = null
    Unison.logger = null
  }

  @Test
  fun `parseSingle reads every field and applies defaults`() {
    val entry = Unison.parseSingle(
      """{"success":true,"data":{"id":1,"videoId":"v1","song":"Song","artist":"Artist",""" +
        """"lyrics":"line","format":"lrc","syncType":"synced","confidence":null}}""",
    )

    assertEquals(1L, entry?.id)
    assertEquals("v1", entry?.videoId)
    assertEquals("Song", entry?.song)
    assertEquals("Artist", entry?.artist)
    assertEquals("line", entry?.lyrics)
    assertEquals("lrc", entry?.format)
    assertEquals("synced", entry?.syncType)
    assertEquals("low", entry?.confidence)
    assertEquals(0.0, entry?.score ?: -1.0, 0.0)
    assertNull(entry?.language)
  }

  @Test
  fun `parseSingle rejects unsuccessful blank and malformed documents`() {
    val entry = entryJson(1, "line")
    assertNull(Unison.parseSingle("""{"success":false,"data":$entry}"""))
    assertNull(Unison.parseSingle(singleBody(entryJson(2, ""))))
    assertNull(Unison.parseSingle("not json"))
    assertNull(Unison.parseSingle(""))
    assertNull(Unison.parseSingle("<html></html>"))
  }

  @Test
  fun `parseSingle tolerates an unknown pagination envelope key`() {
    val entry = Unison.parseSingle(
      """{"success":true,"pagination":{"page":1},"data":${entryJson(3, "line")}}""",
    )
    assertEquals(3L, entry?.id)
  }

  @Test
  fun `parseSearch keeps usable entries and drops blanks`() {
    val body = """{"success":true,"data":[${entryJson(1, "a")},${entryJson(2, " ")}]}"""
    assertEquals(listOf(1L), Unison.parseSearch(body).map { it.id })

    assertTrue(Unison.parseSearch("""{"success":false,"data":[${entryJson(1, "a")}]}""").isEmpty())
    assertTrue(Unison.parseSearch("""{"success":true}""").isEmpty())
    assertTrue(Unison.parseSearch("<html></html>").isEmpty())
  }

  @Test
  fun `getLyrics rejects blank metadata without a request`() = runBlocking {
    val engine = FakeEngine { data -> reply(data, HttpStatusCode.OK, "") }
    Unison.testClient = HttpClient(engine) { }

    assertTrue(Unison.getLyrics(title = " ", artist = "Artist").isFailure)
    assertTrue(Unison.getLyrics(title = "Song", artist = "").isFailure)
    assertEquals(0, engine.requests.size)
  }

  @Test
  fun `getLyrics prefers a video lookup and returns the raw document`() = runBlocking {
    val raw = "<tt>raw document</tt>"
    val engine = FakeEngine { data ->
      if (data.url.parameters["v"] != null) {
        reply(data, HttpStatusCode.OK, singleBody(entryJson(1, raw)))
      } else {
        reply(data, HttpStatusCode.InternalServerError, "")
      }
    }
    Unison.testClient = HttpClient(engine) { }

    val result = Unison.getLyrics(videoId = "v1", title = "Song", artist = "Artist")

    assertEquals(raw, result.getOrNull())
    assertEquals(1, engine.requests.size)
  }

  @Test
  fun `getLyrics falls back to metadata when the video lookup misses`() = runBlocking {
    val engine = FakeEngine { data ->
      if (data.url.parameters["v"] != null) {
        reply(data, HttpStatusCode.OK, """{"success":false}""")
      } else {
        reply(data, HttpStatusCode.OK, singleBody(entryJson(2, "metadata lyrics")))
      }
    }
    Unison.testClient = HttpClient(engine) { }

    val result = Unison.getLyrics(videoId = "v1", title = "Song", artist = "Artist")

    assertEquals("metadata lyrics", result.getOrNull())
    assertEquals(2, engine.requests.size)
  }

  @Test
  fun `getLyrics reports unavailable when every endpoint fails without retrying`() = runBlocking {
    val engine = FakeEngine { data -> reply(data, HttpStatusCode.InternalServerError, "") }
    Unison.testClient = HttpClient(engine) { }

    val result = Unison.getLyrics(videoId = "v1", title = "Song", artist = "Artist")

    assertTrue(result.isFailure)
    assertEquals("Lyrics unavailable", result.exceptionOrNull()?.message)
    assertEquals(2, engine.requests.size)
  }

  @Test
  fun `getAllLyrics calls back zero times for unusable metadata`() = runBlocking {
    val engine = FakeEngine { data -> reply(data, HttpStatusCode.OK, """{"success":true,"data":[]}""") }
    Unison.testClient = HttpClient(engine) { }

    val delivered = ArrayList<String>()
    Unison.getAllLyrics(title = "", artist = "") { delivered.add(it) }

    assertTrue(delivered.isEmpty())
  }

  @Test
  fun `getAllLyrics delivers the first five search results in order`() = runBlocking {
    val entries = (1..7).joinToString(",", "[", "]") { index -> entryJson(index.toLong(), "L$index") }
    val engine = FakeEngine { data ->
      reply(data, HttpStatusCode.OK, """{"success":true,"data":$entries}""")
    }
    Unison.testClient = HttpClient(engine) { }

    val delivered = ArrayList<String>()
    Unison.getAllLyrics(title = "Song", artist = "Artist") { delivered.add(it) }

    assertEquals(listOf("L1", "L2", "L3", "L4", "L5"), delivered)
    assertEquals(1, engine.requests.size)
  }

  @Test
  fun `getAllLyrics falls back to a single lookup when the search is empty`() = runBlocking {
    val engine = FakeEngine { data ->
      if (data.url.encodedPath.endsWith("/search")) {
        reply(data, HttpStatusCode.OK, """{"success":true,"data":[]}""")
      } else {
        reply(data, HttpStatusCode.OK, singleBody(entryJson(9, "single lyrics")))
      }
    }
    Unison.testClient = HttpClient(engine) { }

    val delivered = ArrayList<String>()
    Unison.getAllLyrics(title = "Song", artist = "Artist") { delivered.add(it) }

    assertEquals(listOf("single lyrics"), delivered)
    assertEquals(2, engine.requests.size)
  }

  @Test
  fun `getAllLyrics observes cancellation between callbacks`() = runBlocking {
    val entries = (1..3).joinToString(",", "[", "]") { index -> entryJson(index.toLong(), "L$index") }
    val engine = FakeEngine { data ->
      reply(data, HttpStatusCode.OK, """{"success":true,"data":$entries}""")
    }
    Unison.testClient = HttpClient(engine) { }

    val delivered = ArrayList<String>()
    val holder = arrayOfNulls<Job>(1)
    holder[0] = launch {
      Unison.getAllLyrics(title = "Song", artist = "Artist") { text ->
        delivered.add(text)
        holder[0]?.cancel()
      }
    }
    holder[0]?.join()

    assertEquals(listOf("L1"), delivered)
    assertFalse(holder[0]?.isCompleted == false)
  }
}
