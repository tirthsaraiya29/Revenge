package com.revenge.innertube.pages

import com.revenge.innertube.models.YTItem

/** Charts broken into sections (trending, top, genres, new releases). */
data class ChartsPage(val sections: List<ChartSection>, val continuation: String?) {
  data class ChartSection(val title: String, val items: List<YTItem>, val chartType: ChartType)

  enum class ChartType {
    TRENDING,
    TOP,
    GENRE,
    NEW_RELEASES,
  }
}
