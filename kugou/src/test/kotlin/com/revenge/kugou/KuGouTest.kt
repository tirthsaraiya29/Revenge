package com.revenge.kugou

import com.revenge.kugou.models.Keyword
import com.revenge.kugou.models.SearchLyricsResponse
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
import java.util.Base64
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
) : HttpClientEngineBase("kugou-fake") {
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

private fun jsonString(raw: String): String =
  raw.replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "\\r")
    .replace("\n", "\\n")

private fun bodyFromPath(data: HttpRequestData): String = data.url.encodedPath

class KuGouTest {
  @Before
  fun setUp() {
    KuGou.testClient = null
  }

  @After
  fun tearDown() {
    KuGou.testClient = null
  }

  @Test
  fun `generateKeyword strips a parenthesised suffix`() {
    val keyword = KuGou.generateKeyword("Song (Remastered)", "Artist", "Album")
    assertEquals("Song ", keyword.title)
    assertEquals("Artist", keyword.artist)
    assertEquals("Album", keyword.album)
  }

  @Test
  fun `generateKeyword normalises artist separators and dots`() {
    val keyword = KuGou.generateKeyword("Song", "A, B & C")
    assertEquals("A、B、C", keyword.artist)
    assertNull(keyword.album)
    assertEquals("Mt Joy", KuGou.generateKeyword("T", "Mt. Joy").artist)
  }

  @Test
  fun `buildSearchQuery appends a non-blank album`() {
    assertEquals("T - A", buildSearchQuery(Keyword("T", "A")))
    assertEquals("T - A Album", buildSearchQuery(Keyword("T", "A", "Album")))
    assertEquals("T - A", buildSearchQuery(Keyword("T", "A", "  ")))
  }

  @Test
  fun `normalizeLyrics removes metadata and credit lines`() {
    val raw = "some song info\n[00:00.00]作词: A\n[00:00.00]作曲: B\n" +
      "[00:10.00]line one\n[00:20.50]line two"
    assertEquals("[00:10.00]line one\n[00:20.50]line two", raw.normalizeLyrics())
  }

  @Test
  fun `normalizeLyrics converts CRLF to LF`() {
    assertEquals("[00:01.00]a\n[00:02.00]b", "[00:01.00]a\r\n[00:02.00]b".normalizeLyrics())
  }

  @Test
  fun `normalizeLyrics rejects documents without timestamped lines`() {
    assertEquals("", "no lyrics here".normalizeLyrics())
    assertEquals("", "".normalizeLyrics())
    assertEquals("", "[1:02.00]x".normalizeLyrics())
  }

  @Test
  fun `normalizeLyrics keeps thirty-four body lines and drops a trailing credit`() {
    val body = (0..33).joinToString("\n") { index ->
      String.format(Locale.ROOT, "[00:%02d.00]line %d", index, index)
    }
    val raw = body + "\n[00:33.00]作词: Someone"

    val normalized = raw.normalizeLyrics()
    val lines = normalized.split("\n")

    assertEquals(34, lines.size)
    assertEquals("[00:00.00]line 0", lines.first())
    assertEquals("[00:33.00]line 33", lines.last())
  }

  @Test
  fun `search lyrics response decodes snake case and defaults`() {
    val fixture = """
      {
        "status": 1,
        "info": "ok",
        "errcode": 0,
        "errmsg": "",
        "expire": 0,
        "candidates": [
          {"id": 123, "product_from": "pc", "duration": 200, "accesskey": "key", "extra": true}
        ],
        "unknown": 1
      }
    """.trimIndent()
    val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<SearchLyricsResponse>(fixture)
    val candidate = decoded.candidates.single()
    assertEquals(123L, candidate.id)
    assertEquals("pc", candidate.productFrom)
    assertEquals(200L, candidate.duration)
    assertEquals("key", candidate.accesskey)

    val empty = Json { ignoreUnknownKeys = true }.decodeFromString<SearchLyricsResponse>("{}")
    assertEquals(0, empty.status)
    assertTrue(empty.candidates.isEmpty())
  }

  @Test
  fun `downloads MIME wrapped base64 and normalises it`() = runBlocking {
    val rawLyrics = "[00:00.00]作词: A\n[00:10.00]line one\n[00:20.50]line two"
    val encoded = Base64.getMimeEncoder(8, "\r\n".toByteArray())
      .encodeToString(rawLyrics.toByteArray(Charsets.UTF_8))

    val engine = FakeEngine { data ->
      when (bodyFromPath(data)) {
        "/api/v3/search/song" ->
          reply(data, HttpStatusCode.OK, """{"data":{"info":[{"duration":100,"hash":"h1"}]}}""")
        "/search" ->
          reply(
            data,
            HttpStatusCode.OK,
            """{"candidates":[{"id":1,"product_from":"pc","duration":100,"accesskey":"a1"}]}""",
          )
        "/download" -> reply(data, HttpStatusCode.OK, """{"content":"${jsonString(encoded)}"}""")
        else -> reply(data, HttpStatusCode.NotFound, "")
      }
    }
    KuGou.testClient = HttpClient(engine) { expectSuccess = true }

    val result = KuGou.getLyrics("T", "A", 100)

    assertTrue(result.isSuccess)
    assertEquals("[00:10.00]line one\n[00:20.50]line two", result.getOrNull())
    val downloadUrl = engine.requests.single { it.contains("/download") }
    assertTrue(downloadUrl.contains("fmt=lrc"))
    assertTrue(downloadUrl.contains("charset=utf8"))
    assertTrue(downloadUrl.contains("id=1"))
    assertTrue(downloadUrl.contains("accesskey=a1"))
  }

  @Test
  fun `missing candidate produces the documented message`() = runBlocking {
    val engine = FakeEngine { data ->
      when (bodyFromPath(data)) {
        "/api/v3/search/song" -> reply(data, HttpStatusCode.OK, """{"data":{"info":[]}}""")
        "/search" -> reply(data, HttpStatusCode.OK, """{"candidates":[]}""")
        else -> reply(data, HttpStatusCode.NotFound, "")
      }
    }
    KuGou.testClient = HttpClient(engine) { expectSuccess = true }

    val result = KuGou.getLyrics("T", "A", 100)

    assertTrue(result.isFailure)
    assertEquals("No lyrics candidate for T - A", result.exceptionOrNull()?.message)
  }

  @Test
  fun `forbidden song search fails without retrying`() = runBlocking {
    val engine = FakeEngine { data -> reply(data, HttpStatusCode.Forbidden, "") }
    KuGou.testClient = HttpClient(engine) { expectSuccess = true }

    val result = KuGou.getLyrics("T", "A", 100)

    assertTrue(result.isFailure)
    assertEquals(1, engine.requests.size)
  }

  @Test
  fun `song search uses one page and literal-space keywords`() = runBlocking {
    val engine = FakeEngine { data ->
      reply(data, HttpStatusCode.OK, """{"data":{"info":[]}}""")
    }
    KuGou.testClient = HttpClient(engine) { expectSuccess = true }

    KuGou.searchSongs(KuGou.generateKeyword("T", "A"))

    val url = engine.requests.single()
    assertTrue(url.contains("pagesize=8"))
    assertFalse(url.contains("page="))
    assertTrue(url.contains("keyword=T%20-%20A"))
    assertFalse(url.contains("keyword=T+-+A"))
  }
}
