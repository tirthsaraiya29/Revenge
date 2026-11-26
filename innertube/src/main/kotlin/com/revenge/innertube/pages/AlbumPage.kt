package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.MusicResponsiveHeaderRenderer
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.Run
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.getItems
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.response.BrowseResponse
import com.revenge.innertube.models.splitBySeparator
import com.revenge.innertube.utils.parseTime
import com.revenge.metadata.cleaner.ApiText

data class AlbumPage(
  val album: AlbumItem,
  val songs: List<SongItem>,
  val otherVersions: List<AlbumItem>,
  val releasesForYou: List<AlbumItem> = emptyList(),
  val description: String? = null,
  val descriptionRuns: List<Run>? = null,
) {
  companion object {
    fun getPlaylistId(response: BrowseResponse): String? =
      response.microformat?.microformatDataRenderer?.urlCanonical?.substringAfterLast('=')
        ?: response.header
          ?.musicDetailHeaderRenderer
          ?.menu
          ?.menuRenderer
          ?.topLevelButtons
          ?.firstOrNull()
          ?.buttonRenderer
          ?.navigationEndpoint
          ?.watchPlaylistEndpoint
          ?.playlistId

    fun getTitle(response: BrowseResponse): String? =
      (header(response)?.title ?: response.header?.musicDetailHeaderRenderer?.title)
        ?.runs
        ?.firstOrNull()
        ?.text

    fun getYear(response: BrowseResponse): Int? =
      (header(response)?.subtitle ?: response.header?.musicDetailHeaderRenderer?.subtitle)
        ?.runs
        ?.lastOrNull()
        ?.text
        ?.toIntOrNull()

    fun getThumbnail(response: BrowseResponse): String? =
      response.background?.musicThumbnailRenderer?.getThumbnailUrl()
        ?: response.header
          ?.musicDetailHeaderRenderer
          ?.thumbnail
          ?.croppedSquareThumbnailRenderer
          ?.getThumbnailUrl()

    fun getArtists(response: BrowseResponse): List<Artist> =
      header(response)?.straplineTextOne?.runs?.oddElements()?.map {
        Artist(name = ApiText.clean(it.text), id = it.navigationEndpoint?.browseEndpoint?.browseId)
      }
        ?: response.header
          ?.musicDetailHeaderRenderer
          ?.subtitle
          ?.runs
          ?.splitBySeparator()
          ?.getOrNull(1)
          ?.oddElements()
          ?.map {
            Artist(name = ApiText.clean(it.text), id = it.navigationEndpoint?.browseEndpoint?.browseId)
          }
        ?: emptyList()

    fun getSongs(response: BrowseResponse, album: AlbumItem): List<SongItem> {
      val tabs =
        response.contents?.singleColumnBrowseResultsRenderer?.tabs
          ?: response.contents?.twoColumnBrowseResultsRenderer?.tabs
      val shelf =
        tabs
          ?.firstOrNull()
          ?.tabRenderer
          ?.content
          ?.sectionListRenderer
          ?.contents
          ?.firstOrNull()
          ?.musicShelfRenderer
          ?: response.contents
            ?.twoColumnBrowseResultsRenderer
            ?.secondaryContents
            ?.sectionListRenderer
            ?.contents
            ?.firstOrNull()
            ?.musicShelfRenderer
      return shelf?.contents?.getItems()?.mapNotNull { getSong(it, album) } ?: emptyList()
    }

    /** One track row. [album] supplies the album and artists when the row leaves them out. */
    fun getSong(renderer: MusicResponsiveListItemRenderer, album: AlbumItem? = null): SongItem? {
      val tokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

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
          PageHelper.extractRuns(renderer.flexColumns, "MUSIC_VIDEO")
            .firstOrNull()
            ?.text
            ?.let(ApiText::clean) ?: return null,
        artists =
          PageHelper.extractRuns(renderer.flexColumns, "MUSIC_PAGE_TYPE_ARTIST")
            .map { Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId) }
            .ifEmpty { album?.artists ?: emptyList() },
        album =
          album?.let { Album(it.title, it.browseId) }
            ?: renderer.flexColumns
              .getOrNull(2)
              ?.musicResponsiveListItemFlexColumnRenderer
              ?.text
              ?.runs
              ?.firstOrNull()
              ?.let { Album(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId!!) }!!,
        duration =
          renderer.fixedColumns
            ?.firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.firstOrNull()
            ?.text
            ?.parseTime() ?: return null,
        musicVideoType = renderer.musicVideoType,
        thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: album?.thumbnail!!,
        explicit =
          renderer.badges?.any {
            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
          } == true,
        libraryAddToken = tokens.addToken,
        libraryRemoveToken = tokens.removeToken,
      )
    }

    private fun header(response: BrowseResponse): MusicResponsiveHeaderRenderer? {
      val tabs =
        response.contents?.singleColumnBrowseResultsRenderer?.tabs
          ?: response.contents?.twoColumnBrowseResultsRenderer?.tabs
      return tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.firstOrNull()
        ?.musicResponsiveHeaderRenderer
    }
  }
}
