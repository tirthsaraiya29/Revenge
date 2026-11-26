package com.revenge.lyrics.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsUtilsTest {

    private fun entries(vararg times: Long): List<LyricsEntry> = times.map { LyricsEntry(it, "line") }

    @Test
    fun `standard document parses metadata and two and three digit fractions`() {
        val document = """
            [ar:Artist]
            [ti:Title]
            [00:00.00]First
            [00:12.50]Second
            [01:05.234]Third
        """.trimIndent()

        val parsed = LyricsUtils.parseLyrics(document)

        assertEquals(3, parsed.size)
        assertEquals(listOf(0L, 12_500L, 65_234L), parsed.map { it.time })
        assertEquals(listOf("First", "Second", "Third"), parsed.map { it.text })
        assertTrue(parsed.zipWithNext().all { (a, b) -> a.time <= b.time })
    }

    @Test
    fun `multiple timestamps on one line create one entry each`() {
        val parsed = LyricsUtils.parseLyrics("[00:01.234][00:02.50]Hello")

        assertEquals(2, parsed.size)
        assertEquals(listOf(1_234L, 2_500L), parsed.map { it.time })
        assertTrue(parsed.all { it.text == "Hello" })
    }

    @Test
    fun `metadata only document is empty`() {
        val document = "[ar:Artist]\nFirst line\nSecond line"

        assertTrue(LyricsUtils.parseLyrics(document).isEmpty())
        assertFalse(LyricsUtils.hasSyncedLyrics(document))
    }

    @Test
    fun `zero timestamps are entries but not synced`() {
        val parsed = LyricsUtils.parseLyrics("[00:00.00]First\n[00:00.00]Second")

        assertEquals(2, parsed.size)
        assertFalse(LyricsUtils.hasSyncedLyrics("[00:00.00]First\n[00:00.00]Second"))
    }

    @Test
    fun `blank lines and metadata are skipped`() {
        val parsed = LyricsUtils.parseLyrics("\n[ar:Artist]\n[00:00.00]First\n[00:12.50]Second")

        assertEquals(listOf(0L, 12_500L), parsed.map { it.time })
        assertTrue(LyricsUtils.hasSyncedLyrics("\n[ar:Artist]\n[00:00.00]First\n[00:12.50]Second"))
    }

    @Test
    fun `offset tag is dropped and never applied`() {
        val parsed = LyricsUtils.parseLyrics("[offset:500]\n[00:01.00]A")

        assertEquals(1, parsed.size)
        assertEquals(1_000L, parsed.single().time)
    }

    @Test
    fun `plain text without timestamps is empty`() {
        assertTrue(LyricsUtils.parseLyrics("plain text").isEmpty())
    }

    @Test
    fun `malformed timestamp lines are ignored`() {
        val inputs = listOf("[0:01.00]x", "[00:1.00]x", "[00:01.00]", "[xx:yy.zz]x")
        for (input in inputs) {
            assertTrue("expected empty for $input", LyricsUtils.parseLyrics(input).isEmpty())
        }
    }

    @Test
    fun `escape sequences and html entities are decoded`() {
        val parsed = LyricsUtils.parseLyrics("[00:01.00]A &amp; B\\n[00:02.00]C &#32;D")

        assertEquals(listOf("A & B", "C  D"), parsed.map { it.text })
    }

    @Test
    fun `out of range numeric entity stays literal`() {
        val parsed = LyricsUtils.parseLyrics("[00:00.00]&#x110000;")
        assertEquals("&#x110000;", parsed.single().text)
    }

    @Test
    fun `entry count is capped at twenty thousand`() {
        val document = (1..20_001).joinToString("\n") { "[00:01.00]Line $it" }
        assertEquals(20_000, LyricsUtils.parseLyrics(document).size)
    }

    @Test
    fun `input larger than four mebibytes returns empty`() {
        val huge = "x".repeat(4_194_305)
        assertTrue(LyricsUtils.parseLyrics(huge).isEmpty())
        assertFalse(LyricsUtils.hasSyncedLyrics(huge))
    }

    @Test
    fun `sync lookup follows the lookahead rule`() {
        val lines = entries(0L, 1_000L, 2_000L)

        assertEquals(0, LyricsUtils.findCurrentLineIndex(lines, 0L))
        assertEquals(1, LyricsUtils.findCurrentLineIndex(lines, 800L))
        assertEquals(1, LyricsUtils.findCurrentLineIndex(lines, 1_000L))
        assertEquals(2, LyricsUtils.findCurrentLineIndex(lines, 5_000L))
        assertEquals(-1, LyricsUtils.findCurrentLineIndex(emptyList(), 0L))
        assertEquals(-1, LyricsUtils.findCurrentLineIndex(entries(1_000L), 0L))
    }

    @Test
    fun `script detection matrix`() {
        assertTrue(LyricsUtils.isRussian("Привет"))
        assertTrue(LyricsUtils.isUkrainian("Привіт"))
        assertTrue(LyricsUtils.isHindi("हिन्दी"))
        assertTrue(LyricsUtils.isPunjabi("ਸਤ ਸ੍ਰੀ ਅਕਾਲ"))
        assertTrue(LyricsUtils.isJapanese("こんにちは"))
        assertTrue(LyricsUtils.isKorean("안녕하세요"))
        assertTrue(LyricsUtils.isChinese("你好世界"))
        assertFalse(LyricsUtils.isChinese("こんにちは世界"))
        assertFalse(LyricsUtils.isRussian("hello"))
        assertFalse(LyricsUtils.isHindi("hello"))
        assertFalse(LyricsUtils.isKorean("hello"))
    }

    @Test
    fun `romanized text is stored on the entry`() {
        val entry = LyricsEntry(0L, "text")
        entry.romanizedTextFlow.value = "romanized"
        assertEquals("romanized", entry.romanizedTextFlow.value)
    }

    @Test
    fun `entries compare using truncated time difference`() {
        val earlier = LyricsEntry(1_000L, "a")
        val later = LyricsEntry(2_000L, "b")
        assertTrue(earlier < later)
        assertEquals(0, LyricsEntry(1L, "x").compareTo(LyricsEntry(1L, "y")))
    }
}
