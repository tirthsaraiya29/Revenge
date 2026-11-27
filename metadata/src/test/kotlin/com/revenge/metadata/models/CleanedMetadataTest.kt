package com.revenge.metadata.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CleanedMetadataTest {

  private fun sample(version: VersionInfo? = null): CleanedMetadata =
    CleanedMetadata(
      rawTitle = "Faded",
      rawArtist = "Alan Walker",
      cleanTitle = "Faded",
      primaryArtist = "Alan Walker",
      version = version,
    )

  @Test
  fun `display title appends the version description`() {
    assertEquals(
      "Faded (Live at Wembley)",
      sample(VersionInfo(VersionType.LIVE, "Live at Wembley")).toDisplayTitle(),
    )
  }

  @Test
  fun `display title ignores an original marker`() {
    assertEquals(
      "Faded",
      sample(VersionInfo(VersionType.ORIGINAL, "Original")).toDisplayTitle(),
    )
  }

  @Test
  fun `display title with no version is the clean title`() {
    assertEquals("Faded", sample(null).toDisplayTitle())
  }

  @Test
  fun `search query uses clean title and full clean artist`() {
    val result =
      CleanedMetadata(
        rawTitle = "Title",
        rawArtist = "Calvin Harris & Dua Lipa",
        cleanTitle = "Title",
        primaryArtist = "Calvin Harris",
        cleanArtist = "Calvin Harris & Dua Lipa",
      )
    val (title, artist) = result.toSearchQuery()
    assertEquals("Title", title)
    assertEquals("Calvin Harris & Dua Lipa", artist)
  }

  @Test
  fun `clean artist defaults to the primary artist`() {
    val result = CleanedMetadata("T", "A", "T", "A")
    assertEquals("A", result.cleanArtist)
    assertEquals("T" to "A", result.toSearchQuery())
  }

  @Test
  fun `value semantics follow data class defaults`() {
    val first = sample(VersionInfo(VersionType.REMIX, "Some Remix"))
    val second = sample(VersionInfo(VersionType.REMIX, "Some Remix"))
    assertEquals(first, second)
    assertEquals(first.hashCode(), second.hashCode())

    val changed = first.copy(featuredArtists = listOf("Guest"))
    assertEquals("Faded", changed.cleanTitle)
    assertEquals(listOf("Guest"), changed.featuredArtists)
    assertNotEquals(first, changed)
  }
}
