package com.revenge.innertube

import com.revenge.innertube.models.AccountInfo
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.ArtistItem
import com.revenge.innertube.models.BrowseEndpoint
import com.revenge.innertube.models.GridRenderer
import com.revenge.innertube.models.IpVersion
import com.revenge.innertube.models.MediaInfo
import com.revenge.innertube.models.MusicCarouselShelfRenderer
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.MusicShelfRenderer
import com.revenge.innertube.models.MusicTwoRowItemRenderer
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.Run
import com.revenge.innertube.models.SearchSuggestions
import com.revenge.innertube.models.SectionListRenderer
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.TasteSignals
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.revenge.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_PODCAST_EPISODE
import com.revenge.innertube.models.YTItem
import com.revenge.innertube.models.YouTubeClient
import com.revenge.innertube.models.YouTubeClient.Companion.WEB
import com.revenge.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.revenge.innertube.models.YouTubeLocale
import com.revenge.innertube.models.comment.CommentResponse
import com.revenge.innertube.models.comment.CommentThreadRenderer
import com.revenge.innertube.models.extractCountText
import com.revenge.innertube.models.filterBlockedArtists
import com.revenge.innertube.models.filterDislikedSongs
import com.revenge.innertube.models.getContinuation
import com.revenge.innertube.models.getItems
import com.revenge.innertube.models.oddElements
import com.revenge.innertube.models.response.AccountMenuResponse
import com.revenge.innertube.models.response.BrowseResponse
import com.revenge.innertube.models.response.CreatePlaylistResponse
import com.revenge.innertube.models.response.EditPlaylistResponse
import com.revenge.innertube.models.response.FeedbackResponse
import com.revenge.innertube.models.response.GetQueueResponse
import com.revenge.innertube.models.response.GetSearchSuggestionsResponse
import com.revenge.innertube.models.response.GetTranscriptResponse
import com.revenge.innertube.models.response.ImageUploadResponse
import com.revenge.innertube.models.response.NextResponse
import com.revenge.innertube.models.response.PlayerResponse
import com.revenge.innertube.models.response.SearchResponse
import com.revenge.innertube.pages.AlbumPage
import com.revenge.innertube.pages.ArtistItemsContinuationPage
import com.revenge.innertube.pages.ArtistItemsPage
import com.revenge.innertube.pages.ArtistPage
import com.revenge.innertube.pages.BrowseResult
import com.revenge.innertube.pages.ChartsPage
import com.revenge.innertube.pages.ExplorePage
import com.revenge.innertube.pages.HistoryPage
import com.revenge.innertube.pages.HomePage
import com.revenge.innertube.pages.LibraryContinuationPage
import com.revenge.innertube.pages.LibraryPage
import com.revenge.innertube.pages.MoodAndGenres
import com.revenge.innertube.pages.NewReleaseAlbumPage
import com.revenge.innertube.pages.NextPage
import com.revenge.innertube.pages.NextResult
import com.revenge.innertube.pages.PlaylistContinuationPage
import com.revenge.innertube.pages.PlaylistPage
import com.revenge.innertube.pages.PodcastPage
import com.revenge.innertube.pages.RelatedPage
import com.revenge.innertube.pages.SearchPage
import com.revenge.innertube.pages.SearchResult
import com.revenge.innertube.pages.SearchSuggestionPage
import com.revenge.innertube.pages.SearchSummary
import com.revenge.innertube.pages.SearchSummaryPage
import com.revenge.metadata.cleaner.ApiText
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import java.net.Proxy
import java.text.Normalizer
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * The YouTube Music facade: sends requests through [InnerTube] and turns the JSON into the app's
 * models and page objects. Every method returns a [Result] so callers never see raw exceptions.
 */
object YouTube {
  var blockedArtists: Set<String> = emptySet()
  var dislikedSongs: Set<String> = emptySet()

  private val innerTube = InnerTube()

  var locale: YouTubeLocale
    get() = innerTube.locale
    set(value) {
      innerTube.locale = value
    }

  var visitorData: String?
    get() = innerTube.visitorData
    set(value) {
      innerTube.visitorData = value
    }

  var dataSyncId: String?
    get() = innerTube.dataSyncId
    set(value) {
      innerTube.dataSyncId = value
    }

  var cookie: String?
    get() = innerTube.cookie
    set(value) {
      innerTube.cookie = value
    }

  var proxy: Proxy?
    get() = innerTube.proxy
    set(value) {
      innerTube.proxy = value
    }

  var proxyAuth: String?
    get() = innerTube.proxyAuth
    set(value) {
      innerTube.proxyAuth = value
    }

  var useLoginForBrowse: Boolean
    get() = innerTube.useLoginForBrowse
    set(value) {
      innerTube.useLoginForBrowse = value
    }

  var ipVersion: IpVersion
    get() = innerTube.ipVersion
    set(value) {
      innerTube.ipVersion = value
    }

  suspend fun refreshVisitorData(): Result<String> = visitorData().onSuccess { visitorData = it }

  fun clearGuestSession() {
    visitorData = null
    dataSyncId = null
  }

  suspend fun searchSuggestions(query: String): Result<SearchSuggestions> = runCatching {
    val response =
      innerTube.getSearchSuggestions(WEB_REMIX, query).body<GetSearchSuggestionsResponse>()
    SearchSuggestions(
      queries =
        response.contents
          ?.getOrNull(0)
          ?.searchSuggestionsSectionRenderer
          ?.contents
          ?.mapNotNull { content ->
            content.searchSuggestionRenderer?.suggestion?.runs?.joinToString("") { it.text }
          }
          .orEmpty(),
      recommendedItems =
        response.contents
          ?.getOrNull(1)
          ?.searchSuggestionsSectionRenderer
          ?.contents
          ?.mapNotNull {
            it.musicResponsiveListItemRenderer?.let(SearchSuggestionPage::fromMusicResponsiveListItemRenderer)
          }
          .orEmpty()
          .filterBlockedArtists()
          .filterDislikedSongs(),
    )
  }

  suspend fun searchSummary(query: String): Result<SearchSummaryPage> = runCatching {
    val response = innerTube.search(WEB_REMIX, query).body<SearchResponse>()
    val contents =
      response.contents
        ?.tabbedSearchResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        .orEmpty()

    val shelves =
      contents.mapNotNull { content ->
        if (content.musicCardShelfRenderer != null) {
          val card = content.musicCardShelfRenderer
          val items =
            listOfNotNull(SearchSummaryPage.fromMusicCardShelfRenderer(card))
              .plus(
                card.contents
                  ?.mapNotNull { it.musicResponsiveListItemRenderer }
                  ?.mapNotNull(SearchSummaryPage::fromMusicResponsiveListItemRenderer)
                  .orEmpty(),
              )
              .distinctBy { it.id }
              .ifEmpty { null } ?: return@mapNotNull null
          SearchSummary(
            title =
              card.header?.musicCardShelfHeaderBasicRenderer?.title?.runs?.firstOrNull()?.text
                ?: YouTubeConstants.DEFAULT_TOP_RESULT,
            items = items,
          )
        } else if (content.musicShelfRenderer != null) {
          val shelf = content.musicShelfRenderer
          SearchSummary(
            title =
              shelf.title?.runs?.firstOrNull()?.text ?: YouTubeConstants.DEFAULT_OTHER_RESULTS,
            items =
              shelf.contents
                ?.getItems()
                ?.mapNotNull(SearchSummaryPage::fromMusicResponsiveListItemRenderer)
                ?.distinctBy { it.id }
                ?.ifEmpty { null } ?: return@mapNotNull null,
          )
        } else {
          null
        }
      }

    val flatItems =
      contents
        .mapNotNull { it.itemSectionRenderer }
        .flatMap { it.contents.orEmpty() }
        .mapNotNull { it.musicResponsiveListItemRenderer }
        .mapNotNull(SearchSummaryPage::fromMusicResponsiveListItemRenderer)

    val grouped = mutableListOf<SearchSummary>()
    fun addGroup(title: String, items: List<YTItem>) {
      if (items.isNotEmpty()) grouped.add(SearchSummary(title, items))
    }
    addGroup("Songs", flatItems.filterIsInstance<SongItem>().filter { !it.isVideoSong })
    addGroup("Videos", flatItems.filterIsInstance<SongItem>().filter { it.isVideoSong && !it.isPodcastEpisode })
    addGroup("Episodes", flatItems.filterIsInstance<SongItem>().filter { it.isPodcastEpisode })
    addGroup("Albums", flatItems.filterIsInstance<AlbumItem>())
    addGroup("Artists", flatItems.filterIsInstance<ArtistItem>())
    addGroup("Playlists", flatItems.filterIsInstance<PlaylistItem>())

    val ranked = (shelves.flatMap { it.items } + flatItems).distinctBy { it.id }
    SearchSummaryPage(summaries = withTrackOnTop(shelves, ranked) + grouped)
      .filterBlockedArtists()
      .filterDislikedSongs()
      .copy(ranked = tracksBeforeVideos(ranked).filterBlockedArtists().filterDislikedSongs())
  }

  private fun comparableText(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
      .replace(Regex("\\p{M}+"), "")
      .lowercase()
      .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
      .trim()

  /** The album track for [video] among [items], when the same song exists as a track. */
  private fun trackOf(video: SongItem, items: List<YTItem>): SongItem? {
    val title = comparableText(video.title)
    val artists = video.artists.map { comparableText(it.name) }.toSet()
    return items.filterIsInstance<SongItem>().firstOrNull { track ->
      !track.isVideoSong &&
        comparableText(track.title) == title &&
        track.artists.any { comparableText(it.name) in artists }
    }
  }

  /** YouTube often ranks a song's video above its album track; prefer the track. */
  private fun tracksBeforeVideos(items: List<YTItem>): List<YTItem> {
    val ordered = items.toMutableList()
    var index = 0
    while (index < ordered.size) {
      val item = ordered[index]
      if (item is SongItem && item.isVideoSong && !item.isPodcastEpisode) {
        val track = trackOf(item, ordered.drop(index + 1))
        if (track != null) {
          ordered.remove(track)
          ordered.add(index, track)
          index++
        }
      }
      index++
    }
    return ordered
  }

  private fun withTrackOnTop(
    shelves: List<SearchSummary>,
    ranked: List<YTItem>,
  ): List<SearchSummary> {
    val top = shelves.firstOrNull() ?: return shelves
    val first = top.items.firstOrNull() as? SongItem ?: return shelves
    if (!first.isVideoSong || first.isPodcastEpisode) return shelves
    val track = trackOf(first, ranked) ?: return shelves
    return listOf(top.copy(items = (listOf(track) + top.items).distinctBy { it.id })) + shelves.drop(1)
  }

  suspend fun search(
    query: String,
    filter: SearchFilter,
    setLogin: Boolean? = null,
  ): Result<SearchResult> = runCatching {
    val response =
      innerTube.search(WEB_REMIX, query, filter.value, setLogin = setLogin).body<SearchResponse>()
    val shelf =
      response.contents
        ?.tabbedSearchResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.mapNotNull { it.musicShelfRenderer }
        ?.firstOrNull()
    SearchResult(
      items =
        shelf
          ?.contents
          ?.getItems()
          ?.mapNotNull(SearchPage::toYTItem)
          .orEmpty()
          .filterBlockedArtists()
          .filterDislikedSongs(),
      continuation = shelf?.continuations?.getContinuation(),
    )
  }

  suspend fun searchContinuation(continuation: String): Result<SearchResult> = runCatching {
    val response = innerTube.search(WEB_REMIX, continuation = continuation).body<SearchResponse>()
    val items =
      response.continuationContents?.musicShelfContinuation?.contents?.mapNotNull {
        SearchPage.toYTItem(it.musicResponsiveListItemRenderer)
      } ?: emptyList()
    SearchResult(
      items = items.filterBlockedArtists().filterDislikedSongs(),
      continuation =
        if (items.isEmpty()) null
        else response.continuationContents?.musicShelfContinuation?.continuations?.getContinuation(),
    )
  }

  suspend fun album(browseId: String, withSongs: Boolean = true): Result<AlbumPage> = runCatching {
    val response = innerTube.browse(WEB_REMIX, browseId, setLogin = true).body<BrowseResponse>()

    fun cleanRuns(runs: List<Run>?): List<Run>? =
      runs?.map { Run(text = ApiText.clean(it.text), navigationEndpoint = it.navigationEndpoint) }

    val descriptionRuns =
      sequence {
        for (tab in response.contents?.twoColumnBrowseResultsRenderer?.tabs.orEmpty()) {
          for (content in tab?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty()) {
            content.musicDescriptionShelfRenderer?.description?.runs?.let { yield(it) }
          }
        }
        for (tab in response.contents?.singleColumnBrowseResultsRenderer?.tabs.orEmpty()) {
          for (content in tab.tabRenderer.content?.sectionListRenderer?.contents.orEmpty()) {
            content.musicDescriptionShelfRenderer?.description?.runs?.let { yield(it) }
          }
        }
        response.header?.musicDetailHeaderRenderer?.description?.runs?.let { yield(it) }
        response.header?.musicImmersiveHeaderRenderer?.description?.runs?.let { yield(it) }
        response.header
          ?.musicEditablePlaylistDetailHeaderRenderer
          ?.header
          ?.musicDetailHeaderRenderer
          ?.description
          ?.runs
          ?.let { yield(it) }
        response.header
          ?.musicEditablePlaylistDetailHeaderRenderer
          ?.header
          ?.musicResponsiveHeaderRenderer
          ?.description
          ?.runs
          ?.let { yield(it) }
        for (tab in response.contents?.twoColumnBrowseResultsRenderer?.tabs.orEmpty()) {
          for (content in tab?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty()) {
            content.musicResponsiveHeaderRenderer?.description?.runs?.let { yield(it) }
          }
        }
        for (tab in response.contents?.singleColumnBrowseResultsRenderer?.tabs.orEmpty()) {
          for (content in tab.tabRenderer.content?.sectionListRenderer?.contents.orEmpty()) {
            content.musicResponsiveHeaderRenderer?.description?.runs?.let { yield(it) }
          }
        }
      }
        .firstOrNull()
        ?.let(::cleanRuns)
    val description = descriptionRuns?.joinToString("") { it.text }

    if (browseId.contains("FEmusic_library_privately_owned_release_detail")) {
      val header = response.header?.musicDetailHeaderRenderer!!
      val playlistId =
        header.menu?.menuRenderer?.topLevelButtons?.firstOrNull()?.buttonRenderer
          ?.navigationEndpoint?.watchPlaylistEndpoint?.playlistId!!
      val albumItem =
        AlbumItem(
          browseId = browseId,
          playlistId = playlistId,
          title = header.title.runs?.firstOrNull()?.text?.let(ApiText::clean)!!,
          artists =
            header.subtitle.runs
              ?.filter { it.navigationEndpoint != null }
              ?.map { Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId) },
          year = header.subtitle.runs?.lastOrNull()?.text?.toIntOrNull(),
          thumbnail =
            header.thumbnail.croppedSquareThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()!!.url,
          explicit = false,
          description = description,
        )
      return@runCatching AlbumPage(
        album = albumItem,
        songs =
          response.contents
            ?.singleColumnBrowseResultsRenderer
            ?.tabs
            ?.firstOrNull()
            ?.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.contents
            ?.firstOrNull()
            ?.musicShelfRenderer
            ?.contents
            ?.getItems()
            ?.mapNotNull { AlbumPage.getSong(it, albumItem) }!!
            .toMutableList(),
        otherVersions = emptyList(),
        description = description,
        descriptionRuns = descriptionRuns,
      )
    }

    val headerContent =
      response.contents
        ?.twoColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.firstOrNull()
        ?.musicResponsiveHeaderRenderer
    val playlistId =
      response.microformat?.microformatDataRenderer?.urlCanonical?.substringAfterLast('=')!!
    val albumItem =
      AlbumItem(
        browseId = browseId,
        playlistId = playlistId,
        title = headerContent?.title?.runs?.firstOrNull()?.text?.let(ApiText::clean)!!,
        artists =
          headerContent?.straplineTextOne?.runs?.oddElements()?.map {
            Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
          }!!,
        year = headerContent?.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
        thumbnail =
          headerContent?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url!!,
        explicit = false,
        description = description,
      )
    val secondary =
      response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
    AlbumPage(
      album = albumItem,
      songs = if (withSongs) albumSongs(playlistId, albumItem).getOrThrow() else emptyList(),
      otherVersions =
        secondary?.contents
          ?.find {
            it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs
              ?.any { run -> run.text.contains("versions", ignoreCase = true) } == true
          }
          ?.musicCarouselShelfRenderer
          ?.contents
          ?.mapNotNull { it.musicTwoRowItemRenderer }
          ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
          .orEmpty(),
      releasesForYou =
        secondary?.contents
          ?.find {
            it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs
              ?.any {
                it.text.contains("releases", ignoreCase = true) ||
                  it.text.contains("more from", ignoreCase = true)
              } == true
          }
          ?.musicCarouselShelfRenderer
          ?.contents
          ?.mapNotNull { it.musicTwoRowItemRenderer }
          ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
          .orEmpty(),
      description = description,
      descriptionRuns = descriptionRuns,
    )
  }

  suspend fun albumSongs(playlistId: String, album: AlbumItem? = null): Result<List<SongItem>> =
    runCatching {
      var response = innerTube.browse(WEB_REMIX, "VL$playlistId").body<BrowseResponse>()
      val firstShelf =
        response.contents
          ?.twoColumnBrowseResultsRenderer
          ?.secondaryContents
          ?.sectionListRenderer
          ?.contents
          ?.firstOrNull()
          ?.musicPlaylistShelfRenderer
      val songs =
        firstShelf?.contents?.getItems()?.mapNotNull { AlbumPage.getSong(it, album) }!!
          .toMutableList()
      var continuation = firstShelf?.contents?.getContinuation()
      val visited = mutableSetOf<String>()

      while (continuation != null && visited.size < 50 && visited.add(continuation)) {
        response = innerTube.browse(client = WEB_REMIX, continuation = continuation).body<BrowseResponse>()
        songs +=
          response.onResponseReceivedActions
            ?.firstOrNull()
            ?.appendContinuationItemsAction
            ?.continuationItems
            ?.getItems()
            ?.mapNotNull { AlbumPage.getSong(it, album) }
            .orEmpty()
        continuation =
          response.continuationContents?.musicPlaylistShelfContinuation?.continuations?.getContinuation()
      }
      songs
    }

  suspend fun artist(browseId: String): Result<ArtistPage> = runCatching {
    val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()

    fun cleanRuns(runs: List<Run>?): List<Run>? =
      runs?.map { Run(text = ApiText.clean(it.text), navigationEndpoint = it.navigationEndpoint) }

    val descriptionRuns =
      response.contents
        ?.sectionListRenderer
        ?.contents
        ?.firstOrNull { it.musicDescriptionShelfRenderer != null }
        ?.musicDescriptionShelfRenderer
        ?.description
        ?.runs
        ?.let(::cleanRuns)
        ?: response.header?.musicImmersiveHeaderRenderer?.description?.runs?.let(::cleanRuns)

    val immersive = response.header?.musicImmersiveHeaderRenderer
    val sections =
      response.contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.mapNotNull(ArtistPage::fromSectionListRendererContent)!!

    ArtistPage(
      artist =
        ArtistItem(
          id = browseId,
          title =
            immersive?.title?.runs?.firstOrNull()?.text?.let(ApiText::clean)
              ?: response.header?.musicVisualHeaderRenderer?.title?.runs?.firstOrNull()?.text
                ?.let(ApiText::clean)
              ?: response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text
                ?.let(ApiText::clean)!!,
          thumbnail =
            immersive?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
              ?: response.header?.musicVisualHeaderRenderer?.foregroundThumbnail
                ?.musicThumbnailRenderer?.getThumbnailUrl()
              ?: response.header?.musicDetailHeaderRenderer?.thumbnail?.musicThumbnailRenderer
                ?.getThumbnailUrl(),
          channelId = immersive?.subscriptionButton?.subscribeButtonRenderer?.channelId,
          playEndpoint =
            response.contents
              ?.singleColumnBrowseResultsRenderer
              ?.tabs
              ?.firstOrNull()
              ?.tabRenderer
              ?.content
              ?.sectionListRenderer
              ?.contents
              ?.firstOrNull()
              ?.musicShelfRenderer
              ?.contents
              ?.firstOrNull()
              ?.musicResponsiveListItemRenderer
              ?.overlay
              ?.musicItemThumbnailOverlayRenderer
              ?.content
              ?.musicPlayButtonRenderer
              ?.playNavigationEndpoint
              ?.watchEndpoint,
          shuffleEndpoint =
            immersive?.playButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint
              ?: response.contents
                ?.singleColumnBrowseResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents
                ?.firstOrNull()
                ?.musicShelfRenderer
                ?.contents
                ?.firstOrNull()
                ?.musicResponsiveListItemRenderer
                ?.navigationEndpoint
                ?.watchPlaylistEndpoint,
          radioEndpoint = immersive?.startRadioButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint,
        ),
      sections = sections,
      description = descriptionRuns?.joinToString("") { it.text },
      subscriberCountText =
        immersive?.subscriptionButton2?.subscribeButtonRenderer?.subscriberCountWithSubscribeText
          .extractCountText()
          ?: immersive?.subscriptionButton?.subscribeButtonRenderer?.longSubscriberCountText
            .extractCountText()
          ?: immersive?.subscriptionButton?.subscribeButtonRenderer?.shortSubscriberCountText
            .extractCountText(),
      monthlyListenerCount = immersive?.monthlyListenerCount.extractCountText(),
      descriptionRuns = descriptionRuns,
    )
  }

  suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> = runCatching {
    val response =
      innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
    val content =
      response.contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.firstOrNull()

    val grid = content?.gridRenderer
    val carousel = content?.musicCarouselShelfRenderer
    val playlistShelf = content?.musicPlaylistShelfRenderer
    val shelf = content?.musicShelfRenderer

    when {
      grid != null ->
        ArtistItemsPage(
          title = grid.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
          items =
            grid.items.mapNotNull {
              it.musicTwoRowItemRenderer?.let(ArtistItemsPage::fromMusicTwoRowItemRenderer)
            },
          continuation = grid.continuations?.getContinuation(),
        )
      carousel != null ->
        ArtistItemsPage(
          title =
            carousel.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text
              .orEmpty(),
          items =
            carousel.contents.mapNotNull { item ->
              item.musicTwoRowItemRenderer?.let(ArtistItemsPage::fromMusicTwoRowItemRenderer)
                ?: item.musicResponsiveListItemRenderer?.let(
                  ArtistItemsPage::fromMusicResponsiveListItemRenderer,
                )
            },
          continuation = null,
        )
      shelf != null ->
        ArtistItemsPage(
          title =
            shelf.title?.runs?.firstOrNull()?.text
              ?: response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text
              ?: "",
          items =
            shelf.contents?.getItems()?.mapNotNull(ArtistItemsPage::fromMusicResponsiveListItemRenderer)
              ?: emptyList(),
          continuation = shelf.continuations?.getContinuation(),
        )
      else ->
        ArtistItemsPage(
          title =
            response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text
              ?.let(ApiText::clean) ?: "",
          items =
            playlistShelf?.contents?.getItems()?.mapNotNull(
              ArtistItemsPage::fromMusicResponsiveListItemRenderer,
            ) ?: emptyList(),
          continuation = playlistShelf?.contents?.getContinuation(),
        )
    }
  }

  suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> =
    runCatching {
      val response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()
      val contents = response.continuationContents

      when {
        contents?.gridContinuation != null -> {
          val items =
            contents.gridContinuation.items.mapNotNull {
              it.musicTwoRowItemRenderer?.let(ArtistItemsPage::fromMusicTwoRowItemRenderer)
            }
          ArtistItemsContinuationPage(
            items = items,
            continuation =
              if (items.isEmpty()) null else contents.gridContinuation.continuations?.getContinuation(),
          )
        }
        contents?.musicPlaylistShelfContinuation != null -> {
          val shelf = contents.musicPlaylistShelfContinuation
          val items =
            shelf.contents.getItems().mapNotNull(ArtistItemsPage::fromMusicResponsiveListItemRenderer)
          ArtistItemsContinuationPage(
            items = items,
            continuation = if (items.isEmpty()) null else shelf.continuations?.getContinuation(),
          )
        }
        else -> {
          val items =
            response.onResponseReceivedActions
              ?.firstOrNull()
              ?.appendContinuationItemsAction
              ?.continuationItems
          val parsed =
            items?.getItems()?.mapNotNull(ArtistItemsPage::fromMusicResponsiveListItemRenderer)
              ?: emptyList()
          ArtistItemsContinuationPage(
            items = parsed,
            continuation = if (parsed.isEmpty()) null else items?.getContinuation(),
          )
        }
      }
    }

  suspend fun playlist(playlistId: String): Result<PlaylistPage> = runCatching {
    val response =
      innerTube.browse(client = WEB_REMIX, browseId = "VL$playlistId", setLogin = true)
        .body<BrowseResponse>()
    val base =
      response.contents
        ?.twoColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.firstOrNull()
    val header =
      base?.musicResponsiveHeaderRenderer
        ?: base?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer

    // Podcast shows answer a VL browse without a usable header (or as a raw upload list); their
    // own MPSP page has the show details and only its episodes.
    val vlItems =
      response.contents
        ?.twoColumnBrowseResultsRenderer
        ?.secondaryContents
        ?.sectionListRenderer
        ?.contents
        ?.firstOrNull()
        ?.let { it.musicPlaylistShelfRenderer?.contents?.getItems() ?: it.musicShelfRenderer?.contents?.getItems() }
        .orEmpty()
    val isPodcastPlaylist =
      vlItems
        .mapNotNull { it.musicVideoType }
        .let { types -> types.isNotEmpty() && types.all { it == MUSIC_VIDEO_TYPE_PODCAST_EPISODE } }
    if (header == null || isPodcastPlaylist) {
      podcast(playlistId).getOrNull()?.takeIf { it.songs.isNotEmpty() }?.let { return@runCatching it }
    }

    val editable = base?.musicEditablePlaylistDetailHeaderRenderer != null
    val secondary =
      response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
    var related = secondary?.contents?.let { parseRelatedItems(it.drop(1)) }
    if (related.isNullOrEmpty()) {
      secondary?.continuations?.getContinuation()?.let { token ->
        val continuationResponse =
          innerTube.browse(client = WEB_REMIX, continuation = token, setLogin = true)
            .body<BrowseResponse>()
        continuationResponse.continuationContents?.sectionListContinuation?.contents?.let {
          val parsed = parseRelatedItems(it)
          if (parsed.isNotEmpty()) related = parsed
        }
      }
    }

    val songShelf = secondary?.contents?.firstOrNull()
    PlaylistPage(
      playlist =
        PlaylistItem(
          id = playlistId,
          title = header?.title?.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: "",
          author =
            header?.straplineTextOne?.runs?.firstOrNull()?.let {
              Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
            },
          songCountText = header?.secondSubtitle?.runs?.firstOrNull()?.text,
          thumbnail =
            header?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url ?: "",
          playEndpoint = null,
          shuffleEndpoint =
            header?.buttons
              ?.lastOrNull()
              ?.menuRenderer
              ?.items
              ?.firstOrNull()
              ?.menuNavigationItemRenderer
              ?.navigationEndpoint
              ?.watchPlaylistEndpoint,
          radioEndpoint =
            header?.buttons
              ?.getOrNull(2)
              ?.menuRenderer
              ?.items
              ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
              ?.menuNavigationItemRenderer
              ?.navigationEndpoint
              ?.watchPlaylistEndpoint,
          isEditable = editable,
        ),
      songs =
        (songShelf?.musicPlaylistShelfRenderer?.contents ?: songShelf?.musicShelfRenderer?.contents)
          ?.getItems()
          ?.mapNotNull(PlaylistPage::fromMusicResponsiveListItemRenderer)
          ?: emptyList(),
      songsContinuation =
        songShelf?.musicPlaylistShelfRenderer?.contents?.getContinuation()
          ?: songShelf?.musicPlaylistShelfRenderer?.continuations?.getContinuation()
          ?: songShelf?.musicShelfRenderer?.contents?.getContinuation()
          ?: songShelf?.musicShelfRenderer?.continuations?.getContinuation(),
      continuation = secondary?.continuations?.getContinuation(),
      related = related?.ifEmpty { null },
    )
  }

  /** A podcast show page; [showId] is the show's playlist id without the `MPSP` prefix. */
  suspend fun podcast(showId: String): Result<PlaylistPage> = runCatching {
    val response =
      innerTube.browse(client = WEB_REMIX, browseId = PodcastPage.showBrowseId(showId), setLogin = true)
        .body<BrowseResponse>()
    val header =
      response.contents
        ?.twoColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.firstOrNull()
        ?.musicResponsiveHeaderRenderer
    val shelf =
      response.contents
        ?.twoColumnBrowseResultsRenderer
        ?.secondaryContents
        ?.sectionListRenderer
        ?.contents
        ?.firstOrNull()
        ?.musicShelfRenderer
    val title = header?.title?.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: ""
    val show = Artist(name = title, id = PodcastPage.showBrowseId(showId))
    PlaylistPage(
      playlist =
        PlaylistItem(
          id = showId,
          title = title,
          author =
            header?.straplineTextOne?.runs?.firstOrNull()?.let {
              Artist(ApiText.clean(it.text), it.navigationEndpoint?.browseEndpoint?.browseId)
            },
          songCountText = null,
          thumbnail = header?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url,
          playEndpoint = null,
          shuffleEndpoint = null,
          radioEndpoint = null,
          isPodcast = true,
        ),
      songs =
        shelf?.contents.orEmpty().mapNotNull { content ->
          content.musicMultiRowListItemRenderer?.let { PodcastPage.episode(it, show) }
        },
      songsContinuation = shelf?.continuations?.getContinuation(),
      continuation = null,
    )
  }

  private fun parseRelatedItems(contents: List<SectionListRenderer.Content>): List<YTItem> =
    contents
      .mapNotNull { content ->
        content.musicCarouselShelfRenderer?.let { carousel ->
          carousel.contents
            .mapNotNull { it.musicTwoRowItemRenderer }
            .mapNotNull(RelatedPage::fromMusicTwoRowItemRenderer)
        }
          ?: content.musicShelfRenderer?.let { shelf ->
            shelf.contents?.getItems()?.mapNotNull(
              SearchSummaryPage::fromMusicResponsiveListItemRenderer,
            )
          }
      }
      .flatten()

  suspend fun playlistContinuation(continuation: String): Result<PlaylistContinuationPage> =
    runCatching {
      val response =
        innerTube.browse(client = WEB_REMIX, continuation = continuation, setLogin = true)
          .body<BrowseResponse>()

      val shelfContents =
        response.continuationContents?.musicPlaylistShelfContinuation?.contents ?: emptyList()
      val appended =
        response.onResponseReceivedActions
          ?.firstOrNull()
          ?.appendContinuationItemsAction
          ?.continuationItems
          .orEmpty()
      val episodeContents =
        response.continuationContents?.musicShelfContinuation?.contents ?: emptyList()
      val allContents = shelfContents + appended + episodeContents

      val songs =
        allContents.mapNotNull { content: MusicShelfRenderer.Content ->
          content.musicResponsiveListItemRenderer?.let(PlaylistPage::fromMusicResponsiveListItemRenderer)
            ?: content.musicMultiRowListItemRenderer?.let { PodcastPage.episode(it) }
        }
      val next =
        if (songs.isEmpty()) {
          null
        } else {
          response.continuationContents?.musicPlaylistShelfContinuation?.continuations?.getContinuation()
            ?: response.continuationContents?.musicShelfContinuation?.continuations?.getContinuation()
            ?: appended.getContinuation()
        }
      PlaylistContinuationPage(songs = songs, continuation = next)
    }

  suspend fun home(
    continuation: String? = null,
    browseId: String = "FEmusic_home",
    params: String? = null,
  ): Result<HomePage> = runCatching {
    if (continuation != null) return@runCatching homeContinuation(continuation).getOrThrow()

    val response = innerTube.browse(WEB_REMIX, browseId = browseId, params = params).body<BrowseResponse>()
    val sectionList =
      response.contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
    val sections =
      sectionList
        ?.contents
        .orEmpty()
        .mapNotNull { it.musicCarouselShelfRenderer }
        .mapNotNull { HomePage.Section.fromMusicCarouselShelfRenderer(it) }
        .filterNot { it.isStationListenTogether }
        .toMutableList()
    val chips =
      sectionList?.header?.chipCloudRenderer?.chips?.mapNotNull {
        HomePage.Chip.fromChipCloudChipRenderer(it)
      }
    HomePage(chips, sections, sectionList?.continuations?.getContinuation())
      .filterBlockedArtists()
      .filterDislikedSongs()
  }

  private suspend fun homeContinuation(continuation: String): Result<HomePage> = runCatching {
    val response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()
    val sectionList = response.continuationContents?.sectionListContinuation
    HomePage(
      null,
      sectionList
        ?.contents
        ?.mapNotNull { it.musicCarouselShelfRenderer }
        ?.mapNotNull { HomePage.Section.fromMusicCarouselShelfRenderer(it) }
        ?.filterNot { it.isStationListenTogether }
        .orEmpty(),
      sectionList?.continuations?.getContinuation(),
    )
  }

  suspend fun explore(): Result<ExplorePage> = runCatching {
    val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_explore").body<BrowseResponse>()
    val contents =
      response.contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        .orEmpty()
    ExplorePage(
      newReleaseAlbums =
        contents
          .find {
            it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer
              ?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId ==
              "FEmusic_new_releases_albums"
          }
          ?.musicCarouselShelfRenderer
          ?.contents
          ?.mapNotNull { it.musicTwoRowItemRenderer }
          ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
          .orEmpty(),
      moodAndGenres =
        contents
          .find {
            it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer
              ?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId ==
              "FEmusic_moods_and_genres"
          }
          ?.musicCarouselShelfRenderer
          ?.contents
          ?.mapNotNull { it.musicNavigationButtonRenderer }
          ?.mapNotNull(MoodAndGenres::fromMusicNavigationButtonRenderer)
          .orEmpty(),
    )
  }

  suspend fun newReleaseAlbums(): Result<List<AlbumItem>> = runCatching {
    val response =
      innerTube.browse(WEB_REMIX, browseId = "FEmusic_new_releases_albums").body<BrowseResponse>()
    response.contents
      ?.singleColumnBrowseResultsRenderer
      ?.tabs
      ?.firstOrNull()
      ?.tabRenderer
      ?.content
      ?.sectionListRenderer
      ?.contents
      ?.firstOrNull()
      ?.gridRenderer
      ?.items
      ?.mapNotNull { it.musicTwoRowItemRenderer }
      ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
      .orEmpty()
  }

  suspend fun moodAndGenres(): Result<List<MoodAndGenres>> = runCatching {
    val response =
      innerTube.browse(WEB_REMIX, browseId = "FEmusic_moods_and_genres").body<BrowseResponse>()
    response.contents
      ?.singleColumnBrowseResultsRenderer
      ?.tabs
      ?.firstOrNull()
      ?.tabRenderer
      ?.content
      ?.sectionListRenderer
      ?.contents!!
      .mapNotNull(MoodAndGenres::fromSectionListRendererContent)
  }

  suspend fun browse(browseId: String, params: String?): Result<BrowseResult> = runCatching {
    val response =
      innerTube.browse(WEB_REMIX, browseId = browseId, params = params).body<BrowseResponse>()
    BrowseResult(
      title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text,
      items =
        response.contents
          ?.singleColumnBrowseResultsRenderer
          ?.tabs
          ?.firstOrNull()
          ?.tabRenderer
          ?.content
          ?.sectionListRenderer
          ?.contents
          ?.mapNotNull { content ->
            when {
              content.gridRenderer != null ->
                BrowseResult.Item(
                  title = content.gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text,
                  items =
                    content.gridRenderer.items
                      .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                      .mapNotNull(RelatedPage::fromMusicTwoRowItemRenderer),
                )
              content.musicCarouselShelfRenderer != null ->
                BrowseResult.Item(
                  title =
                    content.musicCarouselShelfRenderer.header
                      ?.musicCarouselShelfBasicHeaderRenderer
                      ?.title
                      ?.runs
                      ?.firstOrNull()
                      ?.text,
                  items =
                    content.musicCarouselShelfRenderer.contents
                      .mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                      .mapNotNull(RelatedPage::fromMusicTwoRowItemRenderer),
                )
              else -> null
            }
          }
          .orEmpty(),
    )
  }

  suspend fun library(browseId: String, tabIndex: Int = 0): Result<LibraryPage> = runCatching {
    val response =
      innerTube.browse(client = WEB_REMIX, browseId = browseId, setLogin = true).body<BrowseResponse>()
    val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs
    val content =
      if (tabs != null && tabs.size > tabIndex) {
        tabs[tabIndex].tabRenderer.content?.sectionListRenderer?.contents?.firstOrNull()
      } else {
        null
      }

    when {
      content?.gridRenderer != null -> {
        val grid = content.gridRenderer
        LibraryPage(
          items =
            grid.items
              .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
              .mapNotNull(LibraryPage::fromMusicTwoRowItemRenderer),
          continuation = grid.continuations?.getContinuation(),
        )
      }
      else -> {
        val shelf = content?.musicShelfRenderer
          ?: throw IllegalStateException("No content found for browseId=$browseId")
        LibraryPage(
          items =
            shelf.contents
              .orEmpty()
              .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
              .mapNotNull(LibraryPage::fromMusicResponsiveListItemRenderer),
          continuation = shelf.continuations?.getContinuation(),
        )
      }
    }
  }

  suspend fun libraryContinuation(continuation: String): Result<LibraryContinuationPage> =
    runCatching {
      val response =
        innerTube.browse(client = WEB_REMIX, continuation = continuation, setLogin = true)
          .body<BrowseResponse>()
      val contents = response.continuationContents
      when {
        contents?.gridContinuation != null ->
          LibraryContinuationPage(
            items =
              contents.gridContinuation.items
                .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                .mapNotNull(LibraryPage::fromMusicTwoRowItemRenderer),
            continuation = contents.gridContinuation.continuations?.getContinuation(),
          )
        else ->
          LibraryContinuationPage(
            items =
              contents
                ?.musicShelfContinuation
                ?.contents!!
                .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                .mapNotNull(LibraryPage::fromMusicResponsiveListItemRenderer),
            continuation = contents?.musicShelfContinuation?.continuations?.getContinuation(),
          )
      }
    }

  suspend fun libraryRecentActivity(): Result<LibraryPage> = runCatching {
    val response =
      innerTube
        .browse(client = WEB_REMIX, continuation = LibraryFilter.FILTER_RECENT_ACTIVITY.value, setLogin = true)
        .body<BrowseResponse>()
    val gridItems =
      response.continuationContents
        ?.sectionListContinuation
        ?.contents
        ?.firstOrNull()
        ?.gridRenderer
        ?.items
        ?: return@runCatching LibraryPage(items = emptyList(), continuation = null)

    val items =
      gridItems
        .mapNotNull { it.musicTwoRowItemRenderer?.let(LibraryPage::fromMusicTwoRowItemRenderer) }
        .toMutableList()

    // Re-fetch each artist page for a proper play endpoint, keeping the library's square thumbnail.
    items.forEachIndexed { index, item ->
      if (item is ArtistItem) {
        artist(item.id).getOrNull()?.artist?.let { fetched ->
          items[index] = fetched.copy(thumbnail = item.thumbnail)
        }
      }
    }
    LibraryPage(items = items, continuation = null)
  }

  suspend fun getChartsPage(continuation: String? = null): Result<ChartsPage> = runCatching {
    val response =
      innerTube
        .browse(
          client = WEB_REMIX,
          browseId = "FEmusic_charts",
          params = "ggMGCgQIgAQ%3D",
          continuation = continuation,
        )
        .body<BrowseResponse>()
    val sections = mutableListOf<ChartsPage.ChartSection>()

    response.contents
      ?.singleColumnBrowseResultsRenderer
      ?.tabs
      ?.firstOrNull()
      ?.tabRenderer
      ?.content
      ?.sectionListRenderer
      ?.contents
      ?.forEach { content ->
        content.musicCarouselShelfRenderer?.let { carousel ->
          val title =
            carousel.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text
              ?: return@forEach
          val items =
            carousel.contents.mapNotNull { item ->
              item.musicResponsiveListItemRenderer?.let { convertToChartItem(it) }
                ?: item.musicTwoRowItemRenderer?.let { convertMusicTwoRowItem(it) }
            }
          if (items.isNotEmpty()) {
            sections.add(ChartsPage.ChartSection(title, items, determineChartType(title)))
          }
        }
        content.gridRenderer?.let { grid ->
          val title = grid.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: return@let
          val items =
            grid.items.mapNotNull { item ->
              item.musicTwoRowItemRenderer?.let { convertMusicTwoRowItem(it) }
            }
          if (items.isNotEmpty()) {
            sections.add(
              ChartsPage.ChartSection(title, items, ChartsPage.ChartType.NEW_RELEASES),
            )
          }
        }
      }

    ChartsPage(
      sections = sections,
      continuation =
        response.continuationContents?.sectionListContinuation?.continuations?.getContinuation(),
    )
  }

  private fun determineChartType(title: String): ChartsPage.ChartType =
    when {
      title.contains("Trending", ignoreCase = true) -> ChartsPage.ChartType.TRENDING
      title.contains("Top", ignoreCase = true) -> ChartsPage.ChartType.TOP
      else -> ChartsPage.ChartType.GENRE
    }

  private fun convertToChartItem(renderer: MusicResponsiveListItemRenderer): YTItem? =
    try {
      if (renderer.flexColumns.size < 3 || renderer.playlistItemData?.videoId == null) {
        null
      } else {
        val first =
          renderer.flexColumns.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text
          ?: return null
        val second =
          renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text
          ?: return null
        val titleRun = first.runs?.firstOrNull() ?: return null
        val title = titleRun.text.takeIf { it.isNotBlank() }?.let(ApiText::clean) ?: return null
        val artists =
          second.runs?.mapNotNull { run ->
            run.text.takeIf { it.isNotBlank() }?.let {
              Artist(ApiText.clean(it), run.navigationEndpoint?.browseEndpoint?.browseId)
            }
          } ?: emptyList()
        val third = renderer.flexColumns.getOrNull(2)?.musicResponsiveListItemFlexColumnRenderer?.text

        SongItem(
          id = renderer.playlistItemData!!.videoId,
          title = title,
          artists = artists,
          thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
          musicVideoType = renderer.musicVideoType,
          explicit =
            renderer.badges?.any {
              it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
            } == true,
          chartPosition = third?.runs?.firstOrNull()?.text?.toIntOrNull(),
          chartChange = third?.runs?.getOrNull(1)?.text,
        )
      }
    } catch (e: Exception) {
      null
    }

  private fun convertMusicTwoRowItem(renderer: MusicTwoRowItemRenderer): YTItem? =
    try {
      when {
        renderer.isSong -> {
          val subtitle = renderer.subtitle?.runs ?: return null
          SongItem(
            id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
            title = renderer.title.runs?.firstOrNull()?.text?.let(ApiText::clean) ?: return null,
            artists =
              subtitle.mapNotNull {
                it.navigationEndpoint?.browseEndpoint?.browseId?.let { id ->
                  Artist(ApiText.clean(it.text), id)
                }
              },
            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            musicVideoType = renderer.musicVideoType,
            explicit =
              renderer.subtitleBadges?.any {
                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
              } == true,
          )
        }
        renderer.isAlbum ->
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
              renderer.subtitle?.runs?.oddElements()?.drop(1)?.mapNotNull {
                it.navigationEndpoint?.browseEndpoint?.browseId?.let { id ->
                  Artist(ApiText.clean(it.text), id)
                }
              },
            year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
            explicit =
              renderer.subtitleBadges?.any {
                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
              } == true,
          )
        else -> null
      }
    } catch (e: Exception) {
      null
    }

  suspend fun musicHistory(): Result<HistoryPage> = runCatching {
    val response =
      innerTube.browse(client = WEB_REMIX, browseId = "FEmusic_history", setLogin = true)
        .body<BrowseResponse>()
    HistoryPage(
      sections =
        response.contents
          ?.singleColumnBrowseResultsRenderer
          ?.tabs
          ?.firstOrNull()
          ?.tabRenderer
          ?.content
          ?.sectionListRenderer
          ?.contents
          ?.mapNotNull { content ->
            content.musicShelfRenderer?.let(HistoryPage::fromMusicShelfRenderer)
          },
    )
  }

  suspend fun likeVideo(videoId: String, like: Boolean) = runCatching {
    if (like) innerTube.likeVideo(WEB_REMIX, videoId) else innerTube.unlikeVideo(WEB_REMIX, videoId)
  }

  suspend fun likePlaylist(playlistId: String, like: Boolean) = runCatching {
    if (like) innerTube.likePlaylist(WEB_REMIX, playlistId)
    else innerTube.unlikePlaylist(WEB_REMIX, playlistId)
  }

  suspend fun subscribeChannel(channelId: String, subscribe: Boolean) = runCatching {
    if (subscribe) innerTube.subscribeChannel(WEB_REMIX, channelId)
    else innerTube.unsubscribeChannel(WEB_REMIX, channelId)
  }

  suspend fun getChannelId(browseId: String): String {
    artist(browseId).onSuccess { return it.artist.channelId ?: "" }
    return ""
  }

  suspend fun addToPlaylist(playlistId: String, videoId: String) = runCatching {
    innerTube.addToPlaylist(WEB_REMIX, playlistId, videoId)
  }

  suspend fun addPlaylistToPlaylist(playlistId: String, addPlaylistId: String) = runCatching {
    innerTube.addPlaylistToPlaylist(WEB_REMIX, playlistId, addPlaylistId)
  }

  suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String) =
    runCatching {
      innerTube.removeFromPlaylist(WEB_REMIX, playlistId, videoId, setVideoId)
    }

  suspend fun moveSongPlaylist(
    playlistId: String,
    setVideoId: String,
    successorSetVideoId: String?,
  ) = runCatching {
    innerTube.moveSongPlaylist(WEB_REMIX, playlistId, setVideoId, successorSetVideoId)
  }

  fun createPlaylist(title: String) = runBlocking {
    innerTube.createPlaylist(WEB_REMIX, title).body<CreatePlaylistResponse>().playlistId
  }

  suspend fun renamePlaylist(playlistId: String, name: String) = runCatching {
    innerTube.renamePlaylist(WEB_REMIX, playlistId, name)
  }

  suspend fun uploadCustomThumbnailLink(playlistId: String, image: ByteArray) = runCatching {
    val uploadId =
      innerTube.getUploadCustomThumbnailLink(WEB_REMIX, image.size).headers["x-guploader-uploadid"]
    val blobResponse = innerTube.uploadCustomThumbnail(WEB_REMIX, uploadId!!, image)
    val blobId = Json.decodeFromString<ImageUploadResponse>(blobResponse.bodyAsText()).encryptedBlobId
    innerTube
      .setThumbnailPlaylist(WEB_REMIX, playlistId, blobId)
      .body<EditPlaylistResponse>()
      .newHeader
      ?.musicEditablePlaylistDetailHeaderRenderer
      ?.header
      ?.musicResponsiveHeaderRenderer
      ?.thumbnail
      ?.musicThumbnailRenderer
      ?.getThumbnailUrl()
  }

  suspend fun removeThumbnailPlaylist(playlistId: String) = runCatching {
    innerTube
      .removeThumbnailPlaylist(WEB_REMIX, playlistId)
      .body<EditPlaylistResponse>()
      .newHeader
      ?.musicEditablePlaylistDetailHeaderRenderer
      ?.header
      ?.musicResponsiveHeaderRenderer
      ?.thumbnail
      ?.musicThumbnailRenderer
      ?.getThumbnailUrl()
  }

  suspend fun deletePlaylist(playlistId: String) = runCatching {
    innerTube.deletePlaylist(WEB_REMIX, playlistId)
  }

  suspend fun player(
    videoId: String,
    playlistId: String? = null,
    client: YouTubeClient,
    signatureTimestamp: Int? = null,
    poToken: String? = null,
  ): Result<PlayerResponse> = runCatching {
    innerTube.player(client, videoId, playlistId, signatureTimestamp, poToken).body<PlayerResponse>()
  }

  /** A client-generated nonce tying together the reports of one playback. */
  fun newPlaybackNonce(): String =
    (1..16)
      .map {
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"[
          Random.Default.nextInt(0, 64)]
      }
      .joinToString("")

  suspend fun reportWatchtime(
    watchtimeUrl: String,
    cpn: String,
    startSeconds: Double,
    positionSeconds: Double,
    lengthSeconds: Double,
    elapsedSeconds: Double,
    paused: Boolean,
  ): Result<Int> = runCatching {
    innerTube
      .reportWatchtime(
        url = watchtimeUrl.replace("https://s.youtube.com", "https://music.youtube.com"),
        cpn = cpn,
        startSeconds = startSeconds,
        positionSeconds = positionSeconds,
        lengthSeconds = lengthSeconds,
        elapsedSeconds = elapsedSeconds,
        state = if (paused) "paused" else "playing",
      )
      .status
      .value
  }

  suspend fun registerPlayback(
    playlistId: String? = null,
    playbackTracking: String,
    cpn: String = newPlaybackNonce(),
  ) = runCatching {
    innerTube.registerPlayback(
      url = playbackTracking.replace("https://s.youtube.com", "https://music.youtube.com"),
      playlistId = playlistId,
      cpn = cpn,
    )
  }

  suspend fun next(endpoint: WatchEndpoint, continuation: String? = null): Result<NextResult> =
    runCatching {
      val response =
        innerTube
          .next(
            WEB_REMIX,
            endpoint.videoId,
            endpoint.playlistId,
            endpoint.playlistSetVideoId,
            endpoint.index,
            endpoint.params,
            continuation,
          )
          .body<NextResponse>()
      val panel =
        response.continuationContents?.playlistPanelContinuation
          ?: response.contents.singleColumnMusicWatchNextResultsRenderer
            ?.tabbedRenderer
            ?.watchNextTabbedResultsRenderer
            ?.tabs
            ?.get(0)
            ?.tabRenderer
            ?.content
            ?.musicQueueRenderer
            ?.content
            ?.playlistPanelRenderer!!
      val queueTab =
        response.contents.singleColumnMusicWatchNextResultsRenderer
          ?.tabbedRenderer
          ?.watchNextTabbedResultsRenderer
          ?.tabs
      val title =
        queueTab?.get(0)?.tabRenderer?.content?.musicQueueRenderer?.header?.musicQueueHeaderRenderer
          ?.subtitle
          ?.runs
          ?.firstOrNull()
          ?.text
      val items =
        panel.contents.mapNotNull { content ->
          content.playlistPanelVideoRenderer?.let { renderer ->
            NextPage.fromPlaylistPanelVideoRenderer(renderer)?.let { song -> song to renderer.selected }
          }
        }
      val songs = items.map { it.first }
      val currentIndex = items.indexOfFirst { it.second }.takeIf { it != -1 }

      val lyricsEndpoint =
        queueTab?.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint
      val relatedEndpoint =
        queueTab?.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint

      panel.contents
        .lastOrNull()
        ?.automixPreviewVideoRenderer
        ?.content
        ?.automixPlaylistVideoRenderer
        ?.navigationEndpoint
        ?.watchPlaylistEndpoint
        ?.let { automixEndpoint ->
          return@runCatching next(automixEndpoint).getOrThrow().let { result ->
            result.copy(
              title = title,
              items = (songs + result.items).filterBlockedArtists().filterDislikedSongs(),
              lyricsEndpoint = lyricsEndpoint,
              relatedEndpoint = relatedEndpoint,
              currentIndex = currentIndex,
              endpoint = automixEndpoint,
            )
          }
        }

      NextResult(
        title = title,
        items = songs.filterBlockedArtists().filterDislikedSongs(),
        currentIndex = currentIndex,
        lyricsEndpoint = lyricsEndpoint,
        relatedEndpoint = relatedEndpoint,
        continuation = panel.continuations?.getContinuation(),
        endpoint = endpoint,
      )
    }

  suspend fun fetchTasteSignals(videoId: String): Result<TasteSignals> = runCatching {
    val response =
      innerTube
        .next(WEB_REMIX, videoId = videoId, playlistId = null, playlistSetVideoId = null, index = null, params = null)
        .body<NextResponse>()
    parseTasteSignals(videoId, response)
  }

  fun parseTasteSignals(videoId: String, response: NextResponse): TasteSignals {
    val moodKeywords =
      setOf(
        "chill", "relax", "relaxing", "workout", "fitness", "focus", "study", "party", "dance",
        "club", "sleep", "sad", "heartbreak", "happy", "feel good", "energetic", "energy",
        "romance", "romantic", "commute", "drive", "driving", "dark", "deep cuts", "familiar",
        "discover", "upbeat", "peaceful", "calm", "hype", "intense", "late night",
      )
    val genreKeywords =
      setOf(
        "pop", "rock", "hip hop", "hip-hop", "rap", "r&b", "rnb", "soul", "indie", "alternative",
        "electronic", "edm", "metal", "punk", "jazz", "blues", "country", "folk", "classical",
        "latin", "reggae", "ambient", "acoustic", "lo-fi", "lofi", "k-pop", "kpop", "j-pop",
        "jpop", "soundtrack", "trap", "house", "techno", "funk", "disco", "synthwave", "afrobeats",
        "reggaeton", "heavy metal", "hard rock", "classic rock", "folk rock",
      )

    val extracted = mutableListOf<String>()
    val tabs =
      response.contents.singleColumnMusicWatchNextResultsRenderer
        ?.tabbedRenderer
        ?.watchNextTabbedResultsRenderer
        ?.tabs
        .orEmpty()
    for (tab in tabs) {
      val content = tab.tabRenderer.content ?: continue
      content.sectionListRenderer?.header?.chipCloudRenderer?.chips?.forEach { chip ->
        chip.chipCloudChipRenderer.text?.runs?.forEach { run ->
          if (run.text.isNotBlank()) extracted.add(run.text.trim())
        }
      }
      content.sectionListRenderer?.contents?.forEach { c ->
        c.musicShelfRenderer?.title?.runs?.forEach { run ->
          if (run.text.isNotBlank()) extracted.add(run.text.trim())
        }
        c.musicShelfRenderer?.contents?.forEach { shelfContent ->
          shelfContent.musicResponsiveListItemRenderer?.flexColumns?.forEach { column ->
            column.musicResponsiveListItemFlexColumnRenderer.text?.runs?.forEach { run ->
              if (run.text.isNotBlank()) extracted.add(run.text.trim())
            }
          }
        }
        c.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs
          ?.forEach { run ->
            if (run.text.isNotBlank()) extracted.add(run.text.trim())
          }
      }
      content.musicQueueRenderer?.header?.musicQueueHeaderRenderer?.subtitle?.runs?.forEach { run ->
        if (run.text.isNotBlank()) extracted.add(run.text.trim())
      }
    }

    val genres = mutableListOf<String>()
    val moods = mutableListOf<String>()
    for (raw in extracted) {
      val lower = raw.lowercase()
      var matched = false
      for (genre in genreKeywords) {
        if (lower == genre || "\\b${Regex.escape(genre)}\\b".toRegex().containsMatchIn(lower)) {
          genres.add(genre)
          matched = true
        }
      }
      for (mood in moodKeywords) {
        if (lower == mood || "\\b${Regex.escape(mood)}\\b".toRegex().containsMatchIn(lower)) {
          moods.add(mood)
          matched = true
        }
      }
      // A "… mix / radio / station" label that matched no known keyword is still a genre hint.
      if (
        !matched &&
          lower.length in 3..25 &&
          !lower.contains("http") &&
          (lower.contains("mix") || lower.contains("radio") || lower.contains("station"))
      ) {
        val cleaned = lower.replace("mix", "").replace("radio", "").replace("station", "").trim()
        if (cleaned.isNotBlank()) genres.add(cleaned)
      }
    }

    return TasteSignals(videoId = videoId, genres = genres.distinct(), moodTags = moods.distinct())
  }

  suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?> = runCatching {
    val response =
      innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
    response.contents
      ?.sectionListRenderer
      ?.contents
      ?.firstOrNull { it.musicDescriptionShelfRenderer != null }
      ?.musicDescriptionShelfRenderer
      ?.description
      ?.runs
      ?.joinToString("") { it.text }
  }

  suspend fun related(endpoint: BrowseEndpoint): Result<RelatedPage> = runCatching {
    val response = innerTube.browse(WEB_REMIX, endpoint.browseId).body<BrowseResponse>()
    val songs = mutableListOf<SongItem>()
    val albums = mutableListOf<AlbumItem>()
    val artists = mutableListOf<ArtistItem>()
    val playlists = mutableListOf<PlaylistItem>()

    fun add(item: YTItem, renderer: MusicResponsiveListItemRenderer?) {
      when (item) {
        is SongItem -> {
          val audioTrack =
            renderer
              ?.overlay
              ?.musicItemThumbnailOverlayRenderer
              ?.content
              ?.musicPlayButtonRenderer
              ?.playNavigationEndpoint
              ?.watchEndpoint
              ?.watchEndpointMusicSupportedConfigs
              ?.watchEndpointMusicConfig
              ?.musicVideoType == MUSIC_VIDEO_TYPE_ATV
          if (audioTrack) songs.add(item)
        }
        is AlbumItem -> albums.add(item)
        is ArtistItem -> artists.add(item)
        is PlaylistItem -> playlists.add(item)
      }
    }

    response.contents?.sectionListRenderer?.contents?.forEach { content ->
      content.musicCarouselShelfRenderer?.contents?.forEach { item ->
        val parsed =
          item.musicResponsiveListItemRenderer?.let(RelatedPage::fromMusicResponsiveListItemRenderer)
            ?: item.musicTwoRowItemRenderer?.let(RelatedPage::fromMusicTwoRowItemRenderer)
        if (parsed != null) add(parsed, item.musicResponsiveListItemRenderer)
      }
      content.musicShelfRenderer?.contents?.forEach { item ->
        val parsed =
          item.musicResponsiveListItemRenderer?.let(RelatedPage::fromMusicResponsiveListItemRenderer)
        if (parsed != null) add(parsed, item.musicResponsiveListItemRenderer)
      }
      content.itemSectionRenderer?.contents?.forEach { item ->
        val parsed =
          item.musicResponsiveListItemRenderer?.let(RelatedPage::fromMusicResponsiveListItemRenderer)
        if (parsed != null) add(parsed, item.musicResponsiveListItemRenderer)
      }
    }
    RelatedPage(songs, albums, artists, playlists)
  }

  suspend fun queue(
    videoIds: List<String>? = null,
    playlistId: String? = null,
  ): Result<List<SongItem>> = runCatching {
    if (videoIds != null) assert(videoIds.size <= MAX_GET_QUEUE_SIZE)
    innerTube
      .getQueue(WEB_REMIX, videoIds, playlistId)
      .body<GetQueueResponse>()
      .queueDatas
      .mapNotNull {
        it.content.playlistPanelVideoRenderer?.let(NextPage::fromPlaylistPanelVideoRenderer)
      }
  }

  suspend fun transcript(videoId: String): Result<String> = runCatching {
    val response = innerTube.getTranscript(WEB, videoId).body<GetTranscriptResponse>()
    response.actions
      ?.firstOrNull()
      ?.updateEngagementPanelAction
      ?.content
      ?.transcriptRenderer
      ?.body
      ?.transcriptBodyRenderer
      ?.cueGroups
      ?.joinToString("\n") { group ->
        val cue = group.transcriptCueGroupRenderer.cues[0].transcriptCueRenderer
        val time = cue.startOffsetMs
        val text = cue.cue.simpleText.trim('♪').trim(' ')
        "[%02d:%02d.%03d]$text".format(time / 60000, (time / 1000) % 60, time % 1000)
      }!!
  }

  suspend fun visitorData(): Result<String> = runCatching {
    Json.parseToJsonElement(innerTube.getSwJsData().bodyAsText().substring(5))
      .jsonArray[0]
      .jsonArray[2]
      .jsonArray
      .first {
        (it as? JsonPrimitive)?.contentOrNull?.let { candidate ->
          VISITOR_DATA_REGEX.containsMatchIn(candidate)
        } ?: false
      }
      .jsonPrimitive
      .content
  }

  suspend fun accountInfo(): Result<AccountInfo> = runCatching {
    innerTube
      .accountMenu(WEB_REMIX)
      .body<AccountMenuResponse>()
      .actions[0]
      .openPopupAction
      .popup
      .multiPageMenuRenderer
      .header
      ?.activeAccountHeaderRenderer
      ?.toAccountInfo()!!
  }

  suspend fun feedback(tokens: List<String>): Result<Boolean> = runCatching {
    innerTube.feedback(WEB_REMIX, tokens).body<FeedbackResponse>().feedbackResponses.all {
      it.isProcessed
    }
  }

  suspend fun addSongToLibrary(videoId: String): Result<Boolean> = runCatching {
    val song =
      next(WatchEndpoint(videoId = videoId)).getOrThrow().items.find { it.id == videoId }
        ?: throw Exception("Song not found in next response")
    val token = song.libraryAddToken ?: throw Exception("Add to library token not available")
    feedback(listOf(token)).getOrThrow()
  }

  suspend fun removeSongFromLibrary(videoId: String): Result<Boolean> = runCatching {
    val song =
      next(WatchEndpoint(videoId = videoId)).getOrThrow().items.find { it.id == videoId }
        ?: throw Exception("Song not found in next response")
    val token = song.libraryRemoveToken ?: throw Exception("Remove from library token not available")
    feedback(listOf(token)).getOrThrow()
  }

  suspend fun toggleSongLibrary(videoId: String, addToLibrary: Boolean): Result<Boolean> =
    runCatching {
      if (addToLibrary) addSongToLibrary(videoId).getOrThrow()
      else removeSongFromLibrary(videoId).getOrThrow()
    }

  suspend fun getMediaInfo(videoId: String): Result<MediaInfo> = innerTube.getMediaInfo(videoId)

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
      val FILTER_RECENT_ACTIVITY =
        LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpYnJhcnlfbGFuZGluZxoQZ2dNR0tnUUlCaEFCb0FZQg%3D%3D")
      val FILTER_RECENTLY_PLAYED =
        LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpYnJhcnlfbGFuZGluZxoQZ2dNR0tnUUlCUkFCb0FZQg%3D%3D")
      val FILTER_PLAYLISTS_ALPHABETICAL =
        LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpa2VkX3BsYXlsaXN0cxoQZ2dNR0tnUUlBUkFBb0FZQg%3D%3D")
      val FILTER_PLAYLISTS_RECENTLY_SAVED =
        LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpa2VkX3BsYXlsaXN0cxoQZ2dNR0tnUUlBQkFCb0FZQg%3D%3D")
    }
  }

  const val MAX_GET_QUEUE_SIZE = 1000

  private val VISITOR_DATA_REGEX = Regex("^Cg[t|s]")

  fun getNewPipeStreamUrls(videoId: String): List<Pair<Int, String>> =
    NewPipeExtractor.newPipePlayer(videoId)

  suspend fun newPipePlayer(videoId: String, tempRes: PlayerResponse): PlayerResponse? {
    if (tempRes.playabilityStatus.status != "OK") return null
    val streams = getNewPipeStreamUrls(videoId)
    if (streams.isEmpty()) return null

    val streaming = tempRes.streamingData ?: return null
    val decoded =
      tempRes.copy(
        streamingData =
          streaming.copy(
            formats =
              streaming.formats?.map { format ->
                format.copy(url = streams.find { it.first == format.itag }?.second ?: format.url)
              },
            adaptiveFormats =
              streaming.adaptiveFormats.map { format ->
                format.copy(url = streams.find { it.first == format.itag }?.second ?: format.url)
              },
          ),
      )

    val urls =
      decoded.streamingData?.adaptiveFormats?.mapNotNull { it.url }?.toMutableList()
        ?: mutableListOf()
    decoded.streamingData?.formats?.mapNotNull { it.url }?.let { urls.addAll(it) }
    return if (urls.isNotEmpty()) decoded else null
  }

  suspend fun comments(videoId: String): Result<Pair<List<CommentThreadRenderer>, String?>> =
    runCatching {
      val response =
        innerTube.next(YouTubeClient.WEB, videoId, null, null, null, null, null).body<NextResponse>()

      val panelToken =
        response.engagementPanels
          ?.firstOrNull {
            it.engagementPanelSectionListRenderer?.panelIdentifier == "engagement-panel-comments-section"
          }
          ?.engagementPanelSectionListRenderer
          ?.content
          ?.sectionListRenderer
          ?.contents
          ?.mapNotNull { it.itemSectionRenderer }
          ?.flatMap { it.contents.orEmpty() }
          ?.mapNotNull { it?.continuationItemRenderer }
          ?.firstOrNull()
          ?.continuationEndpoint
          ?.continuationCommand
          ?.token

      val contentList = response.contents.twoColumnWatchNextResults?.results?.results?.content
      // Prefer the WEB comment token: the Music engagement panel hides nested replies.
      val token =
        contentList
          ?.mapNotNull { it?.continuationItemRenderer }
          ?.firstOrNull()
          ?.continuationEndpoint
          ?.continuationCommand
          ?.token
          ?: contentList
            ?.mapNotNull { it?.itemSectionRenderer }
            ?.flatMap { it.contents.orEmpty() }
            ?.mapNotNull { it?.continuationItemRenderer }
            ?.firstOrNull()
            ?.continuationEndpoint
            ?.continuationCommand
            ?.token
          ?: panelToken
          ?: throw Exception("No comment continuation token found for videoId=$videoId")

      commentContinuation(token).getOrThrow()
    }

  suspend fun commentContinuation(
    continuationToken: String,
  ): Result<Pair<List<CommentThreadRenderer>, String?>> = runCatching {
    val response =
      innerTube.next(YouTubeClient.WEB, null, null, null, null, null, continuationToken)
        .body<CommentResponse>()
    val endpoints = response.onResponseReceivedEndpoints.orEmpty()
    val items =
      endpoints.flatMap { endpoint ->
        endpoint.reloadContinuationItemsCommand?.continuationItems.orEmpty() +
          endpoint.appendContinuationItemsAction?.continuationItems.orEmpty()
      }

    val legacy =
      items
        .mapNotNull { it.commentThreadRenderer }
        .filter { it.comment?.commentRenderer != null || it.commentViewModel?.commentViewModel != null }
    val legacyById =
      legacy.associateBy { thread ->
        thread.comment?.commentRenderer?.commentId
          ?: thread.commentViewModel?.commentViewModel?.commentId
          ?: "legacy-${thread.hashCode()}"
      }

    val mutations = response.frameworkUpdates?.entityBatchUpdate?.mutations.orEmpty()
    val toolbarStates =
      mutations.mapNotNull { it.payload?.engagementToolbarStateEntityPayload }.associateBy { it.key }
    val surfaces =
      mutations.mapNotNull { it.payload?.engagementToolbarSurfaceEntityPayload }.associateBy { it.key }

    val modern =
      mutations.mapNotNull { mutation ->
        mutation.payload?.commentEntityPayload?.let { payload ->
          val toolbar = toolbarStates[payload.properties?.toolbarStateKey]
          val surface = surfaces[payload.properties?.toolbarSurfaceKey]
          val likeCount =
            payload.toolbar?.likeCountNotliked ?: surface?.toolbar?.likeCountNotliked ?: "0"
          val replyCount = payload.toolbar?.replyCount ?: surface?.toolbar?.replyCount ?: "0"
          val commentId = payload.properties?.commentId ?: "framework-${mutation.hashCode()}"

          CommentThreadRenderer(
            comment =
              CommentThreadRenderer.Comment(
                commentRenderer =
                  com.revenge.innertube.models.comment.CommentRenderer(
                    authorText =
                      com.revenge.innertube.models.Runs(
                        listOf(
                          com.revenge.innertube.models.Run(
                            text = ApiText.clean(payload.author?.displayName ?: "Unknown"),
                            navigationEndpoint = null,
                          ),
                        ),
                      ),
                    authorThumbnail =
                      com.revenge.innertube.models.Thumbnails(
                        listOf(
                          com.revenge.innertube.models.Thumbnail(
                            url = payload.author?.avatarThumbnailUrl ?: "",
                            width = 0,
                            height = 0,
                          ),
                        ),
                      ),
                    contentText =
                      com.revenge.innertube.models.Runs(
                        listOf(
                          com.revenge.innertube.models.Run(
                            text = ApiText.clean(payload.properties?.content?.content ?: ""),
                            navigationEndpoint = null,
                          ),
                        ),
                      ),
                    publishedTimeText =
                      com.revenge.innertube.models.Runs(
                        listOf(
                          com.revenge.innertube.models.Run(
                            text = payload.properties?.publishedTime ?: "",
                            navigationEndpoint = null,
                          ),
                        ),
                      ),
                    commentId = commentId,
                    voteCount =
                      com.revenge.innertube.models.Runs(
                        listOf(com.revenge.innertube.models.Run(likeCount, null)),
                      ),
                    voteStatus =
                      when (toolbar?.likeState) {
                        "TOOLBAR_LIKE_STATE_LIKE" -> "UPVOTE"
                        else -> "INDIFFERENT"
                      },
                    replyCount = replyCount.toIntOrNull() ?: 0,
                  ),
              ),
            replies = legacyById[commentId]?.replies,
          )
        }
      }

    val nextToken =
      items
        .mapNotNull { it.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token }
        .firstOrNull()
        ?: endpoints
          .mapNotNull { endpoint ->
            endpoint.appendContinuationItemsAction?.continuationItems?.mapNotNull {
              it.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
            }
          }
          .flatten()
          .firstOrNull()

    val modernById = modern.associateBy { it.comment?.commentRenderer?.commentId }
    val ids = (legacyById.keys + modernById.keys).filterNotNull().distinct()
    // Prefer the framework model: YouTube now puts the text only there. It already received the
    // legacy replies above.
    val comments =
      ids
        .mapNotNull { id -> modernById[id] ?: legacyById[id] }
        .distinctBy { it.comment?.commentRenderer?.commentId ?: it.hashCode() }

    comments to nextToken
  }

  suspend fun commentReplies(
    replyToken: String,
  ): Result<Pair<List<com.revenge.innertube.models.comment.CommentRenderer>, String?>> =
    runCatching {
      val response =
        innerTube.next(YouTubeClient.WEB, null, null, null, null, null, replyToken)
          .body<CommentResponse>()
      val endpoints = response.onResponseReceivedEndpoints.orEmpty()
      val items =
        endpoints.flatMap { endpoint ->
          endpoint.reloadContinuationItemsCommand?.continuationItems.orEmpty() +
            endpoint.appendContinuationItemsAction?.continuationItems.orEmpty()
        }
      val legacy =
        items.mapNotNull { it.commentRenderer ?: it.commentThreadRenderer?.comment?.commentRenderer }

      val mutations = response.frameworkUpdates?.entityBatchUpdate?.mutations.orEmpty()
      val toolbarStates =
        mutations.mapNotNull { it.payload?.engagementToolbarStateEntityPayload }.associateBy { it.key }
      val surfaces =
        mutations.mapNotNull { it.payload?.engagementToolbarSurfaceEntityPayload }.associateBy { it.key }

      val modern =
        mutations.mapNotNull { mutation ->
          mutation.payload?.commentEntityPayload?.let { payload ->
            val toolbar = toolbarStates[payload.properties?.toolbarStateKey]
            val surface = surfaces[payload.properties?.toolbarSurfaceKey]
            val likeCount =
              payload.toolbar?.likeCountNotliked ?: surface?.toolbar?.likeCountNotliked ?: "0"
            com.revenge.innertube.models.comment.CommentRenderer(
              authorText =
                com.revenge.innertube.models.Runs(
                  listOf(
                    com.revenge.innertube.models.Run(
                      text = ApiText.clean(payload.author?.displayName ?: "Unknown"),
                      navigationEndpoint = null,
                    ),
                  ),
                ),
              authorThumbnail =
                com.revenge.innertube.models.Thumbnails(
                  listOf(
                    com.revenge.innertube.models.Thumbnail(
                      url = payload.author?.avatarThumbnailUrl ?: "",
                      width = 0,
                      height = 0,
                    ),
                  ),
                ),
              contentText =
                com.revenge.innertube.models.Runs(
                  listOf(
                    com.revenge.innertube.models.Run(
                      text = ApiText.clean(payload.properties?.content?.content ?: ""),
                      navigationEndpoint = null,
                    ),
                  ),
                ),
              publishedTimeText =
                com.revenge.innertube.models.Runs(
                  listOf(
                    com.revenge.innertube.models.Run(
                      text = payload.properties?.publishedTime ?: "",
                      navigationEndpoint = null,
                    ),
                  ),
                ),
              commentId = payload.properties?.commentId ?: "reply-${mutation.hashCode()}",
              voteCount =
                com.revenge.innertube.models.Runs(
                  listOf(com.revenge.innertube.models.Run(likeCount, null)),
                ),
              voteStatus =
                when (toolbar?.likeState) {
                  "TOOLBAR_LIKE_STATE_LIKE" -> "UPVOTE"
                  else -> "INDIFFERENT"
                },
            )
          }
        }

      val legacyById = legacy.associateBy { it.commentId }
      val modernById = modern.associateBy { it.commentId }
      val ids = (legacyById.keys + modernById.keys).filterNotNull().distinct()
      val merged =
        ids
          .mapNotNull { id ->
            val old = legacyById[id]
            val new = modernById[id]
            when {
              old != null && new != null ->
                old.copy(
                  voteCount = new.voteCount ?: old.voteCount,
                  voteStatus = new.voteStatus ?: old.voteStatus,
                  replyCount = new.replyCount ?: old.replyCount,
                )
              else -> new ?: old
            }
          }
          .distinctBy { it.commentId ?: it.hashCode() }

      val nextToken =
        items
          .mapNotNull { item ->
            item.continuationItemRenderer?.let { renderer ->
              renderer.continuationEndpoint?.continuationCommand?.token
                ?: renderer.button?.buttonRenderer?.command?.continuationCommand?.token
                ?: renderer.button?.buttonRenderer?.navigationEndpoint?.continuationCommand?.token
            }
          }
          .firstOrNull()
          ?: endpoints
            .mapNotNull { endpoint ->
              endpoint.appendContinuationItemsAction?.continuationItems?.mapNotNull {
                it.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
              }
            }
            .flatten()
            .firstOrNull()

      merged to nextToken
    }
}
