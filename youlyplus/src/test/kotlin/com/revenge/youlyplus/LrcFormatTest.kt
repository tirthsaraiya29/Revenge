package com.revenge.youlyplus

import com.revenge.youlyplus.models.LyricsItem
import com.revenge.youlyplus.models.Syllable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrcFormatTest {

  @Test
  fun `timestamps are zero padded`() {
    assertEquals("[00:00.000]", formatLrcTimestamp(0))
    assertEquals("[01:01.234]", formatLrcTimestamp(61_234))
    assertEquals("[60:00.000]", formatLrcTimestamp(3_600_000))
    assertEquals("<01:01.234>", formatLrcTimestamp(61_234, syllable = true))
  }

  @Test
  fun `negative times clamp to zero`() {
    assertEquals("[00:00.000]", formatLrcTimestamp(-5_000))
  }

  @Test
  fun `empty lines produce null`() {
    assertNull(emptyList<LyricsItem>().toLrc())
  }

  @Test
  fun `plain line keeps timestamp and text`() {
    val lrc = listOf(LyricsItem(text = "hello", time = 1_000)).toLrc()
    assertEquals("[00:01.000]hello", lrc)
  }

  @Test
  fun `syllables group into timestamped words`() {
    val line =
      LyricsItem(
        time = 0,
        syllabus =
          listOf(
            Syllable(text = "Hello", time = 0),
            Syllable(text = " world", time = 500),
            Syllable(text = "!", time = 900),
          ),
      )
    val lrc = listOf(line).toLrc()
    assertEquals("[00:00.000]<00:00.000>Hello <00:00.500>world!", lrc)
  }

  @Test
  fun `background syllables add marker`() {
    val line =
      LyricsItem(
        time = 1_000,
        syllabus = listOf(Syllable(text = "la", time = 1_000, isBackground = true)),
      )
    assertEquals("[00:01.000]{bg}<00:01.000>la", listOf(line).toLrc())
  }

  @Test
  fun `multiple lines are newline joined`() {
    val lrc =
      listOf(LyricsItem(text = "a", time = 0), LyricsItem(text = "b", time = 1_000)).toLrc()
    assertEquals("[00:00.000]a\n[00:01.000]b", lrc)
  }

  @Test
  fun `missing time defaults to zero`() {
    assertEquals("[00:00.000]", listOf(LyricsItem(text = "")).toLrc())
  }
}
