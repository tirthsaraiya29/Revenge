package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.ArtistItem
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.clean
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.metadata.cleaner.ApiText

object SearchSuggestionPage {
    fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): YTItem? =
        when {
            renderer.isSong -> song(renderer)
            renderer.isArtist -> artist(renderer)
            renderer.isAlbum -> album(renderer)
            else -> null
        }

    private fun song(renderer: MusicResponsiveListItemRenderer): SongItem? {
        val videoId = renderer.findVideoId() ?: return null
        val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null

        val secondary = renderer.runsAt(1).splitBySeparator().clean()
        val artistRuns = secondary.firstOrNull()?.oddElements().orEmpty()
        if (artistRuns.isEmpty()) return null
        val artists = artistRuns.map {
            Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
        }

        val albumRun = renderer.runsAt(2).firstOrNull()
        val albumId = albumRun?.navigationEndpoint?.browseEndpoint?.browseId ?: return null
        val album = Album(ApiText.clean(albumRun.text), albumId)

        val thumbnail = renderer.thumbnail?.thumbnailUrl() ?: return null

        return SongItem(
            id = videoId,
            title = title,
            artists = artists,
            album = album,
            duration = null,
            musicVideoType = renderer.musicVideoType,
            thumbnail = thumbnail,
            explicit = renderer.badges.hasExplicitBadge(),
        )
    }

    private fun artist(renderer: MusicResponsiveListItemRenderer): ArtistItem? {
        val browse = renderer.navigationEndpoint?.browseEndpoint ?: return null
        val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null
        val thumbnail = renderer.thumbnail?.thumbnailUrl() ?: return null
        return ArtistItem(
            id = browse.browseId,
            title = title,
            thumbnail = thumbnail,
            shuffleEndpoint = renderer.menu.findMenuWatchEndpoint("MUSIC_SHUFFLE"),
            radioEndpoint = renderer.menu.findMenuWatchEndpoint("MIX"),
        )
    }

    private fun album(renderer: MusicResponsiveListItemRenderer): AlbumItem? {
        if (renderer.runsAt(1).isEmpty()) return null
        val split = renderer.runsAt(1).splitBySeparator()

        val browse = renderer.navigationEndpoint?.browseEndpoint ?: return null

        val shuffleItem = renderer.menu
            ?.menuRenderer
            ?.items
            .orEmpty()
            .firstOrNull { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
        val playlistId = shuffleItem
            ?.menuNavigationItemRenderer
            ?.navigationEndpoint
            ?.let { it.watchPlaylistEndpoint?.playlistId ?: it.anyWatchEndpoint?.playlistId }
            ?: return null

        val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null
        val artistRuns = split.getOrNull(1)?.oddElements().orEmpty()
        if (artistRuns.isEmpty()) return null
        val artists = artistRuns.map {
            Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
        }
        val year = split.lastOrNull()?.firstOrNull()?.text?.trim()?.toIntOrNull()
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
}
