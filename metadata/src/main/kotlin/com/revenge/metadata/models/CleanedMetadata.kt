package com.revenge.metadata.models

/**
 * Result of normalising a raw title/artist pair returned by the music API.
 *
 * [cleanTitle] and [primaryArtist] are suitable for duplicate matching and lyric search,
 * [cleanArtist] keeps the full (possibly multi-artist) name for display, and [version]
 * records a non-original variant so it can be shown or searched for separately.
 */
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

  /** Title with its version suffix re-attached, e.g. `"Faded (Live at Wembley)"`. */
  fun toDisplayTitle(): String =
    if (version != null && version.type != VersionType.ORIGINAL) {
      "$cleanTitle (${version.description})"
    } else {
      cleanTitle
    }

  /** `(title, artist)` pair used when querying lyric providers. */
  fun toSearchQuery(): Pair<String, String> = cleanTitle to cleanArtist
}
