package com.revenge.innertube.pages

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.response.BrowseResponse
import com.revenge.innertube.models.MusicResponsiveHeaderRenderer
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.MusicShelfRenderer
import com.revenge.innertube.models.Run
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.getItems
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.splitBySeparator
import com.revenge.innertube.utils.parseTime
import com.revenge.metadata.cleaner.ApiText

data class AlbumPage(
    val album: AlbumItem,
    val songs: List<SongItem>,
    val otherVersions: List<AlbumItem>,
    val releasesForYou: List<AlbumItem> = emptyList(),
    val description: String? = null,
    val descriptionRuns: List<Run>? = null,
) {
    companion object {
        fun getPlaylistId(response: BrowseResponse): String? {
            val canonical = response.microformat?.microformatDataRenderer?.urlCanonical
            if (canonical != null) return canonical.substringAfterLast('=')
            return firstTopLevelPlaylistId(response)
        }

        private fun responsiveHeader(response: BrowseResponse): MusicResponsiveHeaderRenderer? {
            val contents = response.contents
            val tabContents = sequence {
                contents?.singleColumnBrowseResultsRenderer?.tabs.orEmpty().forEach {
                    yield(it.tabRenderer.content?.sectionListRenderer?.contents)
                }
                contents?.twoColumnBrowseResultsRenderer?.tabs.orEmpty().forEach {
                    yield(it.tabRenderer.content?.sectionListRenderer?.contents)
                }
            }
            for (tabContent in tabContents) {
                tabContent?.firstNotNullOfOrNull { it.musicResponsiveHeaderRenderer }?.let { return it }
            }
            return response.header
                ?.musicEditablePlaylistDetailHeaderRenderer
                ?.header
                ?.musicResponsiveHeaderRenderer
        }

        private fun firstTopLevelPlaylistId(response: BrowseResponse): String? {
            val header = response.header ?: return null
            responsiveHeader(response)
                ?.buttons
                ?.firstOrNull()
                ?.musicPlayButtonRenderer
                ?.playNavigationEndpoint
                ?.watchPlaylistEndpoint
                ?.playlistId
                ?.let { return it }
            header.musicImmersiveHeaderRenderer
                ?.playButton
                ?.buttonRenderer
                ?.navigationEndpoint
                ?.watchPlaylistEndpoint
                ?.playlistId
                ?.let { return it }
            header.musicDetailHeaderRenderer
                ?.menu
                ?.menuRenderer
                ?.topLevelButtons
                ?.firstOrNull()
                ?.buttonRenderer
                ?.navigationEndpoint
                ?.watchPlaylistEndpoint
                ?.playlistId
                ?.let { return it }
            return header.musicEditablePlaylistDetailHeaderRenderer
                ?.header
                ?.musicDetailHeaderRenderer
                ?.menu
                ?.menuRenderer
                ?.topLevelButtons
                ?.firstOrNull()
                ?.buttonRenderer
                ?.navigationEndpoint
                ?.watchPlaylistEndpoint
                ?.playlistId
        }

        fun getTitle(response: BrowseResponse): String? {
            val detailTitle = response.header?.musicDetailHeaderRenderer?.title
            val responsiveTitle = responsiveHeader(response)?.title
            val titleRuns = responsiveTitle?.runs ?: detailTitle?.runs ?: return null
            return titleRuns.firstOrNull()?.text?.let(ApiText::clean)
        }

        fun getYear(response: BrowseResponse): Int? {
            val responsiveSubtitle = responsiveHeader(response)?.subtitle
            val detailSubtitle = response.header?.musicDetailHeaderRenderer?.subtitle
            val subtitle = responsiveSubtitle ?: detailSubtitle ?: return null
            return subtitle.runs?.lastOrNull()?.text?.trim()?.toIntOrNull()
        }

        fun getThumbnail(response: BrowseResponse): String? =
            response.background?.musicThumbnailRenderer?.getThumbnailUrl()
                ?: response.header
                    ?.musicDetailHeaderRenderer
                    ?.thumbnail
                    ?.croppedSquareThumbnailRenderer
                    ?.getThumbnailUrl()

        fun getArtists(response: BrowseResponse): List<Artist> {
            responsiveHeader(response)?.straplineTextOne?.runs?.let { runs ->
                return runs.oddElements().map { Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId) }
            }
            val detailSubtitle = response.header?.musicDetailHeaderRenderer?.subtitle?.runs ?: return emptyList()
            val secondGroup = detailSubtitle.splitBySeparator().getOrNull(1) ?: return emptyList()
            return secondGroup.oddElements()
                .map { Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId) }
        }

        fun getSongs(response: BrowseResponse, album: AlbumItem): List<SongItem> {
            val shelf = findShelf(response) ?: return emptyList()
            return shelf.contents.orEmpty().getItems().mapNotNull { getSong(it, album) }
        }

        private fun findShelf(response: BrowseResponse): MusicShelfRenderer? {
            val contents = response.contents ?: return null
            val tabContents = sequence {
                contents.singleColumnBrowseResultsRenderer?.tabs.orEmpty().forEach {
                    yield(it.tabRenderer.content?.sectionListRenderer?.contents)
                }
                contents.twoColumnBrowseResultsRenderer?.tabs.orEmpty().forEach {
                    yield(it.tabRenderer.content?.sectionListRenderer?.contents)
                }
            }
            for (tabContent in tabContents) {
                tabContent?.firstNotNullOfOrNull { it.musicShelfRenderer }?.let { return it }
            }
            return contents.twoColumnBrowseResultsRenderer
                ?.secondaryContents
                ?.sectionListRenderer
                ?.contents
                ?.firstNotNullOfOrNull { it.musicShelfRenderer }
        }

        fun getSong(
            renderer: MusicResponsiveListItemRenderer,
            album: AlbumItem? = null,
        ): SongItem? {
            val videoId = renderer.findVideoId() ?: return null

            val title = PageHelper.extractRuns(renderer.flexColumns, "MUSIC_VIDEO")
                .firstOrNull()
                ?.text
                ?.let(ApiText::clean)
                ?: return null

            val artistRuns = PageHelper.extractRuns(renderer.flexColumns, "MUSIC_PAGE_TYPE_ARTIST")
            val artists = if (artistRuns.isNotEmpty()) {
                artistRuns.map {
                    Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
                }
            } else {
                album?.artists.orEmpty()
            }

            val resolvedAlbum = if (album != null) {
                Album(album.title, album.browseId)
            } else {
                val albumRun = renderer.runsAt(2).firstOrNull {
                    it.navigationEndpoint?.browseEndpoint?.browseId != null
                } ?: throw IllegalStateException("Album row is missing an album browse id")
                Album(
                    ApiText.clean(albumRun.text),
                    albumRun.navigationEndpoint!!.browseEndpoint!!.browseId,
                )
            }

            val duration = renderer.fixedColumns
                ?.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                ?.firstOrNull()
                ?.text
                ?.parseTime()
                ?: return null

            val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                ?: album?.thumbnail
                ?: return null

            val tokens = renderer.libraryTokens()

            return SongItem(
                id = videoId,
                title = title,
                artists = artists,
                album = resolvedAlbum,
                duration = duration,
                musicVideoType = renderer.musicVideoType,
                thumbnail = thumbnail,
                explicit = renderer.badges.hasExplicitBadge(),
                libraryAddToken = tokens.addToken,
                libraryRemoveToken = tokens.removeToken,
            )
        }
    }
}
