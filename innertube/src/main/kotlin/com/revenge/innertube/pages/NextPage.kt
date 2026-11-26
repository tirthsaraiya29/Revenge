package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.BrowseEndpoint
import com.revenge.innertube.models.PlaylistPanelVideoRenderer
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.innertube.utils.parseTime
import com.revenge.metadata.cleaner.ApiText

data class NextResult(
  val title: String? = null,
  val items: List<SongItem>,
  val currentIndex: Int? = null,
  val lyricsEndpoint: BrowseEndpoint? = null,
  val relatedEndpoint: BrowseEndpoint? = null,
  val continuation: String?,
  val endpoint: WatchEndpoint,
)

object NextPage {
  /** The watch queue the player reads; also where library tokens and setVideoIds come from. */
  fun fromPlaylistPanelVideoRenderer(renderer: PlaylistPanelVideoRenderer): SongItem? {
    val byline = renderer.longBylineText?.runs?.splitBySeparator() ?: return null
    val tokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

    return SongItem(
      id = renderer.videoId ?: return null,
      title = renderer.title?.runs?.firstOrNull()?.text ?: return null,
      artists =
        byline.firstOrNull()?.oddElements()?.map {
          Artist(name = ApiText.clean(it.text), id = it.navigationEndpoint?.browseEndpoint?.browseId)
        } ?: return null,
      album =
        byline
          .getOrNull(1)
          ?.firstOrNull()
          ?.takeIf { it.navigationEndpoint?.browseEndpoint != null }
          ?.let { Album(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId!!) },
      duration = renderer.lengthText?.runs?.firstOrNull()?.text?.parseTime() ?: return null,
      musicVideoType = renderer.navigationEndpoint.musicVideoType,
      thumbnail = renderer.thumbnail.thumbnails.lastOrNull()?.url ?: return null,
      explicit =
        renderer.badges?.any {
          it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
        } == true,
      libraryAddToken = tokens.addToken,
      libraryRemoveToken = tokens.removeToken,
    )
  }
}
