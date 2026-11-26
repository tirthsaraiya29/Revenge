package com.revenge.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCutoffGuardTest {

    private val canonical = 200_000L

    @Test
    fun shortfallWithinThresholdAllowsEnd() {
        val guard = PlaybackCutoffGuard()
        assertEquals(CutoffDecision.AllowEnd, guard.verifyTrackCompletion(198_000L, canonical))
    }

    @Test
    fun playerDurationWinsOverLongerCanonicalDuration() {
        val guard = PlaybackCutoffGuard()
        val decision = guard.verifyTrackCompletion(
            currentPositionMs = 150_000L,
            canonicalDurationMs = canonical,
            playerDurationMs = 151_500L,
        )
        assertEquals(CutoffDecision.AllowEnd, decision)
    }

    @Test
    fun unknownCanonicalDurationAllowsEnd() {
        val guard = PlaybackCutoffGuard()
        assertEquals(CutoffDecision.AllowEnd, guard.verifyTrackCompletion(10_000L, -1L))
        assertEquals(CutoffDecision.AllowEnd, guard.verifyTrackCompletion(10_000L, 0L))
    }

    @Test
    fun earlyEndRecoversAtCurrentPosition() {
        val guard = PlaybackCutoffGuard()
        val decision = guard.verifyTrackCompletion(150_000L, canonical)
        assertEquals(CutoffDecision.RecoverPrematureCutoff(150_000L, 1), decision)
    }

    @Test
    fun retryBudgetIsExhaustedAfterConfiguredAttempts() {
        val guard = PlaybackCutoffGuard(maxRetriesPerTrack = 2)
        assertEquals(
            CutoffDecision.RecoverPrematureCutoff(150_000L, 1),
            guard.verifyTrackCompletion(150_000L, canonical),
        )
        assertEquals(
            CutoffDecision.RecoverPrematureCutoff(150_000L, 2),
            guard.verifyTrackCompletion(150_000L, canonical),
        )
        assertEquals(CutoffDecision.AllowEnd, guard.verifyTrackCompletion(150_000L, canonical))
        assertEquals(2, guard.getRetryCount())
    }

    @Test
    fun trackChangeResetsRetryBudget() {
        val guard = PlaybackCutoffGuard(maxRetriesPerTrack = 1)
        guard.verifyTrackCompletion(150_000L, canonical)
        guard.onTrackChanged("track-2")
        assertEquals(0, guard.getRetryCount())
        assertTrue(guard.verifyTrackCompletion(150_000L, canonical) is CutoffDecision.RecoverPrematureCutoff)
    }

    @Test
    fun positionBeyondCanonicalAllowsEnd() {
        val guard = PlaybackCutoffGuard()
        assertEquals(CutoffDecision.AllowEnd, guard.verifyTrackCompletion(250_000L, canonical))
    }

    @Test
    fun negativePositionClampsToZeroInRecovery() {
        val guard = PlaybackCutoffGuard()
        val decision = guard.verifyTrackCompletion(-5_000L, canonical)
        assertEquals(CutoffDecision.RecoverPrematureCutoff(0L, 1), decision)
    }

    @Test
    fun effectiveDurationPrefersCanonicalSeconds() {
        assertEquals(245_000L, PlaybackCutoffGuard.computeEffectiveDuration(245, 123L))
        assertEquals(15_000L, PlaybackCutoffGuard.computeEffectiveDuration(null, 15_000L))
        assertEquals(0L, PlaybackCutoffGuard.computeEffectiveDuration(null, -1L))
        assertEquals(0L, PlaybackCutoffGuard.computeEffectiveDuration(0, C.TIME_UNSET))
        assertEquals(9_000L, PlaybackCutoffGuard.computeEffectiveDuration(-3, 9_000L))
    }
}
