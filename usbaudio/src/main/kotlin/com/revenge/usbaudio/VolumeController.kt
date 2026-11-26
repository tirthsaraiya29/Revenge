package com.revenge.usbaudio

import com.revenge.usbaudio.model.VolumeMode
import kotlin.math.log10
import kotlin.math.pow

/**
 * Maps a linear 0..1 slider onto a perceptual volume curve and produces either a UAC Feature Unit
 * control value or a software attenuation multiplier.
 */
class VolumeController(
  val hasHardwareVolume: Boolean,
  val minDb: Float = -60.0f,
  val maxDb: Float = 0.0f,
  var volumeMode: VolumeMode =
    if (hasHardwareVolume) VolumeMode.HARDWARE else VolumeMode.PURE_BIT_PERFECT,
) {
  private var sliderRatio: Float = 1.0f

  /**
   * Slider position to decibels. The curve spans a 60 dB range, so 0.5 maps to about -18 dB;
   * results are clamped to [minDb]..[maxDb].
   */
  fun calculateDbForSlider(ratio: Float): Float {
    val r = ratio.coerceIn(0.0f, 1.0f)
    if (r <= 0.0f) return minDb
    if (r >= 1.0f) return maxDb
    return (60.0f * log10(r)).coerceIn(minDb, maxDb)
  }

  /** Decibels to the UAC 1/256 dB control unit, clamped to a signed 16-bit value. */
  fun dbToUacMillibel(db: Float): Short {
    val raw = (db * 256.0f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    return raw.toShort()
  }

  /** Stores the requested slider position; the mode decides how it is honoured. */
  fun setVolume(ratio: Float) {
    sliderRatio = ratio.coerceIn(0.0f, 1.0f)
  }

  /**
   * Linear amplitude to apply in software. Bit-perfect and hardware modes leave the samples
   * untouched (1.0), leaving attenuation to the DAC or the external volume control.
   */
  fun getEffectiveSoftwareMultiplier(): Double =
    when (volumeMode) {
      VolumeMode.PURE_BIT_PERFECT -> 1.0
      VolumeMode.HARDWARE -> 1.0
      VolumeMode.SOFTWARE_64BIT ->
        10.0.pow(calculateDbForSlider(sliderRatio).toDouble() / 20.0)
    }
}
