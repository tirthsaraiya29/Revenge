package com.revenge.canvas

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Resolves an artist name to a bare HLS background-video URL for the artist page. Misses are cached
 * alongside hits, as the artist page retries the same name repeatedly.
 */
object AppleMusicArtistBackgroundProvider {
  private const val CACHE_TTL_MS = 86_400_000L
  private const val MIN_SCORE = 4
  private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

  private data class Entry(val value: String?, val expiresAt: Long)

  private data class ScoredArtist(val id: String, val score: Int)

  private data class Detail(val unauthorized: Boolean, val url: String?)

  private val cache = ConcurrentHashMap<String, Entry>()
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
  }

  @Volatile internal var transport: CanvasTransport = ktorCanvasTransport(10, 15, 15)
  @Volatile internal var clock: () -> Long = System::currentTimeMillis
  @Volatile internal var tokenSource: suspend () -> String = { AppleMusicTokenProvider.getToken() }
  @Volatile
  internal var tokenInvalidator: suspend () -> Unit = { AppleMusicTokenProvider.clearToken() }

  internal fun resetForTests() {
    cache.clear()
    transport = ktorCanvasTransport(10, 15, 15)
    clock = System::currentTimeMillis
    tokenSource = { AppleMusicTokenProvider.getToken() }
    tokenInvalidator = { AppleMusicTokenProvider.clearToken() }
  }

  suspend fun getByArtistName(artistName: String, storefront: String = "us"): String? {
    val name = artistName.trim()
    if (name.isEmpty()) return null

    val key = "artist|" + normalized(name) + "|" + normalized(storefront)
    val now = clock()
    cache[key]?.let { if (it.expiresAt > now) return it.value }

    val result =
      try {
        resolve(name, storefront)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        null
      }

    cache[key] = Entry(result, clock() + CACHE_TTL_MS)
    return result
  }

  private suspend fun resolve(name: String, storefront: String): String? {
    val token = tokenSource()
    val search =
      transport.get(
        "https://amp-api.music.apple.com/v1/catalog/${normalized(storefront)}/search",
        listOf("term" to name, "types" to "artists", "limit" to "3"),
        ampHeaders(token),
      )
    if (search.status == 401) {
      tokenInvalidator()
      return null
    }
    if (search.status != 200) return null

    val root = json.parseToJsonElement(search.body) as? JsonObject ?: return null
    val results = root["results"] as? JsonObject ?: return null
    val artistsSection = results["artists"] as? JsonObject ?: return null
    val artists = artistsSection["data"] as? JsonArray ?: return null

    val ranked =
      artists
        .mapNotNull { element ->
          val artist = element as? JsonObject ?: return@mapNotNull null
          val id = jsonString(artist["id"])
          if (id.isNullOrBlank()) return@mapNotNull null
          val candidateName = jsonString((artist["attributes"] as? JsonObject)?.get("name"))
          ScoredArtist(id, nameScore(name, candidateName))
        }
        .sortedByDescending { it.score }

    for (candidate in ranked) {
      if (candidate.score < MIN_SCORE) continue
      val detail = fetchDetail(candidate.id, storefront, token)
      if (detail.unauthorized) {
        tokenInvalidator()
        return null
      }
      if (detail.url != null) return detail.url
    }
    return null
  }

  private suspend fun fetchDetail(id: String, storefront: String, token: String): Detail {
    val response =
      transport.get(
        "https://amp-api.music.apple.com/v1/catalog/${normalized(storefront)}/artists/$id",
        listOf(
          "extend" to "editorialVideo,editorialArtwork",
          "include" to "albums",
          "views" to "featured-albums,top-music-videos",
          "platform" to "web",
        ),
        ampHeaders(token),
      )
    if (response.status == 401) return Detail(unauthorized = true, url = null)
    if (response.status != 200) return Detail(unauthorized = false, url = null)

    val root = json.parseToJsonElement(response.body) as? JsonObject ?: return Detail(false, null)
    val data = root["data"] as? JsonArray ?: return Detail(false, null)
    val artist = data.firstOrNull() as? JsonObject ?: return Detail(false, null)
    return Detail(unauthorized = false, url = motionFromArtist(artist))
  }

  private fun motionFromArtist(artist: JsonObject): String? {
    val attributes = artist["attributes"] as? JsonObject ?: JsonObject(emptyMap())
    firstMotionUrl(attributes["editorialVideo"] as? JsonObject)?.let {
      return it
    }
    firstMotionUrl(attributes["editorialArtwork"] as? JsonObject)?.let {
      return it
    }

    val containers = listOf(artist, attributes)
    val featured = containers.flatMap { container ->
      val views = container["views"] as? JsonObject ?: return@flatMap emptyList()
      val section = views["featured-albums"] as? JsonObject
      val data = section?.get("data") as? JsonArray ?: return@flatMap emptyList()
      data.mapNotNull { it as? JsonObject }
    }
    val related = containers.flatMap { container ->
      val relationships = container["relationships"] as? JsonObject ?: return@flatMap emptyList()
      val albums = relationships["albums"] as? JsonObject
      val data = albums?.get("data") as? JsonArray ?: return@flatMap emptyList()
      data.mapNotNull { it as? JsonObject }
    }
    for (album in featured + related) {
      val albumAttributes = album["attributes"] as? JsonObject
      val editorial = albumAttributes?.get("editorialVideo") as? JsonObject
      firstMotionUrl(editorial)?.let {
        return it
      }
    }
    return null
  }

  private fun ampHeaders(token: String): Map<String, String> =
    mapOf(
      "Authorization" to "Bearer $token",
      "Origin" to "https://music.apple.com",
      "Referer" to "https://music.apple.com/",
      "User-Agent" to DESKTOP_UA,
    )

  private fun normalized(value: String): String = value.trim().lowercase(Locale.ROOT)

  private fun jsonString(element: JsonElement?): String? =
    (element as? JsonPrimitive)?.contentOrNull
}

/** Name-similarity score: 0 unrelated/blank, 10 exact, 5 substring in either direction. */
internal fun nameScore(requested: String, candidate: String?): Int {
  if (candidate.isNullOrBlank()) return 0
  if (
    !candidate.contains(requested, ignoreCase = true) &&
      !requested.contains(candidate, ignoreCase = true)
  )
    return 0
  return if (candidate.equals(requested, ignoreCase = true)) 10 else 5
}

/**
 * First non-blank `video` URL in an editorial block: fixed key precedence first, then a
 * document-order scan of every nested object.
 */
internal fun firstMotionUrl(editorial: JsonObject?): String? {
  if (editorial == null) return null
  for (key in MOTION_KEY_ORDER) {
    val motion = editorial[key] as? JsonObject ?: continue
    jsonVideo(motion)?.let {
      return it
    }
  }
  for (value in editorial.values) {
    val motion = value as? JsonObject ?: continue
    jsonVideo(motion)?.let {
      return it
    }
  }
  return null
}

private fun jsonVideo(motion: JsonObject): String? =
  (motion["video"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private val MOTION_KEY_ORDER =
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
