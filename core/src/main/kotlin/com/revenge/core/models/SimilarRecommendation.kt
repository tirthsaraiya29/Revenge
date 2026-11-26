package com.revenge.core.models

import com.revenge.innertube.models.YTItem
import com.revenge.core.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
