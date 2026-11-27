package com.revenge.innertube.models

import kotlinx.serialization.Serializable

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
    get() = navigationEndpoint.browseEndpoint?.isPlaylistEndpoint == true

  val isAlbum: Boolean
    get() = navigationEndpoint.browseEndpoint?.isAlbumEndpoint == true

  val isArtist: Boolean
    get() = navigationEndpoint.browseEndpoint?.isArtistEndpoint == true

  val musicVideoType: String?
    get() =
      thumbnailOverlay
        ?.musicItemThumbnailOverlayRenderer
        ?.content
        ?.musicPlayButtonRenderer
        ?.playNavigationEndpoint
        ?.musicVideoType ?: navigationEndpoint.musicVideoType
}
