package com.revenge.music.spotifyimport

import com.revenge.music.spotify.SpotifyMapper
import com.revenge.music.spotify.models.SpotifyPlaylist

enum class SpotifyImportSourceType {
  PLAYLIST,
  LIKED_SONGS,
}

data class SpotifyImportSession(
  val isAuthenticated: Boolean = false,
  val accountName: String = "",
  val accountAvatarUrl: String? = null,
)

sealed interface SpotifyImportSource {
  val id: String
  val title: String
  val subtitle: String
  val thumbnailUrl: String?
  val trackCount: Int?
  val localPlaylistId: String
  val type: SpotifyImportSourceType

  data class Playlist(val playlist: SpotifyPlaylist) : SpotifyImportSource {
    override val id: String = "playlist:${playlist.id}"
    override val title: String = playlist.name
    override val subtitle: String = playlist.owner?.displayName.orEmpty()
    override val thumbnailUrl: String? = SpotifyMapper.getPlaylistThumbnail(playlist)
    override val trackCount: Int? = playlist.tracks?.total
    override val localPlaylistId: String = "SPOTIFY_PLAYLIST_${playlist.id}"
    override val type: SpotifyImportSourceType = SpotifyImportSourceType.PLAYLIST
  }

  data class LikedSongs(
    override val title: String,
    override val trackCount: Int,
  ) : SpotifyImportSource {
    override val id: String = "liked_songs"
    override val subtitle: String = ""
    override val thumbnailUrl: String? = null
    override val localPlaylistId: String = "SPOTIFY_LIKED_SONGS"
    override val type: SpotifyImportSourceType = SpotifyImportSourceType.LIKED_SONGS
  }
}

data class SpotifyImportSourceUi(
  val id: String,
  val title: String,
  val subtitle: String,
  val thumbnailUrl: String?,
  val trackCount: Int?,
  val type: SpotifyImportSourceType,
)

data class SpotifyImportProgressUi(
  val sourceTitle: String,
  val completedSources: Int,
  val totalSources: Int,
  val matchedTracks: Int,
  val totalTracks: Int,
  val percent: Int,
)

data class SpotifyImportSourceSummaryUi(
  val title: String,
  val totalTracks: Int,
  val importedTracks: Int,
  val failedTracks: Int,
)

data class SpotifyImportSummaryUi(
  val sources: List<SpotifyImportSourceSummaryUi>,
) {
  val sourceCount: Int
    get() = sources.size

  val totalTracks: Int
    get() = sources.sumOf { it.totalTracks }

  val importedTracks: Int
    get() = sources.sumOf { it.importedTracks }

  val failedTracks: Int
    get() = sources.sumOf { it.failedTracks }
}

data class SpotifyImportUiState(
  val isAuthenticated: Boolean = false,
  val accountName: String = "",
  val accountAvatarUrl: String? = null,
  val isLoading: Boolean = false,
  val sources: List<SpotifyImportSourceUi> = emptyList(),
  val selectedSourceIds: Set<String> = emptySet(),
  val progress: SpotifyImportProgressUi? = null,
  val summary: SpotifyImportSummaryUi? = null,
  val errorMessage: String? = null,
) {
  val hasSources: Boolean
    get() = sources.isNotEmpty()

  val canImport: Boolean
    get() = selectedSourceIds.isNotEmpty() && progress == null && !isLoading
}
