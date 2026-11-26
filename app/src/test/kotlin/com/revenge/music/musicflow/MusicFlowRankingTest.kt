package com.revenge.music.musicflow

import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicFlowRankingTest {
    private fun song(id: String, artist: String, artistId: String? = null) = SongItem(
        id = id,
        title = "Title $id",
        artists = listOf(Artist(artist, artistId)),
        thumbnail = "thumb",
    )

    private fun item(id: String, artist: String, source: MusicFlowSource) =
        MusicFlowItem(song(id, artist), source)

    @Test
    fun candidateFilterRejectsEveryExclusionReason() {
        val items = listOf(
            item("blank", "A", MusicFlowSource.HISTORY).copy(
                song = song("", "A"),
            ),
            item("seen", "A", MusicFlowSource.HISTORY),
            item("recent", "B", MusicFlowSource.HISTORY),
            item("hidden", "C", MusicFlowSource.HISTORY),
            item("excluded", "D", MusicFlowSource.HISTORY),
            item("ok", "E", MusicFlowSource.HISTORY),
            item("ok", "E", MusicFlowSource.LIKED),
        )
        val filter = MusicFlowCandidateFilter(
            seenSongIds = setOf("seen"),
            excludedTrackKeys = setOf(trackKeyOf("Title excluded", "D")),
            hiddenSongIds = setOf("hidden"),
            recentSongIds = setOf("recent"),
        )
        val result = filterMusicFlowCandidates(items, filter)
        assertEquals(listOf("ok"), result.map { it.songId })
    }

    @Test
    fun candidateFilterRejectsDuplicateKeysWithinBatch() {
        val first = item("a", "Same", MusicFlowSource.HISTORY)
        val second = MusicFlowItem(song("b", "Same").copy(title = "Title a"), MusicFlowSource.LIKED)
        val result = filterMusicFlowCandidates(
            listOf(first, second),
            MusicFlowCandidateFilter(),
        )
        assertEquals(listOf("a"), result.map { it.songId })
    }

    @Test
    fun rankingRespectsArtistSpacing() {
        val items = listOf(
            item("1", "A", MusicFlowSource.LIKED),
            item("2", "A", MusicFlowSource.LIKED),
            item("3", "B", MusicFlowSource.LIKED),
            item("4", "C", MusicFlowSource.LIKED),
        )
        val ranked = rankMusicFlowCandidates(
            items,
            MusicFlowRankingConfig(artistSpacing = 2),
        )
        assertEquals(items.size, ranked.size)
        assertEquals(items.toSet(), ranked.toSet())
        assertEquals("B", ranked[1].song.artists.first().name)
    }

    @Test
    fun rankingBreaksTiesByOriginalIndex() {
        val items = listOf(
            item("1", "A", MusicFlowSource.LIKED),
            item("2", "B", MusicFlowSource.LIKED),
        )
        val ranked = rankMusicFlowCandidates(items, MusicFlowRankingConfig(artistSpacing = 0))
        assertEquals(listOf("1", "2"), ranked.map { it.songId })
    }

    @Test
    fun rankingIsPermutationOfInput() {
        val items = listOf(
            item("1", "A", MusicFlowSource.HISTORY),
            item("2", "B", MusicFlowSource.CATALOG),
            item("3", "C", MusicFlowSource.OFFLINE),
        )
        val ranked = rankMusicFlowCandidates(items)
        assertEquals(items.size, ranked.size)
        assertTrue(ranked.containsAll(items))
    }

    @Test
    fun sourceWeightsMatchSpecification() {
        val weights = MusicFlowRankingConfig.DEFAULT_WEIGHTS
        assertEquals(0.6, weights[MusicFlowSource.HISTORY]!!, 0.0001)
        assertEquals(0.8, weights[MusicFlowSource.LIKED]!!, 0.0001)
        assertEquals(0.5, weights[MusicFlowSource.QUICK_PICKS]!!, 0.0001)
        assertEquals(0.2, weights[MusicFlowSource.CATALOG]!!, 0.0001)
        assertEquals(0.4, weights[MusicFlowSource.OFFLINE]!!, 0.0001)
        assertEquals(0.0, weights[MusicFlowSource.NOW_PLAYING]!!, 0.0001)
    }

    @Test
    fun trackKeyNormalisesWhitespaceAndCase() {
        assertEquals("song|artist", trackKeyOf("  Song ", " ARTIST  "))
    }

    @Test
    fun primaryArtistKeyPrefersIdThenLowercaseName() {
        assertEquals("UC1", MusicFlowItem(song("1", "Name", "UC1"), MusicFlowSource.HISTORY)
            .primaryArtistKey)
        assertEquals("name", MusicFlowItem(song("1", "Name"), MusicFlowSource.HISTORY)
            .primaryArtistKey)
    }
}
