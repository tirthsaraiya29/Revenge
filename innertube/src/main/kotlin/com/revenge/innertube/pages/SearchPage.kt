package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.ArtistItem
import com.revenge.innertube.models.BrowseEndpoint
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.Run
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.innertube.utils.parseTime
import com.revenge.metadata.cleaner.ApiText

data class SearchResult(
    val items: List<YTItem>,
    val continuation: String? = null,
)

object SearchPage {
    fun toYTItem(renderer: MusicResponsiveListItemRenderer): YTItem? {
        PodcastPage.show(renderer)?.let { return it }
        PodcastPage.episode(renderer)?.let { return it }

        if (renderer.runsAt(1).isEmpty()) return null
        val split = renderer.runsAt(1).splitBySeparator()

        return when {
            renderer.isSong -> song(renderer, split)
            renderer.isArtist -> artist(renderer)
            renderer.isAlbum -> album(renderer, split)
            renderer.isPlaylist -> playlist(renderer, split)
            else -> null
        }
    }

    private fun song(renderer: MusicResponsiveListItemRenderer, split: List<List<Run>>): SongItem? {
        val videoId = renderer.findVideoId() ?: return null
        val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null

        val artistRuns = split.firstOrNull()?.oddElements().orEmpty()
        if (artistRuns.isEmpty()) return null
        val artists = artistRuns.map {
            Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
        }

        val albumRun = split.getOrNull(1)?.firstOrNull()
        val albumId = albumRun?.navigationEndpoint?.browseEndpoint?.browseId
        val album = if (albumRun != null && albumId != null) {
            Album(ApiText.clean(albumRun.text), albumId)
        } else {
            null
        }

        val duration = split.lastOrNull()?.firstOrNull()?.text?.parseTime()
        val thumbnail = renderer.thumbnail?.thumbnailUrl() ?: return null
        val tokens = renderer.libraryTokens()

        return SongItem(
            id = videoId,
            title = title,
            artists = artists,
            album = album,
            duration = duration,
            musicVideoType = renderer.musicVideoType,
            thumbnail = thumbnail,
            explicit = renderer.badges.hasExplicitBadge(),
            libraryAddToken = tokens.addToken,
            libraryRemoveToken = tokens.removeToken,
        )
    }

    private fun artist(renderer: MusicResponsiveListItemRenderer): ArtistItem? {
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

    private fun album(renderer: MusicResponsiveListItemRenderer, split: List<List<Run>>): com.revenge.innertube.models.AlbumItem? {
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

        return com.revenge.innertube.models.AlbumItem(
            browseId = browse.browseId,
            playlistId = playlistId,
            title = title,
            artists = artists,
            year = year,
            thumbnail = thumbnail,
            explicit = renderer.badges.hasExplicitBadge(),
        )
    }

    private fun playlist(renderer: MusicResponsiveListItemRenderer, split: List<List<Run>>): PlaylistItem? {
        val browse = renderer.navigationEndpoint?.browseEndpoint ?: return null
        val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null
        val authorRun = split.firstOrNull()?.firstOrNull() ?: return null
        val author = Artist(ApiText.clean(authorRun.text), authorRun.navigationEndpoint?.browseEndpoint?.browseId)
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
