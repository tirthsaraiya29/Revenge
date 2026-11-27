package com.revenge.lyrics.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParsingFixturesTest {

  @Test
  fun `rich line extracts words and line timing`() {
    val parsed = LyricsUtils.parseLyrics("[00:12.34]<00:12.34>Hel<00:12.80>lo")

    assertEquals(1, parsed.size)
    val entry = parsed.single()
    assertEquals("Hello", entry.text)
    assertEquals(2, entry.words?.size)
    val words = entry.words!!
    assertEquals(12.34, words[0].startTime, 0.001)
    assertEquals(12.80, words[1].startTime, 0.001)
    assertEquals(12.84, words[1].endTime, 0.001)
  }

  @Test
  fun `rich last word ends at the next rich line start`() {
    val parsed =
      LyricsUtils.parseLyrics(
        "[00:01.00]<00:01.00>A\n[00:02.00]<00:02.00>B",
      )

    assertEquals(2, parsed.size)
    assertEquals(2.0, parsed[0].words!!.single().endTime, 0.001)
  }

  @Test
  fun `background prefix is only recognised at line start`() {
    val parsed = LyricsUtils.parseLyrics("[00:20.00]{bg}echo {agent:v1}")

    val entry = parsed.single()
    assertTrue(entry.isBackground)
    assertEquals("echo", entry.text)
    assertEquals("v1", entry.agent)
  }

  @Test
  fun `background marker in the middle does not set the flag`() {
    val parsed = LyricsUtils.parseLyrics("[00:30.00]{agent:v1}text {bg}not-prefix")

    val entry = parsed.single()
    assertEquals("v1", entry.agent)
    assertFalse(entry.isBackground)
    assertTrue(entry.text.contains("not-prefix"))
  }

  @Test
  fun `rich line without word tags has null words`() {
    val parsed = LyricsUtils.parseLyrics("[00:10.00]plain line")
    assertNull(parsed.single().words)
  }

  @Test
  fun `malformed word tags do not throw`() {
    val parsed = LyricsUtils.parseLyrics("[00:01.00]<bad><00:1.0>text")
    assertEquals(1, parsed.size)
    assertTrue(parsed.single().text.isNotEmpty())
  }

  @Test
  fun `ttml spans become word timed entries in seconds`() {
    val ttml =
      """
      <tt xmlns="http://www.w3.org/ns/ttml">
        <body><p begin="1.5" end="2.5">
          <span begin="1.5" end="2.0">Hello</span>
          <span begin="2.0" end="2.5">world</span>
        </p></body>
      </tt>
      """
        .trimIndent()

    val parsed = LyricsUtils.parseLyrics(ttml)

    assertFalse(parsed.isEmpty())
    val entry = parsed.first()
    assertTrue(entry.text.contains("Hello"))
    assertNotNull(entry.words)
    val starts = entry.words!!.map { it.startTime }
    assertTrue(starts.any { kotlin.math.abs(it - 1.5) < 0.001 })
    assertTrue(starts.any { kotlin.math.abs(it - 2.0) < 0.001 })
  }

  @Test
  fun `malformed ttml never throws`() {
    val parsed = LyricsUtils.parseLyrics("<tt><body><p begin='0'>oops")
    assertTrue(parsed.isEmpty() || parsed.isNotEmpty())
  }

  @Test
  fun `xxe fixture does not resolve external entities`() {
    val ttml =
      """
      <?xml version="1.0"?>
      <!DOCTYPE tt [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
      <tt xmlns="http://www.w3.org/ns/ttml">
        <body><p begin="0" end="1">&xxe;</p></body>
      </tt>
      """
        .trimIndent()

    val parsed = LyricsUtils.parseLyrics(ttml)
    val joined = parsed.joinToString(" ") { it.text }
    assertFalse(joined.contains("root:"))
    assertFalse(joined.contains("daemon:"))
  }

  @Test
  fun `deeply nested xml hits the depth cap quickly`() {
    val nested = buildString {
      append("<tt xmlns=\"http://www.w3.org/ns/ttml\">")
      repeat(10_000) { append("<span>") }
      repeat(10_000) { append("</span>") }
      append("</tt>")
    }

    val started = System.currentTimeMillis()
    val parsed = LyricsUtils.parseLyrics(nested)
    val elapsed = System.currentTimeMillis() - started

    assertTrue(parsed.isEmpty())
    assertTrue("depth cap should short-circuit, took ${elapsed}ms", elapsed < 1_000)
  }

  @Test
  fun `markup-looking plain text falls back without throwing`() {
    val parsed = LyricsUtils.parseLyrics("<3 lyrics")
    assertTrue(parsed.isEmpty())
  }

  @Test
  fun `ttml inline translation is exposed on the entry`() {
    val ttml =
      """
      <tt xmlns="http://www.w3.org/ns/ttml">
        <body><p begin="0" end="1">
          <span begin="0" end="1">Hello</span>
          <span begin="0" end="1" ttm:role="x-translation">Bonjour</span>
        </p></body>
      </tt>
      """
        .trimIndent()

    val parsed = LyricsUtils.parseLyrics(ttml)
    assertFalse(parsed.isEmpty())
    assertTrue(parsed.any { it.translatedTextFlow.value == "Bonjour" })
  }
}
