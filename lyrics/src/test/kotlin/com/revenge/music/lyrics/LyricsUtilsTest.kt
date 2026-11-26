package com.revenge.music.lyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsUtilsTest {
  @Test
  fun `metadata tags do not make plain lyrics synced`() {
    val lyrics = "[ar:Artist]\nFirst line\nSecond line"

    assertFalse(LyricsUtils.hasSyncedLyrics(lyrics))
    // Metadata-only lines are not lyrics; untimed text is dropped by the LRC parser.
    assertTrue(LyricsUtils.parseLyrics(lyrics).isEmpty())
  }

  @Test
  fun `zero timestamps stay readable but are not synced`() {
    val lyrics = "[00:00.00]First line\n[00:00.00]Second line"

    assertFalse(LyricsUtils.hasSyncedLyrics(lyrics))
    assertEquals(
      listOf("First line", "Second line"),
      LyricsUtils.parseLyrics(lyrics).map { it.text },
    )
  }

  @Test
  fun `real timestamps are recognised after metadata`() {
    val lyrics = "\n[ar:Artist]\n[00:00.00]First line\n[00:12.50]Second line"

    assertTrue(LyricsUtils.hasSyncedLyrics(lyrics))
    assertEquals(listOf(0L, 12_500L), LyricsUtils.parseLyrics(lyrics).map { it.time })
  }

  @Test
  fun `multiple timestamps produce one entry each`() {
    val parsed = LyricsUtils.parseLyrics("[00:01.234][00:02.50]Hello")

    assertEquals(2, parsed.size)
    assertEquals(1_234L, parsed[0].time)
    assertEquals(2_500L, parsed[1].time)
    assertEquals("Hello", parsed[0].text)
  }

  @Test
  fun `findCurrentLineIndex honours the lookahead window`() {
    val lines =
      listOf(
        LyricsEntry(0L, "a"),
        LyricsEntry(1_000L, "b"),
        LyricsEntry(2_000L, "c"),
      )

    assertEquals(0, LyricsUtils.findCurrentLineIndex(lines, 0L))
    assertEquals(1, LyricsUtils.findCurrentLineIndex(lines, 800L))
    assertEquals(1, LyricsUtils.findCurrentLineIndex(lines, 1_000L))
    assertEquals(2, LyricsUtils.findCurrentLineIndex(lines, 5_000L))
  }

  @Test
  fun `script detection`() {
    assertTrue(LyricsUtils.isRussian("Привет мир"))
    assertTrue(LyricsUtils.isUkrainian("Привіт світ"))
    assertTrue(LyricsUtils.isHindi("नमस्ते"))
    assertTrue(LyricsUtils.isPunjabi("ਸਤ ਸ੍ਰੀ ਅਕਾਲ"))
    assertTrue(LyricsUtils.isJapanese("こんにちは"))
    assertTrue(LyricsUtils.isKorean("안녕하세요"))
    assertTrue(LyricsUtils.isChinese("你好世界"))
    assertFalse(LyricsUtils.isChinese("こんにちは世界"))
    assertFalse(LyricsUtils.isRussian("hello"))
  }

  @Test
  fun `cyrillic romanization picks the right alphabet`() = runBlocking {
    assertEquals("Privet", LyricsUtils.romanizeCyrillic("Привет"))
    assertEquals("Privit svit", LyricsUtils.romanizeCyrillic("Привіт світ"))
    assertNull(LyricsUtils.romanizeCyrillic("hello"))
  }

  @Test
  fun `hindi and punjabi romanization`() = runBlocking {
    assertEquals("hindee", LyricsUtils.romanizeHindi("हिन्दी"))
    assertEquals("st sree akaal", LyricsUtils.romanizePunjabi("ਸਤ ਸ੍ਰੀ ਅਕਾਲ"))
  }

  @Test
  fun `html entities and escaped newlines are decoded`() {
    val parsed = LyricsUtils.parseLyrics("[00:01.00]A &amp; B\\n[00:02.00]C &#32;D")

    assertEquals(listOf("A & B", "C  D"), parsed.map { it.text })
  }
}
