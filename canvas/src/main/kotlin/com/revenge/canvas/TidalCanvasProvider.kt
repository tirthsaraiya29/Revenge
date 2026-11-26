package com.revenge.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Resolves video covers ("canvases") from the public Tidal v1 API, which exposes them on album
 * and track metadata as `videoCover`.
 *
 * Lookups are cached in memory for 24 hours. Network failures, malformed payloads and
 * non-200 responses all resolve to `null` so callers can fall back to other providers.
 */
object TidalCanvasProvider {
  private const val BASE_URL = "https://api.tidal.com/v1/"
  private const val API_TOKEN = "vNVdglQOjFJJGG2U"
  private const val VIDEO_FILENAME = "1280x1280.mp4"
  private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000

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
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
      }
      install(ContentEncoding) {
        gzip()
        deflate()
      }
      install(HttpCache)
      expectSuccess = false
    }
  }

  private data class CacheEntry(val artwork: CanvasArtwork?, val expiresAtMs: Long)

  private val cache = ConcurrentHashMap<String, CacheEntry>()

  private val countryCode: String by lazy {
    Locale.getDefault().country?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 } ?: "US"
  }

  suspend fun getBySongArtist(song: String, artist: String, album: String? = null): CanvasArtwork? {
    if (song.isBlank() || artist.isBlank()) return null
    val key = cacheKey("song", song, artist, album.orEmpty())
    cached(key)?.let { return it }

    val query = if (album.isNullOrBlank()) "$artist $song" else "$album $artist $song"
    return search(query, "TRACKS", song, artist, album).also { store(key, it) }
  }

  suspend fun getByAlbumArtist(album: String, artist: String): CanvasArtwork? {
    if (album.isBlank() || artist.isBlank()) return null
    val key = cacheKey("album", album, artist)
    cached(key)?.let { return it }

    return search("$album $artist", "ALBUMS", null, artist, album).also { store(key, it) }
  }

  private fun cached(key: String): CanvasArtwork? =
    cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.artwork

  private fun store(key: String, artwork: CanvasArtwork?) {
    if (artwork != null) cache[key] = CacheEntry(artwork, System.currentTimeMillis() + CACHE_TTL_MS)
  }

  private suspend fun search(
    query: String,
    types: String,
    song: String?,
    artist: String,
    album: String?,
  ): CanvasArtwork? {
    return try {
      val response =
        client.get("$BASE_URL/search") {
          header("X-Tidal-Token", API_TOKEN)
          parameter("query", query)
          parameter("limit", "10")
          parameter("types", types)
          parameter("countryCode", countryCode)
        }
      if (response.status != HttpStatusCode.OK) return null

      val items = response.body<JsonObject>().itemsFor(types) ?: return null
      items.firstNotNullOfOrNull { element ->
        (element as? JsonObject)?.let { parseItem(it, types, song, artist, album) }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }
  }

  private fun parseItem(
    item: JsonObject,
    types: String,
    song: String?,
    artist: String,
    album: String?,
  ): CanvasArtwork? {
    val title = item.textValue("title")

    if (types == "TRACKS" && song != null && title != null && !title.contains(song, true)) {
      return null
    }
    if (
      types == "ALBUMS" &&
        album != null &&
        title != null &&
        !title.contains(album, true) &&
        !album.contains(title, true)
    ) {
      return null
    }

    val primaryArtist =
      item.objectValue("artist")?.textValue("name")
        ?: item.arrayValue("artists")?.firstOrNull()?.let { (it as? JsonObject)?.textValue("name") }

    if (primaryArtist != null && !artistsMatch(artist, primaryArtist)) return null

    val owner = if (types == "TRACKS") item.objectValue("album") else item
    val coverId = owner?.textValue("videoCover") ?: return null
    val videoUrl = formatVideoUrl(coverId) ?: return null

    return CanvasArtwork(
      name = title ?: song ?: album ?: "",
      artist = primaryArtist ?: artist,
      videoUrl = videoUrl,
      albumName = if (types == "TRACKS") owner.textValue("title") else title,
    )
  }

  private fun JsonObject.itemsFor(types: String): JsonArray? =
    objectValue(types.lowercase(Locale.ROOT))?.arrayValue("items") ?: findItemsSection(this)

  private fun findItemsSection(element: JsonElement): JsonArray? =
    when (element) {
      is JsonArray -> element.firstNotNullOfOrNull(::findItemsSection)
      is JsonObject ->
        element.arrayValue("items") ?: element.values.firstNotNullOfOrNull(::findItemsSection)
      else -> null
    }

  /**
   * Tidal cover IDs are five dash-separated path segments, e.g. `1a2b-3c4d-5e6f-7a8b-9c0d`.
   * Returns the public 1280x1280 progressive MP4 for that cover, or null when malformed.
   */
  internal fun formatVideoUrl(id: String): String? {
    val parts = id.split('-')
    if (parts.size != 5 || parts.any { it.isBlank() }) return null
    return "https://resources.tidal.com/videos/${parts.joinToString("/")}/$VIDEO_FILENAME"
  }

  /** True when any requested artist name overlaps the returned billing name. */
  internal fun artistsMatch(requested: String, returned: String): Boolean {
    val requestedParts = splitArtists(requested)
    val returnedParts = splitArtists(returned)
    return requestedParts.any { req -> returnedParts.any { res -> res.contains(req) || req.contains(res) } }
  }

  private fun splitArtists(raw: String): List<String> =
    raw.split(ARTIST_SEPARATOR)
      .map { it.replace(WHITESPACE, " ").trim().lowercase(Locale.ROOT) }
      .filter { it.isNotBlank() }

  private fun cacheKey(prefix: String, vararg parts: String): String =
    "$prefix|" + parts.joinToString("|") { it.trim().lowercase(Locale.ROOT) }

  private val WHITESPACE = Regex("\\s+")

  private val ARTIST_SEPARATOR =
    Regex(
      """(?:\s*,\s*|\s*&\s*|\s+×\s+|\s+x\s+|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b)""",
      RegexOption.IGNORE_CASE,
    )
}
