package com.revenge.lyrics.lyrics

import kotlinx.coroutines.flow.MutableStateFlow

/** A single word or syllable with its timing expressed in seconds. */
data class WordTimestamp(
  val text: String,
  val startTime: Double,
  val endTime: Double,
)

/**
 * One display line of lyrics. [time] is the line start in milliseconds. The two state flows are
 * per-entry side channels for romanised and translated text and are shared by [copy].
 */
data class LyricsEntry(
  val time: Long,
  val text: String,
  val words: List<WordTimestamp>? = null,
  val romanizedTextFlow: MutableStateFlow<String?> = MutableStateFlow(null),
  val translatedTextFlow: MutableStateFlow<String?> = MutableStateFlow(null),
  val agent: String? = null,
  val isBackground: Boolean = false,
) : Comparable<LyricsEntry> {

  override fun compareTo(other: LyricsEntry): Int = (time - other.time).toInt()

  companion object {
    val HEAD_LYRICS_ENTRY = LyricsEntry(0L, "")
  }
}

/** Package-level alias so both `HEAD_LYRICS_ENTRY` and `LyricsEntry.HEAD_LYRICS_ENTRY` resolve. */
val HEAD_LYRICS_ENTRY: LyricsEntry
  get() = LyricsEntry.HEAD_LYRICS_ENTRY
