package com.revenge.music.playback

import java.util.Locale

enum class CastDeviceKind {
    TV,
    SPEAKER,
    CHROMECAST,
    UNKNOWN;

    companion object {
        private val speakerNameMarkers = arrayOf("speaker", "home", "nest")
        private const val SPEAKER_MODEL_MARKER = "speaker"
        private const val CHROMECAST_MARKER = "chromecast"
        private const val TV_MARKER = "tv"

        fun fromName(name: String, description: String?): CastDeviceKind {
            val device = name.lowercase(Locale.ROOT)
            val model = description.orEmpty().lowercase(Locale.ROOT)
            return when {
                device.contains(TV_MARKER) || model.contains(TV_MARKER) -> TV
                isSpeaker(device, model) -> SPEAKER
                device.contains(CHROMECAST_MARKER) || model.contains(CHROMECAST_MARKER) -> CHROMECAST
                else -> UNKNOWN
            }
        }

        private fun isSpeaker(device: String, model: String): Boolean =
            speakerNameMarkers.any(device::contains) || model.contains(SPEAKER_MODEL_MARKER)
    }
}
