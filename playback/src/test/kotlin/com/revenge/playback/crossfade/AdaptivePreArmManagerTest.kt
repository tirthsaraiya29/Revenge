package com.revenge.playback.crossfade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdaptivePreArmManagerTest {

    @Test
    fun startsAtBaseLeadTime() {
        assertEquals(20_000L, AdaptivePreArmManager().currentLeadTimeMs)
    }

    @Test
    fun slowPreparationExpandsLeadTime() {
        val manager = AdaptivePreArmManager()

        manager.recordPreparationDuration(9_000L)
        assertEquals(21_500L, manager.currentLeadTimeMs)

        manager.recordPreparationDuration(12_000L)
        assertEquals(26_000L, manager.currentLeadTimeMs)
    }

    @Test
    fun leadTimeIsCappedAtMaximum() {
        val manager = AdaptivePreArmManager()

        repeat(4) { manager.recordPreparationDuration(30_000L) }

        assertEquals(35_000L, manager.currentLeadTimeMs)
    }

    @Test
    fun fastSamplesContractOnlyWhileAverageIsFast() {
        val manager = AdaptivePreArmManager()
        manager.recordPreparationDuration(10_000L)
        assertEquals(23_000L, manager.currentLeadTimeMs)

        repeat(3) { manager.recordPreparationDuration(1_000L) }

        assertEquals(20_000L, manager.currentLeadTimeMs)
    }

    @Test
    fun historyKeepsOnlyLastFiveSamples() {
        val manager = AdaptivePreArmManager()
        repeat(5) { manager.recordPreparationDuration(30_000L) }
        assertEquals(35_000L, manager.currentLeadTimeMs)

        manager.recordPreparationDuration(1_000L)
        assertEquals(35_000L, manager.currentLeadTimeMs)

        repeat(3) { manager.recordPreparationDuration(1_000L) }
        assertEquals(33_000L, manager.currentLeadTimeMs)
    }

    @Test
    fun resetRestoresBaseAndClearsHistory() {
        val manager = AdaptivePreArmManager()
        repeat(3) { manager.recordPreparationDuration(30_000L) }

        manager.reset()

        assertEquals(20_000L, manager.currentLeadTimeMs)
        manager.recordPreparationDuration(1_000L)
        assertEquals(20_000L, manager.currentLeadTimeMs)
    }

    @Test
    fun invalidConstructorArgumentsThrow() {
        assertThrows(IllegalArgumentException::class.java) {
            AdaptivePreArmManager(baseLeadTimeMs = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdaptivePreArmManager(slowPrepThresholdMs = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdaptivePreArmManager(baseLeadTimeMs = 20_000L, maxLeadTimeMs = 10_000L)
        }
    }
}
