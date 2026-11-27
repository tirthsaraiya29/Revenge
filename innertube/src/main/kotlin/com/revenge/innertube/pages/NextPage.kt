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
  fun fromPlaylistPanelVideoRenderer(renderer: PlaylistPanelVideoRenderer): SongItem? {
    val byline = renderer.longBylineText?.runs?.splitBySeparator() ?: return null
    val firstGroup = byline.getOrNull(0) ?: return null

    val id = renderer.videoId ?: return null
    val title = renderer.title?.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null

    val artists =
      firstGroup.oddElements().map {
        Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
      }

    val albumRun = byline.getOrNull(1)?.firstOrNull()
    val albumId = albumRun?.navigationEndpoint?.browseEndpoint?.browseId
    val album =
      if (albumRun != null && albumId != null) {
        Album(ApiText.clean(albumRun.text), albumId)
      } else {
        null
      }

    val duration = renderer.lengthText?.runs?.firstOrNull()?.text?.parseTime() ?: return null
    val thumbnail = renderer.thumbnail.thumbnails.lastOrNull()?.url ?: return null
    val tokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

    return SongItem(
      id = id,
      title = title,
      artists = artists,
      album = album,
      duration = duration,
      musicVideoType = renderer.navigationEndpoint.musicVideoType,
      thumbnail = thumbnail,
      explicit = renderer.badges.hasExplicitBadge(),
      libraryAddToken = tokens.addToken,
      libraryRemoveToken = tokens.removeToken,
    )
  }
}
