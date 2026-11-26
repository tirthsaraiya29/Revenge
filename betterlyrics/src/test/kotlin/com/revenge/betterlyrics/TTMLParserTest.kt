package com.revenge.betterlyrics

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(InternalAPI::class)
private class FakeEngine(
  private val handler: suspend (HttpRequestData) -> HttpResponseData,
) : HttpClientEngineBase("betterlyrics-fake") {
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

private fun wrapParagraph(attributes: String, content: String): String =
  "<tt xmlns=\"http://www.w3.org/ns/ttml\"><body><p $attributes>$content</p></body></tt>"

private fun span(begin: String, end: String, text: String, role: String? = null): String {
  val roleAttribute = if (role == null) "" else " role=\"$role\""
  return "<span begin=\"$begin\" end=\"$end\"$roleAttribute>$text</span>"
}

class TTMLParserTest {
  @Before
  fun setUp() {
    BetterLyrics.testClient = null
  }

  @After
  fun tearDown() {
    BetterLyrics.testClient = null
  }

  @Test
  fun `parses whitespace separated spans into words`() {
    val xml = wrapParagraph(
      "begin=\"1.5\" end=\"3.0\"",
      span("1.5", "2.0", "Hello") + " " + span("2.0", "3.0", "world"),
    )

    val lines = TTMLParser.parseTTML(xml)

    assertEquals(1, lines.size)
    val line = lines.single()
    assertEquals("Hello world", line.text)
    assertEquals(1.5, line.startTime, 0.0)
    assertEquals(2, line.words.size)
    assertEquals(TTMLParser.ParsedWord("Hello", 1.5, 2.0), line.words[0])
    assertEquals(TTMLParser.ParsedWord("world", 2.0, 3.0), line.words[1])
  }

  @Test
  fun `merges adjacent span syllables into one word`() {
    val xml = wrapParagraph(
      "begin=\"0.0\" end=\"0.3\"",
      span("0.0", "0.1", "Hel") + span("0.1", "0.2", "lo"),
    )

    val line = TTMLParser.parseTTML(xml).single()

    assertEquals("Hello", line.text)
    assertEquals(listOf(TTMLParser.ParsedWord("Hello", 0.0, 0.2)), line.words)
  }

  @Test
  fun `extracts background spans as separate lines`() {
    val xml = wrapParagraph(
      "begin=\"0.5\" end=\"3.0\"",
      span("0.5", "1.0", "main") + span("1.0", "2.0", "echo", role = "x-bg"),
    )

    val line = TTMLParser.parseTTML(xml).single()

    assertEquals("main", line.text)
    assertFalse(line.isBackground)
    val background = line.backgroundLines.single()
    assertEquals("echo", background.text)
    assertEquals(1.0, background.startTime, 0.0)
    assertTrue(background.isBackground)
    assertNull(background.agent)
  }

  @Test
  fun `ignores translation and romanisation spans`() {
    val xml = wrapParagraph(
      "begin=\"0.0\" end=\"1.0\"",
      span("0.0", "1.0", "main") +
        span("0.0", "1.0", "translation", role = "x-translation") +
        span("0.0", "1.0", "roman", role = "x-roman"),
    )

    val line = TTMLParser.parseTTML(xml).single()

    assertEquals("main", line.text)
    assertEquals(1, line.words.size)
  }

  @Test
  fun `reads the ttm agent attribute`() {
    val xml = "<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\">" +
      "<body><p begin=\"0.0\" end=\"1.0\" ttm:agent=\"v1\">line</p></body></tt>"

    val line = TTMLParser.parseTTML(xml).single()

    assertEquals("v1", line.agent)
  }

  @Test
  fun `falls back to direct paragraph text when there are no spans`() {
    val xml = wrapParagraph("begin=\"0.0\" end=\"1.0\"", "plain text")

    val line = TTMLParser.parseTTML(xml).single()

    assertEquals("plain text", line.text)
    assertTrue(line.words.isEmpty())
  }

  @Test
  fun `parses hour and plain-second time expressions`() {
    val xml = "<tt xmlns=\"http://www.w3.org/ns/ttml\"><body>" +
      "<p begin=\"01:00:05.000\" end=\"01:00:06.000\">hour</p>" +
      "<p begin=\"12.5\" end=\"13.5\">plain</p>" +
      "</body></tt>"

    val lines = TTMLParser.parseTTML(xml)

    assertEquals(2, lines.size)
    assertEquals(3605.0, lines[0].startTime, 0.0)
    assertEquals(12.5, lines[1].startTime, 0.0)
  }

  @Test
  fun `skips paragraphs without begin and survives malformed input`() {
    val skipped = "<tt xmlns=\"http://www.w3.org/ns/ttml\"><body>" +
      "<p>no begin</p><p begin=\"1.0\" end=\"2.0\">ok</p></body></tt>"
    assertEquals(listOf("ok"), TTMLParser.parseTTML(skipped).map { it.text })

    assertTrue(TTMLParser.parseTTML("<tt><body><p begin='0'>oops").isEmpty())
    assertTrue(TTMLParser.parseTTML("").isEmpty())
  }

  @Test
  fun `toLRC emits agent tags word tracks and backgrounds`() {
    val main = TTMLParser.ParsedLine(
      text = "Hello",
      startTime = 65.234,
      words = listOf(TTMLParser.ParsedWord("Hello", 65.234, 65.9)),
      agent = "v1",
      backgroundLines = listOf(
        TTMLParser.ParsedLine(
          text = "echo",
          startTime = 65.5,
          words = listOf(TTMLParser.ParsedWord("echo", 65.5, 65.7)),
          isBackground = true,
        ),
      ),
    )

    assertEquals(
      "[01:05.23]{agent:v1}Hello\n<Hello:65.234:65.9>\n" +
        "[01:05.50]{bg}echo\n<echo:65.5:65.7>\n",
      TTMLParser.toLRC(listOf(main)),
    )
  }

  @Test
  fun `toLRC emits only a timestamp for a line without words`() {
    val line = TTMLParser.ParsedLine("plain", 0.0, emptyList())
    assertEquals("[00:00.00]plain\n", TTMLParser.toLRC(listOf(line)))
    assertEquals("", TTMLParser.toLRC(emptyList()))
  }

  @Test
  fun `getLyrics rejects non-200 responses`() = runBlocking {
    val engine = FakeEngine { data -> reply(data, HttpStatusCode.Forbidden, "") }
    BetterLyrics.testClient = HttpClient(engine) { }

    val result = BetterLyrics.getLyrics("Song", "Artist", 100)

    assertTrue(result.isFailure)
    assertEquals("Lyrics unavailable", result.exceptionOrNull()?.message)
  }

  @Test
  fun `getLyrics rejects a document with no usable paragraphs`() = runBlocking {
    val engine = FakeEngine { data ->
      reply(data, HttpStatusCode.OK, """{"ttml":"<tt/>"}""")
    }
    BetterLyrics.testClient = HttpClient(engine) { }

    val result = BetterLyrics.getLyrics("Song", "Artist", 100)

    assertTrue(result.isFailure)
    assertEquals("Failed to parse lyrics", result.exceptionOrNull()?.message)
  }

  @Test
  fun `getLyrics survives malformed json`() = runBlocking {
    val engine = FakeEngine { data -> reply(data, HttpStatusCode.OK, "not json at all") }
    BetterLyrics.testClient = HttpClient(engine) { }

    val result = BetterLyrics.getLyrics("Song", "Artist", 100)

    assertTrue(result.isFailure)
  }

  @Test
  fun `getLyrics maps metadata parameters`() = runBlocking {
    val engine = FakeEngine { data ->
      reply(data, HttpStatusCode.OK, """{"ttml":"<tt/>"}""")
    }
    BetterLyrics.testClient = HttpClient(engine) { }

    BetterLyrics.getLyrics("Song", "Artist", 100, "Album")

    val url = engine.requests.single()
    assertTrue(url.contains("s=Song"))
    assertTrue(url.contains("a=Artist"))
    assertTrue(url.contains("d=100"))
    assertTrue(url.contains("al=Album"))
  }

  @Test
  fun `getAllLyrics calls back only on success`() = runBlocking {
    val okEngine = FakeEngine { data ->
      reply(
        data,
        HttpStatusCode.OK,
        """{"ttml":"<tt xmlns=\"http://www.w3.org/ns/ttml\"><body><p begin=\"0.0\" end=\"1.0\">ok</p></body></tt>"}""",
      )
    }
    BetterLyrics.testClient = HttpClient(okEngine) { }

    val delivered = ArrayList<String>()
    BetterLyrics.getAllLyrics("Song", "Artist", 100) { delivered.add(it) }

    assertEquals(listOf("[00:00.00]ok\n"), delivered)

    val failed = ArrayList<String>()
    BetterLyrics.testClient = HttpClient(FakeEngine { data -> reply(data, HttpStatusCode.Forbidden, "") }) { }
    BetterLyrics.getAllLyrics("Song", "Artist", 100) { failed.add(it) }

    assertTrue(failed.isEmpty())
  }
}
