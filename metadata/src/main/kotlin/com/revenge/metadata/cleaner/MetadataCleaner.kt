package com.revenge.metadata.cleaner

import com.revenge.metadata.models.CleanedMetadata
import com.revenge.metadata.models.VersionInfo

/**
 * Normalises raw YouTube Music / InnerTube title and artist strings.
 *
 * The work is organised as a fixed sequence of small stages over a mutable draft: sanitise,
 * optional `Artist - Title` peel, tag extraction, channel scrub, artist split, title normalisation,
 * assembly. Each stage is a pure function of its input plus the draft it is handed, so the whole
 * object remains stateless.
 */
object MetadataCleaner {

  private const val DASH = " - "
  private val HASHTAG = Regex("""#\w+""")
  private val WHITESPACE = Regex("""\s+""")
  private val TITLE_EDGE = charArrayOf('-', '–', '—', '|', '/')
  private val NAMED_ENTITIES =
    listOf(
      "&amp;" to '&',
      "&quot;" to '"',
      "&apos;" to '\'',
      "&lt;" to '<',
      "&gt;" to '>',
    )

  fun clean(rawTitle: String, rawArtist: String = ""): CleanedMetadata {
    val title = sanitise(rawTitle)
    val artist = sanitise(rawArtist)

    val attribution = peel(title, artist)
    val extraction = extract(attribution.title)

    val scrubbedArtist = scrubChannel(attribution.artist)
    val artists = splitArtists(scrubbedArtist)
    val primaryArtist = artists.firstOrNull().orEmpty()
    val otherArtists = if (artists.size > 1) artists.subList(1, artists.size) else emptyList()

    val featured = LinkedHashSet<String>()
    for (candidate in extraction.featured + otherArtists) {
      if (candidate.isBlank()) continue
      if (candidate.equals(primaryArtist, ignoreCase = true)) continue
      featured.add(candidate)
    }

    return CleanedMetadata(
      rawTitle = rawTitle,
      rawArtist = rawArtist,
      cleanTitle = normaliseTitle(extraction.title),
      primaryArtist = primaryArtist,
      cleanArtist = scrubbedArtist.ifBlank { primaryArtist },
      featuredArtists = featured.toList(),
      version = extraction.version,
      junkTags = extraction.junk,
    )
  }

  // ---------------------------------------------------------------------
  // Stage 0: Unicode / entity sanitisation (single character scan)
  // ---------------------------------------------------------------------

  private fun sanitise(raw: String): String {
    val out = StringBuilder(raw.length)
    var index = 0
    while (index < raw.length) {
      val codePoint = raw.codePointAt(index)
      val width = Character.charCount(codePoint)

      val entity = if (codePoint == '&'.code) entityAt(raw, index) else null
      if (entity != null) {
        out.append(entity.second)
        index = entity.first
        continue
      }

      val quote = typographicQuote(codePoint)
      when {
        quote != null -> out.append(quote)
        isRemovable(codePoint) -> Unit
        else -> out.appendCodePoint(codePoint)
      }
      index += width
    }
    return out.toString().trim()
  }

  /** Returns (index after the entity, replacement char) or null. */
  private fun entityAt(text: String, start: Int): Pair<Int, Char>? {
    if (text.startsWith("&#39;", start)) return (start + 5) to '\''
    for ((literal, replacement) in NAMED_ENTITIES) {
      if (text.startsWith(literal, start)) return (start + literal.length) to replacement
    }
    return null
  }

  private fun typographicQuote(codePoint: Int): Char? =
    when (codePoint) {
      '‘'.code,
      '’'.code,
      '´'.code,
      '`'.code -> '\''
      '“'.code,
      '”'.code -> '"'
      else -> null
    }

  private fun isRemovable(codePoint: Int): Boolean {
    val category = Character.getType(codePoint)
    return category == Character.OTHER_SYMBOL.toInt() || category == Character.UNASSIGNED.toInt()
  }

  // ---------------------------------------------------------------------
  // Stage 1: `Artist - Title` peel
  // ---------------------------------------------------------------------

  private data class Attribution(val title: String, val artist: String)

  private fun peel(title: String, artist: String): Attribution {
    val split = title.indexOf(DASH)
    if (split < 0) return Attribution(title, artist)

    val candidateArtist = title.substring(0, split).trim()
    val candidateTitle = title.substring(split + DASH.length).trim()
    if (candidateTitle.isBlank()) return Attribution(title, artist)
    if (isJunkOnly(candidateTitle)) return Attribution(title, artist)

    val agrees =
      artist.isBlank() ||
        candidateArtist.contains(artist, ignoreCase = true) ||
        artist.contains(candidateArtist, ignoreCase = true) ||
        artist.contains("Topic", ignoreCase = true) ||
        artist.contains("VEVO", ignoreCase = true)

    return if (agrees) Attribution(candidateTitle, candidateArtist) else Attribution(title, artist)
  }

  /** Mirrors the §6.3 rejection test for a right-hand-side candidate title. */
  private fun isJunkOnly(text: String): Boolean {
    if (text.isBlank()) return true
    val opener = text.first()
    if (opener == '(' || opener == '[' || opener == '【' || opener == '「') return true
    if (extract(text).title.isBlank()) return true
    if (CleanerRules.VERSION_PATTERNS.any { (pattern, _) -> pattern.matches(text) }) return true
    if (CleanerRules.JUNK_PATTERNS.any { it.matches(text) }) return true
    return CleanerRules.isOnlyUploadWords(text)
  }

  // ---------------------------------------------------------------------
  // Stage 2: tag extraction (pipe tail, hashtags, brackets, inline feat)
  // ---------------------------------------------------------------------

  private class Draft(title: String) {
    var title: String = title
    val junk = ArrayList<String>()
    val featured = ArrayList<String>()
    var version: VersionInfo? = null
  }

  private class Extraction(
    val title: String,
    val junk: List<String>,
    val featured: List<String>,
    val version: VersionInfo?,
  )

  private fun extract(raw: String): Extraction {
    val draft = Draft(raw)
    stripTrailingSuffix(draft)
    stripHashtags(draft)
    stripBracketGroups(draft)
    stripInlineFeaturing(draft)
    return Extraction(
      draft.title.trim(),
      draft.junk.toList(),
      draft.featured.toList(),
      draft.version,
    )
  }

  private fun stripTrailingSuffix(draft: Draft) {
    val text = draft.title
    val pipe = text.indexOf('|')
    val slashes = text.indexOf("//")
    val cut =
      when {
        pipe < 0 -> slashes
        slashes < 0 -> pipe
        else -> minOf(pipe, slashes)
      }
    if (cut < 0) return

    val consumed = if (cut == pipe) 1 else 2
    val tail = text.substring(cut + consumed).trim()
    if (tail.isEmpty()) return

    val disposable =
      CleanerRules.JUNK_PATTERNS.any { it.containsMatchIn(tail) } ||
        CleanerRules.SUFFIX_HINT_MATCHER.containsMatchIn(tail)
    if (!disposable) return

    draft.junk.add(tail)
    draft.title = text.substring(0, cut)
  }

  private fun stripHashtags(draft: Draft) {
    val matches = HASHTAG.findAll(draft.title).toList()
    if (matches.isEmpty()) return
    matches.forEach { draft.junk.add(it.value) }
    draft.title = HASHTAG.replace(draft.title, " ")
  }

  private fun stripBracketGroups(draft: Draft) {
    val text = draft.title
    val out = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
      val closing = closingFor(text[index])
      if (closing == null) {
        out.append(text[index])
        index++
        continue
      }
      val end = matchingClose(text, index, text[index], closing)
      if (end < 0) {
        out.append(text[index])
        index++
        continue
      }
      val group = text.substring(index, end + 1)
      val inner = text.substring(index + 1, end).trim()
      if (classifyGroup(inner, group, draft)) {
        out.append(' ')
      } else {
        out.append(group)
      }
      index = end + 1
    }
    draft.title = out.toString()
  }

  private fun closingFor(opener: Char): Char? =
    when (opener) {
      '(' -> ')'
      '[' -> ']'
      '【' -> '】'
      '「' -> '」'
      else -> null
    }

  private fun matchingClose(text: String, start: Int, opener: Char, closing: Char): Int {
    var depth = 0
    var cursor = start
    while (cursor < text.length) {
      when (text[cursor]) {
        opener -> depth++
        closing -> {
          depth--
          if (depth == 0) return cursor
        }
      }
      cursor++
    }
    return -1
  }

  /** Returns true when the group is consumed (removed). */
  private fun classifyGroup(inner: String, group: String, draft: Draft): Boolean {
    val feature = CleanerRules.BRACKET_FEATURE_PREFIX.matchEntire(inner)
    if (feature != null) {
      draft.featured.addAll(splitArtists(feature.groupValues[1]))
      return true
    }

    val version =
      CleanerRules.VERSION_PATTERNS.firstOrNull { (pattern, _) ->
        pattern.containsMatchIn(inner)
      }
    if (version != null) {
      if (draft.version == null) draft.version = VersionInfo(version.second, inner)
      return true
    }

    val junk =
      CleanerRules.JUNK_PATTERNS.any {
        it.containsMatchIn(group) || it.containsMatchIn(inner)
      }
    if (junk) {
      draft.junk.add(inner.ifBlank { group })
      return true
    }
    return false
  }

  private fun stripInlineFeaturing(draft: Draft) {
    val match = CleanerRules.FEATURING_PATTERNS[2].find(draft.title) ?: return
    draft.featured.addAll(splitArtists(match.groupValues[1]))
    draft.title = draft.title.removeRange(match.range)
  }

  // ---------------------------------------------------------------------
  // Stage 3: channel scrubbing
  // ---------------------------------------------------------------------

  private fun scrubChannel(raw: String): String {
    var name = raw.trim()
    name = CleanerRules.CHANNEL_JUNK_SUFFIXES[0].replace(name, "").trim()

    val vevo = CleanerRules.CHANNEL_JUNK_SUFFIXES[1].find(name)
    if (vevo != null && vevo.range.last == name.lastIndex) {
      name = splitCamelWords(name.substring(0, vevo.range.first).trim())
    }

    name = CleanerRules.CHANNEL_JUNK_SUFFIXES[2].replace(name, "").trim()
    name = CleanerRules.CHANNEL_JUNK_SUFFIXES[3].replace(name, "").trim()
    return name
  }

  /** Splits `TaylorSwift`-style names only when they are mixed-case and spaceless. */
  private fun splitCamelWords(name: String): String {
    if (name.isEmpty() || name.contains(' ')) return name
    var hasUpper = false
    var hasLower = false
    for (ch in name) {
      if (Character.isUpperCase(ch)) hasUpper = true
      if (Character.isLowerCase(ch)) hasLower = true
    }
    if (!(hasUpper && hasLower)) return name

    val out = StringBuilder(name.length + 4)
    for (index in name.indices) {
      val ch = name[index]
      if (index > 0 && Character.isUpperCase(ch) && Character.isLowerCase(name[index - 1])) {
        out.append(' ')
      }
      out.append(ch)
    }
    return out.toString()
  }

  // ---------------------------------------------------------------------
  // Stage 4: artist splitting (simultaneous exact-substring delimiters)
  // ---------------------------------------------------------------------

  private fun splitArtists(value: String): List<String> {
    if (value.isBlank()) return emptyList()
    val separators = CleanerRules.ARTIST_SEPARATORS
    val tokens = ArrayList<String>()
    val current = StringBuilder()
    var index = 0
    while (index < value.length) {
      val separator = separators.firstOrNull { value.startsWith(it, index) }
      if (separator != null) {
        tokens.add(current.toString().trim())
        current.setLength(0)
        index += separator.length
      } else {
        current.append(value[index])
        index++
      }
    }
    tokens.add(current.toString().trim())
    return tokens.filter { it.isNotEmpty() }
  }

  // ---------------------------------------------------------------------
  // Stage 5: title normalisation
  // ---------------------------------------------------------------------

  private fun normaliseTitle(title: String): String =
    WHITESPACE.replace(title, " ").trim().trimEnd(*TITLE_EDGE).trim()
}
