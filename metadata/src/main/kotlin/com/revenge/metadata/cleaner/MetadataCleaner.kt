package com.revenge.metadata.cleaner

import com.revenge.metadata.models.CleanedMetadata
import com.revenge.metadata.models.VersionInfo

/**
 * Turns raw YouTube Music title/artist strings into structured metadata.
 *
 * The pipeline is: decode and sanitise -> peel an `"Artist - Title"` prefix -> extract bracket
 * tags (featuring, version, junk) and trailing junk -> strip channel suffixes -> split multiple
 * artists. Every stage is idempotent on already-clean input.
 */
object MetadataCleaner {

  private val DASH_SPLIT = Regex("""\s+-\s+""")
  private val PIPE_SUFFIX = Regex("""\s*(?:\||//)\s*(.*)$""")
  private val HASHTAG = Regex("""\s*#\w+""")
  private val BRACKET =
    Regex("""(?:\(([^()]*)\)|\[([^\[\]]*)]|【([^【】]*)】|「([^「」]*)」)""")
  private val FEATURE_AT_START = Regex("""^(?:feat\.?|ft\.?|featuring)\s+(.+)$""", RegexOption.IGNORE_CASE)
  private val INLINE_FEATURE =
    Regex("""(?:\s+|^)(?:feat\.?|ft\.?|featuring)\s+([^()\[\]\-|]+)""", RegexOption.IGNORE_CASE)
  private val OFFICIAL_ONLY =
    Regex(
      """\b(?:official\s+music\s+video|official\s+video|official\s+audio|music\s+video|""" +
        """lyric\s+video|lyrics\s+video|official\s+visualizer|visualizer|video|audio|""" +
        """mv|pv|hd|hq|4k|1080p|720p|60fps|uhd)\b""",
      RegexOption.IGNORE_CASE,
    )
  private val SUFFIX_HINT =
    Regex("""\b(?:official|video|visualizer|audio|mv|pv|lyrics?|remaster|hd|4k)\b""", RegexOption.IGNORE_CASE)
  private val TOPIC_SUFFIX = Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE)
  private val RECORDS_SUFFIX = Regex("""\s+Records$""", RegexOption.IGNORE_CASE)
  private val OFFICIAL_SUFFIX = Regex("""\s+Official$""", RegexOption.IGNORE_CASE)
  private val CAMEL_BOUNDARY = Regex("(?<=[a-z])(?=[A-Z])")
  private val WHITESPACE = Regex("""\s+""")
  private val SMART_SINGLE_QUOTES = Regex("[‘’´`]")
  private val SMART_DOUBLE_QUOTES = Regex("[“”]")
  private val EMOJI_AND_UNASSIGNED = Regex("[\\p{So}\\p{Cn}]")

  fun clean(rawTitle: String, rawArtist: String = ""): CleanedMetadata {
    val sanitizedTitle = sanitizeUnicode(rawTitle)
    val sanitizedArtist = sanitizeUnicode(rawArtist)

    val (peeledTitle, peeledArtist) = peelArtistPrefix(sanitizedTitle, sanitizedArtist)
    val scan = scanTags(peeledTitle)
    val artist = scrubChannelJunk(peeledArtist)

    val artistParts = splitArtistNames(artist)
    val primaryArtist = artistParts.first
    val featured =
      (scan.featured + artistParts.second)
        .distinct()
        .filter { it.isNotBlank() && !it.equals(primaryArtist, ignoreCase = true) }

    return CleanedMetadata(
      rawTitle = rawTitle,
      rawArtist = rawArtist,
      cleanTitle = normalizeTitle(scan.title),
      primaryArtist = primaryArtist,
      cleanArtist = artist.ifBlank { primaryArtist },
      featuredArtists = featured,
      version = scan.version,
      junkTags = scan.junk,
    )
  }

  private fun sanitizeUnicode(raw: String): String =
    raw
      .replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&apos;", "'")
      .replace("&#39;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace(SMART_SINGLE_QUOTES, "'")
      .replace(SMART_DOUBLE_QUOTES, "\"")
      .replace(EMOJI_AND_UNASSIGNED, "")
      .trim()

  /**
   * Splits `"Artist - Title"` when the right-hand side is a real title. When an artist is already
   * known we only accept the split if the left side agrees with it (or comes from a Topic/VEVO
   * channel); otherwise the dash is part of the title. A right-hand side that is only a version or
   * junk marker never becomes the title.
   */
  private fun peelArtistPrefix(title: String, artist: String): Pair<String, String> {
    val parts = title.split(DASH_SPLIT)
    if (parts.size < 2) return title to artist

    val candidateArtist = parts.first().trim()
    val candidateTitle = parts.drop(1).joinToString(" - ").trim()
    if (candidateTitle.isBlank() || looksLikeJunk(candidateTitle)) return title to artist

    val matchesKnownArtist =
      artist.isNotBlank() &&
        (candidateArtist.contains(artist, ignoreCase = true) ||
          artist.contains(candidateArtist, ignoreCase = true) ||
          artist.contains("Topic", ignoreCase = true) ||
          artist.contains("VEVO", ignoreCase = true))

    return when {
      artist.isBlank() -> candidateTitle to candidateArtist
      matchesKnownArtist -> candidateTitle to candidateArtist
      else -> title to artist
    }
  }

  private fun looksLikeJunk(candidate: String): Boolean =
    candidate.startsWith("(") ||
      candidate.startsWith("[") ||
      candidate.startsWith("【") ||
      candidate.startsWith("「") ||
      scanTags(candidate).title.isBlank() ||
      CleanerRules.VERSION_PATTERNS.any { (pattern, _) -> pattern.matches(candidate) } ||
      CleanerRules.JUNK_PATTERNS.any { it.matches(candidate) } ||
      OFFICIAL_ONLY.matches(candidate)

  private data class TagScan(
    val title: String,
    val junk: List<String>,
    val featured: List<String>,
    val version: VersionInfo?,
  )

  private fun scanTags(input: String): TagScan {
    var title = input
    val junk = mutableListOf<String>()
    val featured = mutableListOf<String>()
    var version: VersionInfo? = null

    // Trailing "| ..." or "// ..." when it only describes the upload.
    PIPE_SUFFIX.find(title)?.let { match ->
      val trailing = match.groupValues[1].trim()
      if (CleanerRules.JUNK_PATTERNS.any { it.containsMatchIn(trailing) } || SUFFIX_HINT.containsMatchIn(trailing)) {
        junk += trailing
        title = title.substring(0, match.range.first).trim()
      }
    }

    HASHTAG.findAll(title).forEach { junk += it.value.trim() }
    title = title.replace(HASHTAG, "").trim()

    for (match in BRACKET.findAll(title).toList()) {
      val whole = match.value
      val content =
        (match.groups[1]?.value
            ?: match.groups[2]?.value
            ?: match.groups[3]?.value
            ?: match.groups[4]?.value
            ?: "")
          .trim()

      val feature = FEATURE_AT_START.find(content)
      if (feature != null) {
        val (first, others) = splitArtistNames(feature.groupValues[1].trim())
        if (first.isNotBlank()) featured += first
        featured += others
        title = title.replaceFirst(whole, " ")
        continue
      }

      val versionMatch = CleanerRules.VERSION_PATTERNS.firstOrNull { it.first.containsMatchIn(content) }
      if (versionMatch != null) {
        if (version == null) version = VersionInfo(versionMatch.second, content)
        title = title.replaceFirst(whole, " ")
        continue
      }

      if (CleanerRules.JUNK_PATTERNS.any { it.containsMatchIn(whole) || it.containsMatchIn(content) }) {
        junk += content.ifBlank { whole }
        title = title.replaceFirst(whole, " ")
      }
    }

    INLINE_FEATURE.find(title)?.let { match ->
      val (first, others) = splitArtistNames(match.groupValues[1].trim())
      if (first.isNotBlank()) featured += first
      featured += others
      title = title.removeRange(match.range).trim()
    }

    return TagScan(title.trim(), junk, featured, version)
  }

  private fun scrubChannelJunk(raw: String): String {
    var artist = raw.trim()
    artist = artist.replace(TOPIC_SUFFIX, "").trim()
    if (artist.endsWith("VEVO", ignoreCase = true)) {
      artist = splitCamelCase(artist.dropLast(4).trim())
    }
    artist = artist.replace(RECORDS_SUFFIX, "").replace(OFFICIAL_SUFFIX, "").trim()
    return artist
  }

  private fun splitCamelCase(text: String): String {
    if (text.contains(' ') || text.all { it.isUpperCase() } || text.all { it.isLowerCase() }) {
      return text
    }
    return text.replace(CAMEL_BOUNDARY, " ")
  }

  private fun splitArtistNames(raw: String): Pair<String, List<String>> {
    if (raw.isBlank()) return "" to emptyList()

    var tokens = listOf(raw)
    for (separator in CleanerRules.ARTIST_SEPARATORS) {
      tokens = tokens.flatMap { it.split(separator) }
    }

    val names = tokens.map { it.trim() }.filter { it.isNotEmpty() }
    return if (names.isEmpty()) "" to emptyList() else names.first() to names.drop(1)
  }

  private fun normalizeTitle(title: String): String =
    title.replace(WHITESPACE, " ").trim().trimEnd('-', '–', '—', '|', '/').trim()
}
