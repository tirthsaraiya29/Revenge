package com.revenge.dsp.core.models

import kotlinx.serialization.Serializable

/**
 * Filter shapes accepted in AutoEQ-style profiles.
 *
 * [LPQ] and [HPQ] exist only for file compatibility: the renderer treats them as peaking
 * filters, so they are not true low-pass/high-pass sections.
 */
@Serializable
enum class FilterType {
    PK,
    LSC,
    HSC,
    LPQ,
    HPQ,
}
