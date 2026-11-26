package com.revenge.paxsenix

import android.content.Context
import com.revenge.betterlyrics.TTMLParser
import com.revenge.metadata.cleaner.MetadataCleaner
import com.revenge.paxsenix.models.LyricsResponse
import com.revenge.paxsenix.models.SearchResponse
import com.revenge.paxsenix.models.SearchResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Paxsenix lyrics provider backed by https://lyrics.paxsenix.org.
 *
 * The client is process-wide; call [init] once (with a `Context`) before requesting lyrics.
 */
object Paxsenix {
  private const val BASE_URL = "https://lyrics.paxsenix.org"
  private const val UNKNOWN_VERSION = "Unknown"

  private val lock = Any()

  @Volatile private var client: HttpClient? = null

  fun init(context: Context) {
    if (client != null) return

    synchronized(lock) {
      if (client != null) return

      val version = resolveAppVersion(context)
      client = createClient(version)
      Timber.d("Paxsenix client initialized (appVersion=%s)", version)
    }
  }

  suspend fun getLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
  ): Result<String> = attempt {
    val target = trackTarget(title, artist, duration)
    val ranked = findCandidates(target, album)
    check(ranked.isNotEmpty()) { "No matching track found on Paxsenix" }

    awaitBestLyrics(ranked, PaxsenixCore.MAX_GET_LYRICS_CANDIDATES)
      ?: throw IllegalStateException("No lyrics available from Paxsenix")
  }

  suspend fun getAllLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
    callback: (String) -> Unit,
  ) {
    // The non-suspending view only uses the two title/artist queries, matching the provider's
    // fast path; the album variant is reserved for getLyrics.
    val ranked =
      attempt { findCandidates(trackTarget(title, artist, duration), album = null) }
        .onFailure { Timber.w(it, "Paxsenix candidate lookup failed") }
        .getOrDefault(emptyList())

    if (ranked.isEmpty()) {
      Timber.i("Paxsenix found no candidates for %s - %s", title, artist)
      return
    }

    val lyrics =
      awaitBestLyrics(ranked, PaxsenixCore.MAX_ALL_LYRICS_CANDIDATES) ?: return
    callback(lyrics)
  }

  private fun resolveAppVersion(context: Context): String {
    return try {
      context.packageManager
        .getPackageInfo(context.packageName, 0)
        .versionName
        ?.takeIf { it.isNotBlank() }
        ?: UNKNOWN_VERSION
    } catch (e: Exception) {
      Timber.w(e, "Paxsenix could not read the app version")
      UNKNOWN_VERSION
    }
  }

  private fun createClient(version: String): HttpClient =
    HttpClient(OkHttp) {
      expectSuccess = true

      install(HttpTimeout) {
        requestTimeoutMillis = 5_000
        connectTimeoutMillis = 4_000
        socketTimeoutMillis = 5_000
      }

      install(ContentNegotiation) {
        json(
          Json {
            isLenient = true
            ignoreUnknownKeys = true
          },
        )
      }

      defaultRequest {
        url(BASE_URL)
        header(HttpHeaders.UserAgent, "revenge/$version")
      }
    }

  private val httpClient: HttpClient
    get() =
      client ?: throw IllegalStateException("Paxsenix.init() must be called before fetching lyrics")

  private fun trackTarget(title: String, artist: String, duration: Int): TrackTarget {
    val (cleanTitle, cleanArtist) = MetadataCleaner.clean(title, artist).toSearchQuery()
    return TrackTarget(
      title = title,
      cleanTitle = cleanTitle,
      cleanArtist = cleanArtist,
      durationSeconds = duration,
    )
  }

  private suspend fun findCandidates(target: TrackTarget, album: String?): List<ScoredResult> {
    for (query in PaxsenixCore.searchQueries(target.cleanTitle, target.cleanArtist, album)) {
      val ranked = PaxsenixCore.rank(search(query), target)
      if (ranked.isNotEmpty()) return ranked
    }
    return emptyList()
  }

  private suspend fun search(query: String): List<SearchResult> {
    Timber.d("Paxsenix searching: %s", query)
    return attempt {
      httpClient.get("/apple-music/search") { parameter("q", query) }.body<SearchResponse>()
    }
      .onFailure { Timber.w(it, "Paxsenix search failed for query: %s", query) }
      .getOrDefault(emptyList())
  }

  private suspend fun fetchLyrics(id: String): Result<LyricsCandidate> = attempt {
    Timber.d("Paxsenix fetching lyrics for track: %s", id)
    val response =
      httpClient.get("/apple-music/lyrics") { parameter("id", id) }.body<LyricsResponse>()
    PaxsenixCore.buildLyrics(response, ::convertTtmlToLrc).getOrThrow()
  }

  private fun convertTtmlToLrc(ttml: String): String =
    attempt { TTMLParser.toLRC(TTMLParser.parseTTML(ttml)) }
      .onFailure { Timber.w(it, "Paxsenix TTML conversion failed") }
      .getOrDefault("")

  /**
   * Fetches the ranked candidates concurrently and returns the first word-timed result, or the
   * first non-empty unsynced result once every candidate has finished. Remaining requests are
   * cancelled as soon as a winner is found.
   */
  private suspend fun awaitBestLyrics(ranked: List<ScoredResult>, limit: Int): String? =
    coroutineScope {
      val jobs = ranked.take(limit).map { candidate -> async { fetchLyrics(candidate.result.id) } }
      val pending = jobs.toMutableList()
      var fallback: String? = null

      try {
        while (pending.isNotEmpty()) {
          val fetched = select { pending.forEach { job -> job.onAwait { it } } }
          pending.removeAll { it.isCompleted }

          val candidate = fetched.getOrNull() ?: continue
          if (candidate.lyrics.isBlank()) continue

          if (candidate.hasWordTimings) return@coroutineScope candidate.lyrics
          if (fallback == null) fallback = candidate.lyrics
        }
      } finally {
        jobs.forEach { it.cancel() }
      }

      fallback
    }

  /** `runCatching` that never swallows coroutine cancellation. */
  private inline fun <T> attempt(block: () -> T): Result<T> {
    return try {
      Result.success(block())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      Result.failure(e)
    }
  }
}
