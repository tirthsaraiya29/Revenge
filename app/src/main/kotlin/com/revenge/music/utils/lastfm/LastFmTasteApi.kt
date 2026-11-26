package com.revenge.music.utils.lastfm

import com.revenge.music.BuildConfig
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
import kotlinx.serialization.json.jsonObject

data class SimilarTrack(val name: String, val artist: String, val match: Double? = null)

data class SimilarArtist(val name: String, val match: Double? = null)

data class Tag(val name: String, val count: Int = 0)

interface LastFmTasteApi {
  suspend fun getSimilarTracks(artist: String, track: String): Result<List<SimilarTrack>>

  suspend fun getSimilarArtists(artist: String): Result<List<SimilarArtist>>

  suspend fun getTopTags(artist: String): Result<List<Tag>>

  suspend fun getUserTopArtists(user: String, limit: Int = 30): Result<List<String>>

  suspend fun getUserTopTracks(user: String, limit: Int = 50): Result<List<SimilarTrack>>

  suspend fun getUserTopTags(user: String, limit: Int = 15): Result<List<Tag>>
}

@Singleton
class RealLastFmTasteApi @Inject constructor() : LastFmTasteApi {

  private companion object {
    const val BASE = "https://ws.audioscrobbler.com/2.0/"
  }

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  /** Test seam. */
  internal var transport: suspend (Map<String, String>) -> Result<String> = { params -> get(params) }

  override suspend fun getSimilarTracks(
    artist: String,
    track: String,
  ): Result<List<SimilarTrack>> {
    if (artist.isBlank() || track.isBlank()) return Result.success(emptyList())
    return query(
      mapOf(
        "method" to "track.getsimilar",
        "artist" to artist,
        "track" to track,
        "limit" to "30",
      ),
    ).map { body ->
      val root = root(body) ?: return@map emptyList()
      entries(root["similartracks"]?.jsonObject?.get("track")).mapNotNull { item ->
        val obj = item.asObject() ?: return@mapNotNull null
        val name = obj.string("name") ?: return@mapNotNull null
        SimilarTrack(
          name = name,
          artist = obj.string("artist") ?: obj["artist"]?.jsonObject?.string("name").orEmpty(),
          match = obj.double("match"),
        )
      }
    }
  }

  override suspend fun getSimilarArtists(artist: String): Result<List<SimilarArtist>> {
    if (artist.isBlank()) return Result.success(emptyList())
    return query(
      mapOf("method" to "artist.getsimilar", "artist" to artist, "limit" to "20"),
    ).map { body ->
      val root = root(body) ?: return@map emptyList()
      entries(root["similarartists"]?.jsonObject?.get("artist")).mapNotNull { item ->
        val obj = item.asObject() ?: return@mapNotNull null
        val name = obj.string("name") ?: return@mapNotNull null
        SimilarArtist(name = name, match = obj.double("match"))
      }
    }
  }

  override suspend fun getTopTags(artist: String): Result<List<Tag>> {
    if (artist.isBlank()) return Result.success(emptyList())
    return query(
      mapOf("method" to "artist.gettoptags", "artist" to artist),
    ).map { body ->
      val root = root(body) ?: return@map emptyList()
      parseTags(root["toptags"]?.jsonObject?.get("tag"))
    }
  }

  override suspend fun getUserTopArtists(user: String, limit: Int): Result<List<String>> {
    if (user.isBlank()) return Result.success(emptyList())
    return query(
      mapOf(
        "method" to "user.gettopartists",
        "user" to user,
        "limit" to limit.toString(),
        "period" to "overall",
      ),
    ).map { body ->
      val root = root(body) ?: return@map emptyList()
      entries(root["topartists"]?.jsonObject?.get("artist"))
        .mapNotNull { it.asObject()?.string("name") }
    }
  }

  override suspend fun getUserTopTracks(user: String, limit: Int): Result<List<SimilarTrack>> {
    if (user.isBlank()) return Result.success(emptyList())
    return query(
      mapOf(
        "method" to "user.gettoptracks",
        "user" to user,
        "limit" to limit.toString(),
        "period" to "overall",
      ),
    ).map { body ->
      val root = root(body) ?: return@map emptyList()
      entries(root["toptracks"]?.jsonObject?.get("track")).mapNotNull { item ->
        val obj = item.asObject() ?: return@mapNotNull null
        val name = obj.string("name") ?: return@mapNotNull null
        SimilarTrack(
          name = name,
          artist = obj.string("artist") ?: obj["artist"]?.jsonObject?.string("name").orEmpty(),
        )
      }
    }
  }

  override suspend fun getUserTopTags(user: String, limit: Int): Result<List<Tag>> {
    if (user.isBlank()) return Result.success(emptyList())
    return query(
      mapOf(
        "method" to "user.gettoptags",
        "user" to user,
        "limit" to limit.toString(),
      ),
    ).map { body ->
      val root = root(body) ?: return@map emptyList()
      parseTags(root["toptags"]?.jsonObject?.get("tag"))
    }
  }

  private fun parseTags(element: JsonElement?): List<Tag> =
    entries(element).mapNotNull { item ->
      val obj = item.asObject() ?: return@mapNotNull null
      val name = obj.string("name") ?: return@mapNotNull null
      Tag(name = name, count = obj.int("count") ?: 0)
    }

  private fun root(body: String): JsonObject? {
    val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    if (obj.containsKey("error")) return null
    return obj
  }

  private fun entries(element: JsonElement?): List<JsonElement> =
    when (element) {
      null -> emptyList()
      is JsonArray -> element.toList()
      is JsonObject -> listOf(element)
      else -> emptyList()
    }

  private fun apiKey(): String =
    LastFM.getApiKey().ifBlank { BuildConfig.LASTFM_API_KEY }

  private suspend fun query(extras: Map<String, String>): Result<String> {
    val key = apiKey()
    if (key.isBlank()) return Result.success("{}")
    val params =
      extras + mapOf("api_key" to key, "format" to "json")
    return transport(params)
  }

  private suspend fun get(params: Map<String, String>): Result<String> =
    try {
      withContext(Dispatchers.IO) {
        val url =
          BASE +
            "?" +
            params.entries.joinToString("&") { (key, value) ->
              URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
            }
        val connection =
          (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
          }
        try {
          val code = connection.responseCode
          val stream = if (code in 200..299) connection.inputStream else connection.errorStream
          val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
          if (code !in 200..299) Result.failure(Exception("HTTP $code")) else Result.success(body)
        } finally {
          connection.disconnect()
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      Result.failure(e)
    }
}

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonObject.string(key: String): String? =
  (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.int(key: String): Int? =
  (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

private fun JsonObject.double(key: String): Double? =
  (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
