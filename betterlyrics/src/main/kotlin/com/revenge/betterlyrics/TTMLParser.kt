package com.revenge.betterlyrics

import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Converts TTML (Timed Text Markup Language) lyric documents into the app's LRC dialect.
 *
 * The app's dialect understands line timestamps, inline word timestamps (`<w:t0:t1|...>`) and two
 * extensions: `{agent:<id>}` for the vocalist and `{bg}` for background vocals.
 */
object TTMLParser {
  private const val METADATA_NAMESPACE = "http://www.w3.org/ns/ttml#metadata"
  private const val ROLE_BACKGROUND = "x-bg"
  private val IGNORED_ROLES = setOf("x-translation", "x-roman")

  data class ParsedWord(val text: String, val startTime: Double, val endTime: Double)

  data class ParsedLine(
    val text: String,
    val startTime: Double,
    val words: List<ParsedWord>,
    val agent: String? = null,
    val isBackground: Boolean = false,
    val backgroundLines: List<ParsedLine> = emptyList(),
  )

  /** A timed run of text plus whether the source put a word break after it. */
  private data class TimedSpan(
    val text: String,
    val startTime: Double,
    val endTime: Double,
    val endsWord: Boolean,
  )

  /**
   * Parses a TTML document into timed lines. Returns an empty list when the input is malformed or
   * contains no `<p>` element with a `begin` attribute; parsing never throws.
   */
  fun parseTTML(ttml: String): List<ParsedLine> =
    try {
      parseDocument(ttml)
    } catch (e: Exception) {
      emptyList()
    }

  private fun parseDocument(ttml: String): List<ParsedLine> {
    val document = buildDocument(ttml)
    val paragraphs = document.getElementsByTagNameNS("*", "p")
    val lines = ArrayList<ParsedLine>(paragraphs.length)

    for (index in 0 until paragraphs.length) {
      val paragraph = paragraphs.item(index) as? Element ?: continue
      val begin = paragraph.getAttribute("begin")
      if (begin.isBlank()) continue
      val parsed = parseParagraph(paragraph, parseTime(begin)) ?: continue
      lines.add(parsed)
    }

    return lines
  }

  /** Serializes parsed lines to the app's LRC format. */
  fun toLRC(lines: List<ParsedLine>): String = buildString {
    for (line in lines) {
      appendTimedLine(line.startTime, line.agentPrefix(), line.text)
      appendWordTrack(line.words)

      for (background in line.backgroundLines) {
        appendTimedLine(background.startTime, "{bg}", background.text)
        appendWordTrack(background.words)
      }
    }
  }

  private fun buildDocument(ttml: String): Document {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    // Untrusted input: never resolve external entities or DOCTYPEs (XXE hardening).
    factory.setFeatureQuietly("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeatureQuietly("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeatureQuietly("http://xml.org/sax/features/external-parameter-entities", false)

    return factory.newDocumentBuilder().parse(ttml.byteInputStream())
  }

  private fun DocumentBuilderFactory.setFeatureQuietly(name: String, value: Boolean) {
    runCatching { setFeature(name, value) }
  }

  private fun parseParagraph(paragraph: Element, startTime: Double): ParsedLine? {
    val agent = attribute(paragraph, "agent")?.takeIf { it.isNotBlank() }
    val spans = ArrayList<TimedSpan>()
    val backgroundLines = ArrayList<ParsedLine>()

    for (child in paragraph.elementChildren()) {
      if (!child.isSpan()) continue
      val role = attribute(child, "role")
      when {
        role == ROLE_BACKGROUND -> parseBackground(child, startTime)?.let(backgroundLines::add)
        role != null && role in IGNORED_ROLES -> Unit
        else -> timedSpan(child)?.let(spans::add)
      }
    }

    val words = mergeSpans(spans)
    val text = if (words.isNotEmpty()) {
      words.joinToString(" ") { it.text }
    } else {
      directText(paragraph).trim()
    }
    if (text.isEmpty()) return null

    return ParsedLine(
      text = text,
      startTime = startTime,
      words = words,
      agent = agent,
      isBackground = false,
      backgroundLines = backgroundLines,
    )
  }

  private fun parseBackground(span: Element, fallbackStart: Double): ParsedLine? {
    val begin = span.getAttribute("begin")
    val startTime = if (begin.isBlank()) fallbackStart else parseTime(begin)

    val spans = ArrayList<TimedSpan>()
    for (child in span.elementChildren()) {
      if (!child.isSpan()) continue
      val role = attribute(child, "role")
      if (role != null && role in IGNORED_ROLES) continue
      timedSpan(child)?.let(spans::add)
    }

    val words = mergeSpans(spans)
    val text = if (words.isNotEmpty()) {
      words.joinToString(" ") { it.text }
    } else {
      directText(span).trim()
    }
    if (text.isEmpty()) return null

    return ParsedLine(
      text = text,
      startTime = startTime,
      words = words,
      agent = null,
      isBackground = true,
    )
  }

  private fun timedSpan(span: Element): TimedSpan? {
    val begin = span.getAttribute("begin")
    val end = span.getAttribute("end")
    if (begin.isBlank() || end.isBlank()) return null

    val text = span.textContent?.trim().orEmpty()
    if (text.isEmpty()) return null

    return TimedSpan(
      text = text,
      startTime = parseTime(begin),
      endTime = parseTime(end),
      endsWord = endsWithWhitespace(span),
    )
  }

  /**
   * Spans with no whitespace between them are syllables of the same word, so they are joined and
   * their time range is widened to cover the whole word.
   */
  private fun mergeSpans(spans: List<TimedSpan>): List<ParsedWord> {
    if (spans.isEmpty()) return emptyList()

    val words = ArrayList<ParsedWord>(spans.size)
    val current = StringBuilder()
    var wordStart = 0.0
    var wordEnd = 0.0
    var atWordStart = true

    for (index in spans.indices) {
      val span = spans[index]
      if (!atWordStart && spans[index - 1].endsWord) {
        flushWord(current, wordStart, wordEnd, words)
        atWordStart = true
      }

      if (atWordStart) {
        wordStart = span.startTime
        atWordStart = false
      }
      current.append(span.text)
      wordEnd = span.endTime
    }

    flushWord(current, wordStart, wordEnd, words)
    return words
  }

  private fun flushWord(
    buffer: StringBuilder,
    startTime: Double,
    endTime: Double,
    into: MutableList<ParsedWord>,
  ) {
    val text = buffer.toString().trim()
    if (text.isNotEmpty()) {
      into.add(ParsedWord(text, startTime, endTime))
    }
    buffer.clear()
  }

  private fun directText(element: Element): String = buildString {
    for (child in element.childNodesAsList()) {
      if (child.nodeType == Node.TEXT_NODE) {
        append(child.textContent ?: "")
      } else if (child.nodeType == Node.ELEMENT_NODE) {
        val span = child as? Element ?: continue
        val role = attribute(span, "role")
        val ignored = role == ROLE_BACKGROUND || (role != null && role in IGNORED_ROLES)
        if (!ignored && span.isSpan()) {
          append(span.textContent ?: "")
        }
      }
    }
  }

  private fun endsWithWhitespace(node: Node): Boolean {
    val next = node.nextSibling ?: return false
    return next.nodeType == Node.TEXT_NODE &&
      next.textContent?.any { it.isWhitespace() } == true
  }

  /** Reads a TTML metadata attribute regardless of namespace prefix handling. */
  private fun attribute(element: Element, localName: String): String? {
    val namespaced = element.getAttributeNS(METADATA_NAMESPACE, localName)
    if (namespaced.isNotEmpty()) return namespaced

    val plain = element.getAttribute(localName)
    if (plain.isNotEmpty()) return plain

    val prefixed = element.getAttribute("ttm:$localName")
    if (prefixed.isNotEmpty()) return prefixed

    val attributes = element.attributes
    for (index in 0 until attributes.length) {
      val attr = attributes.item(index) ?: continue
      val name = attr.localName ?: attr.nodeName.substringAfter(':', attr.nodeName)
      if (name == localName) return attr.nodeValue ?: ""
    }
    return null
  }

  private fun Element.isSpan(): Boolean =
    (localName ?: tagName.substringAfter(':', tagName)).equals("span", ignoreCase = true)

  private fun Node.childNodesAsList(): List<Node> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) }

  private fun Element.elementChildren(): List<Element> =
    childNodesAsList().filterIsInstance<Element>()

  private fun ParsedLine.agentPrefix(): String =
    agent?.takeIf { it.isNotEmpty() }?.let { "{agent:$it}" }.orEmpty()

  private fun StringBuilder.appendTimedLine(startTime: Double, prefix: String, text: String) {
    val totalMillis = (startTime * 1000.0).toLong()
    val minutes = totalMillis / 60_000
    val seconds = (totalMillis % 60_000) / 1_000
    val centiseconds = (totalMillis % 1_000) / 10
    append(
      String.format(
        Locale.US,
        "[%02d:%02d.%02d]%s%s\n",
        minutes,
        seconds,
        centiseconds,
        prefix,
        text,
      ),
    )
  }

  private fun StringBuilder.appendWordTrack(words: List<ParsedWord>) {
    if (words.isEmpty()) return
    append('<')
    append(words.joinToString("|") { "${it.text}:${it.startTime}:${it.endTime}" })
    append(">\n")
  }

  private fun parseTime(raw: String): Double {
    val value = raw.trim()
    if (value.isEmpty()) return 0.0

    value.toDoubleOrNull()?.let { return it }

    val parts = value.split(':')
    if (parts.size !in 2..3) return 0.0
    val numbers = parts.map { it.toDoubleOrNull() ?: return 0.0 }

    return when (numbers.size) {
      2 -> numbers[0] * 60.0 + numbers[1]
      else -> numbers[0] * 3600.0 + numbers[1] * 60.0 + numbers[2]
    }
  }
}
