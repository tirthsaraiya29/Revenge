package com.revenge.music.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.revenge.core.constants.EnableClickHapticsKey
import com.revenge.core.constants.EnableHapticsKey
import com.revenge.core.constants.EnableLongPressHapticsKey
import com.revenge.core.constants.EnableScrollEdgeHapticsKey
import com.revenge.core.constants.EnableSliderHapticsKey
import com.revenge.core.constants.HapticIntensity
import com.revenge.core.constants.HapticIntensityKey
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get

enum class HapticType {
    CLICK,
    LONG_PRESS,
    SCROLL_EDGE,
    SLIDER,
}

/**
 * App-wide haptic feedback gated by the master toggle and the per-interaction toggle for
 * [HapticType]. Intensity maps to vibration amplitude when the device allows it and otherwise
 * degrades to the platform's view haptics.
 */
class HapticHelper(private val view: View) {
    private val context: Context get() = view.context

    fun performHaptic(type: HapticType) {
        val store = context.dataStore
        if (!store.get(EnableHapticsKey, true)) return
        val enabled = when (type) {
            HapticType.CLICK -> store.get(EnableClickHapticsKey, true)
            HapticType.LONG_PRESS -> store.get(EnableLongPressHapticsKey, true)
            HapticType.SCROLL_EDGE -> store.get(EnableScrollEdgeHapticsKey, true)
            HapticType.SLIDER -> store.get(EnableSliderHapticsKey, true)
        }
        if (!enabled) return

        val intensity = HapticIntensity.fromName(store[HapticIntensityKey])
        if (!vibrate(type, intensity)) {
            view.performHapticFeedback(
                constantFor(type),
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
        }
    }

    private fun vibrate(type: HapticType, intensity: HapticIntensity): Boolean {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return false
        if (!vibrator.hasVibrator()) return false
        val durationMs = when (type) {
            HapticType.LONG_PRESS -> 24L
            HapticType.CLICK, HapticType.SCROLL_EDGE, HapticType.SLIDER -> 10L
        }.let { (it * intensity.scaleFactor).toLong().coerceAtLeast(1L) }
        val amplitude = (255 * intensity.scaleFactor).toInt().coerceIn(1, 255)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun constantFor(type: HapticType): Int = when (type) {
        HapticType.CLICK -> HapticFeedbackConstants.CONTEXT_CLICK
        HapticType.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
        HapticType.SCROLL_EDGE -> HapticFeedbackConstants.CLOCK_TICK
        HapticType.SLIDER -> HapticFeedbackConstants.CLOCK_TICK
    }
}

@Composable
fun rememberHapticHelper(): HapticHelper {
    val view = LocalView.current
    return remember(view) { HapticHelper(view) }
}
