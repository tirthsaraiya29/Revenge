package com.revenge.betterlyrics

import com.revenge.betterlyrics.models.TTMLResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

private const val BASE_URL = "https://lyrics-api.boidu.dev"
private const val LYRICS_UNAVAILABLE = "Lyrics unavailable"
private const val PARSE_FAILED = "Failed to parse lyrics"

object BetterLyrics {
  private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
  }

  internal var testClient: HttpClient? = null

  private val defaultClient: HttpClient by lazy {
    HttpClient(CIO) {
      expectSuccess = false
      install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
      }
    }
  }

  private fun client(): HttpClient = testClient ?: defaultClient

  suspend fun getLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
  ): Result<String> {
    return try {
      val ttml = fetchTtml(title, artist, duration, album)
      if (ttml == null) {
        Result.failure(IllegalStateException(LYRICS_UNAVAILABLE))
      } else {
        val lines = TTMLParser.parseTTML(ttml)
        if (lines.isEmpty()) {
          Result.failure(IllegalStateException(PARSE_FAILED))
        } else {
          Result.success(TTMLParser.toLRC(lines))
        }
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
    callback: (String) -> Unit,
  ) {
    getLyrics(title, artist, duration, album).onSuccess(callback)
  }

  private suspend fun fetchTtml(
    title: String,
    artist: String,
    duration: Int,
    album: String?,
  ): String? {
    return try {
      val response = client().get("$BASE_URL/getLyrics") {
        parameter("s", title)
        parameter("a", artist)
        if (duration > 0) parameter("d", duration)
        if (!album.isNullOrBlank()) parameter("al", album)
      }
      if (response.status != HttpStatusCode.OK) {
        null
      } else {
        json.decodeFromString<TTMLResponse>(response.bodyAsText()).ttml
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }
  }
}
