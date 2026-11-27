package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.ArtistItem
import com.revenge.innertube.models.BrowseEndpoint
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
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
    fun fromSectionListRendererContent(content: SectionListRenderer.Content): ArtistSection? {
      content.musicShelfRenderer?.let {
        return fromMusicShelfRenderer(it)
      }
      content.musicCarouselShelfRenderer?.let {
        return fromMusicCarouselShelfRenderer(it)
      }
      return null
    }

    private fun fromMusicShelfRenderer(
      renderer: com.revenge.innertube.models.MusicShelfRenderer,
    ): ArtistSection? {
      val titleRun = renderer.title?.runs?.firstOrNull()
      val title = titleRun?.text?.let(ApiText::clean) ?: ""
      val items =
        renderer.contents.orEmpty().getItems().mapNotNull(::fromMusicResponsiveListItemRenderer)
      if (items.isEmpty()) return null
      return ArtistSection(title, items, titleRun?.navigationEndpoint?.browseEndpoint)
    }

    private fun fromMusicCarouselShelfRenderer(
      renderer: com.revenge.innertube.models.MusicCarouselShelfRenderer,
    ): ArtistSection? {
      val header = renderer.header?.musicCarouselShelfBasicHeaderRenderer ?: return null
      val titleRun = header.title.runs?.firstOrNull() ?: return null
      val items =
        renderer.contents.mapNotNull { content ->
          content.musicTwoRowItemRenderer?.let(::fromMusicTwoRowItemRenderer)
            ?: content.musicResponsiveListItemRenderer?.let(::fromMusicResponsiveListItemRenderer)
        }
      if (items.isEmpty()) return null
      return ArtistSection(
        title = ApiText.clean(titleRun.text),
        items = items,
        moreEndpoint = header.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint,
      )
    }

    fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
      val videoId = renderer.findVideoId() ?: return null
      val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null

      val artistRuns = renderer.runsAt(1).splitBySeparator().firstOrNull()?.oddElements().orEmpty()
      if (artistRuns.isEmpty()) return null
      val artists = artistRuns.map {
        Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
      }

      val albumRun =
        renderer.flexColumns
          .lastOrNull()
          ?.musicResponsiveListItemFlexColumnRenderer
          ?.text
          ?.runs
          ?.firstOrNull()
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
        duration = null,
        musicVideoType = renderer.musicVideoType,
        thumbnail = thumbnail,
        explicit = renderer.badges.hasExplicitBadge(),
        libraryAddToken = tokens.addToken,
        libraryRemoveToken = tokens.removeToken,
      )
    }

    fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
      val browse = renderer.navigationEndpoint.browseEndpoint
      return when {
        renderer.isSong -> song(renderer)
        browse?.isAlbumEndpoint == true -> album(renderer, browse)
        browse?.isPlaylistEndpoint == true -> playlist(renderer, browse)
        browse?.isArtistEndpoint == true -> artist(renderer, browse)
        else -> null
      }
    }

    private fun song(renderer: MusicTwoRowItemRenderer): SongItem? {
      val watch = renderer.navigationEndpoint.anyWatchEndpoint ?: return null
      val id = watch.videoId ?: return null
      val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null
      val subtitleRuns = renderer.subtitle?.runs.orEmpty()
      val linkedRuns =
        subtitleRuns.oddElements().filter {
          it.navigationEndpoint?.browseEndpoint?.browseId != null
        }
      val artists =
        if (linkedRuns.isNotEmpty()) {
          linkedRuns.map {
            Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
          }
        } else {
          listOfNotNull(subtitleRuns.firstOrNull()?.let { Artist(ApiText.clean(it.text), null) })
        }
      val thumbnail = renderer.thumbnailRenderer.thumbnailUrl() ?: return null
      return SongItem(
        id = id,
        title = title,
        artists = artists,
        thumbnail = thumbnail,
        explicit = renderer.subtitleBadges.hasExplicitBadge(),
        musicVideoType = renderer.musicVideoType,
        endpoint = watch,
      )
    }

    private fun album(renderer: MusicTwoRowItemRenderer, browse: BrowseEndpoint): AlbumItem? {
      val playlistId =
        renderer.thumbnailOverlay?.playEndpoint()?.watchPlaylistEndpoint?.playlistId ?: return null
      val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null
      val year = renderer.subtitle?.runs?.lastOrNull()?.text?.trim()?.toIntOrNull()
      val thumbnail = renderer.thumbnailRenderer.thumbnailUrl() ?: return null
      return AlbumItem(
        browseId = browse.browseId,
        playlistId = playlistId,
        title = title,
        artists = null,
        year = year,
        thumbnail = thumbnail,
        explicit = renderer.subtitleBadges.hasExplicitBadge(),
      )
    }

    private fun playlist(renderer: MusicTwoRowItemRenderer, browse: BrowseEndpoint): PlaylistItem? {
      val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null
      val author =
        renderer.subtitle?.runs?.firstOrNull()?.let { Artist(ApiText.clean(it.text), null) }
      val playEndpoint = renderer.thumbnailOverlay?.playEndpoint()?.watchEndpoint ?: return null
      val shuffleEndpoint = renderer.menu.findMenuWatchEndpoint("MUSIC_SHUFFLE") ?: return null
      val radioEndpoint = renderer.menu.findMenuWatchEndpoint("MIX") ?: return null
      return PlaylistItem(
        id = browse.browseId.removePrefix("VL"),
        title = title,
        author = author,
        songCountText = null,
        thumbnail = renderer.thumbnailRenderer.thumbnailUrl(),
        playEndpoint = playEndpoint,
        shuffleEndpoint = shuffleEndpoint,
        radioEndpoint = radioEndpoint,
      )
    }

    private fun artist(renderer: MusicTwoRowItemRenderer, browse: BrowseEndpoint): ArtistItem? {
      val title = renderer.title.runs?.lastOrNull()?.text?.let(ApiText::clean) ?: return null
      val channelId =
        renderer.menu
          ?.menuRenderer
          ?.items
          .orEmpty()
          .firstNotNullOfOrNull { it.toggleMenuServiceItemRenderer }
          ?.defaultServiceEndpoint
          ?.subscribeEndpoint
          ?.channelIds
          ?.firstOrNull()
      val shuffleEndpoint = renderer.menu.findMenuWatchEndpoint("MUSIC_SHUFFLE") ?: return null
      val radioEndpoint = renderer.menu.findMenuWatchEndpoint("MIX") ?: return null
      return ArtistItem(
        id = browse.browseId,
        title = title,
        thumbnail = renderer.thumbnailRenderer.thumbnailUrl(),
        channelId = channelId,
        shuffleEndpoint = shuffleEndpoint,
        radioEndpoint = radioEndpoint,
      )
    }
  }
}
