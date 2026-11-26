package com.revenge.innertube.pages

import com.revenge.innertube.models.BrowseEndpoint
import com.revenge.innertube.models.GridRenderer
import com.revenge.innertube.models.MusicNavigationButtonRenderer
import com.revenge.innertube.models.SectionListRenderer

/** Explore's moods-and-genres grid. */
data class MoodAndGenres(
  val title: String,
  val items: List<Item>,
) {
  data class Item(
    val title: String,
    val stripeColor: Long,
    val endpoint: BrowseEndpoint,
  )

  companion object {
    fun fromSectionListRendererContent(content: SectionListRenderer.Content): MoodAndGenres? {
      val grid = content.gridRenderer ?: return null
      return MoodAndGenres(
        title = grid.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: return null,
        items =
          grid.items
            .mapNotNull(GridRenderer.Item::musicNavigationButtonRenderer)
            .mapNotNull(::fromMusicNavigationButtonRenderer),
      )
    }

    fun fromMusicNavigationButtonRenderer(renderer: MusicNavigationButtonRenderer): Item? =
      Item(
        title = renderer.buttonText.runs?.firstOrNull()?.text ?: return null,
        stripeColor = renderer.solid?.leftStripeColor ?: return null,
        endpoint = renderer.clickCommand.browseEndpoint ?: return null,
      )
  }
}
