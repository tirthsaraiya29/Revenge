package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.BrowseEndpoint
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.MusicTwoRowItemRenderer
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.metadata.cleaner.ApiText

data class ArtistItemsPage(
  val title: String,
  val items: List<YTItem>,
  val continuation: String?,
) {
  companion object {
    fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
      val videoId = renderer.findVideoId() ?: return null
      val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null

      val artistRuns = renderer.runsAt(1).splitBySeparator().firstOrNull()?.oddElements().orEmpty()
      if (artistRuns.isEmpty()) return null
      val artists = artistRuns.map {
        Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
      }

      val albumRun =
        renderer.flexColumns
          .lastOrNull()
          ?.musicResponsiveListItemFlexColumnRenderer
          ?.text
          ?.runs
          ?.firstOrNull()
      val album =
        albumRun?.navigationEndpoint?.browseEndpoint?.browseId?.let {
          Album(ApiText.clean(albumRun.text), it)
        }

      val thumbnail = renderer.thumbnail?.thumbnailUrl() ?: return null
      val tokens = renderer.libraryTokens()

      return SongItem(
        id = videoId,
        title = title,
        artists = artists,
        album = album,
        duration = null,
        musicVideoType = renderer.musicVideoType,
        thumbnail = thumbnail,
        explicit = renderer.badges.hasExplicitBadge(),
        endpoint = renderer.overlayPlayEndpoint()?.watchEndpoint,
        libraryAddToken = tokens.addToken,
        libraryRemoveToken = tokens.removeToken,
      )
    }

    fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
      val browse = renderer.navigationEndpoint.browseEndpoint
      return when {
        renderer.isSong -> song(renderer)
        browse?.isAlbumEndpoint == true -> album(renderer, browse)
        browse?.isPlaylistEndpoint == true -> playlist(renderer, browse)
        else -> null
      }
    }

    private fun song(renderer: MusicTwoRowItemRenderer): SongItem? {
      val watch = renderer.navigationEndpoint.anyWatchEndpoint ?: return null
      val id = watch.videoId ?: return null
      val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null

      val artistRuns =
        renderer.subtitle?.runs.orEmpty().splitBySeparator().firstOrNull()?.oddElements().orEmpty()
      if (artistRuns.isEmpty()) return null
      val artists = artistRuns.map {
        Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
      }

      val thumbnail = renderer.thumbnailRenderer.thumbnailUrl() ?: return null
      return SongItem(
        id = id,
        title = title,
        artists = artists,
        thumbnail = thumbnail,
        explicit = renderer.subtitleBadges.hasExplicitBadge(),
        musicVideoType = renderer.musicVideoType,
        endpoint = watch,
      )
    }

    private fun album(renderer: MusicTwoRowItemRenderer, browse: BrowseEndpoint): AlbumItem? {
      val playlistId =
        renderer.thumbnailOverlay?.playEndpoint()?.watchPlaylistEndpoint?.playlistId ?: return null
      val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null
      val year = renderer.subtitle?.runs?.lastOrNull()?.text?.trim()?.toIntOrNull()
      val thumbnail = renderer.thumbnailRenderer.thumbnailUrl() ?: return null
      return AlbumItem(
        browseId = browse.browseId,
        playlistId = playlistId,
        title = title,
        artists = null,
        year = year,
        thumbnail = thumbnail,
        explicit = renderer.subtitleBadges.hasExplicitBadge(),
      )
    }

    private fun playlist(renderer: MusicTwoRowItemRenderer, browse: BrowseEndpoint): PlaylistItem? {
      val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null
      val author =
        renderer.subtitle?.runs?.firstOrNull()?.let { Artist(ApiText.clean(it.text), null) }
      val songCountText = renderer.subtitle?.runs?.getOrNull(4)?.text
      val playEndpoint = renderer.thumbnailOverlay?.playEndpoint()?.watchEndpoint ?: return null
      val shuffleEndpoint = renderer.menu.findMenuWatchEndpoint("MUSIC_SHUFFLE") ?: return null
      val radioEndpoint = renderer.menu.findMenuWatchEndpoint("MIX") ?: return null
      return PlaylistItem(
        id = browse.browseId.removePrefix("VL"),
        title = title,
        author = author,
        songCountText = songCountText,
        thumbnail = renderer.thumbnailRenderer.thumbnailUrl(),
        playEndpoint = playEndpoint,
        shuffleEndpoint = shuffleEndpoint,
        radioEndpoint = radioEndpoint,
      )
    }
  }
}
