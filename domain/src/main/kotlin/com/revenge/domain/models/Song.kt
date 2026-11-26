package com.revenge.domain.models

/**
 * A playable track in the player's catalog, independent of any storage or network source.
 *
 * @property durationSeconds track length in seconds.
 * @property thumbnailUrl remote artwork, or a local content URI for local tracks.
 * @property totalPlayTimeMs cumulative listening time used by recommendation heuristics.
 */
data class Song(
  val id: String,
  val title: String,
  val artists: List<ArtistRef>,
  val album: AlbumRef?,
  val durationSeconds: Int,
  val thumbnailUrl: String?,
  val isExplicit: Boolean = false,
  val isLocal: Boolean = false,
  val liked: Boolean = false,
  val totalPlayTimeMs: Long = 0L,
)

data class ArtistRef(
  val id: String,
  val name: String,
)

data class AlbumRef(
  val id: String,
  val title: String,
)
