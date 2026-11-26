package com.revenge.innertube.pages

import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.MusicMultiRowListItemRenderer
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.MusicTwoRowItemRenderer
import com.revenge.innertube.models.PODCAST_BROWSE_PREFIX
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.Run
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_PODCAST_EPISODE
import com.revenge.innertube.utils.parseTime
import com.revenge.metadata.cleaner.ApiText

/**
 * YouTube Music models podcasts as playlists (`PL…`) shown through an `MPSP…` browse id, with each
 * episode a normal video. Shows therefore become [PlaylistItem]s and episodes [SongItem]s, which
 * lets the existing playlist, queue and library code handle them.
 */
object PodcastPage {
  fun showId(browseId: String): String = browseId.removePrefix(PODCAST_BROWSE_PREFIX)

  fun showBrowseId(showId: String): String = PODCAST_BROWSE_PREFIX + showId

  fun isShowBrowseId(browseId: String?): Boolean =
    browseId?.startsWith(PODCAST_BROWSE_PREFIX) == true

  /** An episode row; [show] fills in the show when the row itself omits it. */
  fun episode(renderer: MusicMultiRowListItemRenderer, show: Artist? = null): SongItem? {
    val watchEndpoint = renderer.onTap?.watchEndpoint ?: return null
    return SongItem(
      id = watchEndpoint.videoId ?: return null,
      title = renderer.title?.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
      artists = listOfNotNull(renderer.secondTitle?.runs?.firstOrNull()?.toArtist() ?: show),
      duration =
        renderer.playbackProgress
          ?.musicPlaybackProgressRenderer
          ?.durationText
          ?.runs
          ?.let(::parseDuration),
      musicVideoType = MUSIC_VIDEO_TYPE_PODCAST_EPISODE,
      thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
      endpoint = watchEndpoint,
      publishedText = renderer.subtitle?.runs?.lastOrNull()?.text?.trim()?.takeIf { it.isNotEmpty() },
      playedPercent =
        renderer.playbackProgress?.musicPlaybackProgressRenderer?.playbackProgressPercentage,
      description = renderer.description?.runs?.joinToString("") { it.text }?.takeIf { it.isNotBlank() },
    )
  }

  /** An episode card on Home; its page id is the video id behind the `MPED…` browse id. */
  fun episode(renderer: MusicTwoRowItemRenderer): SongItem? {
    val browse = renderer.navigationEndpoint.browseEndpoint ?: return null
    val pageType =
      browse.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
    if (pageType != "MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE") return null

    val playEndpoint =
      renderer.thumbnailOverlay
        ?.musicItemThumbnailOverlayRenderer
        ?.content
        ?.musicPlayButtonRenderer
        ?.playNavigationEndpoint
        ?.watchEndpoint
    val videoId =
      playEndpoint?.videoId
        ?: browse.browseId.takeIf { it.startsWith(EPISODE_BROWSE_PREFIX) }
          ?.removePrefix(EPISODE_BROWSE_PREFIX)
        ?: return null

    val subtitle = renderer.subtitle?.runs.orEmpty()
    val show =
      subtitle.firstOrNull { isShowBrowseId(it.navigationEndpoint?.browseEndpoint?.browseId) }
        ?: subtitle.lastOrNull()?.takeIf { it.text.isNotBlank() && it.text.trim() != "•" }

    return SongItem(
      id = videoId,
      title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
      artists = listOfNotNull(show?.toArtist()),
      musicVideoType = MUSIC_VIDEO_TYPE_PODCAST_EPISODE,
      thumbnail =
        renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
          ?: renderer.thumbnailRenderer.croppedSquareThumbnailRenderer?.getThumbnailUrl()
          ?: return null,
      explicit =
        renderer.subtitleBadges?.any {
          it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
        } == true,
      endpoint = playEndpoint?.copy(videoId = videoId) ?: WatchEndpoint(videoId = videoId),
    )
  }

  fun show(renderer: MusicTwoRowItemRenderer): PlaylistItem? {
    val browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null
    if (!isShowBrowseId(browseId)) return null
    return PlaylistItem(
      id = showId(browseId),
      title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
      author = renderer.subtitle?.runs?.firstOrNull { it.navigationEndpoint != null }?.toArtist(),
      songCountText = null,
      thumbnail =
        renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
          ?: renderer.thumbnailRenderer.croppedSquareThumbnailRenderer?.getThumbnailUrl(),
      playEndpoint =
        renderer.thumbnailOverlay
          ?.musicItemThumbnailOverlayRenderer
          ?.content
          ?.musicPlayButtonRenderer
          ?.playNavigationEndpoint
          ?.watchPlaylistEndpoint,
      shuffleEndpoint = null,
      radioEndpoint = null,
      isPodcast = true,
    )
  }

  fun show(renderer: MusicResponsiveListItemRenderer): PlaylistItem? {
    val browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null
    if (!isShowBrowseId(browseId)) return null
    return PlaylistItem(
      id = showId(browseId),
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
        renderer.flexColumns
          .getOrNull(1)
          ?.musicResponsiveListItemFlexColumnRenderer
          ?.text
          ?.runs
          ?.firstOrNull { it.navigationEndpoint != null }
          ?.toArtist(),
      songCountText = null,
      thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl(),
      playEndpoint =
        renderer.overlay
          ?.musicItemThumbnailOverlayRenderer
          ?.content
          ?.musicPlayButtonRenderer
          ?.playNavigationEndpoint
          ?.watchPlaylistEndpoint,
      shuffleEndpoint = null,
      radioEndpoint = null,
      isPodcast = true,
    )
  }

  /** An episode in search results: title, then "date • show". */
  fun episode(renderer: MusicResponsiveListItemRenderer): SongItem? {
    val watchEndpoint =
      renderer.overlay
        ?.musicItemThumbnailOverlayRenderer
        ?.content
        ?.musicPlayButtonRenderer
        ?.playNavigationEndpoint
        ?.watchEndpoint ?: return null
    if (renderer.musicVideoType != MUSIC_VIDEO_TYPE_PODCAST_EPISODE) return null

    val secondLine =
      renderer.flexColumns
        .getOrNull(1)
        ?.musicResponsiveListItemFlexColumnRenderer
        ?.text
        ?.runs
        .orEmpty()
    return SongItem(
      id = watchEndpoint.videoId ?: return null,
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
        listOfNotNull(
          secondLine
            .firstOrNull { isShowBrowseId(it.navigationEndpoint?.browseEndpoint?.browseId) }
            ?.toArtist(),
        ),
      musicVideoType = MUSIC_VIDEO_TYPE_PODCAST_EPISODE,
      thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
      endpoint = watchEndpoint,
      publishedText = secondLine.firstOrNull()?.takeIf { it.navigationEndpoint == null }?.text?.trim(),
    )
  }

  /** Parses "23 min", "1 h 5 min", "1:05:00" and similar into seconds. */
  fun parseDuration(runs: List<Run>): Int? {
    val text = runs.joinToString("") { it.text }.replace("•", " ").trim()
    if (text.isEmpty()) return null
    if (text.contains(':')) return text.parseTime()

    var seconds = 0
    var matched = false
    DURATION_PART.findAll(text).forEach { match ->
      val value = match.groupValues[1].toIntOrNull() ?: return@forEach
      val unit = match.groupValues[2].lowercase()
      seconds +=
        when {
          unit.startsWith("h") || unit.startsWith("o") || unit.startsWith("st") -> value * 3600
          unit.startsWith("m") -> value * 60
          unit.startsWith("s") -> value
          else -> return@forEach
        }
      matched = true
    }
    return seconds.takeIf { matched && it > 0 }
  }

  private const val EPISODE_BROWSE_PREFIX = "MPED"
  private val DURATION_PART = Regex("""(\d+)\s*(\p{L}+)""")

  private fun Run.toArtist() =
    Artist(name = ApiText.clean(text), id = navigationEndpoint?.browseEndpoint?.browseId)
}
