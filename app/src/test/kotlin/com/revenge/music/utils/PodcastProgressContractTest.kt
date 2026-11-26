package com.revenge.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastProgressContractTest {
    private fun progress(
        position: Long = 0,
        duration: Long = 1_000,
        finished: Boolean = false,
        dismissed: Boolean = false,
        showBrowseId: String? = null,
    ) = EpisodeProgress(
        id = "episode",
        positionMs = position,
        durationMs = duration,
        finished = finished,
        updatedAt = 0,
        title = "Episode",
        showBrowseId = showBrowseId,
        dismissed = dismissed,
    )

    @Test
    fun nearEndUsesClampedFivePercentWindow() {
        // 10 minutes: window clamped to 60 s.
        assertTrue(PodcastProgress.isNearEnd(540_001, 600_000))
        assertFalse(PodcastProgress.isNearEnd(540_000, 600_000))
        // 100 seconds: window clamped up to 60 s.
        assertTrue(PodcastProgress.isNearEnd(40_001, 100_000))
        // One hour: window clamped to 180 s.
        assertTrue(PodcastProgress.isNearEnd(3_420_001, 3_600_000))
        assertFalse(PodcastProgress.isNearEnd(3_420_000, 3_600_000))
        assertFalse(PodcastProgress.isNearEnd(0, 0))
    }

    @Test
    fun fractionAndRemaining() {
        assertEquals(1f, progress(position = 10, duration = 100, finished = true).fraction)
        assertEquals(0.5f, progress(position = 50, duration = 100).fraction!!, 0.0001f)
        assertNull(progress(position = 50, duration = 0).fraction)
        assertEquals(50L, progress(position = 50, duration = 100).remainingMs)
    }

    @Test
    fun inProgressFlags() {
        assertTrue(progress(position = 100).isInProgress)
        assertFalse(progress(position = 0).isInProgress)
        assertFalse(progress(position = 100, dismissed = true).isInProgress)
        assertFalse(progress(position = 100, finished = true).isInProgress)
        assertTrue(progress(position = 100).asPlayed().dismissed)
    }

    @Test
    fun mislabelledRequiresShortDurationAndNoShowPrefix() {
        assertTrue(progress(duration = 5 * 60_000L).isMislabelled)
        assertFalse(progress(duration = 5 * 60_000L, showBrowseId = "MPSP123").isMislabelled)
        assertFalse(progress(duration = 11 * 60_000L).isMislabelled)
        assertFalse(progress(duration = 0).isMislabelled)
    }

    @Test
    fun speedLabelsTrimTrailingZeros() {
        assertEquals("1x", formatSpeed(1f))
        assertEquals("1.25x", formatSpeed(1.25f))
        assertEquals("1.5x", formatSpeed(1.5f))
    }

    private fun formatSpeed(speed: Float): String {
        val base = if (speed == speed.toInt().toFloat()) {
            speed.toInt().toString()
        } else {
            speed.toString().trimEnd('0').trimEnd('.')
        }
        return "${base}x"
    }
}
