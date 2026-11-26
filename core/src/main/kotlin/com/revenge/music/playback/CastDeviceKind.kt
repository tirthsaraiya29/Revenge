package com.revenge.music.playback

enum class CastDeviceKind {
  TV,
  SPEAKER,
  CHROMECAST,
  UNKNOWN;

  companion object {
    fun fromName(name: String, description: String?): CastDeviceKind {
      val device = name.lowercase()
      val model = description?.lowercase() ?: ""
      return when {
        device.contains("tv") || model.contains("tv") -> TV
        device.contains("speaker") ||
          device.contains("home") ||
          device.contains("nest") ||
          model.contains("speaker") -> SPEAKER
        device.contains("chromecast") || model.contains("chromecast") -> CHROMECAST
        else -> UNKNOWN
      }
    }
  }
}
