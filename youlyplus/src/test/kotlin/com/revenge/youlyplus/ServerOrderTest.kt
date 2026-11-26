package com.revenge.youlyplus

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val QUICK_SERVER = "lyricsplus.binimum.org"
private const val SLOW_SERVER = "lyricsplus-seven.vercel.app"

@OptIn(InternalAPI::class)
private class FakeEngine(
  private val handler: suspend (HttpRequestData) -> HttpResponseData,
) : HttpClientEngineBase("youlyplus-fake") {
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

private const val KPOE_BODY =
  """{"lyrics":[{"text":"la","time":0,"syllabus":[{"text":"la","time":0}]}]}"""

class ServerOrderTest {
  @Before
  fun setUp() {
    YouLyPlus.testClient = null
  }

  @After
  fun tearDown() {
    YouLyPlus.testClient = null
  }

  @Test
  fun `promoteServer moves a known server to the front without duplicates`() {
    val servers = listOf("a", "b", "c")
    assertEquals(servers, promoteServer(null, servers))
    assertEquals(servers, promoteServer("z", servers))
    assertEquals(listOf("b", "a", "c"), promoteServer("b", servers))
    assertEquals(listOf("c", "a", "b"), promoteServer("c", servers))
  }

  @Test
  fun `first working mirror wins and slow mirrors are cancelled`() = runBlocking {
    val engine = FakeEngine { data ->
      when (data.url.host) {
        QUICK_SERVER -> reply(data, HttpStatusCode.OK, KPOE_BODY)
        SLOW_SERVER -> {
          delay(10_000)
          reply(data, HttpStatusCode.OK, """{"syncedLyrics":"slow"}""")
        }
        else -> reply(data, HttpStatusCode.Forbidden, "")
      }
    }
    YouLyPlus.testClient = HttpClient(engine) { expectSuccess = true }

    val started = System.nanoTime()
    val result = YouLyPlus.getLyrics("Song", "Artist", 100)
    val elapsedMillis = (System.nanoTime() - started) / 1_000_000

    assertEquals("[00:00.000]<00:00.000>la", result.getOrNull())
    assertTrue("elapsed=$elapsedMillis", elapsedMillis < 3_000)
  }

  @Test
  fun `all forbidden mirrors fail with the documented message and no retries`() = runBlocking {
    val engine = FakeEngine { data -> reply(data, HttpStatusCode.Forbidden, "") }
    YouLyPlus.testClient = HttpClient(engine) { expectSuccess = true }

    val result = YouLyPlus.getLyrics("Song", "Artist", 100)

    assertTrue(result.isFailure)
    assertEquals("No lyrics found from any YouLyPlus server", result.exceptionOrNull()?.message)
    assertEquals(6, engine.requests.size)
  }

  @Test
  fun `duplicate documents across mirrors are delivered once`() = runBlocking {
    val engine = FakeEngine { data ->
      when (data.url.host) {
        "lyricsplus.prjktla.my.id", "lyricsplus.atomix.one" ->
          reply(data, HttpStatusCode.OK, """{"syncedLyrics":"same"}""")
        else -> reply(data, HttpStatusCode.Forbidden, "")
      }
    }
    YouLyPlus.testClient = HttpClient(engine) { expectSuccess = true }

    val delivered = ArrayList<String>()
    YouLyPlus.getAllLyrics("Song", "Artist", 100) { delivered.add(it) }

    assertEquals(listOf("same"), delivered)
  }

  @Test
  fun `optional metadata is only sent when present`() = runBlocking {
    val engine = FakeEngine { data -> reply(data, HttpStatusCode.Forbidden, "") }
    YouLyPlus.testClient = HttpClient(engine) { expectSuccess = true }

    YouLyPlus.getLyrics("Song", "Artist", 100, album = "Album", id = "v1")
    YouLyPlus.getLyrics("Song", "Artist", 100, album = " ", id = "", isrc = " ")

    val withMetadata = engine.requests.first { it.contains("album=") }
    assertTrue(withMetadata.contains("id=v1"))
    assertTrue(engine.requests.none { it.contains("album=+") })
  }
}
