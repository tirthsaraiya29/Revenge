package com.revenge.dsp.core

import com.revenge.dsp.core.models.FilterType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class BiquadFilter(
    sampleRate: Int,
    frequency: Double,
    gain: Double,
    q: Double = 1.41,
    filterType: FilterType = FilterType.PK
) {
    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double

    private var leftX1 = 0.0
    private var leftX2 = 0.0
    private var leftY1 = 0.0
    private var leftY2 = 0.0

    private var rightX1 = 0.0
    private var rightX2 = 0.0
    private var rightY1 = 0.0
    private var rightY2 = 0.0

    init {
        val rate = if (sampleRate >= 22) sampleRate else 44100
        val safeFrequency = if (frequency.isFinite()) {
            frequency.coerceIn(10.0, rate / 2.0 - 1.0)
        } else {
            10.0
        }
        val safeGain = if (gain.isFinite()) gain.coerceIn(-120.0, 120.0) else 0.0
        val safeQ = if (!q.isFinite() || q <= 0.0) 1.41 else q

        val coefficients = derive(safeFrequency, safeGain, safeQ, filterType, rate)
        b0 = coefficients[0]
        b1 = coefficients[1]
        b2 = coefficients[2]
        a1 = coefficients[3]
        a2 = coefficients[4]
    }

    fun processSample(input: Double): Double {
        val output = b0 * input + b1 * leftX1 + b2 * leftX2 - a1 * leftY1 - a2 * leftY2
        leftX2 = leftX1
        leftX1 = input
        leftY2 = leftY1
        leftY1 = output
        return output
    }

    fun processStereo(inputLeft: Double, inputRight: Double): Pair<Double, Double> {
        val leftOutput = b0 * inputLeft + b1 * leftX1 + b2 * leftX2 - a1 * leftY1 - a2 * leftY2
        leftX2 = leftX1
        leftX1 = inputLeft
        leftY2 = leftY1
        leftY1 = leftOutput

        val rightOutput = b0 * inputRight + b1 * rightX1 + b2 * rightX2 - a1 * rightY1 - a2 * rightY2
        rightX2 = rightX1
        rightX1 = inputRight
        rightY2 = rightY1
        rightY1 = rightOutput

        return leftOutput to rightOutput
    }

    fun reset() {
        leftX1 = 0.0
        leftX2 = 0.0
        leftY1 = 0.0
        leftY2 = 0.0
        rightX1 = 0.0
        rightX2 = 0.0
        rightY1 = 0.0
        rightY2 = 0.0
    }

    private fun derive(
        frequency: Double,
        gainDb: Double,
        q: Double,
        filterType: FilterType,
        sampleRate: Int
    ): DoubleArray {
        val a = 10.0.pow(gainDb / 40.0)
        val omega = 2.0 * PI * frequency / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)

        val raw = when (filterType) {
            FilterType.PK, FilterType.LPQ, FilterType.HPQ -> {
                val alpha = sinOmega / (2.0 * q)
                doubleArrayOf(
                    1.0 + alpha * a,
                    -2.0 * cosOmega,
                    1.0 - alpha * a,
                    1.0 + alpha / a,
                    -2.0 * cosOmega,
                    1.0 - alpha / a
                )
            }

            FilterType.LSC -> {
                val alpha = sinOmega / 2.0 * sqrt(2.0)
                val beta = 2.0 * sqrt(a) * alpha
                val sum = a + 1.0
                val diff = a - 1.0
                doubleArrayOf(
                    a * (sum - diff * cosOmega + beta),
                    2.0 * a * (diff - sum * cosOmega),
                    a * (sum - diff * cosOmega - beta),
                    sum + diff * cosOmega + beta,
                    -2.0 * (diff + sum * cosOmega),
                    sum + diff * cosOmega - beta
                )
            }

            FilterType.HSC -> {
                val alpha = sinOmega / 2.0 * sqrt(2.0)
                val beta = 2.0 * sqrt(a) * alpha
                val sum = a + 1.0
                val diff = a - 1.0
                doubleArrayOf(
                    a * (sum + diff * cosOmega + beta),
                    -2.0 * a * (diff + sum * cosOmega),
                    a * (sum + diff * cosOmega - beta),
                    sum - diff * cosOmega + beta,
                    2.0 * (diff - sum * cosOmega),
                    sum - diff * cosOmega - beta
                )
            }
        }

        val a0 = raw[3]
        if (!a0.isFinite() || abs(a0) <= 1e-12) {
            return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0)
        }

        return doubleArrayOf(
            raw[0] / a0,
            raw[1] / a0,
            raw[2] / a0,
            raw[4] / a0,
            raw[5] / a0
        )
    }
}
