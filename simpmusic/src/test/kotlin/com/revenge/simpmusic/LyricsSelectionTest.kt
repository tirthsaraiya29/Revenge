package com.revenge.simpmusic

import com.revenge.simpmusic.models.LyricsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSelectionTest {
  private fun track(
    duration: Int? = null,
    rich: String? = null,
    synced: String? = null,
    plain: String? = null,
  ) = LyricsData(
    duration = duration,
    richSyncLyrics = rich,
    syncedLyrics = synced,
    plainLyrics = plain,
  )

  @Test
  fun `duration distance treats missing duration as zero`() {
    assertEquals(120, track(duration = null).durationDistance(120))
    assertEquals(20, track(duration = 100).durationDistance(120))
    assertEquals(20, track(duration = 140).durationDistance(120))
  }

  @Test
  fun `matches duration accepts anything when target is not positive`() {
    assertTrue(track(duration = 10).matchesDuration(0))
    assertTrue(track(duration = 10).matchesDuration(-5))
  }

  @Test
  fun `matches duration honours the ten second tolerance`() {
    assertTrue(track(duration = 110).matchesDuration(120))
    assertTrue(track(duration = 130).matchesDuration(120))
    assertFalse(track(duration = 131).matchesDuration(120))
    assertFalse(track(duration = 109).matchesDuration(120))
  }

  @Test
  fun `best text prefers rich sync over synced over plain`() {
    assertEquals("rich", track(rich = "rich", synced = "synced", plain = "plain").bestLyricsText())
    assertEquals("synced", track(synced = "synced", plain = "plain").bestLyricsText())
    assertEquals("plain", track(plain = "plain").bestLyricsText())
    assertNull(track().bestLyricsText())
  }

  @Test
  fun `best text skips blank values`() {
    assertEquals("plain", track(rich = "  ", synced = "", plain = "plain").bestLyricsText())
    assertNull(track(rich = " ", synced = "\n", plain = "").bestLyricsText())
  }

  @Test
  fun `select without duration keeps the first api result`() {
    val tracks = listOf(track(plain = "first"), track(plain = "second"))
    assertEquals("first", selectLyrics(tracks, 0))
  }

  @Test
  fun `select with duration picks the closest candidate`() {
    val tracks =
      listOf(
        track(duration = 240, plain = "far"),
        track(duration = 202, plain = "close"),
        track(duration = 215, plain = "mid"),
      )
    assertEquals("close", selectLyrics(tracks, 200))
  }

  @Test
  fun `select rejects candidates outside tolerance`() {
    val tracks = listOf(track(duration = 300, plain = "wrong"))
    assertNull(selectLyrics(tracks, 200))
  }

  @Test
  fun `select returns null for empty input`() {
    assertNull(selectLyrics(emptyList(), 200))
  }

  @Test
  fun `select falls back from rich to synced to plain within a candidate`() {
    assertEquals("synced", selectLyrics(listOf(track(duration = 200, synced = "synced")), 200))
    assertEquals("plain", selectLyrics(listOf(track(duration = 200, plain = "plain")), 200))
  }

  @Test
  fun `alternatives are ordered by duration closeness`() {
    val tracks =
      listOf(
        track(duration = 208, synced = "far"),
        track(duration = 201, synced = "close"),
      )
    assertEquals(listOf("close", "far"), collectAlternativeLyrics(tracks, 200))
  }

  @Test
  fun `alternatives skip duration mismatches`() {
    val tracks = listOf(track(duration = 400, synced = "wrong"), track(duration = 200, synced = "ok"))
    assertEquals(listOf("ok"), collectAlternativeLyrics(tracks, 200))
  }

  @Test
  fun `alternatives emit plain text at most once`() {
    val tracks =
      listOf(
        track(duration = 200, rich = "rich1", plain = "plain1"),
        track(duration = 201, synced = "synced2", plain = "plain2"),
      )
    assertEquals(listOf("rich1", "plain1", "synced2"), collectAlternativeLyrics(tracks, 200))
  }

  @Test
  fun `alternatives ignore blank values`() {
    val tracks = listOf(track(duration = 200, rich = " ", synced = "", plain = " "), track(duration = 200, plain = "ok"))
    assertEquals(listOf("ok"), collectAlternativeLyrics(tracks, 200))
  }

  @Test
  fun `alternatives are capped`() {
    val tracks = (0 until 10).map { track(duration = 200, synced = "s$it") }
    assertEquals(MAX_ALTERNATE_LYRICS, collectAlternativeLyrics(tracks, 200).size)
  }
}
