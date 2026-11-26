package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.MusicShelfRenderer
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.getItems
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.innertube.utils.parseTime
import com.revenge.metadata.cleaner.ApiText

data class HistoryPage(
  val sections: List<HistorySection>?,
) {
  data class HistorySection(val title: String, val songs: List<SongItem>)

  companion object {
    fun fromMusicShelfRenderer(renderer: MusicShelfRenderer): HistorySection =
      HistorySection(
        title = renderer.title?.runs?.firstOrNull()?.text?.let(ApiText::clean)!!,
        songs =
          renderer.contents?.getItems()?.mapNotNull { fromMusicResponsiveListItemRenderer(it) }!!,
      )

    private fun fromMusicResponsiveListItemRenderer(
      renderer: MusicResponsiveListItemRenderer,
    ): SongItem? {
      val tokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)
      val secondary =
        renderer.flexColumns
          .getOrNull(1)
          ?.musicResponsiveListItemFlexColumnRenderer
          ?.text
          ?.runs
          ?.splitBySeparator()

      val videoId =
        renderer.playlistItemData?.videoId
          ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
          ?: renderer.overlay
            ?.musicItemThumbnailOverlayRenderer
            ?.content
            ?.musicPlayButtonRenderer
            ?.playNavigationEndpoint
            ?.watchEndpoint
            ?.videoId
          ?: renderer.flexColumns
            .firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.firstOrNull()
            ?.navigationEndpoint
            ?.watchEndpoint
            ?.videoId
          ?: return null

      return SongItem(
        id = videoId,
        title =
          renderer.flexColumns
            .firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.firstOrNull()
            ?.text
            ?.let(ApiText::clean) ?: return null,
        artists =
          secondary?.firstOrNull()?.oddElements()?.map {
            Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
          } ?: emptyList(),
        album =
          renderer.flexColumns
            .getOrNull(3)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.firstOrNull()
            ?.let {
              Album(
                ApiText.clean(it.text),
                it.navigationEndpoint?.browseEndpoint?.browseId ?: return@let null,
              )
            },
        duration =
          renderer.fixedColumns
            ?.firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.firstOrNull()
            ?.text
            ?.parseTime(),
        musicVideoType = renderer.musicVideoType,
        thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
        explicit =
          renderer.badges?.any {
            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
          } == true,
        endpoint =
          renderer.overlay
            ?.musicItemThumbnailOverlayRenderer
            ?.content
            ?.musicPlayButtonRenderer
            ?.playNavigationEndpoint
            ?.watchEndpoint,
        libraryAddToken = tokens.addToken,
        libraryRemoveToken = tokens.removeToken,
        historyRemoveToken =
          renderer.menu
            ?.menuRenderer
            ?.items
            ?.find { it.menuServiceItemRenderer?.icon?.iconType == "REMOVE_FROM_HISTORY" }
            ?.menuServiceItemRenderer
            ?.serviceEndpoint
            ?.feedbackEndpoint
            ?.feedbackToken,
      )
    }
  }
}
