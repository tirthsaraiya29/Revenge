package com.revenge.applecanvas

import com.revenge.canvas.AppleMusicTokenProvider
import com.revenge.canvas.CanvasArtwork
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Apple Music (AMP) album/song canvas lookup. Returns a [CanvasArtwork] whose `animated`
 * field carries the extracted HLS URL. Positive album/song results are cached; album-id
 * lookups additionally cache misses.
 */
object AppleMusicCanvasProvider {
    private const val CACHE_TTL_MS = 86_400_000L
    private const val MIN_SCORE = 12
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    private const val LOG_PREFIX = "AppleMusicCanvas: "

    private val EDITION_WORDS = listOf("deluxe", "expanded", "remastered", "remix", "version", "edit", "mix", "bonus")
    private val BLACKLIST_TERMS = listOf(
        "playlist", "set list", "essentials", "session", "dj mix", "mixed", "apple music", "today's hits",
    )
    private val EDITORIAL_KEY_ORDER = listOf(
        "motionSquareVideo1x1", "motionDetailSquare", "motionTallVideo3x4",
        "motionDetailTall", "motionDetailRaw", "motionDetailStatic",
    )
    private val EDITORIAL_URL_FIELDS = listOf("video", "videoUrl", "hlsUrl", "url")

    private data class Entry(val artwork: CanvasArtwork?, val expiresAt: Long)
    private data class ScoredItem(val item: JsonObject, val attributes: JsonObject, val score: Int)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Volatile internal var transport: AmpTransport = ktorAmpTransport(15, 25, 25)
    @Volatile internal var clock: () -> Long = System::currentTimeMillis
    @Volatile internal var tokenSource: suspend () -> String = { AppleMusicTokenProvider.getToken() }
    @Volatile internal var tokenInvalidator: suspend () -> Unit = { AppleMusicTokenProvider.clearToken() }

    internal fun resetForTests() {
        cache.clear()
        transport = ktorAmpTransport(15, 25, 25)
        clock = System::currentTimeMillis
        tokenSource = { AppleMusicTokenProvider.getToken() }
        tokenInvalidator = { AppleMusicTokenProvider.clearToken() }
    }

    suspend fun getByAlbumArtist(album: String, artist: String, storefront: String = "us"): CanvasArtwork? {
        if (album.isBlank() || artist.isBlank()) return null
        return lookup(cacheKey("album", album, artist, storefront), album, artist, null, storefront, "albums")
    }

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        album: String? = null,
        storefront: String = "us",
    ): CanvasArtwork? {
        if (song.isBlank() || artist.isBlank()) return null
        return lookup(cacheKey("song", song, artist, album.orEmpty(), storefront), song, artist, album, storefront, "songs")
    }

    suspend fun getByAlbumId(albumId: String, storefront: String = "us"): CanvasArtwork? {
        val key = cacheKey("id", albumId, storefront)
        val cached = cache[key]
        if (cached != null && cached.expiresAt > clock()) return cached.artwork

        val artwork = try {
            if (albumId.isBlank() || albumId.startsWith("pl.")) null
            else fetchAlbumArtwork(albumId, storefront, fallbackArtist = "", titleOverride = null, artistOverride = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report("album id lookup failed", e)
            null
        }
        cache[key] = Entry(artwork, clock() + CACHE_TTL_MS)
        return artwork
    }

    private suspend fun lookup(
        key: String,
        term: String,
        artist: String,
        album: String?,
        storefront: String,
        types: String,
    ): CanvasArtwork? {
        fresh(key)?.let { return it }

        val artwork = try {
            searchAndResolve(term, artist, album, storefront, types)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report("search failed", e)
            null
        }
        if (artwork != null) cache[key] = Entry(artwork, clock() + CACHE_TTL_MS)
        return artwork
    }

    private suspend fun searchAndResolve(
        term: String,
        artist: String,
        album: String?,
        storefront: String,
        types: String,
    ): CanvasArtwork? {
        val token = tokenSource()
        val response = transport.get(
            "https://amp-api.music.apple.com/v1/catalog/${storefront.trim().lowercase(Locale.ROOT)}/search",
            listOf(
                "term" to buildQuery(term, artist, album),
                "types" to types,
                "limit" to "10",
                "extend" to "editorialVideo",
                "include" to "albums",
            ),
            ampHeaders(token),
        )
        if (response.status == 401) {
            tokenInvalidator()
            return null
        }
        if (response.status != 200) return null

        val root = json.parseToJsonElement(response.body) as? JsonObject ?: return null
        val results = root["results"] as? JsonObject ?: return null
        val section = results[types] as? JsonObject ?: return null
        val data = section["data"] as? JsonArray ?: return null

        val ranked = data.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val attributes = item["attributes"] as? JsonObject ?: return@mapNotNull null
            val score = candidateScore(term, artist, album, attributes) ?: return@mapNotNull null
            ScoredItem(item, attributes, score)
        }.sortedByDescending { it.score }

        for (candidate in ranked) {
            if (candidate.score < MIN_SCORE) continue
            val item = candidate.item
            val attributes = candidate.attributes
            val isSong = jsonString(item["type"]) == "songs"
            val resultArtist = jsonString(attributes["artistName"]) ?: ""
            val albumId = resolveAlbumId(item, attributes) ?: continue
            val directUrl = extractEditorialUrl(attributes["editorialVideo"])
            if (directUrl != null) {
                val albumTitle = jsonString(attributes["name"]) ?: ""
                return CanvasArtwork(
                    name = albumTitle,
                    artist = resultArtist,
                    albumId = albumId,
                    albumName = if (isSong) jsonString(attributes["collectionName"]) else albumTitle,
                    animated = directUrl,
                )
            }
            val resolved = fetchAlbumArtwork(
                albumId = albumId,
                storefront = storefront,
                fallbackArtist = resultArtist,
                titleOverride = if (isSong) jsonString(attributes["name"]) else null,
                artistOverride = if (isSong) resultArtist else null,
            )
            if (resolved != null) return resolved
        }
        return null
    }

    private suspend fun fetchAlbumArtwork(
        albumId: String,
        storefront: String,
        fallbackArtist: String,
        titleOverride: String?,
        artistOverride: String?,
    ): CanvasArtwork? {
        if (albumId.isBlank() || albumId.startsWith("pl.")) return null

        val token = tokenSource()
        val response = transport.get(
            "https://amp-api.music.apple.com/v1/catalog/${storefront.trim().lowercase(Locale.ROOT)}/albums/$albumId",
            listOf(
                "extend" to "editorialVideo",
                "include" to "tracks",
                "platform" to "web",
            ),
            ampHeaders(token),
        )
        if (response.status == 401) {
            tokenInvalidator()
            return null
        }
        if (response.status != 200) return null

        val root = try {
            json.parseToJsonElement(response.body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report("album payload decode failed", e)
            return null
        }
        return mapAlbumResponse(root, albumId, fallbackArtist, titleOverride, artistOverride)
    }

    private fun fresh(key: String): CanvasArtwork? {
        val entry = cache[key] ?: return null
        if (entry.expiresAt > clock()) return entry.artwork
        cache.remove(key)
        return null
    }

    private fun cacheKey(vararg parts: String): String =
        parts.joinToString("|") { it.trim().lowercase(Locale.ROOT) }

    private fun ampHeaders(token: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $token",
        "Origin" to "https://music.apple.com",
        "Referer" to "https://music.apple.com/",
        "User-Agent" to DESKTOP_UA,
    )

    private fun report(message: String, error: Throwable) {
        println(LOG_PREFIX + message)
        error.printStackTrace()
    }
}

/** Free-text query: never duplicate the artist, append the album hint only when absent. */
internal fun buildQuery(term: String, artist: String, album: String?): String {
    var query = if (term.contains(artist, ignoreCase = true)) term else "$artist $term"
    if (!album.isNullOrBlank() && !query.contains(album, ignoreCase = true)) {
        query += " $album"
    }
    return query
}

/** Candidate score, or null when the candidate must be rejected outright. */
internal fun candidateScore(term: String, artist: String, album: String?, attributes: JsonObject): Int? {
    val name = ampString(attributes["name"]) ?: ""
    val collection = ampString(attributes["collectionName"])
    if (isBlacklisted(name) || isBlacklisted(collection)) return null

    val returnedArtist = ampString(attributes["artistName"]) ?: ""
    if (!artistMatches(artist, returnedArtist)) return null

    var score = if (returnedArtist.equals(artist, ignoreCase = true)) 10 else 5

    score += when {
        name.equals(term, ignoreCase = true) -> 15
        name.contains(term, ignoreCase = true) || term.contains(name, ignoreCase = true) -> 7
        else -> -10
    }

    for (word in EDITION_WORDS) {
        val inTerm = term.contains(word, ignoreCase = true)
        val inName = name.contains(word, ignoreCase = true)
        score += when {
            inTerm && inName -> 5
            inName -> -3
            else -> 0
        }
    }

    if (!album.isNullOrBlank() && !collection.isNullOrBlank()) {
        score += when {
            collection.equals(album, ignoreCase = true) -> 20
            collection.contains(album, ignoreCase = true) || album.contains(collection, ignoreCase = true) -> 10
            else -> 0
        }
    }
    return score
}

/** Owning album id for a search item, or null for playlist ids and missing ids. */
internal fun resolveAlbumId(item: JsonObject, attributes: JsonObject): String? {
    val raw = if (ampString(item["type"]) == "songs") {
        val relationships = item["relationships"] as? JsonObject
        val albums = relationships?.get("albums") as? JsonObject
        val albumData = albums?.get("data") as? JsonArray
        val relationshipId = (albumData?.firstOrNull() as? JsonObject)?.let { ampString(it["id"]) }

        relationshipId
            ?: ampString(attributes["collectionId"])
            ?: albumIdFromUrl(ampString(attributes["url"]))
    } else {
        ampString(item["id"])
    }
    return raw?.takeIf { it.isNotBlank() && !it.startsWith("pl.") }
}

/** Numeric album id from a catalog URL, otherwise null. */
internal fun albumIdFromUrl(url: String?): String? {
    if (url == null) return null
    val marker = url.indexOf("/album/")
    if (marker < 0) return null
    val tail = url.substring(marker + "/album/".length).substringBefore('?')
    val id = tail.substringAfterLast('/')
    return id.takeIf { value -> value.isNotEmpty() && value.all { it.isDigit() } }
}

/** First usable motion URL in an editorial block, fixed key precedence then insertion order. */
internal fun extractEditorialUrl(editorial: JsonElement?): String? {
    val block = editorial as? JsonObject ?: return null
    for (key in EDITORIAL_KEY_ORDER) {
        val motion = block[key] as? JsonObject ?: continue
        editorialField(motion)?.let { return it }
    }
    for (value in block.values) {
        val motion = value as? JsonObject ?: continue
        editorialField(motion)?.let { return it }
    }
    return null
}

/** Album-endpoint response mapping, with song overrides applied when supplied. */
internal fun mapAlbumResponse(
    root: JsonElement,
    albumId: String,
    fallbackArtist: String,
    titleOverride: String?,
    artistOverride: String?,
): CanvasArtwork? {
    val data = (root as? JsonObject)?.get("data") as? JsonArray ?: return null
    val album = data.firstOrNull() as? JsonObject ?: return null
    val attributes = album["attributes"] as? JsonObject ?: return null

    val albumName = ampString(attributes["name"]) ?: ""
    if (isBlacklisted(albumName)) return null
    val animated = extractEditorialUrl(attributes["editorialVideo"]) ?: return null

    return CanvasArtwork(
        name = titleOverride ?: albumName,
        artist = artistOverride ?: ampString(attributes["artistName"]) ?: fallbackArtist,
        albumId = albumId,
        albumName = albumName,
        animated = animated,
    )
}

internal fun artistMatches(requested: String, returned: String): Boolean =
    requested.equals(returned, ignoreCase = true) ||
        requested.contains(returned, ignoreCase = true) ||
        returned.contains(requested, ignoreCase = true)

internal fun isBlacklisted(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val lower = text.lowercase(Locale.ROOT)
    return BLACKLIST_TERMS.any { lower.contains(it) }
}

private fun editorialField(motion: JsonObject): String? {
    for (field in EDITORIAL_URL_FIELDS) {
        ampString(motion[field])?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}

private fun ampString(element: JsonElement?): String? =
    (element as? JsonPrimitive)?.contentOrNull

private fun jsonString(element: JsonElement?): String? = ampString(element)
