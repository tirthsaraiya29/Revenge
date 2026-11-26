package com.revenge.simpmusic

import com.revenge.simpmusic.models.SimpMusicApiResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsModelsTest {
  private val json =
    Json {
      isLenient = true
      ignoreUnknownKeys = true
      explicitNulls = false
    }

  @Test
  fun `decodes the response envelope and api field names`() {
    val payload =
      """
      {
        "type": "success",
        "data": [{
          "id": "1",
          "videoId": "abc",
          "songTitle": "Title",
          "artistName": "Artist",
          "albumName": "Album",
          "durationSeconds": 201,
          "syncedLyrics": "[00:01.00]line",
          "plainLyric": "line",
          "richSyncLyrics": "{}",
          "vote": 4,
          "unknownField": true
        }]
      }
      """.trimIndent()

    val response = json.decodeFromString<SimpMusicApiResponse>(payload)

    assertTrue(response.success)
    assertEquals(1, response.data.size)
    val lyrics = response.data.single()
    assertEquals("1", lyrics.id)
    assertEquals("abc", lyrics.videoId)
    assertEquals("Title", lyrics.title)
    assertEquals("Artist", lyrics.artist)
    assertEquals("Album", lyrics.album)
    assertEquals(201, lyrics.duration)
    assertEquals("[00:01.00]line", lyrics.syncedLyrics)
    assertEquals("line", lyrics.plainLyrics)
    assertEquals("{}", lyrics.richSyncLyrics)
    assertEquals(4, lyrics.vote)
  }

  @Test
  fun `non success type is not successful`() {
    val response = json.decodeFromString<SimpMusicApiResponse>("""{"type":"error"}""")
    assertFalse(response.success)
    assertTrue(response.data.isEmpty())
    assertEquals("error", response.type)
    assertTrue(json.decodeFromString<SimpMusicApiResponse>("{}").data.isEmpty())
  }

  @Test
  fun `missing fields decode to null defaults`() {
    val response =
      json.decodeFromString<SimpMusicApiResponse>("""{"type":"success","data":[{}]}""")
    val lyrics = response.data.single()
    assertNull(lyrics.id)
    assertNull(lyrics.title)
    assertNull(lyrics.duration)
    assertNull(lyrics.plainLyrics)
  }
}
