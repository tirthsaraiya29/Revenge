package com.revenge.metadata.models

/**
 * A single detected version marker. [description] is the trimmed bracket/tag text
 * that produced the match, without the surrounding brackets.
 */
data class VersionInfo(
    val type: VersionType,
    val description: String,
)
