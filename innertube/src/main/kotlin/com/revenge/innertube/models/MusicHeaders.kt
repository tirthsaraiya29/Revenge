package com.revenge.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicResponsiveHeaderRenderer(
  val thumbnail: ThumbnailRenderer?,
  val buttons: List<Button>,
  val title: Runs,
  val subtitle: Runs,
  val secondSubtitle: Runs?,
  val straplineTextOne: Runs?,
  val description: Runs? = null,
) {
  @Serializable
  data class Button(
    val musicPlayButtonRenderer: MusicPlayButtonRenderer?,
    val menuRenderer: Menu.MenuRenderer?,
  )

  @Serializable
  data class MusicPlayButtonRenderer(
    val playNavigationEndpoint: NavigationEndpoint?,
  )
}

@Serializable
data class MusicEditablePlaylistDetailHeaderRenderer(
  val header: Header,
  val editHeader: EditHeader,
) {
  @Serializable
  data class Header(
    val musicDetailHeaderRenderer: MusicDetailHeaderRenderer?,
    val musicResponsiveHeaderRenderer: MusicResponsiveHeaderRenderer?,
  )

  @Serializable
  data class EditHeader(
    val musicPlaylistEditHeaderRenderer: MusicPlaylistEditHeaderRenderer?,
  )
}

@Serializable
data class MusicDetailHeaderRenderer(
  val title: Runs,
  val subtitle: Runs,
  val secondSubtitle: Runs,
  val description: Runs?,
  val thumbnail: ThumbnailRenderer,
  val menu: Menu,
)

@Serializable
data class MusicPlaylistEditHeaderRenderer(
  val editTitle: Runs?,
)

@Serializable
data class MusicDescriptionShelfRenderer(
  val header: Runs?,
  val subheader: Runs?,
  val description: Runs,
  val footer: Runs?,
)

@Serializable
data class SubscriptionButton(
  val subscribeButtonRenderer: SubscribeButtonRenderer,
) {
  @Serializable
  data class SubscribeButtonRenderer(
    val subscribed: Boolean,
    val channelId: String,
    val longSubscriberCountText: Runs? = null,
    val shortSubscriberCountText: Runs? = null,
  )
}
