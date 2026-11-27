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
    isLenient = true
  }

  @Test
  fun ca1_prefersAnimatedOverVideo() {
    val artwork = CanvasArtwork(animated = "https://cdn/a.mp4", videoUrl = "https://cdn/v.mp4")
    assertEquals("https://cdn/a.mp4", artwork.preferredAnimationUrl)
  }

  @Test
  fun ca2_fallsBackToVideoAndEmptyIsNull() {
    assertEquals(
      "https://cdn/v.mp4",
      CanvasArtwork(videoUrl = "https://cdn/v.mp4").preferredAnimationUrl,
    )
    assertNull(CanvasArtwork().preferredAnimationUrl)
  }

  @Test
  fun ca3_blankAnimatedStillWins() {
    assertEquals("", CanvasArtwork(animated = "", videoUrl = "v").preferredAnimationUrl)
  }

  @Test
  fun ca4_clipWindowMarksYouTubeSnippet() {
    assertTrue(CanvasArtwork(clipStartMs = 1000).isYouTubeSnippet)
    assertTrue(CanvasArtwork(clipEndMs = 2000).isYouTubeSnippet)
  }

  @Test
  fun ca5_youtubeHostsMarkSnippet() {
    assertTrue(
      CanvasArtwork(videoUrl = "https://rr1.googlevideo.com/videoplayback").isYouTubeSnippet,
    )
    assertTrue(CanvasArtwork(animated = "https://www.youtube.com/watch?v=x").isYouTubeSnippet)
  }

  @Test
  fun ca6_tidalHostIsNotSnippet() {
    assertFalse(
      CanvasArtwork(animated = "https://resources.tidal.com/videos/a/b/c/d/e.mp4").isYouTubeSnippet,
    )
  }

  @Test
  fun ca7_decodesEveryDocumentedKey() {
    val payload =
      """
      {"name":"N","artist":"A","albumId":"42","albumName":"AN","static":"s",
       "animated":"an","videoUrl":"v","clipStartMs":1,"clipEndMs":2}
      """
        .trimIndent()

    val artwork = json.decodeFromString(CanvasArtwork.serializer(), payload)

    assertEquals("N", artwork.name)
    assertEquals("A", artwork.artist)
    assertEquals("42", artwork.albumId)
    assertEquals("AN", artwork.albumName)
    assertEquals("s", artwork.static)
    assertEquals("an", artwork.animated)
    assertEquals("v", artwork.videoUrl)
    assertEquals(1L, artwork.clipStartMs)
    assertEquals(2L, artwork.clipEndMs)
  }

  @Test
  fun ca8_roundTripsFullyPopulatedInstance() {
    val artwork =
      CanvasArtwork(
        name = "N",
        artist = "A",
        albumId = "42",
        albumName = "AN",
        static = "s",
        animated = "an",
        videoUrl = "v",
        clipStartMs = 1L,
        clipEndMs = 2L,
      )
    val encoded = json.encodeToString(CanvasArtwork.serializer(), artwork)
    assertEquals(artwork, json.decodeFromString(CanvasArtwork.serializer(), encoded))
  }

  @Test
  fun ca9_emptyObjectYieldsAllNulls() {
    val artwork = json.decodeFromString(CanvasArtwork.serializer(), "{}")
    assertEquals(CanvasArtwork(), artwork)
    assertNull(artwork.name)
    assertNull(artwork.albumId)
  }

  @Test
  fun ca10_toleratesUnknownKeysAndExplicitNulls() {
    val artwork =
      json.decodeFromString(
        CanvasArtwork.serializer(),
        """{"name":null,"static":null,"mystery":{"deep":true},"extra":[1,2]}""",
      )
    assertNull(artwork.name)
    assertNull(artwork.static)
  }
}
