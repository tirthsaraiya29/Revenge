package com.revenge.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistPanelRenderer(
  val title: String?,
  val titleText: Runs?,
  val shortBylineText: Runs?,
  val contents: List<Content>,
  val isInfinite: Boolean?,
  val numItemsToShow: Int?,
  val playlistId: String?,
  val continuations: List<Continuation>?,
) {
  @Serializable
  data class Content(
    val playlistPanelVideoRenderer: PlaylistPanelVideoRenderer?,
    val automixPreviewVideoRenderer: AutomixPreviewVideoRenderer?,
  )
}

@Serializable
data class PlaylistPanelVideoRenderer(
  val title: Runs?,
  val lengthText: Runs?,
  val longBylineText: Runs?,
  val shortBylineText: Runs?,
  val badges: List<Badges>?,
  val videoId: String?,
  val playlistSetVideoId: String?,
  val selected: Boolean,
  val thumbnail: Thumbnails,
  val unplayableText: Runs?,
  val menu: Menu?,
  val navigationEndpoint: NavigationEndpoint,
)

@Serializable
data class AutomixPreviewVideoRenderer(
  val content: Content,
) {
  @Serializable
  data class Content(
    val automixPlaylistVideoRenderer: AutomixPlaylistVideoRenderer,
  ) {
    @Serializable
    data class AutomixPlaylistVideoRenderer(
      val navigationEndpoint: NavigationEndpoint,
    )
  }
}
