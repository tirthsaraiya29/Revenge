package com.revenge.core.models

import com.revenge.innertube.models.YTItem

data class ItemsPage(
  val items: List<YTItem>,
  val continuation: String?,
)
