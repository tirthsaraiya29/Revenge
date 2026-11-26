package com.revenge.music.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUtilsTest {

  @Test
  fun `resize picks maxresdefault for large ytimg requests`() {
    assertEquals(
      "https://i.ytimg.com/vi/abc/maxresdefault.jpg",
      "https://i.ytimg.com/vi/abc/default.jpg".resize(1200, 1200),
    )
  }

  @Test
  fun `resize picks hqdefault for small ytimg requests`() {
    assertEquals(
      "https://i.ytimg.com/vi/abc/hqdefault.jpg",
      "https://i.ytimg.com/vi/abc/maxresdefault.jpg".resize(544, 544),
    )
  }

  @Test
  fun `resize rewrites googleusercontent width`() {
    assertEquals(
      "https://lh3.googleusercontent.com/abc=w1200-h1200",
      "https://lh3.googleusercontent.com/abc=w60-h60".resize(1200, 1200),
    )
  }

  @Test
  fun `resize rewrites ggpht size suffix`() {
    assertEquals(
      "https://yt3.ggpht.com/abc=s544",
      "https://yt3.ggpht.com/abc=s60".resize(544, 544),
    )
  }

  @Test
  fun `resize leaves unknown hosts and null sizes untouched`() {
    val url = "https://example.com/cover.jpg"
    assertEquals(url, url.resize(1200, 1200))
    assertEquals(url, url.resize())
  }

  @Test
  fun `ytimgFallbackUrl walks maxres to hq720 to hqdefault`() {
    assertEquals(
      "https://i.ytimg.com/vi/abc/hq720.jpg",
      ytimgFallbackUrl("https://i.ytimg.com/vi/abc/maxresdefault.jpg"),
    )
    assertEquals(
      "https://i.ytimg.com/vi/abc/hqdefault.jpg",
      ytimgFallbackUrl("https://i.ytimg.com/vi/abc/hq720.jpg"),
    )
    assertNull(ytimgFallbackUrl("https://i.ytimg.com/vi/abc/hqdefault.jpg"))
  }

  @Test
  fun `ytimgFallbackUrl ignores non ytimg urls`() {
    assertNull(ytimgFallbackUrl("https://example.com/maxresdefault.jpg"))
  }
}
