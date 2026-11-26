package com.revenge.paxsenix

import com.revenge.betterlyrics.TTMLParser
import com.revenge.paxsenix.models.LyricsContent
import com.revenge.paxsenix.models.LyricsResponse
import com.revenge.paxsenix.models.SearchResult
import java.util.Locale
import kotlin.math.abs
import timber.log.Timber

/** The track the app is looking for, in both raw and cleaned form. */
internal data class TrackTarget(
  val title: String,
  val cleanTitle: String,
  val cleanArtist: String,
  val durationSeconds: Int,
)

internal data class ScoredResult(val result: SearchResult, val score: Double)

internal data class LyricsCandidate(val lyrics: String, val hasWordTimings: Boolean)

/**
 * Pure matching/formatting logic, kept independent of Android and networking so it can be unit
 * tested directly.
 */
internal object PaxsenixCore {
  const val MAX_RANKED_RESULTS = 10
  const val MAX_GET_LYRICS_CANDIDATES = 5
  const val MAX_ALL_LYRICS_CANDIDATES = 3

  private const val SYLLABLE_TYPE = "Syllable"
  private const val DURATION_CLOSE_MS = 2_000L
  private const val DURATION_NEAR_MS = 5_000L
  private const val DURATION_OFF_MS = 10_000L

  private val BRACKETED_TAG = Regex("""\s*\([^)]*\)|\s*\[[^\]]*\]""")
  private val WORD_TIMING = Regex("""<[^>]+>""")
  private val WHITESPACE = Regex("""\s+""")

  /**
   * Search attempts, most specific first. The album variant is only useful when an album is known;
   * duplicates are removed so a single network call is not repeated.
   */
  fun searchQueries(cleanTitle: String, cleanArtist: String, album: String?): List<String> {
    val combined = listOf(cleanTitle, cleanArtist).filter { it.isNotBlank() }.joinToString(" ")
    return buildList {
      add(combined)
      add(cleanTitle)
      if (!album.isNullOrBlank()) {
        add(listOf(cleanTitle, cleanArtist, album).filter { it.isNotBlank() }.joinToString(" "))
      }
    }
      .filter { it.isNotBlank() }
      .distinct()
  }

  fun rank(results: List<SearchResult>, target: TrackTarget): List<ScoredResult> {
    return results
      .filter { it.id.isNotBlank() }
      .map { ScoredResult(it, score(it, target)) }
      .filter { it.score > 0.0 }
      .sortedByDescending { it.score }
      .take(MAX_RANKED_RESULTS)
  }

  fun score(result: SearchResult, target: TrackTarget): Double {
    var score = 0.0

    result.duration?.let { durationMs ->
      val diff = abs(durationMs.toLong() - target.durationSeconds.toLong() * 1_000L)
      score +=
        when {
          diff <= DURATION_CLOSE_MS -> 100.0
          diff <= DURATION_NEAR_MS -> 50.0
          diff <= DURATION_OFF_MS -> 10.0
          else -> -50.0
        }
    }

    val targetTitle = normalizedTitle(target.cleanTitle)
    val resultTitle = normalizedTitle(result.displayName)
    if (targetTitle.isNotEmpty() && resultTitle.isNotEmpty()) {
      when {
        resultTitle == targetTitle -> score += 80.0
        resultTitle.contains(targetTitle) || targetTitle.contains(resultTitle) -> score += 40.0
      }
    }

    // Penalise alternate versions the caller did not ask for.
    if (containsVersion(result.displayName, "mixed") && !containsVersion(target.title, "mixed")) {
      score -= 60.0
    }
    if (containsVersion(result.displayName, "remix") && !containsVersion(target.title, "remix")) {
      score -= 40.0
    }

    val targetArtist = target.cleanArtist.lowercase().trim()
    if (targetArtist.isNotEmpty()) {
      val resultArtist = result.displayArtist.lowercase()
      if (resultArtist.contains(targetArtist)) {
        score += 50.0
      } else if (targetArtist.split(WHITESPACE).any { it.length > 2 && resultArtist.contains(it) }) {
        score += 25.0
      }
    }

    return score
  }

  /** Detects enhanced LRC word timings, which are emitted as `<word:start:end|...>`. */
  fun hasWordTimings(text: String): Boolean = WORD_TIMING.containsMatchIn(text)

  fun buildLyrics(
    response: LyricsResponse,
    ttmlConverter: (String) -> String = ::convertTtmlToLrc,
  ): Result<LyricsCandidate> {
    response.ttmlContent?.takeIf { it.isNotBlank() }?.let { ttml ->
      val lrc = ttmlConverter(ttml)
      if (lrc.isNotBlank()) {
        return Result.success(LyricsCandidate(lrc, hasWordTimings(lrc)))
      }
    }

    response.elrcMultiPerson?.takeIf { it.isNotBlank() }?.let { elrc ->
      return Result.success(LyricsCandidate(elrc, hasWordTimings(elrc)))
    }
    response.elrc?.takeIf { it.isNotBlank() }?.let { elrc ->
      return Result.success(LyricsCandidate(elrc, hasWordTimings(elrc)))
    }
    response.plain?.takeIf { it.isNotBlank() }?.let { plain ->
      return Result.success(LyricsCandidate(plain, false))
    }

    if (response.content.isEmpty()) {
      return Result.failure(IllegalStateException("Paxsenix returned no lyrics"))
    }

    val candidate =
      if (response.type.equals(SYLLABLE_TYPE, ignoreCase = true)) {
        val lrc = lrcFromContent(response.content)
        if (lrc.isBlank()) null else LyricsCandidate(lrc, true)
      } else {
        val text = plainTextFromContent(response.content)
        if (text.isBlank()) null else LyricsCandidate(text, false)
      }

    return candidate
      ?.let { Result.success(it) }
      ?: Result.failure(IllegalStateException("Paxsenix returned empty lyrics"))
  }

  fun plainTextFromContent(content: List<LyricsContent>): String {
    return content
      .map { line -> line.text.joinToString(" ") { it.text } }
      .filter { it.isNotBlank() }
      .joinToString("\n")
  }

  fun lrcFromContent(content: List<LyricsContent>): String = buildString {
    content.forEach { line ->
      val lineText = line.text.joinToString(" ") { it.text }
      if (lineText.isBlank()) return@forEach

      append(formatTimestamp(line.timestamp))
      append(agentTag(line))
      append(lineText)
      append('\n')

      val words = line.text.filter { it.text.isNotBlank() }
      if (words.isNotEmpty()) {
        append('<')
        append(words.joinToString("|") { word -> "${word.text}:${seconds(word.timestamp)}:${seconds(word.endtime)}" })
        append(">\n")
      }
    }
  }

  private fun convertTtmlToLrc(ttml: String): String {
    return runCatching { TTMLParser.toLRC(TTMLParser.parseTTML(ttml)) }
      .onFailure { Timber.w(it, "Paxsenix TTML conversion failed") }
      .getOrDefault("")
  }

  private fun normalizedTitle(title: String): String {
    return title.replace(BRACKETED_TAG, "").lowercase().trim()
  }

  private fun containsVersion(text: String, version: String): Boolean {
    return text.contains(version, ignoreCase = true)
  }

  private fun formatTimestamp(timeMs: Long): String {
    val ms = timeMs.coerceAtLeast(0L)
    return String.format(
      Locale.US,
      "[%02d:%02d.%02d]",
      ms / 60_000L,
      (ms % 60_000L) / 1_000L,
      (ms % 1_000L) / 10L,
    )
  }

  private fun seconds(timeMs: Long): Double = timeMs / 1_000.0

  private fun agentTag(line: LyricsContent): String =
    when {
      line.background -> "{bg}"
      line.oppositeTurn -> "{agent:v2}"
      else -> "{agent:v1}"
    }
}
