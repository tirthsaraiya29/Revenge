package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.ArtistItem
import com.revenge.innertube.models.BrowseEndpoint
import com.revenge.innertube.models.MusicCarouselShelfRenderer
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.MusicShelfRenderer
import com.revenge.innertube.models.MusicTwoRowItemRenderer
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.Run
import com.revenge.innertube.models.SectionListRenderer
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.getItems
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.metadata.cleaner.ApiText

data class ArtistSection(
  val title: String,
  val items: List<YTItem>,
  val moreEndpoint: BrowseEndpoint?,
)

data class ArtistPage(
  val artist: ArtistItem,
  val sections: List<ArtistSection>,
  val description: String?,
  val subscriberCountText: String?,
  val monthlyListenerCount: String? = null,
  val descriptionRuns: List<Run>? = null,
) {
  companion object {
    fun fromSectionListRendererContent(content: SectionListRenderer.Content): ArtistSection? =
      when {
        content.musicShelfRenderer != null -> fromMusicShelfRenderer(content.musicShelfRenderer)
        content.musicCarouselShelfRenderer != null ->
          fromMusicCarouselShelfRenderer(content.musicCarouselShelfRenderer)
        else -> null
      }

    private fun fromMusicShelfRenderer(renderer: MusicShelfRenderer): ArtistSection? =
      ArtistSection(
        title = renderer.title?.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: "",
        items =
          renderer.contents
            ?.getItems()
            ?.mapNotNull { fromMusicResponsiveListItemRenderer(it) }
            ?.ifEmpty { null } ?: return null,
        moreEndpoint = renderer.title?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint,
      )

    private fun fromMusicCarouselShelfRenderer(
      renderer: MusicCarouselShelfRenderer,
    ): ArtistSection? {
      val header = renderer.header?.musicCarouselShelfBasicHeaderRenderer ?: return null
      return ArtistSection(
        title = header.title.runs?.firstOrNull()?.text ?: return null,
        items =
          renderer.contents
            .mapNotNull { content ->
              content.musicTwoRowItemRenderer?.let { fromMusicTwoRowItemRenderer(it) }
                ?: content.musicResponsiveListItemRenderer?.let { fromMusicResponsiveListItemRenderer(it) }
            }
            .ifEmpty { null } ?: return null,
        moreEndpoint = header.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint,
      )
    }

    private fun fromMusicResponsiveListItemRenderer(
      renderer: MusicResponsiveListItemRenderer,
    ): SongItem? {
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
        duration = null,
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

    private fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? =
      when {
        renderer.isSong -> {
          val subtitle = renderer.subtitle?.runs?.oddElements() ?: return null
          SongItem(
            id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
            title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
            artists =
              subtitle
                .filter { it.navigationEndpoint?.browseEndpoint != null }
                .map { Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId) }
                .ifEmpty {
                  subtitle.firstOrNull()?.let { listOf(Artist(ApiText.clean(it.text), null)) }
                    ?: emptyList()
                },
            album = null,
            duration = null,
            musicVideoType = renderer.musicVideoType,
            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            explicit =
              renderer.subtitleBadges?.any {
                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
              } == true,
          )
        }
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
        renderer.isPlaylist ->
          PlaylistItem(
            id =
              renderer.navigationEndpoint.browseEndpoint?.browseId?.removePrefix("VL")
                ?: return null,
            title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
            author =
              Artist(
                name = renderer.subtitle?.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
                id = null,
              ),
            songCountText = null,
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
        renderer.isArtist ->
          ArtistItem(
            id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
            title = renderer.title.runs?.lastOrNull()?.text?.let(ApiText::clean) ?: return null,
            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            channelId =
              renderer.menu
                ?.menuRenderer
                ?.items
                ?.find { it.toggleMenuServiceItemRenderer?.defaultIcon?.iconType == "SUBSCRIBE" }
                ?.toggleMenuServiceItemRenderer
                ?.defaultServiceEndpoint
                ?.subscribeEndpoint
                ?.channelIds
                ?.firstOrNull(),
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
