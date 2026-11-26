package com.revenge.dsp.core

import com.revenge.dsp.core.models.FilterType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Direct form I biquad section using the RBJ audio EQ cookbook responses.
 *
 * Coefficients are derived once from the constructor arguments; only the
 * per-channel history is mutable while streaming. Left and right channels keep
 * independent state so [processStereo] can be used on interleaved audio.
 *
 * Sample rate and frequency are clamped to a usable range, so the filter stays
 * finite even when callers pass a frequency at or beyond Nyquist.
 */
class BiquadFilter(
  sampleRate: Int,
  frequency: Double,
  gain: Double,
  q: Double = DEFAULT_Q,
  filterType: FilterType = FilterType.PK,
) {
  private val b0: Double
  private val b1: Double
  private val b2: Double
  private val a1: Double
  private val a2: Double

  private val left = ChannelState()
  private val right = ChannelState()

  init {
    val rate = if (sampleRate >= MIN_SAMPLE_RATE) sampleRate.toDouble() else DEFAULT_SAMPLE_RATE.toDouble()
    val safeFrequency = frequency
      .takeIf { it.isFinite() }
      ?.coerceIn(MIN_FREQUENCY, rate / 2.0 - 1.0)
      ?: MIN_FREQUENCY
    val safeGain = (gain.takeIf { it.isFinite() } ?: 0.0).coerceIn(MIN_GAIN, MAX_GAIN)
    val safeQ = q.takeIf { it.isFinite() && it > 0.0 } ?: DEFAULT_Q

    val omega = 2.0 * PI * safeFrequency / rate
    val (b, a) =
      when (filterType) {
        FilterType.LSC -> shelf(omega, safeGain, lowShelf = true)
        FilterType.HSC -> shelf(omega, safeGain, lowShelf = false)
        FilterType.PK,
        FilterType.LPQ,
        FilterType.HPQ -> peaking(omega, safeGain, safeQ)
      }

    val denominator = a[0]
    if (denominator.isFinite() && abs(denominator) > EPSILON) {
      b0 = b[0] / denominator
      b1 = b[1] / denominator
      b2 = b[2] / denominator
      a1 = a[1] / denominator
      a2 = a[2] / denominator
    } else {
      // Degenerate coefficients would emit NaN; pass audio through untouched.
      b0 = 1.0
      b1 = 0.0
      b2 = 0.0
      a1 = 0.0
      a2 = 0.0
    }
  }

  fun processSample(input: Double): Double = step(left, input)

  fun processStereo(inputLeft: Double, inputRight: Double): Pair<Double, Double> =
    step(left, inputLeft) to step(right, inputRight)

  fun reset() {
    left.reset()
    right.reset()
  }

  private fun step(state: ChannelState, input: Double): Double {
    val output =
      b0 * input + b1 * state.x1 + b2 * state.x2 - a1 * state.y1 - a2 * state.y2
    state.x2 = state.x1
    state.x1 = input
    state.y2 = state.y1
    state.y1 = output
    return output
  }

  /** Returns (numerator, denominator) coefficients before a0 normalisation. */
  private fun peaking(omega: Double, gainDb: Double, q: Double): Pair<DoubleArray, DoubleArray> {
    val amplitude = 10.0.pow(gainDb / 40.0)
    val sine = sin(omega)
    val cosine = cos(omega)
    val alpha = sine / (2.0 * q)
    val numerator = doubleArrayOf(1.0 + alpha * amplitude, -2.0 * cosine, 1.0 - alpha * amplitude)
    val denominator = doubleArrayOf(1.0 + alpha / amplitude, -2.0 * cosine, 1.0 - alpha / amplitude)
    return numerator to denominator
  }

  private fun shelf(
    omega: Double,
    gainDb: Double,
    lowShelf: Boolean,
  ): Pair<DoubleArray, DoubleArray> {
    val amplitude = 10.0.pow(gainDb / 40.0)
    val rootAmplitude = sqrt(amplitude)
    val cosine = cos(omega)
    val alpha = sin(omega) / 2.0 * SQRT_TWO
    val sum = amplitude + 1.0
    val difference = amplitude - 1.0
    val alphaTerm = 2.0 * rootAmplitude * alpha

    val numerator: DoubleArray
    val denominator: DoubleArray
    if (lowShelf) {
      numerator =
        doubleArrayOf(
          amplitude * (sum - difference * cosine + alphaTerm),
          2.0 * amplitude * (difference - sum * cosine),
          amplitude * (sum - difference * cosine - alphaTerm),
        )
      denominator =
        doubleArrayOf(
          sum + difference * cosine + alphaTerm,
          -2.0 * (difference + sum * cosine),
          sum + difference * cosine - alphaTerm,
        )
    } else {
      numerator =
        doubleArrayOf(
          amplitude * (sum + difference * cosine + alphaTerm),
          -2.0 * amplitude * (difference + sum * cosine),
          amplitude * (sum + difference * cosine - alphaTerm),
        )
      denominator =
        doubleArrayOf(
          sum - difference * cosine + alphaTerm,
          2.0 * (difference - sum * cosine),
          sum - difference * cosine - alphaTerm,
        )
    }
    return numerator to denominator
  }

  private class ChannelState {
    var x1 = 0.0
    var x2 = 0.0
    var y1 = 0.0
    var y2 = 0.0

    fun reset() {
      x1 = 0.0
      x2 = 0.0
      y1 = 0.0
      y2 = 0.0
    }
  }

  private companion object {
    const val DEFAULT_Q = 1.41
    const val DEFAULT_SAMPLE_RATE = 44100
    const val MIN_SAMPLE_RATE = 22
    const val MIN_FREQUENCY = 10.0
    const val MIN_GAIN = -120.0
    const val MAX_GAIN = 120.0
    const val EPSILON = 1e-12
    val SQRT_TWO = sqrt(2.0)
  }
}
