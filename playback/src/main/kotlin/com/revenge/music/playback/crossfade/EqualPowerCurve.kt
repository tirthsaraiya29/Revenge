package com.revenge.music.playback.crossfade

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Equal-power fade gains: the outgoing track follows a quarter cosine and the incoming one a
 * quarter sine, so the summed acoustic power stays constant and the blend does not dip in the
 * middle. For any progress in [0, 1], `out^2 + in^2 == 1`.
 */
object EqualPowerCurve {
  data class Gains(val outgoingGain: Float, val incomingGain: Float)

  fun calculateGains(progress: Float): Gains {
    val clamped = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val angle = clamped * (PI.toFloat() / 2f)
    return Gains(outgoingGain = cos(angle), incomingGain = sin(angle))
  }
}
