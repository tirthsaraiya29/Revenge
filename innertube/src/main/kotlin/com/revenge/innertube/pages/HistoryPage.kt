package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.MusicShelfRenderer
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.getItems
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.metadata.cleaner.ApiText

data class HistoryPage(
  val sections: List<HistorySection>?,
) {
  data class HistorySection(
    val title: String,
    val songs: List<SongItem>,
  )

  companion object {
    fun fromMusicShelfRenderer(renderer: MusicShelfRenderer): HistorySection {
      val title =
        renderer.title?.runs?.firstOrNull()?.text?.let(ApiText::clean)
          ?: throw IllegalStateException("History shelf has no title")
      val contents =
        renderer.contents ?: throw IllegalStateException("History shelf has no contents")
      val songs = contents.getItems().mapNotNull(::fromMusicResponsiveListItemRenderer)
      return HistorySection(title, songs)
    }

    private fun fromMusicResponsiveListItemRenderer(
      renderer: MusicResponsiveListItemRenderer,
    ): SongItem? {
      val videoId = renderer.findVideoId() ?: return null
      val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null

      val artistRuns = renderer.runsAt(1).splitBySeparator().firstOrNull()?.oddElements().orEmpty()
      val artists = artistRuns.map {
        Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
      }

      val albumRun = renderer.runsAt(3).firstOrNull()
      val album =
        albumRun?.navigationEndpoint?.browseEndpoint?.browseId?.let {
          Album(ApiText.clean(albumRun.text), it)
        }

      val thumbnail = renderer.thumbnail?.thumbnailUrl() ?: return null
      val tokens = renderer.libraryTokens()

      return SongItem(
        id = videoId,
        title = title,
        artists = artists,
        album = album,
        duration = renderer.durationSeconds(),
        musicVideoType = renderer.musicVideoType,
        thumbnail = thumbnail,
        explicit = renderer.badges.hasExplicitBadge(),
        libraryAddToken = tokens.addToken,
        libraryRemoveToken = tokens.removeToken,
        historyRemoveToken = renderer.menu.historyRemoveToken(),
      )
    }
  }
}
