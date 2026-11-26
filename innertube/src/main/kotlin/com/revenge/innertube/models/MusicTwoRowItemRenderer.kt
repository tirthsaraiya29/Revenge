package com.revenge.innertube.models

import com.revenge.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ALBUM
import com.revenge.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ARTIST
import com.revenge.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_AUDIOBOOK
import com.revenge.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_PLAYLIST
import kotlinx.serialization.Serializable

/**
 * The large-tile row: a big thumbnail plus title and subtitle. Used by grids and carousel shelves
 * for songs, videos, albums, playlists and artists.
 */
@Serializable
data class MusicTwoRowItemRenderer(
  val title: Runs,
  val subtitle: Runs?,
  val subtitleBadges: List<Badges>?,
  val menu: Menu?,
  val thumbnailRenderer: ThumbnailRenderer,
  val navigationEndpoint: NavigationEndpoint,
  val thumbnailOverlay: MusicResponsiveListItemRenderer.Overlay?,
) {
  val isSong: Boolean
    get() = navigationEndpoint.endpoint is WatchEndpoint

  val isPlaylist: Boolean
    get() = pageType == MUSIC_PAGE_TYPE_PLAYLIST

  val isAlbum: Boolean
    get() = pageType == MUSIC_PAGE_TYPE_ALBUM || pageType == MUSIC_PAGE_TYPE_AUDIOBOOK

  val isArtist: Boolean
    get() = pageType == MUSIC_PAGE_TYPE_ARTIST

  val musicVideoType: String?
    get() =
      thumbnailOverlay
        ?.musicItemThumbnailOverlayRenderer
        ?.content
        ?.musicPlayButtonRenderer
        ?.playNavigationEndpoint
        ?.musicVideoType ?: navigationEndpoint.musicVideoType

  private val pageType: String?
    get() =
      navigationEndpoint.browseEndpoint
        ?.browseEndpointContextSupportedConfigs
        ?.browseEndpointContextMusicConfig
        ?.pageType
}
