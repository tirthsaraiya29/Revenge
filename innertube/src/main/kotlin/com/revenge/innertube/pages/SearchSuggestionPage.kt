package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.ArtistItem
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.clean
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.metadata.cleaner.ApiText

object SearchSuggestionPage {
  fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): YTItem? =
    when {
      renderer.isSong -> {
        val secondary =
          renderer.flexColumns
            .getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.splitBySeparator()
            ?.clean()

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
            secondary?.firstOrNull()?.oddElements()?.map {
              Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
            } ?: return null,
          album =
            renderer.flexColumns
              .getOrNull(2)
              ?.musicResponsiveListItemFlexColumnRenderer
              ?.text
              ?.runs
              ?.firstOrNull()
              ?.let {
                Album(
                  ApiText.clean(it.text),
                  it.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                )
              },
          duration = null,
          musicVideoType = renderer.musicVideoType,
          thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
          explicit =
            renderer.badges?.any {
              it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
            } == true,
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
              ?.watchPlaylistEndpoint,
          radioEndpoint =
            renderer.menu
              ?.menuRenderer
              ?.items
              ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
              ?.menuNavigationItemRenderer
              ?.navigationEndpoint
              ?.watchPlaylistEndpoint,
        )
      renderer.isAlbum -> {
        val secondary =
          renderer.flexColumns
            .getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.splitBySeparator() ?: return null
        AlbumItem(
          browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
          playlistId =
            renderer.menu
              ?.menuRenderer
              ?.items
              ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
              ?.menuNavigationItemRenderer
              ?.navigationEndpoint
              ?.watchPlaylistEndpoint
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
          year = secondary.lastOrNull()?.firstOrNull()?.text?.toIntOrNull(),
          thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
          explicit =
            renderer.badges?.any {
              it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
            } == true,
        )
      }
      else -> null
    }
}
