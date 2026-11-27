package com.revenge.innertube.pages

import com.revenge.innertube.models.SongItem

data class PlaylistContinuationPage(
  val songs: List<SongItem>,
  val continuation: String?,
)
