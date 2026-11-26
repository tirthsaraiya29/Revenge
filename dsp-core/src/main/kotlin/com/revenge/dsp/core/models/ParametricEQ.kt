package com.revenge.dsp.core.models

import kotlinx.serialization.Serializable

/** One parametric band: [frequency] in Hz, [gain] in dB, [q] dimensionless. */
@Serializable
data class ParametricEQBand(
    val frequency: Double,
    val gain: Double,
    val q: Double = 1.41,
    val filterType: FilterType = FilterType.PK,
    val enabled: Boolean = true,
)

/**
 * A complete equalizer definition. [bands] order is meaningful and preserved; [metadata]
 * holds free-form text collected from an import file.
 */
@Serializable
data class ParametricEQ(
    val preamp: Double,
    val bands: List<ParametricEQBand>,
    val metadata: Map<String, String> = emptyMap(),
) {
    companion object {
        const val MAX_BANDS: Int = 20
    }
}
