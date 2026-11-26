package com.revenge.youlyplus

import com.revenge.youlyplus.models.LyricsItem
import com.revenge.youlyplus.models.LyricsResponse
import java.util.Locale

internal fun formatLrcTimestamp(timeMs: Long, syllable: Boolean = false): String {
  val clamped = timeMs.coerceAtLeast(0)
  val minutes = clamped / 60_000
  val seconds = (clamped / 1_000) % 60
  val millis = clamped % 1_000
  val body = String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis)
  return if (syllable) "<$body>" else "[$body]"
}

internal fun List<LyricsItem>.toLrc(): String? {
  if (isEmpty()) return null
  val lines = mapNotNull { item -> item.toLrcLine() }
  if (lines.isEmpty()) return null
  return lines.joinToString("\n")
}

private fun LyricsItem.toLrcLine(): String {
  val builder = StringBuilder(formatLrcTimestamp(time ?: 0L))
  val syllables = syllabus
  if (syllables != null && syllables.any { it.isBackground == true }) {
    builder.append("{bg}")
  }
  if (syllables == null || syllables.isEmpty()) {
    builder.append(text.orEmpty())
    return builder.toString()
  }

  var wordStart = 0L
  val word = StringBuilder()

  fun flushWord() {
    if (word.isNotBlank()) {
      builder.append(formatLrcTimestamp(wordStart, syllable = true))
      builder.append(word.toString().trim())
      builder.append(' ')
    }
    word.clear()
    wordStart = 0L
  }

  for (syllable in syllables) {
    val content = syllable.text.orEmpty()
    if (content.startsWith(" ")) flushWord()
    if (word.isBlank()) wordStart = syllable.time ?: 0L
    word.append(content)
    if (content.endsWith(" ")) flushWord()
  }
  flushWord()
  return builder.toString().trimEnd()
}

internal fun LyricsResponse.bestLyrics(): String? {
  syncedLyrics?.takeIf { it.isNotBlank() }?.let { return it }
  lyrics?.toLrc()?.takeIf { it.isNotBlank() }?.let { return it }
  return plainLyrics?.takeIf { it.isNotBlank() }
}
