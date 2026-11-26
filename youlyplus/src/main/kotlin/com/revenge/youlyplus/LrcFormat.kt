package com.revenge.youlyplus

import com.revenge.youlyplus.models.LyricsItem
import java.util.Locale

/** Formats a millisecond offset as an LRC timestamp, e.g. `[01:02.345]` or `<01:02.345>`. */
internal fun formatLrcTimestamp(timeMs: Long, syllable: Boolean = false): String {
  val ms = timeMs.coerceAtLeast(0)
  val minutes = ms / 60_000
  val seconds = (ms / 1_000) % 60
  val millis = ms % 1_000
  val open = if (syllable) '<' else '['
  val close = if (syllable) '>' else ']'
  return String.format(Locale.ROOT, "%c%02d:%02d.%03d%c", open, minutes, seconds, millis, close)
}

/**
 * Converts KPoe-style timed lines into standard LRC. Lines with syllable data become rich-sync
 * lines using `<mm:ss.mmm>` word tags; lines flagged as background carry the `{bg}` marker.
 */
internal fun List<LyricsItem>.toLrc(): String? {
  if (isEmpty()) return null
  val lines = mapNotNull { it.toLrcLine() }
  return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

private fun LyricsItem.toLrcLine(): String? {
  val lineTime = formatLrcTimestamp(time ?: 0L)
  val background = if (syllabus?.any { it.isBackground == true } == true) "{bg}" else ""
  val syllables = syllabus

  if (syllables.isNullOrEmpty()) {
    return lineTime + background + text.orEmpty()
  }

  val builder = StringBuilder(lineTime).append(background)
  val word = StringBuilder()
  var wordStart = 0L

  fun flushWord() {
    val value = word.toString().trim()
    if (value.isNotEmpty()) {
      builder.append(formatLrcTimestamp(wordStart, syllable = true)).append(value).append(' ')
    }
    word.clear()
  }

  for (syllable in syllables) {
    val piece = syllable.text.orEmpty()
    if (piece.startsWith(' ')) flushWord()
    if (word.isBlank()) wordStart = syllable.time ?: 0L
    word.append(piece)
    if (piece.endsWith(' ')) flushWord()
  }
  flushWord()

  return builder.toString().trimEnd()
}

/** Returns [servers] with [last] promoted to the front when it belongs to the set. */
internal fun promoteServer(last: String?, servers: List<String>): List<String> {
  if (last == null || last !in servers) return servers
  return listOf(last) + servers.filter { it != last }
}
