package com.revenge.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekButtonTargetTest {

    @Test
    fun appliesSignedOffsets() {
        assertEquals(35_000L, seekButtonTarget(25_000L, 60_000L, 10_000L))
        assertEquals(15_000L, seekButtonTarget(25_000L, 60_000L, -10_000L))
    }

    @Test
    fun clampsToZeroAndDuration() {
        assertEquals(0L, seekButtonTarget(1_000L, 60_000L, -5_000L))
        assertEquals(60_000L, seekButtonTarget(55_000L, 60_000L, 20_000L))
        assertEquals(0L, seekButtonTarget(0L, 0L, 10_000L))
    }

    @Test
    fun unknownDurationHasNoUpperBoundAndNegativePositionClamps() {
        assertEquals(90_000L, seekButtonTarget(80_000L, -1L, 10_000L))
        assertEquals(0L, seekButtonTarget(-20_000L, -1L, 0L))
        assertEquals(5_000L, seekButtonTarget(-20_000L, -1L, 5_000L))
    }

    @Test
    fun overflowNeverWrapsNegative() {
        assertEquals(
            Long.MAX_VALUE,
            seekButtonTarget(Long.MAX_VALUE - 5L, -1L, 10_000L),
        )
    }

    @Test
    fun zeroOffsetIsClampedNoOp() {
        assertEquals(12_000L, seekButtonTarget(12_000L, 60_000L, 0L))
        assertEquals(60_000L, seekButtonTarget(90_000L, 60_000L, 0L))
        assertEquals(0L, seekButtonTarget(-3_000L, 60_000L, 0L))
    }
}
