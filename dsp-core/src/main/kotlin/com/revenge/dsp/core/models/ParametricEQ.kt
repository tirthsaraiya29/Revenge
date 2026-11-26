package com.revenge.dsp.core.models

import kotlinx.serialization.Serializable

/**
 * A single parametric band. Frequencies are in Hz, [gain] in dB and [q] is the
 * dimensionless quality factor.
 */
@Serializable
data class ParametricEQBand(
  val frequency: Double,
  val gain: Double,
  val q: Double = 1.41,
  val filterType: FilterType = FilterType.PK,
  val enabled: Boolean = true,
)

/** Complete parametric equaliser definition, including the global preamp. */
@Serializable
data class ParametricEQ(
  val preamp: Double,
  val bands: List<ParametricEQBand>,
  val metadata: Map<String, String> = emptyMap(),
) {
  companion object {
    const val MAX_BANDS = 20
  }
}
