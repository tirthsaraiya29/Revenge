package com.revenge.music.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyMapperTest {

  @Test
  fun normalisationIsDeterministicAndReducesNoise() {
    val normalised = SpotifyMapper.normalize("Song (feat. X) [Live] - Remastered 2011!")
    assertEquals("song remastered 2011", normalised)
    assertEquals(normalised, SpotifyMapper.normalize("Song (feat. X) [Live] - Remastered 2011!"))
  }

  @Test
  fun removesFeaturingBracketsRemasterAndRemix() {
    assertEquals("track", SpotifyMapper.normalize("Track (ft. Someone) [Bonus]"))
    assertEquals("track", SpotifyMapper.normalize("Track (2011 Remaster)"))
    assertEquals("track", SpotifyMapper.normalize("Track (Club Remix)"))
  }

  @Test
  fun identicalTitleAndArtistWithNoDurationDifferenceScoresOne() {
    val score =
      SpotifyMapper.matchScore(
        spotifyTitle = "Song",
        spotifyArtist = "Artist",
        spotifyDurationMs = 200_000,
        candidateTitle = "Song",
        candidateArtist = "Artist",
        candidateDurationSec = 200,
      )
    assertEquals(1.0, score, 0.0001)
  }

  @Test
  fun largeDurationDifferenceRemovesDurationComponent() {
    val score =
      SpotifyMapper.matchScore(
        spotifyTitle = "Song",
        spotifyArtist = "Artist",
        spotifyDurationMs = 100_000,
        candidateTitle = "Song",
        candidateArtist = "Artist",
        candidateDurationSec = 131,
      )
    assertEquals(0.8, score, 0.0001)
  }

  @Test
  fun absentDurationUsesNeutralComponent() {
    val score =
      SpotifyMapper.matchScore(
        spotifyTitle = "Song",
        spotifyArtist = "Artist",
        spotifyDurationMs = 100_000,
        candidateTitle = "Song",
        candidateArtist = "Artist",
        candidateDurationSec = null,
      )
    assertEquals(0.9, score, 0.0001)
  }

  @Test
  fun containmentGuaranteesAtLeastSixtyPercent() {
    assertTrue(SpotifyMapper.titleSimilarity("Song", "Song (Extended)") >= 0.6)
    assertTrue(SpotifyMapper.titleSimilarity("Completely Different", "Other") < 0.6)
    assertEquals(0.95, SpotifyMapper.earlyExitThreshold(), 0.0)
  }

  @Test
  fun searchQueryUsesFirstArtistThenTitle() {
    val track =
      com.revenge.music.spotify.models.SpotifyTrack(
        name = "Song",
        artists =
          listOf(
            com.revenge.music.spotify.models.SpotifySimpleArtist(name = "First"),
            com.revenge.music.spotify.models.SpotifySimpleArtist(name = "Second"),
          ),
      )
    assertEquals("First Song", SpotifyMapper.buildSearchQuery(track))
  }
}
