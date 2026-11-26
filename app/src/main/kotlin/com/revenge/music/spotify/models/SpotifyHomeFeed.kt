package com.revenge.music.spotify.models

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyHomeFeed(
  val greeting: String? = null,
  val sections: List<SpotifyHomeFeedSection> = emptyList(),
)

@Serializable
data class SpotifyHomeFeedSection(
  val sectionUri: String = "",
  val title: String? = null,
  val typename: String = "",
  val totalCount: Int = 0,
  val items: List<SpotifyHomeFeedItem> = emptyList(),
)

@Serializable
sealed interface SpotifyHomeFeedItem {
  val uri: String

  @Serializable
  data class Playlist(
    override val uri: String = "",
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val format: String? = null,
    val totalCount: Int = 0,
    val imageUrl: String? = null,
    val extractedColorHex: String? = null,
    val ownerName: String? = null,
    val madeForUsername: String? = null,
  ) : SpotifyHomeFeedItem

  @Serializable
  data class Album(
    override val uri: String = "",
    val id: String = "",
    val name: String = "",
    val albumType: String? = null,
    val artists: List<SpotifySimpleArtist> = emptyList(),
    val imageUrl: String? = null,
  ) : SpotifyHomeFeedItem

  @Serializable
  data class Artist(
    override val uri: String = "",
    val id: String = "",
    val name: String = "",
    val imageUrl: String? = null,
  ) : SpotifyHomeFeedItem
}
