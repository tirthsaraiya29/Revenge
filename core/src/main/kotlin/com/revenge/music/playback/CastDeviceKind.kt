package com.revenge.music.playback

/** Coarse device category used by cast UI to choose a presentation bucket. */
enum class CastDeviceKind {
  TV,
  SPEAKER,
  CHROMECAST,
  UNKNOWN;

  companion object {
    /**
     * Classifies a cast device from its friendly name and optional model description.
     * Precedence is fixed: TV, then speaker-class, then Chromecast, then unknown.
     */
    fun fromName(name: String, description: String?): CastDeviceKind {
      val device = name.lowercase()
      val model = description?.lowercase() ?: ""
      return when {
        "tv" in device || "tv" in model -> TV
        "speaker" in device ||
          "home" in device ||
          "nest" in device ||
          "speaker" in model -> SPEAKER
        "chromecast" in device || "chromecast" in model -> CHROMECAST
        else -> UNKNOWN
      }
    }
  }
}
