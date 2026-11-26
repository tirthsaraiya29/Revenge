package com.revenge.lrclib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcLibLyricsTest {

  @Test
  fun `parses minute second and centisecond timestamps`() {
    val sentences = LrcLib.Lyrics("[00:01.00]first line\n[01:23.45]second line").sentences!!

    assertEquals("first line", sentences[1_000L])
    assertEquals("second line", sentences[83_450L])
  }

  @Test
  fun `always exposes a leading zero entry`() {
    val sentences = LrcLib.Lyrics("[00:10.00]hello").sentences!!

    assertEquals("", sentences[0L])
    assertEquals("hello", sentences[10_000L])
  }

  @Test
  fun `accepts one two and three digit fractions`() {
    val sentences = LrcLib.Lyrics("[00:00.5]a\n[00:01.50]b\n[00:02.500]c").sentences!!

    assertEquals("a", sentences[500L])
    assertEquals("b", sentences[1_500L])
    assertEquals("c", sentences[2_500L])
  }

  @Test
  fun `expands multiple timestamps sharing one line`() {
    val sentences = LrcLib.Lyrics("[00:01.00][00:02.50]shared").sentences!!

    assertEquals("shared", sentences[1_000L])
    assertEquals("shared", sentences[2_500L])
  }

  @Test
  fun `ignores lines without timestamps`() {
    val sentences = LrcLib.Lyrics("not a timestamp\n[00:03.00]text").sentences!!

    assertEquals(2, sentences.size)
    assertEquals("text", sentences[3_000L])
  }

  @Test
  fun `empty text yields only the zero entry`() {
    assertEquals(mapOf(0L to ""), LrcLib.Lyrics("").sentences)
  }

  @Test
  fun `clock punctuation is supported`() {
    val sentences = LrcLib.Lyrics("[01:05:25]dash").sentences!!

    assertTrue(sentences.containsKey(65_250L))
    assertEquals("dash", sentences[65_250L])
  }
}
