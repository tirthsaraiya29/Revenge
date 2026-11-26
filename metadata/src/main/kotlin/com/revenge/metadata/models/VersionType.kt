package com.revenge.metadata.models

/**
 * Classification of a song variant. Ordinals are part of the public contract and
 * must not be reordered.
 */
enum class VersionType {
    ORIGINAL,
    REMIX,
    LIVE,
    ACOUSTIC,
    REMASTER,
    INSTRUMENTAL,
    COVER,
    EDIT,
    EXTENDED,
    RADIO_EDIT,
    SLOWED,
    SPED_UP,
    OTHER_VERSION,
}
