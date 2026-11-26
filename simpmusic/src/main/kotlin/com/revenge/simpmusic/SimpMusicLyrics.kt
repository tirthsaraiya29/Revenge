package com.revenge.simpmusic

import com.revenge.simpmusic.models.LyricsData
import com.revenge.simpmusic.models.SimpMusicApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json

/**
 * Client for the SimpMusic lyrics API. Fixes a 10 second duration tolerance when matching tracks.
 */
object SimpMusicLyrics {
  private const val PRIMARY_BASE_URL = "https://api-lyrics.simpmusic.org/v1/"
  private const val FALLBACK_BASE_URL = "https://vivi-yt-music-server.onrender.com/v1/"
  private const val UNAVAILABLE = "Lyrics unavailable"

  private val jsonFormat =
    Json {
      isLenient = true
      ignoreUnknownKeys = true
      explicitNulls = false
    }

  private val client: HttpClient by lazy {
    HttpClient(CIO) {
      expectSuccess = false
      install(ContentNegotiation) { json(jsonFormat) }
      install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
      }
      defaultRequest {
        header(HttpHeaders.Accept, "application/json")
        header(HttpHeaders.UserAgent, "SimpMusicLyrics/1.0")
      }
    }
  }

  /**
   * Returns every lyrics candidate the API knows for [videoId]. The primary endpoint is tried first;
   * when it fails at the transport or status level the fallback mirror is queried instead.
   */
  suspend fun getLyricsByVideoId(videoId: String): List<LyricsData> {
    fetch(videoId, PRIMARY_BASE_URL)?.let { return it }
    return fetch(videoId, FALLBACK_BASE_URL) ?: emptyList()
  }

  /**
   * Resolves the best lyrics text for [videoId], filtered to candidates within
   * [DURATION_TOLERANCE_SECONDS] of [duration]. Returns a failure when no usable text exists.
   */
  suspend fun getLyrics(videoId: String, duration: Int = 0): Result<String> =
    try {
      val text = selectLyrics(getLyricsByVideoId(videoId), duration)
        ?: throw IllegalStateException(UNAVAILABLE)
      Result.success(text)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  /** Emits up to [MAX_ALTERNATE_LYRICS] distinct lyrics alternatives, best duration match first. */
  suspend fun getAllLyrics(
    videoId: String,
    duration: Int = 0,
    callback: (String) -> Unit,
  ) {
    collectAlternativeLyrics(getLyricsByVideoId(videoId), duration).forEach(callback)
  }

  /**
   * Fetches candidates from [baseUrl]. Returns null when the request should fall through to the
   * next endpoint; an empty list means the endpoint answered but has no lyrics.
   */
  private suspend fun fetch(videoId: String, baseUrl: String): List<LyricsData>? =
    try {
      val response = client.get(baseUrl + videoId)
      if (response.status != HttpStatusCode.OK) {
        null
      } else {
        val body = response.body<SimpMusicApiResponse>()
        if (body.success) body.data else emptyList()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }
}
