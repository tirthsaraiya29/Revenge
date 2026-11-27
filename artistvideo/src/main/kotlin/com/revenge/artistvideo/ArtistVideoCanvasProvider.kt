package com.revenge.artistvideo

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Looks up artist videos in the third-party archive. Both hits and misses are cached for a short
 * window to bound retry pressure; blank inputs are intentionally not filtered.
 */
object ArtistVideoCanvasProvider {
  private const val BASE_URL = "https://artwork-archivetune.koiiverse.cloud/"
  private const val CACHE_TTL_MS = 60_000L

  private data class Entry(val value: ArtistVideoResponse?, val expiresAt: Long)

  private val cache = ConcurrentHashMap<String, Entry>()
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
  }

  @Volatile internal var transport: ArtistVideoTransport = ktorArtistVideoTransport()
  @Volatile internal var clock: () -> Long = System::currentTimeMillis

  internal fun resetForTests() {
    cache.clear()
    transport = ktorArtistVideoTransport()
    clock = System::currentTimeMillis
  }

  suspend fun getBySongArtist(
    song: String,
    artist: String,
    album: String? = null,
    duration: Int? = null,
  ): ArtistVideoResponse? {
    val key = buildKey(song, artist, album, duration)
    val now = clock()
    cache[key]?.let { if (it.expiresAt > now) return it.value }

    val result =
      try {
        val params = buildList {
          add("s" to song)
          add("a" to artist)
          if (album != null) add("al" to album)
          if (duration != null && duration > 0) add("d" to duration.toString())
        }
        val response = transport.get(BASE_URL, params, emptyMap())
        if (response.status != 200) {
          null
        } else {
          json.decodeFromString(ArtistVideoResponse.serializer(), response.body)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        null
      }

    cache[key] = Entry(result, clock() + CACHE_TTL_MS)
    return result
  }

  private fun buildKey(song: String, artist: String, album: String?, duration: Int?): String =
    listOf(song, artist, album.orEmpty(), duration?.toString().orEmpty()).joinToString("|") {
      it.trim().lowercase(Locale.ROOT)
    }
}
