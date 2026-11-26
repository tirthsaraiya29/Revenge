package com.revenge.metadata.cleaner

import com.revenge.metadata.models.VersionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataCleanerTest {

  @Test
  fun `strips official video and quality tags`() {
    val result = MetadataCleaner.clean("Alan Walker - Faded (Official Music Video) [4K]", "Alan Walker")

    assertEquals("Faded", result.cleanTitle)
    assertEquals("Alan Walker", result.primaryArtist)
    assertEquals("Alan Walker", result.cleanArtist)
    assertNull(result.version)
    assertTrue(result.junkTags.any { it.contains("Official Music Video", ignoreCase = true) })
    assertTrue(result.junkTags.any { it.contains("4K", ignoreCase = true) })
  }

  @Test
  fun `keeps remix as version info`() {
    val result =
      MetadataCleaner.clean(
        "Illenium - Crawl Outta Love (The Glitch Mob Remix) [Official Audio]",
        "Illenium",
      )

    assertEquals("Crawl Outta Love", result.cleanTitle)
    assertEquals("Illenium", result.primaryArtist)
    assertNotNull(result.version)
    assertEquals(VersionType.REMIX, result.version?.type)
    assertEquals("The Glitch Mob Remix", result.version?.description)
    assertEquals("Crawl Outta Love (The Glitch Mob Remix)", result.toDisplayTitle())
    assertEquals("Crawl Outta Love", result.toSearchQuery().first)
  }

  @Test
  fun `extracts featuring credit from bracket`() {
    val result =
      MetadataCleaner.clean(
        "Major Lazer - Lean On (feat. MØ) (Official Lyric Video)",
        "Major Lazer",
      )

    assertEquals("Lean On", result.cleanTitle)
    assertEquals("Major Lazer", result.primaryArtist)
    assertTrue(result.featuredArtists.contains("MØ"))
  }

  @Test
  fun `scrubs Topic channel suffix`() {
    val result = MetadataCleaner.clean("Yellow", "Coldplay - Topic")

    assertEquals("Yellow", result.cleanTitle)
    assertEquals("Coldplay", result.primaryArtist)
  }

  @Test
  fun `splits camel case VEVO channel name`() {
    val result = MetadataCleaner.clean("Blank Space", "TaylorSwiftVEVO")

    assertEquals("Blank Space", result.cleanTitle)
    assertEquals("Taylor Swift", result.primaryArtist)
  }

  @Test
  fun `removes CJK bracket tags`() {
    val result = MetadataCleaner.clean("RADWIMPS - 前前前世 【Official MV】", "RADWIMPS")

    assertEquals("前前前世", result.cleanTitle)
    assertEquals("RADWIMPS", result.primaryArtist)
  }

  @Test
  fun `removes trailing pipe status`() {
    val result = MetadataCleaner.clean("Song Name | Official Visualizer 2024", "Artist")

    assertEquals("Song Name", result.cleanTitle)
    assertEquals("Artist", result.primaryArtist)
    assertTrue(result.junkTags.any { it.contains("Visualizer", ignoreCase = true) })
  }

  @Test
  fun `detects live version`() {
    val result = MetadataCleaner.clean("Hotel California (Live on MTV 1994) [HD]", "Eagles")

    assertEquals("Hotel California", result.cleanTitle)
    assertEquals(VersionType.LIVE, result.version?.type)
    assertEquals("Live on MTV 1994", result.version?.description)
  }

  @Test
  fun `detects acoustic version`() {
    val result = MetadataCleaner.clean("Take On Me (Acoustic Version)", "a-ha")

    assertEquals("Take On Me", result.cleanTitle)
    assertEquals(VersionType.ACOUSTIC, result.version?.type)
  }

  @Test
  fun `detects slowed reverb version`() {
    val result = MetadataCleaner.clean("After Dark (Slowed + Reverb)", "Mr. Kitty")

    assertEquals("After Dark", result.cleanTitle)
    assertEquals(VersionType.SLOWED, result.version?.type)
    assertEquals("Slowed + Reverb", result.version?.description)
  }

  @Test
  fun `detects extended and instrumental variants`() {
    assertEquals(
      VersionType.EXTENDED,
      MetadataCleaner.clean("Tune (Extended Mix)", "Artist").version?.type,
    )
    assertEquals(
      VersionType.INSTRUMENTAL,
      MetadataCleaner.clean("Tune (Instrumental)", "Artist").version?.type,
    )
  }

  @Test
  fun `splits multiple artists but keeps full display name`() {
    val result = MetadataCleaner.clean("Title", "Calvin Harris & Dua Lipa")

    assertEquals("Calvin Harris", result.primaryArtist)
    assertEquals("Calvin Harris & Dua Lipa", result.cleanArtist)
    assertTrue(result.featuredArtists.contains("Dua Lipa"))
    assertEquals("Calvin Harris & Dua Lipa", result.toSearchQuery().second)
  }

  @Test
  fun `does not peel a bracketed junk suffix into the title`() {
    val result = MetadataCleaner.clean("Alone - (Official Video)", "Marshmello")

    assertEquals("Alone", result.cleanTitle)
    assertEquals("Marshmello", result.primaryArtist)
  }

  @Test
  fun `does not treat junk-only right hand side as title`() {
    val result = MetadataCleaner.clean("Alan Walker - Official Music Video", "Alan Walker - Topic")

    assertEquals("Alan Walker", result.primaryArtist)
    assertTrue(result.cleanTitle != "Official Music Video")
  }

  @Test
  fun `decodes entities and smart quotes`() {
    val result = MetadataCleaner.clean("Rock &amp; Roll &#39;King&#39; “Edition”", "Artist")

    assertEquals("Rock & Roll 'King' \"Edition\"", result.cleanTitle)
  }

  @Test
  fun `strips hashtags into junk tags`() {
    val result = MetadataCleaner.clean("Best Song Ever #shorts #trending", "Artist")

    assertEquals("Best Song Ever", result.cleanTitle)
    assertTrue(result.junkTags.contains("#shorts"))
    assertTrue(result.junkTags.contains("#trending"))
  }

  @Test
  fun `combines featured artists from artist field and title`() {
    val result = MetadataCleaner.clean("Major Lazer & DJ Snake - Lean On feat. MØ", "")

    assertEquals("Lean On", result.cleanTitle)
    assertEquals("Major Lazer", result.primaryArtist)
    assertTrue(result.featuredArtists.contains("DJ Snake"))
    assertTrue(result.featuredArtists.contains("MØ"))
  }

  @Test
  fun `keeps full multi word artist in search query`() {
    assertEquals(
      "Simon & Garfunkel",
      MetadataCleaner.clean("The Sound of Silence", "Simon & Garfunkel").toSearchQuery().second,
    )
    assertEquals(
      "Earth, Wind & Fire",
      MetadataCleaner.clean("September", "Earth, Wind & Fire").toSearchQuery().second,
    )
    assertEquals(
      "Florence and the Machine",
      MetadataCleaner.clean("Dog Days Are Over", "Florence and the Machine").toSearchQuery().second,
    )
  }

  @Test
  fun `leaves clean input unchanged`() {
    val result = MetadataCleaner.clean("Simple Song", "Simple Artist")

    assertEquals("Simple Song", result.cleanTitle)
    assertEquals("Simple Artist", result.primaryArtist)
    assertEquals(listOf<String>(), result.featuredArtists)
    assertEquals(listOf<String>(), result.junkTags)
    assertNull(result.version)
  }

  @Test
  fun `handles empty input`() {
    val result = MetadataCleaner.clean("", "")

    assertEquals("", result.cleanTitle)
    assertEquals("", result.primaryArtist)
    assertEquals("", result.cleanArtist)
    assertTrue(result.featuredArtists.isEmpty())
  }

  @Test
  fun `does not split on dash when artist does not match`() {
    val result = MetadataCleaner.clean("Part - Two", "Someone")

    assertEquals("Part - Two", result.cleanTitle)
    assertEquals("Someone", result.primaryArtist)
  }
}
