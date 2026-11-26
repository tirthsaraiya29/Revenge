package com.revenge.paxsenix

import com.revenge.betterlyrics.TTMLParser
import com.revenge.paxsenix.models.LyricsContent
import com.revenge.paxsenix.models.LyricsResponse
import com.revenge.paxsenix.models.SearchResult
import java.util.Locale
import kotlin.math.abs
import timber.log.Timber

internal data class TrackTarget(
  val title: String,
  val cleanTitle: String,
  val cleanArtist: String,
  val durationSeconds: Int,
)

internal data class ScoredResult(
  val result: SearchResult,
  val score: Double,
)

internal data class LyricsCandidate(
  val lyrics: String,
  val hasWordTimings: Boolean,
)

internal fun convertTtmlToLrc(ttml: String): String {
  return try {
    TTMLParser.toLRC(TTMLParser.parseTTML(ttml))
  } catch (e: Exception) {
    Timber.w(e, "Failed to convert Paxsenix TTML")
    ""
  }
}

internal object PaxsenixCore {
  const val MAX_RANKED_RESULTS: Int = 10
  const val MAX_GET_LYRICS_CANDIDATES: Int = 5
  const val MAX_ALL_LYRICS_CANDIDATES: Int = 3

  private val TITLE_NOISE = Regex("\\s*\\([^)]*\\)|\\s*\\[[^\\]]*\\]")
  private val WORD_TIMING = Regex("<[^>]+>")

  fun searchQueries(cleanTitle: String, cleanArtist: String, album: String?): List<String> {
    val combined = listOf(cleanTitle, cleanArtist).filter { it.isNotBlank() }.joinToString(" ")
    val queries = ArrayList<String>()
    queries.add(combined)
    queries.add(cleanTitle)
    if (!album.isNullOrBlank()) {
      queries.add(listOf(cleanTitle, cleanArtist, album).filter { it.isNotBlank() }.joinToString(" "))
    }
    return queries.filter { it.isNotBlank() }.distinct()
  }

  fun rank(results: List<SearchResult>, target: TrackTarget): List<ScoredResult> {
    return results.asSequence()
      .filter { it.id.isNotBlank() }
      .map { ScoredResult(it, score(it, target)) }
      .filter { it.score > 0.0 }
      .sortedByDescending { it.score }
      .take(MAX_RANKED_RESULTS)
      .toList()
  }

  fun score(result: SearchResult, target: TrackTarget): Double {
    var total = 0.0

    val duration = result.duration
    if (duration != null) {
      val diff = abs(duration - target.durationSeconds * 1000)
      total += when {
        diff <= 2_000 -> 100.0
        diff <= 5_000 -> 50.0
        diff <= 10_000 -> 10.0
        else -> -50.0
      }
    }

    val wantedTitle = normalizeTitle(target.cleanTitle)
    val resultTitle = normalizeTitle(result.displayName)
    when {
      wantedTitle == resultTitle -> total += 80.0
      wantedTitle.isNotEmpty() && resultTitle.isNotEmpty() &&
        (wantedTitle.contains(resultTitle) || resultTitle.contains(wantedTitle)) -> total += 40.0
    }

    val resultName = result.displayName.lowercase()
    val rawTitle = target.title.lowercase()
    if (resultName.contains("mixed") && !rawTitle.contains("mixed")) total -= 60.0
    if (resultName.contains("remix") && !rawTitle.contains("remix")) total -= 40.0

    val wantedArtist = target.cleanArtist.lowercase().trim()
    if (wantedArtist.isNotEmpty()) {
      val resultArtist = result.displayArtist.lowercase()
      if (resultArtist.contains(wantedArtist)) {
        total += 50.0
      } else if (wantedArtist.split(Regex("\\s+")).any { it.length > 2 && resultArtist.contains(it) }) {
        total += 25.0
      }
    }

    return total
  }

  fun hasWordTimings(text: String): Boolean = WORD_TIMING.containsMatchIn(text)

  fun buildLyrics(
    response: LyricsResponse,
    ttmlConverter: (String) -> String = ::convertTtmlToLrc,
  ): Result<LyricsCandidate> {
    val ttml = response.ttmlContent
    if (!ttml.isNullOrBlank()) {
      val converted = ttmlConverter(ttml)
      if (converted.isNotBlank()) {
        return Result.success(LyricsCandidate(converted, hasWordTimings(converted)))
      }
    }

    response.elrcMultiPerson?.takeIf { it.isNotBlank() }?.let {
      return Result.success(LyricsCandidate(it, hasWordTimings(it)))
    }
    response.elrc?.takeIf { it.isNotBlank() }?.let {
      return Result.success(LyricsCandidate(it, hasWordTimings(it)))
    }
    response.plain?.takeIf { it.isNotBlank() }?.let {
      return Result.success(LyricsCandidate(it, false))
    }

    if (response.content.isEmpty()) {
      return Result.failure(IllegalStateException("Paxsenix returned no lyrics"))
    }

    return if (response.type.equals("Syllable", ignoreCase = true)) {
      val lrc = lrcFromContent(response.content)
      if (lrc.isBlank()) {
        Result.failure(IllegalStateException("Paxsenix returned empty lyrics"))
      } else {
        Result.success(LyricsCandidate(lrc, true))
      }
    } else {
      val plain = plainTextFromContent(response.content)
      if (plain.isBlank()) {
        Result.failure(IllegalStateException("Paxsenix returned empty lyrics"))
      } else {
        Result.success(LyricsCandidate(plain, false))
      }
    }
  }

  fun plainTextFromContent(content: List<LyricsContent>): String {
    return content.mapNotNull { line ->
      line.text
        .map { it.text }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .takeIf { it.isNotBlank() }
    }.joinToString("\n")
  }

  fun lrcFromContent(content: List<LyricsContent>): String {
    val builder = StringBuilder()
    for (line in content) {
      val words = line.text
      val lineText = words.joinToString(" ") { it.text }
      if (lineText.isBlank()) continue

      builder.append(formatTimestamp(line.timestamp))
      builder.append(
        when {
          line.background -> "{bg}"
          line.oppositeTurn -> "{agent:v2}"
          else -> "{agent:v1}"
        },
      )
      builder.append(lineText).append('\n')

      if (words.any { it.text.isNotBlank() }) {
        builder.append('<')
        words.forEachIndexed { index, word ->
          if (index > 0) builder.append('|')
          builder.append(word.text)
            .append(':').append(word.timestamp / 1000.0)
            .append(':').append(word.endtime / 1000.0)
        }
        builder.append(">\n")
      }
    }
    return builder.toString()
  }

  private fun normalizeTitle(value: String): String =
    value.replace(TITLE_NOISE, "").lowercase().trim()

  private fun formatTimestamp(timeMs: Long): String {
    val clamped = timeMs.coerceAtLeast(0)
    val minutes = clamped / 60_000
    val seconds = (clamped / 1_000) % 60
    val centis = (clamped % 1_000) / 10
    return String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, centis)
  }
}
