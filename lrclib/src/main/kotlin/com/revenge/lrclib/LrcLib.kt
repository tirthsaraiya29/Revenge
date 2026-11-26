package com.revenge.lrclib

import com.revenge.lrclib.models.Track
import com.revenge.lrclib.models.bestMatchingFor
import com.revenge.lrclib.models.bestMatchingForRelaxed
import com.revenge.lrclib.models.similarity
import com.revenge.metadata.cleaner.MetadataCleaner
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json

/**
 * Client for the public LrcLib lyrics database (https://lrclib.net).
 *
 * Searches are progressively widened (title + primary artist, full artist, title only, free-text,
 * raw metadata) and the first query that returns usable lyrics wins. Network and parsing failures
 * degrade to "no results" rather than throwing.
 */
object LrcLib {
  private const val BASE_URL = "https://lrclib.net"
  private const val SEARCH_ENDPOINT = "/api/search"
  private const val RELAXED_TOLERANCE_SECONDS = 5
  private const val MAX_ALTERNATE_LYRICS = 5
  private const val UNAVAILABLE = "Lyrics unavailable"

  private val jsonFormat =
    Json {
      isLenient = true
      ignoreUnknownKeys = true
    }

  private val client: HttpClient by lazy {
    HttpClient(CIO) {
      expectSuccess = true
      install(ContentNegotiation) { json(jsonFormat) }
      install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
      }
      defaultRequest { url(BASE_URL) }
    }
  }

  private data class SearchQuery(
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val freeText: String? = null,
  )

  /** Resolves the best lyrics text for the track. Fails when nothing usable is found. */
  suspend fun getLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
  ): Result<String> =
    try {
      val cleaned = MetadataCleaner.clean(title, artist)
      val (cleanTitle, cleanArtist) = cleaned.toSearchQuery()
      val tracks = findTracks(artist, title, album)
      val match =
        if (duration == -1) {
          tracks.bestMatchingFor(duration, cleanTitle, cleanArtist)
        } else {
          tracks.bestMatchingForRelaxed(duration)
        }
      val text =
        match?.let { it.syncedLyrics ?: it.plainLyrics }
          ?: throw IllegalStateException(UNAVAILABLE)
      Result.success(text)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  /**
   * Emits up to [MAX_ALTERNATE_LYRICS] lyrics variants, best match first. At most one plain
   * (unsynchronised) variant is emitted, and only for tracks close to [duration].
   */
  suspend fun getAllLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
    callback: (String) -> Unit,
  ) {
    val cleaned = MetadataCleaner.clean(title, artist)
    val (cleanTitle, cleanArtist) = cleaned.toSearchQuery()
    val tracks = findTracks(artist, title, album)

    val ordered =
      if (duration == -1) {
        tracks.sortedByDescending { rank(it, cleanTitle, cleanArtist) }
      } else {
        tracks.sortedBy { abs(it.duration.toInt() - duration) }
      }

    var delivered = 0
    var deliveredPlain = false
    for (track in ordered) {
      currentCoroutineContext().ensureActive()
      if (delivered >= MAX_ALTERNATE_LYRICS) break

      if (duration == -1) {
        track.syncedLyrics?.let {
          callback(it)
          delivered++
        }
        continue
      }

      if (abs(track.duration.toInt() - duration) > RELAXED_TOLERANCE_SECONDS) continue

      track.syncedLyrics?.let {
        callback(it)
        delivered++
      }
      val plain = track.plainLyrics
      if (!deliveredPlain && plain != null) {
        callback(plain)
        deliveredPlain = true
        delivered++
      }
    }
  }

  /** Raw search results for [title] by [artist], used when callers want to inspect candidates. */
  suspend fun lyrics(artist: String, title: String): Result<List<Track>> =
    try {
      Result.success(findTracks(artist, title, album = null))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  private suspend fun findTracks(artist: String, title: String, album: String?): List<Track> {
    val cleaned = MetadataCleaner.clean(title, artist)
    val (searchTitle, searchArtist) = cleaned.toSearchQuery()
    val primaryArtist = cleaned.primaryArtist

    val queries =
      buildList {
        add(SearchQuery(trackName = searchTitle, artistName = primaryArtist, albumName = album))
        if (primaryArtist != searchArtist) {
          add(SearchQuery(trackName = searchTitle, artistName = searchArtist, albumName = album))
        }
        add(SearchQuery(trackName = searchTitle))
        add(SearchQuery(freeText = "$searchArtist $searchTitle".trim()))
        add(SearchQuery(freeText = searchTitle))
        if (searchTitle != title.trim()) {
          add(SearchQuery(trackName = title.trim(), artistName = artist.trim()))
        }
      }

    for (query in queries) {
      val usable = search(query).filter { it.syncedLyrics != null || it.plainLyrics != null }
      if (usable.isNotEmpty()) return usable
    }
    return emptyList()
  }

  private suspend fun search(query: SearchQuery): List<Track> =
    try {
      client
        .get(SEARCH_ENDPOINT) {
          query.freeText?.let { parameter("q", it) }
          query.trackName?.let { parameter("track_name", it) }
          query.artistName?.let { parameter("artist_name", it) }
          query.albumName?.let { parameter("album_name", it) }
        }
        .body<List<Track>>()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      emptyList()
    }

  private fun rank(track: Track, title: String, artist: String): Double {
    val metadataScore =
      (similarity(title, track.trackName) + similarity(artist, track.artistName)) / 2.0
    return metadataScore + if (track.syncedLyrics != null) 1.0 else 0.0
  }

  /** Lyrics text wrapper that can lazily parse LRC timestamps into a time-to-line map. */
  @JvmInline
  value class Lyrics(val text: String) {
    /**
     * Parsed LRC lines keyed by start time in milliseconds, always containing `0L` as a leading
     * empty entry. Returns null only if parsing fails unexpectedly.
     */
    val sentences: Map<Long, String>?
      get() =
        runCatching {
          buildMap {
            put(0L, "")
            text.lineSequence().forEach { line ->
              val stamps = LRC_TIMESTAMP.findAll(line).toList()
              if (stamps.isEmpty()) return@forEach
              val content = line.substring(stamps.last().range.last + 1)
              for (stamp in stamps) {
                put(stamp.toMillis(), content)
              }
            }
          }
        }
          .getOrNull()
  }
}

private val LRC_TIMESTAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

private fun MatchResult.toMillis(): Long {
  val minutes = groupValues[1].toLong()
  val seconds = groupValues[2].toLong()
  val fraction = groupValues[3]
  val millis =
    when (fraction.length) {
      0 -> 0L
      1 -> fraction.toLong() * 100
      2 -> fraction.toLong() * 10
      else -> fraction.take(3).toLong()
    }
  return minutes * 60_000L + seconds * 1_000L + millis
}
