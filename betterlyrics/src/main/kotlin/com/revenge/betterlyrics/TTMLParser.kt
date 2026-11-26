package com.revenge.betterlyrics

import java.io.StringReader
import java.util.Locale
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException

private const val TTML_METADATA_NS = "http://www.w3.org/ns/ttml#metadata"

private fun hardenedBuilder(): DocumentBuilder {
  val factory = DocumentBuilderFactory.newInstance()
  factory.isNamespaceAware = true
  val features = listOf(
    "http://apache.org/xml/features/disallow-doctype-decl" to true,
    "http://xml.org/sax/features/external-general-entities" to false,
    "http://xml.org/sax/features/external-parameter-entities" to false,
    "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
  )
  for ((name, value) in features) {
    try {
      factory.setFeature(name, value)
    } catch (_: Exception) {
      // Hardening features are best-effort on JVMs that do not recognise them.
    }
  }
  try {
    factory.isXIncludeAware = false
  } catch (_: Exception) {
  }
  try {
    factory.setExpandEntityReferences(false)
  } catch (_: Exception) {
  }
  return factory.newDocumentBuilder().apply {
    setEntityResolver { _, _ -> InputSource(StringReader("")) }
    setErrorHandler(object : ErrorHandler {
      override fun warning(exception: SAXParseException) = Unit

      override fun error(exception: SAXParseException) {
        throw exception
      }

      override fun fatalError(exception: SAXParseException) {
        throw exception
      }
    })
  }
}

private fun localName(element: Element): String =
  element.localName ?: element.tagName.substringAfter(':')

private fun isSpan(element: Element): Boolean = localName(element).equals("span", ignoreCase = true)

private fun roleOf(element: Element): String = element.getAttribute("role").trim()

private fun isIgnoredRole(role: String): Boolean = role == "x-translation" || role == "x-roman"

private fun collectElements(root: Node, name: String): List<Element> {
  val found = ArrayList<Element>()
  fun visit(node: Node) {
    if (node.nodeType == Node.ELEMENT_NODE) {
      val element = node as Element
      if (localName(element) == name) found.add(element)
    }
    var child = node.firstChild
    while (child != null) {
      visit(child)
      child = child.nextSibling
    }
  }
  visit(root)
  return found
}

private fun childSpans(element: Element): List<Element> {
  val spans = ArrayList<Element>()
  var child = element.firstChild
  while (child != null) {
    if (child.nodeType == Node.ELEMENT_NODE) {
      val candidate = child as Element
      if (isSpan(candidate)) spans.add(candidate)
    }
    child = child.nextSibling
  }
  return spans
}

private fun parseTime(raw: String?): Double? {
  val value = raw?.trim().orEmpty()
  if (value.isEmpty()) return null
  val parts = value.split(':')
  return when (parts.size) {
    1 -> parts[0].toDoubleOrNull()
    2 -> {
      val minutes = parts[0].toDoubleOrNull() ?: return null
      val seconds = parts[1].toDoubleOrNull() ?: return null
      minutes * 60 + seconds
    }
    3 -> {
      val hours = parts[0].toDoubleOrNull() ?: return null
      val minutes = parts[1].toDoubleOrNull() ?: return null
      val seconds = parts[2].toDoubleOrNull() ?: return null
      hours * 3600 + minutes * 60 + seconds
    }
    else -> null
  }
}

private fun agentOf(element: Element): String? {
  element.getAttributeNS(TTML_METADATA_NS, "agent").takeIf { it.isNotBlank() }?.let { return it }
  element.getAttribute("agent").takeIf { it.isNotBlank() }?.let { return it }
  element.getAttribute("ttm:agent").takeIf { it.isNotBlank() }?.let { return it }
  val attributes = element.attributes
  for (index in 0 until attributes.length) {
    val attribute = attributes.item(index)
    val local = attribute.localName ?: attribute.nodeName.substringAfter(':')
    if (local == "agent") {
      val value = attribute.nodeValue
      if (!value.isNullOrBlank()) return value
    }
  }
  return null
}

private fun followedByWhitespace(span: Element): Boolean {
  val next = span.nextSibling ?: return false
  val data = next.nodeValue ?: ""
  return next.nodeType == Node.TEXT_NODE && data.isNotEmpty() && data.isBlank()
}

private fun directText(element: Element): String {
  val builder = StringBuilder()
  var child = element.firstChild
  while (child != null) {
    when (child.nodeType) {
      Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> builder.append(child.nodeValue ?: "")
      Node.ELEMENT_NODE -> {
        val nested = child as Element
        if (isSpan(nested) && !isIgnoredRole(roleOf(nested)) && roleOf(nested) != "x-bg") {
          builder.append(nested.textContent ?: "")
        }
      }
    }
    child = child.nextSibling
  }
  return builder.toString().trim()
}

private fun buildWords(spans: List<Element>): List<TTMLParser.ParsedWord> {
  val words = ArrayList<TTMLParser.ParsedWord>()
  var previous: Element? = null
  var buffer: StringBuilder? = null
  var wordStart = 0.0
  var wordEnd = 0.0

  fun flush() {
    val current = buffer ?: return
    val text = current.toString().trim()
    if (text.isNotEmpty()) {
      words.add(TTMLParser.ParsedWord(text, wordStart, wordEnd))
    }
    buffer = null
  }

  for (span in spans) {
    val text = (span.textContent ?: "").trim()
    val begin = parseTime(span.getAttribute("begin"))
    val end = parseTime(span.getAttribute("end"))
    if (begin == null || end == null || text.isEmpty()) continue
    if (previous == null || followedByWhitespace(previous)) flush()
    if (buffer == null) {
      buffer = StringBuilder()
      wordStart = begin
    }
    buffer!!.append(text)
    wordEnd = end
    previous = span
  }
  flush()
  return words
}

private fun joinWords(words: List<TTMLParser.ParsedWord>): String =
  words.joinToString(" ") { it.text }

private fun formatTimestamp(seconds: Double): String {
  val totalMillis = (seconds * 1000.0).roundToLong().coerceAtLeast(0)
  val minutes = totalMillis / 60_000
  val secs = (totalMillis / 1_000) % 60
  val centis = (totalMillis % 1_000) / 10
  return String.format(Locale.ROOT, "[%02d:%02d.%02d]", minutes, secs, centis)
}

private fun appendTimestampLine(
  builder: StringBuilder,
  text: String,
  startTime: Double,
  agent: String?,
  background: Boolean,
) {
  builder.append(formatTimestamp(startTime))
  if (background) {
    builder.append("{bg}")
  } else if (!agent.isNullOrEmpty()) {
    builder.append("{agent:").append(agent).append('}')
  }
  builder.append(text).append('\n')
}

private fun appendWordTrack(builder: StringBuilder, words: List<TTMLParser.ParsedWord>) {
  if (words.isEmpty()) return
  builder.append('<')
  words.forEachIndexed { index, word ->
    if (index > 0) builder.append('|')
    builder.append(word.text).append(':').append(word.startTime).append(':').append(word.endTime)
  }
  builder.append(">\n")
}

object TTMLParser {
  data class ParsedWord(
    val text: String,
    val startTime: Double,
    val endTime: Double,
  )

  data class ParsedLine(
    val text: String,
    val startTime: Double,
    val words: List<ParsedWord>,
    val agent: String? = null,
    val isBackground: Boolean = false,
    val backgroundLines: List<ParsedLine> = emptyList(),
  )

  fun parseTTML(ttml: String): List<ParsedLine> {
    return try {
      val document = hardenedBuilder().parse(InputSource(StringReader(ttml)))
      collectElements(document, "p").mapNotNull { paragraph -> parseParagraph(paragraph) }
    } catch (e: Exception) {
      emptyList()
    }
  }

  private fun parseParagraph(paragraph: Element): ParsedLine? {
    val startTime = parseTime(paragraph.getAttribute("begin")) ?: return null
    val agent = agentOf(paragraph)
    val spans = childSpans(paragraph)

    val wordSpans = spans.filter { span ->
      val role = roleOf(span)
      role != "x-bg" && !isIgnoredRole(role)
    }
    val words = buildWords(wordSpans)
    val text = if (words.isNotEmpty()) joinWords(words) else directText(paragraph)
    if (text.isEmpty()) return null

    val backgroundLines = spans
      .filter { roleOf(it) == "x-bg" }
      .mapNotNull { backgroundSpan -> parseBackground(backgroundSpan, startTime) }

    return ParsedLine(
      text = text,
      startTime = startTime,
      words = words,
      agent = agent,
      isBackground = false,
      backgroundLines = backgroundLines,
    )
  }

  private fun parseBackground(span: Element, paragraphStart: Double): ParsedLine? {
    val startTime = parseTime(span.getAttribute("begin")) ?: paragraphStart
    val nestedSpans = childSpans(span).filter { !isIgnoredRole(roleOf(it)) }
    val words = buildWords(nestedSpans)
    val text = if (words.isNotEmpty()) joinWords(words) else directText(span)
    if (text.isEmpty()) return null
    return ParsedLine(
      text = text,
      startTime = startTime,
      words = words,
      agent = null,
      isBackground = true,
      backgroundLines = emptyList(),
    )
  }

  fun toLRC(lines: List<ParsedLine>): String {
    val builder = StringBuilder()
    for (line in lines) {
      appendTimestampLine(builder, line.text, line.startTime, line.agent, background = false)
      appendWordTrack(builder, line.words)
      for (background in line.backgroundLines) {
        appendTimestampLine(builder, background.text, background.startTime, agent = null, background = true)
        appendWordTrack(builder, background.words)
      }
    }
    return builder.toString()
  }
}
