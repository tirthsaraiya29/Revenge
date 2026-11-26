package com.revenge.metadata.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CleanedMetadataTest {

    private fun sample(
        cleanTitle: String = "Faded",
        primaryArtist: String = "Alan Walker",
        version: VersionInfo? = null,
    ) = CleanedMetadata(
        rawTitle = cleanTitle,
        rawArtist = primaryArtist,
        cleanTitle = cleanTitle,
        primaryArtist = primaryArtist,
        version = version,
    )

    @Test
    fun displayTitleAppendsVersionDescription() {
        val result = sample(version = VersionInfo(VersionType.LIVE, "Live at Wembley"))
        assertEquals("Faded (Live at Wembley)", result.toDisplayTitle())
    }

    @Test
    fun displayTitleIgnoresOriginalType() {
        val result = sample(version = VersionInfo(VersionType.ORIGINAL, "Original"))
        assertEquals("Faded", result.toDisplayTitle())
    }

    @Test
    fun displayTitleWithoutVersion() {
        assertEquals("Faded", sample(version = null).toDisplayTitle())
    }

    @Test
    fun searchQueryUsesCleanTitleAndCleanArtist() {
        val result = sample()
        assertEquals("Faded" to "Alan Walker", result.toSearchQuery())
    }

    @Test
    fun cleanArtistDefaultsToPrimaryArtist() {
        val result = CleanedMetadata(
            rawTitle = "Faded",
            rawArtist = "Alan Walker",
            cleanTitle = "Faded",
            primaryArtist = "Alan Walker",
        )
        assertEquals("Alan Walker", result.cleanArtist)
    }

    @Test
    fun searchQueryUsesFullCleanArtist() {
        val result = sample().copy(cleanArtist = "Alan Walker & Ava Max")
        assertEquals("Faded" to "Alan Walker & Ava Max", result.toSearchQuery())
    }

    @Test
    fun defaultsAreEmptyListsAndNullVersion() {
        val result = sample()
        assertEquals(emptyList<String>(), result.featuredArtists)
        assertEquals(emptyList<String>(), result.junkTags)
        assertNull(result.version)
    }

    @Test
    fun valueSemantics() {
        val first = sample(version = VersionInfo(VersionType.REMIX, "Remix"))
        val second = sample(version = VersionInfo(VersionType.REMIX, "Remix"))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, second.copy(cleanTitle = "Other"))
    }

    @Test
    fun copyPreservesUnspecifiedFields() {
        val original = sample(version = VersionInfo(VersionType.REMIX, "Remix"))
            .copy(featuredArtists = listOf("Ava Max"), junkTags = listOf("#shorts"))

        val copied = original.copy(cleanTitle = "New Title")

        assertEquals("New Title", copied.cleanTitle)
        assertEquals("Alan Walker", copied.primaryArtist)
        assertEquals(listOf("Ava Max"), copied.featuredArtists)
        assertEquals(listOf("#shorts"), copied.junkTags)
        assertEquals(VersionType.REMIX, copied.version!!.type)
    }
}
