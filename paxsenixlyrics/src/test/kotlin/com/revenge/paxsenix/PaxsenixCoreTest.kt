package com.revenge.paxsenix

import com.revenge.metadata.cleaner.MetadataCleaner
import com.revenge.paxsenix.models.LyricText
import com.revenge.paxsenix.models.LyricsContent
import com.revenge.paxsenix.models.LyricsResponse
import com.revenge.paxsenix.models.SearchResult
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
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(InternalAPI::class)
private class FakeEngine(
  private val handler: suspend (HttpRequestData) -> HttpResponseData,
) : HttpClientEngineBase("paxsenix-fake") {
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

private val json = Json {
  isLenient = true
  ignoreUnknownKeys = true
}

private fun target(title: String = "Song", artist: String = "Artist", duration: Int = 100): TrackTarget =
  TrackTarget(title, title, artist, duration)

private fun line(
  timestamp: Long,
  texts: List<String>,
  background: Boolean = false,
  oppositeTurn: Boolean = false,
): LyricsContent = LyricsContent(
  timestamp = timestamp,
  endtime = timestamp + 1000,
  duration = 1000,
  text = texts.mapIndexed { index, text ->
    val start = timestamp + index * 300
    LyricText(text, start, start + 300, 300)
  },
  background = background,
  oppositeTurn = oppositeTurn,
)

class PaxsenixCoreTest {
  @Before
  fun setUp() {
    Paxsenix.testClient = null
  }

  @After
  fun tearDown() {
    Paxsenix.testClient = null
  }

  @Test
  fun `ranking prefers exact matches over distant ones`() {
    val wanted = target()
    val exact = SearchResult("1", trackName = "Song", artistName = "Artist", duration = 100_000)
    val distant = SearchResult("2", trackName = "Other", artistName = "Someone", duration = 900_000)

    val ranked = PaxsenixCore.rank(listOf(distant, exact), wanted)

    assertEquals(listOf("1"), ranked.map { it.result.id })
    assertTrue(PaxsenixCore.score(exact, wanted) > PaxsenixCore.score(distant, wanted))
  }

  @Test
  fun `ranking drops unrelated and id-less results`() {
    val unrelated = SearchResult("3", trackName = "Nothing", artistName = "Nobody")
    val blankId = SearchResult("", trackName = "Song", artistName = "Artist", duration = 100_000)

    assertTrue(PaxsenixCore.rank(listOf(unrelated, blankId), target()).isEmpty())
  }

  @Test
  fun `remix and mixed variants are penalised`() {
    val wanted = target()
    val original = SearchResult("1", trackName = "Song", artistName = "Artist", duration = 100_000)
    val remix = SearchResult("2", trackName = "Song remix", artistName = "Artist", duration = 100_000)
    val mixed = SearchResult("3", trackName = "Song mixed", artistName = "Artist", duration = 100_000)

    val ranked = PaxsenixCore.rank(listOf(remix, mixed, original), wanted)

    assertEquals("1", ranked.first().result.id)
    assertTrue(PaxsenixCore.score(original, wanted) > PaxsenixCore.score(remix, wanted))
    assertTrue(PaxsenixCore.score(original, wanted) > PaxsenixCore.score(mixed, wanted))
  }

  @Test
  fun `searchQueries builds distinct queries in order`() {
    assertEquals(
      listOf("Song Artist", "Song", "Song Artist Album"),
      PaxsenixCore.searchQueries("Song", "Artist", "Album"),
    )
    assertEquals(listOf("Song Artist", "Song"), PaxsenixCore.searchQueries("Song", "Artist", null))
    assertEquals(listOf("Song"), PaxsenixCore.searchQueries("Song", "", " "))
  }

  @Test
  fun `word timings are detected by angle brackets`() {
    assertTrue(PaxsenixCore.hasWordTimings("[00:01.00]<00:01.00>Hello"))
    assertFalse(PaxsenixCore.hasWordTimings("[00:01.00]Hello"))
  }

  @Test
  fun `plain text content skips blank words and lines`() {
    val content = listOf(
      line(0, listOf("Hello", " ", "world")),
      line(1000, listOf(" ", "  ")),
      line(2000, listOf("second")),
    )

    assertEquals("Hello world\nsecond", PaxsenixCore.plainTextFromContent(content))
  }

  @Test
  fun `lrc content emits the exact expected document`() {
    val content = listOf(
      LyricsContent(
        timestamp = 61_500,
        endtime = 62_000,
        duration = 500,
        oppositeTurn = true,
        text = listOf(
          LyricText("Hello", 61_500, 61_800, 300),
          LyricText("world", 61_800, 62_000, 200),
        ),
      ),
    )

    assertEquals(
      "[01:01.50]{agent:v2}Hello world\n<Hello:61.5:61.8|world:61.8:62.0>\n",
      PaxsenixCore.lrcFromContent(content),
    )
  }

  @Test
  fun `lrc content uses the right agent tag`() {
    assertTrue(PaxsenixCore.lrcFromContent(listOf(line(0, listOf("a"), background = true))).contains("{bg}"))
    assertTrue(PaxsenixCore.lrcFromContent(listOf(line(0, listOf("a"), oppositeTurn = true))).contains("{agent:v2}"))
    assertTrue(PaxsenixCore.lrcFromContent(listOf(line(0, listOf("a")))).contains("{agent:v1}"))
  }

  @Test
  fun `buildLyrics follows the documented precedence`() {
    val full = LyricsResponse(
      ttmlContent = "ttml",
      elrcMultiPerson = "[00:01.00]multi",
      elrc = "[00:01.00]single",
      plain = "plain",
    )
    assertEquals("converted", PaxsenixCore.buildLyrics(full) { "converted" }.getOrThrow().lyrics)
    assertEquals(
      "[00:01.00]multi",
      PaxsenixCore.buildLyrics(full) { "" }.getOrThrow().lyrics,
    )
    assertEquals(
      "[00:01.00]single",
      PaxsenixCore.buildLyrics(full.copy(elrcMultiPerson = null, ttmlContent = null)).getOrThrow().lyrics,
    )
    assertEquals(
      "plain",
      PaxsenixCore.buildLyrics(full.copy(elrcMultiPerson = null, elrc = null, ttmlContent = null)).getOrThrow().lyrics,
    )
    assertEquals(
      "plain",
      PaxsenixCore.buildLyrics(full.copy(elrcMultiPerson = null, elrc = null)) { "" }.getOrThrow().lyrics,
    )
  }

  @Test
  fun `buildLyrics distinguishes plain and syllable content`() {
    val plainResponse = LyricsResponse(type = "Line", content = listOf(line(0, listOf("Hello", "world"))))
    val plain = PaxsenixCore.buildLyrics(plainResponse).getOrThrow()
    assertEquals("Hello world", plain.lyrics)
    assertFalse(plain.hasWordTimings)

    val syllableResponse = LyricsResponse(
      type = "Syllable",
      content = listOf(line(0, listOf("Hi"))),
    )
    val syllable = PaxsenixCore.buildLyrics(syllableResponse).getOrThrow()
    assertEquals("[00:00.00]{agent:v1}Hi\n<Hi:0.0:0.3>\n", syllable.lyrics)
    assertTrue(syllable.hasWordTimings)
  }

  @Test
  fun `buildLyrics reports missing and empty lyrics`() {
    assertEquals(
      "Paxsenix returned no lyrics",
      PaxsenixCore.buildLyrics(LyricsResponse()).exceptionOrNull()?.message,
    )
    val blankSyllable = LyricsResponse(
      type = "Syllable",
      content = listOf(line(0, listOf(" "))),
    )
    assertEquals(
      "Paxsenix returned empty lyrics",
      PaxsenixCore.buildLyrics(blankSyllable).exceptionOrNull()?.message,
    )
  }

  @Test
  fun `dtos decode bare arrays and lyric responses`() {
    val search = json.decodeFromString<SearchResponse>(
      """[{"id":"a","songName":"Song","artistName":"Artist","duration":100,"extra":1}]""",
    )
    assertEquals("Song", search.single().displayName)
    assertEquals("Artist", search.single().displayArtist)

    val lyrics = json.decodeFromString<LyricsResponse>(
      """{"type":"Syllable","content":[{"timestamp":0,"endtime":10,"duration":10,"text":[{"text":"a","timestamp":0,"endtime":10,"duration":10}]}]}""",
    )
    assertEquals("Syllable", lyrics.type)
    assertEquals(1, lyrics.content.size)
  }

  @Test
  fun `fetching before init fails with the documented message`() = runBlocking {
    val result = Paxsenix.getLyrics("Song", "Artist", 100)

    assertTrue(result.isFailure)
    assertEquals(
      "Paxsenix.init() must be called before fetching lyrics",
      result.exceptionOrNull()?.message,
    )
  }

  @Test
  fun `forbidden search tries every query once and fails`() = runBlocking {
    val engine = FakeEngine { data -> reply(data, HttpStatusCode.Forbidden, "") }
    Paxsenix.testClient = HttpClient(engine) { expectSuccess = true }

    val (cleanTitle, cleanArtist) = MetadataCleaner.clean("Song", "Artist").toSearchQuery()
    val expectedQueries = PaxsenixCore.searchQueries(cleanTitle, cleanArtist, null).size

    val result = Paxsenix.getLyrics("Song", "Artist", 100)

    assertTrue(result.isFailure)
    assertEquals("No matching track found on Paxsenix", result.exceptionOrNull()?.message)
    assertEquals(expectedQueries, engine.requests.size)
  }
}
