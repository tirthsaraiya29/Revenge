package com.revenge.unison

import com.revenge.metadata.cleaner.MetadataCleaner
import com.revenge.unison.models.UnisonEntry
import com.revenge.unison.models.UnisonResponse
import com.revenge.unison.models.UnisonSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json

/**
 * Client for the Unison lyrics service (https://unison.boidu.dev).
 *
 * Lookups are attempted by YouTube video id first, then fall back to a metadata search using
 * cleaned song/artist values. All network failures are contained and surfaced as empty/null
 * results or a failed [Result]; coroutine cancellation is always rethrown.
 */
object Unison {
  private const val API_BASE_URL = "https://unison.boidu.dev/"
  private const val MAX_SEARCH_RESULTS = 5

  private val jsonFormat by lazy {
    Json {
      isLenient = true
      ignoreUnknownKeys = true
      coerceInputValues = true
    }
  }

  private val client by lazy {
    HttpClient(OkHttp) {
      install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 20_000
      }

      defaultRequest { url(API_BASE_URL) }
      expectSuccess = false
    }
  }

  /** Optional sink for diagnostic logging. Invoked from network coroutines. */
  var logger: ((String) -> Unit)? = null

  /**
   * Resolves a single lyric document for the given track.
   *
   * @return the raw lyric text (LRC, TTML or plain text) or a failure when the required metadata is
   *   missing or the service has no match.
   */
  suspend fun getLyrics(
    videoId: String? = null,
    title: String,
    artist: String,
    album: String? = null,
    durationSeconds: Int = -1,
  ): Result<String> =
    try {
      require(title.isNotBlank() && artist.isNotBlank()) {
        "Song title and artist are required"
      }

      val entry =
        fetchEntry(videoId, title, artist, album, durationSeconds)
          ?: throw IllegalStateException("Lyrics unavailable")

      logger?.invoke(
        "Unison lyrics: format=${entry.format}, syncType=${entry.syncType}, size=${entry.lyrics.length}",
      )
      Result.success(entry.lyrics)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  /**
   * Streams every distinct candidate lyric document through [callback], capped at
   * [MAX_SEARCH_RESULTS]. When the search yields nothing, falls back to a single metadata lookup.
   */
  suspend fun getAllLyrics(
    videoId: String? = null,
    title: String,
    artist: String,
    album: String? = null,
    durationSeconds: Int = -1,
    callback: (String) -> Unit,
  ) {
    val results = searchEntries(title, artist, album, durationSeconds)

    if (results.isNotEmpty()) {
      results.take(MAX_SEARCH_RESULTS).forEachIndexed { index, entry ->
        currentCoroutineContext().ensureActive()
        logger?.invoke(
          "Unison search result ${index + 1}: format=${entry.format}, syncType=${entry.syncType}",
        )
        callback(entry.lyrics)
      }
      return
    }

    currentCoroutineContext().ensureActive()
    val single = fetchEntry(videoId, title, artist, album, durationSeconds) ?: return
    logger?.invoke("Unison search empty, using single result: format=${single.format}")
    callback(single.lyrics)
  }

  private suspend fun fetchEntry(
    videoId: String?,
    title: String,
    artist: String,
    album: String?,
    durationSeconds: Int,
  ): UnisonEntry? {
    if (!videoId.isNullOrBlank()) {
      logger?.invoke("Unison lookup by videoId: $videoId")
      fetchByVideoId(videoId)?.let { return it }
      logger?.invoke("Unison videoId lookup missed, falling back to metadata search")
    }

    val (cleanTitle, cleanArtist) = MetadataCleaner.clean(title, artist).toSearchQuery()
    if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null

    return fetchByMetadata(cleanTitle, cleanArtist, album, durationSeconds)
  }

  private suspend fun fetchByVideoId(videoId: String): UnisonEntry? {
    val body = request("lyrics", listOf("v" to videoId)) ?: return null
    return parseSingle(body)
  }

  private suspend fun fetchByMetadata(
    title: String,
    artist: String,
    album: String?,
    durationSeconds: Int,
  ): UnisonEntry? {
    val body = request("lyrics", metadataParams(title, artist, album, durationSeconds))
    return body?.let(::parseSingle)
  }

  private suspend fun searchEntries(
    title: String,
    artist: String,
    album: String?,
    durationSeconds: Int,
  ): List<UnisonEntry> {
    val (cleanTitle, cleanArtist) = MetadataCleaner.clean(title, artist).toSearchQuery()
    if (cleanTitle.isBlank() || cleanArtist.isBlank()) return emptyList()

    logger?.invoke("Unison search: title=$cleanTitle, artist=$cleanArtist")
    val body =
      request("lyrics/search", metadataParams(cleanTitle, cleanArtist, album, durationSeconds))
        ?: return emptyList()
    return parseSearch(body)
  }

  private fun metadataParams(
    title: String,
    artist: String,
    album: String?,
    durationSeconds: Int,
  ): List<Pair<String, Any?>> = buildList<Pair<String, Any?>> {
    add("song" to title)
    add("artist" to artist)
    album?.trim()?.takeIf { it.isNotEmpty() }?.let { add("album" to it) }
    if (durationSeconds > 0) add("duration" to durationSeconds)
  }

  /**
   * Performs a GET relative to [API_BASE_URL] and returns the response body text, or null on a
   * non-success status or any network/read failure. Cancellation is propagated.
   */
  private suspend fun request(
    endpoint: String,
    params: List<Pair<String, Any?>>,
  ): String? {
    return try {
      val response =
        client.get(endpoint) {
          params.forEach { (name, value) -> if (value != null) parameter(name, value) }
        }
      logger?.invoke("Unison $endpoint status: ${response.status}")
      if (!response.status.isSuccess()) return null

      val body = response.bodyAsText()
      logger?.invoke("Unison $endpoint body: ${body.take(300)}")
      body
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      logger?.invoke("Unison $endpoint error: ${e.message}")
      null
    }
  }

  internal fun parseSingle(body: String): UnisonEntry? =
    runCatching { jsonFormat.decodeFromString<UnisonResponse>(body) }
      .getOrNull()
      ?.takeIf { it.success }
      ?.data
      ?.takeIf { it.lyrics.isNotBlank() }

  internal fun parseSearch(body: String): List<UnisonEntry> =
    runCatching { jsonFormat.decodeFromString<UnisonSearchResponse>(body) }
      .getOrNull()
      ?.takeIf { it.success }
      ?.data
      .orEmpty()
      .filter { it.lyrics.isNotBlank() }
}
