package com.revenge.core.playback

/** Device family shared by the GMS Cast handler and the picker UI. */
enum class CastDeviceKind {
  TV,
  SPEAKER,
  CHROMECAST,
  UNKNOWN;

  companion object {
    private val TV_KEYWORDS = listOf("tv", "television", "google tv", "android tv")
    private val SPEAKER_KEYWORDS =
      listOf("speaker", "home", "nest", "soundbar", "audio", "mini", "max", "hub")

    fun fromName(name: String, description: String?): CastDeviceKind {
      val haystack = "${name.lowercase()} ${description.orEmpty().lowercase()}"
      return when {
        TV_KEYWORDS.any { haystack.contains(it) } -> TV
        SPEAKER_KEYWORDS.any { haystack.contains(it) } -> SPEAKER
        haystack.contains("chromecast") -> CHROMECAST
        else -> UNKNOWN
      }
    }
  }
}
