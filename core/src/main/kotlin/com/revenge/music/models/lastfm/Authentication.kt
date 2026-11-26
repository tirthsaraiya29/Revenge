package com.revenge.music.models.lastfm

import kotlinx.serialization.Serializable

@Serializable
data class Authentication(val session: Session) {
  @Serializable
  data class Session(
    val name: String,
    val key: String,
    val subscriber: Int,
  )
}

/** Response of the "get token" auth step. */
@Serializable data class TokenResponse(val token: String)

/** Last.fm's error envelope. */
@Serializable data class LastFmError(val error: Int, val message: String)
