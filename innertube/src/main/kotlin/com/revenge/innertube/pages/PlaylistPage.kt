package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.innertube.utils.parseTime
import com.revenge.metadata.cleaner.ApiText

data class PlaylistPage(
  val playlist: PlaylistItem,
  val songs: List<SongItem>,
  val songsContinuation: String?,
  val continuation: String?,
  val related: List<YTItem>? = null,
) {
  companion object {
    fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
      val tokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)
      val secondary =
        renderer.flexColumns
          .getOrNull(1)
          ?.musicResponsiveListItemFlexColumnRenderer
          ?.text
          ?.runs
          ?.splitBySeparator()

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

      return SongItem(
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
          secondary
            ?.firstOrNull()
            ?.oddElements()
            ?.map {
              Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
            }
            .orEmpty(),
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
                it.navigationEndpoint?.browseEndpoint?.browseId ?: return@let null,
              )
            },
        duration =
          renderer.fixedColumns
            ?.firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.firstOrNull()
            ?.text
            ?.parseTime(),
        musicVideoType = renderer.musicVideoType,
        thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
        explicit =
          renderer.badges?.any {
            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
          } == true,
        endpoint =
          renderer.overlay
            ?.musicItemThumbnailOverlayRenderer
            ?.content
            ?.musicPlayButtonRenderer
            ?.playNavigationEndpoint
            ?.watchEndpoint,
        setVideoId =
          renderer.playlistItemData?.playlistSetVideoId
            ?: renderer.navigationEndpoint?.watchEndpoint?.playlistSetVideoId
            ?: renderer.overlay
              ?.musicItemThumbnailOverlayRenderer
              ?.content
              ?.musicPlayButtonRenderer
              ?.playNavigationEndpoint
              ?.watchEndpoint
              ?.playlistSetVideoId
            ?: renderer.flexColumns
              .firstOrNull()
              ?.musicResponsiveListItemFlexColumnRenderer
              ?.text
              ?.runs
              ?.firstOrNull()
              ?.navigationEndpoint
              ?.watchEndpoint
              ?.playlistSetVideoId,
        libraryAddToken = tokens.addToken,
        libraryRemoveToken = tokens.removeToken,
      )
    }
  }
}
