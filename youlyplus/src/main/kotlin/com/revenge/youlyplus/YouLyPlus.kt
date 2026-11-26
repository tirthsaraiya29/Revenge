package com.revenge.youlyplus

import com.revenge.youlyplus.models.LyricsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Client for the community-hosted LyricsPlus / KPoe lyrics APIs.
 *
 * Every mirror exposes `GET /v2/lyrics/get?title=&artist=&duration=`. Mirrors are queried in
 * parallel and the first usable result wins; the winning mirror is remembered and tried first on
 * subsequent lookups.
 */
object YouLyPlus {

  private val baseServers =
    listOf(
      "https://lyricsplus.prjktla.my.id",
      "https://lyricsplus.atomix.one",
      "https://lyricsplus.binimum.org",
      "https://lyricsplus.prjktla.workers.dev",
      "https://lyricsplus-seven.vercel.app",
      "https://lyrics-plus-backend.vercel.app",
    )

  /** Last mirror that produced lyrics, promoted to the front of the next query. */
  private val lastWorkingServer = AtomicReference<String?>(null)

  private val jsonFormat = Json {
    isLenient = true
    ignoreUnknownKeys = true
  }

  private val client: HttpClient by lazy {
    HttpClient(OkHttp) {
      expectSuccess = true
      install(HttpTimeout) {
        connectTimeoutMillis = 3_000
        requestTimeoutMillis = 8_000
        socketTimeoutMillis = 8_000
      }
      install(ContentNegotiation) {
        json(jsonFormat)
      }
    }
  }

  /**
   * Looks up lyrics, racing all mirrors. Returns success with the first non-blank result, or a
   * failure when every mirror errors out or returns nothing usable.
   *
   * Cancelling the calling coroutine cancels all in-flight requests.
   */
  suspend fun getLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
    id: String? = null,
    isrc: String? = null,
  ): Result<String> =
    try {
      Result.success(fetchFirst(title, artist, duration, album, id, isrc))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  /**
   * Queries every mirror in parallel and invokes [callback] once per distinct non-blank lyrics
   * result, in arrival order. Never invokes [callback] when no mirror returns anything usable.
   */
  suspend fun getAllLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
    id: String? = null,
    isrc: String? = null,
    callback: (String) -> Unit,
  ) {
    val servers = promoteServer(lastWorkingServer.get(), baseServers)
    coroutineScope {
      val results = Channel<Pair<String, String?>>(servers.size)
      val jobs =
        servers.map { server ->
          launch { results.trySend(server to fetchLrc(server, title, artist, duration, album, id, isrc)) }
        }
      try {
        val seen = HashSet<String>()
        repeat(servers.size) {
          val (server, lrc) = results.receive()
          if (!lrc.isNullOrBlank() && seen.add(lrc)) {
            lastWorkingServer.set(server)
            callback(lrc)
          }
        }
      } finally {
        jobs.forEach { it.cancel() }
        results.close()
      }
    }
  }

  private suspend fun fetchFirst(
    title: String,
    artist: String,
    duration: Int,
    album: String?,
    id: String?,
    isrc: String?,
  ): String {
    val servers = promoteServer(lastWorkingServer.get(), baseServers)
    return coroutineScope {
      val results = Channel<Pair<String, String?>>(servers.size)
      val jobs =
        servers.map { server ->
          launch { results.trySend(server to fetchLrc(server, title, artist, duration, album, id, isrc)) }
        }
      try {
        repeat(servers.size) {
          val (server, lrc) = results.receive()
          if (!lrc.isNullOrBlank()) {
            lastWorkingServer.set(server)
            return@coroutineScope lrc
          }
        }
        throw IllegalStateException("No lyrics found from any YouLyPlus server")
      } finally {
        jobs.forEach { it.cancel() }
        results.close()
      }
    }
  }

  /** Fetches and normalises lyrics from one mirror; returns null on any failure. */
  private suspend fun fetchLrc(
    server: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
    id: String?,
    isrc: String?,
  ): String? =
    try {
      val response =
        client
          .get(server.trimEnd('/') + "/v2/lyrics/get") {
            parameter("title", title)
            parameter("artist", artist)
            parameter("duration", duration)
            album?.takeIf { it.isNotBlank() }?.let { parameter("album", it) }
            id?.takeIf { it.isNotBlank() }?.let { parameter("id", it) }
            isrc?.takeIf { it.isNotBlank() }?.let { parameter("isrc", it) }
          }
          .body<LyricsResponse>()
      response.bestLyrics()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }

  private fun LyricsResponse.bestLyrics(): String? =
    syncedLyrics?.takeIf { it.isNotBlank() }
      ?: lyrics?.toLrc()?.takeIf { it.isNotBlank() }
      ?: plainLyrics?.takeIf { it.isNotBlank() }
}
