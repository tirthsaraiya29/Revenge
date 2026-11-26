package com.revenge.dsp.core.models

import kotlinx.serialization.Serializable

/**
 * An equaliser profile persisted by the app. [id] is stable across restarts and
 * [addedTimestamp] orders profiles from newest to oldest.
 */
@Serializable
data class SavedEQProfile(
  val id: String,
  val name: String,
  val deviceModel: String = "",
  val bands: List<ParametricEQBand> = emptyList(),
  val preamp: Double = 0.0,
  val isCustom: Boolean = false,
  val isActive: Boolean = false,
  val addedTimestamp: Long = System.currentTimeMillis(),
) {
  /** View of this profile as a plain [ParametricEQ] for the DSP pipeline. */
  val parametricEQ: ParametricEQ
    get() = ParametricEQ(preamp = preamp, bands = bands)
}
