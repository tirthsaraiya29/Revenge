package com.revenge.lrclib

import com.revenge.lrclib.models.DURATION_UNKNOWN
import com.revenge.lrclib.models.Track
import com.revenge.lrclib.models.bestMatchingFor
import com.revenge.lrclib.models.bestMatchingForRelaxed
import com.revenge.lrclib.models.similarity
import com.revenge.metadata.cleaner.MetadataCleaner
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json

private const val BASE_URL = "https://lrclib.net"
private const val SEARCH_PATH = "/api/search"
private const val MAX_ALTERNATE_LYRICS = 5
private const val LYRICS_UNAVAILABLE = "Lyrics unavailable"

private val TIME_TAG = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

private fun parseSentences(text: String): Map<Long, String> {
  val sentences = linkedMapOf<Long, String>(0L to "")
  for (line in text.lineSequence()) {
    val matches = TIME_TAG.findAll(line).toList()
    if (matches.isEmpty()) continue
    val content = line.substring(matches.last().range.last + 1)
    for (match in matches) {
      val minutes = match.groupValues[1].toLongOrNull() ?: continue
      val seconds = match.groupValues[2].toLongOrNull() ?: continue
      val fraction = match.groupValues[3]
      val millis =
        when (fraction.length) {
          0 -> 0L
          1 -> fraction.toLong() * 100
          2 -> fraction.toLong() * 10
          else -> fraction.take(3).toLong()
        }
      sentences[minutes * 60_000 + seconds * 1_000 + millis] = content
    }
  }
  return sentences
}

object LrcLib {
  private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
  }

  internal var testClient: HttpClient? = null

  private val defaultClient: HttpClient by lazy {
    HttpClient(CIO) {
      expectSuccess = true
      install(ContentEncoding) {
        gzip()
        deflate()
      }
      install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
      }
    }
  }

  private fun client(): HttpClient = testClient ?: defaultClient

  suspend fun getLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
  ): Result<String> {
    return try {
      val cleaned = MetadataCleaner.clean(title, artist)
      val (cleanTitle, cleanArtist) = cleaned.toSearchQuery()
      val tracks = findTracks(title, artist, album)
      val track =
        if (duration == DURATION_UNKNOWN) {
          tracks.bestMatchingFor(DURATION_UNKNOWN, cleanTitle, cleanArtist)
        } else {
          tracks.bestMatchingForRelaxed(duration)
        }
      val lyrics = track?.syncedLyrics ?: track?.plainLyrics
      if (lyrics == null) {
        Result.failure(IllegalStateException(LYRICS_UNAVAILABLE))
      } else {
        Result.success(lyrics)
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
    val cleaned = MetadataCleaner.clean(title, artist)
    val (cleanTitle, cleanArtist) = cleaned.toSearchQuery()
    val tracks = findTracks(title, artist, album)
    val delivered = ArrayList<String>()

    if (duration == DURATION_UNKNOWN) {
      for (track in tracks.sortedByDescending { rank(it, cleanTitle, cleanArtist) }) {
        if (delivered.size >= MAX_ALTERNATE_LYRICS) break
        currentCoroutineContext().ensureActive()
        val synced = track.syncedLyrics ?: continue
        delivered.add(synced)
        currentCoroutineContext().ensureActive()
        callback(synced)
      }
    } else {
      val inRange =
        tracks
          .filter { abs(it.duration.toInt() - duration) <= 5 }
          .sortedBy { abs(it.duration.toInt() - duration) }
      var plainDelivered = false
      for (track in inRange) {
        if (delivered.size >= MAX_ALTERNATE_LYRICS) break
        currentCoroutineContext().ensureActive()
        val synced = track.syncedLyrics
        if (synced != null) {
          delivered.add(synced)
          currentCoroutineContext().ensureActive()
          callback(synced)
        }
        if (delivered.size >= MAX_ALTERNATE_LYRICS) break
        val plain = track.plainLyrics
        if (!plainDelivered && plain != null) {
          plainDelivered = true
          delivered.add(plain)
          currentCoroutineContext().ensureActive()
          callback(plain)
        }
      }
    }
  }

  suspend fun lyrics(artist: String, title: String): Result<List<Track>> {
    return try {
      Result.success(findTracks(title, artist, album = null))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun rank(track: Track, cleanTitle: String, cleanArtist: String): Double {
    val names =
      (similarity(cleanTitle, track.trackName) + similarity(cleanArtist, track.artistName)) / 2.0
    return names + if (track.syncedLyrics != null) 1.0 else 0.0
  }

  private suspend fun findTracks(title: String, artist: String, album: String?): List<Track> {
    val cleaned = MetadataCleaner.clean(title, artist)
    val (searchTitle, searchArtist) = cleaned.toSearchQuery()
    val primaryArtist = cleaned.primaryArtist

    usableSearch { search(searchTitle, primaryArtist, album) }
      ?.let {
        return it
      }
    if (primaryArtist != searchArtist) {
      usableSearch { search(searchTitle, searchArtist, album) }
        ?.let {
          return it
        }
    }
    usableSearch { search(trackName = searchTitle) }
      ?.let {
        return it
      }
    usableSearch { search(query = "$searchArtist $searchTitle".trim()) }
      ?.let {
        return it
      }
    usableSearch { search(query = searchTitle) }
      ?.let {
        return it
      }
    if (searchTitle != title.trim()) {
      usableSearch { search(trackName = title.trim(), artistName = artist.trim()) }
        ?.let {
          return it
        }
    }
    return emptyList()
  }

  private suspend fun usableSearch(block: suspend () -> List<Track>): List<Track>? {
    return try {
      block().filter { it.syncedLyrics != null || it.plainLyrics != null }.ifEmpty { null }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }
  }

  private suspend fun search(
    trackName: String? = null,
    artistName: String? = null,
    albumName: String? = null,
    query: String? = null,
  ): List<Track> {
    val response =
      client().get("$BASE_URL$SEARCH_PATH") {
        parameter("track_name", trackName)
        parameter("artist_name", artistName)
        parameter("album_name", albumName)
        parameter("q", query)
      }
    return json.decodeFromString<List<Track>>(response.bodyAsText())
  }

  @JvmInline
  value class Lyrics(val text: String) {
    val sentences: Map<Long, String>?
      get() =
        try {
          parseSentences(text)
        } catch (e: Exception) {
          null
        }
  }
}
