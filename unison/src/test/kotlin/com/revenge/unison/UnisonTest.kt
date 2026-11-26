package com.revenge.unison

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnisonTest {

  private val entryJson =
    """
    {
      "id": 42,
      "videoId": "dQw4w9WgXcQ",
      "song": "Song",
      "artist": "Artist",
      "lyrics": "[00:01.00]line",
      "format": "lrc",
      "syncType": "synced",
      "score": 1.5,
      "effectiveScore": 1.25,
      "voteCount": 7,
      "confidence": "high",
      "language": "en"
    }
    """

  @Test
  fun `parseSingle decodes a full entry`() {
    val entry = Unison.parseSingle("""{"success":true,"data":$entryJson}""")

    assertEquals(42L, entry?.id)
    assertEquals("dQw4w9WgXcQ", entry?.videoId)
    assertEquals("Song", entry?.song)
    assertEquals("Artist", entry?.artist)
    assertEquals("[00:01.00]line", entry?.lyrics)
    assertEquals("lrc", entry?.format)
    assertEquals("synced", entry?.syncType)
    assertEquals(1.5, entry?.score!!, 0.0)
    assertEquals(1.25, entry?.effectiveScore!!, 0.0)
    assertEquals(7, entry?.voteCount)
    assertEquals("high", entry?.confidence)
    assertEquals("en", entry?.language)
  }

  @Test
  fun `parseSingle applies defaults for optional fields`() {
    val entry =
      Unison.parseSingle(
        """{"success":true,"data":{"id":1,"song":"s","artist":"a","lyrics":"x","format":"lrc","syncType":"plain"}}""",
      )

    assertEquals(0.0, entry?.score!!, 0.0)
    assertEquals(0.0, entry?.effectiveScore!!, 0.0)
    assertEquals(0, entry?.voteCount)
    assertEquals("low", entry?.confidence)
    assertNull(entry?.videoId)
    assertNull(entry?.language)
  }

  @Test
  fun `parseSingle returns null when success is false`() {
    assertNull(Unison.parseSingle("""{"success":false,"data":$entryJson}"""))
  }

  @Test
  fun `parseSingle returns null when lyrics are blank`() {
    val blank = entryJson.replace("[00:01.00]line", "   ")
    assertNull(Unison.parseSingle("""{"success":true,"data":$blank}"""))
  }

  @Test
  fun `parseSingle ignores unknown keys and malformed json`() {
    val withExtra =
      "{\"success\":true,\"pagination\":{\"page\":1},\"data\":" +
        entryJson.replaceFirst("{", "{\"weird\":true,") +
        "}"
    assertEquals(42L, Unison.parseSingle(withExtra)?.id)

    assertNull(Unison.parseSingle("not json"))
    assertNull(Unison.parseSingle(""))
  }

  @Test
  fun `parseSearch keeps valid entries and drops blank lyrics`() {
    val blank = entryJson.replace("[00:01.00]line", "")
    val body = """{"success":true,"data":[$entryJson,$blank]}"""

    val entries = Unison.parseSearch(body)
    assertEquals(1, entries.size)
    assertEquals(42L, entries.first().id)
  }

  @Test
  fun `parseSearch returns empty on failure or bad payload`() {
    assertTrue(Unison.parseSearch("""{"success":false,"data":[$entryJson]}""").isEmpty())
    assertTrue(Unison.parseSearch("""{"success":true}""").isEmpty())
    assertTrue(Unison.parseSearch("<html>oops</html>").isEmpty())
  }

  @Test
  fun `getLyrics fails fast when title or artist is blank`() = runBlocking {
    val missingTitle = Unison.getLyrics(title = "  ", artist = "Artist")
    val missingArtist = Unison.getLyrics(title = "Song", artist = "")

    assertTrue(missingTitle.isFailure)
    assertTrue(missingArtist.isFailure)
  }

  @Test
  fun `getAllLyrics emits nothing for unusable metadata`() = runBlocking {
    var calls = 0
    Unison.getAllLyrics(title = " ", artist = "", callback = { calls++ })
    Unison.getAllLyrics(videoId = "  ", title = "", artist = "", callback = { calls++ })

    assertEquals(0, calls)
  }
}
