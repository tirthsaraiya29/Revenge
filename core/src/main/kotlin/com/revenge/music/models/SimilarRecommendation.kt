package com.revenge.music.models

import com.revenge.innertube.models.YTItem
import com.revenge.music.db.entities.LocalItem

/** A shelf of recommended items tied to the local item it was derived from. */
data class SimilarRecommendation(
  val title: LocalItem,
  val items: List<YTItem>,
)
