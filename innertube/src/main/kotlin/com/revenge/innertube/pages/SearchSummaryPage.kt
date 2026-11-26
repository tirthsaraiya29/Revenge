package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.ArtistItem
import com.revenge.innertube.models.MusicCardShelfRenderer
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.clean
import com.revenge.innertube.models.filterBlockedArtists
import com.revenge.innertube.models.filterDislikedSongs
import com.revenge.innertube.models.filterExplicit
import com.revenge.innertube.models.filterVideoSongs
import com.revenge.innertube.models.filterYoutubeShorts
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.innertube.utils.parseTime
import com.revenge.metadata.cleaner.ApiText

data class SearchSummary(val title: String, val items: List<YTItem>)

data class SearchSummaryPage(
  val summaries: List<SearchSummary>,
  /** Every result in YouTube's ranking order, top result first and not grouped. */
  val ranked: List<YTItem> = emptyList(),
) {
  fun filterBlockedArtists() = mapSummaries(filter = { it.filterBlockedArtists() })

  fun filterDislikedSongs() = mapSummaries(filter = { it.filterDislikedSongs() })

  fun filterExplicit(enabled: Boolean) =
    mapSummaries(enabled = enabled, filter = { it.filterExplicit() })

  fun filterVideoSongs(disableVideos: Boolean) =
    if (disableVideos) {
      SearchSummaryPage(
        summaries.mapNotNull { summary ->
          if (summary.title == "Videos") summary
          else summary.copy(items = summary.items.filterVideoSongs(true)).takeIf { it.items.isNotEmpty() }
        },
      )
    } else {
      this
    }

  fun filterYoutubeShorts(enabled: Boolean = false) =
    mapSummaries({ it.filterYoutubeShorts(true) }, enabled)

  private fun mapSummaries(filter: (List<YTItem>) -> List<YTItem>, enabled: Boolean = true) =
    if (enabled) {
      SearchSummaryPage(
        summaries.mapNotNull { summary ->
          SearchSummary(summary.title, filter(summary.items)).takeIf { it.items.isNotEmpty() }
        },
      )
    } else {
      this
    }

  companion object {
    /** The large "top result" card above the shelves. */
    fun fromMusicCardShelfRenderer(renderer: MusicCardShelfRenderer): YTItem? {
      val subtitle = renderer.subtitle.runs?.splitBySeparator()
      return when {
        renderer.onTap.watchEndpoint != null ->
          SongItem(
            id = renderer.onTap.watchEndpoint.videoId ?: return null,
            title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
            artists =
              subtitle?.getOrNull(1)?.oddElements()?.map {
                Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
              } ?: return null,
            album =
              subtitle
                .getOrNull(2)
                ?.firstOrNull()
                ?.takeIf { it.navigationEndpoint?.browseEndpoint != null }
                ?.let { Album(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId!!) },
            duration = subtitle.lastOrNull()?.firstOrNull()?.text?.parseTime(),
            musicVideoType = renderer.onTap.musicVideoType,
            thumbnail = renderer.thumbnail.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            explicit =
              renderer.subtitleBadges?.any {
                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
              } == true,
          )
        renderer.onTap.browseEndpoint?.isArtistEndpoint == true ->
          ArtistItem(
            id = renderer.onTap.browseEndpoint.browseId,
            title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
            thumbnail = renderer.thumbnail.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            shuffleEndpoint =
              renderer.buttons
                .find { it.buttonRenderer.icon?.iconType == "MUSIC_SHUFFLE" }
                ?.buttonRenderer
                ?.command
                ?.watchPlaylistEndpoint ?: return null,
            radioEndpoint =
              renderer.buttons
                .find { it.buttonRenderer.icon?.iconType == "MIX" }
                ?.buttonRenderer
                ?.command
                ?.watchPlaylistEndpoint ?: return null,
          )
        renderer.onTap.browseEndpoint?.isAlbumEndpoint == true ->
          AlbumItem(
            browseId = renderer.onTap.browseEndpoint.browseId,
            playlistId =
              renderer.buttons.firstOrNull()?.buttonRenderer?.command?.anyWatchEndpoint?.playlistId
                ?: return null,
            title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
            artists =
              subtitle?.getOrNull(1)?.oddElements()?.map {
                Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
              } ?: return null,
            year = null,
            thumbnail = renderer.thumbnail.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            explicit =
              renderer.subtitleBadges?.any {
                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
              } == true,
          )
        renderer.onTap.browseEndpoint?.isPlaylistEndpoint == true ->
          PlaylistItem(
            id = renderer.onTap.browseEndpoint.browseId.removePrefix("VL"),
            title =
              renderer.header?.musicCardShelfHeaderBasicRenderer?.title?.runs?.joinToString("") {
                ApiText.clean(it.text)
              } ?: return null,
            author =
              Artist(
                id = null,
                name = renderer.subtitle.runs?.joinToString { ApiText.clean(it.text) } ?: return null,
              ),
            songCountText = null,
            thumbnail = renderer.thumbnail.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            playEndpoint =
              renderer.buttons
                .find { it.buttonRenderer.icon?.iconType == "PLAY_ARROW" }
                ?.buttonRenderer
                ?.command
                ?.watchPlaylistEndpoint ?: return null,
            shuffleEndpoint =
              renderer.buttons
                .find { it.buttonRenderer.icon?.iconType == "MUSIC_SHUFFLE" }
                ?.buttonRenderer
                ?.command
                ?.watchPlaylistEndpoint ?: return null,
            radioEndpoint = null,
          )
        else -> null
      }
    }

    fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): YTItem? {
      PodcastPage.show(renderer)?.let { return it }
      PodcastPage.episode(renderer)?.let { return it }

      val secondary =
        renderer.flexColumns
          .getOrNull(1)
          ?.musicResponsiveListItemFlexColumnRenderer
          ?.text
          ?.runs
          ?.splitBySeparator() ?: return null
      val third =
        renderer.flexColumns
          .getOrNull(2)
          ?.musicResponsiveListItemFlexColumnRenderer
          ?.text
          ?.runs
          ?.splitBySeparator() ?: emptyList()
      val listRun = (secondary + third).clean()

      return when {
        renderer.isSong -> {
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

          SongItem(
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
              listRun.getOrNull(0)?.oddElements()?.map {
                Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
              } ?: return null,
            album =
              listRun
                .getOrNull(1)
                ?.firstOrNull()
                ?.takeIf { it.navigationEndpoint?.browseEndpoint != null }
                ?.let { Album(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId!!) },
            duration = secondary.lastOrNull()?.firstOrNull()?.text?.parseTime(),
            musicVideoType = renderer.musicVideoType,
            thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            explicit =
              renderer.badges?.any {
                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
              } == true,
            libraryAddToken = tokens.addToken,
            libraryRemoveToken = tokens.removeToken,
          )
        }
        renderer.isArtist ->
          ArtistItem(
            id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
            title =
              renderer.flexColumns
                .firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                ?.firstOrNull()
                ?.text
                ?.let(ApiText::clean) ?: return null,
            thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            shuffleEndpoint =
              renderer.menu
                ?.menuRenderer
                ?.items
                ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                ?.menuNavigationItemRenderer
                ?.navigationEndpoint
                ?.watchPlaylistEndpoint ?: return null,
            radioEndpoint =
              renderer.menu
                ?.menuRenderer
                ?.items
                ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
                ?.menuNavigationItemRenderer
                ?.navigationEndpoint
                ?.watchPlaylistEndpoint ?: return null,
          )
        renderer.isAlbum ->
          AlbumItem(
            browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
            playlistId =
              renderer.overlay
                ?.musicItemThumbnailOverlayRenderer
                ?.content
                ?.musicPlayButtonRenderer
                ?.playNavigationEndpoint
                ?.watchPlaylistEndpoint
                ?.playlistId ?: return null,
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
              secondary.getOrNull(1)?.oddElements()?.map {
                Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
              } ?: return null,
            year = secondary.getOrNull(2)?.firstOrNull()?.text?.toIntOrNull(),
            thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            explicit =
              renderer.badges?.any {
                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
              } == true,
          )
        renderer.isPlaylist ->
          PlaylistItem(
            id =
              renderer.navigationEndpoint?.browseEndpoint?.browseId?.removePrefix("VL")
                ?: return null,
            title =
              renderer.flexColumns
                .firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                ?.firstOrNull()
                ?.text
                ?.let(ApiText::clean) ?: return null,
            author =
              secondary.getOrNull(1)?.firstOrNull()?.let {
                Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
              } ?: return null,
            songCountText =
              renderer.flexColumns
                .getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                ?.lastOrNull()
                ?.text ?: return null,
            thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            playEndpoint =
              renderer.overlay
                ?.musicItemThumbnailOverlayRenderer
                ?.content
                ?.musicPlayButtonRenderer
                ?.playNavigationEndpoint
                ?.watchPlaylistEndpoint ?: return null,
            shuffleEndpoint =
              renderer.menu
                ?.menuRenderer
                ?.items
                ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                ?.menuNavigationItemRenderer
                ?.navigationEndpoint
                ?.watchPlaylistEndpoint ?: return null,
            radioEndpoint =
              renderer.menu
                ?.menuRenderer
                ?.items
                ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
                ?.menuNavigationItemRenderer
                ?.navigationEndpoint
                ?.watchPlaylistEndpoint ?: return null,
          )
        else -> null
      }
    }
  }
}
