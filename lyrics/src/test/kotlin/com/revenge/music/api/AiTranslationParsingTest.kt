package com.revenge.music.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiTranslationParsingTest {

    @Test
    fun `extracts openai message content`() {
        val body = """{"choices":[{"message":{"content":"  hello  "}}],"extra":1}"""
        assertEquals("hello", extractContent(body))
    }

    @Test
    fun `extracts and concatenates anthropic content blocks`() {
        val body = """
            {"content":[{"type":"text","text":"Hello "},{"type":"text","text":"world"}],"id":"x"}
        """.trimIndent()
        assertEquals("Hello world", extractContent(body))
    }

    @Test
    fun `ignores blocks without string text`() {
        val body = """{"content":[{"type":"tool_use"},{"text":"ok"}]}"""
        assertEquals("ok", extractContent(body))
    }

    @Test
    fun `malformed bodies yield null`() {
        assertNull(extractContent("not json"))
        assertNull(extractContent("{}"))
        assertNull(extractContent("""{"choices":[]}"""))
    }

    @Test
    fun `parses a plain json array`() {
        assertEquals(listOf("a", "b"), parseLines("""["a","b"]""", 2))
    }

    @Test
    fun `recovers from fenced output`() {
        val fenced = "Here you go:\n```json\n[\"a\",\"b\"]\n```\nDone"
        assertEquals(listOf("a", "b"), parseLines(fenced, 2))
    }

    @Test
    fun `pads missing lines with empty strings`() {
        assertEquals(listOf("a", ""), parseLines("""["a"]""", 2))
    }

    @Test
    fun `truncates surplus lines`() {
        assertEquals(listOf("a"), parseLines("""["a","b","c"]""", 1))
    }

    @Test
    fun `non string elements become empty strings`() {
        assertEquals(listOf("1", "", "", "x"), parseLines("""[1,null,{"a":1},"x"]""", 4))
    }

    @Test
    fun `loose fallback recovers bracketed lines`() {
        val loose = "prefix [\n\"one\",\n\"two\"\n] suffix"
        assertEquals(listOf("one", "two"), parseLines(loose, 2))
    }

    @Test
    fun `no bracket and no array yields null`() {
        assertNull(parseLines("nothing here", 2))
        assertNull(parseLines(null, 2))
    }

    @Test
    fun `normalize keeps the expected size`() {
        assertEquals(listOf("a", ""), normalize(listOf("a"), 2))
        assertEquals(listOf("a"), normalize(listOf("a", "b"), 1))
        assertEquals(emptyList<String>(), normalize(emptyList(), 0))
    }
}
