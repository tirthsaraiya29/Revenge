package com.revenge.innertube.pages

import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.MusicMultiRowListItemRenderer
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.MusicTwoRowItemRenderer
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.Run
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.innertube.utils.parseTime
import com.revenge.metadata.cleaner.ApiText

object PodcastPage {
    private const val PODCAST_EPISODE_TYPE = "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"
    private const val NON_MUSIC_AUDIO_TRACK_PAGE = "MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE"

    fun showId(browseId: String): String = browseId.removePrefix("MPSP")

    fun showBrowseId(showId: String): String = "MPSP$showId"

    fun isShowBrowseId(browseId: String?): Boolean = browseId?.startsWith("MPSP") == true

    fun episode(renderer: MusicMultiRowListItemRenderer, show: Artist? = null): SongItem? {
        val watch = renderer.onTap?.watchEndpoint ?: return null
        val videoId = watch.videoId ?: return null
        val title = renderer.title?.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null
        val thumbnail = renderer.thumbnail.thumbnailUrl() ?: return null

        val secondTitleArtist = renderer.secondTitle?.runs?.firstOrNull()?.let {
            Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
        }
        val artists = listOfNotNull(secondTitleArtist ?: show)

        val progress = renderer.playbackProgress?.musicPlaybackProgressRenderer
        val duration = parseDuration(progress?.durationText?.runs.orEmpty())
        val publishedText = renderer.subtitle?.runs?.lastOrNull()?.text?.trim()?.takeIf { it.isNotEmpty() }
        val description = renderer.description?.runs
            ?.joinToString("") { it.text }
            ?.takeIf { it.isNotBlank() }

        return SongItem(
            id = videoId,
            title = title,
            artists = artists,
            duration = duration,
            musicVideoType = PODCAST_EPISODE_TYPE,
            thumbnail = thumbnail,
            endpoint = watch,
            publishedText = publishedText,
            playedPercent = progress?.playbackProgressPercentage,
            description = description,
        )
    }

    fun episode(renderer: MusicTwoRowItemRenderer): SongItem? {
        val browse = renderer.navigationEndpoint.browseEndpoint ?: return null
        if (browse.pageType != NON_MUSIC_AUDIO_TRACK_PAGE) return null

        val playEndpoint = renderer.thumbnailOverlay?.playEndpoint()?.watchEndpoint
        val videoId = playEndpoint?.videoId ?: browse.browseId.removePrefix("MPED")
        val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null
        val thumbnail = renderer.thumbnailRenderer.thumbnailUrl() ?: return null

        val subtitleRuns = renderer.subtitle?.runs.orEmpty()
        val showRun = subtitleRuns.firstOrNull {
            it.navigationEndpoint?.browseEndpoint?.browseId?.startsWith("MPSP") == true
        } ?: subtitleRuns.lastOrNull {
            it.text.trim().isNotEmpty() && it.text.trim() != "•"
        }
        val artists = listOfNotNull(
            showRun?.let { Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId) },
        )

        val endpoint = playEndpoint?.copy(videoId = videoId) ?: WatchEndpoint(videoId = videoId)

        return SongItem(
            id = videoId,
            title = title,
            artists = artists,
            musicVideoType = PODCAST_EPISODE_TYPE,
            thumbnail = thumbnail,
            explicit = renderer.subtitleBadges.hasExplicitBadge(),
            endpoint = endpoint,
        )
    }

    fun show(renderer: MusicTwoRowItemRenderer): PlaylistItem? {
        val browse = renderer.navigationEndpoint.browseEndpoint ?: return null
        if (!browse.browseId.startsWith("MPSP")) return null

        val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null
        val author = renderer.subtitle?.runs
            ?.firstOrNull { it.navigationEndpoint != null }
            ?.let { Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId) }

        return PlaylistItem(
            id = browse.browseId.removePrefix("MPSP"),
            title = title,
            author = author,
            songCountText = null,
            thumbnail = renderer.thumbnailRenderer.thumbnailUrl(),
            playEndpoint = renderer.thumbnailOverlay?.playEndpoint()?.watchEndpoint,
            shuffleEndpoint = null,
            radioEndpoint = null,
            isPodcast = true,
        )
    }

    fun show(renderer: MusicResponsiveListItemRenderer): PlaylistItem? {
        val browse = renderer.navigationEndpoint?.browseEndpoint ?: return null
        if (!browse.browseId.startsWith("MPSP")) return null

        val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null
        val author = renderer.runsAt(1)
            .firstOrNull { it.navigationEndpoint != null }
            ?.let { Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId) }

        return PlaylistItem(
            id = browse.browseId.removePrefix("MPSP"),
            title = title,
            author = author,
            songCountText = null,
            thumbnail = renderer.thumbnail?.thumbnailUrl(),
            playEndpoint = renderer.overlayPlayEndpoint()?.watchEndpoint,
            shuffleEndpoint = null,
            radioEndpoint = null,
            isPodcast = true,
        )
    }

    fun episode(renderer: MusicResponsiveListItemRenderer): SongItem? {
        if (renderer.musicVideoType != PODCAST_EPISODE_TYPE) return null
        val play = renderer.overlayPlayEndpoint()?.watchEndpoint ?: return null
        val videoId = play.videoId ?: return null
        val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null
        val thumbnail = renderer.thumbnail?.thumbnailUrl() ?: return null

        val artist = renderer.runsAt(1).firstOrNull {
            it.navigationEndpoint?.browseEndpoint?.browseId?.startsWith("MPSP") == true
        }?.let { Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId) }

        val publishedText = renderer.runsAt(1)
            .firstOrNull { it.navigationEndpoint == null }
            ?.text
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return SongItem(
            id = videoId,
            title = title,
            artists = listOfNotNull(artist),
            musicVideoType = PODCAST_EPISODE_TYPE,
            thumbnail = thumbnail,
            endpoint = play,
            publishedText = publishedText,
        )
    }

    fun parseDuration(runs: List<Run>): Int? {
        val joined = runs.joinToString("") { it.text }.replace("•", " ").trim()
        if (joined.isEmpty()) return null
        if (joined.contains(":")) return joined.parseTime()

        var totalSeconds = 0
        var recognised = false
        for (match in durationPartRegex.findAll(joined)) {
            val amount = match.groupValues[1].toIntOrNull() ?: continue
            val unit = match.groupValues[2].lowercase()
            val unitSeconds = when {
                unit.startsWith("st") || unit.startsWith("h") || unit.startsWith("o") -> 3600
                unit.startsWith("m") -> 60
                unit.startsWith("s") -> 1
                else -> null
            } ?: continue
            recognised = true
            totalSeconds += amount * unitSeconds
        }
        return if (recognised && totalSeconds > 0) totalSeconds else null
    }

    private val durationPartRegex = Regex("(\\d+)\\s*([A-Za-z]+)")
}
