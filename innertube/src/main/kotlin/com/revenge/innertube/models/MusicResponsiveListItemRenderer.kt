package com.revenge.innertube.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

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
        get() {
            val endpoint = navigationEndpoint ?: return true
            return endpoint.watchEndpoint != null || endpoint.watchPlaylistEndpoint != null
        }

    val isPlaylist: Boolean
        get() = navigationEndpoint?.browseEndpoint?.isPlaylistEndpoint == true

    val isAlbum: Boolean
        get() = navigationEndpoint?.browseEndpoint?.isAlbumEndpoint == true

    val isArtist: Boolean
        get() {
            val pageType = navigationEndpoint?.browseEndpoint?.pageType
            return pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_LIBRARY_ARTIST"
        }

    val musicVideoType: String?
        get() = overlay
            ?.musicItemThumbnailOverlayRenderer
            ?.content
            ?.musicPlayButtonRenderer
            ?.playNavigationEndpoint
            ?.musicVideoType
            ?: navigationEndpoint?.musicVideoType

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
        )

        @Serializable
        data class Content(
            val musicPlayButtonRenderer: MusicPlayButtonRenderer,
        )

        @Serializable
        data class MusicPlayButtonRenderer(
            val playNavigationEndpoint: NavigationEndpoint?,
        )
    }
}
