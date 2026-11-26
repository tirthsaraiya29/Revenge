package com.revenge.music.playback.crossfade

import kotlin.math.max
import kotlin.math.min

/**
 * Adapts how early the next player is prepared: slow preparations grow the lead time towards
 * [maxLeadTimeMs], fast ones let it decay back to [baseLeadTimeMs]. A fixed lead time either
 * cold-starts on slow networks or wastes buffering time on fast ones.
 */
class AdaptivePreArmManager(
  val baseLeadTimeMs: Long = 20_000L,
  val maxLeadTimeMs: Long = 35_000L,
  private val slowPrepThresholdMs: Long = 8_000L,
) {
  init {
    require(baseLeadTimeMs > 0) { "baseLeadTimeMs must be positive" }
    require(slowPrepThresholdMs > 0) { "slowPrepThresholdMs must be positive" }
    require(maxLeadTimeMs >= baseLeadTimeMs) { "maxLeadTimeMs must be >= baseLeadTimeMs" }
  }

  @Volatile
  var currentLeadTimeMs: Long = baseLeadTimeMs
    private set

  private val prepHistory = ArrayDeque<Long>()

  @Synchronized
  fun recordPreparationDuration(durationMs: Long) {
    prepHistory.addLast(durationMs)
    while (prepHistory.size > HISTORY_SIZE) prepHistory.removeFirst()

    if (durationMs > slowPrepThresholdMs) {
      val extra = ((durationMs - slowPrepThresholdMs) * 1.5).toLong()
      currentLeadTimeMs = min(maxLeadTimeMs, max(currentLeadTimeMs, baseLeadTimeMs + extra))
    } else {
      // Only contract once the recent average is comfortably fast, to avoid oscillating.
      if (prepHistory.average() < slowPrepThresholdMs) {
        currentLeadTimeMs = max(baseLeadTimeMs, currentLeadTimeMs - DECAY_STEP_MS)
      }
    }
  }

  @Synchronized
  fun reset() {
    currentLeadTimeMs = baseLeadTimeMs
    prepHistory.clear()
  }

  private companion object {
    const val HISTORY_SIZE = 5
    const val DECAY_STEP_MS = 2_000L
  }
}
