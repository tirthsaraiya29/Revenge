package com.revenge.dsp.core

import com.revenge.dsp.core.models.FilterType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fixed-coefficient RBJ biquad. Coefficients are derived once in the constructor and never
 * recomputed while samples are processed.
 */
class BiquadFilter(
    sampleRate: Int,
    frequency: Double,
    gain: Double,
    q: Double = 1.41,
    filterType: FilterType = FilterType.PK,
) {
    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double

    private var lx1 = 0.0
    private var lx2 = 0.0
    private var ly1 = 0.0
    private var ly2 = 0.0
    private var rx1 = 0.0
    private var rx2 = 0.0
    private var ry1 = 0.0
    private var ry2 = 0.0

    init {
        val rate = if (sampleRate < 22) DEFAULT_RATE else sampleRate
        val centre = when {
            !frequency.isFinite() -> MIN_FREQUENCY
            else -> frequency.coerceIn(MIN_FREQUENCY, rate / 2.0 - 1.0)
        }
        val gainDb = if (gain.isFinite()) gain.coerceIn(MIN_GAIN, MAX_GAIN) else 0.0
        val quality = if (!q.isFinite() || q <= 0.0) DEFAULT_Q else q

        val normalised = designSection(rate, centre, gainDb, quality, filterType)
        if (normalised == null) {
            b0 = 1.0
            b1 = 0.0
            b2 = 0.0
            a1 = 0.0
            a2 = 0.0
        } else {
            b0 = normalised[0]
            b1 = normalised[1]
            b2 = normalised[2]
            a1 = normalised[3]
            a2 = normalised[4]
        }
    }

    fun processSample(input: Double): Double {
        val output = b0 * input + b1 * lx1 + b2 * lx2 - a1 * ly1 - a2 * ly2
        lx2 = lx1
        lx1 = input
        ly2 = ly1
        ly1 = output
        return output
    }

    fun processStereo(inputLeft: Double, inputRight: Double): Pair<Double, Double> {
        val outputLeft = b0 * inputLeft + b1 * lx1 + b2 * lx2 - a1 * ly1 - a2 * ly2
        lx2 = lx1
        lx1 = inputLeft
        ly2 = ly1
        ly1 = outputLeft

        val outputRight = b0 * inputRight + b1 * rx1 + b2 * rx2 - a1 * ry1 - a2 * ry2
        rx2 = rx1
        rx1 = inputRight
        ry2 = ry1
        ry1 = outputRight
        return outputLeft to outputRight
    }

    fun reset() {
        lx1 = 0.0
        lx2 = 0.0
        ly1 = 0.0
        ly2 = 0.0
        rx1 = 0.0
        rx2 = 0.0
        ry1 = 0.0
        ry2 = 0.0
    }

    private companion object {
        const val DEFAULT_RATE = 44100
        const val MIN_FREQUENCY = 10.0
        const val MIN_GAIN = -120.0
        const val MAX_GAIN = 120.0
        const val DEFAULT_Q = 1.41
        const val IDENTITY_EPSILON = 1e-12

        fun designSection(
            rate: Int,
            frequency: Double,
            gainDb: Double,
            q: Double,
            filterType: FilterType,
        ): DoubleArray? {
            val w0 = 2.0 * Math.PI * frequency / rate
            val cosine = cos(w0)
            val sine = sin(w0)
            val amplitude = 10.0.pow(gainDb / 40.0)

            val numerator: DoubleArray
            val denominator: DoubleArray
            when (filterType) {
                FilterType.LSC -> {
                    val alpha = sine / 2.0 * sqrt(2.0)
                    val coupling = 2.0 * sqrt(amplitude) * alpha
                    numerator = doubleArrayOf(
                        amplitude * ((amplitude + 1.0) - (amplitude - 1.0) * cosine + coupling),
                        2.0 * amplitude * ((amplitude - 1.0) - (amplitude + 1.0) * cosine),
                        amplitude * ((amplitude + 1.0) - (amplitude - 1.0) * cosine - coupling),
                    )
                    denominator = doubleArrayOf(
                        (amplitude + 1.0) + (amplitude - 1.0) * cosine + coupling,
                        -2.0 * ((amplitude - 1.0) + (amplitude + 1.0) * cosine),
                        (amplitude + 1.0) + (amplitude - 1.0) * cosine - coupling,
                    )
                }
                FilterType.HSC -> {
                    val alpha = sine / 2.0 * sqrt(2.0)
                    val coupling = 2.0 * sqrt(amplitude) * alpha
                    numerator = doubleArrayOf(
                        amplitude * ((amplitude + 1.0) + (amplitude - 1.0) * cosine + coupling),
                        -2.0 * amplitude * ((amplitude - 1.0) + (amplitude + 1.0) * cosine),
                        amplitude * ((amplitude + 1.0) + (amplitude - 1.0) * cosine - coupling),
                    )
                    denominator = doubleArrayOf(
                        (amplitude + 1.0) - (amplitude - 1.0) * cosine + coupling,
                        2.0 * ((amplitude - 1.0) - (amplitude + 1.0) * cosine),
                        (amplitude + 1.0) - (amplitude - 1.0) * cosine - coupling,
                    )
                }
                else -> {
                    val alpha = sine / (2.0 * q)
                    numerator = doubleArrayOf(
                        1.0 + alpha * amplitude,
                        -2.0 * cosine,
                        1.0 - alpha * amplitude,
                    )
                    denominator = doubleArrayOf(
                        1.0 + alpha / amplitude,
                        -2.0 * cosine,
                        1.0 - alpha / amplitude,
                    )
                }
            }

            val a0 = denominator[0]
            if (!a0.isFinite() || abs(a0) <= IDENTITY_EPSILON) return null
            val result = doubleArrayOf(
                numerator[0] / a0,
                numerator[1] / a0,
                numerator[2] / a0,
                denominator[1] / a0,
                denominator[2] / a0,
            )
            return if (result.all { it.isFinite() }) result else null
        }
    }
}
