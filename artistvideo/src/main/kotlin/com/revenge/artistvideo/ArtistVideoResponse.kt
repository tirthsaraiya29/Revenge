package com.revenge.artistvideo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire model for the third-party artist-video archive. All fields are optional. */
@Serializable
data class ArtistVideoResponse(
  val name: String? = null,
  val artist: String? = null,
  @SerialName("albumId") val albumId: String? = null,
  val static: String? = null,
  val animated: String? = null,
  val videoUrl: String? = null,
) {
  val preferredAnimationUrl: String?
    get() = animated ?: videoUrl
}
