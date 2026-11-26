package com.revenge.lrclib.models

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMatchingTest {

  private fun track(
    id: Int,
    duration: Double,
    trackName: String = "Song $id",
    artistName: String = "Artist",
    synced: String? = null,
    plain: String? = "plain",
  ) =
    Track(
      id = id,
      trackName = trackName,
      artistName = artistName,
      duration = duration,
      plainLyrics = plain,
      syncedLyrics = synced,
    )

  @Test
  fun `strict matching rejects durations outside the two second window`() {
    val tracks = listOf(track(1, 100.0), track(2, 110.0))

    assertEquals(1, tracks.bestMatchingFor(102)?.id)
    assertNull(tracks.bestMatchingFor(105))
  }

  @Test
  fun `relaxed matching prefers synced lyrics within tolerance`() {
    val tracks =
      listOf(
        track(1, 100.0, plain = "plain"),
        track(2, 103.0, synced = "[00:01.00]synced"),
      )

    assertEquals(2, tracks.bestMatchingForRelaxed(101)?.id)
  }

  @Test
  fun `relaxed matching falls back to the closest track then rejects far ones`() {
    val tracks = listOf(track(1, 100.0), track(2, 104.0))

    assertEquals(2, tracks.bestMatchingForRelaxed(103)?.id)
    assertNull(tracks.bestMatchingForRelaxed(120))
  }

  @Test
  fun `unknown duration prefers the first synced result`() {
    val tracks = listOf(track(1, 10.0), track(2, 20.0, synced = "[00:01.00]x"))

    assertEquals(2, tracks.bestMatchingFor(DURATION_UNKNOWN)?.id)
    assertEquals(2, tracks.bestMatchingForRelaxed(DURATION_UNKNOWN)?.id)
  }

  @Test
  fun `unknown duration favours the best title and artist match`() {
    val tracks =
      listOf(
        track(1, 10.0, trackName = "Hello", artistName = "Adele", synced = "[00:01.00]x"),
        track(2, 20.0, trackName = "Some Other Song", artistName = "Nobody Else"),
      )

    assertEquals(1, tracks.bestMatchingFor(DURATION_UNKNOWN, "hello", "adele")?.id)
    assertNull(tracks.bestMatchingFor(DURATION_UNKNOWN, "unrelated", "stranger"))
  }

  @Test
  fun `empty lists never match`() {
    assertNull(emptyList<Track>().bestMatchingFor(10))
    assertNull(emptyList<Track>().bestMatchingForRelaxed(10))
    assertNull(emptyList<Track>().bestMatchingFor(DURATION_UNKNOWN, "a", "b"))
  }

  @Test
  fun `track deserialises from the lrclib payload shape`() {
    val payload =
      """
      {
        "id": 42,
        "trackName": "Faded",
        "artistName": "Alan Walker",
        "duration": 212.0,
        "plainLyrics": null,
        "syncedLyrics": "[00:01.00]You were the shadow",
        "instrumental": false,
        "lang": "en"
      }
      """
        .trimIndent()

    val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<Track>(payload)

    assertEquals(42, parsed.id)
    assertEquals("Faded", parsed.trackName)
    assertEquals("Alan Walker", parsed.artistName)
    assertEquals(212.0, parsed.duration, 0.0)
    assertNull(parsed.plainLyrics)
    assertEquals("[00:01.00]You were the shadow", parsed.syncedLyrics)
  }
}
