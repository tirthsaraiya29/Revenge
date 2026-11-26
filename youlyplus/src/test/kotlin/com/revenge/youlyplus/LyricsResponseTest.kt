package com.revenge.youlyplus

import com.revenge.youlyplus.models.LyricsResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val json = Json {
  isLenient = true
  ignoreUnknownKeys = true
}

class LyricsResponseTest {
  @Test
  fun `decodes the lrclib style payload and ignores unknown keys`() {
    val fixture = """
      {
        "id": 1,
        "syncedLyrics": "[00:01.00]synced",
        "plainLyrics": "plain",
        "type": "lyrics",
        "unknown": {"nested": true}
      }
    """.trimIndent()

    val response = json.decodeFromString<LyricsResponse>(fixture)

    assertEquals(1, response.id)
    assertEquals("[00:01.00]synced", response.syncedLyrics)
    assertEquals("plain", response.plainLyrics)
    assertNull(response.lyrics)
  }

  @Test
  fun `decodes the kpoe style payload`() {
    val fixture = """
      {
        "lyrics": [
          {
            "text": "hello",
            "time": 1000,
            "duration": 500,
            "syllabus": [
              {"text": "hel", "time": 1000, "duration": 200, "isBackground": false}
            ]
          }
        ]
      }
    """.trimIndent()

    val response = json.decodeFromString<LyricsResponse>(fixture)

    val item = response.lyrics?.single()
    assertEquals("hello", item?.text)
    assertEquals(1000L, item?.time)
    assertEquals("hel", item?.syllabus?.single()?.text)
  }

  @Test
  fun `empty payload leaves every field null`() {
    val response = json.decodeFromString<LyricsResponse>("{}")
    assertNull(response.id)
    assertNull(response.syncedLyrics)
    assertNull(response.plainLyrics)
    assertNull(response.lyrics)
  }

  @Test
  fun `best lyrics prefers synced then kpoe then plain`() {
    val synced = LyricsResponse(syncedLyrics = "synced", plainLyrics = "plain")
    assertEquals("synced", synced.bestLyrics())

    val kpoe = LyricsResponse(
      syncedLyrics = "  ",
      plainLyrics = "plain",
      lyrics = listOf(com.revenge.youlyplus.models.LyricsItem(text = "kpoe", time = 0)),
    )
    assertEquals("[00:00.000]kpoe", kpoe.bestLyrics())

    val plain = LyricsResponse(syncedLyrics = "", plainLyrics = "plain")
    assertEquals("plain", plain.bestLyrics())
  }

  @Test
  fun `all blank documents produce no lyrics`() {
    val response = LyricsResponse(syncedLyrics = "  ", plainLyrics = "")
    assertNull(response.bestLyrics())
  }
}
