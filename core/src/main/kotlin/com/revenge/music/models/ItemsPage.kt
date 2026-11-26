package com.revenge.music.models

import com.revenge.innertube.models.YTItem

/** A page of browse/search results plus the token needed to fetch the next page. */
data class ItemsPage(
  val items: List<YTItem>,
  val continuation: String?,
)
