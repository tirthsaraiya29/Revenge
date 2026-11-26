package com.revenge.dsp.core.models

import kotlinx.serialization.Serializable

/**
 * A user-visible EQ profile persisted by `:audio-dsp`. Serialized as one JSON array element.
 *
 * [addedTimestamp] defaults at construction time, so a freshly built profile without an
 * explicit value carries the current epoch millis.
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
    /** Read-only projection; not serialized, and carries empty metadata. */
    val parametricEQ: ParametricEQ
        get() = ParametricEQ(preamp = preamp, bands = bands)
}
