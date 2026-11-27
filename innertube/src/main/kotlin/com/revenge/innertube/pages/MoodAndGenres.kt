package com.revenge.innertube.pages

import com.revenge.innertube.models.BrowseEndpoint
import com.revenge.innertube.models.MusicNavigationButtonRenderer
import com.revenge.innertube.models.SectionListRenderer
import com.revenge.metadata.cleaner.ApiText

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
      val title = grid.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: return null
      val items =
        grid.items.mapNotNull { item ->
          item.musicNavigationButtonRenderer?.let(::fromMusicNavigationButtonRenderer)
        }
      return MoodAndGenres(ApiText.clean(title), items)
    }

    fun fromMusicNavigationButtonRenderer(renderer: MusicNavigationButtonRenderer): Item? {
      val title = renderer.buttonText.runs?.firstOrNull()?.text ?: return null
      val stripeColor = renderer.solid?.leftStripeColor ?: return null
      val endpoint = renderer.clickCommand.browseEndpoint ?: return null
      return Item(ApiText.clean(title), stripeColor, endpoint)
    }
  }
}
