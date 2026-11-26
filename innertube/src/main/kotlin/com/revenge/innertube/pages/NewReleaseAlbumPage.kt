package com.revenge.innertube.pages

import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.MusicTwoRowItemRenderer
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.metadata.cleaner.ApiText

object NewReleaseAlbumPage {
    fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
        val browse = renderer.navigationEndpoint.browseEndpoint ?: return null
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
        if (artistRuns.isEmpty()) return null
        val artists = artistRuns.map {
            Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
        }

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
}
