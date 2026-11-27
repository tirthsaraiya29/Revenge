package com.revenge.kugou

import com.revenge.kugou.models.DownloadLyricsResponse
import com.revenge.kugou.models.Keyword
import com.revenge.kugou.models.SearchLyricsResponse
import com.revenge.kugou.models.SearchSongResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import java.util.Base64
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

private const val SONG_SEARCH_URL = "https://mobileservice.kugou.com/api/v3/search/song"
private const val LYRICS_SEARCH_URL = "https://lyrics.kugou.com/search"
private const val LYRICS_DOWNLOAD_URL = "https://lyrics.kugou.com/download"
private const val PAGE_SIZE = "8"
private const val DURATION_TOLERANCE = 8
private const val CREDIT_SCAN_LIMIT = 30
private const val DURATION_UNKNOWN = -1

private val TITLE_NOISE =
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

private val PAREN_NOISE = listOf(Regex("\\(.*\\)"), Regex("（.*）"))

private val HEAD_CREDIT = Regex(".+].+[:：].+")

internal val LRC_LINE_PATTERN = Regex("^\\[\\d\\d:\\d\\d\\.\\d{2,3}].*")

internal val LRC_CREDIT_PATTERN =
  Regex(
    "^\\[\\d\\d:\\d\\d\\.\\d{2,3}].*" +
      "(作词|作曲|编曲|制作|监制|混音|母带|录音|和声|吉他|贝斯|出品|发行|策划|统筹|演唱|" +
      "lyrics?\\s*by|compos|arrang|producer|mix|master|written|performed|music)",
    RegexOption.IGNORE_CASE,
  )

internal fun buildSearchQuery(keyword: Keyword): String = buildString {
  append(keyword.title)
  append(" - ")
  append(keyword.artist)
  val album = keyword.album
  if (!album.isNullOrBlank()) {
    append(' ')
    append(album)
  }
}

internal fun String.normalizeLyrics(): String {
  val lines = lineSequence().filter { LRC_LINE_PATTERN.matches(it) }.toList()
  if (lines.isEmpty()) return ""

  var retained = lines
  val headLimit = minOf(CREDIT_SCAN_LIMIT, retained.lastIndex)
  var headCut = -1
  for (index in headLimit downTo 0) {
    if (HEAD_CREDIT.containsMatchIn(retained[index])) {
      headCut = index
      break
    }
  }
  if (headCut >= 0) retained = retained.drop(headCut + 1)
  if (retained.isEmpty()) return ""

  val lastIndex = retained.lastIndex
  val tailStart = lastIndex - minOf(retained.size - CREDIT_SCAN_LIMIT, lastIndex)
  var tailCut = -1
  for (index in tailStart..lastIndex) {
    if (LRC_CREDIT_PATTERN.containsMatchIn(retained[index])) {
      tailCut = index
      break
    }
  }
  if (tailCut >= 0) retained = retained.take(tailCut)
  return retained.joinToString("\n")
}

object KuGou {
  var useTraditionalChinese: Boolean = false

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  internal var testClient: HttpClient? = null

  private val defaultClient: HttpClient by lazy {
    HttpClient(OkHttp) {
      expectSuccess = true
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

  fun generateKeyword(title: String, artist: String, album: String? = null): Keyword {
    var cleanTitle = title
    for (pattern in TITLE_NOISE) cleanTitle = cleanTitle.replace(pattern, "")

    var cleanArtist =
      artist.replace(", ", "、").replace(" & ", "、").replace(".", "").replace("和", "、")
    for (pattern in PAREN_NOISE) cleanArtist = cleanArtist.replace(pattern, "")

    return Keyword(cleanTitle, cleanArtist, album)
  }

  suspend fun getLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
  ): Result<String> {
    return try {
      val keyword = generateKeyword(title, artist, album)
      val candidate = getLyricsCandidate(keyword, duration)
      if (candidate == null) {
        Result.failure(
          IllegalStateException("No lyrics candidate for ${keyword.title} - ${keyword.artist}"),
        )
      } else {
        val content = download(candidate)
        val decoded = String(Base64.getMimeDecoder().decode(content), Charsets.UTF_8)
        Result.success(decoded.normalizeLyrics())
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun getAllPossibleLyricsOptions(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
    callback: (String) -> Unit,
  ) {
    val keyword = generateKeyword(title, artist, album)
    val songs = searchSongs(keyword)
    for (song in songs.data.info) {
      if (duration == DURATION_UNKNOWN || abs(song.duration - duration) <= DURATION_TOLERANCE) {
        val candidate = searchLyricsByHash(song.hash).candidates.firstOrNull() ?: continue
        callback(decodeCandidate(candidate))
      }
    }
    for (candidate in searchLyricsByKeyword(keyword, duration).candidates) {
      callback(decodeCandidate(candidate))
    }
  }

  suspend fun getLyricsCandidate(
    keyword: Keyword,
    duration: Int,
  ): SearchLyricsResponse.Candidate? {
    val songs = searchSongs(keyword)
    for (song in songs.data.info) {
      if (duration == DURATION_UNKNOWN || abs(song.duration - duration) <= DURATION_TOLERANCE) {
        val candidate = searchLyricsByHash(song.hash).candidates.firstOrNull()
        if (candidate != null) return candidate
      }
    }
    return searchLyricsByKeyword(keyword, duration).candidates.firstOrNull()
  }

  suspend fun searchSongs(keyword: Keyword): SearchSongResponse {
    val response =
      client().get(SONG_SEARCH_URL) {
        url {
          encodedParameters.append("version", "9108")
          encodedParameters.append("plat", "0")
          encodedParameters.append("pagesize", PAGE_SIZE)
          encodedParameters.append("showtype", "0")
          encodedParameters.append(
            "keyword",
            buildSearchQuery(keyword).encodeURLParameter(spaceToPlus = false),
          )
        }
      }
    return json.decodeFromString<SearchSongResponse>(response.bodyAsText())
  }

  private suspend fun searchLyricsByHash(hash: String): SearchLyricsResponse {
    val response =
      client().get(LYRICS_SEARCH_URL) {
        url {
          encodedParameters.append("ver", "1")
          encodedParameters.append("man", "yes")
          encodedParameters.append("client", "pc")
          encodedParameters.append("hash", hash)
        }
      }
    return json.decodeFromString<SearchLyricsResponse>(response.bodyAsText())
  }

  private suspend fun searchLyricsByKeyword(keyword: Keyword, duration: Int): SearchLyricsResponse {
    val response =
      client().get(LYRICS_SEARCH_URL) {
        url {
          encodedParameters.append("ver", "1")
          encodedParameters.append("man", "yes")
          encodedParameters.append("client", "pc")
          if (duration != DURATION_UNKNOWN) {
            encodedParameters.append("duration", (duration * 1000).toString())
          }
          encodedParameters.append(
            "keyword",
            buildSearchQuery(keyword).encodeURLParameter(spaceToPlus = false),
          )
        }
      }
    return json.decodeFromString<SearchLyricsResponse>(response.bodyAsText())
  }

  private suspend fun download(candidate: SearchLyricsResponse.Candidate): String {
    val response =
      client().get(LYRICS_DOWNLOAD_URL) {
        url {
          encodedParameters.append("fmt", "lrc")
          encodedParameters.append("charset", "utf8")
          encodedParameters.append("client", "pc")
          encodedParameters.append("ver", "1")
          encodedParameters.append("id", candidate.id.toString())
          encodedParameters.append("accesskey", candidate.accesskey)
        }
      }
    return json.decodeFromString<DownloadLyricsResponse>(response.bodyAsText()).content
  }

  private suspend fun decodeCandidate(candidate: SearchLyricsResponse.Candidate): String {
    val content = download(candidate)
    val decoded = String(Base64.getMimeDecoder().decode(content), Charsets.UTF_8)
    return decoded.normalizeLyrics()
  }
}
