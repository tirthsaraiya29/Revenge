package com.revenge.metadata.cleaner

/**
 * De-escapes short API display strings.
 *
 * Three linear passes run in the order the caller contract fixes: numeric character references
 * first, then named entities, then removal of orphan entity fragments. Each pass is a forward
 * scanner that never re-reads its own output, so the result is deterministic and free of
 * replacement-order surprises.
 */
object ApiText {

  private const val MAX_CODE_POINT = 0x10FFFF

  private val NAMED_ENTITIES =
    listOf(
      "&quot;" to '"',
      "&apos;" to '\'',
      "&lt;" to '<',
      "&gt;" to '>',
      "&nbsp;" to ' ',
      "&amp;" to '&',
    )

  private val ORPHAN_FRAGMENTS = listOf("amp;", "quot;", "apos;", "lt;", "gt;", "nbsp;")

  fun clean(raw: String): String {
    if (raw.isEmpty()) return raw
    return stripOrphanFragments(decodeNamedEntities(decodeNumericReferences(raw)))
  }

  // ---------------------------------------------------------------------
  // Pass 1: `&#decimal;` / `&#xhex;`
  // ---------------------------------------------------------------------

  private fun decodeNumericReferences(text: String): String {
    if (text.indexOf('&') < 0) return text
    val out = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
      if (text[index] == '&' && index + 2 < text.length && text[index + 1] == '#') {
        val reference = readNumericReference(text, index)
        if (reference != null) {
          out.appendCodePoint(reference.codePoint)
          index = reference.end
          continue
        }
      }
      out.append(text[index])
      index++
    }
    return out.toString()
  }

  private class NumericReference(val codePoint: Int, val end: Int)

  private fun readNumericReference(text: String, start: Int): NumericReference? {
    var cursor = start + 2
    val hexadecimal = cursor < text.length && text[cursor] == 'x'
    if (hexadecimal) cursor++
    val radix = if (hexadecimal) 16 else 10

    val digitsStart = cursor
    var value = 0L
    var outOfRange = false
    while (cursor < text.length && text[cursor] != ';') {
      val digit = Character.digit(text[cursor], radix)
      if (digit < 0) return null
      if (!outOfRange) {
        value = value * radix + digit
        if (value > MAX_CODE_POINT) outOfRange = true
      }
      cursor++
    }
    if (cursor == digitsStart || cursor >= text.length || text[cursor] != ';') return null
    if (outOfRange) return null
    return NumericReference(value.toInt(), cursor + 1)
  }

  // ---------------------------------------------------------------------
  // Pass 2: named entities, `&amp;` last by construction of the table
  // ---------------------------------------------------------------------

  private fun decodeNamedEntities(text: String): String {
    if (text.indexOf('&') < 0) return text
    val out = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
      if (text[index] == '&') {
        val entity = NAMED_ENTITIES.firstOrNull { text.startsWith(it.first, index) }
        if (entity != null) {
          out.append(entity.second)
          index += entity.first.length
          continue
        }
      }
      out.append(text[index])
      index++
    }
    return out.toString()
  }

  // ---------------------------------------------------------------------
  // Pass 3: orphan fragments at string start / after whitespace
  // ---------------------------------------------------------------------

  private fun stripOrphanFragments(text: String): String {
    val out = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
      val atBoundary = index == 0 || Character.isWhitespace(text[index - 1])
      if (atBoundary) {
        val fragment = ORPHAN_FRAGMENTS.firstOrNull { text.startsWith(it, index) }
        if (fragment != null) {
          index += fragment.length
          while (index < text.length && Character.isWhitespace(text[index])) index++
          continue
        }
      }
      out.append(text[index])
      index++
    }
    return out.toString()
  }
}
