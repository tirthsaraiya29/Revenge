package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.ArtistItem
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.MusicTwoRowItemRenderer
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.Run
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.utils.parseTime
import com.revenge.metadata.cleaner.ApiText

data class LibraryPage(
  val items: List<YTItem>,
  val continuation: String?,
) {
  companion object {
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
            artists = parseArtists(renderer.subtitle?.runs),
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
              renderer.subtitle?.runs?.firstOrNull()?.let {
                Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
              },
            songCountText = renderer.subtitle?.runs?.lastOrNull()?.text,
            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            playEndpoint =
              renderer.thumbnailOverlay
                ?.musicItemThumbnailOverlayRenderer
                ?.content
                ?.musicPlayButtonRenderer
                ?.playNavigationEndpoint
                ?.watchPlaylistEndpoint,
            shuffleEndpoint = menuWatchPlaylist(renderer, "MUSIC_SHUFFLE"),
            radioEndpoint = menuWatchPlaylist(renderer, "MIX"),
            isEditable =
              renderer.menu?.menuRenderer?.items?.any {
                it.menuNavigationItemRenderer?.icon?.iconType == "EDIT"
              } == true,
          )
        renderer.isArtist ->
          ArtistItem(
            id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
            title = renderer.title.runs?.lastOrNull()?.text?.let(ApiText::clean) ?: return null,
            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            shuffleEndpoint = menuWatchPlaylist(renderer, "MUSIC_SHUFFLE") ?: return null,
            radioEndpoint = menuWatchPlaylist(renderer, "MIX") ?: return null,
          )
        else -> null
      }

    fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): YTItem? {
      val tokens = PageHelper.extractLibraryTokensFromMenuItems(renderer.menu?.menuRenderer?.items)

      return when {
        renderer.isSong -> {
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

          val title =
            renderer.flexColumns
              .firstOrNull()
              ?.musicResponsiveListItemFlexColumnRenderer
              ?.text
              ?.runs
              ?.firstOrNull()
              ?.text ?: return null

          // Uploaded tracks can lack artist/album browse ids; keep the names with an empty id.
          val artists =
            renderer.flexColumns
              .getOrNull(1)
              ?.musicResponsiveListItemFlexColumnRenderer
              ?.text
              ?.runs
              ?.oddElements()
              ?.map { Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId.orEmpty()) }
              .orEmpty()
          val album =
            renderer.flexColumns
              .getOrNull(2)
              ?.musicResponsiveListItemFlexColumnRenderer
              ?.text
              ?.runs
              ?.firstOrNull()
              ?.let {
                Album(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId.orEmpty())
              }
          val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null

          SongItem(
            id = videoId,
            title = title,
            artists = artists,
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
            thumbnail = thumbnail,
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
            shuffleEndpoint = menuWatchPlaylist(renderer, "MUSIC_SHUFFLE"),
            radioEndpoint = menuWatchPlaylist(renderer, "MIX"),
          )
        else -> null
      }
    }

    private fun menuWatchPlaylist(
      renderer: MusicTwoRowItemRenderer,
      iconType: String,
    ) =
      renderer.menu
        ?.menuRenderer
        ?.items
        ?.find { it.menuNavigationItemRenderer?.icon?.iconType == iconType }
        ?.menuNavigationItemRenderer
        ?.navigationEndpoint
        ?.watchPlaylistEndpoint

    private fun menuWatchPlaylist(
      renderer: MusicResponsiveListItemRenderer,
      iconType: String,
    ) =
      renderer.menu
        ?.menuRenderer
        ?.items
        ?.find { it.menuNavigationItemRenderer?.icon?.iconType == iconType }
        ?.menuNavigationItemRenderer
        ?.navigationEndpoint
        ?.watchPlaylistEndpoint

    private fun parseArtists(runs: List<Run>?): List<Artist> =
      runs.orEmpty().mapNotNull { run ->
        run.navigationEndpoint?.browseEndpoint?.browseId?.let {
          Artist(id = it, name = ApiText.clean(run.text))
        }
      }
  }
}
