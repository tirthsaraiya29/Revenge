package com.revenge.canvas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cross-provider animated-artwork descriptor. Persisted as JSON by the app, so the serial names,
 * nullability and null defaults below are compatibility-frozen.
 */
@Serializable
data class CanvasArtwork(
  val name: String? = null,
  val artist: String? = null,
  @SerialName("albumId") val albumId: String? = null,
  val albumName: String? = null,
  val static: String? = null,
  val animated: String? = null,
  val videoUrl: String? = null,
  val clipStartMs: Long? = null,
  val clipEndMs: Long? = null,
) {
  val preferredAnimationUrl: String?
    get() = animated ?: videoUrl

  val isYouTubeSnippet: Boolean
    get() =
      clipStartMs != null ||
        clipEndMs != null ||
        videoUrl.isYouTubeHost() ||
        animated.isYouTubeHost()
}

private fun String?.isYouTubeHost(): Boolean =
  this != null &&
    (contains("googlevideo.com", ignoreCase = true) || contains("youtube.com", ignoreCase = true))
