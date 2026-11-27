package com.revenge.music.cloud

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Jellyfin REST transport; all network work is off the caller thread and Result-wrapped. */
@Singleton
class JellyfinApiService @Inject constructor() {

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  suspend fun authenticate(
    serverUrl: String,
    username: String,
    password: String,
  ): Result<JellyfinCredentials> = wrap {
    val base = normalize(serverUrl)
    val body = buildJsonObject {
      put("Username", username)
      put("Pw", password)
    }
    val response = post(base + "/Users/AuthenticateByName", authHeader(null), body.toString())
    val user = response["User"]?.jsonObject
    val token = response.string("AccessToken")
    val userId = user?.string("Id")
    if (token.isNullOrBlank() || userId.isNullOrBlank()) {
      throw Exception("Invalid authentication response from Jellyfin")
    }
    JellyfinCredentials(
      serverUrl = serverUrl.trim().trimEnd('/'),
      username = username,
      accessToken = token,
      userId = userId,
      serverId = response.string("ServerId"),
    )
  }

  suspend fun ping(credentials: JellyfinCredentials): Result<String> = wrap {
    val response = get(credentials, "/System/Info")
    val name = response.string("ServerName")?.takeIf { it.isNotBlank() } ?: "Jellyfin Server"
    val version = response.string("Version").orEmpty()
    "$name (v$version)"
  }

  suspend fun getPlaylists(credentials: JellyfinCredentials): Result<List<JellyfinPlaylist>> =
    wrap {
      val userId = credentials.userId ?: throw Exception("Missing User ID")
      val response =
        get(
          credentials,
          "/Users/${encode(userId)}/Items?includeItemTypes=Playlist&recursive=true",
        )
      response.items().mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj.string("Id") ?: return@mapNotNull null
        JellyfinPlaylist(
          id = id,
          name = obj.string("Name") ?: "Unknown Playlist",
          songCount = obj.int("ChildCount") ?: 0,
          hasCover = obj.hasPrimaryImage(),
        )
      }
    }

  suspend fun getPlaylistSongs(
    credentials: JellyfinCredentials,
    playlistId: String,
  ): Result<List<JellyfinSong>> = wrap {
    val userId = credentials.userId ?: throw Exception("Missing User ID")
    val response =
      get(
        credentials,
        "/Playlists/${encode(playlistId)}/Items?userId=${encode(userId)}" +
          "&fields=ItemCounts,PrimaryImageAspectRatio,Path,MediaSources",
      )
    response.items().mapNotNull { parseSong(it) }
  }

  suspend fun getSongs(
    credentials: JellyfinCredentials,
    limit: Int = 100,
  ): Result<List<JellyfinSong>> = wrap {
    val userId = credentials.userId ?: throw Exception("Missing User ID")
    val response =
      get(
        credentials,
        "/Users/${encode(userId)}/Items?includeItemTypes=Audio&recursive=true" +
          "&sortBy=DateCreated&sortOrder=Descending&limit=$limit" +
          "&fields=ItemCounts,PrimaryImageAspectRatio,Path,MediaSources",
      )
    response.items().mapNotNull { parseSong(it) }
  }

  fun getStreamUrl(credentials: JellyfinCredentials, songId: String): String? {
    val token = credentials.accessToken?.takeIf { it.isNotBlank() } ?: return null
    return normalize(credentials.serverUrl) +
      "/Audio/${encode(songId)}/stream?static=true&api_key=${encode(token)}"
  }

  fun getItemImageUrl(credentials: JellyfinCredentials, itemId: String): String? {
    val token = credentials.accessToken?.takeIf { it.isNotBlank() } ?: return null
    return normalize(credentials.serverUrl) +
      "/Items/${encode(itemId)}/Images/Primary?quality=90&api_key=${encode(token)}"
  }

  internal fun authHeader(token: String?): String {
    val base =
      "MediaBrowser Client=\"Revenge\", Device=\"Android\", DeviceId=\"Revenge-Android\", " +
        "Version=\"1.0\""
    return if (token.isNullOrBlank()) base else "$base, Token=\"$token\""
  }

  // ------------------------------------------------------------------ transport

  private suspend fun get(credentials: JellyfinCredentials, path: String): JsonObject =
    withContext(Dispatchers.IO) {
      request(credentials.serverUrl, credentials.accessToken, path, "GET", null)
    }

  private suspend fun post(
    baseUrl: String,
    authorization: String,
    body: String,
  ): JsonObject =
    withContext(Dispatchers.IO) {
      request(baseUrl, null, "/Users/AuthenticateByName", "POST", body, authorization)
    }

  private fun request(
    serverUrl: String,
    token: String?,
    path: String,
    method: String,
    body: String?,
    authorizationOverride: String? = null,
  ): JsonObject {
    val url = normalize(serverUrl) + path
    val connection =
      (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 15_000
        readTimeout = 30_000
        useCaches = false
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Authorization", authorizationOverride ?: authHeader(token))
        if (body != null) {
          doOutput = true
          setRequestProperty("Content-Type", "application/json")
        }
      }
    try {
      if (body != null) {
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
      }
      val code = connection.responseCode
      if (code !in 200..299) {
        val message = runCatching { connection.responseMessage }.getOrNull().orEmpty()
        throw Exception("HTTP $code: $message")
      }
      val text = connection.inputStream.bufferedReader().use { it.readText() }
      return json.parseToJsonElement(text).jsonObject
    } finally {
      connection.disconnect()
    }
  }

  private fun parseSong(element: JsonElement): JellyfinSong? {
    val obj = element as? JsonObject ?: return null
    val id = obj.string("Id") ?: return null
    val ticks = obj.long("RunTimeTicks") ?: 0L
    val source = (obj["MediaSources"] as? JsonArray)?.firstOrNull()?.jsonObject
    val artists = (obj["Artists"] as? JsonArray)?.mapNotNull { it.stringOrNull() }.orEmpty()
    val artist =
      artists.firstOrNull()
        ?: obj.string("AlbumArtist")?.takeIf { it.isNotBlank() }
        ?: "Unknown Artist"
    return JellyfinSong(
      id = id,
      title = obj.string("Name") ?: "Unknown Song",
      artist = artist,
      album = obj.string("Album") ?: "Unknown Album",
      durationMs = if (ticks > 0) ticks / 10_000 else 0L,
      bitRate = source?.long("Bitrate")?.takeIf { it > 0 }?.toInt(),
      container = source?.string("Container")?.takeIf { it.isNotBlank() },
      hasCover = obj.hasPrimaryImage(),
      year = obj.int("ProductionYear")?.takeIf { it > 0 },
    )
  }

  private fun JsonObject.items(): List<JsonElement> =
    (this["Items"] as? JsonArray)?.toList().orEmpty()

  private fun JsonObject.hasPrimaryImage(): Boolean =
    (this["ImageTags"] as? JsonObject)?.get("Primary") != null

  private suspend inline fun <T> wrap(block: suspend () -> T): Result<T> =
    try {
      Result.success(block())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      Result.failure(e)
    }

  private companion object {
    fun normalize(serverUrl: String): String = serverUrl.trim().trimEnd('/')

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
  }
}

private fun JsonObject.string(key: String): String? =
  (this[key] as? JsonElement)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()

private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()

private fun JsonElement.stringOrNull(): String? =
  (this as? JsonPrimitive)?.let { runCatching { it.content }.getOrNull() }
