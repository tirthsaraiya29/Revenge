package com.revenge.metadata.cleaner

import com.revenge.metadata.models.VersionType
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataCleanerTest {

    private fun clean(title: String, artist: String = "") = MetadataCleaner.clean(title, artist)

    // §11.2 CleanerRules surface

    @Test
    fun versionPatternPrecedenceMatchesSpec() {
        assertEquals(
            listOf(
                VersionType.REMIX,
                VersionType.LIVE,
                VersionType.ACOUSTIC,
                VersionType.REMASTER,
                VersionType.INSTRUMENTAL,
                VersionType.COVER,
                VersionType.RADIO_EDIT,
                VersionType.EXTENDED,
                VersionType.SLOWED,
                VersionType.SPED_UP,
            ),
            CleanerRules.VERSION_PATTERNS.map { it.second },
        )
    }

    @Test
    fun artistSeparatorsMatchSpec() {
        assertEquals(
            listOf(" & ", ", ", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " and ", " x ", " X "),
            CleanerRules.ARTIST_SEPARATORS,
        )
    }

    @Test
    fun versionKeywordsClassifyAsDocumented() {
        val cases = listOf(
            "Remix" to VersionType.REMIX,
            "Flip" to VersionType.REMIX,
            "Bootleg" to VersionType.REMIX,
            "VIP" to VersionType.REMIX,
            "Re-edit" to VersionType.REMIX,
            "Club Mix" to VersionType.REMIX,
            "Dub" to VersionType.REMIX,
            "Live" to VersionType.LIVE,
            "Session" to VersionType.LIVE,
            "Unplugged" to VersionType.LIVE,
            "In Concert" to VersionType.LIVE,
            "Acoustic" to VersionType.ACOUSTIC,
            "Orchestral" to VersionType.ACOUSTIC,
            "Symphonic" to VersionType.ACOUSTIC,
            "Remastered" to VersionType.REMASTER,
            "Deluxe" to VersionType.REMASTER,
            "Anniversary" to VersionType.REMASTER,
            "Instrumental" to VersionType.INSTRUMENTAL,
            "Karaoke" to VersionType.INSTRUMENTAL,
            "Cover" to VersionType.COVER,
            "Radio Edit" to VersionType.RADIO_EDIT,
            "Edit" to VersionType.RADIO_EDIT,
            "Extended" to VersionType.EXTENDED,
            "Extended Mix" to VersionType.EXTENDED,
            "Slowed" to VersionType.SLOWED,
            "Slowed + Reverb" to VersionType.SLOWED,
            "Reverb" to VersionType.SLOWED,
            "Sped Up" to VersionType.SPED_UP,
            "Nightcore" to VersionType.SPED_UP,
        )
        for ((keyword, expected) in cases) {
            val actual = CleanerRules.VERSION_PATTERNS.first { it.first.containsMatchIn(keyword) }.second
            assertEquals("keyword=$keyword", expected, actual)
        }
    }

    @Test
    fun remixTakesPrecedenceOverLive() {
        val actual = CleanerRules.VERSION_PATTERNS.first { it.first.containsMatchIn("Remix Live") }.second
        assertEquals(VersionType.REMIX, actual)
    }

    @Test
    fun junkPatternsMatchTheirFragments() {
        val fragments = listOf(
            "(Official Music Video)",
            "[HD]",
            "【Official MV】",
            "「Official Audio」",
            "Song | Official Video",
            "Song // Official Video",
            "#shorts",
            "Song - Official Video",
        )
        fragments.forEachIndexed { index, fragment ->
            assertTrue("pattern $index", CleanerRules.JUNK_PATTERNS[index].containsMatchIn(fragment))
        }
    }

    @Test
    fun featuringPatternsExposeCaptureGroup() {
        val parenthesised = CleanerRules.FEATURING_PATTERNS[0].find("Song (feat. X)")!!
        assertEquals("X", parenthesised.groupValues[1])

        val bracketed = CleanerRules.FEATURING_PATTERNS[1].find("Song [ft. Y]")!!
        assertEquals("Y", bracketed.groupValues[1])

        val inline = CleanerRules.FEATURING_PATTERNS[2].find("Song featuring Z")!!
        assertEquals("Z", inline.groupValues[1].trim())
    }

    // §11.3 Junk and brackets

    @Test
    fun removesBracketedUploadJunk() {
        val result = clean("Alan Walker - Faded (Official Music Video) [4K]", "Alan Walker")
        assertEquals("Faded", result.cleanTitle)
        assertEquals("Alan Walker", result.primaryArtist)
        assertEquals("Alan Walker", result.cleanArtist)
        assertNull(result.version)
        assertTrue(result.junkTags.any { it.contains("Official Music Video", ignoreCase = true) })
        assertTrue(result.junkTags.any { it.contains("4K", ignoreCase = true) })
    }

    @Test
    fun removesPipeTrailer() {
        val result = clean("Song Name | Official Visualizer 2024", "Artist")
        assertEquals("Song Name", result.cleanTitle)
        assertTrue(result.junkTags.any { it.contains("Visualizer", ignoreCase = true) })
    }

    @Test
    fun removesHashtags() {
        val result = clean("Best Song Ever #shorts #trending", "Artist")
        assertEquals("Best Song Ever", result.cleanTitle)
        assertTrue(result.junkTags.contains("#shorts"))
        assertTrue(result.junkTags.contains("#trending"))
    }

    @Test
    fun removesCjkCornerBracketJunk() {
        val result = clean("RADWIMPS - 前前前世 【Official MV】", "RADWIMPS")
        assertEquals("前前前世", result.cleanTitle)
        assertEquals("RADWIMPS", result.primaryArtist)
    }

    @Test
    fun doesNotPeelJunkOnlyRightSide() {
        val result = clean("Alone - (Official Video)", "Marshmello")
        assertEquals("Alone", result.cleanTitle)
        assertEquals("Marshmello", result.primaryArtist)
    }

    @Test
    fun doesNotPeelWhenRightSideIsJunkWords() {
        val result = clean("Alan Walker - Official Music Video", "Alan Walker - Topic")
        assertEquals("Alan Walker", result.primaryArtist)
        assertTrue(result.cleanTitle != "Official Music Video")
    }

    // §11.4 Versions

    @Test
    fun extractsRemixVersion() {
        val result = clean(
            "Illenium - Crawl Outta Love (The Glitch Mob Remix) [Official Audio]",
            "Illenium",
        )
        assertEquals("Crawl Outta Love", result.cleanTitle)
        assertEquals(VersionType.REMIX, result.version!!.type)
        assertEquals("The Glitch Mob Remix", result.version!!.description)
        assertEquals("Crawl Outta Love (The Glitch Mob Remix)", result.toDisplayTitle())
        assertEquals("Crawl Outta Love", result.toSearchQuery().first)
    }

    @Test
    fun extractsLiveVersion() {
        val result = clean("Hotel California (Live on MTV 1994) [HD]", "Eagles")
        assertEquals("Hotel California", result.cleanTitle)
        assertEquals(VersionType.LIVE, result.version!!.type)
        assertEquals("Live on MTV 1994", result.version!!.description)
    }

    @Test
    fun extractsAcousticVersion() {
        assertEquals(VersionType.ACOUSTIC, clean("Take On Me (Acoustic Version)", "a-ha").version!!.type)
    }

    @Test
    fun extractsSlowedVersion() {
        val result = clean("After Dark (Slowed + Reverb)", "Mr. Kitty")
        assertEquals(VersionType.SLOWED, result.version!!.type)
        assertEquals("Slowed + Reverb", result.version!!.description)
    }

    @Test
    fun extractsExtendedAndInstrumental() {
        assertEquals(VersionType.EXTENDED, clean("Tune (Extended Mix)", "Artist").version!!.type)
        assertEquals(VersionType.INSTRUMENTAL, clean("Tune (Instrumental)", "Artist").version!!.type)
    }

    @Test
    fun keepsFirstVersionAndRemovesBothBrackets() {
        val result = clean("Song Name (Remix) (Live)", "Artist")
        assertEquals("Song Name", result.cleanTitle)
        assertEquals(VersionType.REMIX, result.version!!.type)
    }

    // §11.5 Featuring and splitting

    @Test
    fun extractsFeaturedArtistFromBracket() {
        val result = clean("Major Lazer - Lean On (feat. MØ) (Official Lyric Video)", "Major Lazer")
        assertEquals("Lean On", result.cleanTitle)
        assertTrue(result.featuredArtists.contains("MØ"))
    }

    @Test
    fun peelsArtistAndExtractsInlineFeaturing() {
        val result = clean("Major Lazer & DJ Snake - Lean On feat. MØ", "")
        assertEquals("Lean On", result.cleanTitle)
        assertEquals("Major Lazer", result.primaryArtist)
        assertTrue(result.featuredArtists.contains("DJ Snake"))
        assertTrue(result.featuredArtists.contains("MØ"))
    }

    @Test
    fun splitsMultipleArtistsButKeepsFullDisplayArtist() {
        val result = clean("Title", "Calvin Harris & Dua Lipa")
        assertEquals("Calvin Harris", result.primaryArtist)
        assertEquals("Calvin Harris & Dua Lipa", result.cleanArtist)
        assertTrue(result.featuredArtists.contains("Dua Lipa"))
        assertEquals("Calvin Harris & Dua Lipa", result.toSearchQuery().second)
    }

    @Test
    fun keepsFullArtistForSearchQuery() {
        assertEquals("Simon & Garfunkel", clean("X", "Simon & Garfunkel").toSearchQuery().second)
        assertEquals("Earth, Wind & Fire", clean("X", "Earth, Wind & Fire").toSearchQuery().second)
        assertEquals("Florence and the Machine", clean("X", "Florence and the Machine").toSearchQuery().second)
    }

    @Test
    fun removesPrimaryFromFeaturedAndDeduplicates() {
        val sameAsPrimary = clean("Song (feat. Artist)", "Artist")
        assertTrue(sameAsPrimary.featuredArtists.isEmpty())

        val duplicated = clean("Song (feat. Dua Lipa)", "Calvin Harris & Dua Lipa")
        assertEquals(listOf("Dua Lipa"), duplicated.featuredArtists)
    }

    // §11.6 Channel scrubbing

    @Test
    fun scrubsTopicSuffix() {
        assertEquals("Coldplay", clean("Yellow", "Coldplay - Topic").primaryArtist)
    }

    @Test
    fun scrubsVevoSuffixWithCamelSplit() {
        assertEquals("Taylor Swift", clean("Blank Space", "TaylorSwiftVEVO").primaryArtist)
    }

    @Test
    fun scrubsChannelSuffixes() {
        assertEquals("Some Band", clean("X", "SomeBandVEVO").primaryArtist)
        assertEquals("ABC", clean("X", "ABCVEVO").primaryArtist)
        assertEquals("Some Band", clean("X", "Some Band Records").primaryArtist)
        assertEquals("Some Band", clean("X", "Some Band Official").primaryArtist)
    }

    // §11.7 Sanitisation and dash handling

    @Test
    fun sanitisesEntitiesAndTypographicQuotes() {
        val result = clean("Rock &amp; Roll &#39;King&#39; “Edition”", "Artist")
        assertEquals("Rock & Roll 'King' \"Edition\"", result.cleanTitle)
    }

    @Test
    fun removesEmojiFromTitleAndArtist() {
        val result = clean("Song 🎵 Name", "Artist 🎤")
        assertEquals("Song Name", result.cleanTitle)
        assertEquals("Artist", result.primaryArtist)
    }

    @Test
    fun doesNotPeelUnrelatedArtistPrefix() {
        val result = clean("Part - Two", "Someone")
        assertEquals("Part - Two", result.cleanTitle)
        assertEquals("Someone", result.primaryArtist)
    }

    @Test
    fun isIdempotentOnCleanInput() {
        val result = clean("Simple Song", "Simple Artist")
        assertEquals("Simple Song", result.cleanTitle)
        assertEquals("Simple Artist", result.primaryArtist)
        assertTrue(result.featuredArtists.isEmpty())
        assertTrue(result.junkTags.isEmpty())
        assertNull(result.version)
    }

    @Test
    fun handlesEmptyInput() {
        val result = clean("", "")
        assertEquals("", result.cleanTitle)
        assertEquals("", result.primaryArtist)
        assertEquals("", result.cleanArtist)
        assertTrue(result.featuredArtists.isEmpty())
    }

    @Test
    fun echoesRawArguments() {
        val result = MetadataCleaner.clean("Rock &amp; Roll 🎵", "Some &amp; Artist")
        assertEquals("Rock &amp; Roll 🎵", result.rawTitle)
        assertEquals("Some &amp; Artist", result.rawArtist)
    }

    // §11.9 Non-functional

    @Test
    fun neverThrowsOnMalformedInput() {
        val inputs = listOf("", "   ", "(", "]", "&", "&#;", "&#xZZ;", "(((", "【】", "「", "#", "|", "//", " - ")
        for (input in inputs) {
            val result = MetadataCleaner.clean(input, input)
            assertEquals(input, result.rawTitle)
            ApiText.clean(input)
        }
    }

    @Test
    fun concurrentCallsMatchSequentialResults() {
        val inputs = listOf(
            "Alan Walker - Faded (Official Music Video) [4K]" to "Alan Walker",
            "Hotel California (Live on MTV 1994) [HD]" to "Eagles",
            "Major Lazer & DJ Snake - Lean On feat. MØ" to "",
        )
        val expected = inputs.map { (title, artist) -> MetadataCleaner.clean(title, artist) }

        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = inputs.map { (title, artist) ->
                Callable { MetadataCleaner.clean(title, artist) }
            }.toList()
            val repeated = pool.invokeAll(tasks + tasks + tasks).map { it.get() }
            repeated.forEachIndexed { index, actual ->
                assertEquals(expected[index % inputs.size], actual)
            }
        } finally {
            pool.shutdown()
        }
    }
}
