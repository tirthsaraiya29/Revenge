package com.revenge.innertube

import com.revenge.innertube.models.*
import com.revenge.innertube.models.comment.Comment
import com.revenge.innertube.models.comment.CommentRenderer
import com.revenge.innertube.models.comment.CommentResponse
import com.revenge.innertube.models.comment.CommentThreadRenderer
import com.revenge.innertube.models.response.*
import com.revenge.innertube.pages.*
import com.revenge.metadata.cleaner.ApiText
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import java.text.Normalizer
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/**
 * Facade over [InnerTube]: decodes InnerTube JSON into page models, applies user filters and maps
 * every failure into the typed [InnerTubeException] hierarchy. Cancellation always propagates.
 */
object YouTube {

    const val MAX_GET_QUEUE_SIZE = 1000

    @JvmInline
    value class SearchFilter(val value: String) {
        companion object {
            val FILTER_SONG = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            val FILTER_VIDEO = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ALBUM = SearchFilter("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ARTIST = SearchFilter("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_FEATURED_PLAYLIST = SearchFilter("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D")
            val FILTER_COMMUNITY_PLAYLIST = SearchFilter("EgeKAQQoAEABagoQAxAEEAoQCRAF")
            val FILTER_PODCAST = SearchFilter("EgWKAQJQAWoKEAkQBRAKEAMQBA%3D%3D")
            val FILTER_EPISODE = SearchFilter("EgWKAQJIAWoKEAkQBRAKEAMQBA%3D%3D")
        }
    }

    @JvmInline
    value class LibraryFilter(val value: String) {
        companion object {
            val FILTER_RECENT_ACTIVITY = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpYnJhcnlfbGFuZGluZxoQZ2dNR0tnUUlCaEFCb0FZQg%3D%3D")
            val FILTER_RECENTLY_PLAYED = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpYnJhcnlfbGFuZGluZxoQZ2dNR0tnUUlCUkFCb0FZQg%3D%3D")
            val FILTER_PLAYLISTS_ALPHABETICAL = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpa2VkX3BsYXlsaXN0cxoQZ2dNR0tnUUlBUkFBb0FZQg%3D%3D")
            val FILTER_PLAYLISTS_RECENTLY_SAVED = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpa2VkX3BsYXlsaXN0cxoQZ2dNR0tnUUlBQkFCb0FZQg%3D%3D")
        }
    }

    private val innerTube = InnerTube()

    var blockedArtists: Set<String> = emptySet()
    var dislikedSongs: Set<String> = emptySet()

    var locale: YouTubeLocale
        get() = innerTube.locale
        set(value) { innerTube.locale = value }

    var visitorData: String?
        get() = innerTube.visitorData
        set(value) { innerTube.visitorData = value }

    var dataSyncId: String?
        get() = innerTube.dataSyncId
        set(value) { innerTube.dataSyncId = value }

    var cookie: String?
        get() = innerTube.cookie
        set(value) { innerTube.cookie = value }

    var proxy: java.net.Proxy?
        get() = innerTube.proxy
        set(value) { innerTube.proxy = value }

    var proxyAuth: String?
        get() = innerTube.proxyAuth
        set(value) { innerTube.proxyAuth = value }

    var useLoginForBrowse: Boolean
        get() = innerTube.useLoginForBrowse
        set(value) { innerTube.useLoginForBrowse = value }

    var ipVersion: IpVersion
        get() = innerTube.ipVersion
        set(value) { innerTube.ipVersion = value }

    // ---- shared helpers -------------------------------------------------------------------

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(InnerTubeErrors.classify(error))
        }

    private fun runsText(runs: Runs?): String? =
        runs?.runs?.joinToString("") { it.text }?.takeIf { it.isNotBlank() }

    private fun thumbnailUrl(renderer: ThumbnailRenderer?): String? =
        renderer?.musicThumbnailRenderer?.getThumbnailUrl()
            ?: renderer?.croppedSquareThumbnailRenderer?.getThumbnailUrl()

    private fun sectionContents(response: BrowseResponse): List<SectionListRenderer.Content> {
        val result = mutableListOf<SectionListRenderer.Content>()
        response.contents?.sectionListRenderer?.contents?.let(result::addAll)
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.tabs.orEmpty().forEach { tab ->
            tab?.tabRenderer?.content?.sectionListRenderer?.contents?.let(result::addAll)
        }
        response.contents?.twoColumnBrowseResultsRenderer?.tabs.orEmpty().forEach { tab ->
            tab?.tabRenderer?.content?.sectionListRenderer?.contents?.let(result::addAll)
        }
        return result
    }

    private fun primarySectionList(response: BrowseResponse): SectionListRenderer? =
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer
            ?: response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer
            ?: response.contents?.sectionListRenderer

    private fun headerTitle(response: BrowseResponse): String? {
        response.header?.musicImmersiveHeaderRenderer?.title?.let { return runsText(it) }
        response.header?.musicVisualHeaderRenderer?.title?.let { return runsText(it) }
        response.header?.musicHeaderRenderer?.title?.let { return runsText(it) }
        response.header?.musicDetailHeaderRenderer?.title?.let { return runsText(it) }
        response.header?.musicEditablePlaylistDetailHeaderRenderer?.let { editable ->
            editable.header.musicResponsiveHeaderRenderer?.title?.let { return runsText(it) }
            editable.header.musicDetailHeaderRenderer?.title?.let { return runsText(it) }
        }
        return sectionContents(response).firstNotNullOfOrNull { it.musicResponsiveHeaderRenderer?.title?.let(::runsText) }
    }

    private fun headerThumbnail(header: BrowseResponse.Header?): String? =
        thumbnailUrl(header?.musicImmersiveHeaderRenderer?.thumbnail)
            ?: thumbnailUrl(header?.musicVisualHeaderRenderer?.foregroundThumbnail)
            ?: thumbnailUrl(header?.musicVisualHeaderRenderer?.thumbnail)
            ?: header?.musicHeaderRenderer?.thumbnail?.let { thumb ->
                thumb.musicThumbnailRenderer.getThumbnailUrl() ?: thumb.thumbnails?.lastOrNull()?.url
            }
            ?: thumbnailUrl(header?.musicDetailHeaderRenderer?.thumbnail)
            ?: header?.musicEditablePlaylistDetailHeaderRenderer?.let { editable ->
                thumbnailUrl(editable.header.musicResponsiveHeaderRenderer?.thumbnail)
                    ?: thumbnailUrl(editable.header.musicDetailHeaderRenderer?.thumbnail)
            }

    private fun descriptionRunsOf(response: BrowseResponse): List<Run>? {
        sectionContents(response).forEach { content ->
            content.musicDescriptionShelfRenderer?.description?.runs?.let { return it }
        }
        response.header?.musicDetailHeaderRenderer?.description?.runs?.let { return it }
        response.header?.musicImmersiveHeaderRenderer?.description?.runs?.let { return it }
        response.header?.musicEditablePlaylistDetailHeaderRenderer?.let { editable ->
            editable.header.musicDetailHeaderRenderer?.description?.runs?.let { return it }
            editable.header.musicResponsiveHeaderRenderer?.description?.runs?.let { return it }
        }
        sectionContents(response).forEach { content ->
            content.musicResponsiveHeaderRenderer?.description?.runs?.let { return it }
        }
        return null
    }

    private fun menuRendererEndpoint(renderer: Menu.MenuRenderer?, iconType: String): WatchEndpoint? {
        renderer?.items.orEmpty().forEach { item ->
            item.menuNavigationItemRenderer?.let { nav ->
                if (nav.icon.iconType == iconType) return nav.navigationEndpoint.anyWatchEndpoint
            }
            item.menuServiceItemRenderer?.let { service ->
                if (service.icon.iconType == iconType) return service.serviceEndpoint.anyWatchEndpoint
            }
        }
        return null
    }

    private fun menuEndpoint(menu: Menu?, iconType: String): WatchEndpoint? =
        menuRendererEndpoint(menu?.menuRenderer, iconType)

    private fun authorFromRuns(runs: Runs?): Artist? {
        val run = runs?.runs?.firstOrNull {
            it.navigationEndpoint?.browseEndpoint?.isArtistEndpoint == true
        } ?: return null
        return Artist(ApiText.clean(run.text), run.navigationEndpoint?.browseEndpoint?.browseId)
    }

    private fun carouselTitle(shelf: MusicCarouselShelfRenderer): String? =
        runsText(shelf.header?.musicCarouselShelfBasicHeaderRenderer?.title)

    private fun carouselMoreBrowseId(shelf: MusicCarouselShelfRenderer): String? =
        shelf.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer
            ?.navigationEndpoint?.browseEndpoint?.browseId

    private fun carouselAlbumItems(shelf: MusicCarouselShelfRenderer): List<AlbumItem> =
        shelf.contents.mapNotNull { content ->
            content.musicTwoRowItemRenderer?.let { run -> NewReleaseAlbumPage.fromMusicTwoRowItemRenderer(run) }
        }

    private fun relatedFromContent(content: SectionListRenderer.Content): List<YTItem> {
        val fromCarousel = content.musicCarouselShelfRenderer?.contents.orEmpty().mapNotNull { item ->
            item.musicTwoRowItemRenderer?.let { run -> RelatedPage.fromMusicTwoRowItemRenderer(run) }
        }
        val fromShelf = content.musicShelfRenderer?.contents.orEmpty().mapNotNull { item ->
            item.musicResponsiveListItemRenderer?.let { run -> RelatedPage.fromMusicResponsiveListItemRenderer(run) }
        }
        return fromCarousel + fromShelf
    }

    private fun chartResponsiveItem(renderer: MusicResponsiveListItemRenderer): YTItem? {
        if (renderer.flexColumns.size < 3) return null
        if (renderer.playlistItemData?.videoId == null) return null
        val item = SearchPage.toYTItem(renderer) ?: return null
        if (item !is SongItem) return item
        val third = renderer.flexColumns[2].musicResponsiveListItemFlexColumnRenderer.text?.runs.orEmpty()
        return item.copy(
            chartPosition = third.getOrNull(0)?.text?.toIntOrNull(),
            chartChange = third.getOrNull(1)?.text,
        )
    }

    private fun chartTypeOf(title: String): ChartType = when {
        title.contains("Trending", ignoreCase = true) -> ChartType.TRENDING
        title.contains("Top", ignoreCase = true) -> ChartType.TOP
        else -> ChartType.GENRE
    }

    private fun normalizeTitle(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    private fun sameSong(a: SongItem, b: SongItem): Boolean =
        normalizeTitle(a.title) == normalizeTitle(b.title) && a.artists.any { left ->
            b.artists.any { right ->
                (left.id != null && left.id == right.id) || left.name.equals(right.name, ignoreCase = true)
            }
        }

    private fun reorderAlbumTracks(items: List<YTItem>): List<YTItem> {
        val tracks = items.filterIsInstance<SongItem>().filter { !it.isVideoSong }
        val emitted = HashSet<String>()
        val result = mutableListOf<YTItem>()
        for (item in items) {
            if (item is SongItem && item.isVideoSong) {
                val match = tracks.firstOrNull { track ->
                    track.id != item.id && track.id !in emitted && sameSong(item, track)
                }
                if (match != null) {
                    emitted += match.id
                    result += match
                }
            }
            if (emitted.add(item.id)) result += item
        }
        return result
    }

    private fun flatSummaries(flat: List<YTItem>): List<SearchSummary> {
        val groups: List<Pair<String, List<YTItem>>> = listOf(
            "Songs" to flat.filterIsInstance<SongItem>().filter { !it.isVideoSong },
            "Videos" to flat.filterIsInstance<SongItem>().filter { it.isVideoSong && !it.isPodcastEpisode },
            "Episodes" to flat.filterIsInstance<SongItem>().filter { it.isPodcastEpisode },
            "Albums" to flat.filterIsInstance<AlbumItem>(),
            "Artists" to flat.filterIsInstance<ArtistItem>(),
            "Playlists" to flat.filterIsInstance<PlaylistItem>(),
        )
        return groups.filter { it.second.isNotEmpty() }.map { SearchSummary(it.first, it.second) }
    }

    // ---- search ---------------------------------------------------------------------------

    suspend fun searchSuggestions(query: String): Result<SearchSuggestions> = attempt {
        val response = innerTube.getSearchSuggestions(YouTubeClient.WEB_REMIX, query)
            .body<GetSearchSuggestionsResponse>()
        val sections = response.contents.orEmpty()
        val queries = sections.getOrNull(0)?.searchSuggestionsSectionRenderer?.contents.orEmpty()
            .mapNotNull { content ->
                content.searchSuggestionRenderer?.suggestion?.runs?.joinToString("") { it.text }
            }
        val items = sections.getOrNull(1)?.searchSuggestionsSectionRenderer?.contents.orEmpty()
            .mapNotNull { content ->
                content.musicResponsiveListItemRenderer?.let { row ->
                    SearchSuggestionPage.fromMusicResponsiveListItemRenderer(row)
                }
            }
            .filterBlockedArtists()
            .filterDislikedSongs()
        SearchSuggestions(queries, items)
    }

    suspend fun searchSummary(query: String): Result<SearchSummaryPage> = attempt {
        val response = innerTube.search(YouTubeClient.WEB_REMIX, query = query).body<SearchResponse>()
        val contents = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty()

        val summaries = mutableListOf<SearchSummary>()
        val flat = mutableListOf<YTItem>()

        contents.forEach { content ->
            content.musicCardShelfRenderer?.let { card ->
                val title = runsText(card.header?.musicCardShelfHeaderBasicRenderer?.title)
                    ?: YouTubeConstants.DEFAULT_TOP_RESULT
                val items = buildList<YTItem> {
                    SearchSummaryPage.fromMusicCardShelfRenderer(card)?.let { item -> add(item) }
                    card.contents.orEmpty().forEach { child ->
                        child.musicResponsiveListItemRenderer?.let { row ->
                            SearchSummaryPage.fromMusicResponsiveListItemRenderer(row)?.let { item -> add(item) }
                        }
                    }
                }.distinctBy { it.id }
                summaries += SearchSummary(title, items)
            }
            content.musicShelfRenderer?.let { shelf ->
                val title = runsText(shelf.title) ?: YouTubeConstants.DEFAULT_OTHER_RESULTS
                val items = shelf.contents.orEmpty().getItems()
                    .mapNotNull { row -> SearchSummaryPage.fromMusicResponsiveListItemRenderer(row) }
                    .distinctBy { it.id }
                summaries += SearchSummary(title, items)
            }
            content.itemSectionRenderer?.contents.orEmpty().forEach { child ->
                child.musicResponsiveListItemRenderer?.let { row ->
                    SearchSummaryPage.fromMusicResponsiveListItemRenderer(row)?.let { item -> flat.add(item) }
                }
            }
        }

        var ranked = reorderAlbumTracks((summaries.flatMap { it.items } + flat).distinctBy { it.id })
        val firstSummary = summaries.firstOrNull()
        val firstItem = firstSummary?.items?.firstOrNull()
        if (firstSummary != null && firstItem is SongItem && firstItem.isVideoSong) {
            val track = ranked.filterIsInstance<SongItem>()
                .firstOrNull { it.id != firstItem.id && !it.isVideoSong && sameSong(firstItem, it) }
            if (track != null) {
                summaries[0] = firstSummary.copy(items = (listOf(track) + firstSummary.items).distinctBy { it.id })
                ranked = reorderAlbumTracks((summaries.flatMap { it.items } + flat).distinctBy { it.id })
            }
        }

        val allSummaries = summaries + flatSummaries(flat)
        SearchSummaryPage(
            summaries = allSummaries.map {
                SearchSummary(it.title, it.items.filterBlockedArtists().filterDislikedSongs())
            },
            ranked = ranked.filterBlockedArtists().filterDislikedSongs(),
        )
    }

    suspend fun search(query: String, filter: SearchFilter, setLogin: Boolean? = null): Result<SearchResult> = attempt {
        val response = innerTube.search(YouTubeClient.WEB_REMIX, query, filter.value, setLogin = setLogin)
            .body<SearchResponse>()
        val shelf = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty()
            .firstNotNullOfOrNull { it.musicShelfRenderer }
        val items = shelf?.contents.orEmpty().getItems()
            .mapNotNull { SearchPage.toYTItem(it) }
            .filterBlockedArtists()
            .filterDislikedSongs()
        SearchResult(items, shelf?.continuations.orEmpty().getContinuation())
    }

    suspend fun searchContinuation(continuation: String): Result<SearchResult> = attempt {
        val response = innerTube.search(YouTubeClient.WEB_REMIX, continuation = continuation)
            .body<SearchResponse>()
        val shelf = response.continuationContents?.musicShelfContinuation
        val items = shelf?.contents.orEmpty()
            .mapNotNull { SearchPage.toYTItem(it.musicResponsiveListItemRenderer) }
            .filterBlockedArtists()
            .filterDislikedSongs()
        SearchResult(items, if (items.isEmpty()) null else shelf?.continuations.orEmpty().getContinuation())
    }

    // ---- albums and artists ---------------------------------------------------------------

    suspend fun album(browseId: String, withSongs: Boolean = true): Result<AlbumPage> = attempt {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, browseId, setLogin = true).body<BrowseResponse>()
        val descriptionRuns = descriptionRunsOf(response)
        val privateRelease = browseId.contains("FEmusic_library_privately_owned_release_detail")

        val albumItem = AlbumItem(
            browseId = browseId,
            playlistId = AlbumPage.getPlaylistId(response) ?: "",
            title = AlbumPage.getTitle(response) ?: "",
            artists = AlbumPage.getArtists(response),
            year = AlbumPage.getYear(response),
            thumbnail = AlbumPage.getThumbnail(response) ?: "",
            description = descriptionRuns?.joinToString("") { it.text },
        )

        val songs = when {
            !withSongs -> emptyList()
            privateRelease -> AlbumPage.getSongs(response, albumItem)
            else -> albumSongs(albumItem.playlistId, albumItem).getOrThrow()
        }

        val carousels = sectionContents(response).mapNotNull { it.musicCarouselShelfRenderer }
        val otherVersions = carousels
            .filter { carouselTitle(it)?.contains("versions", ignoreCase = true) == true }
            .flatMap { carouselAlbumItems(it) }
        val releasesForYou = carousels
            .filter {
                val title = carouselTitle(it)
                title != null && (title.contains("releases", true) || title.contains("more from", true))
            }
            .flatMap { carouselAlbumItems(it) }

        AlbumPage(
            album = albumItem,
            songs = songs,
            otherVersions = otherVersions,
            releasesForYou = releasesForYou,
            description = albumItem.description,
            descriptionRuns = descriptionRuns,
        )
    }

    suspend fun albumSongs(playlistId: String, album: AlbumItem? = null): Result<List<SongItem>> = attempt {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, "VL$playlistId", setLogin = true)
            .body<BrowseResponse>()
        val shelf = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents
            ?.sectionListRenderer?.contents?.firstNotNullOfOrNull { it.musicPlaylistShelfRenderer }

        val songs = mutableListOf<SongItem>()
        shelf?.contents.orEmpty().forEach { content ->
            content.musicResponsiveListItemRenderer?.let { row ->
                AlbumPage.getSong(row, album)?.let { item -> songs.add(item) }
            }
        }

        val visited = HashSet<String>()
        var token = shelf?.continuations.orEmpty().getContinuation()
        var pages = 0
        while (token != null && pages < 50 && visited.add(token)) {
            val page = innerTube.browse(YouTubeClient.WEB_REMIX, continuation = token, setLogin = true)
                .body<BrowseResponse>()
            page.onResponseReceivedActions.orEmpty().forEach { action ->
                action.appendContinuationItemsAction?.continuationItems.orEmpty().forEach { content ->
                    content.musicResponsiveListItemRenderer?.let { row ->
                        AlbumPage.getSong(row, album)?.let { item -> songs.add(item) }
                    }
                }
            }
            token = page.continuationContents?.musicPlaylistShelfContinuation
                ?.continuations.orEmpty().getContinuation()
            pages++
        }
        songs
    }

    suspend fun artist(browseId: String): Result<ArtistPage> = attempt {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, browseId, setLogin = true).body<BrowseResponse>()
        val header = response.header
        val immersive = header?.musicImmersiveHeaderRenderer
        val visual = header?.musicVisualHeaderRenderer
        val musicHeader = header?.musicHeaderRenderer
        val detail = header?.musicDetailHeaderRenderer
        val contents = primarySectionList(response)?.contents.orEmpty()

        val title = runsText(immersive?.title)
            ?: runsText(visual?.title)
            ?: runsText(musicHeader?.title)
            ?: runsText(detail?.title)
        val thumbnail = thumbnailUrl(immersive?.thumbnail)
            ?: thumbnailUrl(visual?.foregroundThumbnail)
            ?: thumbnailUrl(visual?.thumbnail)
            ?: musicHeader?.thumbnail?.let { thumb ->
                thumb.musicThumbnailRenderer.getThumbnailUrl() ?: thumb.thumbnails?.lastOrNull()?.url
            }
            ?: thumbnailUrl(detail?.thumbnail)

        val descriptionRuns = contents.firstNotNullOfOrNull {
            it.musicDescriptionShelfRenderer?.description?.runs
        } ?: immersive?.description?.runs ?: detail?.description?.runs

        val channelId = immersive?.subscriptionButton?.subscribeButtonRenderer?.channelId
        val subscriberCount = immersive?.subscriptionButton?.subscribeButtonRenderer
            ?.longSubscriberCountText?.extractCountText()
            ?: immersive?.subscriptionButton?.subscribeButtonRenderer
                ?.shortSubscriberCountText?.extractCountText()
            ?: immersive?.subscriptionButton2?.subscribeButtonRenderer
                ?.subscriberCountWithSubscribeText?.extractCountText()
        val monthlyListeners = immersive?.monthlyListenerCount?.extractCountText()

        val firstRow = contents.firstNotNullOfOrNull { it.musicShelfRenderer }
            ?.contents.orEmpty().getItems().firstOrNull()
        val playEndpoint = immersive?.playButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint
            ?: firstRow?.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
                ?.playNavigationEndpoint?.watchEndpoint
        val shuffleEndpoint = menuEndpoint(immersive?.menu, "MUSIC_SHUFFLE")
            ?: menuEndpoint(firstRow?.menu, "MUSIC_SHUFFLE")
        val radioEndpoint = menuEndpoint(immersive?.menu, "MIX")
            ?: menuEndpoint(firstRow?.menu, "MIX")

        val artistItem = ArtistItem(
            id = browseId,
            title = title ?: "",
            thumbnail = thumbnail,
            channelId = channelId,
            playEndpoint = playEndpoint,
            shuffleEndpoint = shuffleEndpoint,
            radioEndpoint = radioEndpoint,
        )

        ArtistPage(
            artist = artistItem,
            sections = contents.mapNotNull { ArtistPage.fromSectionListRendererContent(it) },
            description = descriptionRuns?.joinToString("") { it.text },
            subscriberCountText = subscriberCount,
            monthlyListenerCount = monthlyListeners,
            descriptionRuns = descriptionRuns,
        )
    }

    suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> = attempt {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, endpoint.browseId, endpoint.params)
            .body<BrowseResponse>()
        val contents = sectionContents(response)
        val title = headerTitle(response) ?: ""

        val grid = contents.firstNotNullOfOrNull { it.gridRenderer }
        val carousel = contents.firstNotNullOfOrNull { it.musicCarouselShelfRenderer }
        val shelf = contents.firstNotNullOfOrNull { it.musicShelfRenderer }
        val playlistShelf = contents.firstNotNullOfOrNull { it.musicPlaylistShelfRenderer }

        when {
            grid != null -> ArtistItemsPage(
                title,
                grid.items.mapNotNull { item ->
                    item.musicTwoRowItemRenderer?.let { run -> ArtistItemsPage.fromMusicTwoRowItemRenderer(run) }
                },
                grid.continuations.orEmpty().getContinuation(),
            )
            carousel != null -> ArtistItemsPage(
                title,
                carousel.contents.mapNotNull { item ->
                    item.musicTwoRowItemRenderer?.let { run -> ArtistItemsPage.fromMusicTwoRowItemRenderer(run) }
                },
                null,
            )
            shelf != null -> ArtistItemsPage(
                title,
                shelf.contents.orEmpty().getItems().mapNotNull { row ->
                    ArtistItemsPage.fromMusicResponsiveListItemRenderer(row)
                },
                shelf.continuations.orEmpty().getContinuation(),
            )
            playlistShelf != null -> ArtistItemsPage(
                title,
                playlistShelf.contents.mapNotNull { content ->
                    content.musicResponsiveListItemRenderer?.let { row ->
                        ArtistItemsPage.fromMusicResponsiveListItemRenderer(row)
                    }
                },
                playlistShelf.contents.getContinuation(),
            )
            else -> ArtistItemsPage(title, emptyList(), null)
        }
    }

    suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> = attempt {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, continuation = continuation, setLogin = true)
            .body<BrowseResponse>()
        val grid = response.continuationContents?.gridContinuation
        val playlistShelf = response.continuationContents?.musicPlaylistShelfContinuation
        val appended = response.onResponseReceivedActions.orEmpty().firstOrNull()
            ?.appendContinuationItemsAction?.continuationItems

        val items: List<YTItem>
        val next: String?
        when {
            grid != null -> {
                items = grid.items.mapNotNull { item ->
                    item.musicTwoRowItemRenderer?.let { run -> ArtistItemsPage.fromMusicTwoRowItemRenderer(run) }
                }
                next = grid.continuations.orEmpty().getContinuation()
            }
            playlistShelf != null -> {
                items = playlistShelf.contents.mapNotNull { content ->
                    content.musicResponsiveListItemRenderer?.let { row ->
                        ArtistItemsPage.fromMusicResponsiveListItemRenderer(row)
                    }
                }
                next = playlistShelf.continuations.orEmpty().getContinuation()
            }
            else -> {
                val rows = appended.orEmpty()
                items = rows.mapNotNull { content ->
                    content.musicResponsiveListItemRenderer?.let { row ->
                        ArtistItemsPage.fromMusicResponsiveListItemRenderer(row)
                    }
                }
                next = rows.getContinuation()
            }
        }
        ArtistItemsContinuationPage(items, if (items.isEmpty()) null else next)
    }

    // ---- playlists and podcasts -----------------------------------------------------------

    private data class PlaylistHeaderData(
        val title: String?,
        val thumbnail: String?,
        val subtitle: Runs?,
        val secondSubtitle: Runs?,
        val strapline: Runs?,
        val playEndpoint: WatchEndpoint?,
        val menuRenderers: List<Menu.MenuRenderer>,
        val isEditable: Boolean,
    )

    private fun responsiveHeaderData(
        renderer: MusicResponsiveHeaderRenderer,
        editable: Boolean,
    ): PlaylistHeaderData {
        val buttons = renderer.buttons
        return PlaylistHeaderData(
            title = runsText(renderer.title),
            thumbnail = thumbnailUrl(renderer.thumbnail),
            subtitle = renderer.subtitle,
            secondSubtitle = renderer.secondSubtitle,
            strapline = renderer.straplineTextOne,
            playEndpoint = buttons.firstNotNullOfOrNull {
                it.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint
            },
            menuRenderers = buttons.mapNotNull { it.menuRenderer },
            isEditable = editable,
        )
    }

    private fun playlistHeader(response: BrowseResponse): PlaylistHeaderData? {
        response.header?.musicEditablePlaylistDetailHeaderRenderer?.let { editable ->
            editable.header.musicResponsiveHeaderRenderer?.let { return responsiveHeaderData(it, true) }
            editable.header.musicDetailHeaderRenderer?.let { detail ->
                return PlaylistHeaderData(
                    title = runsText(detail.title),
                    thumbnail = thumbnailUrl(detail.thumbnail),
                    subtitle = detail.subtitle,
                    secondSubtitle = detail.secondSubtitle,
                    strapline = null,
                    playEndpoint = detail.menu.menuRenderer.topLevelButtons.orEmpty()
                        .firstNotNullOfOrNull { it.buttonRenderer?.navigationEndpoint?.anyWatchEndpoint },
                    menuRenderers = listOf(detail.menu.menuRenderer),
                    isEditable = true,
                )
            }
        }
        sectionContents(response).firstNotNullOfOrNull { it.musicResponsiveHeaderRenderer }?.let { renderer ->
            return responsiveHeaderData(renderer, false)
        }
        return null
    }

    suspend fun playlist(playlistId: String): Result<PlaylistPage> = attempt {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, "VL$playlistId", setLogin = true)
            .body<BrowseResponse>()
        val secondary = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
        val playlistShelf = secondary?.contents?.firstNotNullOfOrNull { it.musicPlaylistShelfRenderer }
        val musicShelf = secondary?.contents?.firstNotNullOfOrNull { it.musicShelfRenderer }
        val rows = playlistShelf?.contents ?: musicShelf?.contents.orEmpty()

        val header = playlistHeader(response)
        val allPodcast = rows.isNotEmpty() && rows.all { content ->
            content.musicResponsiveListItemRenderer?.musicVideoType == WatchEndpoint.MUSIC_VIDEO_TYPE_PODCAST_EPISODE
        }
        if (header == null || allPodcast) {
            val podcastPage = podcast(playlistId).getOrNull()
            if (podcastPage != null && podcastPage.songs.isNotEmpty()) return@attempt podcastPage
        }

        val songs = rows.mapNotNull { content ->
            content.musicResponsiveListItemRenderer?.let { row ->
                PlaylistPage.fromMusicResponsiveListItemRenderer(row)
            }
        }
        val songsContinuation = playlistShelf?.continuations.orEmpty().getContinuation()
            ?: musicShelf?.continuations.orEmpty().getContinuation()

        val shuffle = header?.menuRenderers.orEmpty()
            .firstNotNullOfOrNull { menuRendererEndpoint(it, "MUSIC_SHUFFLE") }
        val radio = header?.menuRenderers.orEmpty()
            .firstNotNullOfOrNull { menuRendererEndpoint(it, "MIX") }

        val playlistItem = PlaylistItem(
            id = playlistId,
            title = header?.title ?: "",
            author = authorFromRuns(header?.strapline ?: header?.subtitle),
            songCountText = header?.secondSubtitle?.extractCountText(),
            thumbnail = header?.thumbnail,
            playEndpoint = header?.playEndpoint,
            shuffleEndpoint = shuffle,
            radioEndpoint = radio,
            isEditable = header?.isEditable == true,
            isPodcast = false,
        )

        var related = secondary?.contents.orEmpty().drop(1).flatMap { content ->
            val rows = content.musicShelfRenderer?.contents.orEmpty() +
                content.musicPlaylistShelfRenderer?.contents.orEmpty()
            rows.mapNotNull { item ->
                item.musicResponsiveListItemRenderer?.let { row ->
                    RelatedPage.fromMusicResponsiveListItemRenderer(row)
                }
            }
        }
        if (related.isEmpty()) {
            related = response.continuationContents?.sectionListContinuation?.contents.orEmpty()
                .flatMap(::relatedFromContent)
        }

        PlaylistPage(
            playlist = playlistItem,
            songs = songs,
            songsContinuation = songsContinuation,
            continuation = secondary?.continuations.orEmpty().getContinuation(),
            related = related,
        )
    }

    suspend fun podcast(showId: String): Result<PlaylistPage> = attempt {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, PodcastPage.showBrowseId(showId), setLogin = true)
            .body<BrowseResponse>()
        val contents = primarySectionList(response)?.contents.orEmpty()
        val shelf = contents.firstNotNullOfOrNull { it.musicShelfRenderer }
        val header = playlistHeader(response)

        val playlistItem = PlaylistItem(
            id = showId,
            title = header?.title ?: headerTitle(response) ?: "",
            author = authorFromRuns(header?.strapline ?: header?.subtitle),
            songCountText = header?.secondSubtitle?.extractCountText(),
            thumbnail = header?.thumbnail,
            playEndpoint = header?.playEndpoint,
            shuffleEndpoint = null,
            radioEndpoint = null,
            isEditable = false,
            isPodcast = true,
        )

        val songs = shelf?.contents.orEmpty().mapNotNull { content ->
            content.musicMultiRowListItemRenderer?.let { PodcastPage.episode(it) }
                ?: content.musicResponsiveListItemRenderer?.let { PodcastPage.episode(it) }
        }
        PlaylistPage(playlistItem, songs, shelf?.continuations.orEmpty().getContinuation(), null, null)
    }

    suspend fun playlistContinuation(continuation: String): Result<PlaylistContinuationPage> = attempt {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, continuation = continuation, setLogin = true)
            .body<BrowseResponse>()
        val shelfContinuation = response.continuationContents?.musicPlaylistShelfContinuation
        val appended = response.onResponseReceivedActions.orEmpty().firstOrNull()
            ?.appendContinuationItemsAction?.continuationItems
        val musicShelf = response.continuationContents?.musicShelfContinuation

        val songs = mutableListOf<SongItem>()
        shelfContinuation?.contents.orEmpty().forEach { content ->
            content.musicResponsiveListItemRenderer?.let { row ->
                PlaylistPage.fromMusicResponsiveListItemRenderer(row)?.let { item -> songs.add(item) }
            }
        }
        appended.orEmpty().forEach { content ->
            content.musicResponsiveListItemRenderer?.let { row ->
                PlaylistPage.fromMusicResponsiveListItemRenderer(row)?.let { item -> songs.add(item) }
            }
            content.musicMultiRowListItemRenderer?.let { row ->
                PodcastPage.episode(row)?.let { item -> songs.add(item) }
            }
        }
        musicShelf?.contents.orEmpty().forEach { content ->
            content.musicResponsiveListItemRenderer?.let { row ->
                PlaylistPage.fromMusicResponsiveListItemRenderer(row)?.let { item -> songs.add(item) }
            }
            content.musicMultiRowListItemRenderer?.let { row ->
                PodcastPage.episode(row)?.let { item -> songs.add(item) }
            }
        }

        val next = shelfContinuation?.continuations.orEmpty().getContinuation()
            ?: appended.orEmpty().getContinuation()
            ?: musicShelf?.continuations.orEmpty().getContinuation()
        PlaylistContinuationPage(songs, if (songs.isEmpty()) null else next)
    }

    // ---- home, explore, browse, library ---------------------------------------------------

    suspend fun home(
        continuation: String? = null,
        browseId: String = "FEmusic_home",
        params: String? = null,
    ): Result<HomePage> = attempt {
        if (continuation != null) {
            val response = innerTube.browse(YouTubeClient.WEB_REMIX, continuation = continuation, setLogin = true)
                .body<BrowseResponse>()
            val section = response.continuationContents?.sectionListContinuation
            val sections = section?.contents.orEmpty().mapNotNull { content ->
                content.musicCarouselShelfRenderer?.let { HomePage.fromMusicCarouselShelfRenderer(it) }
            }
            HomePage(null, sections, section?.continuations.orEmpty().getContinuation())
                .filterBlockedArtists()
                .filterDislikedSongs()
        } else {
            val response = innerTube.browse(YouTubeClient.WEB_REMIX, browseId, params, setLogin = true)
                .body<BrowseResponse>()
            val list = primarySectionList(response)
            val sections = list?.contents.orEmpty()
                .mapNotNull { content ->
                    content.musicCarouselShelfRenderer?.let { HomePage.fromMusicCarouselShelfRenderer(it) }
                }
                .filterNot { it.isStationListenTogether }
            val chips = list?.header?.chipCloudRenderer?.chips?.mapNotNull {
                HomePage.Chip.fromChipCloudChipRenderer(it)
            }
            HomePage(chips, sections, list?.continuations.orEmpty().getContinuation())
                .filterBlockedArtists()
                .filterDislikedSongs()
        }
    }

    suspend fun explore(): Result<ExplorePage> = attempt {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, "FEmusic_explore", setLogin = true)
            .body<BrowseResponse>()
        val carousels = sectionContents(response).mapNotNull { it.musicCarouselShelfRenderer }
        val newReleases = carousels.firstOrNull { carouselMoreBrowseId(it) == "FEmusic_new_releases_albums" }
            ?.let { carouselAlbumItems(it) }
            .orEmpty()
        val moods = carousels.firstOrNull { carouselMoreBrowseId(it) == "FEmusic_moods_and_genres" }
            ?.contents.orEmpty()
            .mapNotNull { item ->
                item.musicNavigationButtonRenderer?.let { MoodAndGenres.fromMusicNavigationButtonRenderer(it) }
            }
        ExplorePage(newReleases, moods)
    }

    suspend fun newReleaseAlbums(): Result<List<AlbumItem>> = attempt {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, "FEmusic_new_releases_albums", setLogin = true)
            .body<BrowseResponse>()
        sectionContents(response).firstNotNullOfOrNull { it.gridRenderer }?.let { grid ->
            grid.items.mapNotNull { item ->
                item.musicTwoRowItemRenderer?.let { NewReleaseAlbumPage.fromMusicTwoRowItemRenderer(it) }
            }
        }.orEmpty()
    }

    suspend fun moodAndGenres(): Result<List<MoodAndGenres>> = attempt {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, "FEmusic_moods_and_genres", setLogin = true)
            .body<BrowseResponse>()
        val contents = primarySectionList(response)?.contents
            ?: throw InnerTubeException.ParseError("Missing moods and genres content")
        contents.mapNotNull { MoodAndGenres.fromSectionListRendererContent(it) }
    }

    suspend fun browse(browseId: String, params: String?): Result<BrowseResult> = attempt {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, browseId, params, setLogin = true)
            .body<BrowseResponse>()
        val items = sectionContents(response).mapNotNull { content ->
            content.gridRenderer?.let { grid ->
                BrowseResult.Item(
                    runsText(grid.header?.gridHeaderRenderer?.title),
                    grid.items.mapNotNull { item ->
                        item.musicTwoRowItemRenderer?.let { RelatedPage.fromMusicTwoRowItemRenderer(it) }
                    },
                )
            } ?: content.musicCarouselShelfRenderer?.let { carousel ->
                BrowseResult.Item(
                    carouselTitle(carousel),
                    carousel.contents.mapNotNull { item ->
                        item.musicTwoRowItemRenderer?.let { RelatedPage.fromMusicTwoRowItemRenderer(it) }
                    },
                )
            }
        }
        BrowseResult(headerTitle(response), items)
    }

    suspend fun library(browseId: String, tabIndex: Int = 0): Result<LibraryPage> = try {
        Result.success(fetchLibrary(browseId, tabIndex))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: IllegalStateException) {
        if (error.javaClass == IllegalStateException::class.java) Result.failure(error)
        else Result.failure(InnerTubeErrors.classify(error))
    } catch (error: Throwable) {
        Result.failure(InnerTubeErrors.classify(error))
    }

    private suspend fun fetchLibrary(browseId: String, tabIndex: Int): LibraryPage {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, browseId, setLogin = true).body<BrowseResponse>()
        val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.tabs
            ?: response.contents?.twoColumnBrowseResultsRenderer?.tabs
        val sectionList = tabs?.getOrNull(tabIndex)?.tabRenderer?.content?.sectionListRenderer
            ?: primarySectionList(response)
        val contents = sectionList?.contents.orEmpty()

        val grid = contents.firstNotNullOfOrNull { it.gridRenderer }
        if (grid != null) {
            return LibraryPage(
                grid.items.mapNotNull { item ->
                    item.musicTwoRowItemRenderer?.let { LibraryPage.fromMusicTwoRowItemRenderer(it) }
                },
                grid.continuations.orEmpty().getContinuation(),
            )
        }
        val shelf = contents.firstNotNullOfOrNull { it.musicShelfRenderer }
            ?: throw IllegalStateException("No content found for browseId=$browseId")
        return LibraryPage(
            shelf.contents.orEmpty().getItems().mapNotNull { row ->
                LibraryPage.fromMusicResponsiveListItemRenderer(row)
            },
            shelf.continuations.orEmpty().getContinuation(),
        )
    }

    suspend fun libraryContinuation(continuation: String): Result<LibraryContinuationPage> = attempt {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, continuation = continuation, setLogin = true)
            .body<BrowseResponse>()
        val grid = response.continuationContents?.gridContinuation
        if (grid != null) {
            LibraryContinuationPage(
                grid.items.mapNotNull { item ->
                    item.musicTwoRowItemRenderer?.let { LibraryPage.fromMusicTwoRowItemRenderer(it) }
                },
                grid.continuations.orEmpty().getContinuation(),
            )
        } else {
            val shelf = response.continuationContents?.musicShelfContinuation
                ?: throw InnerTubeException.ParseError("Missing library continuation content")
            LibraryContinuationPage(
                shelf.contents.orEmpty().getItems().mapNotNull { row ->
                    LibraryPage.fromMusicResponsiveListItemRenderer(row)
                },
                shelf.continuations.orEmpty().getContinuation(),
            )
        }
    }

    suspend fun libraryRecentActivity(): Result<LibraryPage> = attempt {
        val response = innerTube.browse(
            YouTubeClient.WEB_REMIX,
            continuation = LibraryFilter.FILTER_RECENT_ACTIVITY.value,
            setLogin = true,
        ).body<BrowseResponse>()
        val continuation = response.continuationContents?.sectionListContinuation
        val gridItems = continuation?.contents?.firstOrNull()?.gridRenderer?.items
        if (gridItems.isNullOrEmpty()) return@attempt LibraryPage(emptyList(), null)

        val items = gridItems.mapNotNull { item ->
            item.musicTwoRowItemRenderer?.let { LibraryPage.fromMusicTwoRowItemRenderer(it) }
        }
        val refreshed = items.map { item ->
            if (item is ArtistItem) {
                val fetched = artist(item.id).getOrNull()?.artist
                if (fetched != null) fetched.copy(thumbnail = item.thumbnail) else item
            } else {
                item
            }
        }
        LibraryPage(refreshed, continuation?.continuations.orEmpty().getContinuation())
    }

    suspend fun getChartsPage(continuation: String? = null): Result<ChartsPage> = attempt {
        val response = innerTube.browse(
            YouTubeClient.WEB_REMIX,
            "FEmusic_charts",
            params = "ggMGCgQIgAQ%3D",
            continuation = continuation,
            setLogin = true,
        ).body<BrowseResponse>()
        val list = primarySectionList(response)
        val sections = list?.contents.orEmpty().mapNotNull { content ->
            content.gridRenderer?.let { grid ->
                val items = grid.items.mapNotNull { item ->
                    item.musicTwoRowItemRenderer?.let { NewReleaseAlbumPage.fromMusicTwoRowItemRenderer(it) }
                }
                if (items.isEmpty()) null else ChartSection(
                    runsText(grid.header?.gridHeaderRenderer?.title) ?: "",
                    items,
                    ChartType.NEW_RELEASES,
                )
            } ?: content.musicCarouselShelfRenderer?.let { carousel ->
                val title = carouselTitle(carousel) ?: return@let null
                val items = carousel.contents.mapNotNull { item ->
                    item.musicTwoRowItemRenderer?.let { RelatedPage.fromMusicTwoRowItemRenderer(it) }
                        ?: item.musicResponsiveListItemRenderer?.let { chartResponsiveItem(it) }
                }
                if (items.isEmpty()) null else ChartSection(title, items, chartTypeOf(title))
            }
        }
        val next = response.continuationContents?.sectionListContinuation?.continuations.orEmpty().getContinuation()
            ?: list?.continuations.orEmpty().getContinuation()
        ChartsPage(sections, next)
    }

    suspend fun musicHistory(): Result<HistoryPage> = attempt {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, "FEmusic_history", setLogin = true)
            .body<BrowseResponse>()
        val contents = primarySectionList(response)?.contents ?: return@attempt HistoryPage(null)
        HistoryPage(
            contents.mapNotNull { content ->
                content.musicShelfRenderer?.let { shelf ->
                    runCatching { HistoryPage.fromMusicShelfRenderer(shelf) }.getOrNull()
                }
            },
        )
    }

    // ---- account and write actions --------------------------------------------------------

    suspend fun likeVideo(videoId: String, like: Boolean): Result<HttpResponse> = attempt {
        if (like) innerTube.likeVideo(YouTubeClient.WEB_REMIX, videoId)
        else innerTube.unlikeVideo(YouTubeClient.WEB_REMIX, videoId)
    }

    suspend fun likePlaylist(playlistId: String, like: Boolean): Result<HttpResponse> = attempt {
        if (like) innerTube.likePlaylist(YouTubeClient.WEB_REMIX, playlistId)
        else innerTube.unlikePlaylist(YouTubeClient.WEB_REMIX, playlistId)
    }

    suspend fun subscribeChannel(channelId: String, subscribe: Boolean): Result<HttpResponse> = attempt {
        if (subscribe) innerTube.subscribeChannel(YouTubeClient.WEB_REMIX, channelId)
        else innerTube.unsubscribeChannel(YouTubeClient.WEB_REMIX, channelId)
    }

    suspend fun getChannelId(browseId: String): String =
        artist(browseId).getOrNull()?.artist?.channelId ?: ""

    suspend fun addToPlaylist(playlistId: String, videoId: String): Result<HttpResponse> =
        attempt { innerTube.addToPlaylist(YouTubeClient.WEB_REMIX, playlistId.removePrefix("VL"), videoId) }

    suspend fun addPlaylistToPlaylist(playlistId: String, addPlaylistId: String): Result<HttpResponse> =
        attempt { innerTube.addPlaylistToPlaylist(YouTubeClient.WEB_REMIX, playlistId.removePrefix("VL"), addPlaylistId) }

    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String): Result<HttpResponse> =
        attempt { innerTube.removeFromPlaylist(YouTubeClient.WEB_REMIX, playlistId.removePrefix("VL"), videoId, setVideoId) }

    suspend fun moveSongPlaylist(
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
    ): Result<HttpResponse> =
        attempt { innerTube.moveSongPlaylist(YouTubeClient.WEB_REMIX, playlistId, setVideoId, successorSetVideoId) }

    fun createPlaylist(title: String): String = runBlocking {
        innerTube.createPlaylist(YouTubeClient.WEB_REMIX, title).body<CreatePlaylistResponse>().playlistId
    }

    suspend fun renamePlaylist(playlistId: String, name: String): Result<HttpResponse> =
        attempt { innerTube.renamePlaylist(YouTubeClient.WEB_REMIX, playlistId, name) }

    suspend fun uploadCustomThumbnailLink(playlistId: String, image: ByteArray): Result<String?> = attempt {
        val start = innerTube.getUploadCustomThumbnailLink(YouTubeClient.WEB_REMIX, image.size)
        val uploadId = start.headers["x-guploader-uploadid"]
            ?: throw InnerTubeException.ParseError("Missing upload id")
        val blob = innerTube.uploadCustomThumbnail(YouTubeClient.WEB_REMIX, uploadId, image)
            .body<ImageUploadResponse>().encryptedBlobId
        val edit = innerTube.setThumbnailPlaylist(YouTubeClient.WEB_REMIX, playlistId, blob)
            .body<EditPlaylistResponse>()
        headerThumbnail(edit.newHeader)
    }

    suspend fun removeThumbnailPlaylist(playlistId: String): Result<String?> = attempt {
        val edit = innerTube.removeThumbnailPlaylist(YouTubeClient.WEB_REMIX, playlistId)
            .body<EditPlaylistResponse>()
        headerThumbnail(edit.newHeader)
    }

    suspend fun deletePlaylist(playlistId: String): Result<HttpResponse> =
        attempt { innerTube.deletePlaylist(YouTubeClient.WEB_REMIX, playlistId) }

    suspend fun feedback(tokens: List<String>): Result<Boolean> = attempt {
        innerTube.feedback(YouTubeClient.WEB_REMIX, tokens).body<FeedbackResponse>()
            .feedbackResponses.all { it.isProcessed }
    }

    private suspend fun setLibrary(videoId: String, add: Boolean): Result<Boolean> = attempt {
        val result = next(WatchEndpoint(videoId = videoId)).getOrThrow()
        val song = result.items.firstOrNull { it.id == videoId }
            ?: throw InnerTubeException.ParseError("Song not found in queue")
        val token = (if (add) song.libraryAddToken else song.libraryRemoveToken)
            ?: throw InnerTubeException.ParseError("Library token unavailable")
        feedback(listOf(token)).getOrThrow()
    }

    suspend fun addSongToLibrary(videoId: String): Result<Boolean> = setLibrary(videoId, true)

    suspend fun removeSongFromLibrary(videoId: String): Result<Boolean> = setLibrary(videoId, false)

    suspend fun toggleSongLibrary(videoId: String, addToLibrary: Boolean): Result<Boolean> =
        if (addToLibrary) addSongToLibrary(videoId) else removeSongFromLibrary(videoId)

    suspend fun accountInfo(): Result<AccountInfo> = attempt {
        val response = innerTube.accountMenu(YouTubeClient.WEB_REMIX).body<AccountMenuResponse>()
        response.actions.firstOrNull()?.openPopupAction?.popup?.multiPageMenuRenderer
            ?.header?.activeAccountHeaderRenderer?.toAccountInfo()
            ?: throw InnerTubeException.ParseError("Account header unavailable")
    }

    suspend fun visitorData(): Result<String> = attempt {
        val text = innerTube.getSwJsData().bodyAsText().drop(5)
        val root = innertubeJson.parseToJsonElement(text).jsonArray
        val nested = root.getOrNull(0)?.jsonArray
            ?: throw InnerTubeException.ParseError("Visitor data malformed")
        val candidates = nested.getOrNull(2)?.jsonArray
            ?: throw InnerTubeException.ParseError("Visitor data malformed")
        candidates.firstOrNull { element ->
            element is JsonPrimitive && element.isString && VISITOR_DATA_REGEX.containsMatchIn(element.content)
        }?.jsonPrimitive?.content
            ?: throw InnerTubeException.ParseError("Visitor data missing")
    }

    suspend fun refreshVisitorData(): Result<String> =
        visitorData().onSuccess { visitorData = it }

    fun clearGuestSession() {
        visitorData = null
        dataSyncId = null
    }

    // ---- playback, queue and transcript ---------------------------------------------------

    suspend fun player(
        videoId: String,
        playlistId: String? = null,
        client: YouTubeClient,
        signatureTimestamp: Int? = null,
        poToken: String? = null,
    ): Result<PlayerResponse> = attempt {
        innerTube.player(client, videoId, playlistId, signatureTimestamp, poToken).body<PlayerResponse>()
    }

    fun newPlaybackNonce(): String = buildString(16) {
        repeat(16) { append(NONCE_ALPHABET[Random.nextInt(NONCE_ALPHABET.length)]) }
    }

    suspend fun reportWatchtime(
        watchtimeUrl: String,
        cpn: String,
        startSeconds: Double,
        positionSeconds: Double,
        lengthSeconds: Double,
        elapsedSeconds: Double,
        paused: Boolean,
    ): Result<Int> = attempt {
        val url = watchtimeUrl.replace("https://s.youtube.com", "https://music.youtube.com")
        innerTube.reportWatchtime(
            url = url,
            cpn = cpn,
            startSeconds = startSeconds,
            positionSeconds = positionSeconds,
            lengthSeconds = lengthSeconds,
            elapsedSeconds = elapsedSeconds,
            state = if (paused) "paused" else "playing",
        ).status.value
    }

    suspend fun registerPlayback(
        playlistId: String? = null,
        playbackTracking: String,
        cpn: String = newPlaybackNonce(),
    ): Result<HttpResponse> = attempt {
        val url = playbackTracking.replace("https://s.youtube.com", "https://music.youtube.com")
        innerTube.registerPlayback(url, cpn, playlistId)
    }

    suspend fun next(endpoint: WatchEndpoint, continuation: String? = null): Result<NextResult> = attempt {
        val response = innerTube.next(
            YouTubeClient.WEB_REMIX,
            endpoint.videoId,
            endpoint.playlistId,
            endpoint.playlistSetVideoId,
            endpoint.index,
            endpoint.params,
            continuation,
        ).body<NextResponse>()

        val tabs = response.contents.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer
            ?.watchNextTabbedResultsRenderer?.tabs.orEmpty()
        val panel = response.continuationContents?.playlistPanelContinuation
            ?: tabs.firstOrNull()?.tabRenderer?.content?.musicQueueRenderer?.content?.playlistPanelRenderer
            ?: throw InnerTubeException.ParseError("Missing playlist panel")

        var items = panel.contents.mapNotNull { content ->
            content.playlistPanelVideoRenderer?.let { NextPage.fromPlaylistPanelVideoRenderer(it) }
        }
        val title = tabs.firstOrNull()?.tabRenderer?.content?.musicQueueRenderer?.header
            ?.musicQueueHeaderRenderer?.subtitle?.runs?.firstOrNull()?.text
        val currentIndex = panel.contents
            .indexOfFirst { it.playlistPanelVideoRenderer?.selected == true }
            .takeIf { it >= 0 }
        val lyricsEndpoint = tabs.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint
        val relatedEndpoint = tabs.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint

        var finalEndpoint = endpoint
        val automix = panel.contents.lastOrNull()?.automixPreviewVideoRenderer
            ?.content?.automixPlaylistVideoRenderer?.navigationEndpoint?.watchPlaylistEndpoint
        if (automix != null) {
            val automixResponse = innerTube.next(
                YouTubeClient.WEB_REMIX,
                automix.videoId,
                automix.playlistId,
                automix.playlistSetVideoId,
                automix.index,
                automix.params,
                null,
            ).body<NextResponse>()
            val automixTabs = automixResponse.contents.singleColumnMusicWatchNextResultsRenderer
                ?.tabbedRenderer?.watchNextTabbedResultsRenderer?.tabs.orEmpty()
            val automixPanel = automixResponse.continuationContents?.playlistPanelContinuation
                ?: automixTabs.firstOrNull()?.tabRenderer?.content?.musicQueueRenderer
                    ?.content?.playlistPanelRenderer
            val automixItems = automixPanel?.contents.orEmpty().mapNotNull { content ->
                content.playlistPanelVideoRenderer?.let { NextPage.fromPlaylistPanelVideoRenderer(it) }
            }
            items = items + automixItems
            finalEndpoint = automix
        }

        NextResult(
            title = title,
            items = items.filterBlockedArtists().filterDislikedSongs(),
            currentIndex = currentIndex,
            lyricsEndpoint = lyricsEndpoint,
            relatedEndpoint = relatedEndpoint,
            continuation = panel.continuations.orEmpty().getContinuation(),
            endpoint = finalEndpoint,
        )
    }

    suspend fun fetchTasteSignals(videoId: String): Result<TasteSignals> = attempt {
        val response = innerTube.next(YouTubeClient.WEB_REMIX, videoId, null, null, null, null, null)
            .body<NextResponse>()
        parseTasteSignals(videoId, response)
    }

    fun parseTasteSignals(videoId: String, response: NextResponse): TasteSignals {
        val labels = mutableListOf<String>()
        val tabs = response.contents.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer
            ?.watchNextTabbedResultsRenderer?.tabs.orEmpty()

        tabs.forEach { tab ->
            val renderer = tab.tabRenderer
            val sectionList = renderer.content?.sectionListRenderer
            sectionList?.header?.chipCloudRenderer?.chips.orEmpty().forEach { chip ->
                chip.chipCloudChipRenderer.text?.runs?.forEach { run -> labels += run.text }
            }
            sectionList?.contents.orEmpty().forEach { content ->
                content.musicShelfRenderer?.title?.runs?.forEach { run -> labels += run.text }
                content.musicCarouselShelfRenderer?.let { carousel ->
                    carousel.header?.musicCarouselShelfBasicHeaderRenderer?.let { header ->
                        header.title.runs?.forEach { run -> labels += run.text }
                        header.strapline?.runs?.forEach { run -> labels += run.text }
                    }
                    carousel.contents.forEach { item ->
                        item.musicResponsiveListItemRenderer?.let { row ->
                            row.flexColumns.forEach { column ->
                                column.musicResponsiveListItemFlexColumnRenderer.text?.runs
                                    ?.forEach { run -> labels += run.text }
                            }
                        }
                    }
                }
                content.itemSectionRenderer?.contents.orEmpty().forEach { item ->
                    item.musicResponsiveListItemRenderer?.let { row ->
                        row.flexColumns.forEach { column ->
                            column.musicResponsiveListItemFlexColumnRenderer.text?.runs
                                ?.forEach { run -> labels += run.text }
                        }
                    }
                }
            }
            val header = renderer.content?.musicQueueRenderer?.header?.musicQueueHeaderRenderer
            header?.title?.runs?.forEach { run -> labels += run.text }
            header?.subtitle?.runs?.forEach { run -> labels += run.text }
        }

        val genres = mutableListOf<String>()
        val moods = mutableListOf<String>()
        for (label in labels) {
            val trimmed = label.trim()
            if (trimmed.isEmpty()) continue
            val lower = trimmed.lowercase(Locale.ROOT)
            val genre = GENRE_PATTERNS.firstOrNull { it.second.containsMatchIn(lower) }?.first
            if (genre != null && genre !in genres) genres += genre
            val mood = MOOD_PATTERNS.firstOrNull { it.second.containsMatchIn(lower) }?.first
            if (mood != null && mood !in moods) moods += mood
            if (genre == null && mood == null && lower.length in 3..25 &&
                !lower.contains("http") &&
                (lower.contains("mix") || lower.contains("radio") || lower.contains("station"))
            ) {
                val stripped = trimmed.replace(Regex("(?i)\\b(mix|radio|station)\\b"), "").trim()
                if (stripped.isNotEmpty() && stripped !in moods) moods += stripped
            }
        }
        return TasteSignals(videoId, genres.distinct(), moods.distinct())
    }

    suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?> = attempt {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, endpoint.browseId, endpoint.params)
            .body<BrowseResponse>()
        sectionContents(response).firstNotNullOfOrNull { it.musicDescriptionShelfRenderer?.description?.runs }
            ?.joinToString("") { it.text }
    }

    suspend fun related(endpoint: BrowseEndpoint): Result<RelatedPage> = attempt {
        val response = innerTube.browse(YouTubeClient.WEB_REMIX, endpoint.browseId).body<BrowseResponse>()
        val songs = mutableListOf<SongItem>()
        val albums = mutableListOf<AlbumItem>()
        val artists = mutableListOf<ArtistItem>()
        val playlists = mutableListOf<PlaylistItem>()

        fun accept(item: YTItem?) {
            when (item) {
                is SongItem -> songs += item
                is AlbumItem -> albums += item
                is ArtistItem -> artists += item
                is PlaylistItem -> playlists += item
                else -> Unit
            }
        }

        sectionContents(response).forEach { content ->
            content.musicCarouselShelfRenderer?.contents.orEmpty().forEach { item ->
                item.musicTwoRowItemRenderer?.let { accept(RelatedPage.fromMusicTwoRowItemRenderer(it)) }
                item.musicResponsiveListItemRenderer?.let { row ->
                    if (row.musicVideoType == WatchEndpoint.MUSIC_VIDEO_TYPE_ATV) {
                        accept(RelatedPage.fromMusicResponsiveListItemRenderer(row))
                    }
                }
            }
            content.musicShelfRenderer?.contents.orEmpty().forEach { item ->
                item.musicResponsiveListItemRenderer?.let { row ->
                    if (row.musicVideoType == WatchEndpoint.MUSIC_VIDEO_TYPE_ATV) {
                        accept(RelatedPage.fromMusicResponsiveListItemRenderer(row))
                    }
                }
            }
            content.itemSectionRenderer?.contents.orEmpty().forEach { item ->
                item.musicResponsiveListItemRenderer?.let { row ->
                    if (row.musicVideoType == WatchEndpoint.MUSIC_VIDEO_TYPE_ATV) {
                        accept(RelatedPage.fromMusicResponsiveListItemRenderer(row))
                    }
                }
            }
        }
        RelatedPage(songs, albums, artists, playlists)
    }

    suspend fun queue(videoIds: List<String>? = null, playlistId: String? = null): Result<List<SongItem>> {
        if (videoIds != null && videoIds.size > MAX_GET_QUEUE_SIZE) {
            return Result.failure(
                InnerTubeException.IllegalArgumentError("At most $MAX_GET_QUEUE_SIZE video ids may be queued"),
            )
        }
        return attempt {
            innerTube.getQueue(YouTubeClient.WEB_REMIX, videoIds, playlistId).body<GetQueueResponse>()
                .queueDatas.mapNotNull { data ->
                    data.content.playlistPanelVideoRenderer?.let { NextPage.fromPlaylistPanelVideoRenderer(it) }
                }
        }
    }

    suspend fun transcript(videoId: String): Result<String> = attempt {
        val response = innerTube.getTranscript(YouTubeClient.WEB, videoId).body<GetTranscriptResponse>()
        val body = response.actions.orEmpty()
            .mapNotNull {
                it.updateEngagementPanelAction?.content?.transcriptRenderer?.body?.transcriptBodyRenderer
            }
            .firstOrNull()
            ?: throw InnerTubeException.ParseError("Transcript body missing")
        val groups = body.cueGroups
        if (groups.isEmpty()) throw InnerTubeException.ParseError("Transcript cues missing")
        groups.joinToString("\n") { group ->
            val cue = group.transcriptCueGroupRenderer?.cues?.firstOrNull()?.transcriptCueRenderer
                ?: throw InnerTubeException.ParseError("Transcript cue missing")
            val text = cue.cue.simpleText.trim { it == '♪' || it.isWhitespace() }
            val offset = cue.startOffsetMs
            String.format(
                Locale.ROOT,
                "[%02d:%02d.%03d]%s",
                offset / 60_000,
                (offset / 1_000) % 60,
                offset % 1_000,
                text,
            )
        }
    }

    // ---- media info and NewPipe bridge ----------------------------------------------------

    suspend fun getMediaInfo(videoId: String): Result<MediaInfo> = innerTube.getMediaInfo(videoId)

    fun getNewPipeStreamUrls(videoId: String): List<Pair<Int, String>> =
        NewPipeExtractor.newPipePlayer(videoId)

    suspend fun newPipePlayer(videoId: String, tempRes: PlayerResponse): PlayerResponse? {
        if (tempRes.playabilityStatus.status != "OK") return null
        val urls = getNewPipeStreamUrls(videoId)
        if (urls.isEmpty()) return null
        val streaming = tempRes.streamingData ?: return null
        val byItag = urls.toMap()
        val formats = streaming.formats?.map { it.copy(url = byItag[it.itag] ?: it.url) }
        val adaptive = streaming.adaptiveFormats.map { it.copy(url = byItag[it.itag] ?: it.url) }
        val matched = formats.orEmpty().any { byItag.containsKey(it.itag) } ||
            adaptive.any { byItag.containsKey(it.itag) }
        if (!matched) return null
        return tempRes.copy(
            streamingData = streaming.copy(formats = formats, adaptiveFormats = adaptive),
        )
    }

    // ---- comments -------------------------------------------------------------------------

    suspend fun comments(videoId: String): Result<Pair<List<CommentThreadRenderer>, String?>> = attempt {
        val response = innerTube.next(YouTubeClient.WEB, videoId, null, null, null, null, null)
            .body<NextResponse>()
        val token = commentContinuationToken(response)
            ?: throw InnerTubeException.ParseError("Comment continuation token missing")
        commentContinuation(token).getOrThrow()
    }

    private fun commentContinuationToken(response: NextResponse): String? {
        val contents = response.contents.twoColumnWatchNextResults?.results?.results?.contents.orEmpty()
        contents.firstNotNullOfOrNull {
            it?.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
        }?.let { return it }
        contents.firstNotNullOfOrNull { content ->
            content?.itemSectionRenderer?.contents?.firstNotNullOfOrNull {
                it?.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
            }
        }?.let { return it }
        return response.engagementPanels.orEmpty()
            .firstOrNull {
                it.engagementPanelSectionListRenderer?.panelIdentifier == "engagement-panel-comments-section"
            }
            ?.engagementPanelSectionListRenderer?.content?.sectionListRenderer?.contents
            ?.firstNotNullOfOrNull {
                it?.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
            }
    }

    private suspend fun fetchCommentPage(continuation: String): CommentResponse =
        innerTube.next(YouTubeClient.WEB, null, null, null, null, null, continuation).body()

    suspend fun commentContinuation(
        continuationToken: String,
    ): Result<Pair<List<CommentThreadRenderer>, String?>> = attempt {
        val response = fetchCommentPage(continuationToken)
        val items = response.onResponseReceivedEndpoints.orEmpty().flatMap { endpoint ->
            endpoint.reloadContinuationItemsCommand?.continuationItems.orEmpty() +
                endpoint.appendContinuationItemsAction?.continuationItems.orEmpty()
        }
        val mutations = response.frameworkUpdates?.entityBatchUpdate?.mutations.orEmpty()
        val entities = mutations.mapNotNull { it.payload?.commentEntityPayload }
        val toolbarStates = mutations.mapNotNull { it.payload?.engagementToolbarStateEntityPayload }
        val toolbarSurfaces = mutations.mapNotNull { it.payload?.engagementToolbarSurfaceEntityPayload }

        val threads = items.mapNotNull { item ->
            val thread = item.commentThreadRenderer ?: return@mapNotNull null
            val legacy = thread.comment?.commentRenderer
            val legacyId = legacy?.commentId
            val key = thread.commentViewModel?.commentViewModel?.commentKey
            val payload = entities.firstOrNull { it.key != null && it.key == key }
                ?: entities.firstOrNull { candidate ->
                    legacyId != null && candidate.properties?.commentId == legacyId
                }
            val state = toolbarStates.firstOrNull { it.key == payload?.properties?.toolbarStateKey }
            val surface = toolbarSurfaces.firstOrNull { it.key == payload?.properties?.toolbarSurfaceKey }
            val liked = state?.likeState == "TOOLBAR_LIKE_STATE_LIKE"
            val likeCount = (if (liked) payload?.toolbar?.likeCountLiked else payload?.toolbar?.likeCountNotliked)
                ?: (if (liked) surface?.toolbar?.likeCountLiked else surface?.toolbar?.likeCountNotliked)
                ?: "0"
            val replyCount = payload?.toolbar?.replyCount ?: surface?.toolbar?.replyCount ?: 0
            val author = payload?.author?.displayName
            val avatar = payload?.author?.avatarThumbnailUrl
            val content = payload?.properties?.content?.content
            val published = payload?.properties?.publishedTime
            val merged = CommentRenderer(
                authorText = author?.let { Runs(listOf(Run(it, null))) } ?: legacy?.authorText,
                authorThumbnail = avatar?.let {
                    Thumbnails(listOf(Thumbnail(it, null, null)))
                } ?: legacy?.authorThumbnail,
                contentText = content?.let { Runs(listOf(Run(it, null))) } ?: legacy?.contentText,
                publishedTimeText = published?.let { Runs(listOf(Run(it, null))) } ?: legacy?.publishedTimeText,
                authorEndpoint = legacy?.authorEndpoint,
                commentId = legacy?.commentId ?: payload?.properties?.commentId,
                voteCount = Runs(listOf(Run(likeCount, null))),
                voteStatus = if (liked) "UPVOTE" else "INDIFFERENT",
                replyCount = replyCount,
            )
            CommentThreadRenderer(
                comment = Comment(merged),
                commentViewModel = thread.commentViewModel,
                replies = thread.replies,
            )
        }
        val next = items.firstNotNullOfOrNull {
            it.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
        }
        threads to next
    }

    suspend fun commentReplies(
        replyToken: String,
    ): Result<Pair<List<CommentRenderer>, String?>> = attempt {
        val response = fetchCommentPage(replyToken)
        val items = response.onResponseReceivedEndpoints.orEmpty().flatMap { endpoint ->
            endpoint.reloadContinuationItemsCommand?.continuationItems.orEmpty() +
                endpoint.appendContinuationItemsAction?.continuationItems.orEmpty()
        }
        val mutations = response.frameworkUpdates?.entityBatchUpdate?.mutations.orEmpty()
        val entities = mutations.mapNotNull { it.payload?.commentEntityPayload }
        val toolbarStates = mutations.mapNotNull { it.payload?.engagementToolbarStateEntityPayload }
        val toolbarSurfaces = mutations.mapNotNull { it.payload?.engagementToolbarSurfaceEntityPayload }

        val replies = items.mapNotNull { it.commentRenderer }.map { legacy ->
            val legacyId = legacy.commentId
            val payload = entities.firstOrNull { candidate ->
                legacyId != null && candidate.properties?.commentId == legacyId
            } ?: entities.firstOrNull { it.key != null && it.key == legacyId }
            if (payload == null) return@map legacy
            val state = toolbarStates.firstOrNull { it.key == payload.properties?.toolbarStateKey }
            val surface = toolbarSurfaces.firstOrNull { it.key == payload.properties?.toolbarSurfaceKey }
            val liked = state?.likeState == "TOOLBAR_LIKE_STATE_LIKE"
            val likeCount = (if (liked) payload.toolbar?.likeCountLiked else payload.toolbar?.likeCountNotliked)
                ?: (if (liked) surface?.toolbar?.likeCountLiked else surface?.toolbar?.likeCountNotliked)
            val replyCount = payload.toolbar?.replyCount ?: surface?.toolbar?.replyCount
            legacy.copy(
                voteCount = likeCount?.let { Runs(listOf(Run(it, null))) } ?: legacy.voteCount,
                voteStatus = if (liked) "UPVOTE" else "INDIFFERENT",
                replyCount = replyCount ?: legacy.replyCount,
            )
        }

        val next = items.firstNotNullOfOrNull { item ->
            val holder = item.continuationItemRenderer ?: return@firstNotNullOfOrNull null
            holder.continuationEndpoint?.continuationCommand?.token
                ?: holder.button?.buttonRenderer?.command?.continuationCommand?.token
                ?: holder.button?.buttonRenderer?.navigationEndpoint?.continuationCommand?.token
        }
        replies to next
    }

    // ---- constants ------------------------------------------------------------------------

    private val VISITOR_DATA_REGEX = Regex("^Cg[t|s]")
    private const val NONCE_ALPHABET =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

    private val GENRE_VOCABULARY = listOf(
        "pop", "rock", "hip hop", "rap", "r&b", "jazz", "blues", "classical", "country",
        "electronic", "dance", "edm", "metal", "punk", "reggae", "latin", "soul", "funk", "folk",
        "indie", "alternative", "k-pop", "j-pop", "gospel", "soundtrack", "lo-fi", "house",
        "techno", "trap", "drill", "afrobeat", "salsa", "ambient", "opera", "bluegrass", "disco",
        "dubstep", "world", "new age",
    )

    private val MOOD_VOCABULARY = listOf(
        "chill", "happy", "sad", "energetic", "relaxing", "calm", "focus", "study", "sleep",
        "workout", "party", "romantic", "love", "angry", "dark", "uplifting", "melancholy",
        "peaceful", "upbeat", "nostalgic", "motivational", "summer", "winter", "christmas",
        "halloween", "feel good", "commute", "meditation",
    )

    private fun patterns(vocabulary: List<String>): List<Pair<String, Regex>> = vocabulary.map { word ->
        word to Regex(
            "(?<![\\p{L}\\p{N}])${Regex.escape(word)}(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE,
        )
    }

    private val GENRE_PATTERNS = patterns(GENRE_VOCABULARY)
    private val MOOD_PATTERNS = patterns(MOOD_VOCABULARY)
}
