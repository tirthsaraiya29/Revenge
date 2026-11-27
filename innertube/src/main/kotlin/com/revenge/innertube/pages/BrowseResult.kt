package com.revenge.innertube.pages

import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.filterBlockedArtists
import com.revenge.innertube.models.filterDislikedSongs
import com.revenge.innertube.models.filterExplicit
import com.revenge.innertube.models.filterVideoSongs
import com.revenge.innertube.models.filterYoutubeShorts

data class BrowseResult(
  val title: String?,
  val items: List<Item>,
) {
  data class Item(
    val title: String?,
    val items: List<YTItem>,
  )

  fun filterBlockedArtists(): BrowseResult =
    copy(items = items.map { it.copy(items = it.items.filterBlockedArtists()) })

  fun filterDislikedSongs(): BrowseResult =
    copy(items = items.map { it.copy(items = it.items.filterDislikedSongs()) })

  fun filterExplicit(enabled: Boolean = true): BrowseResult {
    if (!enabled) return this
    return copy(
      items =
        items.mapNotNull { section ->
          val filtered = section.items.filterExplicit(true)
          if (filtered.isEmpty()) null else section.copy(items = filtered)
        },
    )
  }

  fun filterVideoSongs(disableVideos: Boolean = false): BrowseResult {
    if (!disableVideos) return this
    return copy(
      items =
        items.mapNotNull { section ->
          val filtered = section.items.filterVideoSongs(true)
          if (filtered.isEmpty()) null else section.copy(items = filtered)
        },
    )
  }

  fun filterYoutubeShorts(enabled: Boolean = false): BrowseResult {
    if (!enabled) return this
    return copy(
      items =
        items.mapNotNull { section ->
          val filtered = section.items.filterYoutubeShorts(true)
          if (filtered.isEmpty()) null else section.copy(items = filtered)
        },
    )
  }
}
