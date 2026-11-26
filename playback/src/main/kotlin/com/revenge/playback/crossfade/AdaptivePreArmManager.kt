package com.revenge.playback.crossfade

class AdaptivePreArmManager(
    val baseLeadTimeMs: Long = 20_000L,
    val maxLeadTimeMs: Long = 35_000L,
    private val slowPrepThresholdMs: Long = 8_000L,
) {

    init {
        require(baseLeadTimeMs > 0L) {
            "baseLeadTimeMs must be greater than zero"
        }
        require(slowPrepThresholdMs > 0L) {
            "slowPrepThresholdMs must be greater than zero"
        }
        require(maxLeadTimeMs >= baseLeadTimeMs) {
            "maxLeadTimeMs must be greater than or equal to baseLeadTimeMs"
        }
    }

    @Volatile
    var currentLeadTimeMs: Long = baseLeadTimeMs
        private set

    private val history = ArrayDeque<Long>()

    @Synchronized
    fun recordPreparationDuration(durationMs: Long) {
        history.addLast(durationMs)
        while (history.size > HISTORY_SIZE) history.removeFirst()

        if (durationMs > slowPrepThresholdMs) {
            val extra = ((durationMs - slowPrepThresholdMs) * 1.5).toLong()
            val candidate = baseLeadTimeMs + extra
            currentLeadTimeMs = minOf(maxLeadTimeMs, maxOf(currentLeadTimeMs, candidate))
        } else if (history.average() < slowPrepThresholdMs) {
            currentLeadTimeMs = maxOf(baseLeadTimeMs, currentLeadTimeMs - CONTRACTION_STEP_MS)
        }
    }

    @Synchronized
    fun reset() {
        currentLeadTimeMs = baseLeadTimeMs
        history.clear()
    }

    private companion object {
        const val HISTORY_SIZE = 5
        const val CONTRACTION_STEP_MS = 2000L
    }
}
