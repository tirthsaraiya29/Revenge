package com.revenge.dsp.core.models

import kotlinx.serialization.Serializable

/**
 * Biquad filter response used by a parametric EQ band.
 *
 * [PK] peaking, [LSC] low shelf, [HSC] high shelf. [LPQ] and [HPQ] are accepted
 * for file compatibility; the audio pipeline currently renders them as peaking
 * bands, which is the behaviour existing profiles rely on.
 */
@Serializable
enum class FilterType {
  PK,
  LSC,
  HSC,
  LPQ,
  HPQ,
}
