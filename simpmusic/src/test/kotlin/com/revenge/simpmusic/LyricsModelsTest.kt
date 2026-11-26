package com.revenge.simpmusic

import com.revenge.simpmusic.models.LyricsData
import com.revenge.simpmusic.models.SimpMusicApiResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val json = Json {
  isLenient = true
  ignoreUnknownKeys = true
}

class LyricsModelsTest {
  @Test
  fun `decodes wire names and ignores unknown keys`() {
    val fixture = """
      {
        "type": "success",
        "data": [
          {
            "id": "id1",
            "videoId": "video1",
            "songTitle": "Song",
            "artistName": "Artist",
            "albumName": "Album",
            "durationSeconds": 120,
            "syncedLyrics": "[00:01.00]synced",
            "plainLyric": "plain",
            "richSyncLyrics": "rich",
            "vote": 3,
            "unknown": true
          }
        ]
      }
    """.trimIndent()

    val response = json.decodeFromString<SimpMusicApiResponse>(fixture)

    assertTrue(response.success)
    val data = response.data.single()
    assertEquals("id1", data.id)
    assertEquals("video1", data.videoId)
    assertEquals("Song", data.title)
    assertEquals("Artist", data.artist)
    assertEquals("Album", data.album)
    assertEquals(120, data.duration)
    assertEquals("[00:01.00]synced", data.syncedLyrics)
    assertEquals("plain", data.plainLyrics)
    assertEquals("rich", data.richSyncLyrics)
    assertEquals(3, data.vote)
  }

  @Test
  fun `empty envelope is unsuccessful with no data`() {
    val response = json.decodeFromString<SimpMusicApiResponse>("{}")
    assertFalse(response.success)
    assertTrue(response.data.isEmpty())
  }

  @Test
  fun `missing lyric fields decode to null`() {
    val data = json.decodeFromString<LyricsData>("""{"songTitle":"Only title"}""")
    assertEquals("Only title", data.title)
    assertNull(data.syncedLyrics)
    assertNull(data.plainLyrics)
    assertNull(data.richSyncLyrics)
    assertNull(data.duration)
  }
}
