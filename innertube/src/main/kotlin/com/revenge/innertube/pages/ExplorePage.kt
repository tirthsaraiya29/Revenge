package com.revenge.innertube.pages

import com.revenge.innertube.models.AlbumItem

data class ExplorePage(
  val newReleaseAlbums: List<AlbumItem>,
  val moodAndGenres: List<MoodAndGenres.Item>,
)
