package com.revenge.innertube.pages

import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.filterBlockedArtists
import com.revenge.innertube.models.filterDislikedSongs
import com.revenge.innertube.models.filterExplicit
import com.revenge.innertube.models.filterVideoSongs
import com.revenge.innertube.models.filterYoutubeShorts

/** A browse page split into titled sections, as returned by [com.revenge.innertube.YouTube.browse]. */
data class BrowseResult(
  val title: String?,
  val items: List<Item>,
) {
  data class Item(
    val title: String?,
    val items: List<YTItem>,
  )

  fun filterBlockedArtists() = mapSections(filter = { it.filterBlockedArtists() })

  fun filterDislikedSongs() = mapSections(filter = { it.filterDislikedSongs() })

  fun filterExplicit(enabled: Boolean = true) =
    mapSections(enabled = enabled, filter = { it.filterExplicit() })

  fun filterVideoSongs(disableVideos: Boolean = false) =
    mapSections(enabled = disableVideos, filter = { it.filterVideoSongs(true) })

  fun filterYoutubeShorts(enabled: Boolean = false) =
    mapSections(enabled = enabled, filter = { it.filterYoutubeShorts(true) })

  private fun mapSections(filter: (List<YTItem>) -> List<YTItem>, enabled: Boolean = true) =
    if (enabled) {
      copy(
        items =
          items.mapNotNull { section ->
            section.copy(items = filter(section.items)).takeIf { it.items.isNotEmpty() }
          },
      )
    } else {
      this
    }
}
