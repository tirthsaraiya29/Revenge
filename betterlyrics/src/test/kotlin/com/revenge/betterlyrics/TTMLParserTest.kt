package com.revenge.betterlyrics

import com.revenge.betterlyrics.models.TTMLResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TTMLParserTest {
  private val delta = 1e-9

  private fun document(content: String): String =
    """<?xml version="1.0" encoding="UTF-8"?>
    <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
      <body><div>$content</div></body>
    </tt>""".trimIndent()

  @Test
  fun `parses a paragraph into lines and timed words`() {
    val lines =
      TTMLParser.parseTTML(
        document(
          """<p begin="00:00:01.500">""" +
            """<span begin="00:00:01.500" end="00:00:02.000">Hello</span> """ +
            """<span begin="00:00:02.000" end="00:00:02.500">world</span></p>"""
        )
      )

    assertEquals(1, lines.size)
    val line = lines.single()
    assertEquals("Hello world", line.text)
    assertEquals(1.5, line.startTime, delta)
    assertEquals(2, line.words.size)
    assertEquals("Hello", line.words[0].text)
    assertEquals(1.5, line.words[0].startTime, delta)
    assertEquals(2.0, line.words[0].endTime, delta)
    assertEquals("world", line.words[1].text)
    assertEquals(2.5, line.words[1].endTime, delta)
    assertNull(line.agent)
    assertTrue(line.backgroundLines.isEmpty())
  }

  @Test
  fun `merges adjacent spans without whitespace into one word`() {
    val line =
      TTMLParser.parseTTML(
          document(
            """<p begin="0">""" +
              """<span begin="0.0" end="0.1">Hel</span>""" +
              """<span begin="0.1" end="0.2">lo</span></p>"""
          )
        )
        .single()

    assertEquals("Hello", line.text)
    assertEquals(1, line.words.size)
    assertEquals("Hello", line.words.single().text)
    assertEquals(0.0, line.words.single().startTime, delta)
    assertEquals(0.2, line.words.single().endTime, delta)
  }

  @Test
  fun `extracts background vocals as separate lines`() {
    val line =
      TTMLParser.parseTTML(
          document(
            """<p begin="00:00:03.000">""" +
              """<span begin="00:00:03.000" end="00:00:03.500">Main</span>""" +
              """<span ttm:role="x-bg" begin="00:00:03.200">""" +
              """<span begin="00:00:03.200" end="00:00:03.400">echo</span>""" +
              """</span></p>"""
          )
        )
        .single()

    assertEquals("Main", line.text)
    assertEquals(1, line.backgroundLines.size)
    val background = line.backgroundLines.single()
    assertEquals("echo", background.text)
    assertEquals(3.2, background.startTime, delta)
    assertTrue(background.isBackground)
  }

  @Test
  fun `ignores translation and romanization spans`() {
    val line =
      TTMLParser.parseTTML(
          document(
            """<p begin="0">""" +
              """<span begin="0" end="0.5">Song</span>""" +
              """<span ttm:role="x-translation" begin="0" end="0.5">歌</span>""" +
              """<span ttm:role="x-roman" begin="0" end="0.5">song</span>""" +
              """</p>"""
          )
        )
        .single()

    assertEquals("Song", line.text)
    assertEquals(1, line.words.size)
    assertEquals("Song", line.words.single().text)
  }

  @Test
  fun `reads the ttm agent from a paragraph`() {
    val line =
      TTMLParser.parseTTML(
          document("""<p begin="0" ttm:agent="v1"><span begin="0" end="1">Hi</span></p>""")
        )
        .single()

    assertEquals("v1", line.agent)
  }

  @Test
  fun `falls back to direct text when a paragraph has no word spans`() {
    val line =
      TTMLParser.parseTTML(document("""<p begin="0">Just plain text</p>""")).single()

    assertEquals("Just plain text", line.text)
    assertTrue(line.words.isEmpty())
  }

  @Test
  fun `parses hour minute second timestamps`() {
    val line =
      TTMLParser.parseTTML(
          document(
            """<p begin="01:00:05.000">""" +
              """<span begin="01:00:05.000" end="01:00:06.000">x</span></p>"""
          )
        )
        .single()

    assertEquals(3605.0, line.startTime, delta)
  }

  @Test
  fun `parses plain second timestamps`() {
    val line =
      TTMLParser.parseTTML(
          document("""<p begin="12.5"><span begin="12.5" end="13.0">x</span></p>""")
        )
        .single()

    assertEquals(12.5, line.startTime, delta)
  }

  @Test
  fun `skips paragraphs without a begin attribute`() {
    val lines =
      TTMLParser.parseTTML(
        document("""<p><span begin="0" end="1">orphan</span></p><p begin="1">kept</p>""")
      )

    assertEquals(1, lines.size)
    assertEquals("kept", lines.single().text)
  }

  @Test
  fun `returns an empty list for malformed input`() {
    assertTrue(TTMLParser.parseTTML("<tt><body><p begin='0'>oops").isEmpty())
    assertTrue(TTMLParser.parseTTML("").isEmpty())
  }

  @Test
  fun `formats lines, agents, words and background vocals as LRC`() {
    val background =
      TTMLParser.ParsedLine(
        text = "echo",
        startTime = 65.5,
        words = listOf(TTMLParser.ParsedWord("echo", 65.5, 65.7)),
        isBackground = true,
      )
    val line =
      TTMLParser.ParsedLine(
        text = "Hello",
        startTime = 65.234,
        words = listOf(TTMLParser.ParsedWord("Hello", 65.234, 65.9)),
        agent = "v1",
        backgroundLines = listOf(background),
      )

    val expected =
      """
      [01:05.23]{agent:v1}Hello
      <Hello:65.234:65.9>
      [01:05.50]{bg}echo
      <echo:65.5:65.7>
      """
        .trimIndent() + "\n"

    assertEquals(expected, TTMLParser.toLRC(listOf(line)))
  }

  @Test
  fun `emits no word track when a line has no timed words`() {
    val line = TTMLParser.ParsedLine("plain", 0.0, emptyList())
    assertEquals("[00:00.00]plain\n", TTMLParser.toLRC(listOf(line)))
  }

  @Test
  fun `deserializes the endpoint response with unknown keys`() {
    val json = Json { ignoreUnknownKeys = true }
    val response =
      json.decodeFromString<TTMLResponse>("""{"ttml":"<tt/>","unexpected":42}""")

    assertEquals("<tt/>", response.ttml)
  }
}
