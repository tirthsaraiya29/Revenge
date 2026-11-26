package com.revenge.metadata.cleaner

import com.revenge.metadata.models.VersionType

object CleanerRules {

    private const val JUNK_WORD =
        "(?:official\\s+music\\s+video|official\\s+video|official\\s+audio|music\\s+video|" +
            "lyrics?\\s+video|official\\s+visualizer|visualizer|video|audio|mv|pv|hd|hq|4k|" +
            "1080p|720p|60fps|uhd)"

    private const val MEDIA_MARKER = "(?:official|video|audio|lyrics?|mv|pv)"

    val JUNK_PATTERNS: List<Regex> = listOf(
        Regex("""\(\s*[^()]*\b$JUNK_WORD\b[^()]*\)""", RegexOption.IGNORE_CASE),
        Regex("""\[\s*[^\[\]]*\b$JUNK_WORD\b[^\[\]]*\]""", RegexOption.IGNORE_CASE),
        Regex("""【[^【】]*\b$MEDIA_MARKER\b[^【】]*】""", RegexOption.IGNORE_CASE),
        Regex("""「[^「」]*\b$MEDIA_MARKER\b[^「」]*」""", RegexOption.IGNORE_CASE),
        Regex("""\s*\|\s*[^|]*$"""),
        Regex("""\s*//\s*.*$"""),
        Regex("""#\w+"""),
        Regex("""\s*-\s*(?:official|video|audio|lyrics?|visualizer)\b.*$""", RegexOption.IGNORE_CASE),
    )

    val FEATURING_PATTERNS: List<Regex> = listOf(
        Regex("""\(\s*(?:feat\.?|ft\.?)\s+([^)]+)\)""", RegexOption.IGNORE_CASE),
        Regex("""\[\s*(?:feat\.?|ft\.?)\s+([^\[\]]+)\]""", RegexOption.IGNORE_CASE),
        Regex(
            """(?:^|\s)(?:feat\.?|ft\.?|featuring)\s+([^()\[\]【】「」\-|]+)""",
            RegexOption.IGNORE_CASE,
        ),
    )

    val VERSION_PATTERNS: List<Pair<Regex, VersionType>> = listOf(
        Regex(
            """\b(?:remix|flip|bootleg|vip|re-edit|club\s+mix|dub)\b""",
            RegexOption.IGNORE_CASE,
        ) to VersionType.REMIX,
        Regex(
            """\b(?:live\s+at\s+[a-z0-9][a-z0-9\s]*|live|session|unplugged|in\s+concert)\b""",
            RegexOption.IGNORE_CASE,
        ) to VersionType.LIVE,
        Regex(
            """\b(?:acoustic|orchestral|symphonic)\b""",
            RegexOption.IGNORE_CASE,
        ) to VersionType.ACOUSTIC,
        Regex(
            """\b(?:remaster(?:ed)?|deluxe|anniversary)\b""",
            RegexOption.IGNORE_CASE,
        ) to VersionType.REMASTER,
        Regex(
            """\b(?:instrumental|karaoke)\b""",
            RegexOption.IGNORE_CASE,
        ) to VersionType.INSTRUMENTAL,
        Regex(
            """\bcover\b""",
            RegexOption.IGNORE_CASE,
        ) to VersionType.COVER,
        Regex(
            """\b(?:radio\s+edit|edit)\b""",
            RegexOption.IGNORE_CASE,
        ) to VersionType.RADIO_EDIT,
        Regex(
            """\b(?:extended(?:\s+mix)?)\b""",
            RegexOption.IGNORE_CASE,
        ) to VersionType.EXTENDED,
        Regex(
            """\b(?:slowed(?:\s*\+\s*reverb)?|reverb)\b""",
            RegexOption.IGNORE_CASE,
        ) to VersionType.SLOWED,
        Regex(
            """\b(?:sped\s+up|nightcore)\b""",
            RegexOption.IGNORE_CASE,
        ) to VersionType.SPED_UP,
    )

    val ARTIST_SEPARATORS: List<String> = listOf(
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

    val CHANNEL_JUNK_SUFFIXES: List<Regex> = listOf(
        Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE),
        Regex("""\s*VEVO$""", RegexOption.IGNORE_CASE),
        Regex("""\s*Records$""", RegexOption.IGNORE_CASE),
        Regex("""\s*Official$""", RegexOption.IGNORE_CASE),
    )
}
