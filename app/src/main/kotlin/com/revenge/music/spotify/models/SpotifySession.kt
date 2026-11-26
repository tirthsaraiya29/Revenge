package com.revenge.music.spotify.models

import kotlinx.serialization.Serializable

@Serializable
data class SpotifySession(
  val spDc: String,
  val spKey: String? = null,
  val accessToken: String? = null,
  val expiresAt: Long = 0L,
  val accountName: String? = null,
  val accountAvatarUrl: String? = null,
) {
  val isAuthenticated: Boolean
    get() = spDc.isNotBlank() && !accessToken.isNullOrBlank()
}
