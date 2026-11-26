package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.metadata.cleaner.ApiText

data class PlaylistPage(
    val playlist: PlaylistItem,
    val songs: List<SongItem>,
    val songsContinuation: String?,
    val continuation: String?,
    val related: List<YTItem>? = null,
) {
    companion object {
        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            val videoId = renderer.findVideoId() ?: return null
            val title = renderer.textAt(0)?.let(ApiText::clean) ?: return null

            val artists = renderer.runsAt(1)
                .splitBySeparator()
                .firstOrNull()
                ?.oddElements()
                .orEmpty()
                .map { Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId) }

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
                duration = renderer.durationSeconds(),
                musicVideoType = renderer.musicVideoType,
                thumbnail = thumbnail,
                explicit = renderer.badges.hasExplicitBadge(),
                endpoint = renderer.overlayPlayEndpoint()?.watchEndpoint,
                setVideoId = renderer.findSetVideoId(),
                libraryAddToken = tokens.addToken,
                libraryRemoveToken = tokens.removeToken,
            )
        }
    }
}
