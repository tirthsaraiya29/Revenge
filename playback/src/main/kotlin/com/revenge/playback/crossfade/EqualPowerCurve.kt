package com.revenge.playback.crossfade

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object EqualPowerCurve {

  data class Gains(val outgoingGain: Float, val incomingGain: Float)

  fun calculateGains(progress: Float): Gains {
    val clamped = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val angle = clamped * (PI / 2.0)
    return Gains(
      outgoingGain = cos(angle).toFloat(),
      incomingGain = sin(angle).toFloat(),
    )
  }
}
