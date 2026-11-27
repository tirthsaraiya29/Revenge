package com.revenge.innertube.pages

import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.MusicTwoRowItemRenderer
import com.revenge.metadata.cleaner.ApiText

data class LibraryAlbumsPage(
  val albums: List<AlbumItem>,
  val continuation: String?,
) {
  companion object {
    fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
      val browse = renderer.navigationEndpoint.browseEndpoint ?: return null
      val playlistId =
        renderer.thumbnailOverlay?.playEndpoint()?.watchPlaylistEndpoint?.playlistId ?: return null
      val title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null
      val year = renderer.subtitle?.runs?.lastOrNull()?.text?.trim()?.toIntOrNull()
      val thumbnail = renderer.thumbnailRenderer.thumbnailUrl() ?: return null
      return AlbumItem(
        browseId = browse.browseId,
        playlistId = playlistId,
        title = title,
        artists = null,
        year = year,
        thumbnail = thumbnail,
        explicit = renderer.subtitleBadges.hasExplicitBadge(),
      )
    }
  }
}
