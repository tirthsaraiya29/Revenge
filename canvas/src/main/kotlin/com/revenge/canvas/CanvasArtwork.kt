package com.revenge.canvas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single animated artwork ("canvas") resolved from any provider.
 *
 * Instances are persisted as JSON by the app, so every field must keep its name and default.
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
  /** The animation URL a player should use, preferring the explicitly animated stream. */
  val preferredAnimationUrl: String?
    get() = animated ?: videoUrl

  /** True when this artwork is a fragment of a YouTube video rather than a full canvas loop. */
  val isYouTubeSnippet: Boolean
    get() =
      clipStartMs != null ||
        clipEndMs != null ||
        videoUrl.isYouTubeHosted() ||
        animated.isYouTubeHosted()

  private fun String?.isYouTubeHosted(): Boolean =
    this != null &&
      (contains("googlevideo.com", ignoreCase = true) ||
        contains("youtube.com", ignoreCase = true))
}
