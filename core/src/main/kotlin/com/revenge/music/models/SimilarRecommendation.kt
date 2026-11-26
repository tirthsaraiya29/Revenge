package com.revenge.music.models

import com.revenge.innertube.models.YTItem
import com.revenge.music.db.entities.LocalItem

data class SimilarRecommendation(val title: LocalItem, val items: List<YTItem>)
