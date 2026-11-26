package com.revenge.kugou

import com.revenge.kugou.models.Keyword
import com.revenge.kugou.models.SearchLyricsResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KuGouTest {

  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun generateKeywordStripsBracketedTitleDecorations() {
    val keyword = KuGou.generateKeyword("Song (Remastered)", "Artist", "Album")

    assertEquals("Song ", keyword.title)
    assertEquals("Artist", keyword.artist)
    assertEquals("Album", keyword.album)
  }

  @Test
  fun generateKeywordNormalizesArtistSeparatorsAndDots() {
    val keyword = KuGou.generateKeyword("Song", "A, B & C")

    assertEquals("A、B、C", keyword.artist)
    assertNull(keyword.album)
    assertEquals("Mt Joy", KuGou.generateKeyword("T", "Mt. Joy").artist)
  }

  @Test
  fun buildSearchQueryIncludesAlbumOnlyWhenPresent() {
    assertEquals("T - A", buildSearchQuery(Keyword("T", "A")))
    assertEquals("T - A Album", buildSearchQuery(Keyword("T", "A", "Album")))
    assertEquals("T - A", buildSearchQuery(Keyword("T", "A", "  ")))
  }

  @Test
  fun normalizeLyricsDropsUntimestampedLinesAndHeaderCredits() {
    val raw =
      """
      song info
      [00:00.00]作词: Alice
      [00:00.00]作曲: Bob
      [00:10.00]line one
      [00:20.50]line two
      """
        .trimIndent()

    assertEquals("[00:10.00]line one\n[00:20.50]line two", raw.normalizeLyrics())
  }

  @Test
  fun normalizeLyricsKeepsPlainTimestampedDocument() {
    val raw = "[00:01.00]a\r\n[00:02.00]b"

    assertEquals("[00:01.00]a\n[00:02.00]b", raw.normalizeLyrics())
  }

  @Test
  fun normalizeLyricsReturnsEmptyWhenNoTimestampedLineExists() {
    assertEquals("", "no lyrics here".normalizeLyrics())
    assertEquals("", "".normalizeLyrics())
  }

  @Test
  fun normalizeLyricsDropsTrailingCreditBlockOnLongDocuments() {
    val body = (0 until 34).joinToString("\n") { "[00:${"%02d".format(it % 60)}.00]line $it" }
    val raw = "$body\n[00:59.00]作词: Alice"

    val normalized = raw.normalizeLyrics()
    val lines = normalized.lines()

    assertEquals(34, lines.size)
    assertEquals("[00:00.00]line 0", lines.first())
    assertEquals("[00:33.00]line 33", lines.last())
  }

  @Test
  fun searchLyricsResponseReadsSnakeCaseFieldsAndToleratesMissingOnes() {
    val payload =
      """
      {
        "status": 1,
        "info": "ok",
        "errcode": 0,
        "errmsg": "",
        "expire": 0,
        "candidates": [
          {"id": 123, "product_from": "官方推荐歌词", "duration": 240, "accesskey": "abc"}
        ]
      }
      """
        .trimIndent()

    val response = json.decodeFromString<SearchLyricsResponse>(payload)

    assertEquals(1, response.candidates.size)
    val candidate = response.candidates.first()
    assertEquals(123L, candidate.id)
    assertEquals("官方推荐歌词", candidate.productFrom)
    assertEquals(240L, candidate.duration)
    assertEquals("abc", candidate.accesskey)
  }

  @Test
  fun searchLyricsResponseDefaultsEmptyCandidates() {
    val response = json.decodeFromString<SearchLyricsResponse>("{}")

    assertEquals(0, response.status)
    assertTrue(response.candidates.isEmpty())
  }
}
