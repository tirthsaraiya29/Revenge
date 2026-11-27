package com.revenge.playback

fun seekButtonTarget(positionMs: Long, durationMs: Long, offsetMs: Long): Long {
  val position = positionMs.coerceAtLeast(0L)
  var target =
    if (offsetMs > 0L && position > Long.MAX_VALUE - offsetMs) {
      Long.MAX_VALUE
    } else {
      (position + offsetMs).coerceAtLeast(0L)
    }
  if (durationMs >= 0L) {
    target = target.coerceAtMost(durationMs)
  }
  return target
}
