package com.revenge.simpmusic

import com.revenge.simpmusic.models.LyricsData
import com.revenge.simpmusic.models.SimpMusicApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

private const val PRIMARY_BASE_URL = "https://api-lyrics.simpmusic.org/v1/"
private const val FALLBACK_BASE_URL = "https://vivi-yt-music-server.onrender.com/v1/"
private const val USER_AGENT = "SimpMusicLyrics/1.0"
private const val LYRICS_UNAVAILABLE = "Lyrics unavailable"

@OptIn(ExperimentalSerializationApi::class)
object SimpMusicLyrics {
  private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    explicitNulls = false
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

  suspend fun getLyricsByVideoId(videoId: String): List<LyricsData> {
    return fetch(videoId, PRIMARY_BASE_URL)
      ?: fetch(videoId, FALLBACK_BASE_URL)
      ?: emptyList()
  }

  suspend fun getLyrics(videoId: String, duration: Int = 0): Result<String> {
    return try {
      val text = selectLyrics(getLyricsByVideoId(videoId), duration)
      if (text == null) {
        Result.failure(IllegalStateException(LYRICS_UNAVAILABLE))
      } else {
        Result.success(text)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun getAllLyrics(
    videoId: String,
    duration: Int = 0,
    callback: (String) -> Unit,
  ) {
    collectAlternativeLyrics(getLyricsByVideoId(videoId), duration).forEach(callback)
  }

  private suspend fun fetch(videoId: String, baseUrl: String): List<LyricsData>? {
    return try {
      val response = client().get("$baseUrl$videoId") {
        accept(ContentType.Application.Json)
        header(HttpHeaders.UserAgent, USER_AGENT)
      }
      if (response.status.value != 200) {
        null
      } else {
        val body = json.decodeFromString<SimpMusicApiResponse>(response.bodyAsText())
        if (body.success) body.data else emptyList()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }
  }
}
