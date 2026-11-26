package com.revenge.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Resolves Apple Music artist motion artwork (the HLS video used as an artist-page background).
 *
 * Flow: search the catalog by artist name, then request the matched artist with
 * `extend=editorialVideo,editorialArtwork` and walk the editorial motion keys, falling back to a
 * featured or related album motion video. Results (including misses) are cached for 24 hours.
 */
object AppleMusicArtistBackgroundProvider {
  private const val BASE_URL = "https://amp-api.music.apple.com"
  private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
  private const val MIN_NAME_SCORE = 4
  private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000

  private val preferredMotionKeys =
    listOf(
      "motionArtistWide16x9",
      "motionArtistFullscreen16x9",
      "motionArtistSquare1x1",
      "motionDetailTall",
      "motionTallVideo3x4",
      "motionDetailSquare",
      "motionSquareVideo1x1",
      "motionDetailRaw",
    )

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
  }

  private val client by lazy {
    HttpClient(OkHttp) {
      install(ContentNegotiation) { json(json) }
      install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 25_000
        socketTimeoutMillis = 25_000
      }
      install(ContentEncoding) {
        gzip()
        deflate()
      }
      install(HttpCache)
      expectSuccess = false
    }
  }

  private data class CacheEntry(val videoUrl: String?, val expiresAtMs: Long)

  private val cache = ConcurrentHashMap<String, CacheEntry>()

  suspend fun getByArtistName(artistName: String, storefront: String = "us"): String? {
    val name = artistName.trim()
    if (name.isEmpty()) return null

    val key = cacheKey("artist", name, storefront)
    cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.videoUrl }

    val result = searchArtistMotion(name, storefront)
    cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
    return result
  }

  private suspend fun searchArtistMotion(artistName: String, storefront: String): String? =
    try {
      val response =
        authorizedGet("$BASE_URL/v1/catalog/$storefront/search") {
          parameter("term", artistName)
          parameter("types", "artists")
          parameter("limit", "3")
        }
      when (response.status) {
        HttpStatusCode.Unauthorized -> {
          AppleMusicTokenProvider.clearToken()
          null
        }
        HttpStatusCode.OK -> resolveFirstMatchingArtist(response, artistName, storefront)
        else -> null
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }

  private suspend fun resolveFirstMatchingArtist(
    response: HttpResponse,
    artistName: String,
    storefront: String,
  ): String? {
    val root = response.body<JsonObject>()
    val artists =
      root.objectValue("results")?.objectValue("artists")?.arrayValue("data") ?: return null

    val ranked =
      artists
        .mapNotNull { element ->
          val artist = element as? JsonObject ?: return@mapNotNull null
          val name = artist.objectValue("attributes")?.textValue("name") ?: ""
          val score = nameScore(artistName, name)
          if (score == 0) null else score to artist
        }
        .sortedByDescending { it.first }

    for ((score, artist) in ranked) {
      if (score < MIN_NAME_SCORE) continue
      val artistId = artist.textValue("id") ?: continue
      val motionUrl = fetchArtistMotion(artistId, storefront)
      if (!motionUrl.isNullOrBlank()) return motionUrl
    }
    return null
  }

  private suspend fun fetchArtistMotion(artistId: String, storefront: String): String? =
    try {
      val response =
        authorizedGet("$BASE_URL/v1/catalog/$storefront/artists/$artistId") {
          parameter("extend", "editorialVideo,editorialArtwork")
          parameter("include", "albums")
          parameter("views", "featured-albums,top-music-videos")
          parameter("platform", "web")
        }
      when (response.status) {
        HttpStatusCode.Unauthorized -> {
          AppleMusicTokenProvider.clearToken()
          null
        }
        HttpStatusCode.OK -> extractArtistMotion(response)
        else -> null
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }

  private suspend fun extractArtistMotion(response: HttpResponse): String? {
    val root = response.body<JsonObject>()
    val artist = root.arrayValue("data")?.firstOrNull() as? JsonObject ?: return null
    val attributes = artist.objectValue("attributes")

    attributes?.objectValue("editorialVideo")?.let(::firstVideoUrl)?.let { url ->
      if (url.isNotBlank()) return url
    }
    attributes?.objectValue("editorialArtwork")?.let(::firstVideoUrl)?.let { url ->
      if (url.isNotBlank()) return url
    }

    val albums = mutableListOf<JsonObject>()
    artist
      .objectValue("views")
      ?.objectValue("featured-albums")
      ?.arrayValue("data")
      ?.forEach { (it as? JsonObject)?.let(albums::add) }
    artist
      .objectValue("relationships")
      ?.objectValue("albums")
      ?.arrayValue("data")
      ?.forEach { (it as? JsonObject)?.let(albums::add) }

    for (album in albums) {
      val url = album.objectValue("attributes")?.objectValue("editorialVideo")?.let(::firstVideoUrl)
      if (!url.isNullOrBlank()) return url
    }
    return null
  }

  private fun firstVideoUrl(editorial: JsonObject): String? {
    for (key in preferredMotionKeys) {
      val url = editorial.objectValue(key)?.textValue("video")
      if (!url.isNullOrBlank()) return url
    }
    for (value in editorial.values) {
      val url = (value as? JsonObject)?.textValue("video")
      if (!url.isNullOrBlank()) return url
    }
    return null
  }

  private fun nameScore(requested: String, candidate: String): Int {
    if (candidate.isBlank()) return 0
    if (
      !candidate.contains(requested, ignoreCase = true) &&
        !requested.contains(candidate, ignoreCase = true)
    ) {
      return 0
    }
    return if (candidate.equals(requested, ignoreCase = true)) 10 else 5
  }

  private suspend fun authorizedGet(
    url: String,
    block: HttpRequestBuilder.() -> Unit,
  ): HttpResponse {
    val token = AppleMusicTokenProvider.getToken()
    return client.get(url) {
      header("Authorization", "Bearer $token")
      header("Origin", "https://music.apple.com")
      header("Referer", "https://music.apple.com/")
      header("User-Agent", USER_AGENT)
      block()
    }
  }

  private fun cacheKey(prefix: String, vararg parts: String): String =
    "$prefix|" + parts.joinToString("|") { it.trim().lowercase(Locale.ROOT) }
}
