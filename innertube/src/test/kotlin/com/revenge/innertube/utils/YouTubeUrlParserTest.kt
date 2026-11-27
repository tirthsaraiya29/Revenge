package com.revenge.innertube.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeUrlParserTest {
  @Test
  fun `extracts video ids from all supported link shapes`() {
    val links =
      listOf(
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDAMVM",
        "https://youtu.be/dQw4w9WgXcQ?t=1",
        "https://www.youtube.com/shorts/dQw4w9WgXcQ",
      )
    links.forEach { assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId(it)) }
  }

  @Test
  fun `extracts artist ids only from music links`() {
    val artist =
      YouTubeUrlParser.parse("https://music.youtube.com/browse/MPREb_abcdef")
        as YouTubeUrlParser.Artist
    assertEquals("MPREb_abcdef", artist.id)
    assertNull(YouTubeUrlParser.parse("https://www.youtube.com/channel/UCabc"))
  }

  @Test
  fun `builds a watch endpoint and rejects non-youtube text`() {
    assertEquals(
      "dQw4w9WgXcQ",
      YouTubeUrlParser.createWatchEndpoint("https://youtu.be/dQw4w9WgXcQ")?.videoId,
    )
    assertTrue(YouTubeUrlParser.isYouTubeUrl("https://youtu.be/dQw4w9WgXcQ"))
    assertNull(YouTubeUrlParser.parse("https://example.com/video"))
  }
}
