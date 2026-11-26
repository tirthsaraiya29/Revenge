package com.revenge.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCutoffGuardTest {

  private fun guard() = PlaybackCutoffGuard(thresholdMs = 3_000L, maxRetriesPerTrack = 2)

  @Test
  fun shortfallWithinThresholdIsARealEnd() {
    val guard = guard()
    guard.onTrackChanged("song_1")
    assertSame(
      CutoffDecision.AllowEnd,
      guard.verifyTrackCompletion(currentPositionMs = 198_500L, canonicalDurationMs = 200_000L),
    )
  }

  @Test
  fun decoderDurationWinsOverLongerMetadata() {
    val guard = guard()
    guard.onTrackChanged("song_2")
    assertSame(
      CutoffDecision.AllowEnd,
      guard.verifyTrackCompletion(
        currentPositionMs = 194_000L,
        canonicalDurationMs = 210_000L,
        playerDurationMs = 195_000L,
      ),
    )
  }

  @Test
  fun unknownDurationAlwaysAllowsEnd() {
    val guard = guard()
    guard.onTrackChanged("live")
    assertSame(CutoffDecision.AllowEnd, guard.verifyTrackCompletion(1_000L, -1L))
    assertSame(CutoffDecision.AllowEnd, guard.verifyTrackCompletion(1_000L, 0L))
  }

  @Test
  fun largeShortfallRecoversFromTheCurrentPosition() {
    val guard = guard()
    guard.onTrackChanged("song_3")
    val decision = guard.verifyTrackCompletion(currentPositionMs = 150_000L, canonicalDurationMs = 200_000L)
    assertTrue(decision is CutoffDecision.RecoverPrematureCutoff)
    val recovery = decision as CutoffDecision.RecoverPrematureCutoff
    assertEquals(150_000L, recovery.resumePositionMs)
    assertEquals(1, recovery.retryAttempt)
  }

  @Test
  fun retriesAreBoundedAndThenAllowedToEnd() {
    val guard = guard()
    guard.onTrackChanged("broken")

    val first = guard.verifyTrackCompletion(100_000L, 200_000L)
    assertEquals(1, (first as CutoffDecision.RecoverPrematureCutoff).retryAttempt)
    val second = guard.verifyTrackCompletion(120_000L, 200_000L)
    assertEquals(2, (second as CutoffDecision.RecoverPrematureCutoff).retryAttempt)
    assertSame(CutoffDecision.AllowEnd, guard.verifyTrackCompletion(125_000L, 200_000L))
  }

  @Test
  fun newTrackResetsTheRetryBudget() {
    val guard = guard()
    guard.onTrackChanged("a")
    guard.verifyTrackCompletion(100_000L, 200_000L)
    guard.verifyTrackCompletion(100_000L, 200_000L)

    guard.onTrackChanged("b")
    val decision = guard.verifyTrackCompletion(100_000L, 200_000L)
    assertEquals(1, (decision as CutoffDecision.RecoverPrematureCutoff).retryAttempt)
  }

  @Test
  fun effectiveDurationPrefersCanonicalThenPlayer() {
    assertEquals(245_000L, PlaybackCutoffGuard.computeEffectiveDuration(245, 123L))
    assertEquals(15_000L, PlaybackCutoffGuard.computeEffectiveDuration(null, 15_000L))
    assertEquals(0L, PlaybackCutoffGuard.computeEffectiveDuration(null, -1L))
  }
}
