package com.revenge.usbaudio

import com.revenge.usbaudio.model.VolumeMode
import kotlin.math.log10
import kotlin.math.pow

class VolumeController(
    val hasHardwareVolume: Boolean,
    val minDb: Float = -60.0f,
    val maxDb: Float = 0.0f,
    var volumeMode: VolumeMode = if (hasHardwareVolume) VolumeMode.HARDWARE else VolumeMode.PURE_BIT_PERFECT,
) {
    private var sliderRatio: Float = 0f

    fun calculateDbForSlider(ratio: Float): Float {
        val bounded = ratio.coerceIn(0f, 1f)
        return when {
            bounded <= 0f -> minDb
            bounded >= 1f -> maxDb
            else -> (60f * log10(bounded)).coerceIn(minDb, maxDb)
        }
    }

    fun dbToUacMillibel(db: Float): Short {
        val millibels = (db * 256f).toInt()
        return millibels.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    fun setVolume(ratio: Float) {
        sliderRatio = ratio.coerceIn(0f, 1f)
    }

    fun getEffectiveSoftwareMultiplier(): Double = when (volumeMode) {
        VolumeMode.PURE_BIT_PERFECT, VolumeMode.HARDWARE -> 1.0
        VolumeMode.SOFTWARE_64BIT -> 10.0.pow(calculateDbForSlider(sliderRatio).toDouble() / 20.0)
    }
}
