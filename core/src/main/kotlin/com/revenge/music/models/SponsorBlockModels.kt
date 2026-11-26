package com.revenge.music.models

import kotlinx.serialization.Serializable

/** A single SponsorBlock segment as returned by the public API. */
@Serializable
data class SponsorBlockSegment(
  val segment: List<Float>,
  val UUID: String,
  val category: String,
  val actionType: String,
  val locked: Int,
  val votes: Int,
  val description: String,
)
