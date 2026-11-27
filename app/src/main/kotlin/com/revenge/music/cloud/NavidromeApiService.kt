package com.revenge.music.cloud

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ThreadLocalRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SubsonicApiException(val code: Int, val serverMessage: String) :
  Exception("API Error $code: $serverMessage")

/**
 * Subsonic-compatible REST transport. Every call is performed on [Dispatchers.IO] and wrapped in a
 * [Result]; the only exception that escapes is coroutine cancellation.
 */
@Singleton
class NavidromeApiService @Inject constructor() {

  internal val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  /** Replaced in tests so the salt used for token authentication is deterministic. */
  internal var saltFactory: () -> String = {
    val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    buildString(6) {
      repeat(6) { append(alphabet[ThreadLocalRandom.current().nextInt(alphabet.length)]) }
    }
  }

  suspend fun ping(credentials: NavidromeCredentials): Result<NavidromeCredentials> = wrap {
    val (_, effective) = request(credentials, "ping.view")
    credentials.copy(authMethod = effective)
  }

  suspend fun getPlaylists(credentials: NavidromeCredentials): Result<List<NavidromePlaylist>> =
    wrap {
      val root = request(credentials, "getPlaylists.view").first
      val array =
        root["subsonic-response"]?.jsonObject?.get("playlists")?.jsonObject?.get("playlist")
      array.asArray().mapNotNull { parsePlaylist(it) }
    }

  suspend fun getPlaylistSongs(
    credentials: NavidromeCredentials,
    playlistId: String,
  ): Result<List<NavidromeSong>> = wrap {
    val root = request(credentials, "getPlaylist.view", mapOf("id" to playlistId)).first
    val array = root["subsonic-response"]?.jsonObject?.get("playlist")?.jsonObject?.get("entry")
    array.asArray().mapNotNull { parseSong(it) }
  }

  suspend fun getRandomSongs(
    credentials: NavidromeCredentials,
    size: Int = 50,
  ): Result<List<NavidromeSong>> = wrap {
    val root = request(credentials, "getRandomSongs.view", mapOf("size" to size.toString())).first
    val array = root["subsonic-response"]?.jsonObject?.get("randomSongs")?.jsonObject?.get("song")
    array.asArray().mapNotNull { parseSong(it) }
  }

  fun getStreamUrl(credentials: NavidromeCredentials, songId: String): String =
    buildUrl(
      credentials,
      "stream.view",
      mapOf("id" to songId, "format" to "raw"),
    )

  fun getCoverArtUrl(credentials: NavidromeCredentials, coverArtId: String): String =
    buildUrl(credentials, "getCoverArt.view", mapOf("id" to coverArtId, "size" to "600"))

  // ------------------------------------------------------------------ transport

  private suspend fun request(
    credentials: NavidromeCredentials,
    method: String,
    extra: Map<String, String> = emptyMap(),
  ): Pair<JsonObject, NavidromeAuthMethod> =
    withContext(Dispatchers.IO) {
      var effective = credentials.authMethod
      try {
        execute(credentials, method, extra) to effective
      } catch (e: SubsonicApiException) {
        if (e.code == 41 && effective == NavidromeAuthMethod.TOKEN) {
          effective = NavidromeAuthMethod.PASSWORD
          execute(credentials.copy(authMethod = NavidromeAuthMethod.PASSWORD), method, extra) to
            effective
        } else {
          throw e
        }
      }
    }

  private fun execute(
    credentials: NavidromeCredentials,
    method: String,
    extra: Map<String, String>,
  ): JsonObject {
    val connection = open(buildUrl(credentials, method, extra))
    try {
      val code = connection.responseCode
      if (code !in 200..299) {
        val message = runCatching { connection.responseMessage }.getOrNull().orEmpty()
        throw Exception("HTTP $code: $message")
      }
      val text = connection.inputStream.bufferedReader().use { it.readText() }
      val body = json.parseToJsonElement(text).jsonObject
      val response =
        body["subsonic-response"]?.jsonObject
          ?: throw IllegalStateException("Invalid Subsonic response")
      if (response["status"]?.jsonPrimitive?.content != "ok") {
        val error = response["error"]?.jsonObject
        throw SubsonicApiException(
          error?.get("code")?.jsonPrimitive?.content?.toIntOrNull() ?: -1,
          error?.get("message")?.jsonPrimitive?.content ?: "Subsonic API error",
        )
      }
      return body
    } finally {
      connection.disconnect()
    }
  }

  private fun open(url: String): HttpURLConnection =
    (URI(url).toURL().openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = 15_000
      readTimeout = 30_000
      useCaches = false
      setRequestProperty("Accept", "application/json")
    }

  internal fun buildUrl(
    credentials: NavidromeCredentials,
    method: String,
    extra: Map<String, String>,
  ): String {
    val base = credentials.serverUrl.trim().trimEnd('/') + "/rest/" + method
    val params =
      linkedMapOf(
        "u" to credentials.username,
        "v" to "1.16.1",
        "c" to "Revenge",
        "f" to "json",
      )
    when (credentials.authMethod) {
      NavidromeAuthMethod.TOKEN -> {
        val salt = saltFactory()
        params["t"] = md5Hex(credentials.password + salt)
        params["s"] = salt
      }
      NavidromeAuthMethod.PASSWORD -> {
        params["p"] = "enc:" + hex(credentials.password.toByteArray(Charsets.UTF_8))
      }
    }
    params.putAll(extra)
    return base +
      "?" +
      params.entries.joinToString("&") { (k, v) ->
        URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
      }
  }

  // ------------------------------------------------------------------ mapping

  private fun parsePlaylist(element: JsonElement): NavidromePlaylist? {
    val obj = element as? JsonObject ?: return null
    val id = obj.string("id") ?: return null
    return NavidromePlaylist(
      id = id,
      name = obj.string("name") ?: "Unknown Playlist",
      songCount = obj.int("songCount") ?: 0,
      duration = obj.long("duration") ?: 0L,
      coverArt = obj.string("coverArt")?.takeIf { it.isNotBlank() },
    )
  }

  private fun parseSong(element: JsonElement): NavidromeSong? {
    val obj = element as? JsonObject ?: return null
    val id = obj.string("id") ?: return null
    val bitRate = obj.int("bitRate")?.takeIf { it > 0 }
    val year = obj.int("year")?.takeIf { it > 0 }
    val track = obj.int("track")?.takeIf { it > 0 }
    return NavidromeSong(
      id = id,
      title = obj.string("title") ?: "Unknown Title",
      artist = obj.string("artist") ?: "Unknown Artist",
      album = obj.string("album") ?: "Unknown Album",
      duration = obj.long("duration") ?: 0L,
      bitRate = bitRate,
      mimeType = obj.string("contentType")?.takeIf { it.isNotBlank() },
      suffix = obj.string("suffix")?.takeIf { it.isNotBlank() },
      coverArt = obj.string("coverArt")?.takeIf { it.isNotBlank() },
      year = year,
      track = track,
    )
  }

  // ------------------------------------------------------------------ helpers

  private suspend inline fun <T> wrap(block: suspend () -> T): Result<T> =
    try {
      Result.success(block())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      Result.failure(e)
    }

  internal companion object {
    fun md5Hex(value: String): String =
      hex(MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)))

    fun hex(bytes: ByteArray): String =
      buildString(bytes.size * 2) {
        for (b in bytes) {
          val v = b.toInt() and 0xff
          append("0123456789abcdef"[v ushr 4])
          append("0123456789abcdef"[v and 0x0f])
        }
      }
  }
}

private fun JsonElement?.asArray(): List<JsonElement> =
  when (this) {
    is JsonArray -> this
    null -> emptyList()
    else -> listOf(this)
  }

private fun JsonObject.string(key: String): String? =
  (this[key] as? JsonElement)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()

private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()
