package com.revenge.youlyplus.models

import kotlinx.serialization.Serializable

/**
 * Payload returned by the LyricsPlus / KPoe backend at `GET /v2/lyrics/get`.
 *
 * The backend serves two shapes: LRCLib-style (`syncedLyrics` / `plainLyrics`) and KPoe-style
 * (`lyrics` as a list of timed lines). Both are modelled here so either can be consumed.
 */
@Serializable
data class LyricsResponse(
  val id: Int? = null,
  val syncedLyrics: String? = null,
  val plainLyrics: String? = null,
  val lyrics: List<LyricsItem>? = null,
  val type: String? = null,
  val trackName: String? = null,
  val artistName: String? = null,
  val albumName: String? = null,
  val duration: Double? = null,
)

/** A single timed lyric line. All times are milliseconds. */
@Serializable
data class LyricsItem(
  val text: String? = null,
  val time: Long? = null,
  val duration: Long? = null,
  val syllabus: List<Syllable>? = null,
)

/** A word/syllable fragment for word-by-word ("rich") sync. Times are milliseconds. */
@Serializable
data class Syllable(
  val text: String? = null,
  val time: Long? = null,
  val duration: Long? = null,
  val isBackground: Boolean? = null,
)
