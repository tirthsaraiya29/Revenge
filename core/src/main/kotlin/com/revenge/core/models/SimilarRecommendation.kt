package com.revenge.core.models

import com.revenge.core.db.entities.LocalItem
import com.revenge.innertube.models.YTItem

data class SimilarRecommendation(
  val title: LocalItem,
  val items: List<YTItem>,
)
