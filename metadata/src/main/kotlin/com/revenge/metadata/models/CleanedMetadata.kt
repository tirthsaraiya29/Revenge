package com.revenge.metadata.models

/** Immutable result of [com.revenge.metadata.cleaner.MetadataCleaner.clean]. */
data class CleanedMetadata(
  val rawTitle: String,
  val rawArtist: String,
  val cleanTitle: String,
  val primaryArtist: String,
  val cleanArtist: String = primaryArtist,
  val featuredArtists: List<String> = emptyList(),
  val version: VersionInfo? = null,
  val junkTags: List<String> = emptyList(),
) {

  fun toDisplayTitle(): String {
    val marker = version
    return if (marker != null && marker.type != VersionType.ORIGINAL) {
      "$cleanTitle (${marker.description})"
    } else {
      cleanTitle
    }
  }

  fun toSearchQuery(): Pair<String, String> = cleanTitle to cleanArtist
}
