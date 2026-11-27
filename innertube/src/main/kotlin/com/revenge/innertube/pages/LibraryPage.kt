package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.ArtistItem
import com.revenge.innertube.models.BrowseEndpoint
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.MusicTwoRowItemRenderer
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.oddElements
import com.revenge.metadata.cleaner.ApiText

data class LibraryPage(
  val items: List<YTItem>,
  val continuation: String? = null,
) {
  companion object {
    fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
      val browse = renderer.navigationEndpoint.browseEndpoint ?: return null
      return when {
        browse.isAlbumEndpoint -> album(renderer, browse)
        browse.isPlaylistEndpoint -> playlist(renderer, browse)
        browse.isArtistEndpoint -> artist(renderer, browse)
        else -> null
      }
    }

    private fun album(renderer: MusicTwoRowItemRenderer, browse: BrowseEndpoint): AlbumItem? {
      val playlistId =
        renderer.thumbnailOverlay?.playEndpoint()?.anyWatchEndpoint?.playlistId ?: return null
      val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null
      val artists =
        renderer.subtitle
          ?.runs
          .orEmpty()
          .filter { it.navigationEndpoint?.browseEndpoint?.browseId != null }
          .map { Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId) }
      val year = renderer.subtitle?.runs?.lastOrNull()?.text?.trim()?.toIntOrNull()
      val thumbnail = renderer.thumbnailRenderer.thumbnailUrl() ?: return null
      return AlbumItem(
        browseId = browse.browseId,
        playlistId = playlistId,
        title = title,
        artists = artists,
        year = year,
        thumbnail = thumbnail,
        explicit = renderer.subtitleBadges.hasExplicitBadge(),
      )
    }

    private fun playlist(renderer: MusicTwoRowItemRenderer, browse: BrowseEndpoint): PlaylistItem? {
      val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null
      val author =
        renderer.subtitle?.runs?.firstOrNull()?.let { Artist(ApiText.clean(it.text), null) }
      val songCountText = renderer.subtitle?.runs?.lastOrNull()?.text
      return PlaylistItem(
        id = browse.browseId.removePrefix("VL"),
        title = title,
        author = author,
        songCountText = songCountText,
        thumbnail = renderer.thumbnailRenderer.thumbnailUrl(),
        playEndpoint = renderer.thumbnailOverlay?.playEndpoint()?.watchEndpoint,
        shuffleEndpoint = renderer.menu.findMenuWatchEndpoint("MUSIC_SHUFFLE"),
        radioEndpoint = renderer.menu.findMenuWatchEndpoint("MIX"),
        isEditable = renderer.menu.hasMenuIcon("EDIT"),
      )
    }

    private fun artist(renderer: MusicTwoRowItemRenderer, browse: BrowseEndpoint): ArtistItem? {
      val title = renderer.title.runs?.lastOrNull()?.text?.let(ApiText::clean) ?: return null
      val shuffleEndpoint = renderer.menu.findMenuWatchEndpoint("MUSIC_SHUFFLE") ?: return null
      val radioEndpoint = renderer.menu.findMenuWatchEndpoint("MIX") ?: return null
      return ArtistItem(
        id = browse.browseId,
        title = title,
        thumbnail = renderer.thumbnailRenderer.thumbnailUrl(),
        shuffleEndpoint = shuffleEndpoint,
        radioEndpoint = radioEndpoint,
      )
    }

    fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): YTItem? =
      when {
        renderer.isSong -> song(renderer)
        renderer.isArtist -> artist(renderer)
        else -> null
      }

    private fun song(renderer: MusicResponsiveListItemRenderer): SongItem? {
      val videoId = renderer.findVideoId() ?: return null
      val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null

      val artists =
        renderer.runsAt(1).oddElements().map {
          Artist(
            ApiText.clean(it.text),
            it.navigationEndpoint?.browseEndpoint?.browseId ?: "",
          )
        }

      val albumRun = renderer.runsAt(2).firstOrNull()
      val album = albumRun?.let {
        Album(
          ApiText.clean(it.text),
          it.navigationEndpoint?.browseEndpoint?.browseId ?: "",
        )
      }

      val thumbnail = renderer.thumbnail?.thumbnailUrl() ?: return null
      val tokens = renderer.libraryTokens()

      return SongItem(
        id = videoId,
        title = title,
        artists = artists,
        album = album,
        duration = renderer.durationSeconds(),
        musicVideoType = renderer.musicVideoType,
        thumbnail = thumbnail,
        explicit = renderer.badges.hasExplicitBadge(),
        endpoint = renderer.overlayPlayEndpoint()?.watchEndpoint,
        libraryAddToken = tokens.addToken,
        libraryRemoveToken = tokens.removeToken,
      )
    }

    private fun artist(renderer: MusicResponsiveListItemRenderer): ArtistItem? {
      val browse = renderer.navigationEndpoint?.browseEndpoint ?: return null
      val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null
      val thumbnail = renderer.thumbnail?.thumbnailUrl() ?: return null
      return ArtistItem(
        id = browse.browseId,
        title = title,
        thumbnail = thumbnail,
        shuffleEndpoint = renderer.menu.findMenuWatchEndpoint("MUSIC_SHUFFLE"),
        radioEndpoint = renderer.menu.findMenuWatchEndpoint("MIX"),
      )
    }
  }
}
