package com.revenge.dsp.core

import com.revenge.dsp.core.models.FilterType
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
    filterType: FilterType = FilterType.PK,
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
        val rate = if (sampleRate < 22) 44100 else sampleRate
        val freq = if (!frequency.isFinite()) 10.0 else frequency.coerceIn(10.0, rate / 2.0 - 1.0)
        val gainDb = if (!gain.isFinite()) 0.0 else gain.coerceIn(-120.0, 120.0)
        val qValue = if (!q.isFinite() || q <= 0.0) 1.41 else q

        val omega = 2.0 * Math.PI * freq / rate
        val sinW = sin(omega)
        val cosW = cos(omega)

        var nb0: Double
        var nb1: Double
        var nb2: Double
        var na0: Double
        var na1: Double
        var na2: Double

        if (filterType == FilterType.LSC || filterType == FilterType.HSC) {
            val a = 10.0.pow(gainDb / 40.0)
            val alpha = sinW / 2.0 * sqrt(2.0)
            val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
            if (filterType == FilterType.LSC) {
                nb0 = a * ((a + 1.0) - (a - 1.0) * cosW + twoSqrtAAlpha)
                nb1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW)
                nb2 = a * ((a + 1.0) - (a - 1.0) * cosW - twoSqrtAAlpha)
                na0 = (a + 1.0) + (a - 1.0) * cosW + twoSqrtAAlpha
                na1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW)
                na2 = (a + 1.0) + (a - 1.0) * cosW - twoSqrtAAlpha
            } else {
                nb0 = a * ((a + 1.0) + (a - 1.0) * cosW + twoSqrtAAlpha)
                nb1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW)
                nb2 = a * ((a + 1.0) + (a - 1.0) * cosW - twoSqrtAAlpha)
                na0 = (a + 1.0) - (a - 1.0) * cosW + twoSqrtAAlpha
                na1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW)
                na2 = (a + 1.0) - (a - 1.0) * cosW - twoSqrtAAlpha
            }
        } else {
            val a = 10.0.pow(gainDb / 40.0)
            val alpha = sinW / (2.0 * qValue)
            nb0 = 1.0 + alpha * a
            nb1 = -2.0 * cosW
            nb2 = 1.0 - alpha * a
            na0 = 1.0 + alpha / a
            na1 = -2.0 * cosW
            na2 = 1.0 - alpha / a
        }

        if (!na0.isFinite() || abs(na0) <= 1e-12) {
            b0 = 1.0
            b1 = 0.0
            b2 = 0.0
            a1 = 0.0
            a2 = 0.0
        } else {
            b0 = nb0 / na0
            b1 = nb1 / na0
            b2 = nb2 / na0
            a1 = na1 / na0
            a2 = na2 / na0
        }
    }

    fun processSample(input: Double): Double {
        val out = b0 * input + b1 * leftX1 + b2 * leftX2 - a1 * leftY1 - a2 * leftY2
        leftX2 = leftX1
        leftX1 = input
        leftY2 = leftY1
        leftY1 = out
        return out
    }

    fun processStereo(inputLeft: Double, inputRight: Double): Pair<Double, Double> {
        val outLeft = b0 * inputLeft + b1 * leftX1 + b2 * leftX2 - a1 * leftY1 - a2 * leftY2
        leftX2 = leftX1
        leftX1 = inputLeft
        leftY2 = leftY1
        leftY1 = outLeft

        val outRight = b0 * inputRight + b1 * rightX1 + b2 * rightX2 - a1 * rightY1 - a2 * rightY2
        rightX2 = rightX1
        rightX1 = inputRight
        rightY2 = rightY1
        rightY1 = outRight

        return outLeft to outRight
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
}
