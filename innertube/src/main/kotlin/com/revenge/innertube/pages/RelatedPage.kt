package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.ArtistItem
import com.revenge.innertube.models.BrowseEndpoint
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.MusicTwoRowItemRenderer
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.metadata.cleaner.ApiText

data class RelatedPage(
    val songs: List<SongItem>,
    val albums: List<AlbumItem>,
    val artists: List<ArtistItem>,
    val playlists: List<PlaylistItem>,
) {
    companion object {
        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            val videoId = renderer.findVideoId() ?: return null
            val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null

            val artistRuns = renderer.runsAt(1).splitBySeparator().firstOrNull()?.oddElements().orEmpty()
            if (artistRuns.isEmpty()) return null
            val artists = artistRuns.map {
                Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
            }

            val albumRun = renderer.runsAt(2).firstOrNull()
            val albumId = albumRun?.navigationEndpoint?.browseEndpoint?.browseId
            val album = if (albumRun != null && albumId != null) {
                Album(ApiText.clean(albumRun.text), albumId)
            } else {
                null
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
            val browse = renderer.navigationEndpoint.browseEndpoint ?: return null
            return when {
                browse.isAlbumEndpoint -> album(renderer, browse)
                browse.isPlaylistEndpoint -> playlist(renderer, browse)
                browse.isArtistEndpoint -> artist(renderer, browse)
                else -> null
            }
        }

        private fun album(renderer: MusicTwoRowItemRenderer, browse: BrowseEndpoint): AlbumItem? {
            val playlistId = renderer.thumbnailOverlay
                ?.playEndpoint()
                ?.watchPlaylistEndpoint
                ?.playlistId
                ?: return null
            val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null
            val artistRuns = renderer.subtitle?.runs
                .orEmpty()
                .splitBySeparator()
                .getOrNull(1)
                ?.oddElements()
                .orEmpty()
            val artists = artistRuns.map {
                Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
            }.takeIf { it.isNotEmpty() }
            val year = renderer.subtitle?.runs?.lastOrNull()?.text?.trim()?.toIntOrNull()
            val thumbnail = renderer.thumbnailRenderer.thumbnailUrl() ?: return null
            return AlbumItem(
                browseId = browse.browseId,
                playlistId = playlistId,
                title = title,
                artists = artists,
                year = year,
                thumbnail = thumbnail,
                explicit = renderer.subtitleBadges.hasExplicitBadge(),
            )
        }

        private fun playlist(renderer: MusicTwoRowItemRenderer, browse: BrowseEndpoint): PlaylistItem? {
            val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null
            val authorRun = renderer.subtitle?.runs?.lastOrNull()
            val author = Artist(ApiText.clean(authorRun?.text.orEmpty()), null)
            val songCountText = renderer.subtitle?.runs
                ?.lastOrNull { run ->
                    run.text.any(Char::isDigit) && !run.text.contains("view", ignoreCase = true)
                }
                ?.text
            val playEndpoint = renderer.thumbnailOverlay?.playEndpoint()?.watchEndpoint ?: return null
            val shuffleEndpoint = renderer.menu.findMenuWatchEndpoint("MUSIC_SHUFFLE") ?: return null
            return PlaylistItem(
                id = browse.browseId.removePrefix("VL"),
                title = title,
                author = author,
                songCountText = songCountText,
                thumbnail = renderer.thumbnailRenderer.thumbnailUrl(),
                playEndpoint = playEndpoint,
                shuffleEndpoint = shuffleEndpoint,
                radioEndpoint = renderer.menu.findMenuWatchEndpoint("MIX"),
            )
        }

        private fun artist(renderer: MusicTwoRowItemRenderer, browse: BrowseEndpoint): ArtistItem? {
            val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null
            val shuffleEndpoint = renderer.menu.findMenuWatchEndpoint("MUSIC_SHUFFLE") ?: return null
            val radioEndpoint = renderer.menu.findMenuWatchEndpoint("MIX") ?: return null
            return ArtistItem(
                id = browse.browseId,
                title = title,
                thumbnail = renderer.thumbnailRenderer.thumbnailUrl(),
                shuffleEndpoint = shuffleEndpoint,
                radioEndpoint = radioEndpoint,
            )
        }
    }
}
