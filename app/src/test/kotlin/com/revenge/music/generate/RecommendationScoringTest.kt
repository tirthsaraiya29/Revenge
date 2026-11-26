package com.revenge.music.generate

import com.revenge.core.db.entities.SongPlayStatsEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationScoringTest {

  @Test
  fun testSimilarTrackBonusAppliesCorrectly() {
    val base = 10.0
    val scored = scoreCandidateWithExternals(base, similarTrack = true)
    assertEquals(12.0, scored, 0.001)
  }

  @Test
  fun testSimilarArtistBonusAppliesCorrectly() {
    val base = 10.0
    val scored = scoreCandidateWithExternals(base, similarArtist = true)
    assertEquals(11.0, scored, 0.001)
  }

  @Test
  fun testTagMatchBonusAppliesCorrectly() {
    val base = 10.0
    val scored = scoreCandidateWithExternals(base, tagMatch = true)
    assertEquals(11.5, scored, 0.001)
  }

  @Test
  fun testGenreMatchBonusAppliesCorrectly() {
    val base = 10.0
    val scored = scoreCandidateWithExternals(base, genreMatch = true)
    assertEquals(11.0, scored, 0.001)
  }

  @Test
  fun testAllExternalBonusesAreAdditive() {
    val base = 10.0
    val scored =
      scoreCandidateWithExternals(
        baseScore = base,
        similarTrack = true,
        similarArtist = true,
        tagMatch = true,
      )
    // 10.0 + 2.0 (track) + 1.0 (artist) + 1.5 (tag) = 14.5
    assertEquals(14.5, scored, 0.001)
  }

  @Test
  fun testAllSlice3ExternalBonusesAreAdditive() {
    val base = 10.0
    val scored =
      scoreCandidateWithExternals(
        baseScore = base,
        similarTrack = true,
        similarArtist = true,
        tagMatch = true,
        genreMatch = true,
      )
    // 10.0 + 2.0 (track) + 1.0 (artist) + 1.5 (tag) + 1.0 (genre) = 15.5
    assertEquals(15.5, scored, 0.001)
  }

  @Test
  fun testDegradedModeScoresIdenticalToLocalScore() {
    val stats =
      SongPlayStatsEntity(
        trackKey = "song|artist",
        title = "Song",
        artist = "Artist",
        totalPlayTimeMs = 120_000L,
        playCount = 4,
        skipCount = 0,
        lastPlayedAtMillis = System.currentTimeMillis(),
      )
    val localScore = scoreStats(stats, likedKeys = emptySet(), timeOfDay = "afternoon")

    // With no external signals, external scoring must equal local score exactly
    val candidateScore =
      scoreCandidateWithExternals(
        baseScore = localScore,
        similarTrack = false,
        similarArtist = false,
        tagMatch = false,
      )
    assertEquals(localScore, candidateScore, 0.001)
  }
}
