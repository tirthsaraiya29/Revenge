package com.revenge.youlyplus

import com.revenge.youlyplus.models.LyricsItem
import com.revenge.youlyplus.models.Syllable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrcFormatTest {
  @Test
  fun `formats timestamps in milliseconds`() {
    assertEquals("[00:00.000]", formatLrcTimestamp(0))
    assertEquals("[01:01.234]", formatLrcTimestamp(61_234))
    assertEquals("[60:00.000]", formatLrcTimestamp(3_600_000))
    assertEquals("[00:00.000]", formatLrcTimestamp(-5_000))
    assertEquals("<01:01.234>", formatLrcTimestamp(61_234, syllable = true))
  }

  @Test
  fun `empty list produces no document`() {
    assertNull(emptyList<LyricsItem>().toLrc())
  }

  @Test
  fun `plain item becomes a single timestamped line`() {
    val item = LyricsItem(text = "hello", time = 1_000)
    assertEquals("[00:01.000]hello", listOf(item).toLrc())
  }

  @Test
  fun `syllables are grouped into words`() {
    val item =
      LyricsItem(
        time = 0,
        syllabus =
          listOf(
            Syllable(text = "Hello", time = 0),
            Syllable(text = " world", time = 500),
            Syllable(text = "!", time = 900),
          ),
      )
    assertEquals("[00:00.000]<00:00.000>Hello <00:00.500>world!", listOf(item).toLrc())
  }

  @Test
  fun `background syllables add the background marker`() {
    val item =
      LyricsItem(
        time = 1_000,
        syllabus = listOf(Syllable(text = "la", time = 1_000, isBackground = true)),
      )
    assertEquals("[00:01.000]{bg}<00:01.000>la", listOf(item).toLrc())
  }

  @Test
  fun `lines are joined and textless items keep their timestamp`() {
    val items =
      listOf(
        LyricsItem(text = "first", time = 1_000),
        LyricsItem(time = 0),
        LyricsItem(text = "third", time = 3_000),
      )
    assertEquals("[00:01.000]first\n[00:00.000]\n[00:03.000]third", items.toLrc())
  }
}
