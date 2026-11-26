package com.revenge.applecanvas

import com.revenge.canvas.AppleMusicTokenProvider
import com.revenge.canvas.CanvasArtwork
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val AMP_BASE_URL = "https://amp-api.music.apple.com"
private const val SEARCH_LIMIT = 10
private const val MIN_CANDIDATE_SCORE = 12
private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
private const val USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private val PREFERRED_MOTION_KEYS =
  listOf(
    "motionSquareVideo1x1",
    "motionDetailSquare",
    "motionTallVideo3x4",
    "motionDetailTall",
    "motionDetailRaw",
    "motionDetailStatic",
  )

private val EDITION_WORDS =
  listOf("deluxe", "expanded", "remastered", "remix", "version", "edit", "mix", "bonus")

private val COLLECTION_BLACKLIST =
  listOf("playlist", "set list", "essentials", "session", "dj mix", "mixed", "apple music", "today's hits")

private fun log(message: String) = println("AppleMusicCanvas: $message")

private fun logFailure(error: Throwable, message: String) {
  println("AppleMusicCanvas: $message")
  error.printStackTrace()
}

/**
 * Resolves Apple Music motion artwork (HLS "canvas" video) for albums and songs through the public
 * Apple Music web API.
 *
 * Two strategies are attempted:
 * 1. `editorialVideo` returned directly by the catalog search endpoint (extended with
 *    `?extend=editorialVideo`).
 * 2. A follow-up album lookup (`?extend=editorialVideo&include=tracks`) for the album that owns the
 *    matched song. This covers albums whose motion artwork is not exposed on the search result.
 *
 * Positive results are cached for 24 hours; lookup misses are not cached so a transient API failure
 * can be retried, except for explicit album-id lookups where the result is fully cached.
 */
object AppleMusicCanvasProvider {

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
  }

  private val client by lazy {
    HttpClient(OkHttp) {
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

  private data class CacheEntry(val value: CanvasArtwork?, val expiresAtMs: Long)

  private val cache = ConcurrentHashMap<String, CacheEntry>()

  /** Fetches motion artwork for an album by album title and artist. */
  suspend fun getByAlbumArtist(
    album: String,
    artist: String,
    storefront: String = "us",
  ): CanvasArtwork? =
    cached(cacheKey("album", album, artist, storefront), cacheMisses = false) {
      searchAndResolve(
        term = album,
        artist = artist,
        album = album,
        storefront = storefront,
        types = "albums",
      )
    }

  /**
   * Fetches motion artwork for a song by song title and artist. When [album] is supplied the search
   * is narrowed to that album and the returned artwork keeps the song title as its name.
   */
  suspend fun getBySongArtist(
    song: String,
    artist: String,
    album: String? = null,
    storefront: String = "us",
  ): CanvasArtwork? =
    cached(cacheKey("song", song, artist, album ?: "", storefront), cacheMisses = false) {
      searchAndResolve(
        term = song,
        artist = artist,
        album = album,
        storefront = storefront,
        types = "songs",
      )
    }

  /** Fetches motion artwork for a known Apple Music album id. */
  suspend fun getByAlbumId(
    albumId: String,
    storefront: String = "us",
  ): CanvasArtwork? =
    cached(cacheKey("id", albumId, storefront), cacheMisses = true) {
      fetchAlbumArtwork(albumId = albumId, storefront = storefront, fallbackArtist = null)
    }

  private suspend fun cached(
    key: String,
    cacheMisses: Boolean,
    fetch: suspend () -> CanvasArtwork?,
  ): CanvasArtwork? {
    val now = System.currentTimeMillis()
    cache[key]?.takeIf { it.expiresAtMs > now }?.let { return it.value }

    val value = fetch()
    if (value != null || cacheMisses) {
      cache[key] = CacheEntry(value, System.currentTimeMillis() + CACHE_TTL_MS)
    }
    return value
  }

  private suspend fun searchAndResolve(
    term: String,
    artist: String,
    album: String?,
    storefront: String,
    types: String,
  ): CanvasArtwork? {
    if (term.isBlank() || artist.isBlank()) return null
    return try {
      val query = buildQuery(term, artist, album)
      val root =
        ampJson(
          "$AMP_BASE_URL/v1/catalog/$storefront/search",
          "term" to query,
          "types" to types,
          "limit" to SEARCH_LIMIT.toString(),
          "extend" to "editorialVideo",
          "include" to "albums",
        ) ?: return null

      val data = root.obj("results")?.obj(types)?.arr("data") ?: return null
      val ranked =
        data
          .mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val attributes = item.obj("attributes") ?: return@mapNotNull null
            val score = scoreCandidate(attributes, term, artist, album) ?: return@mapNotNull null
            score to item
          }
          .sortedByDescending { it.first }

      for ((score, item) in ranked) {
        if (score < MIN_CANDIDATE_SCORE) continue
        val attributes = item.obj("attributes") ?: continue
        val isSong = item.text("type") == "songs"
        val resultArtist = attributes.text("artistName") ?: ""
        val albumId = resolveAlbumId(item, attributes) ?: continue

        val directUrl = attributes.obj("editorialVideo")?.let(::extractEditorialVideoUrl)
        if (!directUrl.isNullOrBlank()) {
          return CanvasArtwork(
            name = attributes.text("name"),
            artist = resultArtist,
            albumId = albumId,
            albumName = if (isSong) attributes.text("collectionName") else attributes.text("name"),
            animated = directUrl,
          )
        }

        val fetched =
          fetchAlbumArtwork(
            albumId = albumId,
            storefront = storefront,
            fallbackArtist = resultArtist,
            titleOverride = if (isSong) attributes.text("name") else null,
            artistOverride = if (isSong) resultArtist else null,
          )
        if (fetched != null) return fetched
      }
      null
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      logFailure(error, "search failed for '$term' by '$artist'")
      null
    }
  }

  private suspend fun fetchAlbumArtwork(
    albumId: String,
    storefront: String,
    fallbackArtist: String?,
    titleOverride: String? = null,
    artistOverride: String? = null,
  ): CanvasArtwork? {
    if (albumId.isBlank() || albumId.startsWith("pl.")) return null
    return try {
      val root =
        ampJson(
          "$AMP_BASE_URL/v1/catalog/$storefront/albums/$albumId",
          "extend" to "editorialVideo",
          "include" to "tracks",
          "platform" to "web",
        ) ?: return null

      albumArtworkFromResponse(root, albumId, fallbackArtist, titleOverride, artistOverride)
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      logFailure(error, "album lookup failed for $albumId")
      null
    }
  }

  private suspend fun ampJson(url: String, vararg parameters: Pair<String, String>): JsonObject? {
    val token = AppleMusicTokenProvider.getToken()
    val response =
      client.get(url) {
        header("Authorization", "Bearer $token")
        header("Origin", "https://music.apple.com")
        header("Referer", "https://music.apple.com/")
        header("User-Agent", USER_AGENT)
        for ((name, value) in parameters) parameter(name, value)
      }

    if (response.status == HttpStatusCode.Unauthorized) {
      AppleMusicTokenProvider.clearToken()
      return null
    }
    if (response.status != HttpStatusCode.OK) {
      log("request to $url failed with ${response.status}")
      return null
    }
    val body = response.bodyAsText()
    return try {
      json.parseToJsonElement(body) as? JsonObject
    } catch (error: Exception) {
      log("malformed JSON from $url: ${error.message}")
      null
    }
  }
}

/** Builds the free-text search query, avoiding repeated artist/album text. */
internal fun buildQuery(term: String, artist: String, album: String?): String {
  var query = if (term.contains(artist, ignoreCase = true)) term else "$artist $term"
  if (!album.isNullOrBlank() && !query.contains(album, ignoreCase = true)) {
    query = "$query $album"
  }
  return query
}

/**
 * Scores a search result against the requested term. Returns `null` when the result is not a
 * plausible match (wrong artist, playlist/compilation, or a different title).
 */
internal fun scoreCandidate(
  attributes: JsonObject,
  term: String,
  artist: String,
  album: String?,
): Int? {
  val name = attributes.text("name") ?: ""
  val resultArtist = attributes.text("artistName") ?: ""
  val collection = attributes.text("collectionName") ?: ""

  if (isBlacklisted(name) || isBlacklisted(collection)) return null
  if (!artistMatches(resultArtist, artist)) return null

  var score = if (resultArtist.equals(artist, ignoreCase = true)) 10 else 5

  score +=
    when {
      name.equals(term, ignoreCase = true) -> 15
      name.contains(term, ignoreCase = true) || term.contains(name, ignoreCase = true) -> 7
      else -> -10
    }

  for (word in EDITION_WORDS) {
    val inTerm = term.contains(word, ignoreCase = true)
    val inResult = name.contains(word, ignoreCase = true)
    if (inTerm && inResult) score += 5 else if (!inTerm && inResult) score -= 3
  }

  if (!album.isNullOrBlank() && collection.isNotBlank()) {
    score +=
      when {
        collection.equals(album, ignoreCase = true) -> 20
        collection.contains(album, ignoreCase = true) ||
          album.contains(collection, ignoreCase = true) -> 10
        else -> 0
      }
  }

  return score
}

internal fun isBlacklisted(text: String): Boolean {
  if (text.isBlank()) return false
  val lower = text.lowercase(Locale.ROOT)
  return COLLECTION_BLACKLIST.any { lower.contains(it) }
}

internal fun artistMatches(resultArtist: String, artist: String): Boolean =
  resultArtist.equals(artist, ignoreCase = true) ||
    resultArtist.contains(artist, ignoreCase = true) ||
    artist.contains(resultArtist, ignoreCase = true)

/** Resolves the owning album id from an album or song search result. */
internal fun resolveAlbumId(item: JsonObject, attributes: JsonObject): String? {
  val id =
    when (item.text("type")) {
      "songs" ->
        item
          .obj("relationships")
          ?.obj("albums")
          ?.arr("data")
          ?.firstOrNull()
          ?.let { it as? JsonObject }
          ?.text("id")
          ?: attributes.text("collectionId")
          ?: albumIdFromUrl(attributes.text("url"))
      else -> item.text("id")
    }
  return id?.takeIf { it.isNotBlank() && !it.startsWith("pl.") }
}

/** Extracts the catalog album id from a `music.apple.com/.../album/<slug>/<id>?i=...` URL. */
internal fun albumIdFromUrl(url: String?): String? {
  if (url.isNullOrBlank()) return null
  val albumPart = url.substringAfter("/album/", "").substringBefore("?")
  val id = albumPart.substringAfterLast("/", "")
  return id.takeIf { candidate -> candidate.isNotBlank() && candidate.all { it.isDigit() } }
}

/** Picks the best motion artwork URL from an `editorialVideo` object. */
internal fun extractEditorialVideoUrl(editorialVideo: JsonObject): String? {
  for (key in PREFERRED_MOTION_KEYS) {
    editorialVideo.obj(key)?.videoUrl()?.takeIf { it.isNotBlank() }?.let { return it }
  }
  for (value in editorialVideo.values) {
    val asset = value as? JsonObject ?: continue
    asset.videoUrl()?.takeIf { it.isNotBlank() }?.let { return it }
  }
  return null
}

/** Maps an album endpoint response to [CanvasArtwork], returning `null` when no motion exists. */
internal fun albumArtworkFromResponse(
  root: JsonObject,
  albumId: String,
  fallbackArtist: String?,
  titleOverride: String? = null,
  artistOverride: String? = null,
): CanvasArtwork? {
  val album = root.arr("data")?.firstOrNull() as? JsonObject ?: return null
  val attributes = album.obj("attributes") ?: return null
  val albumName = attributes.text("name") ?: ""
  if (isBlacklisted(albumName)) return null

  val animated = attributes.obj("editorialVideo")?.let(::extractEditorialVideoUrl) ?: return null
  if (animated.isBlank()) return null

  return CanvasArtwork(
    name = titleOverride ?: albumName,
    artist = artistOverride ?: attributes.text("artistName") ?: fallbackArtist,
    albumId = albumId,
    albumName = albumName,
    animated = animated,
  )
}

internal fun cacheKey(prefix: String, vararg parts: String): String =
  buildString {
    append(prefix)
    for (part in parts) {
      append('|')
      append(part.trim().lowercase(Locale.ROOT))
    }
  }

private fun JsonObject.videoUrl(): String? =
  text("video") ?: text("videoUrl") ?: text("hlsUrl") ?: text("url")

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
