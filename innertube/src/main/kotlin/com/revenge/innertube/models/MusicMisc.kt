package com.revenge.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Button(
    val buttonRenderer: ButtonRenderer,
) {
    @Serializable
    data class ButtonRenderer(
        val text: Runs,
        val navigationEndpoint: NavigationEndpoint?,
        val command: NavigationEndpoint?,
        val icon: Icon?,
    )
}

@Serializable
data class MusicNavigationButtonRenderer(
    val buttonText: Runs,
    val solid: Solid?,
    val iconStyle: IconStyle?,
    val clickCommand: NavigationEndpoint,
) {
    @Serializable
    data class Solid(
        val leftStripeColor: Long,
    )

    @Serializable
    data class IconStyle(
        val icon: Icon,
    )
}

@Serializable
data class MusicMultiRowListItemRenderer(
    val title: Runs?,
    val secondTitle: Runs? = null,
    val subtitle: Runs? = null,
    val description: Runs? = null,
    val thumbnail: ThumbnailRenderer?,
    val onTap: NavigationEndpoint?,
    val playbackProgress: PlaybackProgress? = null,
) {
    @Serializable
    data class PlaybackProgress(
        val musicPlaybackProgressRenderer: MusicPlaybackProgressRenderer?,
    )

    @Serializable
    data class MusicPlaybackProgressRenderer(
        val playbackProgressPercentage: Int? = null,
        val durationText: Runs? = null,
    )
}

@Serializable
data class MusicMultiRowImageItemRenderer(
    val title: Runs,
    val subtitle: Runs,
    val thumbnail: ThumbnailRenderer,
    val onTap: NavigationEndpoint,
)

@Serializable
data class MusicShuffleRenderer(
    val title: Runs?,
    val subtitle: Runs?,
    val thumbnail: ThumbnailRenderer?,
) {
    fun getThumbnailUrl(): String? =
        thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
            ?: thumbnail?.croppedSquareThumbnailRenderer?.getThumbnailUrl()
}

@Serializable
data class MusicQueueRenderer(
    val content: Content?,
    val header: Header?,
) {
    @Serializable
    data class Content(
        val playlistPanelRenderer: PlaylistPanelRenderer,
    )

    @Serializable
    data class Header(
        val musicQueueHeaderRenderer: MusicQueueHeaderRenderer?,
    )

    @Serializable
    data class MusicQueueHeaderRenderer(
        val title: Runs?,
        val subtitle: Runs?,
    )
}
