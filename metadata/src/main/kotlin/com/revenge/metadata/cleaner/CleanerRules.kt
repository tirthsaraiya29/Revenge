package com.revenge.metadata.cleaner

import com.revenge.metadata.models.VersionType

/**
 * Public rule tables used by [MetadataCleaner] and available to callers that parse
 * titles themselves. The keyword sets and their order are API; the concrete regex
 * source is not.
 *
 * Every table is assembled from plain token lists below so the vocabulary lives in
 * one place and the matchers are derived from it.
 */
object CleanerRules {

    private val CASE = RegexOption.IGNORE_CASE

    // ---------------------------------------------------------------------
    // Derivation helpers
    // ---------------------------------------------------------------------

    /** Negative/positive lookarounds that approximate a Unicode-aware word edge. */
    private const val EDGE_BEFORE = """(?<![\p{L}\p{N}])"""
    private const val EDGE_AFTER = """(?![\p{L}\p{N}])"""

    /** Longest-first alternation so compound phrases win over their prefixes. */
    private fun alternatives(words: List<String>): String =
        words.sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }

    private fun wordMatcher(words: List<String>): Regex =
        Regex(EDGE_BEFORE + "(?:" + alternatives(words) + ")" + EDGE_AFTER, CASE)

    /** Like [wordMatcher] but also accepts caller-supplied raw pattern fragments. */
    private fun versionMatcher(words: List<String>, raw: List<String> = emptyList()): Regex {
        val alternation =
            alternatives(words) + raw.joinToString(separator = "", prefix = if (raw.isEmpty()) "" else "|")
        return Regex(EDGE_BEFORE + "(?:" + alternation + ")" + EDGE_AFTER, CASE)
    }

    // ---------------------------------------------------------------------
    // Vocabulary
    // ---------------------------------------------------------------------

    /** Upload/quality words that describe the video, never the song. */
    internal val UPLOAD_WORDS: List<String> = listOf(
        "official music video", "official video", "official audio",
        "music video", "lyric video", "lyrics video", "official visualizer",
        "visualizer", "video", "audio", "mv", "pv", "hd", "hq", "4k",
        "1080p", "720p", "60fps", "uhd",
    )

    private val uploadMatchers: List<Regex> =
        UPLOAD_WORDS.sortedByDescending { it.length }
            .map { Regex(EDGE_BEFORE + Regex.escape(it) + EDGE_AFTER, CASE) }

    /** Hint words that make a trailing `|` / `//` tail disposable. */
    private val SUFFIX_HINTS: List<String> = listOf(
        "official", "video", "visualizer", "audio", "mv", "pv",
        "lyric", "lyrics", "remaster", "hd", "4k",
    )

    /** Markers accepted inside CJK/Japanese corner brackets. */
    private val CORNER_MARKERS: List<String> = listOf(
        "official", "video", "audio", "lyric", "lyrics",
    )

    /** Words that can open a trailing `- <marker> ...` upload segment. */
    private val TRAILING_MARKERS: List<String> = listOf(
        "official", "video", "audio", "lyric", "visualizer",
    )

    private val uploadEdge: String =
        EDGE_BEFORE + "(?:" + alternatives(UPLOAD_WORDS) + ")" + EDGE_AFTER
    private val cornerEdge: String =
        EDGE_BEFORE + "(?:" + alternatives(CORNER_MARKERS) + ")" + EDGE_AFTER
    private val trailingEdge: String =
        EDGE_BEFORE + "(?:" + alternatives(TRAILING_MARKERS) + ")" + EDGE_AFTER

    // ---------------------------------------------------------------------
    // Public tables
    // ---------------------------------------------------------------------

    /**
     * Upload/description fragments, in the documented semantic order:
     * parentheses, square brackets, `【】`, `「」`, trailing `|`, trailing `//`,
     * hashtags, trailing `- <marker>`.
     */
    val JUNK_PATTERNS: List<Regex> = listOf(
        Regex("""\([^()]*${uploadEdge}[^()]*\)""", CASE),
        Regex("""\[[^\[\]]*${uploadEdge}[^\[\]]*\]""", CASE),
        Regex("""【[^】]*${cornerEdge}[^】]*】""", CASE),
        Regex("""「[^」]*${cornerEdge}[^」]*」""", CASE),
        Regex("""\s*\|.*$"""),
        Regex("""\s*//.*$"""),
        Regex("""#\w+"""),
        Regex("""\s*-\s*${trailingEdge}.*$""", CASE),
    )

    /**
     * Featuring-credit locators. Capture group 1 always holds the raw artist text.
     */
    val FEATURING_PATTERNS: List<Regex> = listOf(
        Regex("""\(\s*(?:feat\.?|ft\.?)\s+([^)]*)\)""", CASE),
        Regex("""\[\s*(?:feat\.?|ft\.?)\s+([^\]\[]*)\]""", CASE),
        Regex("""(?:^|\s)(?:feat\.?|ft\.?|featuring)\s+([^()\[\]{|\-]+)""", CASE),
    )

    /**
     * Version-marker precedence table. The list order is the precedence order:
     * the first matching pair wins.
     */
    val VERSION_PATTERNS: List<Pair<Regex, VersionType>> =
        listOf(
            wordMatcher(listOf("remix", "flip", "bootleg", "vip", "re-edit", "club mix", "dub")) to
                VersionType.REMIX,
            versionMatcher(
                listOf("live", "session", "unplugged", "in concert"),
                raw = listOf("""live at [\p{L}\p{N} ]+"""),
            ) to VersionType.LIVE,
            wordMatcher(listOf("acoustic", "orchestral", "symphonic")) to VersionType.ACOUSTIC,
            wordMatcher(listOf("remaster", "remastered", "deluxe", "anniversary")) to
                VersionType.REMASTER,
            wordMatcher(listOf("instrumental", "karaoke")) to VersionType.INSTRUMENTAL,
            wordMatcher(listOf("cover")) to VersionType.COVER,
            wordMatcher(listOf("radio edit", "edit")) to VersionType.RADIO_EDIT,
            wordMatcher(listOf("extended", "extended mix")) to VersionType.EXTENDED,
            versionMatcher(
                listOf("slowed", "reverb"),
                raw = listOf("""slowed\s*\+\s*reverb"""),
            ) to VersionType.SLOWED,
            wordMatcher(listOf("sped up", "nightcore")) to VersionType.SPED_UP,
        )

    /** Exact-substring multi-artist delimiters; order and content are API. */
    val ARTIST_SEPARATORS: List<String> = listOf(
        " & ", ", ", " feat. ", " feat ", " ft. ", " ft ",
        " featuring ", " and ", " x ", " X ",
    )

    /** Uploader suffixes stripped from the trailing edge of a channel name. */
    val CHANNEL_JUNK_SUFFIXES: List<Regex> = listOf(
        Regex("""\s*-\s*Topic\s*$""", CASE),
        Regex("""\s*VEVO\s*$""", CASE),
        Regex("""\s*Records\s*$""", CASE),
        Regex("""\s*Official\s*$""", CASE),
    )

    // ---------------------------------------------------------------------
    // Internal helpers shared with MetadataCleaner (not part of the ABI)
    // ---------------------------------------------------------------------

    internal val SUFFIX_HINT_MATCHER: Regex = wordMatcher(SUFFIX_HINTS)

    internal val BRACKET_FEATURE_PREFIX: Regex =
        Regex("""^\s*(?:feat\.?|ft\.?|featuring)\s+(.+)$""", CASE)

    /** True when [text] is made up exclusively of upload/quality vocabulary. */
    internal fun isOnlyUploadWords(text: String): Boolean {
        if (text.isBlank()) return false
        var rest = text
        for (matcher in uploadMatchers) rest = matcher.replace(rest, " ")
        return rest.isBlank()
    }
}
