package com.revenge.betterlyrics

import com.revenge.betterlyrics.models.TTMLResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json

/**
 * Client for the BetterLyrics API, which serves word-level TTML for a track.
 *
 * [getLyrics] and [getAllLyrics] never throw for expected failures: transport errors, non-2xx
 * responses, malformed JSON and unparseable TTML are surfaced as a failed [Result].
 */
object BetterLyrics {
  private const val BASE_URL = "https://lyrics-api.boidu.dev"

  private val client by lazy {
    HttpClient(CIO) {
      install(ContentNegotiation) {
        json(
          Json {
            isLenient = true
            ignoreUnknownKeys = true
          },
        )
      }

      install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
      }

      expectSuccess = false
    }
  }

  /**
   * Fetches synced lyrics for a track and converts them from TTML to the app's LRC dialect.
   *
   * @param title track title used for the provider lookup
   * @param artist performing artist
   * @param duration track duration in whole seconds; ignored when non-positive
   * @param album optional album name
   * @return the LRC text, or a failure describing why nothing usable was returned
   */
  suspend fun getLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
  ): Result<String> =
    try {
      // The API matches on exact metadata, so no normalization is applied here.
      val ttml = fetchTtml(title, artist, duration, album)
        ?: throw IllegalStateException("Lyrics unavailable")
      val lines = TTMLParser.parseTTML(ttml)
      if (lines.isEmpty()) {
        throw IllegalStateException("Failed to parse lyrics")
      }
      Result.success(TTMLParser.toLRC(lines))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  /**
   * Convenience wrapper for callers that only care about a successful result.
   * [callback] is invoked exactly once, and only when lyrics were retrieved.
   */
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
  ): String? =
    try {
      val response =
        client.get("$BASE_URL/getLyrics") {
          parameter("s", title)
          parameter("a", artist)
          if (duration > 0) {
            parameter("d", duration)
          }
          if (!album.isNullOrBlank()) {
            parameter("al", album)
          }
        }
      if (response.status == HttpStatusCode.OK) {
        response.body<TTMLResponse>().ttml
      } else {
        null
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }
}
