package com.revenge.innertube.pages

import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.MusicTwoRowItemRenderer
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.metadata.cleaner.ApiText

object NewReleaseAlbumPage {
  fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? =
    AlbumItem(
      browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
      playlistId =
        renderer.thumbnailOverlay
          ?.musicItemThumbnailOverlayRenderer
          ?.content
          ?.musicPlayButtonRenderer
          ?.playNavigationEndpoint
          ?.watchPlaylistEndpoint
          ?.playlistId ?: return null,
      title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
      artists =
        renderer.subtitle?.runs?.splitBySeparator()?.getOrNull(1)?.oddElements()?.map {
          Artist(name = ApiText.clean(it.text), id = it.navigationEndpoint?.browseEndpoint?.browseId)
        } ?: return null,
      year = renderer.subtitle.runs.lastOrNull()?.text?.toIntOrNull(),
      thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
      explicit =
        renderer.subtitleBadges?.any {
          it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
        } == true,
    )
}
