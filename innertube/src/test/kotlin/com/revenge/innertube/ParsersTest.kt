package com.revenge.innertube

import com.revenge.innertube.models.Badges
import com.revenge.innertube.models.BrowseEndpoint
import com.revenge.innertube.models.DefaultServiceEndpoint
import com.revenge.innertube.models.FeedbackEndpoint
import com.revenge.innertube.models.Icon
import com.revenge.innertube.models.Menu
import com.revenge.innertube.models.MusicCarouselShelfRenderer
import com.revenge.innertube.models.MusicResponsiveListItemRenderer
import com.revenge.innertube.models.MusicTwoRowItemRenderer
import com.revenge.innertube.models.NavigationEndpoint
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.Run
import com.revenge.innertube.models.Runs
import com.revenge.innertube.models.SectionListRenderer
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.Thumbnail
import com.revenge.innertube.models.ThumbnailRenderer
import com.revenge.innertube.models.Thumbnails
import com.revenge.innertube.models.ToggledServiceEndpoint
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.innertube.models.response.BrowseResponse
import com.revenge.innertube.pages.AlbumPage
import com.revenge.innertube.pages.BrowseResult
import com.revenge.innertube.pages.HomePage
import com.revenge.innertube.pages.LibraryPage
import com.revenge.innertube.pages.PageHelper
import com.revenge.innertube.pages.PlaylistPage
import com.revenge.innertube.pages.PodcastPage
import com.revenge.innertube.pages.SearchPage
import com.revenge.innertube.pages.SearchSummary
import com.revenge.innertube.pages.SearchSummaryPage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
  }

  private fun thumb(url: String = "https://img/thumb"): ThumbnailRenderer =
    ThumbnailRenderer(
      musicThumbnailRenderer =
        ThumbnailRenderer.MusicThumbnailRenderer(
          thumbnail = Thumbnails(listOf(Thumbnail(url, 60, 60))),
          thumbnailCrop = null,
          thumbnailScale = null,
        ),
      musicAnimatedThumbnailRenderer = null,
      croppedSquareThumbnailRenderer = null,
    )

  private fun musicRun(text: String, videoId: String? = null): Run =
    Run(
      text,
      NavigationEndpoint(
        watchEndpoint =
          WatchEndpoint(
            videoId = videoId,
            watchEndpointMusicSupportedConfigs =
              WatchEndpoint.WatchEndpointMusicSupportedConfigs(
                WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig(
                  "MUSIC_VIDEO_TYPE_ATV",
                ),
              ),
          ),
      ),
    )

  private fun flex(runs: List<Run>): MusicResponsiveListItemRenderer.FlexColumn =
    MusicResponsiveListItemRenderer.FlexColumn(
      MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer(
        Runs(runs),
      ),
    )

  private fun responsive(
    flex: List<MusicResponsiveListItemRenderer.FlexColumn>,
    fixed: List<MusicResponsiveListItemRenderer.FlexColumn>? = null,
    thumbnail: ThumbnailRenderer? = thumb(),
    menu: Menu? = null,
    badges: List<Badges>? = null,
    playlistItemData: MusicResponsiveListItemRenderer.PlaylistItemData? = null,
    overlay: MusicResponsiveListItemRenderer.Overlay? = null,
    navigationEndpoint: NavigationEndpoint? = null,
  ): MusicResponsiveListItemRenderer =
    MusicResponsiveListItemRenderer(
      badges = badges,
      fixedColumns = fixed,
      flexColumns = flex,
      thumbnail = thumbnail,
      menu = menu,
      playlistItemData = playlistItemData,
      overlay = overlay,
      navigationEndpoint = navigationEndpoint,
    )

  private fun pageType(pageType: String) =
    BrowseEndpoint.BrowseEndpointContextSupportedConfigs(
      BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig(
        pageType,
      ),
    )

  private fun album() =
    com.revenge.innertube.models.AlbumItem(
      browseId = "browse",
      playlistId = "playlist",
      title = "Album",
      artists = emptyList(),
      thumbnail = "album-thumb",
    )

  @Test
  fun podcastDurationLocalised() {
    assertEquals(1380, PodcastPage.parseDuration(listOf(Run("23 min"))))
    assertEquals(3900, PodcastPage.parseDuration(listOf(Run("1 h 5 min"))))
    assertEquals(3900, PodcastPage.parseDuration(listOf(Run("1:05:00"))))
    assertEquals(45, PodcastPage.parseDuration(listOf(Run("45 s"))))
  }

  @Test
  fun podcastDurationInvalid() {
    assertNull(PodcastPage.parseDuration(listOf(Run("unknown"))))
    assertNull(PodcastPage.parseDuration(listOf(Run("0 min"))))
    assertNull(PodcastPage.parseDuration(emptyList()))
  }

  @Test
  fun podcastShowIds() {
    assertEquals("MPSPPLabc", PodcastPage.showBrowseId("PLabc"))
    assertEquals("PLabc", PodcastPage.showId("MPSPPLabc"))
    assertTrue(PodcastPage.isShowBrowseId("MPSPPLabc"))
    assertFalse(PodcastPage.isShowBrowseId("PLabc"))
  }

  @Test
  fun libraryTokensAddFirst() {
    val item = toggleItem("LIBRARY_ADD", "add", "remove")
    assertEquals(
      PageHelper.LibraryFeedbackTokens("add", "remove"),
      PageHelper.extractLibraryTokensFromMenuItems(listOf(item)),
    )
  }

  @Test
  fun libraryTokensSavedFirst() {
    val item = toggleItem("LIBRARY_REMOVE", "remove", "add")
    assertEquals(
      PageHelper.LibraryFeedbackTokens("add", "remove"),
      PageHelper.extractLibraryTokensFromMenuItems(listOf(item)),
    )
  }

  @Test
  fun libraryIconsIgnoreKeep() {
    assertFalse(PageHelper.isLibraryIcon("KEEP"))
    assertFalse(PageHelper.isLibraryIcon("KEEP_OFF"))
    assertNull(
      PageHelper.extractLibraryTokensFromMenuItems(listOf(toggleItem("KEEP", "x", "y"))).addToken,
    )
  }

  private fun toggleItem(
    icon: String,
    defaultToken: String,
    toggledToken: String,
  ): Menu.MenuRenderer.Item =
    Menu.MenuRenderer.Item(
      menuNavigationItemRenderer = null,
      menuServiceItemRenderer = null,
      toggleMenuServiceItemRenderer =
        Menu.MenuRenderer.Item.ToggleMenuServiceRenderer(
          defaultIcon = Icon(icon),
          defaultServiceEndpoint =
            DefaultServiceEndpoint(
              subscribeEndpoint = null,
              feedbackEndpoint = FeedbackEndpoint(defaultToken),
            ),
          toggledServiceEndpoint = ToggledServiceEndpoint(FeedbackEndpoint(toggledToken)),
        ),
    )

  @Test
  fun homeChipFromRenderer() {
    val renderer =
      SectionListRenderer.Header.ChipCloudRenderer.Chip(
        chipCloudChipRenderer =
          SectionListRenderer.Header.ChipCloudRenderer.ChipCloudChipRenderer(
            navigationEndpoint = NavigationEndpoint(browseEndpoint = BrowseEndpoint("X")),
            onDeselectedCommand = NavigationEndpoint(browseEndpoint = BrowseEndpoint("Y")),
            text = Runs(listOf(Run("Chill"))),
            uniqueId = null,
          ),
      )
    assertEquals(
      HomePage.Chip("Chill", BrowseEndpoint("X"), BrowseEndpoint("Y")),
      HomePage.Chip.fromChipCloudChipRenderer(renderer),
    )
  }

  @Test
  fun albumSongPrefersPlaylistItemVideoId() {
    val renderer =
      responsive(
        flex = listOf(flex(listOf(musicRun("Title", "flex-video")))),
        fixed = listOf(flex(listOf(Run("3:33")))),
        playlistItemData = MusicResponsiveListItemRenderer.PlaylistItemData(null, "v1"),
        navigationEndpoint = NavigationEndpoint(watchEndpoint = WatchEndpoint(videoId = "v2")),
      )
    assertEquals("v1", AlbumPage.getSong(renderer, album())?.id)
  }

  @Test
  fun albumSongRequiresDuration() {
    val renderer =
      responsive(
        flex = listOf(flex(listOf(musicRun("Title", "v")))),
        fixed = null,
      )
    assertNull(AlbumPage.getSong(renderer, album()))
  }

  @Test
  fun playlistSongRequiresThumbnail() {
    val renderer =
      responsive(
        flex =
          listOf(
            flex(listOf(musicRun("Title", "v"))),
            flex(listOf(Run("Artist", null))),
          ),
        fixed = listOf(flex(listOf(Run("3:00")))),
        thumbnail = null,
      )
    assertNull(PlaylistPage.fromMusicResponsiveListItemRenderer(renderer))
  }

  @Test
  fun libraryUploadedSongUsesEmptyArtistId() {
    val renderer =
      responsive(
        flex =
          listOf(
            flex(listOf(musicRun("Uploaded", "v"))),
            flex(listOf(Run("Artist", null))),
          ),
      )
    val item = LibraryPage.fromMusicResponsiveListItemRenderer(renderer)
    assertTrue(item is SongItem)
    assertEquals("", (item as SongItem).artists.first().id)
  }

  @Test
  fun podcastEpisodeFromTwoRow() {
    val renderer =
      MusicTwoRowItemRenderer(
        title = Runs(listOf(Run("Episode"))),
        subtitle = null,
        subtitleBadges = null,
        menu = null,
        thumbnailRenderer = thumb(),
        navigationEndpoint =
          NavigationEndpoint(
            browseEndpoint =
              BrowseEndpoint(
                browseId = "MPEDvid",
                browseEndpointContextSupportedConfigs =
                  pageType("MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE"),
              ),
          ),
        thumbnailOverlay = null,
      )
    val song = PodcastPage.episode(renderer)
    assertEquals("vid", song?.id)
    assertEquals("MUSIC_VIDEO_TYPE_PODCAST_EPISODE", song?.musicVideoType)
  }

  @Test
  fun searchPagePrefersPodcastShow() {
    val renderer =
      responsive(
        flex =
          listOf(
            flex(listOf(Run("Show"))),
            flex(listOf(Run("Author"))),
          ),
        navigationEndpoint =
          NavigationEndpoint(
            browseEndpoint = BrowseEndpoint("MPSP123"),
            watchEndpoint = WatchEndpoint(videoId = "v"),
          ),
      )
    val item = SearchPage.toYTItem(renderer)
    assertTrue(item is PlaylistItem)
    assertTrue((item as PlaylistItem).isPodcast)
  }

  @Test
  fun searchSummaryKeepsVideosSection() {
    val video =
      SongItem(
        id = "v",
        title = "T",
        artists = emptyList(),
        musicVideoType = "MUSIC_VIDEO_TYPE_OMV",
        thumbnail = "t",
      )
    val filtered =
      SearchSummaryPage(
          summaries = listOf(SearchSummary("Videos", listOf(video))),
        )
        .filterVideoSongs(true)
    assertEquals(1, filtered.summaries.size)
    assertEquals(listOf(video), filtered.summaries.first().items)
  }

  @Test
  fun browseResultDropsEmptySection() {
    val explicitSong =
      SongItem(
        id = "v",
        title = "T",
        artists = emptyList(),
        thumbnail = "t",
        explicit = true,
      )
    val result =
      BrowseResult(
          title = null,
          items = listOf(BrowseResult.Item("Section", listOf(explicitSong))),
        )
        .filterExplicit(true)
    assertTrue(result.items.isEmpty())
  }

  @Test
  fun homePageKeepsEmptySection() {
    val explicitSong =
      SongItem(
        id = "v",
        title = "T",
        artists = emptyList(),
        thumbnail = "t",
        explicit = true,
      )
    val filtered =
      HomePage(
          chips = null,
          sections = listOf(HomePage.Section("Section", null, null, null, listOf(explicitSong))),
        )
        .filterExplicit(true)
    assertEquals(1, filtered.sections.size)
    assertTrue(filtered.sections.first().items.isEmpty())
  }

  @Test
  fun malformedRowIsDropped() {
    val renderer = responsive(flex = emptyList(), thumbnail = null)
    assertNull(SearchPage.toYTItem(renderer))
  }

  @Test
  fun unsupportedCarouselContentEmitsNothing() {
    val carousel =
      MusicCarouselShelfRenderer(
        header =
          MusicCarouselShelfRenderer.Header(
            MusicCarouselShelfRenderer.Header.MusicCarouselShelfBasicHeaderRenderer(
              strapline = null,
              title = Runs(listOf(Run("Section"))),
              thumbnail = null,
              moreContentButton = null,
            ),
          ),
        contents =
          listOf(
            MusicCarouselShelfRenderer.Content(null, null, null, null, null),
          ),
        itemSize = "MEDIUM",
        numItemsPerColumn = null,
      )
    assertNull(HomePage.Section.fromMusicCarouselShelfRenderer(carousel))
  }

  @Test
  fun albumPlaylistIdFromMicroformat() {
    val response =
      json.decodeFromString<BrowseResponse>(
        """{"microformat": {"microformatDataRenderer": {"urlCanonical": "https://music.youtube.com/playlist?list=PL123"}}, "responseContext": {}}""",
      )
    assertEquals("PL123", AlbumPage.getPlaylistId(response))
  }
}
