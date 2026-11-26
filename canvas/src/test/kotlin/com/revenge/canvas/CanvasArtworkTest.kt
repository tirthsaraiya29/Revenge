package com.revenge.canvas

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasArtworkTest {

  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  @Test
  fun preferredAnimationUrlPrefersAnimated() {
    assertEquals(
      "https://cdn/animated.mp4",
      CanvasArtwork(animated = "https://cdn/animated.mp4", videoUrl = "https://cdn/fallback.mp4")
        .preferredAnimationUrl,
    )
  }

  @Test
  fun preferredAnimationUrlFallsBackToVideoUrl() {
    assertEquals("https://cdn/fallback.mp4", CanvasArtwork(videoUrl = "https://cdn/fallback.mp4").preferredAnimationUrl)
    assertNull(CanvasArtwork().preferredAnimationUrl)
  }

  @Test
  fun clipBoundariesMarkYoutubeSnippet() {
    assertTrue(CanvasArtwork(clipStartMs = 1_000L).isYouTubeSnippet)
    assertTrue(CanvasArtwork(clipEndMs = 2_000L).isYouTubeSnippet)
  }

  @Test
  fun youtubeHostedUrlsMarkSnippet() {
    assertTrue(CanvasArtwork(videoUrl = "https://rr1.googlevideo.com/videoplayback").isYouTubeSnippet)
    assertTrue(CanvasArtwork(animated = "https://www.youtube.com/watch?v=x").isYouTubeSnippet)
    assertFalse(CanvasArtwork(animated = "https://resources.tidal.com/videos/a/b/c/d/e.mp4").isYouTubeSnippet)
  }

  @Test
  fun decodesPersistedFieldNames() {
    val decoded =
      json.decodeFromString<CanvasArtwork>(
        """
        {
          "name": "Song",
          "artist": "Artist",
          "albumId": "123",
          "albumName": "Album",
          "static": "https://cdn/cover.jpg",
          "animated": "https://cdn/canvas.mp4",
          "clipStartMs": 500,
          "clipEndMs": 5500
        }
        """
          .trimIndent(),
      )

    assertEquals("Song", decoded.name)
    assertEquals("Artist", decoded.artist)
    assertEquals("123", decoded.albumId)
    assertEquals("Album", decoded.albumName)
    assertEquals(500L, decoded.clipStartMs ?: 0L)
    assertEquals(5_500L, decoded.clipEndMs ?: 0L)
    assertEquals("https://cdn/canvas.mp4", decoded.preferredAnimationUrl)
  }

  @Test
  fun roundTripPreservesFields() {
    val original =
      CanvasArtwork(
        name = "Song",
        artist = "Artist",
        albumId = "42",
        albumName = "Album",
        static = "s",
        animated = "a",
        videoUrl = "v",
        clipStartMs = 1L,
        clipEndMs = 2L,
      )
    val decoded =
      json.decodeFromString<CanvasArtwork>(json.encodeToString(CanvasArtwork.serializer(), original))

    assertEquals(original, decoded)
  }

  @Test
  fun missingFieldsDecodeToDefaults() {
    val decoded = json.decodeFromString<CanvasArtwork>("{}")

    assertNull(decoded.name)
    assertNull(decoded.animated)
    assertNull(decoded.clipStartMs)
  }
}
