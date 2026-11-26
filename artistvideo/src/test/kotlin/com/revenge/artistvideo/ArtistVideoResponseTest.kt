package com.revenge.artistvideo

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistVideoResponseTest {
  private val json =
    Json {
      ignoreUnknownKeys = true
      isLenient = true
      explicitNulls = false
    }

  @Test
  fun decodesAllDocumentedFields() {
    val payload =
      """
      {
        "name": "Song",
        "artist": "Artist",
        "albumId": "album-1",
        "static": "https://cdn/static.jpg",
        "animated": "https://cdn/anim.m3u8",
        "videoUrl": "https://cdn/video.mp4"
      }
      """

    val response = json.decodeFromString(ArtistVideoResponse.serializer(), payload)

    assertEquals("Song", response.name)
    assertEquals("Artist", response.artist)
    assertEquals("album-1", response.albumId)
    assertEquals("https://cdn/static.jpg", response.static)
    assertEquals("https://cdn/anim.m3u8", response.animated)
    assertEquals("https://cdn/video.mp4", response.videoUrl)
  }

  @Test
  fun missingFieldsDecodeToNull() {
    val response = json.decodeFromString(ArtistVideoResponse.serializer(), "{}")

    assertNull(response.name)
    assertNull(response.artist)
    assertNull(response.albumId)
    assertNull(response.static)
    assertNull(response.animated)
    assertNull(response.videoUrl)
    assertNull(response.preferredAnimationUrl)
  }

  @Test
  fun explicitNullsAndUnknownKeysAreTolerated() {
    val payload =
      """
      {"name": null, "albumId": null, "extra": 42, "nested": {"a": true}}
      """

    val response = json.decodeFromString(ArtistVideoResponse.serializer(), payload)

    assertNull(response.name)
    assertNull(response.albumId)
    assertNull(response.preferredAnimationUrl)
  }

  @Test
  fun prefersAnimatedOverVideoUrl() {
    val response =
      ArtistVideoResponse(
        animated = "https://cdn/anim.m3u8",
        videoUrl = "https://cdn/video.mp4",
      )

    assertEquals("https://cdn/anim.m3u8", response.preferredAnimationUrl)
  }

  @Test
  fun fallsBackToVideoUrlWhenAnimatedMissing() {
    val response = ArtistVideoResponse(animated = null, videoUrl = "https://cdn/video.mp4")

    assertEquals("https://cdn/video.mp4", response.preferredAnimationUrl)
  }

  @Test
  fun blankAnimatedStillWins() {
    // Callers filter blanks themselves; the getter only falls back on null.
    val response = ArtistVideoResponse(animated = "", videoUrl = "https://cdn/video.mp4")

    assertEquals("", response.preferredAnimationUrl)
  }

  @Test
  fun nullWhenNeitherAnimationFieldPresent() {
    val response = ArtistVideoResponse()

    assertNull(response.preferredAnimationUrl)
  }
}
