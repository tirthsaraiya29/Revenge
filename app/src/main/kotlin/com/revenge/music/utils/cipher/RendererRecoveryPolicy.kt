package com.revenge.music.utils.cipher

/**
 * Bounded retry policy for Executor/WebView based player-JS evaluation.
 *
 * [nextDelayMs] grows exponentially from [baseDelayMs] up to [maxDelayMs]. After [maxAttempts]
 * consecutive failures [shouldRetry] turns false so callers stop recreating a renderer that keeps
 * dying; one success resets the counter.
 */
class RendererRecoveryPolicy(
  private val maxAttempts: Int = 3,
  private val baseDelayMs: Long = 500L,
  private val maxDelayMs: Long = 8_000L,
) {
  private val lock = Any()
  private var failures = 0

  val attempts: Int
    get() = synchronized(lock) { failures }

  fun shouldRetry(): Boolean = synchronized(lock) { failures < maxAttempts }

  fun recordFailure(): Int = synchronized(lock) { ++failures }

  fun recordSuccess() {
    synchronized(lock) { failures = 0 }
  }

  fun reset() {
    synchronized(lock) { failures = 0 }
  }

  fun nextDelayMs(): Long =
    synchronized(lock) {
      if (failures <= 0) return baseDelayMs
      val shift = (failures - 1).coerceAtMost(16)
      val delay = baseDelayMs shl shift
      delay.coerceAtMost(maxDelayMs)
    }

  companion object {
    fun forPlayerRenderer(): RendererRecoveryPolicy =
      RendererRecoveryPolicy(maxAttempts = 4, baseDelayMs = 750L, maxDelayMs = 15_000L)
  }
}
