package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.ArtistItem
import com.revenge.innertube.models.BrowseEndpoint
import com.revenge.innertube.models.MusicCardShelfRenderer
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.Run
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

data class SearchSummary(
    val title: String,
    val items: List<YTItem>,
)

data class SearchSummaryPage(
    val summaries: List<SearchSummary>,
    val ranked: List<YTItem> = emptyList(),
) {
    fun filterBlockedArtists(): SearchSummaryPage = copy(
        summaries = summaries.map { it.copy(items = it.items.filterBlockedArtists()) },
        ranked = ranked.filterBlockedArtists(),
    )

    fun filterDislikedSongs(): SearchSummaryPage = copy(
        summaries = summaries.map { it.copy(items = it.items.filterDislikedSongs()) },
        ranked = ranked.filterDislikedSongs(),
    )

    fun filterExplicit(enabled: Boolean = true): SearchSummaryPage {
        if (!enabled) return this
        return copy(
            summaries = summaries.mapNotNull { summary ->
                val filtered = summary.items.filterExplicit(true)
                if (filtered.isEmpty()) null else summary.copy(items = filtered)
            },
            ranked = ranked.filterExplicit(true),
        )
    }

    fun filterVideoSongs(disableVideos: Boolean = false): SearchSummaryPage {
        if (!disableVideos) return this
        return copy(
            summaries = summaries.mapNotNull { summary ->
                if (summary.title == "Videos") return@mapNotNull summary
                val filtered = summary.items.filterVideoSongs(true)
                if (filtered.isEmpty()) null else summary.copy(items = filtered)
            },
            ranked = ranked.filterVideoSongs(true),
        )
    }

    fun filterYoutubeShorts(enabled: Boolean = false): SearchSummaryPage {
        if (!enabled) return this
        return copy(
            summaries = summaries.mapNotNull { summary ->
                val filtered = summary.items.filterYoutubeShorts(true)
                if (filtered.isEmpty()) null else summary.copy(items = filtered)
            },
            ranked = ranked.filterYoutubeShorts(true),
        )
    }

    companion object {
        fun fromMusicCardShelfRenderer(renderer: MusicCardShelfRenderer): YTItem? {
            val onTap = renderer.onTap
            onTap.watchEndpoint?.let { return cardSong(renderer, it.videoId, onTap.musicVideoType) }
            val browse = onTap.browseEndpoint ?: return null
            return when {
                browse.isArtistEndpoint -> cardArtist(renderer, browse)
                browse.isAlbumEndpoint -> cardAlbum(renderer, browse)
                browse.isPlaylistEndpoint -> cardPlaylist(renderer, browse)
                else -> null
            }
        }

        private fun cardSong(
            renderer: MusicCardShelfRenderer,
            videoId: String?,
            musicVideoType: String?,
        ): SongItem? {
            val id = videoId ?: return null
            val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null
            val groups = renderer.subtitle.runs.orEmpty().splitBySeparator()

            val artistRuns = groups.getOrNull(1)?.oddElements().orEmpty()
            if (artistRuns.isEmpty()) return null
            val artists = artistRuns.map {
                Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
            }

            val albumRun = groups.getOrNull(2)?.firstOrNull()
            val albumId = albumRun?.navigationEndpoint?.browseEndpoint?.browseId
            val album = if (albumRun != null && albumId != null) {
                Album(ApiText.clean(albumRun.text), albumId)
            } else {
                null
            }

            val duration = groups.lastOrNull()?.firstOrNull()?.text?.parseTime()
            val thumbnail = renderer.thumbnail.thumbnailUrl() ?: return null

            return SongItem(
                id = id,
                title = title,
                artists = artists,
                album = album,
                duration = duration,
                musicVideoType = musicVideoType,
                thumbnail = thumbnail,
                explicit = renderer.subtitleBadges.hasExplicitBadge(),
            )
        }

        private fun cardArtist(renderer: MusicCardShelfRenderer, browse: BrowseEndpoint): ArtistItem? {
            val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null
            val shuffleEndpoint = renderer.buttons.findButtonWatchEndpoint("MUSIC_SHUFFLE") ?: return null
            val radioEndpoint = renderer.buttons.findButtonWatchEndpoint("MIX") ?: return null
            return ArtistItem(
                id = browse.browseId,
                title = title,
                thumbnail = renderer.thumbnail.thumbnailUrl(),
                shuffleEndpoint = shuffleEndpoint,
                radioEndpoint = radioEndpoint,
            )
        }

        private fun cardAlbum(renderer: MusicCardShelfRenderer, browse: BrowseEndpoint): AlbumItem? {
            val playlistId = renderer.buttons
                .firstOrNull()
                ?.buttonRenderer
                ?.command
                ?.anyWatchEndpoint
                ?.playlistId
                ?: return null
            val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null

            val artistRuns = renderer.subtitle.runs
                .orEmpty()
                .splitBySeparator()
                .getOrNull(1)
                ?.oddElements()
                .orEmpty()
            if (artistRuns.isEmpty()) return null
            val artists = artistRuns.map {
                Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
            }

            val thumbnail = renderer.thumbnail.thumbnailUrl() ?: return null
            return AlbumItem(
                browseId = browse.browseId,
                playlistId = playlistId,
                title = title,
                artists = artists,
                year = null,
                thumbnail = thumbnail,
                explicit = renderer.subtitleBadges.hasExplicitBadge(),
            )
        }

        private fun cardPlaylist(renderer: MusicCardShelfRenderer, browse: BrowseEndpoint): PlaylistItem? {
            val title = renderer.header
                ?.musicCardShelfHeaderBasicRenderer
                ?.title
                ?.runs
                ?.joinToString("") { it.text }
                ?.let(ApiText::clean)
                ?.takeIf { it.isNotBlank() }
                ?: return null

            val authorName = renderer.subtitle.runs
                .orEmpty()
                .joinToString(", ") { it.text }
                .let(ApiText::clean)
                .takeIf { it.isNotBlank() }
                ?: return null

            val playEndpoint = renderer.buttons.findButtonWatchEndpoint("PLAY_ARROW") ?: return null
            val shuffleEndpoint = renderer.buttons.findButtonWatchEndpoint("MUSIC_SHUFFLE") ?: return null

            return PlaylistItem(
                id = browse.browseId.removePrefix("VL"),
                title = title,
                author = Artist(authorName, null),
                songCountText = null,
                thumbnail = renderer.thumbnail.thumbnailUrl(),
                playEndpoint = playEndpoint,
                shuffleEndpoint = shuffleEndpoint,
                radioEndpoint = null,
            )
        }

        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): YTItem? {
            PodcastPage.show(renderer)?.let { return it }
            PodcastPage.episode(renderer)?.let { return it }

            if (renderer.runsAt(1).isEmpty()) return null
            val secondary = renderer.runsAt(1).splitBySeparator()
            val third = if (renderer.runsAt(2).isEmpty()) emptyList() else renderer.runsAt(2).splitBySeparator()
            val listRun = (secondary + third).clean()

            return when {
                renderer.isSong -> responsiveSong(renderer, listRun, secondary)
                renderer.isArtist -> responsiveArtist(renderer)
                renderer.isAlbum -> responsiveAlbum(renderer, secondary)
                renderer.isPlaylist -> responsivePlaylist(renderer, secondary)
                else -> null
            }
        }

        private fun responsiveSong(
            renderer: MusicResponsiveListItemRenderer,
            listRun: List<List<Run>>,
            secondary: List<List<Run>>,
        ): SongItem? {
            val videoId = renderer.findVideoId() ?: return null
            val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null

            val artistRuns = listRun.getOrNull(0)?.oddElements().orEmpty()
            if (artistRuns.isEmpty()) return null
            val artists = artistRuns.map {
                Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
            }

            val albumRun = listRun.getOrNull(1)?.firstOrNull()
            val albumId = albumRun?.navigationEndpoint?.browseEndpoint?.browseId
            val album = if (albumRun != null && albumId != null) {
                Album(ApiText.clean(albumRun.text), albumId)
            } else {
                null
            }

            val duration = secondary.lastOrNull()?.firstOrNull()?.text?.parseTime()
            val thumbnail = renderer.thumbnail?.thumbnailUrl() ?: return null

            return SongItem(
                id = videoId,
                title = title,
                artists = artists,
                album = album,
                duration = duration,
                musicVideoType = renderer.musicVideoType,
                thumbnail = thumbnail,
                explicit = renderer.badges.hasExplicitBadge(),
            )
        }

        private fun responsiveArtist(renderer: MusicResponsiveListItemRenderer): ArtistItem? {
            val browse = renderer.navigationEndpoint?.browseEndpoint ?: return null
            val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null
            val thumbnail = renderer.thumbnail?.thumbnailUrl() ?: return null
            val shuffleEndpoint = renderer.menu.findMenuWatchEndpoint("MUSIC_SHUFFLE") ?: return null
            val radioEndpoint = renderer.menu.findMenuWatchEndpoint("MIX") ?: return null
            return ArtistItem(
                id = browse.browseId,
                title = title,
                thumbnail = thumbnail,
                shuffleEndpoint = shuffleEndpoint,
                radioEndpoint = radioEndpoint,
            )
        }

        private fun responsiveAlbum(
            renderer: MusicResponsiveListItemRenderer,
            split: List<List<Run>>,
        ): AlbumItem? {
            val browse = renderer.navigationEndpoint?.browseEndpoint ?: return null
            val playlistId = renderer.overlayPlayEndpoint()?.anyWatchEndpoint?.playlistId ?: return null
            val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null

            val artistRuns = split.getOrNull(1)?.oddElements().orEmpty()
            if (artistRuns.isEmpty()) return null
            val artists = artistRuns.map {
                Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
            }

            val year = split.getOrNull(2)?.firstOrNull()?.text?.trim()?.toIntOrNull()
            val thumbnail = renderer.thumbnail?.thumbnailUrl() ?: return null
            return AlbumItem(
                browseId = browse.browseId,
                playlistId = playlistId,
                title = title,
                artists = artists,
                year = year,
                thumbnail = thumbnail,
                explicit = renderer.badges.hasExplicitBadge(),
            )
        }

        private fun responsivePlaylist(
            renderer: MusicResponsiveListItemRenderer,
            secondary: List<List<Run>>,
        ): PlaylistItem? {
            val browse = renderer.navigationEndpoint?.browseEndpoint ?: return null
            val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null
            val authorRun = secondary.getOrNull(1)?.firstOrNull() ?: return null
            val author = Artist(
                ApiText.clean(authorRun.text),
                authorRun.navigationEndpoint?.browseEndpoint?.browseId,
            )
            val songCountText = renderer.runsAt(1).lastOrNull()?.text ?: return null
            val playEndpoint = renderer.overlayPlayEndpoint()?.watchPlaylistEndpoint ?: return null
            val shuffleEndpoint = renderer.menu.findMenuWatchEndpoint("MUSIC_SHUFFLE") ?: return null
            val radioEndpoint = renderer.menu.findMenuWatchEndpoint("MIX") ?: return null

            return PlaylistItem(
                id = browse.browseId.removePrefix("VL"),
                title = title,
                author = author,
                songCountText = songCountText,
                thumbnail = renderer.thumbnail?.thumbnailUrl(),
                playEndpoint = playEndpoint,
                shuffleEndpoint = shuffleEndpoint,
                radioEndpoint = radioEndpoint,
            )
        }
    }
}
