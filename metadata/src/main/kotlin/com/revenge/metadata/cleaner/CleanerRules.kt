package com.revenge.metadata.cleaner

import com.revenge.metadata.models.VersionType

/** Regular expressions and separators used while cleaning raw YouTube Music metadata. */
object CleanerRules {

  private const val VIDEO_TAG =
    "official\\s+music\\s+video|official\\s+video|official\\s+audio|music\\s+video|" +
      "lyric\\s+video|lyrics\\s+video|official\\s+visualizer|visualizer|" +
      "video|audio|mv|pv|hd|hq|4k|1080p|720p|60fps|uhd"

  /** Bracket contents and trailing segments that only describe the upload, not the song. */
  val JUNK_PATTERNS: List<Regex> =
    listOf(
      Regex("""\s*\([^)]*\b(?:$VIDEO_TAG)\b[^)]*\)""", RegexOption.IGNORE_CASE),
      Regex("""\s*\[[^\[\]]*\b(?:$VIDEO_TAG)\b[^\[\]]*]""", RegexOption.IGNORE_CASE),
      Regex(
        """\s*【[^【】]*\b(?:official|mv|pv|video|audio|lyrics)\b[^【】]*】""",
        RegexOption.IGNORE_CASE,
      ),
      Regex(
        """\s*「[^「」]*\b(?:official|mv|pv|video|audio|lyrics)\b[^「」]*」""",
        RegexOption.IGNORE_CASE,
      ),
      Regex("""\s*\|.*$"""),
      Regex("""\s*//.*$"""),
      Regex("""\s*#\w+"""),
      Regex(
        """\s*-\s*(?:official|video|audio|lyrics|lyric|visualizer).*$""",
        RegexOption.IGNORE_CASE,
      ),
    )

  /** Placements of a featuring credit, exported for callers that parse titles themselves. */
  val FEATURING_PATTERNS: List<Regex> =
    listOf(
      Regex("""\s*\(feat\.?\s+([^()]+)\)""", RegexOption.IGNORE_CASE),
      Regex("""\s*\(ft\.?\s+([^()]+)\)""", RegexOption.IGNORE_CASE),
      Regex("""\s*\[feat\.?\s+([^\[\]]+)]""", RegexOption.IGNORE_CASE),
      Regex("""\s*\[ft\.?\s+([^\[\]]+)]""", RegexOption.IGNORE_CASE),
      Regex(
        """\s*\b(?:feat\.?|ft\.?|featuring)\s+([^()\[\]\-|]+)""",
        RegexOption.IGNORE_CASE,
      ),
    )

  /** Version markers, ordered by precedence: the first match wins. */
  val VERSION_PATTERNS: List<Pair<Regex, VersionType>> =
    listOf(
      Regex("""\b(remix|flip|bootleg|vip|re-edit|club mix|dub)\b""", RegexOption.IGNORE_CASE) to
        VersionType.REMIX,
      Regex(
        """\b(live|session|unplugged|in concert|live at [a-zA-Z0-9\s]+)\b""",
        RegexOption.IGNORE_CASE,
      ) to VersionType.LIVE,
      Regex("""\b(acoustic|orchestral|symphonic)\b""", RegexOption.IGNORE_CASE) to
        VersionType.ACOUSTIC,
      Regex("""\b(remaster|remastered|deluxe|anniversary)\b""", RegexOption.IGNORE_CASE) to
        VersionType.REMASTER,
      Regex("""\b(instrumental|karaoke)\b""", RegexOption.IGNORE_CASE) to
        VersionType.INSTRUMENTAL,
      Regex("""\b(cover)\b""", RegexOption.IGNORE_CASE) to VersionType.COVER,
      Regex("""\b(radio edit|edit)\b""", RegexOption.IGNORE_CASE) to VersionType.RADIO_EDIT,
      Regex("""\b(extended|extended mix)\b""", RegexOption.IGNORE_CASE) to VersionType.EXTENDED,
      Regex("""\b(slowed|slowed \+ reverb|reverb)\b""", RegexOption.IGNORE_CASE) to
        VersionType.SLOWED,
      Regex("""\b(sped up|nightcore)\b""", RegexOption.IGNORE_CASE) to VersionType.SPED_UP,
    )

  /** Delimiters that separate multiple artists in a single artist string. */
  val ARTIST_SEPARATORS: List<String> =
    listOf(
      " & ",
      ", ",
      " feat. ",
      " feat ",
      " ft. ",
      " ft ",
      " featuring ",
      " and ",
      " x ",
      " X ",
    )

  /** Channel-name suffixes that describe the uploader rather than the artist. */
  val CHANNEL_JUNK_SUFFIXES: List<Regex> =
    listOf(
      Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE),
      Regex("""VEVO$""", RegexOption.IGNORE_CASE),
      Regex("""\s+Records$""", RegexOption.IGNORE_CASE),
      Regex("""\s+Official$""", RegexOption.IGNORE_CASE),
    )
}
