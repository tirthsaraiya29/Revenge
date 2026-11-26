package com.revenge.innertube

import com.revenge.innertube.models.Run
import com.revenge.innertube.pages.PodcastPage
import com.revenge.innertube.utils.YouTubeUrlParser
import com.revenge.innertube.utils.parseCookieString
import com.revenge.innertube.utils.parseTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parsing helpers have no network or Android dependency, so they are unit tested directly. */
class ParsersTest {
  @Test
  fun cookieStringKeepsValuesContainingEquals() {
    val parsed = parseCookieString("SAPISID=abc; malformed; token=x=y")
    assertEquals(mapOf("SAPISID" to "abc", "token" to "x=y"), parsed)
  }

  @Test
  fun cookieStringOfEmptyInputIsEmpty() {
    assertTrue(parseCookieString("").isEmpty())
  }

  @Test
  fun timeParsesMinutesAndHours() {
    assertEquals(200, "03:20".parseTime())
    assertEquals(3900, "1:05:00".parseTime())
    assertNull("no time".parseTime())
    assertNull("1:2:3:4".parseTime())
  }

  @Test
  fun urlParserFindsVideoIds() {
    assertEquals(
      "dQw4w9WgXcQ",
      (YouTubeUrlParser.parse("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1") as
          YouTubeUrlParser.ParsedUrl.Video)
        .id,
    )
    assertEquals(
      "dQw4w9WgXcQ",
      (YouTubeUrlParser.parse("https://youtu.be/dQw4w9WgXcQ") as YouTubeUrlParser.ParsedUrl.Video).id,
    )
  }

  @Test
  fun urlParserFindsArtistOnMusicHostOnly() {
    assertEquals(
      "UC123",
      (YouTubeUrlParser.parse("https://music.youtube.com/channel/UC123") as
          YouTubeUrlParser.ParsedUrl.Artist)
        .id,
    )
    assertNull(YouTubeUrlParser.parse("https://www.youtube.com/channel/UC123"))
    assertNull(YouTubeUrlParser.parse("just some words"))
    assertFalse(YouTubeUrlParser.isYouTubeUrl("not a link"))
    assertTrue(YouTubeUrlParser.isYouTubeUrl("https://youtu.be/dQw4w9WgXcQ"))
  }

  @Test
  fun podcastDurationsParseLocalisedUnits() {
    assertEquals(23 * 60, PodcastPage.parseDuration(listOf(Run("23 min", null))))
    assertEquals(3900, PodcastPage.parseDuration(listOf(Run("1 h 5 min", null))))
    assertEquals(3900, PodcastPage.parseDuration(listOf(Run("1:05:00", null))))
    assertEquals(45, PodcastPage.parseDuration(listOf(Run("45 s", null))))
    assertNull(PodcastPage.parseDuration(listOf(Run("unknown", null))))
    assertNull(PodcastPage.parseDuration(listOf(Run("0 min", null))))
  }
}
