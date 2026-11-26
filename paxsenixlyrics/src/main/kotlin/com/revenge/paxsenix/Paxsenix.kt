package com.revenge.paxsenix

import android.content.Context
import com.revenge.metadata.cleaner.MetadataCleaner
import com.revenge.paxsenix.models.LyricsResponse
import com.revenge.paxsenix.models.SearchResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber

private const val BASE_URL = "https://lyrics.paxsenix.org"
private const val INIT_ERROR = "Paxsenix.init() must be called before fetching lyrics"
private const val NO_MATCH = "No matching track found on Paxsenix"
private const val NO_LYRICS = "No lyrics available from Paxsenix"

internal typealias SearchResponse = List<SearchResult>

object Paxsenix {
  private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
  }

  private val initLock = Any()

  @Volatile
  private var client: HttpClient? = null

  @Volatile
  private var version: String = "Unknown"

  internal var testClient: HttpClient? = null

  fun init(context: Context) {
    if (client != null) return
    synchronized(initLock) {
      if (client != null) return
      version = readVersion(context)
      client = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentEncoding) {
          gzip()
          deflate()
        }
        install(HttpTimeout) {
          requestTimeoutMillis = 5_000
          connectTimeoutMillis = 4_000
          socketTimeoutMillis = 5_000
        }
      }
    }
  }

  suspend fun getLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
  ): Result<String> {
    return try {
      val target = trackTarget(title, artist, duration)
      val ranked = findCandidates(target, album)
      if (ranked.isEmpty()) {
        Result.failure(IllegalStateException(NO_MATCH))
      } else {
        val candidate = awaitBestLyrics(ranked, PaxsenixCore.MAX_GET_LYRICS_CANDIDATES)
        if (candidate == null) {
          Result.failure(IllegalStateException(NO_LYRICS))
        } else {
          Result.success(candidate.lyrics)
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun getAllLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
    callback: (String) -> Unit,
  ) {
    try {
      val target = trackTarget(title, artist, duration)
      val ranked = findCandidates(target, album = null)
      if (ranked.isEmpty()) {
        Timber.d("Paxsenix found no matching track")
        return
      }
      val candidate = awaitBestLyrics(ranked, PaxsenixCore.MAX_ALL_LYRICS_CANDIDATES) ?: return
      callback(candidate.lyrics)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Timber.w(e, "Paxsenix getAllLyrics failed")
    }
  }

  private fun readVersion(context: Context): String {
    return try {
      context.packageManager.getPackageInfo(context.packageName, 0).versionName
        ?.takeIf { it.isNotBlank() } ?: "Unknown"
    } catch (e: Exception) {
      Timber.w(e, "Paxsenix could not read app version")
      "Unknown"
    }
  }

  private fun http(): HttpClient =
    testClient ?: client ?: throw IllegalStateException(INIT_ERROR)

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
    return try {
      getJson(
        path = "/apple-music/search",
        params = listOf("q" to query),
      ) { body -> json.decodeFromString<List<SearchResult>>(body) }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Timber.w(e, "Paxsenix search failed")
      emptyList()
    }
  }

  private suspend fun fetchLyrics(id: String): LyricsCandidate {
    val response = getJson(
      path = "/apple-music/lyrics",
      params = listOf("id" to id),
    ) { body -> json.decodeFromString<LyricsResponse>(body) }
    return PaxsenixCore.buildLyrics(response, ::convertTtmlToLrc).getOrThrow()
  }

  private suspend fun <T> getJson(
    path: String,
    params: List<Pair<String, Any?>>,
    decode: (String) -> T,
  ): T {
    val response = http().get("$BASE_URL$path") {
      header(HttpHeaders.UserAgent, "revenge/$version")
      for ((key, value) in params) {
        parameter(key, value)
      }
    }
    return decode(response.bodyAsText())
  }

  private suspend fun awaitBestLyrics(
    ranked: List<ScoredResult>,
    limit: Int,
  ): LyricsCandidate? {
    val targets = ranked.take(limit)
    if (targets.isEmpty()) return null
    return coroutineScope {
      val results = Channel<LyricsCandidate?>(Channel.UNLIMITED)
      val jobs = targets.map { scored ->
        launch {
          val candidate = try {
            fetchLyrics(scored.result.id)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            null
          }
          results.send(candidate)
        }
      }
      var fallback: LyricsCandidate? = null
      var winner: LyricsCandidate? = null
      try {
        for (index in targets.indices) {
          val candidate = results.receive() ?: continue
          if (candidate.lyrics.isBlank()) continue
          if (candidate.hasWordTimings) {
            winner = candidate
            break
          }
          if (fallback == null) fallback = candidate
        }
      } finally {
        jobs.forEach { it.cancel() }
      }
      winner ?: fallback
    }
  }
}
