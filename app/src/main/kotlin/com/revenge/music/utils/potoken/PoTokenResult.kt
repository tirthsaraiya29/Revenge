package com.revenge.music.utils.potoken

/**
 * Tokens minted for a single web-client request.
 *
 * [playerRequestPoToken] belongs in `serviceIntegrityDimensions.poToken` of the player request;
 * [streamingDataPoToken] is the `pot` query parameter of the media URL.
 */
data class PoTokenResult(
  val playerRequestPoToken: String?,
  val streamingDataPoToken: String?,
  val visitorData: String? = null,
)
