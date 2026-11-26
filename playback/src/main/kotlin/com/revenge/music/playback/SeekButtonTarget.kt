package com.revenge.music.playback

/**
 * Resolves a relative seek to an absolute position, clamped to the track. A negative duration means
 * "unknown", so only the lower bound applies.
 */
fun seekButtonTarget(positionMs: Long, durationMs: Long, offsetMs: Long): Long {
  val position = positionMs.coerceAtLeast(0L)
  val target =
    if (offsetMs > 0L && position > Long.MAX_VALUE - offsetMs) {
      Long.MAX_VALUE
    } else {
      (position + offsetMs).coerceAtLeast(0L)
    }
  return if (durationMs >= 0L) target.coerceAtMost(durationMs) else target
}
