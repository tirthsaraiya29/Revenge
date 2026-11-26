package com.revenge.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Menu(
  val menuRenderer: MenuRenderer,
) {
  @Serializable
  data class MenuRenderer(
    val items: List<Item>?,
    val topLevelButtons: List<TopLevelButton>?,
  ) {
    @Serializable
    data class Item(
      val menuNavigationItemRenderer: MenuNavigationItemRenderer?,
      val menuServiceItemRenderer: MenuServiceItemRenderer?,
      val toggleMenuServiceItemRenderer: ToggleMenuServiceRenderer?,
    ) {
      @Serializable
      data class MenuNavigationItemRenderer(
        val text: Runs,
        val icon: Icon,
        val navigationEndpoint: NavigationEndpoint,
      )

      @Serializable
      data class MenuServiceItemRenderer(
        val text: Runs,
        val icon: Icon,
        val serviceEndpoint: NavigationEndpoint,
      )

      @Serializable
      data class ToggleMenuServiceRenderer(
        val defaultIcon: Icon,
        val defaultServiceEndpoint: DefaultServiceEndpoint,
        val toggledServiceEndpoint: ToggledServiceEndpoint?,
      )
    }

    @Serializable
    data class TopLevelButton(
      val buttonRenderer: ButtonRenderer?,
    ) {
      @Serializable
      data class ButtonRenderer(
        val icon: Icon,
        val navigationEndpoint: NavigationEndpoint,
      )
    }
  }
}

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
