package com.revenge.metadata.models

/**
 * A version marker found in a title, for example the `"Live at Wembley"` inside
 * `"Faded (Live at Wembley)"`.
 */
data class VersionInfo(
  val type: VersionType,
  val description: String,
)
