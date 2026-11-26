package com.revenge.metadata.cleaner

import com.revenge.metadata.models.CleanedMetadata
import com.revenge.metadata.models.VersionInfo

object MetadataCleaner {

    private val WHITESPACE = Regex("""\s+""")
    private val HASHTAG = Regex("""#\w+""")
    private val TOPIC_SUFFIX = Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE)
    private val RECORDS_SUFFIX = Regex("""\s*Records$""", RegexOption.IGNORE_CASE)
    private val OFFICIAL_SUFFIX = Regex("""\s*Official$""", RegexOption.IGNORE_CASE)
    private val BRACKET = Regex("""\(([^()]*)\)|\[([^\[\]]*)\]|【([^【】]*)】|「([^「」]*)」""")
    private val BRACKET_FEATURE = Regex("""^(?:feat\.?|ft\.?|featuring)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val HINT_WORD = Regex(
        """\b(?:official|video|visualizer|audio|mv|pv|lyrics?|remaster|hd|4k)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val ONLY_JUNK_WORD = Regex(
        """\b(?:official\s+music\s+video|official\s+video|official\s+audio|music\s+video|""" +
            """lyrics?\s+video|official\s+visualizer|visualizer|video|audio|mv|pv|hd|hq|4k|""" +
            """1080p|720p|60fps|uhd)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun clean(rawTitle: String, rawArtist: String = ""): CleanedMetadata {
        val title = sanitise(rawTitle)
        val artist = sanitise(rawArtist)

        val (peeledTitle, peeledArtist) = peelArtistPrefix(title, artist)
        val scan = scan(peeledTitle)
        val scrubbed = scrubChannel(peeledArtist)
        val tokens = splitArtists(scrubbed)

        val primaryArtist = tokens.firstOrNull().orEmpty()
        val cleanArtist = if (scrubbed.isNotBlank()) scrubbed else primaryArtist

        val featured = (scan.featured + tokens.drop(1))
            .distinct()
            .filter { it.isNotBlank() }
            .filter { !it.equals(primaryArtist, ignoreCase = true) }

        return CleanedMetadata(
            rawTitle = rawTitle,
            rawArtist = rawArtist,
            cleanTitle = normaliseTitle(scan.title),
            primaryArtist = primaryArtist,
            cleanArtist = cleanArtist,
            featuredArtists = featured,
            version = scan.version,
            junkTags = scan.junk,
        )
    }

    private class ScanResult(
        val title: String,
        val junk: List<String>,
        val featured: List<String>,
        val version: VersionInfo?,
    )

    private fun sanitise(input: String): String {
        var text = input
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

        val mapped = StringBuilder(text.length)
        for (ch in text) {
            mapped.append(
                when (ch) {
                    '‘', '’', '´', '`' -> '\''
                    '“', '”' -> '"'
                    else -> ch
                },
            )
        }
        return stripSymbols(mapped.toString()).trim()
    }

    private fun stripSymbols(input: String): String {
        val out = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            val codePoint = Character.codePointAt(input, index)
            val type = Character.getType(codePoint)
            if (type != Character.OTHER_SYMBOL.toInt() && type != Character.UNASSIGNED.toInt()) {
                out.appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
        return out.toString()
    }

    private fun peelArtistPrefix(title: String, artist: String): Pair<String, String> {
        val parts = title.split(" - ")
        if (parts.size < 2) return title to artist

        val candidateArtist = parts.first().trim()
        val candidateTitle = parts.drop(1).joinToString(" - ").trim()
        if (candidateTitle.isBlank() || looksLikeJunk(candidateTitle)) return title to artist

        val agrees = artist.isBlank() ||
            artist.contains(candidateArtist, ignoreCase = true) ||
            candidateArtist.contains(artist, ignoreCase = true) ||
            artist.contains("Topic", ignoreCase = true) ||
            artist.contains("VEVO", ignoreCase = true)

        return if (agrees) candidateTitle to candidateArtist else title to artist
    }

    private fun looksLikeJunk(candidate: String): Boolean {
        if (candidate.isEmpty()) return false
        if (candidate.first() in "([【「") return true

        val scan = scan(candidate)
        if (scan.title.isBlank()) return true
        if (CleanerRules.VERSION_PATTERNS.any { (regex, _) -> regex.matches(candidate) }) return true
        if (CleanerRules.JUNK_PATTERNS.any { it.matches(candidate) }) return true
        if (ONLY_JUNK_WORD.replace(candidate, " ").isBlank()) return true
        return false
    }

    private fun scan(input: String): ScanResult {
        val junk = mutableListOf<String>()
        val featured = mutableListOf<String>()
        var version: VersionInfo? = null

        var title = stripTrailingSuffix(input, junk)
        title = HASHTAG.replace(title) { match ->
            junk.add(match.value.trim())
            " "
        }
        title = BRACKET.replace(title) { match ->
            val content = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: ""
            processBracket(match.value, content, junk, featured) { found ->
                if (version == null) version = found
            }.let { removed -> if (removed) " " else match.value }
        }
        title = stripInlineFeaturing(title, featured)

        return ScanResult(title.trim(), junk, featured.toList(), version)
    }

    private fun processBracket(
        whole: String,
        content: String,
        junk: MutableList<String>,
        featured: MutableList<String>,
        onVersion: (VersionInfo) -> Unit,
    ): Boolean {
        val trimmed = content.trim()

        val feature = BRACKET_FEATURE.matchEntire(trimmed)
        if (feature != null) {
            featured.addAll(splitArtists(feature.groupValues[1]))
            return true
        }

        val versionMatch = CleanerRules.VERSION_PATTERNS.firstOrNull { (regex, _) ->
            regex.containsMatchIn(trimmed)
        }
        if (versionMatch != null) {
            onVersion(VersionInfo(versionMatch.second, trimmed))
            return true
        }

        val isJunk = CleanerRules.JUNK_PATTERNS.any {
            it.containsMatchIn(whole) || it.containsMatchIn(trimmed)
        }
        if (isJunk) {
            junk.add(trimmed.ifBlank { whole.trim() })
            return true
        }

        return false
    }

    private fun stripTrailingSuffix(title: String, junk: MutableList<String>): String {
        val pipe = title.indexOf('|')
        val slash = title.indexOf("//")
        val candidates = listOf(pipe to 1, slash to 2).filter { it.first >= 0 }
        if (candidates.isEmpty()) return title
        val (index, length) = candidates.minBy { it.first }

        val trailing = title.substring(index + length).trim()
        if (trailing.isEmpty()) return title
        if (!isJunkOrHint(trailing)) return title

        junk.add(trailing)
        return title.substring(0, index)
    }

    private fun isJunkOrHint(text: String): Boolean =
        HINT_WORD.containsMatchIn(text) || CleanerRules.JUNK_PATTERNS.any { it.containsMatchIn(text) }

    private fun stripInlineFeaturing(title: String, featured: MutableList<String>): String {
        val match = CleanerRules.FEATURING_PATTERNS[2].find(title) ?: return title
        featured.addAll(splitArtists(match.groupValues[1]))
        return title.replaceRange(match.range, " ")
    }

    private fun scrubChannel(input: String): String {
        var text = input.trim()
        text = TOPIC_SUFFIX.replace(text, "").trim()

        if (text.endsWith("VEVO", ignoreCase = true)) {
            text = camelCase(text.dropLast(4).trim())
        }

        text = RECORDS_SUFFIX.replace(text, "").trim()
        text = OFFICIAL_SUFFIX.replace(text, "").trim()
        return text
    }

    private fun camelCase(input: String): String {
        if (input.contains(' ')) return input
        if (input.none { it.isLowerCase() } || input.none { it.isUpperCase() }) return input

        val out = StringBuilder(input.length + 4)
        input.forEachIndexed { index, ch ->
            if (index > 0 && ch.isUpperCase() && input[index - 1].isLowerCase()) out.append(' ')
            out.append(ch)
        }
        return out.toString()
    }

    private fun splitArtists(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        var tokens = listOf(input)
        for (separator in CleanerRules.ARTIST_SEPARATORS) {
            tokens = tokens.flatMap { it.split(separator) }
        }
        return tokens.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun normaliseTitle(input: String): String =
        input.replace(WHITESPACE, " ")
            .trim()
            .trimEnd('-', '–', '—', '|', '/')
            .trim()
}
