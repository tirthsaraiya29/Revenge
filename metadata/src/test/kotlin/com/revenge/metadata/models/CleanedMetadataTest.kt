package com.revenge.metadata.models

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanedMetadataTest {

  private fun metadata(version: VersionInfo?) =
    CleanedMetadata(
      rawTitle = "Whatever",
      rawArtist = "Somebody",
      cleanTitle = "Faded",
      primaryArtist = "Alan Walker",
      version = version,
      junkTags = emptyList(),
    )

  @Test
  fun `display title reattaches non original version`() {
    assertEquals(
      "Faded (Live at Wembley)",
      metadata(VersionInfo(VersionType.LIVE, "Live at Wembley")).toDisplayTitle(),
    )
  }

  @Test
  fun `display title ignores original version marker`() {
    assertEquals(
      "Faded",
      metadata(VersionInfo(VersionType.ORIGINAL, "Original")).toDisplayTitle(),
    )
  }

  @Test
  fun `display title without version is the clean title`() {
    assertEquals("Faded", metadata(null).toDisplayTitle())
  }

  @Test
  fun `search query uses clean title and clean artist`() {
    val meta =
      CleanedMetadata(
        rawTitle = "Faded (Remix) feat. Someone",
        rawArtist = "Alan Walker & Someone",
        cleanTitle = "Faded",
        primaryArtist = "Alan Walker",
        cleanArtist = "Alan Walker & Someone",
        featuredArtists = listOf("Someone"),
        version = VersionInfo(VersionType.REMIX, "Remix"),
      )

    assertEquals("Faded" to "Alan Walker & Someone", meta.toSearchQuery())
  }

  @Test
  fun `clean artist defaults to primary artist`() {
    assertEquals("Alan Walker", metadata(null).cleanArtist)
  }
}
