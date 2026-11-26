package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.ArtistItem
import com.revenge.innertube.models.BrowseEndpoint
import com.revenge.innertube.models.MusicCarouselShelfRenderer
import com.revenge.innertube.models.MusicTwoRowItemRenderer
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.SectionListRenderer
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.filterBlockedArtists
import com.revenge.innertube.models.filterDislikedSongs
import com.revenge.innertube.models.filterExplicit
import com.revenge.innertube.models.filterVideoSongs
import com.revenge.innertube.models.oddElements
import com.revenge.metadata.cleaner.ApiText

data class HomePage(
  val chips: List<Chip>?,
  val sections: List<Section>,
  val continuation: String? = null,
) {
  data class Chip(
    val title: String,
    val endpoint: BrowseEndpoint?,
    val deselectEndPoint: BrowseEndpoint?,
  ) {
    companion object {
      fun fromChipCloudChipRenderer(
        renderer: SectionListRenderer.Header.ChipCloudRenderer.Chip,
      ): Chip? =
        Chip(
          title = renderer.chipCloudChipRenderer.text?.runs?.firstOrNull()?.text ?: return null,
          endpoint = renderer.chipCloudChipRenderer.navigationEndpoint.browseEndpoint,
          deselectEndPoint = renderer.chipCloudChipRenderer.onDeselectedCommand?.browseEndpoint,
        )
    }
  }

  data class Section(
    val title: String,
    val label: String?,
    val thumbnail: String?,
    val endpoint: BrowseEndpoint?,
    val items: List<YTItem>,
    /** The shuffle card leading a shelf, when YouTube ships one. */
    val shuffle: Shuffle? = null,
    /** Every item is a live stream, as with the "Listen together" stations. */
    val liveOnly: Boolean = false,
  ) {
    val isStationListenTogether: Boolean
      get() {
        if (liveOnly) return true
        val upperTitle = title.uppercase()
        val upperLabel = label?.uppercase().orEmpty()
        return upperTitle.contains("LISTEN TOGETHER") ||
          (upperLabel.contains("STATION") && upperTitle.contains("LISTEN TOGETHER")) ||
          (upperLabel.contains("LISTEN TOGETHER") && upperTitle.contains("STATION"))
      }

    data class Shuffle(val title: String, val thumbnail: String?)

    companion object {
      fun fromMusicCarouselShelfRenderer(renderer: MusicCarouselShelfRenderer): Section? {
        val header = renderer.header?.musicCarouselShelfBasicHeaderRenderer ?: return null
        return Section(
          title = header.title.runs?.firstOrNull()?.text ?: return null,
          label = header.strapline?.runs?.firstOrNull()?.text,
          thumbnail =
            header.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
              ?: renderer.contents.firstNotNullOfOrNull { it.musicShuffleRenderer?.getThumbnailUrl() }
              ?: renderer.contents
                .mapNotNull { it.musicTwoRowItemRenderer }
                .mapNotNull { itemThumbnailUrl(it) }
                .firstOrNull()
              ?: renderer.contents.firstNotNullOfOrNull {
                it.musicMultiRowListItemRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
              },
          endpoint = header.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint,
          items =
            renderer.contents
              .mapNotNull { content ->
                content.musicTwoRowItemRenderer?.let { fromMusicTwoRowItemRenderer(it) }
                  ?: content.musicMultiRowListItemRenderer?.let { PodcastPage.episode(it) }
                  ?: content.musicResponsiveListItemRenderer?.let {
                    SearchSummaryPage.fromMusicResponsiveListItemRenderer(it)
                  }
              }
              .ifEmpty { return null },
          liveOnly =
            renderer.contents
              .filter { it.musicShuffleRenderer == null }
              .let { cards ->
                cards.isNotEmpty() &&
                  cards.all { content ->
                    val card = content.musicTwoRowItemRenderer ?: return@all false
                    card.navigationEndpoint.watchEndpoint?.videoId != null &&
                      card.musicVideoType != "MUSIC_VIDEO_TYPE_PODCAST_EPISODE" &&
                      card.subtitleBadges?.any { it.musicInlineBadgeRenderer == null } == true
                  }
              },
          shuffle =
            renderer.contents.firstNotNullOfOrNull { content ->
              content.musicShuffleRenderer?.let { shuffle ->
                Shuffle(
                  title = shuffle.title?.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: "Shuffle",
                  thumbnail = shuffle.getThumbnailUrl(),
                )
              }
            },
        )
      }

      private fun itemThumbnailUrl(renderer: MusicTwoRowItemRenderer): String? =
        renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
          ?: renderer.thumbnailRenderer.croppedSquareThumbnailRenderer?.getThumbnailUrl()

      private fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
        PodcastPage.show(renderer)?.let { return it }
        PodcastPage.episode(renderer)?.let { return it }

        return when {
          renderer.isSong -> {
            val subtitle = renderer.subtitle?.runs?.oddElements() ?: return null
            SongItem(
              id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
              title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
              artists =
                subtitle
                  .filter { run ->
                    val browseId = run.navigationEndpoint?.browseEndpoint?.browseId
                    browseId?.startsWith("UC") == true ||
                      (run.navigationEndpoint?.browseEndpoint != null &&
                        browseId?.startsWith("MPREb_") != true)
                  }
                  .map { run ->
                    Artist(ApiText.clean(run.text), run.navigationEndpoint?.browseEndpoint?.browseId)
                  }
                  .ifEmpty {
                    subtitle.firstOrNull()?.let { run -> listOf(Artist(ApiText.clean(run.text), null)) }
                      ?: emptyList()
                  },
              album =
                subtitle
                  .firstOrNull {
                    it.navigationEndpoint?.browseEndpoint?.browseId?.startsWith("MPREb_") == true
                  }
                  ?.let {
                    Album(
                      ApiText.clean(it.text),
                      it.navigationEndpoint?.browseEndpoint?.browseId ?: return@let null,
                    )
                  },
              duration = null,
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
                  ?.watchPlaylistEndpoint
                  ?.playlistId ?: return null,
              title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
              artists =
                renderer.subtitle?.runs?.oddElements()?.drop(1)?.map {
                  Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
                },
              year = null,
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
                  name =
                    renderer.subtitle?.runs?.firstOrNull()?.text?.let(ApiText::clean)
                      ?: return null,
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
              radioEndpoint = renderer.watchPlaylistFor("MIX"),
            )
          renderer.isArtist ->
            ArtistItem(
              id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
              title = renderer.title.runs?.lastOrNull()?.text?.let(ApiText::clean) ?: return null,
              thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
              shuffleEndpoint = renderer.watchPlaylistFor("MUSIC_SHUFFLE") ?: return null,
              radioEndpoint = renderer.watchPlaylistFor("MIX") ?: return null,
            )
          else -> null
        }
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

  fun filterBlockedArtists() =
    copy(sections = sections.map { it.copy(items = it.items.filterBlockedArtists()) })

  fun filterDislikedSongs() =
    copy(sections = sections.map { it.copy(items = it.items.filterDislikedSongs()) })

  fun filterExplicit(enabled: Boolean = true) =
    if (enabled) copy(sections = sections.map { it.copy(items = it.items.filterExplicit()) }) else this

  fun filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
      copy(
        sections = sections.map { section ->
          section.copy(items = section.items.filterVideoSongs(true))
        },
      )
    } else {
      this
    }
}
