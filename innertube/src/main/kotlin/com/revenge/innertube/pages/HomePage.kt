package com.revenge.innertube.pages

import com.revenge.innertube.models.BrowseEndpoint
import com.revenge.innertube.models.MusicCarouselShelfRenderer
import com.revenge.innertube.models.SectionListRenderer
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.filterBlockedArtists
import com.revenge.innertube.models.filterDislikedSongs
import com.revenge.innertube.models.filterExplicit
import com.revenge.innertube.models.filterVideoSongs
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
      ): Chip? {
        val title = renderer.chipCloudChipRenderer.text?.runs?.firstOrNull()?.text ?: return null
        return Chip(
          title = title,
          endpoint = renderer.chipCloudChipRenderer.navigationEndpoint.browseEndpoint,
          deselectEndPoint = renderer.chipCloudChipRenderer.onDeselectedCommand?.browseEndpoint,
        )
      }
    }
  }

  data class Section(
    val title: String,
    val label: String?,
    val thumbnail: String?,
    val endpoint: BrowseEndpoint?,
    val items: List<YTItem>,
    val shuffle: Shuffle? = null,
    val liveOnly: Boolean = false,
  ) {
    val isStationListenTogether: Boolean
      get() {
        if (liveOnly) return true
        val upperTitle = title.uppercase()
        val upperLabel = label?.uppercase() ?: ""
        return upperTitle.contains("LISTEN TOGETHER") ||
          (upperLabel.contains("STATION") && upperTitle.contains("LISTEN TOGETHER")) ||
          (upperLabel.contains("LISTEN TOGETHER") && upperTitle.contains("STATION"))
      }

    data class Shuffle(
      val title: String,
      val thumbnail: String?,
    )

    companion object {
      private const val NON_MUSIC_AUDIO_TRACK_PAGE = "MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE"
      private const val PODCAST_EPISODE_TYPE = "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"

      fun fromMusicCarouselShelfRenderer(renderer: MusicCarouselShelfRenderer): Section? {
        val header = renderer.header?.musicCarouselShelfBasicHeaderRenderer ?: return null
        val titleRun = header.title.runs?.firstOrNull() ?: return null
        val title = ApiText.clean(titleRun.text).takeIf { it.isNotBlank() } ?: return null

        val label = header.strapline?.runs?.firstOrNull()?.text?.let(ApiText::clean)

        val thumbnail =
          header.thumbnail?.thumbnailUrl()
            ?: renderer.contents.firstNotNullOfOrNull { it.musicShuffleRenderer?.getThumbnailUrl() }
            ?: renderer.contents.firstNotNullOfOrNull {
              it.musicTwoRowItemRenderer?.thumbnailRenderer?.thumbnailUrl()
            }
            ?: renderer.contents.firstNotNullOfOrNull {
              it.musicMultiRowListItemRenderer?.thumbnail?.thumbnailUrl()
            }

        val endpoint = header.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint

        val items =
          renderer.contents.mapNotNull { content ->
            val twoRow = content.musicTwoRowItemRenderer
            when {
              twoRow != null -> {
                val browse = twoRow.navigationEndpoint.browseEndpoint
                when {
                  browse?.isPodcastEndpoint == true -> PodcastPage.show(twoRow)
                  browse?.pageType == NON_MUSIC_AUDIO_TRACK_PAGE -> PodcastPage.episode(twoRow)
                  else -> ArtistPage.fromMusicTwoRowItemRenderer(twoRow)
                }
              }
              content.musicResponsiveListItemRenderer != null ->
                content.musicResponsiveListItemRenderer?.let {
                  SearchSummaryPage.fromMusicResponsiveListItemRenderer(it)
                }
              content.musicMultiRowListItemRenderer != null ->
                content.musicMultiRowListItemRenderer?.let { PodcastPage.episode(it) }
              else -> null
            }
          }
        if (items.isEmpty()) return null

        val nonShuffle = renderer.contents.filter { it.musicShuffleRenderer == null }
        val allLiveTwoRow =
          nonShuffle.isNotEmpty() &&
            nonShuffle.all { content ->
              val twoRow = content.musicTwoRowItemRenderer ?: return@all false
              twoRow.navigationEndpoint.anyWatchEndpoint?.videoId != null &&
                twoRow.navigationEndpoint.browseEndpoint?.pageType != NON_MUSIC_AUDIO_TRACK_PAGE &&
                twoRow.musicVideoType != PODCAST_EPISODE_TYPE
            }
        val hasUnlabeledBadge =
          renderer.contents.any { content ->
            content.musicTwoRowItemRenderer?.subtitleBadges?.any {
              it.musicInlineBadgeRenderer == null
            } == true
          }
        val liveOnly = allLiveTwoRow && hasUnlabeledBadge

        val shuffleRenderer = renderer.contents.firstNotNullOfOrNull { it.musicShuffleRenderer }
        val shuffle = shuffleRenderer?.let { shuffleRenderer ->
          val shuffleTitle =
            shuffleRenderer.title?.runs?.firstOrNull()?.text?.let(ApiText::clean)?.takeIf {
              it.isNotBlank()
            } ?: "Shuffle"
          Shuffle(shuffleTitle, shuffleRenderer.getThumbnailUrl())
        }

        return Section(
          title = title,
          label = label,
          thumbnail = thumbnail,
          endpoint = endpoint,
          items = items,
          shuffle = shuffle,
          liveOnly = liveOnly,
        )
      }
    }
  }

  fun filterBlockedArtists(): HomePage =
    copy(sections = sections.map { it.copy(items = it.items.filterBlockedArtists()) })

  fun filterDislikedSongs(): HomePage =
    copy(sections = sections.map { it.copy(items = it.items.filterDislikedSongs()) })

  fun filterExplicit(enabled: Boolean = true): HomePage =
    if (!enabled) this
    else copy(sections = sections.map { it.copy(items = it.items.filterExplicit(true)) })

  fun filterVideoSongs(disableVideos: Boolean = false): HomePage =
    if (!disableVideos) this
    else copy(sections = sections.map { it.copy(items = it.items.filterVideoSongs(true)) })
}
