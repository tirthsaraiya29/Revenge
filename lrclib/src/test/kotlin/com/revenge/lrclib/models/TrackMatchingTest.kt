package com.revenge.lrclib.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val json = Json {
  isLenient = true
  ignoreUnknownKeys = true
}

private fun track(
  id: Int,
  duration: Double,
  name: String = "Song",
  artist: String = "Artist",
  plain: String? = null,
  synced: String? = null,
): Track = Track(id, name, artist, duration, plain, synced)

class TrackMatchingTest {
  @Test
  fun `strict matching accepts two seconds and rejects five`() {
    val tracks = listOf(track(1, 100.0), track(2, 110.0))
    assertEquals(1, tracks.bestMatchingFor(102)?.id)
    assertNull(tracks.bestMatchingFor(105))
  }

  @Test
  fun `relaxed matching prefers the closest synced track`() {
    val tracks = listOf(track(1, 100.0, plain = "plain"), track(2, 103.0, synced = "synced"))
    assertEquals(2, tracks.bestMatchingForRelaxed(101)?.id)
  }

  @Test
  fun `relaxed matching picks the closest and rejects out of range`() {
    val tracks = listOf(track(1, 100.0), track(2, 104.0))
    assertEquals(2, tracks.bestMatchingForRelaxed(103)?.id)
    assertNull(tracks.bestMatchingForRelaxed(120))
  }

  @Test
  fun `unknown duration prefers synced tracks`() {
    val tracks = listOf(track(1, 10.0), track(2, 20.0, synced = "synced"))
    assertEquals(2, tracks.bestMatchingFor(DURATION_UNKNOWN)?.id)
    assertEquals(2, tracks.bestMatchingForRelaxed(DURATION_UNKNOWN)?.id)
  }

  @Test
  fun `unknown duration with names uses similarity`() {
    val tracks = listOf(track(1, 10.0, name = "Hello", artist = "Adele"), track(2, 20.0))
    assertEquals(1, tracks.bestMatchingFor(DURATION_UNKNOWN, "Hello", "Adele")?.id)
    assertNull(tracks.bestMatchingFor(DURATION_UNKNOWN, "Completely Different", "Nobody At All"))
  }

  @Test
  fun `empty lists match nothing`() {
    val empty = emptyList<Track>()
    assertNull(empty.bestMatchingFor(10))
    assertNull(empty.bestMatchingForRelaxed(10))
    assertNull(empty.bestMatchingFor(DURATION_UNKNOWN, "Song", "Artist"))
  }

  @Test
  fun `track decodes with unknown keys ignored`() {
    val fixture =
      """
      {
        "id": 7,
        "trackName": "Song",
        "artistName": "Artist",
        "duration": 123.4,
        "plainLyrics": null,
        "syncedLyrics": "[00:01.00]You were the shadow",
        "instrumental": false,
        "lang": "en"
      }
      """
        .trimIndent()

    val decoded = json.decodeFromString<Track>(fixture)

    assertEquals(7, decoded.id)
    assertNull(decoded.plainLyrics)
    assertEquals("[00:01.00]You were the shadow", decoded.syncedLyrics)
  }

  @Test
  fun `similarity handles equality emptiness containment and distance`() {
    assertEquals(1.0, similarity("", ""), 0.0)
    assertEquals(1.0, similarity("Song", " song "), 0.0)
    assertEquals(0.0, similarity("Song", ""), 0.0)
    assertEquals(0.8, similarity("Hello World", "Hello"), 0.0)
    assertEquals(0.75, similarity("abcd", "abce"), 0.0)
  }
}
