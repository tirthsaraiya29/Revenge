package com.revenge.lyrics.lyrics

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomanizationTest {

  @Test
  fun `empty katakana input becomes empty`() {
    assertEquals("", LyricsUtils.katakanaToRomaji(null))
    assertEquals("", LyricsUtils.katakanaToRomaji(""))
  }

  @Test
  fun `youon and small tsu follow the katakana rules`() {
    assertEquals("kya", LyricsUtils.katakanaToRomaji("キャ"))
    assertEquals("kka", LyricsUtils.katakanaToRomaji("ッカ", "カ"))
    assertEquals("", LyricsUtils.katakanaToRomaji("ッ"))
  }

  @Test
  fun `japanese uses kuromoji readings and joins tokens with spaces`() = runTest {
    assertEquals("konnichiha", LyricsUtils.romanizeJapanese("こんにちは"))
    assertEquals("", LyricsUtils.romanizeJapanese(""))
  }

  @Test
  fun `korean uses revised romanisation with liaison`() = runTest {
    assertEquals("annyeonghaseyo", LyricsUtils.romanizeKorean("안녕하세요"))
    assertEquals("", LyricsUtils.romanizeKorean(""))
    assertEquals("hello", LyricsUtils.romanizeKorean("hello"))
  }

  @Test
  fun `chinese uses pinyin and strips spaces before punctuation`() = runTest {
    assertEquals("ni hao", LyricsUtils.romanizeChinese("你好"))
    assertEquals("ni hao，shi jie", LyricsUtils.romanizeChinese("你好，世界"))
    assertEquals("", LyricsUtils.romanizeChinese(""))
  }

  @Test
  fun `cyrillic detects the alphabet and transliterates`() = runTest {
    assertEquals("Privet", LyricsUtils.romanizeCyrillic("Привет"))
    assertEquals("Privit svit", LyricsUtils.romanizeCyrillic("Привіт світ"))
    assertNull(LyricsUtils.romanizeCyrillic("hello"))
    assertNull(LyricsUtils.romanizeCyrillic("е"))
    assertNull(LyricsUtils.romanizeCyrillic(""))
  }

  @Test
  fun `serbian and other cyrillic alphabets are supported`() = runTest {
    assertEquals("Ljubav", LyricsUtils.romanizeCyrillic("Љубав"))
    assertEquals("Khristov", LyricsUtils.romanizeCyrillic("Христов"))
    assertTrue(LyricsUtils.romanizeCyrillic("Беларусь") != null)
    assertTrue(LyricsUtils.romanizeCyrillic("Кыргызстан") != null)
    assertEquals("Makedonija", LyricsUtils.romanizeCyrillic("Македонија"))
  }

  @Test
  fun `hindi and punjabi tables transliterate`() = runTest {
    assertEquals("hindee", LyricsUtils.romanizeHindi("हिन्दी"))
    assertEquals("st sree akaal", LyricsUtils.romanizePunjabi("ਸਤ ਸ੍ਰੀ ਅਕਾਲ"))
  }

  @Test
  fun `script predicates are precise`() {
    assertTrue(LyricsUtils.isRussian("Привет"))
    assertTrue(LyricsUtils.isUkrainian("Привіт"))
    assertTrue(LyricsUtils.isSerbian("Живот"))
    assertTrue(LyricsUtils.isBulgarian("България"))
    assertTrue(LyricsUtils.isBelarusian("Беларусь"))
    assertTrue(LyricsUtils.isKyrgyz("Кыргызстан"))
    assertTrue(LyricsUtils.isMacedonian("Македонија"))
    assertTrue(LyricsUtils.isJapanese("こんにちは"))
    assertTrue(LyricsUtils.isKorean("안녕"))
    assertTrue(LyricsUtils.isChinese("你好"))
    assertTrue(LyricsUtils.isHindi("हिन्दी"))
    assertTrue(LyricsUtils.isPunjabi("ਪੰਜਾਬੀ"))
    assertFalse(LyricsUtils.isChinese("こんにちは世界"))
    assertFalse(LyricsUtils.isRussian("hello"))
    assertFalse(LyricsUtils.isJapanese("hello"))
  }
}
