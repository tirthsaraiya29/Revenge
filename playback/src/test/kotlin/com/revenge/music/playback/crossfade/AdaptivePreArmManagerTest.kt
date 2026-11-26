package com.revenge.music.playback.crossfade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePreArmManagerTest {

  private fun manager() = AdaptivePreArmManager(baseLeadTimeMs = 20_000L, maxLeadTimeMs = 35_000L)

  @Test
  fun startsAtBaseLeadTime() {
    assertEquals(20_000L, manager().currentLeadTimeMs)
  }

  @Test
  fun slowPreparationGrowsLeadTime() {
    val manager = manager()
    manager.recordPreparationDuration(9_000L)
    assertTrue(manager.currentLeadTimeMs > 20_000L)

    manager.recordPreparationDuration(12_000L)
    assertTrue(manager.currentLeadTimeMs >= 25_000L)
  }

  @Test
  fun leadTimeIsCapped() {
    val manager = manager()
    repeat(4) { manager.recordPreparationDuration(30_000L) }
    assertEquals(35_000L, manager.currentLeadTimeMs)
  }

  @Test
  fun fastPreparationDecaysTowardsBase() {
    val manager = manager()
    manager.recordPreparationDuration(10_000L)
    val expanded = manager.currentLeadTimeMs

    repeat(3) { manager.recordPreparationDuration(1_000L) }
    assertTrue(manager.currentLeadTimeMs < expanded)
    assertTrue(manager.currentLeadTimeMs >= 20_000L)
  }

  @Test
  fun resetRestoresBase() {
    val manager = manager()
    manager.recordPreparationDuration(30_000L)
    manager.reset()
    assertEquals(20_000L, manager.currentLeadTimeMs)
  }
}
