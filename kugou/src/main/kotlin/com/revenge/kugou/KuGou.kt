package com.revenge.kugou

import com.revenge.kugou.models.DownloadLyricsResponse
import com.revenge.kugou.models.Keyword
import com.revenge.kugou.models.SearchLyricsResponse
import com.revenge.kugou.models.SearchSongResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.serialization.json.Json

private const val SONG_SEARCH_URL = "https://mobileservice.kugou.com/api/v3/search/song"
private const val LYRICS_SEARCH_URL = "https://lyrics.kugou.com/search"
private const val LYRICS_DOWNLOAD_URL = "https://lyrics.kugou.com/download"

private const val PAGE_SIZE = 8
private const val DURATION_TOLERANCE_SECONDS = 8
private const val CREDIT_SCAN_LIMIT = 30

private val jsonFormat =
  Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

private val client by lazy {
  HttpClient(OkHttp) {
    expectSuccess = true

    install(ContentNegotiation) {
      json(jsonFormat)
      json(jsonFormat, ContentType.Text.Html)
      json(jsonFormat, ContentType.Text.Plain)
    }

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

/**
 * Lyrics client for the KuGou service, originally ported from
 * [ViMusic](https://github.com/vfsfitvnm/ViMusic).
 *
 * Lookup order: search songs by `title - artist [album]`, try the lyric candidates attached to each
 * matching hash, then fall back to a direct lyric search by keyword. Every public entry point either
 * returns a [Result] or a nullable value; coroutine cancellation is always rethrown.
 */
object KuGou {
  /**
   * Exposed for the app's zh-TW locale toggle. The reference service protocol returns the same
   * simplified-Chinese document regardless, so results are not converted without an OpenCC-style
   * dependency.
   */
  var useTraditionalChinese: Boolean = false

  /** Resolves and decodes the best matching lyric document, normalized to plain LRC lines. */
  suspend fun getLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
  ): Result<String> = runCatchingResult {
    val keyword = generateKeyword(title, artist, album)
    val candidate =
      getLyricsCandidate(keyword, duration)
        ?: throw IllegalStateException("No lyrics candidate for ${keyword.title} - ${keyword.artist}")
    downloadAndDecode(candidate)
  }

  /**
   * Emits every distinct matching lyric document through [callback]: first the lyrics linked to the
   * individual song hashes, then the lyrics found by a direct keyword search.
   */
  suspend fun getAllPossibleLyricsOptions(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
    callback: (String) -> Unit,
  ) {
    val keyword = generateKeyword(title, artist, album)

    searchSongs(keyword).data.info.forEach { song ->
      if (duration == -1 || abs(song.duration - duration) <= DURATION_TOLERANCE_SECONDS) {
        searchLyricsByHash(song.hash).candidates.firstOrNull()?.let { candidate ->
          callback(downloadAndDecode(candidate))
        }
      }
    }

    searchLyricsByKeyword(keyword, duration).candidates.forEach { candidate ->
      callback(downloadAndDecode(candidate))
    }
  }

  /** Returns the first lyric candidate whose song duration is within the tolerance, if any. */
  suspend fun getLyricsCandidate(
    keyword: Keyword,
    duration: Int,
  ): SearchLyricsResponse.Candidate? {
    searchSongs(keyword).data.info.forEach { song ->
      if (duration == -1 || abs(song.duration - duration) <= DURATION_TOLERANCE_SECONDS) {
        searchLyricsByHash(song.hash).candidates.firstOrNull()?.let { return it }
      }
    }
    return searchLyricsByKeyword(keyword, duration).candidates.firstOrNull()
  }

  suspend fun searchSongs(keyword: Keyword): SearchSongResponse =
    client
      .get(SONG_SEARCH_URL) {
        parameter("version", 9108)
        parameter("plat", 0)
        parameter("pagesize", PAGE_SIZE)
        parameter("showtype", 0)
        appendKeyword(keyword)
      }
      .body()

  private suspend fun searchLyricsByKeyword(
    keyword: Keyword,
    duration: Int,
  ): SearchLyricsResponse =
    client
      .get(LYRICS_SEARCH_URL) {
        parameter("ver", 1)
        parameter("man", "yes")
        parameter("client", "pc")
        // -1 means the caller does not know (or does not care about) the duration.
        parameter("duration", duration.takeIf { it != -1 }?.times(1000))
        appendKeyword(keyword)
      }
      .body()

  private suspend fun searchLyricsByHash(hash: String): SearchLyricsResponse =
    client
      .get(LYRICS_SEARCH_URL) {
        parameter("ver", 1)
        parameter("man", "yes")
        parameter("client", "pc")
        parameter("hash", hash)
      }
      .body()

  private suspend fun downloadLyrics(id: Long, accessKey: String): DownloadLyricsResponse =
    client
      .get(LYRICS_DOWNLOAD_URL) {
        parameter("fmt", "lrc")
        parameter("charset", "utf8")
        parameter("client", "pc")
        parameter("ver", 1)
        parameter("id", id)
        parameter("accesskey", accessKey)
      }
      .body()

  private suspend fun downloadAndDecode(candidate: SearchLyricsResponse.Candidate): String {
    val encoded = downloadLyrics(candidate.id, candidate.accesskey).content
    return Base64.getMimeDecoder().decode(encoded).decodeToString().normalizeLyrics()
  }

  fun generateKeyword(
    title: String,
    artist: String,
    album: String? = null,
  ): Keyword = Keyword(normalizeTitle(title), normalizeArtist(artist), album)
}

private fun HttpRequestBuilder.appendKeyword(keyword: Keyword) {
  // KuGou expects literal spaces in the keyword, not '+'.
  url.encodedParameters.append(
    "keyword",
    buildSearchQuery(keyword).encodeURLParameter(spaceToPlus = false),
  )
}

/** @return the raw `title - artist [album]` search phrase before URL encoding. */
internal fun buildSearchQuery(keyword: Keyword): String =
  buildString {
    append(keyword.title)
    append(" - ")
    append(keyword.artist)
    if (!keyword.album.isNullOrBlank()) {
      append(' ')
      append(keyword.album)
    }
  }

private val TITLE_NOISE_PATTERNS =
  listOf(
    Regex("\\(.*\\)"),
    Regex("（.*）"),
    Regex("「.*」"),
    Regex("『.*』"),
    Regex("<.*>"),
    Regex("《.*》"),
    Regex("〈.*〉"),
    Regex("＜.*＞"),
  )

private val ARTIST_NOISE_PATTERNS = listOf(Regex("\\(.*\\)"), Regex("（.*）"))

private fun normalizeTitle(title: String): String =
  TITLE_NOISE_PATTERNS.fold(title) { value, pattern -> value.replace(pattern, "") }

private fun normalizeArtist(artist: String): String =
  ARTIST_NOISE_PATTERNS
    .fold(
      artist
        .replace(", ", "、")
        .replace(" & ", "、")
        .replace(".", "")
        .replace("和", "、"),
    ) { value, pattern -> value.replace(pattern, "") }

/** Matches a timestamped LRC line, e.g. `[01:23.45]text` or `[01:23.456]text`. */
internal val LRC_LINE_PATTERN = Regex("""^\[\d\d:\d\d\.\d{2,3}].*""")

/** Matches credit metadata such as `[00:00.00]作词:某某`. */
internal val LRC_CREDIT_PATTERN = Regex(""".+].+[:：].+""")

/**
 * Keeps only timestamped lines and strips the leading/trailing credit block (composer, lyricist,
 * producer and similar) that KuGou attaches to the document.
 */
internal fun String.normalizeLyrics(): String {
  val lines = lines().filter { LRC_LINE_PATTERN.matches(it) }
  if (lines.isEmpty()) return ""

  var headCut = 0
  for (i in minOf(CREDIT_SCAN_LIMIT, lines.lastIndex) downTo 0) {
    if (LRC_CREDIT_PATTERN.matches(lines[i])) {
      headCut = i + 1
      break
    }
  }

  var tailCut = 0
  for (i in minOf(lines.size - CREDIT_SCAN_LIMIT, lines.lastIndex) downTo 0) {
    if (LRC_CREDIT_PATTERN.matches(lines[lines.lastIndex - i])) {
      tailCut = i + 1
      break
    }
  }

  return lines.drop(headCut).dropLast(tailCut).joinToString("\n")
}

/** [runCatching] that does not swallow coroutine cancellation. */
private suspend inline fun <T> runCatchingResult(block: suspend () -> T): Result<T> =
  try {
    Result.success(block())
  } catch (cancellation: CancellationException) {
    throw cancellation
  } catch (error: Exception) {
    Result.failure(error)
  }
