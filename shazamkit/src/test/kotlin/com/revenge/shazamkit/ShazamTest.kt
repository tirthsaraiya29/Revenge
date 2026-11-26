package com.revenge.shazamkit

import com.revenge.shazamkit.models.RecognitionStatus
import com.revenge.shazamkit.models.ShazamResponseJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShazamTest {

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  private fun decode(payload: String): ShazamResponseJson =
    json.decodeFromString(ShazamResponseJson.serializer(), payload)

  @Test
  fun `maps a complete discovery response`() {
    val response =
      decode(
        """
        {
          "tagid": "tag-fallback",
          "track": {
            "key": "track-key",
            "title": "Song Title",
            "subtitle": "Artist Name",
            "url": "https://www.shazam.com/track/1",
            "isrc": "US1234567890",
            "images": {
              "coverart": "https://images/400x400.jpg",
              "coverarthq": "https://images/1000x1000.jpg"
            },
            "genres": { "primary": "Pop" },
            "sections": [
              null,
              {
                "type": "SONG",
                "metadata": [
                  { "title": "Album", "text": "Album Name" },
                  { "title": "Label", "text": "Label Name" },
                  { "title": "Released", "text": "2020-01-01" }
                ]
              },
              { "type": "LYRICS", "text": ["first line", "second line"] }
            ],
            "hub": {
              "options": [
                {
                  "type": "APPLE_MUSIC",
                  "providername": "Apple Music",
                  "actions": [{ "uri": "https://music.apple.com/song/1" }]
                },
                {
                  "type": "video",
                  "providername": "YouTube",
                  "actions": [{ "uri": "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10" }]
                }
              ],
              "providers": [
                {
                  "caption": "Spotify",
                  "actions": [{ "uri": "https://open.spotify.com/track/abc" }]
                }
              ]
            }
          }
        }
        """,
      )

    val result = response.toRecognitionResult()

    requireNotNull(result)
    assertEquals("track-key", result.trackId)
    assertEquals("Song Title", result.title)
    assertEquals("Artist Name", result.artist)
    assertEquals("Album Name", result.album)
    assertEquals("Label Name", result.label)
    assertEquals("2020-01-01", result.releaseDate)
    assertEquals(listOf("first line", "second line"), result.lyrics)
    assertEquals("https://images/400x400.jpg", result.coverArtUrl)
    assertEquals("https://images/1000x1000.jpg", result.coverArtHqUrl)
    assertEquals("Pop", result.genre)
    assertEquals("https://www.shazam.com/track/1", result.shazamUrl)
    assertEquals("https://music.apple.com/song/1", result.appleMusicUrl)
    assertEquals("https://open.spotify.com/track/abc", result.spotifyUrl)
    assertEquals("US1234567890", result.isrc)
    assertEquals("dQw4w9WgXcQ", result.youtubeVideoId)
  }

  @Test
  fun `response without a track has no result`() {
    assertNull(decode("""{"tagid":"only-tag","matches":[]}""").toRecognitionResult())
  }

  @Test
  fun `minimal track tolerates missing optional data`() {
    val result = decode("""{"track":{"title":"T","subtitle":"A"}}""").toRecognitionResult()

    requireNotNull(result)
    assertEquals("", result.trackId)
    assertEquals("T", result.title)
    assertEquals("A", result.artist)
    assertNull(result.album)
    assertNull(result.label)
    assertNull(result.releaseDate)
    assertNull(result.lyrics)
    assertNull(result.genre)
    assertNull(result.youtubeVideoId)
  }

  @Test
  fun `track id falls back to tag id when key is absent or blank`() {
    val absent = decode("""{"tagid":"tag-9","track":{"title":"T"}}""").toRecognitionResult()
    val blank =
      decode("""{"tagid":"tag-9","track":{"key":"   ","title":"T"}}""").toRecognitionResult()

    assertEquals("tag-9", absent?.trackId)
    assertEquals("tag-9", blank?.trackId)
  }

  @Test
  fun `extracts youtube id from common url shapes`() {
    assertEquals(
      "dQw4w9WgXcQ",
      extractYouTubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
    )
    assertEquals(
      "dQw4w9WgXcQ",
      extractYouTubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=abc"),
    )
    assertEquals(
      "dQw4w9WgXcQ",
      extractYouTubeVideoId("https://youtu.be/dQw4w9WgXcQ"),
    )
    assertEquals(
      "dQw4w9WgXcQ",
      extractYouTubeVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"),
    )
    assertNull(extractYouTubeVideoId(null))
    assertNull(extractYouTubeVideoId(""))
    assertNull(extractYouTubeVideoId("https://www.youtube.com/watch?v=tooShort"))
  }

  @Test
  fun `maps http statuses to readable failures`() {
    assertEquals("No match found", httpErrorFor(404)?.message)
    assertEquals("Too many requests", httpErrorFor(429)?.message)
    assertTrue(httpErrorFor(503)?.message.orEmpty().contains("temporarily unavailable"))
    assertTrue(httpErrorFor(400)?.message.orEmpty().contains("400"))
    assertNull(httpErrorFor(200))
  }

  @Test
  fun `rejects invalid input before touching the network`() = runBlocking {
    val blank = Shazam.recognize("", 1_000)
    val badDuration = Shazam.recognize("signature", 0)

    assertTrue(blank.exceptionOrNull() is IllegalArgumentException)
    assertTrue(badDuration.exceptionOrNull() is IllegalArgumentException)
  }

  @Test
  fun `no match status has a default message`() {
    assertEquals("No matches found", (RecognitionStatus.NoMatch() as RecognitionStatus.NoMatch).message)
  }
}
