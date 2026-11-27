package com.revenge.canvas

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Resolves TIDAL track/album search results into an animated artwork.
 *
 * Process-wide singleton: the in-memory cache and the resolved country code live for the whole
 * process. Both entry points suspend on the caller's context and never switch dispatchers.
 */
object TidalCanvasProvider {
  private const val SEARCH_URL = "https://api.tidal.com/v1/search"
  private const val CLIENT_TOKEN = "CzET4vdadNUFQ5JU"
  private const val CACHE_TTL_MS = 86_400_000L
  private const val MAX_RESULTS = "10"

  private data class Entry(val artwork: CanvasArtwork, val expiresAt: Long)

  private val cache = ConcurrentHashMap<String, Entry>()
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
  }

  @Volatile internal var transport: CanvasTransport = ktorCanvasTransport(10, 15, 15)

  @Volatile internal var clock: () -> Long = System::currentTimeMillis

  internal val countryCode: String =
    Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.ROOT) ?: "US"

  internal fun resetForTests() {
    cache.clear()
    transport = ktorCanvasTransport(10, 15, 15)
    clock = System::currentTimeMillis
  }

  suspend fun getBySongArtist(song: String, artist: String, album: String? = null): CanvasArtwork? {
    if (song.isBlank() || artist.isBlank()) return null

    val key =
      "song|" + normalized(song) + "|" + normalized(artist) + "|" + normalized(album.orEmpty())
    freshEntry(key)?.let {
      return it
    }

    val query = if (album.isNullOrBlank()) "$artist $song" else "$album $artist $song"
    val artwork = search(query, "TRACKS", artist, song, albumOnly = false) ?: return null
    store(key, artwork)
    return artwork
  }

  suspend fun getByAlbumArtist(album: String, artist: String): CanvasArtwork? {
    if (album.isBlank() || artist.isBlank()) return null

    val key = "album|" + normalized(album) + "|" + normalized(artist)
    freshEntry(key)?.let {
      return it
    }

    val artwork = search("$album $artist", "ALBUMS", artist, album, albumOnly = true) ?: return null
    store(key, artwork)
    return artwork
  }

  private suspend fun search(
    query: String,
    types: String,
    requestedArtist: String,
    requestedTitle: String,
    albumOnly: Boolean,
  ): CanvasArtwork? =
    try {
      val response =
        transport.get(
          SEARCH_URL,
          listOf(
            "query" to query,
            "limit" to MAX_RESULTS,
            "types" to types,
            "countryCode" to countryCode,
          ),
          mapOf("X-Tidal-Token" to CLIENT_TOKEN),
        )
      if (response.status != 200) {
        null
      } else {
        val payload = json.parseToJsonElement(response.body)
        val items = locateItems(payload, types.lowercase(Locale.ROOT))
        items?.firstNotNullOfOrNull { element ->
          mapCandidate(element as? JsonObject, requestedArtist, requestedTitle, albumOnly)
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }

  private fun mapCandidate(
    item: JsonObject?,
    requestedArtist: String,
    requestedTitle: String,
    albumOnly: Boolean,
  ): CanvasArtwork? {
    if (item == null) return null

    val title = tidalString(item["title"])
    if (title != null) {
      val titleAccepted =
        if (albumOnly) {
          title.contains(requestedTitle, ignoreCase = true) ||
            requestedTitle.contains(title, ignoreCase = true)
        } else {
          title.contains(requestedTitle, ignoreCase = true)
        }
      if (!titleAccepted) return null
    }

    val primaryArtist =
      tidalString(item["artist"].let { (it as? JsonObject)?.get("name") })
        ?: (item["artists"] as? JsonArray)?.firstOrNull()?.let {
          tidalString((it as? JsonObject)?.get("name"))
        }
    if (primaryArtist != null && !artistsMatch(requestedArtist, primaryArtist)) return null

    val owner = if (albumOnly) item else (item["album"] as? JsonObject ?: return null)
    val cover = tidalString(owner["videoCover"])
    if (cover.isNullOrBlank()) return null
    val videoUrl = tidalCoverUrl(cover) ?: return null

    return CanvasArtwork(
      name = title ?: requestedTitle,
      artist = primaryArtist ?: requestedArtist,
      videoUrl = videoUrl,
      albumName = if (albumOnly) title else tidalString(owner["title"]),
    )
  }

  private fun locateItems(element: JsonElement, section: String): JsonArray? {
    val root = element as? JsonObject
    if (root != null) {
      val sectionObject =
        root.entries.firstOrNull { it.key.lowercase(Locale.ROOT) == section }?.value as? JsonObject
      (sectionObject?.get("items") as? JsonArray)?.let {
        return it
      }
    }
    return firstItemsArray(element)
  }

  private fun firstItemsArray(element: JsonElement): JsonArray? {
    when (element) {
      is JsonArray -> {
        for (child in element) {
          firstItemsArray(child)?.let {
            return it
          }
        }
      }
      is JsonObject -> {
        (element["items"] as? JsonArray)?.let {
          return it
        }
        for (child in element.values) {
          firstItemsArray(child)?.let {
            return it
          }
        }
      }
      else -> Unit
    }
    return null
  }

  private fun freshEntry(key: String): CanvasArtwork? {
    val entry = cache[key] ?: return null
    if (entry.expiresAt > clock()) return entry.artwork
    cache.remove(key)
    return null
  }

  private fun store(key: String, artwork: CanvasArtwork) {
    cache[key] = Entry(artwork, clock() + CACHE_TTL_MS)
  }

  private fun normalized(value: String): String = value.trim().lowercase(Locale.ROOT)
}

/** Formats a five-segment TIDAL video cover id, or returns null when the id is malformed. */
internal fun tidalCoverUrl(coverId: String): String? {
  val segments = coverId.split('-')
  if (segments.size != 5 || segments.any { it.isBlank() }) return null
  return "https://resources.tidal.com/videos/${segments[0]}/${segments[1]}/${segments[2]}/${segments[3]}/${segments[4]}/1280x1280.mp4"
}

/** Artist-equivalence rule used for both TIDAL search entry points. */
internal fun artistsMatch(requested: String?, returned: String?): Boolean {
  val left = splitArtistNames(requested)
  val right = splitArtistNames(returned)
  if (left.isEmpty() || right.isEmpty()) return false
  return left.any { a -> right.any { b -> a.contains(b) || b.contains(a) } }
}

private val ARTIST_SEPARATOR =
  Regex(
    """\s*(?:,|&|×|\s+x\s+|\b(?:feat|ft)\.?\s+|\bfeaturing\b|\bwith\b)\s*""",
    RegexOption.IGNORE_CASE,
  )

private fun splitArtistNames(raw: String?): List<String> =
  raw
    ?.split(ARTIST_SEPARATOR)
    ?.map { it.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT) }
    ?.filter { it.isNotEmpty() }
    .orEmpty()

private fun tidalString(element: JsonElement?): String? =
  (element as? JsonPrimitive)?.takeIf { it.isString }?.content
