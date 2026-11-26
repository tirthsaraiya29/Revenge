package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.ArtistItem
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.innertube.utils.parseTime
import com.revenge.metadata.cleaner.ApiText

data class SearchResult(
  val items: List<YTItem>,
  val continuation: String? = null,
)

object SearchPage {
  fun toYTItem(renderer: MusicResponsiveListItemRenderer): YTItem? {
    PodcastPage.show(renderer)?.let { return it }
    PodcastPage.episode(renderer)?.let { return it }

    val secondary =
      renderer.flexColumns
        .getOrNull(1)
        ?.musicResponsiveListItemFlexColumnRenderer
        ?.text
        ?.runs
        ?.splitBySeparator() ?: return null

    return when {
      renderer.isSong -> {
        val tokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

        val videoId =
          renderer.playlistItemData?.videoId
            ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
            ?: renderer.overlay
              ?.musicItemThumbnailOverlayRenderer
              ?.content
              ?.musicPlayButtonRenderer
              ?.playNavigationEndpoint
              ?.watchEndpoint
              ?.videoId
            ?: renderer.flexColumns
              .firstOrNull()
              ?.musicResponsiveListItemFlexColumnRenderer
              ?.text
              ?.runs
              ?.firstOrNull()
              ?.navigationEndpoint
              ?.watchEndpoint
              ?.videoId
            ?: return null

        SongItem(
          id = videoId,
          title =
            renderer.flexColumns
              .firstOrNull()
              ?.musicResponsiveListItemFlexColumnRenderer
              ?.text
              ?.runs
              ?.firstOrNull()
              ?.text
              ?.let(ApiText::clean) ?: return null,
          artists =
            secondary.firstOrNull()?.oddElements()?.map {
              Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
            } ?: return null,
          album =
            secondary
              .getOrNull(1)
              ?.firstOrNull()
              ?.takeIf { it.navigationEndpoint?.browseEndpoint != null }
              ?.let {
                Album(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId!!)
              },
          duration = secondary.lastOrNull()?.firstOrNull()?.text?.parseTime(),
          musicVideoType = renderer.musicVideoType,
          thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
          explicit =
            renderer.badges?.any {
              it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
            } == true,
          libraryAddToken = tokens.addToken,
          libraryRemoveToken = tokens.removeToken,
        )
      }
      renderer.isArtist ->
        ArtistItem(
          id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
          title =
            renderer.flexColumns
              .firstOrNull()
              ?.musicResponsiveListItemFlexColumnRenderer
              ?.text
              ?.runs
              ?.firstOrNull()
              ?.text
              ?.let(ApiText::clean) ?: return null,
          thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
          shuffleEndpoint =
            renderer.menu
              ?.menuRenderer
              ?.items
              ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
              ?.menuNavigationItemRenderer
              ?.navigationEndpoint
              ?.watchPlaylistEndpoint ?: return null,
          radioEndpoint =
            renderer.menu
              ?.menuRenderer
              ?.items
              ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
              ?.menuNavigationItemRenderer
              ?.navigationEndpoint
              ?.watchPlaylistEndpoint ?: return null,
        )
      renderer.isAlbum ->
        AlbumItem(
          browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
          playlistId =
            renderer.overlay
              ?.musicItemThumbnailOverlayRenderer
              ?.content
              ?.musicPlayButtonRenderer
              ?.playNavigationEndpoint
              ?.anyWatchEndpoint
              ?.playlistId ?: return null,
          title =
            renderer.flexColumns
              .firstOrNull()
              ?.musicResponsiveListItemFlexColumnRenderer
              ?.text
              ?.runs
              ?.firstOrNull()
              ?.text
              ?.let(ApiText::clean) ?: return null,
          artists =
            secondary.getOrNull(1)?.oddElements()?.map {
              Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
            } ?: return null,
          year = secondary.getOrNull(2)?.firstOrNull()?.text?.toIntOrNull(),
          thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
          explicit =
            renderer.badges?.any {
              it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
            } == true,
        )
      renderer.isPlaylist ->
        PlaylistItem(
          id =
            renderer.navigationEndpoint?.browseEndpoint?.browseId?.removePrefix("VL")
              ?: return null,
          title =
            renderer.flexColumns
              .firstOrNull()
              ?.musicResponsiveListItemFlexColumnRenderer
              ?.text
              ?.runs
              ?.firstOrNull()
              ?.text
              ?.let(ApiText::clean) ?: return null,
          author =
            secondary.firstOrNull()?.firstOrNull()?.let {
              Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
            } ?: return null,
          songCountText =
            renderer.flexColumns
              .getOrNull(1)
              ?.musicResponsiveListItemFlexColumnRenderer
              ?.text
              ?.runs
              ?.lastOrNull()
              ?.text ?: return null,
          thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
          playEndpoint =
            renderer.overlay
              ?.musicItemThumbnailOverlayRenderer
              ?.content
              ?.musicPlayButtonRenderer
              ?.playNavigationEndpoint
              ?.watchPlaylistEndpoint ?: return null,
          shuffleEndpoint =
            renderer.menu
              ?.menuRenderer
              ?.items
              ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
              ?.menuNavigationItemRenderer
              ?.navigationEndpoint
              ?.watchPlaylistEndpoint ?: return null,
          radioEndpoint =
            renderer.menu
              ?.menuRenderer
              ?.items
              ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
              ?.menuNavigationItemRenderer
              ?.navigationEndpoint
              ?.watchPlaylistEndpoint ?: return null,
        )
      else -> null
    }
  }
}
