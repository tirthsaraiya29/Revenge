package com.revenge.metadata.models

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
        val variant = version
        return if (variant != null && variant.type != VersionType.ORIGINAL) {
            "$cleanTitle (${variant.description})"
        } else {
            cleanTitle
        }
    }

    fun toSearchQuery(): Pair<String, String> = cleanTitle to cleanArtist
}
