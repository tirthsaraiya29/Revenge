@file:OptIn(ExperimentalSerializationApi::class)

package com.revenge.innertube.models

import com.revenge.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ALBUM
import com.revenge.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ARTIST
import com.revenge.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_AUDIOBOOK
import com.revenge.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_LIBRARY_ARTIST
import com.revenge.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_PLAYLIST
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * The standard list row. It powers quick picks, search results and table views, and its flexible
 * columns carry title, artist and album metadata.
 */
@Serializable
data class MusicResponsiveListItemRenderer(
  val badges: List<Badges>?,
  val fixedColumns: List<FlexColumn>?,
  val flexColumns: List<FlexColumn>,
  val thumbnail: ThumbnailRenderer?,
  val menu: Menu?,
  val playlistItemData: PlaylistItemData?,
  val overlay: Overlay?,
  val navigationEndpoint: NavigationEndpoint?,
) {
  val isSong: Boolean
    get() =
      navigationEndpoint == null ||
        navigationEndpoint.watchEndpoint != null ||
        navigationEndpoint.watchPlaylistEndpoint != null

  val isPlaylist: Boolean
    get() = pageType == MUSIC_PAGE_TYPE_PLAYLIST

  val isAlbum: Boolean
    get() = pageType == MUSIC_PAGE_TYPE_ALBUM || pageType == MUSIC_PAGE_TYPE_AUDIOBOOK

  val isArtist: Boolean
    get() = pageType == MUSIC_PAGE_TYPE_ARTIST || pageType == MUSIC_PAGE_TYPE_LIBRARY_ARTIST

  val musicVideoType: String?
    get() =
      overlay
        ?.musicItemThumbnailOverlayRenderer
        ?.content
        ?.musicPlayButtonRenderer
        ?.playNavigationEndpoint
        ?.musicVideoType ?: navigationEndpoint?.musicVideoType

  private val pageType: String?
    get() =
      navigationEndpoint
        ?.browseEndpoint
        ?.browseEndpointContextSupportedConfigs
        ?.browseEndpointContextMusicConfig
        ?.pageType

  @Serializable
  data class FlexColumn(
    @JsonNames("musicResponsiveListItemFixedColumnRenderer")
    val musicResponsiveListItemFlexColumnRenderer: MusicResponsiveListItemFlexColumnRenderer,
  ) {
    @Serializable
    data class MusicResponsiveListItemFlexColumnRenderer(
      val text: Runs?,
    )
  }

  @Serializable
  data class PlaylistItemData(
    val playlistSetVideoId: String?,
    val videoId: String,
  )

  @Serializable
  data class Overlay(
    val musicItemThumbnailOverlayRenderer: MusicItemThumbnailOverlayRenderer,
  ) {
    @Serializable
    data class MusicItemThumbnailOverlayRenderer(
      val content: Content,
    ) {
      @Serializable
      data class Content(
        val musicPlayButtonRenderer: MusicPlayButtonRenderer,
      ) {
        @Serializable
        data class MusicPlayButtonRenderer(
          val playNavigationEndpoint: NavigationEndpoint?,
        )
      }
    }
  }
}
