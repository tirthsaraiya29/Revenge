package com.revenge.youlyplus

import com.revenge.youlyplus.models.LyricsResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsResponseTest {

  private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
  }

  @Test
  fun `parses lrclib style payload and ignores unknown keys`() {
    val payload =
      """{"id":1,"syncedLyrics":"[00:00.000]hi","plainLyrics":"hi","extra":{"x":1}}"""
    val response = json.decodeFromString<LyricsResponse>(payload)
    assertEquals("[00:00.000]hi", response.syncedLyrics)
    assertEquals("hi", response.plainLyrics)
  }

  @Test
  fun `parses kpoe style lines`() {
    val payload =
      """{"lyrics":[{"text":"hi","time":1000,"syllabus":[{"text":"hi","time":1000}]}],"type":"rich"}"""
    val response = json.decodeFromString<LyricsResponse>(payload)
    assertEquals(1, response.lyrics?.size)
    assertEquals("hi", response.lyrics?.first()?.syllabus?.first()?.text)
  }

  @Test
  fun `missing optional fields stay null`() {
    val response = json.decodeFromString<LyricsResponse>("{}")
    assertNull(response.syncedLyrics)
    assertNull(response.lyrics)
    assertNull(response.duration)
  }
}
