package com.revenge.artistvideo

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Looks up the animated artist artwork ("artist video") for a track.
 *
 * Results, including negative ones, are cached briefly so the same artist page does not hammer the
 * endpoint while the user scrolls. Any transport or decoding failure yields `null`; callers treat a
 * missing video as "show the still cover" rather than a hard error.
 */
object ArtistVideoCanvasProvider {
  private const val BASE_URL = "https://artwork-archivetune.koiiverse.cloud/"
  private const val CACHE_TTL_MS = 60_000L

  private val json =
    Json {
      ignoreUnknownKeys = true
      isLenient = true
      explicitNulls = false
    }

  private val client by lazy {
    HttpClient(OkHttp) {
      expectSuccess = false
      install(ContentNegotiation) { json(json) }
      install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
      }
      install(ContentEncoding) {
        gzip()
        deflate()
      }
      install(HttpCache)
    }
  }

  private data class CacheEntry(val value: ArtistVideoResponse?, val expiresAtMs: Long)

  private val cache = ConcurrentHashMap<String, CacheEntry>()

  suspend fun getBySongArtist(
    song: String,
    artist: String,
    album: String? = null,
    duration: Int? = null,
  ): ArtistVideoResponse? {
    val key = cacheKey(song, artist, album.orEmpty(), duration?.toString().orEmpty())

    cache[key]?.let { entry ->
      if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
      cache.remove(key)
    }

    val result = fetch(song, artist, album, duration)
    cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
    return result
  }

  private suspend fun fetch(
    song: String,
    artist: String,
    album: String?,
    duration: Int?,
  ): ArtistVideoResponse? {
    return try {
      val response =
        client.get(BASE_URL) {
          parameter("s", song)
          parameter("a", artist)
          if (album != null) parameter("al", album)
          if (duration != null && duration > 0) parameter("d", duration)
        }
      if (response.status == HttpStatusCode.OK) {
        try {
          response.body<ArtistVideoResponse>()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          null
        }
      } else {
        null
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }
  }

  private fun cacheKey(vararg parts: String): String =
    parts.joinToString("|") { it.trim().lowercase(Locale.ROOT) }
}

@Serializable
data class ArtistVideoResponse(
  val name: String? = null,
  val artist: String? = null,
  @SerialName("albumId") val albumId: String? = null,
  val static: String? = null,
  val animated: String? = null,
  val videoUrl: String? = null,
) {
  val preferredAnimationUrl: String?
    get() = animated ?: videoUrl
}
