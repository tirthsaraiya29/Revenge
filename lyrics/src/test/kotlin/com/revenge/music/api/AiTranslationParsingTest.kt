package com.revenge.music.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationParsingTest {
  @Test
  fun `url safety rejects non http schemes and metadata hosts`() {
    assertTrue(OpenRouterService.isUrlSafe(""))
    assertTrue(OpenRouterService.isUrlSafe("https://openrouter.ai/api/v1/chat/completions"))
    assertTrue(OpenRouterService.isUrlSafe("http://localhost:11434/v1/chat/completions"))
    assertFalse(OpenRouterService.isUrlSafe("file:///etc/passwd"))
    assertFalse(OpenRouterService.isUrlSafe("https://169.254.169.254/latest/meta-data"))
    assertFalse(OpenRouterService.isUrlSafe("http://metadata.google.internal/"))
    assertFalse(OpenRouterService.isUrlSafe("not a url"))
  }

  @Test
  fun `openai and anthropic content shapes are extracted`() {
    val openAi = """{"choices":[{"message":{"role":"assistant","content":"hello"}}]}"""
    assertEquals("hello", AiTranslationParsing.extractContent(openAi))

    val anthropic = """{"content":[{"type":"text","text":"he"},{"type":"text","text":"llo"}]}"""
    assertEquals("hello", AiTranslationParsing.extractContent(anthropic))

    assertNull(AiTranslationParsing.extractContent(""))
  }

  @Test
  fun `plain json arrays are parsed and normalised`() {
    assertEquals(
      listOf("a", "b"),
      AiTranslationParsing.parseLines("""["a","b"]""", 2),
    )
    // Surplus lines are dropped.
    assertEquals(
      listOf("a"),
      AiTranslationParsing.parseLines("""["a","b"]""", 1),
    )
    // Missing lines are padded with empty strings.
    assertEquals(
      listOf("a", ""),
      AiTranslationParsing.parseLines("""["a"]""", 2),
    )
  }

  @Test
  fun `fenced and prose wrapped arrays are recovered`() {
    assertEquals(
      listOf("one", "two"),
      AiTranslationParsing.parseLines("```json\n[\"one\",\"two\"]\n```", 2),
    )
    assertEquals(
      listOf("one", "two"),
      AiTranslationParsing.parseLines("""Sure, here you go: ["one","two"] enjoy""", 2),
    )
  }

  @Test
  fun `unparseable content returns null`() {
    assertNull(AiTranslationParsing.parseLines("no array here", 2))
  }
}
