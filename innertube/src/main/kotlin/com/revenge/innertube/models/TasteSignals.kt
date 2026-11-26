package com.revenge.innertube.models

import kotlinx.serialization.Serializable

/** Taste metadata attached to a track, used by offline recommendations. */
@Serializable
data class TasteSignals(
  val videoId: String,
  val genres: List<String> = emptyList(),
  val moodTags: List<String> = emptyList(),
)
