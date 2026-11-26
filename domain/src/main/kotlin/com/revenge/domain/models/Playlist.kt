package com.revenge.domain.models

/** A user-created playlist summary. */
data class Playlist(
  val id: String,
  val name: String,
  val songCount: Int = 0,
  val thumbnailUrl: String? = null,
)
