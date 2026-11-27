package com.revenge.music.spotify

import com.revenge.music.spotify.models.SpotifyAlbum
import com.revenge.music.spotify.models.SpotifyArtist
import com.revenge.music.spotify.models.SpotifyHomeFeed
import com.revenge.music.spotify.models.SpotifyHomeFeedItem
import com.revenge.music.spotify.models.SpotifyHomeFeedSection
import com.revenge.music.spotify.models.SpotifyImage
import com.revenge.music.spotify.models.SpotifyLibraryFolder
import com.revenge.music.spotify.models.SpotifyLibraryItem
import com.revenge.music.spotify.models.SpotifyPaging
import com.revenge.music.spotify.models.SpotifyPlaylist
import com.revenge.music.spotify.models.SpotifyPlaylistOwner
import com.revenge.music.spotify.models.SpotifyPlaylistTrack
import com.revenge.music.spotify.models.SpotifyPlaylistTracksRef
import com.revenge.music.spotify.models.SpotifyRecommendations
import com.revenge.music.spotify.models.SpotifySavedTrack
import com.revenge.music.spotify.models.SpotifySearchResult
import com.revenge.music.spotify.models.SpotifySimpleAlbum
import com.revenge.music.spotify.models.SpotifySimpleArtist
import com.revenge.music.spotify.models.SpotifyTrack
import com.revenge.music.spotify.models.SpotifyUser
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

data class PlaylistItemRef(val uri: String, val uid: String)

data class ArtistTopTracksResponse(val tracks: List<SpotifyTrack> = emptyList())

data class RelatedArtistsResponse(val artists: List<SpotifyArtist> = emptyList())

data class NewReleasesResponse(val albums: SpotifyPaging<SpotifyAlbum>? = null)

/**
 * Client for Spotify's internal web-player endpoints. All public operations are [Result]-based;
 * failures are [SpotifyException]. The HTTP transport is an internal seam so tests can drive the
 * client with canned responses.
 */
object Spotify {

  private const val GRAPHQL_URL = "https://api-partner.spotify.com/pathfinder/v2/query"
  private const val REST_BASE = "https://api.spotify.com/v1/"
  private const val CHROME_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
      "Chrome/126.0.0.0 Safari/537.36"

  @Volatile var accessToken: String? = null

  @Volatile var logger: ((level: String, message: String) -> Unit)? = null

  @Volatile var onHashExpired: ((operationName: String) -> Unit)? = null

  fun isAuthenticated(): Boolean = accessToken != null

  class SpotifyException(
    val statusCode: Int,
    message: String,
    val retryAfterSec: Long = 0,
  ) : Exception(message)

  internal data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String? = null,
  )

  internal data class HttpResponse(
    val status: Int,
    val body: String,
    val retryAfter: Long = 0,
  )

  internal var transport: suspend (HttpRequest) -> HttpResponse = { realTransport(it) }

  internal val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  val defaultZoneId: String
    get() = ZoneId.systemDefault().id

  // ------------------------------------------------------------------ public operations

  suspend fun me(): Result<SpotifyUser> =
    graphql("profileAttributes", buildJsonObject {})
      .fold(
        onSuccess = { data ->
          val profile = data.obj("me")?.obj("profile")
          if (profile == null) restMe()
          else {
            val uri = profile.str("uri")
            Result.success(
              SpotifyUser(
                id = idFromUri(uri).ifBlank { profile.str("id").orEmpty() },
                displayName = profile.str("name") ?: profile.obj("profile")?.str("name"),
                images = imagesFrom(profile.obj("avatar")?.get("sources") ?: profile["images"]),
              ),
            )
          }
        },
        onFailure = { restMe() },
      )

  private suspend fun restMe(): Result<SpotifyUser> =
    rest("GET", "me").map { body ->
      SpotifyUser(
        id = body.str("id").orEmpty(),
        displayName = body.str("display_name"),
        email = body.str("email"),
        images = imagesFrom(body["images"]),
        product = body.str("product"),
        country = body.str("country"),
      )
    }

  suspend fun myPlaylists(
    limit: Int = 50,
    offset: Int = 0,
  ): Result<SpotifyPaging<SpotifyPlaylist>> =
    library(
      limit = limit,
      offset = offset,
      flatten = true,
      folderUri = null,
      filters = listOf("Playlists"),
    ) { wrapper ->
      val data = wrapper.obj("data") ?: return@library null
      val typename = data.str("__typename")
      data.str("__typename")?.let { if (it != "Playlist") return@library null }
      if (typename == null && data.obj("ownerV2") == null) return@library null
      parsePlaylist(data)
    }

  suspend fun myLibraryNode(
    folderUri: String? = null,
    limit: Int = 50,
    offset: Int = 0,
  ): Result<SpotifyPaging<SpotifyLibraryItem>> =
    library(limit = limit, offset = offset, flatten = false, folderUri = folderUri) { wrapper ->
      val data = wrapper.obj("data") ?: return@library null
      when (data.str("__typename")) {
        "Playlist" -> SpotifyLibraryItem.Playlist(parsePlaylist(data))
        "Folder" ->
          SpotifyLibraryItem.Folder(
            SpotifyLibraryFolder(
              uri = data.str("uri").orEmpty(),
              name = data.str("name").orEmpty(),
              totalChildren = data.int("totalCount") ?: 0,
            ),
          )
        else -> null
      }
    }

  suspend fun myArtists(
    limit: Int = 50,
    offset: Int = 0,
  ): Result<SpotifyPaging<SpotifyArtist>> =
    library(
      limit = limit,
      offset = offset,
      flatten = true,
      folderUri = null,
      filters = listOf("Artists"),
    ) { wrapper ->
      val data = wrapper.obj("data") ?: return@library null
      val typename = data.str("__typename").orEmpty()
      if (!typename.contains("Artist")) return@library null
      parseArtist(data)
    }

  private suspend fun <T> library(
    limit: Int,
    offset: Int,
    flatten: Boolean,
    folderUri: String?,
    filters: List<String> = emptyList(),
    item: (JsonObject) -> T?,
  ): Result<SpotifyPaging<T>> =
    graphql(
        "libraryV3",
        buildJsonObject {
          put("limit", limit)
          put("offset", offset)
          put("flatten", flatten)
          folderUri?.let { put("folderUri", it) }
          if (filters.isNotEmpty()) {
            put("filters", buildJsonArray { filters.forEach { add(JsonPrimitive(it)) } })
          }
        },
      )
      .map { data ->
        val root =
          data.obj("me")?.obj("libraryV3")
            ?: throw SpotifyException(500, "Invalid libraryV3 response")
        val items = root.arr("items").mapNotNull { it.asObj()?.let(item) }
        val paging = root.obj("pagingInfo")
        SpotifyPaging(
          items = items,
          total = root.int("totalCount") ?: items.size,
          limit = paging?.int("limit") ?: limit,
          offset = paging?.int("offset") ?: offset,
        )
      }

  suspend fun playlist(playlistId: String): Result<SpotifyPlaylist> =
    graphql(
        "fetchPlaylist",
        buildJsonObject {
          put("uri", "spotify:playlist:$playlistId")
          put("limit", 25)
        },
      )
      .map { data ->
        val union =
          data.obj("playlistV2") ?: throw SpotifyException(500, "Invalid fetchPlaylist response")
        parsePlaylist(union)
      }

  suspend fun playlistTracks(
    playlistId: String,
    limit: Int = 100,
    offset: Int = 0,
  ): Result<SpotifyPaging<SpotifyPlaylistTrack>> =
    graphql(
        "fetchPlaylist",
        buildJsonObject {
          put("uri", "spotify:playlist:$playlistId")
          put("limit", limit)
          put("offset", offset)
        },
      )
      .map { data ->
        val content =
          data.obj("playlistV2")?.obj("content")
            ?: throw SpotifyException(500, "Invalid fetchPlaylist response")
        val items =
          content.arr("items").mapNotNull { element ->
            val outer = element.asObj() ?: return@mapNotNull null
            val inner = outer.obj("itemV2") ?: return@mapNotNull null
            val trackData = inner.obj("data")?.let { parseTrack(it) } ?: return@mapNotNull null
            SpotifyPlaylistTrack(
              uid = outer.str("uid") ?: inner.str("uid") ?: trackData.uri?.let(::idFromUri),
              track = trackData,
              isLocal = trackData.isLocal,
            )
          }
        SpotifyPaging(
          items = items,
          total = content.int("totalCount") ?: items.size,
          limit = limit,
          offset = offset,
        )
      }

  suspend fun addTracksToPlaylist(playlistId: String, trackUris: List<String>): Result<Unit> =
    withTimeout(20_000) {
      graphql(
          "addToPlaylist",
          buildJsonObject {
            put("playlistUri", "spotify:playlist:$playlistId")
            put("playlistItemUris", buildJsonArray { trackUris.forEach { add(JsonPrimitive(it)) } })
            put("newPosition", "BOTTOM_OF_PLAYLIST")
          },
        )
        .map {}
    }

  suspend fun removeTracksFromPlaylist(
    playlistId: String,
    items: List<PlaylistItemRef>,
  ): Result<Unit> =
    graphql(
        "removeFromPlaylist",
        buildJsonObject {
          put("playlistUri", "spotify:playlist:$playlistId")
          put(
            "items",
            buildJsonArray {
              items.forEach { item ->
                add(buildJsonObject { put("uid", item.uid) })
              }
            },
          )
        },
      )
      .map {}

  suspend fun moveItemsInPlaylist(
    playlistId: String,
    uids: List<String>,
    beforeUid: String?,
  ): Result<Unit> =
    graphql(
        "moveItemsInPlaylist",
        buildJsonObject {
          put("playlistUri", "spotify:playlist:$playlistId")
          put("uids", buildJsonArray { uids.forEach { add(JsonPrimitive(it)) } })
          if (beforeUid != null) {
            put("beforeUid", beforeUid)
            put("position", "BEFORE_UID")
          } else {
            put("position", "BOTTOM_OF_PLAYLIST")
          }
        },
      )
      .map {}

  suspend fun editPlaylistAttributes(
    playlistId: String,
    newName: String? = null,
    newDescription: String? = null,
  ): Result<Unit> =
    graphql(
        "editPlaylistAttributes",
        buildJsonObject {
          put("playlistUri", "spotify:playlist:$playlistId")
          newName?.let { put("name", it) }
          newDescription?.let { put("description", it) }
        },
      )
      .map {}

  suspend fun likedSongs(
    limit: Int = 50,
    offset: Int = 0,
  ): Result<SpotifyPaging<SpotifySavedTrack>> =
    graphql(
        "fetchLibraryTracks",
        buildJsonObject {
          put("limit", limit)
          put("offset", offset)
        },
      )
      .map { data ->
        val tracks =
          data.obj("me")?.obj("library")?.obj("tracks")
            ?: throw SpotifyException(500, "Invalid fetchLibraryTracks response")
        val items =
          tracks.arr("items").mapNotNull { element ->
            val track =
              element.asObj()?.obj("track")?.obj("data")?.let(::parseTrack)
                ?: return@mapNotNull null
            SpotifySavedTrack(track = track)
          }
        SpotifyPaging(
          items = items,
          total = tracks.int("totalCount") ?: items.size,
          limit = limit,
          offset = offset,
        )
      }

  suspend fun addToLibrary(uris: List<String>): Result<Unit> =
    graphql("addToLibrary", libraryUris(uris)).map {}

  suspend fun removeFromLibrary(uris: List<String>): Result<Unit> =
    graphql("removeFromLibrary", libraryUris(uris)).map {}

  private fun libraryUris(uris: List<String>) = buildJsonObject {
    put("libraryItemUris", buildJsonArray { uris.forEach { add(JsonPrimitive(it)) } })
  }

  suspend fun topTracks(
    timeRange: String = "medium_term",
    limit: Int = 50,
    offset: Int = 0,
  ): Result<SpotifyPaging<SpotifyTrack>> =
    rest(
        "GET",
        "me/top/tracks?time_range=${encode(timeRange)}&limit=$limit&offset=$offset",
        failFastOn429 = true,
      )
      .map { body -> pagingOf(body) { obj -> parseTrack(obj) } }

  suspend fun topArtists(
    timeRange: String = "medium_term",
    limit: Int = 50,
    offset: Int = 0,
  ): Result<SpotifyPaging<SpotifyArtist>> =
    rest(
        "GET",
        "me/top/artists?time_range=${encode(timeRange)}&limit=$limit&offset=$offset",
        failFastOn429 = true,
      )
      .map { body -> pagingOf(body) { obj -> parseArtist(obj) } }

  suspend fun recommendations(
    seedTrackIds: List<String> = emptyList(),
    seedArtistIds: List<String> = emptyList(),
    seedGenres: List<String> = emptyList(),
    limit: Int = 50,
  ): Result<SpotifyRecommendations> {
    val seeds = buildList {
      seedTrackIds.forEach { add(it) }
      seedArtistIds.forEach { add(it) }
      seedGenres.forEach { add(it) }
    }
      .joinToString(",")
    return rest(
        "GET",
        "recommendations?limit=$limit&seed_tracks=${encode(seedTrackIds.joinToString(","))}" +
          "&seed_artists=${encode(seedArtistIds.joinToString(","))}" +
          "&seed_genres=${encode(seedGenres.joinToString(","))}",
      )
      .map { body ->
        SpotifyRecommendations(
          tracks = body.arr("tracks").mapNotNull { parseTrack(it) },
          seeds = emptyList(),
        )
      }
  }

  suspend fun search(
    query: String,
    types: List<String> = listOf("track"),
    limit: Int = 20,
    offset: Int = 0,
  ): Result<SpotifySearchResult> =
    graphql(
        "searchDesktop",
        buildJsonObject {
          put("searchTerm", query)
          put("limit", limit)
          put("offset", offset)
          put("numberOfTopResults", 5)
        },
      )
      .map { data ->
        val root =
          data.obj("searchV2") ?: throw SpotifyException(500, "Invalid searchDesktop response")
        SpotifySearchResult(
          tracks =
            parseSearchSection(root.obj("tracksV2"), "Track") { obj -> parseTrack(obj) }
              ?: SpotifyPaging(items = emptyList(), limit = limit, offset = offset),
          albums = parseSearchSection(root.obj("albumsV2"), "Album") { obj -> parseAlbum(obj) },
          artists = parseSearchSection(root.obj("artists"), "Artist") { obj -> parseArtist(obj) },
          playlists =
            parseSearchSection(root.obj("playlists"), "Playlist") { obj -> parsePlaylist(obj) },
        )
      }

  suspend fun newReleases(limit: Int = 20, offset: Int = 0): Result<NewReleasesResponse> =
    graphql(
        "queryWhatsNewFeed",
        buildJsonObject {
          put("limit", limit)
          put("offset", offset)
        },
      )
      .map { data ->
        val root = data.arr("whatsNewFeedItems")
        val albums = root.mapNotNull { element ->
          val dataObj = element.asObj()?.obj("data") ?: return@mapNotNull null
          if (dataObj.str("__typename") != "Album") return@mapNotNull null
          parseAlbum(dataObj)
        }
        NewReleasesResponse(
          albums =
            SpotifyPaging(items = albums, total = albums.size, limit = limit, offset = offset),
        )
      }

  suspend fun home(
    sectionItemsLimit: Int = 10,
    timeZone: String = defaultZoneId,
  ): Result<SpotifyHomeFeed> =
    graphql(
        "home",
        buildJsonObject {
          put("timeZone", timeZone)
          put("sectionItemsLimit", sectionItemsLimit)
        },
      )
      .map { data ->
        val root = data.obj("home") ?: throw SpotifyException(500, "Invalid home response")
        val container = root.obj("sectionContainer")
        val sections =
          container
            ?.arr("sections")
            ?.mapNotNull { element ->
              val obj = element.asObj() ?: return@mapNotNull null
              val items = obj.arr("sectionItems").mapNotNull { it.asObj()?.let(::parseHomeItem) }
              if (items.isEmpty()) return@mapNotNull null
              SpotifyHomeFeedSection(
                sectionUri = obj.obj("sectionUri")?.str("uri") ?: obj.str("uri").orEmpty(),
                title = obj.obj("title")?.str("transformedLabel") ?: obj.str("title"),
                typename = obj.str("__typename").orEmpty(),
                totalCount = obj.int("totalCount") ?: items.size,
                items = items,
              )
            }
            .orEmpty()
        SpotifyHomeFeed(
          greeting = root.obj("greeting")?.str("transformedLabel"),
          sections = sections,
        )
      }

  suspend fun album(albumId: String): Result<SpotifyAlbum> =
    graphql(
        "getAlbum",
        buildJsonObject {
          put("uri", "spotify:album:$albumId")
          put("locale", "")
          put("offset", 0)
          put("limit", 50)
        },
      )
      .map { data ->
        val union =
          data.obj("albumUnion") ?: throw SpotifyException(500, "Invalid getAlbum response")
        val tracks =
          union
            .obj("tracksV2")
            ?.arr("items")
            ?.mapNotNull { element ->
              element.asObj()?.obj("track")?.let(::parseTrack)
            }
            .orEmpty()
        parseAlbum(union)
          .copy(
            tracks = SpotifyPaging(items = tracks, total = tracks.size, limit = 50, offset = 0),
          )
      }

  suspend fun artist(artistId: String): Result<SpotifyArtist> =
    graphql(
        "queryArtistOverview",
        buildJsonObject {
          put("uri", "spotify:artist:$artistId")
          put("locale", "")
          put("includePrerelease", true)
        },
      )
      .map { data ->
        data.obj("artistUnion")
          ?: throw SpotifyException(500, "Invalid queryArtistOverview response")
      }
      .map(::parseArtist)

  suspend fun artistTopTracks(
    artistId: String,
    market: String = "US",
  ): Result<ArtistTopTracksResponse> =
    graphql(
        "queryArtistOverview",
        buildJsonObject {
          put("uri", "spotify:artist:$artistId")
          put("locale", "")
        },
      )
      .map { data ->
        val union =
          data.obj("artistUnion")
            ?: throw SpotifyException(500, "Invalid queryArtistOverview response")
        val items =
          union
            .obj("discography")
            ?.obj("topTracks")
            ?.arr("items")
            ?.mapNotNull { it.asObj()?.obj("track")?.let(::parseTrack) }
            .orEmpty()
        ArtistTopTracksResponse(tracks = items)
      }

  suspend fun artistRelatedArtists(artistId: String): Result<List<SpotifyArtist>> =
    graphql(
        "queryArtistOverview",
        buildJsonObject {
          put("uri", "spotify:artist:$artistId")
          put("locale", "")
        },
      )
      .map { data ->
        val union =
          data.obj("artistUnion")
            ?: throw SpotifyException(500, "Invalid queryArtistOverview response")
        union
          .obj("relatedContent")
          ?.obj("relatedArtists")
          ?.arr("items")
          ?.mapNotNull { it.asObj()?.obj("data")?.let(::parseArtist) }
          .orEmpty()
      }

  suspend fun relatedArtists(artistId: String): Result<RelatedArtistsResponse> =
    rest("GET", "artists/$artistId/related-artists").map { body ->
      RelatedArtistsResponse(
        artists = body.arr("artists").mapNotNull { element -> element.asObj()?.let(::parseArtist) },
      )
    }

  suspend fun getCanvas(trackId: String): String? {
    if (accessToken == null) return null
    return graphql(
        "canvas",
        buildJsonObject {
          put("uri", "spotify:track:$trackId")
        },
      )
      .getOrNull()
      ?.obj("trackUnion")
      ?.obj("canvas")
      ?.str("url")
  }

  suspend fun getCanvasForSong(title: String, artist: String): String? {
    val track =
      search("$artist $title", limit = 1).getOrNull()?.tracks?.items?.firstOrNull() ?: return null
    return getCanvas(track.id)
  }

  // ------------------------------------------------------------------ graphql / rest

  private suspend fun graphql(operationName: String, variables: JsonObject): Result<JsonObject> =
    try {
      val token = accessToken ?: throw SpotifyException(401, "Not authenticated")
      val primary = SpotifyHashProvider.getHash(operationName)
      val hashes = buildList {
        add(primary)
        SpotifyHashProvider.getPreviousHash(operationName)?.takeIf { it != primary }?.let(::add)
      }
      var rotated = false
      for (hash in hashes) {
        var notFound = false
        for (attempt in 0 until 3) {
          val request =
            HttpRequest(
              method = "POST",
              url = GRAPHQL_URL,
              headers = graphqlHeaders(token),
              body = buildJsonObject {
                  put("variables", variables)
                  put("operationName", operationName)
                  put(
                    "extensions",
                    buildJsonObject {
                      put(
                        "persistedQuery",
                        buildJsonObject {
                          put("version", 1)
                          put("sha256Hash", hash)
                        },
                      )
                    },
                  )
                }
                  .toString(),
            )
          val response = transport(request)
          val parsed = runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull()
          val message = parsed?.arr("errors")?.firstOrNull()?.asObj()?.str("message")
          if (response.status == 412 || message?.contains("PersistedQueryNotFound", true) == true) {
            notFound = true
            break
          }
          when {
            response.status == 401 -> throw SpotifyException(401, "Not authenticated")
            response.status == 429 -> {
              val wait = if (response.retryAfter > 0) response.retryAfter else 2L * (attempt + 1)
              if (attempt == 2) throw SpotifyException(429, "Rate limited", retryAfterSec = wait)
              delay(wait * 1000)
            }
            response.status !in 200..299 ->
              throw SpotifyException(
                response.status,
                "GraphQL error ${response.status}: ${response.body}",
              )
            else -> {
              val errors = parsed?.arr("errors")?.firstOrNull()?.asObj()
              if (errors != null) {
                val errorMessage = errors.str("message").orEmpty()
                if (errorMessage.contains("PersistedQueryNotFound", true)) {
                  notFound = true
                  break
                }
                throw SpotifyException(400, "GraphQL: $errorMessage")
              }
              val data =
                parsed?.obj("data")
                  ?: throw SpotifyException(500, "Invalid $operationName response")
              return Result.success(data)
            }
          }
        }
        if (notFound) rotated = true else rotated = false
      }
      if (rotated || hashes.isNotEmpty()) {
        onHashExpired?.invoke(operationName)
        throw SpotifyException(412, "$operationName hash may have rotated")
      }
      throw SpotifyException(412, "$operationName hash may have rotated")
    } catch (e: CancellationException) {
      throw e
    } catch (e: SpotifyException) {
      Result.failure(e)
    } catch (e: Throwable) {
      Result.failure(e)
    }

  private suspend fun rest(
    method: String,
    path: String,
    failFastOn429: Boolean = false,
  ): Result<JsonObject> =
    try {
      val token = accessToken ?: throw SpotifyException(401, "Not authenticated")
      val url = REST_BASE + path
      val attempts = if (failFastOn429) 1 else 3
      for (attempt in 0 until attempts) {
        val response =
          transport(HttpRequest(method = method, url = url, headers = restHeaders(token)))
        when {
          response.status == 401 -> throw SpotifyException(401, "Not authenticated")
          response.status == 429 -> {
            val retryAfter = response.retryAfter
            if (failFastOn429 && retryAfter > 3) {
              throw SpotifyException(429, "Rate limited", retryAfterSec = retryAfter)
            }
            val wait = if (retryAfter > 0) retryAfter else 2L * (attempt + 1)
            if (attempt == attempts - 1) {
              throw SpotifyException(429, "Rate limited", retryAfterSec = wait)
            }
            delay(wait * 1000)
          }
          response.status !in 200..299 ->
            throw SpotifyException(response.status, "HTTP ${response.status}: ${response.body}")
          else -> return Result.success(json.parseToJsonElement(response.body).jsonObject)
        }
      }
      throw SpotifyException(429, "Rate limited")
    } catch (e: CancellationException) {
      throw e
    } catch (e: SpotifyException) {
      Result.failure(e)
    } catch (e: Throwable) {
      Result.failure(e)
    }

  private fun graphqlHeaders(token: String) =
    mapOf(
      "Authorization" to "Bearer $token",
      "Content-Type" to "application/json",
      "Accept" to "application/json",
      "app-platform" to "WebPlayer",
      "Origin" to "https://open.spotify.com",
      "Referer" to "https://open.spotify.com/",
      "User-Agent" to CHROME_UA,
    )

  private fun restHeaders(token: String) =
    mapOf(
      "Authorization" to "Bearer $token",
      "Accept" to "application/json",
      "app-platform" to "WebPlayer",
      "Origin" to "https://open.spotify.com",
      "Referer" to "https://open.spotify.com/",
      "User-Agent" to CHROME_UA,
    )

  private suspend fun realTransport(request: HttpRequest): HttpResponse =
    withContext(Dispatchers.IO) {
      val connection =
        (URI(request.url).toURL().openConnection() as HttpURLConnection).apply {
          requestMethod = request.method
          connectTimeout = 15_000
          readTimeout = 30_000
          useCaches = false
          request.headers.forEach { (k, v) -> setRequestProperty(k, v) }
          if (request.body != null) {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
          }
        }
      try {
        if (request.body != null) {
          connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        HttpResponse(
          status = status,
          body = body,
          retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull() ?: 0,
        )
      } finally {
        connection.disconnect()
      }
    }

  // ------------------------------------------------------------------ parsers

  internal fun parseTrack(element: JsonElement?): SpotifyTrack? {
    val outer = element?.asObj() ?: return null
    val obj = outer.obj("track") ?: outer
    val uri = obj.str("uri") ?: outer.str("uri")
    val name = obj.str("name") ?: obj.obj("profile")?.str("name") ?: ""
    if (name.isBlank() && uri == null) return null
    return SpotifyTrack(
      id = idFromUri(uri).ifBlank { obj.str("id").orEmpty() },
      name = name,
      artists = parseSimpleArtists(obj["artists"]),
      album = obj.obj("album")?.let(::parseSimpleAlbum),
      durationMs = parseDurationMs(obj),
      explicit = obj.bool("explicit") ?: false,
      isLocal = obj.bool("isLocal") ?: obj.bool("is_local") ?: false,
      previewUrl = obj.str("previewUrl") ?: obj.str("preview_url"),
      trackNumber = obj.int("trackNumber") ?: obj.int("track_number"),
      uri = uri,
      popularity = obj.int("popularity"),
    )
  }

  internal fun parsePlaylist(obj: JsonObject): SpotifyPlaylist {
    val uri = obj.str("uri")
    val owner = obj.obj("ownerV2")?.obj("data") ?: obj.obj("owner")
    val content = obj.obj("content") ?: obj.obj("contents")
    return SpotifyPlaylist(
      id = idFromUri(uri).ifBlank { obj.str("id").orEmpty() },
      name = obj.str("name").orEmpty(),
      description = obj.str("description"),
      images =
        imagesFromList(
            obj
              .obj("images")
              ?.arr("items")
              ?.flatMap { it.asObj()?.arr("sources").orEmpty() }
              .orEmpty(),
          )
          .ifEmpty { imagesFrom(obj["images"]) },
      owner =
        owner?.let {
          val ownerUri = it.str("uri")
          SpotifyPlaylistOwner(
            id = idFromUri(ownerUri).ifBlank { it.str("id").orEmpty() },
            displayName = it.str("name") ?: it.str("displayName") ?: it.str("display_name"),
            uri = ownerUri,
          )
        },
      tracks =
        SpotifyPlaylistTracksRef(
          total =
            content?.int("totalCount")
              ?: obj.int("totalCount")
              ?: obj.obj("tracksV2")?.int("totalCount")
              ?: obj.int("trackCount")
              ?: obj.int("numTracks"),
        ),
      uri = uri,
      collaborative = (obj.obj("members")?.arr("items")?.size ?: 0) > 1,
      snapshotId = obj.str("snapshotId"),
    )
  }

  internal fun parseArtist(obj: JsonObject): SpotifyArtist {
    val uri = obj.str("uri")
    val profile = obj.obj("profile")
    return SpotifyArtist(
      id = idFromUri(uri).ifBlank { obj.str("id").orEmpty() },
      name = profile?.str("name") ?: obj.str("name").orEmpty(),
      images =
        imagesFrom(
          obj.obj("visuals")?.obj("avatarImage")?.get("sources") ?: obj["images"],
        ),
      genres = obj.arr("genres").mapNotNull { it.asString() },
      popularity = obj.int("popularity"),
      uri = uri,
    )
  }

  internal fun parseAlbum(obj: JsonObject): SpotifyAlbum {
    val uri = obj.str("uri")
    val artists =
      obj
        .obj("artists")
        ?.arr("items")
        ?.mapNotNull { element ->
          element.asObj()?.obj("profile")?.str("name")?.let { SpotifySimpleArtist(name = it) }
            ?: element.asObj()?.str("name")?.let { SpotifySimpleArtist(name = it) }
        }
        .orEmpty()
    return SpotifyAlbum(
      id = idFromUri(uri).ifBlank { obj.str("id").orEmpty() },
      name = obj.str("name").orEmpty(),
      albumType = obj.str("type") ?: obj.str("albumType"),
      artists = artists,
      images = imagesFrom(obj.obj("coverArt")?.get("sources") ?: obj["images"]),
      releaseDate = obj.obj("date")?.str("isoString") ?: obj.str("releaseDate"),
      totalTracks = obj.obj("tracksV2")?.int("totalCount") ?: obj.int("totalTracks") ?: 0,
      uri = uri,
      popularity = obj.int("popularity"),
      genres = obj.arr("genres").mapNotNull { it.asString() },
    )
  }

  private fun parseSimpleArtists(element: JsonElement?): List<SpotifySimpleArtist> {
    val array = element as? JsonArray ?: return emptyList()
    return array.mapNotNull { item ->
      val obj = item.asObj() ?: return@mapNotNull null
      val profile = obj.obj("profile")
      val name = profile?.str("name") ?: obj.str("name") ?: return@mapNotNull null
      val uri = obj.str("uri")
      SpotifySimpleArtist(id = idFromUri(uri).ifBlank { obj.str("id") }, name = name, uri = uri)
    }
  }

  private fun parseSimpleAlbum(obj: JsonObject): SpotifySimpleAlbum {
    val uri = obj.str("uri")
    return SpotifySimpleAlbum(
      id = idFromUri(uri).ifBlank { obj.str("id").orEmpty() },
      name = obj.str("name").orEmpty(),
      images = imagesFrom(obj.obj("coverArt")?.get("sources") ?: obj["images"]),
      releaseDate = obj.obj("date")?.str("isoString") ?: obj.str("releaseDate"),
      albumType = obj.str("type") ?: obj.str("albumType"),
      artists = parseSimpleArtists(obj["artists"]),
      uri = uri,
    )
  }

  private fun parseDurationMs(obj: JsonObject): Int {
    obj
      .obj("trackDuration")
      ?.long("totalMilliseconds")
      ?.takeIf { it > 0 }
      ?.let {
        return it.toInt()
      }
    obj
      .obj("duration")
      ?.long("totalMilliseconds")
      ?.takeIf { it > 0 }
      ?.let {
        return it.toInt()
      }
    (obj.long("durationMs") ?: obj.long("duration_ms"))
      ?.takeIf { it > 0 }
      ?.let {
        return it.toInt()
      }
    val seconds = (obj["duration"] as? JsonPrimitive)?.content?.toLongOrNull()
    if (seconds != null && seconds > 0) return (seconds * 1000).toInt()
    val nested =
      obj.obj("track")?.let { (it["duration"] as? JsonPrimitive)?.content?.toLongOrNull() }
    if (nested != null && nested > 0) return (nested * 1000).toInt()
    return 0
  }

  private fun parseHomeItem(obj: JsonObject): SpotifyHomeFeedItem? {
    val data = obj.obj("content")?.obj("data") ?: obj.obj("data") ?: obj
    val typename = data.str("__typename")
    val uri = data.str("uri") ?: obj.str("uri") ?: return null
    return when {
      typename?.contains("Playlist") == true ->
        SpotifyHomeFeedItem.Playlist(
          uri = uri,
          id = idFromUri(uri),
          name = data.str("name").orEmpty(),
          description = data.str("description"),
          totalCount = data.int("totalCount") ?: 0,
          imageUrl =
            data
              .obj("images")
              ?.arr("items")
              ?.firstOrNull()
              ?.asObj()
              ?.arr("sources")
              ?.firstOrNull()
              ?.asObj()
              ?.str("url") ?: imagesFrom(data["images"]).firstOrNull()?.url,
          ownerName = data.obj("ownerV2")?.obj("data")?.str("name"),
        )
      typename?.contains("Album") == true ->
        SpotifyHomeFeedItem.Album(
          uri = uri,
          id = idFromUri(uri),
          name = data.str("name").orEmpty(),
          imageUrl = imagesFrom(data.obj("coverArt")?.get("sources")).firstOrNull()?.url,
        )
      typename?.contains("Artist") == true ->
        SpotifyHomeFeedItem.Artist(
          uri = uri,
          id = idFromUri(uri),
          name = data.obj("profile")?.str("name") ?: data.str("name").orEmpty(),
          imageUrl =
            imagesFrom(data.obj("visuals")?.obj("avatarImage")?.get("sources")).firstOrNull()?.url,
        )
      else -> null
    }
  }

  private fun <T> parseSearchSection(
    section: JsonObject?,
    typename: String,
    parser: (JsonObject) -> T?,
  ): SpotifyPaging<T>? {
    if (section == null) return null
    val items =
      section.arr("items").mapNotNull { element ->
        val wrapper = element.asObj() ?: return@mapNotNull null
        val data = wrapper.obj("data") ?: return@mapNotNull null
        if (data.str("__typename") != typename) return@mapNotNull null
        runCatching { parser(data) }.getOrNull()
      }
    if (items.isEmpty()) return null
    return SpotifyPaging(
      items = items,
      total = section.int("totalCount") ?: items.size,
      limit = section.int("limit") ?: items.size,
      offset = 0,
    )
  }

  private fun <T> pagingOf(body: JsonObject, parser: (JsonObject) -> T?): SpotifyPaging<T> =
    SpotifyPaging(
      items =
        body.arr("items").mapNotNull { element ->
          element.asObj()?.let(parser)
        },
      total = body.int("total") ?: 0,
      limit = body.int("limit") ?: 20,
      offset = body.int("offset") ?: 0,
      next = body.str("next"),
      previous = body.str("previous"),
      href = body.str("href"),
    )

  private fun imagesFromList(elements: List<JsonElement>): List<SpotifyImage> =
    imagesFrom(JsonArray(elements))

  private fun imagesFrom(element: JsonElement?): List<SpotifyImage> {
    val array = element as? JsonArray ?: return emptyList()
    return array.mapNotNull { item ->
      val obj = item.asObj() ?: return@mapNotNull null
      val url = obj.str("url") ?: return@mapNotNull null
      SpotifyImage(url = url, height = obj.int("height"), width = obj.int("width"))
    }
  }

  private fun idFromUri(uri: String?): String =
    uri?.substringAfterLast(':')?.takeIf { it.isNotBlank() } ?: ""

  private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

// ------------------------------------------------------------------ JSON helpers

internal fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArr(): JsonArray? = this as? JsonArray

internal fun JsonElement?.asString(): String? =
  (this as? JsonPrimitive)?.let { runCatching { it.content }.getOrNull() }

internal fun JsonObject.obj(key: String): JsonObject? = (this[key] as? JsonObject)

internal fun JsonObject.arr(key: String): List<JsonElement> =
  (this[key] as? JsonArray)?.toList().orEmpty()

internal fun JsonObject.str(key: String): String? =
  (this[key] as? JsonPrimitive)?.let { runCatching { it.content }.getOrNull() }

internal fun JsonObject.int(key: String): Int? = str(key)?.toIntOrNull()

internal fun JsonObject.long(key: String): Long? = str(key)?.toLongOrNull()

internal fun JsonObject.bool(key: String): Boolean? = str(key)?.toBooleanStrictOrNull()
