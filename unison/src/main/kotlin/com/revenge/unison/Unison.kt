package com.revenge.unison

import com.revenge.metadata.cleaner.MetadataCleaner
import com.revenge.unison.models.UnisonEntry
import com.revenge.unison.models.UnisonResponse
import com.revenge.unison.models.UnisonSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json

private const val BASE_URL = "https://unison.boidu.dev/"
private const val MAX_SEARCH_RESULTS = 5
private const val LYRICS_UNAVAILABLE = "Lyrics unavailable"

object Unison {
  var logger: ((String) -> Unit)? = null

  private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    coerceInputValues = true
  }

  internal var testClient: HttpClient? = null

  private val defaultClient: HttpClient by lazy {
    HttpClient(OkHttp) {
      expectSuccess = false
      install(ContentEncoding) {
        gzip()
        deflate()
      }
      install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 20_000
      }
    }
  }

  private fun client(): HttpClient = testClient ?: defaultClient

  suspend fun getLyrics(
    videoId: String? = null,
    title: String,
    artist: String,
    album: String? = null,
    durationSeconds: Int = -1,
  ): Result<String> {
    return try {
      require(title.isNotBlank() && artist.isNotBlank()) {
        "Song title and artist are required"
      }
      val entry = fetchEntry(videoId, title, artist, album, durationSeconds)
      if (entry == null) {
        Result.failure(IllegalStateException(LYRICS_UNAVAILABLE))
      } else {
        Result.success(entry.lyrics)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun getAllLyrics(
    videoId: String? = null,
    title: String,
    artist: String,
    album: String? = null,
    durationSeconds: Int = -1,
    callback: (String) -> Unit,
  ) {
    val entries = searchEntries(title, artist, album, durationSeconds)
    if (entries.isNotEmpty()) {
      logger?.invoke("Unison search returned ${entries.size} entries")
      for (entry in entries.take(MAX_SEARCH_RESULTS)) {
        currentCoroutineContext().ensureActive()
        logger?.invoke("Unison delivering search entry")
        callback(entry.lyrics)
      }
      return
    }

    currentCoroutineContext().ensureActive()
    logger?.invoke("Unison search empty; trying single lookup")
    val entry = fetchEntry(videoId, title, artist, album, durationSeconds) ?: return
    callback(entry.lyrics)
  }

  internal fun parseSingle(body: String): UnisonEntry? {
    val response =
      try {
        json.decodeFromString<UnisonResponse>(body)
      } catch (e: Exception) {
        return null
      }
    if (!response.success) return null
    val entry = response.data ?: return null
    return entry.takeIf { it.lyrics.isNotBlank() }
  }

  internal fun parseSearch(body: String): List<UnisonEntry> {
    val response =
      try {
        json.decodeFromString<UnisonSearchResponse>(body)
      } catch (e: Exception) {
        return emptyList()
      }
    if (!response.success) return emptyList()
    return response.data.orEmpty().filter { it.lyrics.isNotBlank() }
  }

  private suspend fun fetchEntry(
    videoId: String?,
    title: String,
    artist: String,
    album: String?,
    durationSeconds: Int,
  ): UnisonEntry? {
    if (!videoId.isNullOrBlank()) {
      fetch("lyrics", listOf("v" to videoId))?.let { body ->
        parseSingle(body)?.let {
          return it
        }
      }
    }

    val (cleanTitle, cleanArtist) = MetadataCleaner.clean(title, artist).toSearchQuery()
    if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null
    val body =
      fetch(
        "lyrics",
        metadataParams(cleanTitle, cleanArtist, album, durationSeconds),
      ) ?: return null
    return parseSingle(body)
  }

  private suspend fun searchEntries(
    title: String,
    artist: String,
    album: String?,
    durationSeconds: Int,
  ): List<UnisonEntry> {
    val (cleanTitle, cleanArtist) = MetadataCleaner.clean(title, artist).toSearchQuery()
    if (cleanTitle.isBlank() || cleanArtist.isBlank()) return emptyList()
    val body =
      fetch(
        "lyrics/search",
        metadataParams(cleanTitle, cleanArtist, album, durationSeconds),
      ) ?: return emptyList()
    return parseSearch(body)
  }

  private fun metadataParams(
    title: String,
    artist: String,
    album: String?,
    durationSeconds: Int,
  ): List<Pair<String, Any?>> {
    val params = ArrayList<Pair<String, Any?>>()
    params.add("song" to title)
    params.add("artist" to artist)
    if (!album.isNullOrBlank()) params.add("album" to album.trim())
    if (durationSeconds > 0) params.add("duration" to durationSeconds)
    return params
  }

  private suspend fun fetch(path: String, params: List<Pair<String, Any?>>): String? {
    return try {
      val response =
        client().get("$BASE_URL$path") {
          for ((key, value) in params) {
            parameter(key, value)
          }
        }
      if (!response.status.isSuccess()) {
        null
      } else {
        response.bodyAsText()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }
  }
}
