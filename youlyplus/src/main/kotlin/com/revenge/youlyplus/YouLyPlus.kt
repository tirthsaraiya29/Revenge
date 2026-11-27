package com.revenge.youlyplus

import com.revenge.youlyplus.models.LyricsResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private const val NO_LYRICS = "No lyrics found from any YouLyPlus server"

private val SERVERS =
  listOf(
    "https://lyricsplus.prjktla.my.id",
    "https://lyricsplus.atomix.one",
    "https://lyricsplus.binimum.org",
    "https://lyricsplus.prjktla.workers.dev",
    "https://lyricsplus-seven.vercel.app",
    "https://lyrics-plus-backend.vercel.app",
  )

internal fun promoteServer(last: String?, servers: List<String>): List<String> {
  if (last == null || last !in servers) return servers
  return listOf(last) + servers.filter { it != last }
}

object YouLyPlus {
  private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
  }

  @Volatile private var lastWorkingServer: String? = null

  internal var testClient: HttpClient? = null

  private val defaultClient: HttpClient by lazy {
    HttpClient(OkHttp) {
      expectSuccess = true
      install(HttpTimeout) {
        requestTimeoutMillis = 8_000
        connectTimeoutMillis = 3_000
        socketTimeoutMillis = 8_000
      }
    }
  }

  private fun client(): HttpClient = testClient ?: defaultClient

  suspend fun getLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
    id: String? = null,
    isrc: String? = null,
  ): Result<String> {
    return try {
      val servers = promoteServer(lastWorkingServer, SERVERS)
      val winner = coroutineScope {
        val results = Channel<Pair<String?, String>>(Channel.UNLIMITED)
        val jobs = servers.map { server ->
          launch {
            results.send(fetch(server, title, artist, duration, album, id, isrc) to server)
          }
        }
        var found: Pair<String, String>? = null
        try {
          for (index in servers.indices) {
            val (text, server) = results.receive()
            if (!text.isNullOrBlank()) {
              found = text to server
              break
            }
          }
        } finally {
          jobs.forEach { it.cancel() }
        }
        found
      }
      if (winner == null) {
        Result.failure(IllegalStateException(NO_LYRICS))
      } else {
        lastWorkingServer = winner.second
        Result.success(winner.first)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun getAllLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
    id: String? = null,
    isrc: String? = null,
    callback: (String) -> Unit,
  ) {
    val servers = promoteServer(lastWorkingServer, SERVERS)
    coroutineScope {
      val results = Channel<Pair<String?, String>>(Channel.UNLIMITED)
      val jobs = servers.map { server ->
        launch {
          results.send(fetch(server, title, artist, duration, album, id, isrc) to server)
        }
      }
      val delivered = HashSet<String>()
      try {
        for (index in servers.indices) {
          val (text, server) = results.receive()
          if (!text.isNullOrBlank()) {
            if (delivered.add(text)) {
              lastWorkingServer = server
              callback(text)
            }
          }
        }
      } finally {
        jobs.forEach { it.cancel() }
      }
    }
  }

  private suspend fun fetch(
    server: String,
    title: String,
    artist: String,
    duration: Int,
    album: String?,
    id: String?,
    isrc: String?,
  ): String? {
    return try {
      val response =
        client().get(server.trimEnd('/') + "/v2/lyrics/get") {
          parameter("title", title)
          parameter("artist", artist)
          parameter("duration", duration)
          if (!album.isNullOrBlank()) parameter("album", album)
          if (!id.isNullOrBlank()) parameter("id", id)
          if (!isrc.isNullOrBlank()) parameter("isrc", isrc)
        }
      json.decodeFromString<LyricsResponse>(response.bodyAsText()).bestLyrics()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }
  }
}
