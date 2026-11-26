package com.revenge.metadata.cleaner

import com.revenge.metadata.models.VersionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataCleanerTest {

    // ---- junk and brackets -------------------------------------------------

    @Test
    fun `strips bracketed upload tags from a peeled title`() {
        val result = MetadataCleaner.clean(
            "Alan Walker - Faded (Official Music Video) [4K]",
            "Alan Walker",
        )
        assertEquals("Faded", result.cleanTitle)
        assertEquals("Alan Walker", result.primaryArtist)
        assertEquals("Alan Walker", result.cleanArtist)
        assertNull(result.version)
        assertTrue(result.junkTags.any { it.contains("Official Music Video") })
        assertTrue(result.junkTags.any { it.contains("4K") })
    }

    @Test
    fun `removes pipe tail when it carries a hint word`() {
        val result = MetadataCleaner.clean("Song Name | Official Visualizer 2024", "Artist")
        assertEquals("Song Name", result.cleanTitle)
        assertTrue(result.junkTags.any { it.contains("Visualizer", ignoreCase = true) })
    }

    @Test
    fun `keeps pipe tail when it carries no hint`() {
        assertEquals("Song | Other", MetadataCleaner.clean("Song | Other", "Artist").cleanTitle)
    }

    @Test
    fun `extracts every hashtag in order`() {
        val result = MetadataCleaner.clean("Best Song Ever #shorts #trending", "Artist")
        assertEquals("Best Song Ever", result.cleanTitle)
        assertTrue(result.junkTags.contains("#shorts"))
        assertTrue(result.junkTags.contains("#trending"))
    }

    @Test
    fun `strips CJK corner bracket markers`() {
        val result = MetadataCleaner.clean("RADWIMPS - 前前前世 【Official MV】", "RADWIMPS")
        assertEquals("前前前世", result.cleanTitle)
        assertEquals("RADWIMPS", result.primaryArtist)
    }

    @Test
    fun `does not peel a junk only right hand side`() {
        val result = MetadataCleaner.clean("Alone - (Official Video)", "Marshmello")
        assertEquals("Alone", result.cleanTitle)
        assertEquals("Marshmello", result.primaryArtist)
    }

    @Test
    fun `never promotes an upload phrase to the title`() {
        val result = MetadataCleaner.clean("Alan Walker - Official Music Video", "Alan Walker - Topic")
        assertEquals("Alan Walker", result.primaryArtist)
        assertTrue(result.cleanTitle != "Official Music Video")
    }

    @Test
    fun `leaves an unrecognised bracket in place`() {
        assertEquals("Song (Interlude)", MetadataCleaner.clean("Song (Interlude)", "Artist").cleanTitle)
    }

    @Test
    fun `leaves an unbalanced bracket in place`() {
        assertEquals("Song (Live", MetadataCleaner.clean("Song (Live", "Artist").cleanTitle)
    }

    // ---- versions ----------------------------------------------------------

    @Test
    fun `classifies a remix and keeps its description`() {
        val result = MetadataCleaner.clean(
            "Illenium - Crawl Outta Love (The Glitch Mob Remix) [Official Audio]",
            "Illenium",
        )
        assertEquals("Crawl Outta Love", result.cleanTitle)
        assertNotNull(result.version)
        assertEquals(VersionType.REMIX, result.version!!.type)
        assertEquals("The Glitch Mob Remix", result.version!!.description)
        assertEquals("Crawl Outta Love (The Glitch Mob Remix)", result.toDisplayTitle())
        assertEquals("Crawl Outta Love", result.toSearchQuery().first)
    }

    @Test
    fun `classifies a live marker`() {
        val result = MetadataCleaner.clean("Hotel California (Live on MTV 1994) [HD]", "Eagles")
        assertEquals("Hotel California", result.cleanTitle)
        assertEquals(VersionType.LIVE, result.version!!.type)
        assertEquals("Live on MTV 1994", result.version!!.description)
    }

    @Test
    fun `classifies acoustic slowed extended and instrumental markers`() {
        assertEquals(VersionType.ACOUSTIC, MetadataCleaner.clean("Take On Me (Acoustic Version)", "a-ha").version!!.type)
        val slowed = MetadataCleaner.clean("After Dark (Slowed + Reverb)", "Mr. Kitty").version!!
        assertEquals(VersionType.SLOWED, slowed.type)
        assertEquals("Slowed + Reverb", slowed.description)
        assertEquals(VersionType.EXTENDED, MetadataCleaner.clean("Tune (Extended Mix)", "Artist").version!!.type)
        assertEquals(VersionType.INSTRUMENTAL, MetadataCleaner.clean("Tune (Instrumental)", "Artist").version!!.type)
    }

    @Test
    fun `first version wins and later version brackets are removed`() {
        val result = MetadataCleaner.clean("Tune (Remix) (Live)", "Artist")
        assertEquals("Tune", result.cleanTitle)
        assertEquals(VersionType.REMIX, result.version!!.type)
    }

    @Test
    fun `remix precedence beats live`() {
        val result = MetadataCleaner.clean("X (Live Remix)", "Artist")
        assertEquals(VersionType.REMIX, result.version!!.type)
    }

    // ---- featuring and artist splitting -----------------------------------

    @Test
    fun `extracts a bracketed featuring credit`() {
        val result = MetadataCleaner.clean(
            "Major Lazer - Lean On (feat. MØ) (Official Lyric Video)",
            "Major Lazer",
        )
        assertEquals("Lean On", result.cleanTitle)
        assertTrue(result.featuredArtists.contains("MØ"))
    }

    @Test
    fun `peels a dotted title with a blank artist and extracts inline featuring`() {
        val result = MetadataCleaner.clean("Major Lazer & DJ Snake - Lean On feat. MØ", "")
        assertEquals("Lean On", result.cleanTitle)
        assertEquals("Major Lazer", result.primaryArtist)
        assertTrue(result.featuredArtists.contains("DJ Snake"))
        assertTrue(result.featuredArtists.contains("MØ"))
    }

    @Test
    fun `splits artists but preserves the full display string`() {
        val result = MetadataCleaner.clean("Title", "Calvin Harris & Dua Lipa")
        assertEquals("Calvin Harris", result.primaryArtist)
        assertEquals("Calvin Harris & Dua Lipa", result.cleanArtist)
        assertTrue(result.featuredArtists.contains("Dua Lipa"))
        assertEquals("Calvin Harris & Dua Lipa", result.toSearchQuery().second)
    }

    @Test
    fun `artist splitting handles commas and the word and`() {
        assertEquals("Earth", MetadataCleaner.clean("Title", "Earth, Wind & Fire").primaryArtist)
        assertEquals("Earth, Wind & Fire", MetadataCleaner.clean("Title", "Earth, Wind & Fire").toSearchQuery().second)
        assertEquals("Simon", MetadataCleaner.clean("Title", "Simon & Garfunkel").primaryArtist)
        assertEquals("Florence", MetadataCleaner.clean("Title", "Florence and the Machine").primaryArtist)
        assertEquals("Florence and the Machine", MetadataCleaner.clean("Title", "Florence and the Machine").toSearchQuery().second)
    }

    @Test
    fun `featured names equal to the primary artist are dropped and duplicates collapse`() {
        val primaryMatch = MetadataCleaner.clean("Song (feat. Artist)", "Artist")
        assertTrue(primaryMatch.featuredArtists.isEmpty())

        val duplicated = MetadataCleaner.clean("Song (feat. Dua Lipa)", "Calvin Harris & Dua Lipa")
        assertEquals(listOf("Dua Lipa"), duplicated.featuredArtists)
    }

    // ---- channel scrubbing -------------------------------------------------

    @Test
    fun `removes topic suffix`() {
        assertEquals("Coldplay", MetadataCleaner.clean("Yellow", "Coldplay - Topic").primaryArtist)
    }

    @Test
    fun `splits camel case vevo channels`() {
        assertEquals("Taylor Swift", MetadataCleaner.clean("Blank Space", "TaylorSwiftVEVO").primaryArtist)
        assertEquals("Some Band", MetadataCleaner.clean("X", "SomeBandVEVO").primaryArtist)
    }

    @Test
    fun `suppresses camel split for uniformly cased vevo names`() {
        assertEquals("ABC", MetadataCleaner.clean("X", "ABCVEVO").primaryArtist)
        assertEquals("someband", MetadataCleaner.clean("X", "somebandVEVO").primaryArtist)
    }

    @Test
    fun `removes records and official suffixes`() {
        assertEquals("Some Band", MetadataCleaner.clean("X", "Some Band Records").primaryArtist)
        assertEquals("Some Band", MetadataCleaner.clean("X", "Some Band Official").primaryArtist)
    }

    // ---- sanitisation and dash handling -----------------------------------

    @Test
    fun `sanitises entities and smart quotes`() {
        val result = MetadataCleaner.clean("Rock &amp; Roll &#39;King&#39; “Edition”", "Artist")
        assertEquals("Rock & Roll 'King' \"Edition\"", result.cleanTitle)
    }

    @Test
    fun `removes emoji and symbol characters`() {
        val result = MetadataCleaner.clean("Song 🎵 Name", "Art🎤ist")
        assertEquals("Song Name", result.cleanTitle)
        assertEquals("Artist", result.primaryArtist)
    }

    @Test
    fun `does not peel when the candidate artist does not agree`() {
        val result = MetadataCleaner.clean("Part - Two", "Someone")
        assertEquals("Part - Two", result.cleanTitle)
        assertEquals("Someone", result.primaryArtist)
    }

    @Test
    fun `is idempotent on already clean input`() {
        val result = MetadataCleaner.clean("Simple Song", "Simple Artist")
        assertEquals("Simple Song", result.cleanTitle)
        assertEquals("Simple Artist", result.primaryArtist)
        assertTrue(result.featuredArtists.isEmpty())
        assertTrue(result.junkTags.isEmpty())
        assertNull(result.version)
    }

    @Test
    fun `empty input produces an empty clean result`() {
        val result = MetadataCleaner.clean("", "")
        assertEquals("", result.cleanTitle)
        assertEquals("", result.primaryArtist)
        assertEquals("", result.cleanArtist)
        assertTrue(result.featuredArtists.isEmpty())
        assertTrue(result.junkTags.isEmpty())
        assertNull(result.version)
    }

    @Test
    fun `whitespace only input behaves as empty`() {
        val result = MetadataCleaner.clean("   ", "  ")
        assertEquals("", result.cleanTitle)
        assertEquals("", result.primaryArtist)
    }

    @Test
    fun `raw values are echoed byte for byte`() {
        val result = MetadataCleaner.clean("Rock &amp; Roll 🎵", "Art🎤ist")
        assertEquals("Rock &amp; Roll 🎵", result.rawTitle)
        assertEquals("Art🎤ist", result.rawArtist)
    }

    @Test
    fun `malformed input never throws`() {
        val result = MetadataCleaner.clean("((((", "&&&")
        assertNotNull(result.cleanTitle)
        assertNotNull(result.cleanArtist)
    }

    // ---- CleanerRules surface (spec 11.2) --------------------------------

    @Test
    fun `version table exposes the documented precedence sequence`() {
        assertEquals(
            listOf(
                VersionType.REMIX, VersionType.LIVE, VersionType.ACOUSTIC,
                VersionType.REMASTER, VersionType.INSTRUMENTAL, VersionType.COVER,
                VersionType.RADIO_EDIT, VersionType.EXTENDED, VersionType.SLOWED,
                VersionType.SPED_UP,
            ),
            CleanerRules.VERSION_PATTERNS.map { it.second },
        )
    }

    @Test
    fun `artist separators are the documented ordered list`() {
        assertEquals(
            listOf(
                " & ", ", ", " feat. ", " feat ", " ft. ", " ft ",
                " featuring ", " and ", " x ", " X ",
            ),
            CleanerRules.ARTIST_SEPARATORS,
        )
    }

    @Test
    fun `junk patterns match their documented fragments`() {
        val fragments = listOf(
            "(Official Music Video)",
            "[HD]",
            "【Official Video】",
            "「Audio」",
            "Song | Official Video",
            "Song // Audio",
            "#shorts",
            "Song - Official Video",
        )
        assertEquals(fragments.size, CleanerRules.JUNK_PATTERNS.size)
        fragments.forEachIndexed { index, fragment ->
            assertTrue(
                "junk pattern $index should match $fragment",
                CleanerRules.JUNK_PATTERNS[index].containsMatchIn(fragment),
            )
        }
    }

    @Test
    fun `featuring patterns expose the artist text in group one`() {
        assertEquals("X", CleanerRules.FEATURING_PATTERNS[0].find("(feat. X)")!!.groupValues[1])
        assertEquals("Y", CleanerRules.FEATURING_PATTERNS[1].find("[ft. Y]")!!.groupValues[1])
        assertEquals("Z", CleanerRules.FEATURING_PATTERNS[2].find("featuring Z")!!.groupValues[1])
    }

    @Test
    fun `every documented version keyword classifies correctly`() {
        val table = mapOf(
            VersionType.REMIX to listOf(
                "remix", "flip", "bootleg", "vip", "re-edit", "club mix", "dub",
            ),
            VersionType.LIVE to listOf("live", "live at Wembley", "session", "unplugged", "in concert"),
            VersionType.ACOUSTIC to listOf("acoustic", "orchestral", "symphonic"),
            VersionType.REMASTER to listOf("remaster", "remastered", "deluxe", "anniversary"),
            VersionType.INSTRUMENTAL to listOf("instrumental", "karaoke"),
            VersionType.COVER to listOf("cover"),
            VersionType.RADIO_EDIT to listOf("radio edit", "edit"),
            VersionType.EXTENDED to listOf("extended", "extended mix"),
            VersionType.SLOWED to listOf("slowed", "slowed + reverb", "reverb"),
            VersionType.SPED_UP to listOf("sped up", "nightcore"),
        )
        for ((type, keywords) in table) {
            for (keyword in keywords) {
                val result = MetadataCleaner.clean("Tune ($keyword)", "Artist")
                assertEquals("keyword $keyword", type, result.version!!.type)
            }
        }
    }

    @Test
    fun `version-only right hand side is not peeled`() {
        assertEquals(
            "Artist - Live at Wembley",
            MetadataCleaner.clean("Artist - Live at Wembley", "Artist").cleanTitle,
        )
        assertEquals(
            "Artist - Slowed + Reverb",
            MetadataCleaner.clean("Artist - Slowed + Reverb", "Artist").cleanTitle,
        )
    }

    @Test
    fun `live at and slowed plus reverb are whole-entry version entries`() {
        assertTrue(CleanerRules.VERSION_PATTERNS.any { it.first.matches("Live at Wembley") })
        assertTrue(CleanerRules.VERSION_PATTERNS.any { it.first.matches("Slowed + Reverb") })
    }
}
