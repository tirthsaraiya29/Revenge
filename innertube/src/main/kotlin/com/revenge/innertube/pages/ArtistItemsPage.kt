package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.MusicTwoRowItemRenderer
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.innertube.utils.parseTime
import com.revenge.metadata.cleaner.ApiText

data class ArtistItemsPage(
  val title: String,
  val items: List<YTItem>,
  val continuation: String?,
) {
  companion object {
    fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
      val secondary =
        renderer.flexColumns
          .getOrNull(1)
          ?.musicResponsiveListItemFlexColumnRenderer
          ?.text
          ?.runs
          ?.splitBySeparator()
      val artists =
        secondary?.firstOrNull()?.oddElements()?.map {
          Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
        }
      val album =
        renderer.flexColumns
          .lastOrNull()
          ?.musicResponsiveListItemFlexColumnRenderer
          ?.text
          ?.runs
          ?.firstOrNull()
          ?.let {
            val browseId = it.navigationEndpoint?.browseEndpoint?.browseId
            if (browseId != null) Album(ApiText.clean(it.text), browseId) else null
          }
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
          renderer.flexColumns
            .firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.firstOrNull()
            ?.text
            ?.let(ApiText::clean) ?: return null,
        artists = artists ?: return null,
        album = album,
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
      )
    }

    fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? =
      when {
        renderer.isAlbum ->
          AlbumItem(
            browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
            playlistId =
              renderer.thumbnailOverlay
                ?.musicItemThumbnailOverlayRenderer
                ?.content
                ?.musicPlayButtonRenderer
                ?.playNavigationEndpoint
                ?.anyWatchEndpoint
                ?.playlistId ?: return null,
            title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
            artists = null,
            year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            explicit =
              renderer.subtitleBadges?.any {
                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
              } == true,
          )
        renderer.isSong ->
          SongItem(
            id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
            title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
            artists =
              renderer.subtitle?.runs?.splitBySeparator()?.firstOrNull()?.oddElements()?.map {
                Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
              } ?: return null,
            album = null,
            duration = null,
            musicVideoType = renderer.musicVideoType,
            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            endpoint = renderer.navigationEndpoint.watchEndpoint,
          )
        renderer.isPlaylist ->
          PlaylistItem(
            id =
              renderer.navigationEndpoint.browseEndpoint?.browseId?.removePrefix("VL")
                ?: return null,
            title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
            author =
              renderer.subtitle?.runs?.firstOrNull()?.let {
                Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
              },
            songCountText = renderer.subtitle?.runs?.getOrNull(4)?.text,
            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            playEndpoint =
              renderer.thumbnailOverlay
                ?.musicItemThumbnailOverlayRenderer
                ?.content
                ?.musicPlayButtonRenderer
                ?.playNavigationEndpoint
                ?.watchPlaylistEndpoint ?: return null,
            shuffleEndpoint = renderer.watchPlaylistFor("MUSIC_SHUFFLE") ?: return null,
            radioEndpoint = renderer.watchPlaylistFor("MIX") ?: return null,
          )
        else -> null
      }

    private fun MusicTwoRowItemRenderer.watchPlaylistFor(iconType: String) =
      menu
        ?.menuRenderer
        ?.items
        ?.find { it.menuNavigationItemRenderer?.icon?.iconType == iconType }
        ?.menuNavigationItemRenderer
        ?.navigationEndpoint
        ?.watchPlaylistEndpoint
  }
}
