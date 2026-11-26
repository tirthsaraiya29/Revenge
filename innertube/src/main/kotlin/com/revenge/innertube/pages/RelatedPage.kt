package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.ArtistItem
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.MusicTwoRowItemRenderer
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.metadata.cleaner.ApiText

data class RelatedPage(
  val songs: List<SongItem>,
  val albums: List<AlbumItem>,
  val artists: List<ArtistItem>,
  val playlists: List<PlaylistItem>,
) {
  companion object {
    fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
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
          } ?: return null,
        album =
          renderer.flexColumns
            .getOrNull(2)
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.firstOrNull()
            ?.let {
              Album(
                ApiText.clean(it.text),
                it.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
              )
            },
        duration = null,
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
                ?.watchPlaylistEndpoint
                ?.playlistId ?: return null,
            title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
            artists =
              renderer.subtitle?.runs?.splitBySeparator()?.getOrNull(1)?.oddElements()?.map {
                Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
              },
            year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            explicit =
              renderer.subtitleBadges?.any {
                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
              } == true,
          )
        renderer.isPlaylist ->
          PlaylistItem(
            id =
              renderer.navigationEndpoint.browseEndpoint?.browseId?.removePrefix("VL")
                ?: return null,
            title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
            author =
              Artist(
                name = ApiText.clean(renderer.subtitle?.runs?.lastOrNull()?.text ?: return null),
                id = null,
              ),
            songCountText =
              renderer.subtitle
                ?.runs
                ?.findLast {
                  it.text.any { c -> c.isDigit() } && !it.text.contains("view", ignoreCase = true)
                }
                ?.text,
            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            playEndpoint =
              renderer.thumbnailOverlay
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
                ?.watchPlaylistEndpoint,
          )
        renderer.isArtist ->
          ArtistItem(
            id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
            title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
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
