package com.revenge.music.spotify.models

import kotlinx.serialization.Serializable

@Serializable
sealed interface SpotifyLibraryItem {
  val uri: String

  @Serializable
  data class Playlist(val playlist: SpotifyPlaylist) : SpotifyLibraryItem {
    override val uri: String
      get() = playlist.uri ?: "spotify:playlist:${playlist.id}"
  }

  @Serializable
  data class Folder(val folder: SpotifyLibraryFolder) : SpotifyLibraryItem {
    override val uri: String
      get() = folder.uri
  }
}

@Serializable
data class SpotifyLibraryFolder(
  val uri: String = "",
  val name: String = "",
  val totalChildren: Int = 0,
)
