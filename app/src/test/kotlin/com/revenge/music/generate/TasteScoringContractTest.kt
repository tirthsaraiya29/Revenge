package com.revenge.music.generate

import com.revenge.core.db.entities.SongPlayStatsEntity
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteScoringContractTest {
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun stats(
        total: Long = 60_000,
        skips: Int = 0,
        lastPlayed: Long = 0,
        key: String = "song|artist",
    ) = SongPlayStatsEntity(
        trackKey = key,
        title = "Song",
        artist = "Artist",
        totalPlayTimeMs = total,
        skipCount = skips,
        lastPlayedAtMillis = lastPlayed,
    )

    @Test
    fun trackKeyTrimsAndLowercases() {
        assertEquals("hello world|some artist", trackKeyOf("  Hello World ", " Some Artist  "))
    }

    @Test
    fun neutralScoreMatchesFormula() {
        val expected = ln(2.0) * PLAY_WEIGHT
        assertEquals(
            expected,
            scoreStats(stats(), emptySet(), "afternoon", now),
            0.000001,
        )
    }

    @Test
    fun likedAddsBonus() {
        val expected = ln(2.0) * PLAY_WEIGHT + LIKED_BONUS
        assertEquals(
            expected,
            scoreStats(stats(), setOf("song|artist"), "afternoon", now),
            0.000001,
        )
    }

    @Test
    fun skipsSubtractPenaltyAndClampAtZero() {
        assertEquals(0.0, scoreStats(stats(skips = 2), emptySet(), "afternoon", now), 0.000001)
    }

    @Test
    fun recentAddsBonus() {
        val expected = ln(2.0) * PLAY_WEIGHT + RECENCY_BONUS
        assertEquals(
            expected,
            scoreStats(stats(lastPlayed = now - day), emptySet(), "afternoon", now),
            0.000001,
        )
    }

    @Test
    fun morningRecentMultiplies() {
        val expected = (ln(2.0) * PLAY_WEIGHT + RECENCY_BONUS) * TIME_OF_DAY_BONUS
        assertEquals(
            expected,
            scoreStats(stats(lastPlayed = now - day), emptySet(), "morning", now),
            0.000001,
        )
    }

    @Test
    fun nightLikedMultiplies() {
        val expected = (ln(2.0) * PLAY_WEIGHT + LIKED_BONUS) * TIME_OF_DAY_BONUS
        assertEquals(
            expected,
            scoreStats(stats(), setOf("song|artist"), "night", now),
            0.000001,
        )
    }

    @Test
    fun candidateFilterTruthTable() {
        assertTrue(applyCandidateFilter(stats(skips = 1)))
        assertTrue(applyCandidateFilter(stats(skips = 5, total = 45_001)))
        assertFalse(applyCandidateFilter(stats(skips = 5, total = 45_000)))
        assertTrue(applyCandidateFilter(stats(skips = 0, total = 0)))
    }

    @Test
    fun timeOfDayBoundaries() {
        assertEquals("night", currentTimeOfDay(5))
        assertEquals("morning", currentTimeOfDay(6))
        assertEquals("morning", currentTimeOfDay(11))
        assertEquals("afternoon", currentTimeOfDay(12))
        assertEquals("afternoon", currentTimeOfDay(17))
        assertEquals("evening", currentTimeOfDay(18))
        assertEquals("evening", currentTimeOfDay(21))
        assertEquals("night", currentTimeOfDay(22))
    }

    @Test
    fun externalBonusesAreAdditive() {
        assertEquals(5.0 + SIMILAR_TRACK_BONUS, scoreCandidateWithExternals(5.0, similarTrack = true), 0.000001)
        assertEquals(
            5.0 + SIMILAR_ARTIST_BONUS + TAG_MATCH_BONUS + GENRE_MATCH_BONUS,
            scoreCandidateWithExternals(
                5.0,
                similarArtist = true,
                tagMatch = true,
                genreMatch = true,
            ),
            0.000001,
        )
    }
}
