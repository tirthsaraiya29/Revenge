package com.revenge.music.models.lastfm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Authentication(
  val session: Session? = null,
) {
  @Serializable
  data class Session(
    val name: String = "",
    val key: String = "",
    val subscriber: Int = 0,
  )
}

@Serializable
data class TokenResponse(
  val token: String = "",
)

@Serializable
data class LastFmError(
  val error: Int = 0,
  val message: String = "",
)
