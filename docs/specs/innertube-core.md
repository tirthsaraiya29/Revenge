# `:innertube` Clean-Room Specification (`innertube-core`)

Status: M2 specification (implementation-independent, behavioural contract).
Module: `:innertube` (Gradle path `:innertube`, directory `innertube`).
Target namespace: `com.revenge.innertube`.
Cluster scope: the whole `innertube` module — InnerTube transport, the `YouTube` facade, the
NewPipe extractor bridge, network configuration, URL/cookie/time helpers, and every serializable
wire DTO and page model those entry points expose.

This document states observable behaviour, public API shape and wire contracts. It deliberately
omits source bodies. Any implementation that satisfies the contracts and acceptance tests is
acceptable. Where the baseline has a known defect (for example swallowing cancellation), §9 states
the normative target: the acceptance tests win.

---

## 0. Scope and non-goals

In scope (must be implemented in this module):

- `InnerTube`: HTTP transport for every YouTube Music `youtubei/v1` endpoint used by the app,
  request bodies, auth headers, retry policy, player response retrieval.
- `YouTube`: the single facade that decodes InnerTube JSON into page models and app domain items;
  every method returns `kotlin.Result` except where stated.
- `NetworkConfig`: shared Ktor/OkHttp client factory and adaptive timeout tables.
- `NewPipeExtractor` / `NewPipeDownloaderImpl` / `NewPipeUtils`: NewPipe bridge for signature
  timestamps, cipher URL de-obfuscation, throttling removal and alternate stream enumeration.
- `utils`: pagination-completing `Result` helpers, cookie/time/SHA-1 helpers, and the URL parser.
- `models` and `pages`: the exact DTO and page types the above return.

Out of scope (adjacent subsystems; they consume this module but are specified elsewhere):

- `:app` cipher (`PlayerConfigParser`, `CipherWebView`, QuickJS executor), PO-token WebView,
  SABR `n`-transform, `Fix403` instrumentation, `BotDetectionMitigator`, `SponsorBlockRepository`.
  This module only *accepts* a PO token and a signature timestamp and exposes the player response
  those subsystems need; §12 defines the boundary.
- `:lyrics` / `:youlyplus` / `:betterlyrics` LRC/TTML parsing and translation normalisation. §13
  includes boundary acceptance fixtures required by the cluster contract, because
  `YouTube.lyrics` and `YouTube.transcript` feed those parsers.

Hard rules for the implementer:

- No source body, comment or identifier may be copied from a non-clean-room baseline. Names and
  wire strings in this document are public ABI and wire contract and are therefore mandatory.
- Do not change package names, class names, method names, parameter order or default arguments
  listed here: downstream modules compile against them.
- Do not add Android UI, Compose, Hilt, Room or persistence dependencies.

---

## 1. Module build contract

`innertube/build.gradle.kts` must:

- apply `com.android.library` and the Kotlin serialization plugin;
- set `namespace = "com.revenge.innertube"`, `compileSdk = 37`, `minSdk = 26`;
- use Java/Kotlin toolchain 21 and enable core-library desugaring;
- declare `implementation` on Ktor client core, Ktor OkHttp engine, Ktor content negotiation,
  Ktor JSON serialization, Ktor content encoding, Brotli (`org.brotli:dec`), NewPipeExtractor
  (`v0.26.1`, excluding `com.google.protobuf:protobuf-java`) and PipePipeExtractor (commit
  `f8982ca9e7`, same exclusion);
- declare `api(project(":metadata"))` (not `implementation`): `CleanedMetadata` appears in the
  public signature of `SongItem.cleanedMetadata`, so consumers need the type on their compile
  classpath;
- add `coreLibraryDesugaring(libs.desugaring)`;
- add test dependencies: JUnit 4, `kotlinx-coroutines-test`, and a MockWebServer equivalent
  (`com.squareup.okhttp3:mockwebserver`) for the bounded-retry/timeout acceptance tests. If the
  catalog lacks MockWebServer, add the catalog entry; do not use a real network in tests.

`src/main/AndroidManifest.xml` may remain an empty `<manifest>` declaring no permissions or
components. The module performs network I/O but the app manifest owns `INTERNET`.

Kotlin serialization is mandatory: every type marked `@Serializable` in §10 must be serializable
with names exactly as listed.

---

## 2. Package layout and target files

The implementer must write exactly the files below. Helper classes may be `private` inside these
files; any public type must match §4–§12.

Main (`innertube/src/main/kotlin/com/revenge/innertube/`):

| File | Responsibility |
|---|---|
| `YouTube.kt` | `object YouTube`, `SearchFilter`, `LibraryFilter`, facade decoding. |
| `InnerTube.kt` | `class InnerTube`, request bodies/headers, retry, `getMediaInfo`. |
| `InnerTubeException.kt` | typed error hierarchy + classification (§9). New file required by this spec. |
| `NetworkConfig.kt` | `object NetworkConfig`, `NetworkQuality`, `TimeoutConfig`. |
| `YouTubeConstants.kt` | `object YouTubeConstants`. |
| `NewPipeExtractor.kt` | `NewPipeDownloaderImpl`, `NewPipeUtils`, `object NewPipeExtractor`, `NewPipeVideoTrack`. |
| `utils/Utils.kt` | `completed()` overloads, `toHex`, `sha1`, `parseCookieString`, `parseTime`, `isPrivateId`. |
| `utils/YouTubeUrlParser.kt` | `object YouTubeUrlParser`, `ParsedUrl`. |
| `models/*.kt` | every file listed in §10 under `com.revenge.innertube.models`. |
| `models/body/Bodies.kt` | request bodies. |
| `models/comment/Comments.kt` | comment response DTOs. |
| `models/response/*.kt` | response DTOs. |
| `pages/*.kt` | every page file listed in §11. |

Models (must exist at `models/` with these exact names): `AccountInfo.kt`, `Badges.kt`, `Context.kt`,
`Continuation.kt`, `ContinuationItemRenderer.kt`, `Endpoint.kt`, `GridRenderer.kt`, `Icon.kt`,
`IpVersion.kt`, `MediaInfo.kt`, `Menu.kt`, `MetadataExtensions.kt`, `MusicCardShelfRenderer.kt`,
`MusicCarouselShelfRenderer.kt`, `MusicHeaders.kt`, `MusicMisc.kt`,
`MusicPlaylistShelfRenderer.kt`, `MusicResponsiveListItemRenderer.kt`, `MusicShelfRenderer.kt`,
`MusicTwoRowItemRenderer.kt`, `NavigationEndpoint.kt`, `PlaylistPanel.kt`, `ResponseContext.kt`,
`ReturnYouTubeDislikeResponse.kt`, `Runs.kt`, `SearchSuggestions.kt`, `SectionListRenderer.kt`,
`Tabs.kt`, `TasteSignals.kt`, `ThumbnailRenderer.kt`, `Thumbnails.kt`,
`TwoColumnBrowseResultsRenderer.kt`, `UrlEndpoint.kt`, `YTItem.kt`, `YouTubeClient.kt`,
`YouTubeDataPage.kt`, `YouTubeLocale.kt`.

Pages (must exist at `pages/` with these exact names): `AlbumPage.kt`,
`ArtistItemsContinuationPage.kt`, `ArtistItemsPage.kt`, `ArtistPage.kt`, `BrowseResult.kt`,
`ChartsPage.kt`, `ExplorePage.kt`, `HistoryPage.kt`, `HomePage.kt`, `LibraryAlbumsPage.kt`,
`LibraryContinuationPage.kt`, `LibraryPage.kt`, `MoodAndGenres.kt`, `NewReleaseAlbumPage.kt`,
`NextPage.kt`, `PageHelper.kt`, `PlaylistContinuationPage.kt`, `PlaylistPage.kt`, `PodcastPage.kt`,
`RelatedPage.kt`, `SearchPage.kt`, `SearchSuggestionPage.kt`, `SearchSummaryPage.kt`.

Tests (`innertube/src/test/kotlin/com/revenge/innertube/`):

- `ParsersTest.kt`
- `models/RunsTest.kt`
- `models/SerializationTest.kt`
- `utils/UtilsTest.kt`
- `utils/YouTubeUrlParserTest.kt`
- `InnerTubeErrorTest.kt`
- `RetryAndCancellationTest.kt`
- `PaginationTest.kt`
- `PlayerFixtureTest.kt`
- `SearchSummaryTest.kt`
- `NewPipeBridgeTest.kt`
- `LyricsBoundaryTest.kt`

Test resources (`innertube/src/test/resources/fixtures/`): JSON captures for browse/search/next/
player/account/queue/transcript, `sw.js_data` sample, and the lyric/translation fixtures named in
§13. Fixtures may be synthetic; they must be deterministic and committed.

---

## 3. Runtime architecture and lifecycle

Two layers only:

1. `InnerTube` owns the Ktor `HttpClient` (OkHttp engine), request construction, auth headers and
   transport retry. It knows nothing about page models.
2. `YouTube` is a process-wide `object` holding one private `InnerTube` instance. It decodes
   responses into `models.response.*` DTOs, maps them to `pages.*` models / `YTItem`s, applies
   block/dislike filters, and wraps failures in `Result`.

Configuration is global mutable state on `YouTube` (delegated to `InnerTube`). It is intended to be
set once during app start, before concurrent use. No configuration property is internally
synchronised; concurrent mutation while requests are in flight is a caller error (see §9.5).

`YouTube` filters: `blockedArtists: Set<String>` and `dislikedSongs: Set<String>`. Entries are
either `"id"` or `"id||name"`. `filterBlockedArtists` splits on `||`, matches ids against
`ArtistItem.id`, `ArtistItem.channelId`, artist ids in songs/albums, playlist author id, and matches
names exactly (case-sensitive) against titles/artist names. `filterDislikedSongs` uses the part
before `||` and drops `SongItem`s whose id matches. Empty set means no filtering.

---

## 4. `com.revenge.innertube.YouTube` public API

All members are on the singleton `object YouTube`. Unless a method is marked otherwise it is
`suspend`, returns `kotlin.Result<T>`, and follows §9 (errors become `Result.failure`, cancellation
is rethrown). `T` nullability is explicit below.

### 4.1 Configuration properties

| Property | Type | Default / semantics |
|---|---|---|
| `blockedArtists` | `Set<String>` | `emptySet()`; see §3. |
| `dislikedSongs` | `Set<String>` | `emptySet()`; see §3. |
| `locale` | `YouTubeLocale` | get/set delegates to `InnerTube.locale`. Initial value: `gl = Locale.getDefault().country`, `hl = Locale.getDefault().toLanguageTag()`. |
| `visitorData` | `String?` | get/set delegates; guest identity; sent as `X-Goog-Visitor-Id`. |
| `dataSyncId` | `String?` | get/set delegates; account channel id used as `user.onBehalfOfUser`. |
| `cookie` | `String?` | get/set delegates; setting parses cookie pairs into the SAPISID lookup map. |
| `proxy` | `java.net.Proxy?` | get/set delegates; setting rebuilds and replaces the HTTP client. |
| `proxyAuth` | `String?` | get/set delegates; read when a client is built. |
| `useLoginForBrowse` | `Boolean` | get/set delegates; default `false`. When true, browse/search attach login when a cookie exists. |
| `ipVersion` | `IpVersion` | get/set delegates; affects NewPipe DNS only. |

`refreshVisitorData(): Result<String>` — calls `visitorData()`, stores the result on success.
`clearGuestSession()` — sets `visitorData = null` and `dataSyncId = null`; leaves cookie untouched.

### 4.2 Method summary

| Signature | Returns | One-line contract |
|---|---|---|
| `searchSuggestions(query: String)` | `Result<SearchSuggestions>` | `music/get_search_suggestions`; queries + recommended items. |
| `searchSummary(query: String)` | `Result<SearchSummaryPage>` | grouped search result page + `ranked`. |
| `search(query: String, filter: SearchFilter, setLogin: Boolean? = null)` | `Result<SearchResult>` | first music shelf of one filter. |
| `searchContinuation(continuation: String)` | `Result<SearchResult>` | next shelf page. |
| `album(browseId: String, withSongs: Boolean = true)` | `Result<AlbumPage>` | album page; optionally fetch tracks. |
| `albumSongs(playlistId: String, album: AlbumItem? = null)` | `Result<List<SongItem>>` | complete album track list. |
| `artist(browseId: String)` | `Result<ArtistPage>` | artist header + sections. |
| `artistItems(endpoint: BrowseEndpoint)` | `Result<ArtistItemsPage>` | "more" grid/list page. |
| `artistItemsContinuation(continuation: String)` | `Result<ArtistItemsContinuationPage>` | next artist-items page. |
| `playlist(playlistId: String)` | `Result<PlaylistPage>` | playlist/show page. |
| `podcast(showId: String)` | `Result<PlaylistPage>` | podcast show page (`MPSP`). |
| `playlistContinuation(continuation: String)` | `Result<PlaylistContinuationPage>` | next playlist track page. |
| `home(continuation: String? = null, browseId: String = "FEmusic_home", params: String? = null)` | `Result<HomePage>` | home shelves; continuation delegates. |
| `explore()` | `Result<ExplorePage>` | new releases + moods/genres. |
| `newReleaseAlbums()` | `Result<List<AlbumItem>>` | new-release grid. |
| `moodAndGenres()` | `Result<List<MoodAndGenres>>` | mood/genre grids. |
| `browse(browseId: String, params: String?)` | `Result<BrowseResult>` | generic titled-section browse. |
| `library(browseId: String, tabIndex: Int = 0)` | `Result<LibraryPage>` | library tab. |
| `libraryContinuation(continuation: String)` | `Result<LibraryContinuationPage>` | next library page. |
| `libraryRecentActivity()` | `Result<LibraryPage>` | recent-activity grid, artist pages refreshed. |
| `getChartsPage(continuation: String? = null)` | `Result<ChartsPage>` | charts sections. |
| `musicHistory()` | `Result<HistoryPage>` | listening-history shelves. |
| `likeVideo(videoId: String, like: Boolean)` | `Result<HttpResponse>` | like/unlike a video. |
| `likePlaylist(playlistId: String, like: Boolean)` | `Result<HttpResponse>` | like/unlike a playlist. |
| `subscribeChannel(channelId: String, subscribe: Boolean)` | `Result<HttpResponse>` | subscribe/unsubscribe. |
| `getChannelId(browseId: String)` | `String` (non-Result) | artist channel id or `""`. |
| `addToPlaylist(playlistId: String, videoId: String)` | `Result<HttpResponse>` | edit_playlist add. |
| `addPlaylistToPlaylist(playlistId: String, addPlaylistId: String)` | `Result<HttpResponse>` | add a playlist. |
| `removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String)` | `Result<HttpResponse>` | remove a track. |
| `moveSongPlaylist(playlistId: String, setVideoId: String, successorSetVideoId: String?)` | `Result<HttpResponse>` | move a track. |
| `createPlaylist(title: String)` | `String` (non-Result, non-suspend) | creates and returns playlist id; throws. |
| `renamePlaylist(playlistId: String, name: String)` | `Result<HttpResponse>` | rename. |
| `uploadCustomThumbnailLink(playlistId: String, image: ByteArray)` | `Result<String?>` | upload + set, returns URL. |
| `removeThumbnailPlaylist(playlistId: String)` | `Result<String?>` | remove, returns default URL. |
| `deletePlaylist(playlistId: String)` | `Result<HttpResponse>` | delete. |
| `player(videoId: String, playlistId: String? = null, client: YouTubeClient, signatureTimestamp: Int? = null, poToken: String? = null)` | `Result<PlayerResponse>` | player response. |
| `newPlaybackNonce()` | `String` (non-suspend) | 16-char tracking nonce. |
| `reportWatchtime(watchtimeUrl: String, cpn: String, startSeconds: Double, positionSeconds: Double, lengthSeconds: Double, elapsedSeconds: Double, paused: Boolean)` | `Result<Int>` | HTTP status code. |
| `registerPlayback(playlistId: String? = null, playbackTracking: String, cpn: String = newPlaybackNonce())` | `Result<HttpResponse>` | playback start ping. |
| `next(endpoint: WatchEndpoint, continuation: String? = null)` | `Result<NextResult>` | watch queue page. |
| `fetchTasteSignals(videoId: String)` | `Result<TasteSignals>` | taste metadata. |
| `parseTasteSignals(videoId: String, response: NextResponse)` | `TasteSignals` (pure, non-suspend) | keyword extraction. |
| `lyrics(endpoint: BrowseEndpoint)` | `Result<String?>` | plain unsynchronised lyrics or null. |
| `related(endpoint: BrowseEndpoint)` | `Result<RelatedPage>` | related songs/albums/artists/playlists. |
| `queue(videoIds: List<String>? = null, playlistId: String? = null)` | `Result<List<SongItem>>` | `music/get_queue`. |
| `transcript(videoId: String)` | `Result<String>` | timestamped subtitle text. |
| `visitorData()` | `Result<String>` | discover guest visitor id. |
| `accountInfo()` | `Result<AccountInfo>` | signed-in account header. |
| `feedback(tokens: List<String>)` | `Result<Boolean>` | true only if all processed. |
| `addSongToLibrary(videoId: String)` | `Result<Boolean>` | add via next token. |
| `removeSongFromLibrary(videoId: String)` | `Result<Boolean>` | remove via next token. |
| `toggleSongLibrary(videoId: String, addToLibrary: Boolean)` | `Result<Boolean>` | dispatch add/remove. |
| `getMediaInfo(videoId: String)` | `Result<MediaInfo>` | watch-page metadata. |
| `getNewPipeStreamUrls(videoId: String)` | `List<Pair<Int, String>>` (non-suspend) | itag→url. |
| `newPipePlayer(videoId: String, tempRes: PlayerResponse)` | `PlayerResponse?` (suspend) | player response with NewPipe URLs. |
| `comments(videoId: String)` | `Result<Pair<List<CommentThreadRenderer>, String?>>` | first comment page + token. |
| `commentContinuation(continuationToken: String)` | `Result<Pair<List<CommentThreadRenderer>, String?>>` | next comment page. |
| `commentReplies(replyToken: String)` | `Result<Pair<List<CommentRenderer>, String?>>` | reply page. |

### 4.3 Search semantics

- `search`: sends `search` with `query`, `params = filter.value`, optional login. Uses the **first**
  `musicShelfRenderer` found in the first tab's section list. Items are mapped via
  `SearchPage.toYTItem`; unparseable rows are dropped. Returns `continuation` from the shelf's
  continuation tokens (first `Continuation` with `nextContinuationData.continuation`), nullable.
- `searchContinuation`: sends `search` with `continuation` (also passed as `ctoken`). Reads
  `continuationContents.musicShelfContinuation.contents`. If the mapped item list is empty the
  returned `continuation` is forced to `null` (prevents an infinite request loop against an empty
  page). Filters blocked/disliked.
- `searchSummary`: sends `search` with the query and no filter. Builds:
  - a `SearchSummary` per `musicCardShelfRenderer` titled from the card header (fallback
    `YouTubeConstants.DEFAULT_TOP_RESULT`) whose items are the card itself (if mappable) plus its
    `musicResponsiveListItemRenderer` children, deduplicated by `id`;
  - a `SearchSummary` per `musicShelfRenderer` titled from the shelf title (fallback
    `YouTubeConstants.DEFAULT_OTHER_RESULTS`), items deduplicated by id;
  - flat `itemSectionRenderer` rows are additionally grouped into fixed titles, in this order:
    `"Songs"` (non-video `SongItem`s), `"Videos"` (video `SongItem`s that are not podcast
    episodes), `"Episodes"`, `"Albums"`, `"Artists"`, `"Playlists"`. Empty groups are omitted.
  - `ranked` is the deduplicated concatenation of all shelf items plus flat items, then reordered so
    that for each video song a matching album track (normalised title equality ignoring accents,
    case and punctuation; at least one common artist) is moved ahead of it. If the first top-result
    item is a video song with a matching album track, the track is prepended to the top summary.
  - blocked-artist and disliked-song filters are applied to summaries and `ranked`.
- `searchSuggestions`: sends `music/get_search_suggestions`; `contents[0]` supplies suggestion
  queries (concatenated run text per suggestion), `contents[1]` supplies recommended
  `musicResponsiveListItemRenderer`s mapped via `SearchSuggestionPage`. Missing sections produce
  empty lists. Filters blocked/disliked.

`SearchFilter` is an `@JvmInline value class SearchFilter(val value: String)` with companion values:
`FILTER_SONG`, `FILTER_VIDEO`, `FILTER_ALBUM`, `FILTER_ARTIST`, `FILTER_FEATURED_PLAYLIST`,
`FILTER_COMMUNITY_PLAYLIST`, `FILTER_PODCAST`, `FILTER_EPISODE`, whose exact opaque base64 strings
are preserved from the baseline (wire constants; see §12.3).

### 4.4 Album / artist / playlist / podcast

- `album(browseId, withSongs)`: browses with `WEB_REMIX`, login. Extracts description runs from the
  first available of: description shelves in either tab layout, `musicDetailHeaderRenderer`,
  `musicImmersiveHeaderRenderer`, editable-playlist header variants, or a
  `musicResponsiveHeaderRenderer` in tabs. Concatenates run text.
  - Private upload releases (`browseId` contains `FEmusic_library_privately_owned_release_detail`):
    builds `AlbumItem` from the detail header and playlist id from the first top-level button's
    watch-playlist endpoint; songs from the single-column first shelf.
  - Otherwise the canonical playlist id is `microformatDataRenderer.urlCanonical.substringAfterLast('=')`;
    header from the first tab's `musicResponsiveHeaderRenderer`; year from the last subtitle run
    parsed as Int; thumbnail from the last thumbnail URL.
  - `songs` = `albumSongs(playlistId, albumItem)` when `withSongs`; otherwise empty.
  - `otherVersions` = carousel entries whose header title contains `"versions"` (case-insensitive).
  - `releasesForYou` = carousel entries whose header title contains `"releases"` or `"more from"`
    (case-insensitive). `descriptionRuns` retains endpoints; `description` is their joined text.
- `albumSongs`: browses `"VL$playlistId"`; takes the first `musicPlaylistShelfRenderer` from the
  secondary contents. Appends rows from each continuation response's
  `onResponseReceivedActions[0].appendContinuationItemsAction.continuationItems`; the next token is
  read from `continuationContents.musicPlaylistShelfContinuation.continuations`. Stops when the
  token is null, repeats (visited set), or after **50** distinct tokens. Unparseable rows dropped.
- `artist`: header may be `musicImmersiveHeaderRenderer`, `musicVisualHeaderRenderer`,
  `musicHeaderRenderer` or `musicDetailHeaderRenderer`. Description runs from the first description
  shelf or the immersive description. `sections` from the first tab, mapped by
  `ArtistPage.fromSectionListRendererContent`; empty sections are omitted. Subscriber/monthly
  listener text is extracted with `extractCountText` (compact suffixes supported). `channelId` only
  from the immersive subscribe button. Play/shuffle/radio endpoints prefer immersive buttons, else
  first shelf row overlay/menu.
- `artistItems(endpoint)`: browses by `browseId` + `params`. Chooses, in order: `gridRenderer`
  (continuation from grid), `musicCarouselShelfRenderer` (no continuation), `musicShelfRenderer`
  (continuation from shelf), else `musicPlaylistShelfRenderer` (continuation from contents). Title
  falls back to the page header title then `""`. Never throws for a missing content node: returns
  an empty page.
- `artistItemsContinuation`: reads `gridContinuation`, `musicPlaylistShelfContinuation`, or
  `onResponseReceivedActions[0].appendContinuationItemsAction.continuationItems`, in that order.
  Continuation is null when the mapped item list is empty.
- `playlist(playlistId)`: browse `"VL$playlistId"` with login. Header from
  `musicResponsiveHeaderRenderer` or editable-playlist wrapper. If the header is absent **or** every
  row in the secondary shelf has `musicVideoType == MUSIC_VIDEO_TYPE_PODCAST_EPISODE`, it
  delegates to `podcast(playlistId)` and returns that page when it has songs. `isEditable` is true
  for the editable header. Track continuation prefers playlist-shelf continuation over shelf
  continuation. Related content is parsed from secondary shelves (skipping the first) and, if empty,
  from the secondary section continuation.
- `podcast(showId)`: browses `PodcastPage.showBrowseId(showId)` = `"MPSP$showId"`; builds a
  `PlaylistItem(isPodcast = true)` and episode `SongItem`s via `PodcastPage.episode`; songs
  continuation from the shelf.
- `playlistContinuation`: concatenates `musicPlaylistShelfContinuation.contents`,
  `appendContinuationItemsAction.continuationItems`, and `musicShelfContinuation.contents`; maps
  responsive rows and multi-row podcast episodes; continuation is null when the resulting song list
  is empty, otherwise the first available of the three continuation sources.

### 4.5 Home / explore / browse / library / charts / history

- `home`: if `continuation != null`, returns `homeContinuation(continuation)` (private helper)
  directly. Otherwise browses `browseId` + `params`; sections are `musicCarouselShelfRenderer`s that
  parse successfully; sections whose `isStationListenTogether` is true are removed. Chips come from
  `sectionListRenderer.header.chipCloudRenderer.chips`. Applies blocked/disliked filters.
  `homeContinuation` takes only `sectionListContinuation`, has `chips = null`, and carries the next
  continuation.
- `explore`: browses `"FEmusic_explore"`; `newReleaseAlbums` from the carousel whose "more" button
  targets `FEmusic_new_releases_albums`; `moodAndGenres` from the carousel targeting
  `FEmusic_moods_and_genres`, mapped via `MoodAndGenres.fromMusicNavigationButtonRenderer`.
- `newReleaseAlbums`: first grid of `"FEmusic_new_releases_albums"`.
- `moodAndGenres`: all `sectionListRenderer` contents of `"FEmusic_moods_and_genres"` that parse as
  a mood/genre grid. An absent content list is a parse failure, not a crash.
- `browse`: browsed page header title plus one `BrowseResult.Item` per `gridRenderer` or
  `musicCarouselShelfRenderer`; other nodes are skipped; items via
  `RelatedPage.fromMusicTwoRowItemRenderer`.
- `library(browseId, tabIndex)`: browses with login. Picks tab `tabIndex` when present. A
  `gridRenderer` yields `LibraryPage.fromMusicTwoRowItemRenderer`; otherwise a `musicShelfRenderer`
  is required and yields `LibraryPage.fromMusicResponsiveListItemRenderer`; if neither exists the
  method fails with `IllegalStateException("No content found for browseId=<id>")` (wrapped in
  `Result.failure`). Continuation from the chosen container.
- `libraryContinuation`: `gridContinuation` (two-row items) or `musicShelfContinuation`
  (responsive rows); a missing shelf continuation body is a parse failure.
- `libraryRecentActivity`: browses with `LibraryFilter.FILTER_RECENT_ACTIVITY.value` as the
  continuation. If no `sectionListContinuation.contents[0].gridRenderer.items`, returns
  `LibraryPage(emptyList(), null)`. For each parsed `ArtistItem`, re-fetches `artist(id)` and
  replaces it with the fetched `artist` but keeps the original square thumbnail. Never re-fetches
  other item kinds. Failure of an individual artist fetch is ignored (keeps original item).
- `getChartsPage(continuation)`: browses `"FEmusic_charts"` with fixed params
  `"ggMGCgQIgAQ%3D"` and optional continuation. Each carousel with a title becomes a
  `ChartSection`; each grid becomes a `NEW_RELEASES` section. Chart type from title:
  contains `"Trending"` → `TRENDING`, contains `"Top"` → `TOP`, else `GENRE`. Empty sections are
  dropped. Continuation from `sectionListContinuation`.
- `musicHistory`: browses `"FEmusic_history"` with login; one `HistorySection` per parseable
  `musicShelfRenderer`; `sections` is null only if the content list is absent (the mapper is applied
  with `mapNotNull`, so unparseable shelves are dropped).

### 4.6 Account / write actions

- `likeVideo`, `likePlaylist`, `subscribeChannel`: dispatch to the corresponding `InnerTube` call
  with `WEB_REMIX`; success is any 2xx (the transport has `expectSuccess = true`).
- `getChannelId`: non-Result. Returns `artist(browseId).artist.channelId ?: ""` on success, `""` on
  any failure. Must not throw.
- Playlist edit methods dispatch to `InnerTube` with `WEB_REMIX`; `playlistId.removePrefix("VL")` is
  applied for add/remove but not for move/rename/delete/thumbnail operations. The playlist id is
  passed through otherwise unchanged.
- `createPlaylist`: **blocking** (`runBlocking`), not `suspend`, not a `Result`. Posts
  `playlist/create`, decodes `CreatePlaylistResponse`, returns `playlistId`. Throws on transport or
  parse failure. Callers must invoke it off the main thread.
- `uploadCustomThumbnailLink`: gets an upload id from the response header `x-guploader-uploadid`
  (case-insensitive header lookup); uploads bytes; decodes `ImageUploadResponse.encryptedBlobId`;
  sets the thumbnail; returns the new thumbnail URL from `EditPlaylistResponse.newHeader` or null.
  Missing upload id is a failure.
- `removeThumbnailPlaylist`: removes the custom thumbnail and returns the resulting thumbnail URL
  or null.
- `feedback`: true only when every `feedbackResponses[i].isProcessed` is true; an empty response
  list yields `true` (all-of over an empty list).
- `addSongToLibrary` / `removeSongFromLibrary`: fetch `next(WatchEndpoint(videoId))`, find the item
  with `id == videoId`; missing item or missing `libraryAddToken`/`libraryRemoveToken` is a failure.
  Then `feedback(listOf(token))`. `toggleSongLibrary` dispatches and propagates the boolean.
- `accountInfo`: `account/account_menu`; returns the first action's popup active account header
  mapped by `toAccountInfo()`. Missing nodes are a failure.
- `visitorData()`: GETs `https://music.youtube.com/sw.js_data`, drops the first 5 characters,
  parses JSON, walks `[0][2]` as an array, and returns the first string primitive matching
  `^Cg[t|s]`. Missing/malformed data is a failure.

### 4.7 Player, playback reporting, queue and next

- `player`: posts `player` for the supplied `YouTubeClient`, always attempting login
  (`attachClient(..., withLogin = true)`), with context `user.onBehalfOfUser = dataSyncId` when the
  client supports login. Adds embedded `thirdParty.embedUrl` when `client.isEmbedded`; adds
  `playbackContext.contentPlaybackContext.signatureTimestamp` when
  `client.useSignatureTimestamp && signatureTimestamp != null`; adds
  `serviceIntegrityDimensions.poToken` when `client.useWebPoTokens && poToken != null`. Returns the
  decoded `PlayerResponse` on success **even when `playabilityStatus.status != "OK"`**: playability
  is data, not a transport error. Decoding failure is a `ParseError`.
- `newPlaybackNonce`: 16 characters drawn uniformly from
  `abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_`.
- `reportWatchtime`: replaces the prefix `https://s.youtube.com` in `watchtimeUrl` with
  `https://music.youtube.com`, sends `state = "paused"` or `"playing"`, and returns the HTTP status
  code as `Result<Int>`. Only a 2xx status is success.
- `registerPlayback`: same URL rewrite; sends `ver=2`, `c=<clientName>`, `cpn`, and when
  `playlistId != null` also `list` and `referrer=https://music.youtube.com/playlist?list=<id>`.
- `next(endpoint, continuation)`: posts `next` with `WEB_REMIX` and the endpoint's videoId,
  playlistId, playlistSetVideoId, index, params. Panel source:
  `continuationContents.playlistPanelContinuation` else the first tab's
  `musicQueueRenderer.content.playlistPanelRenderer` (absence is a failure).
  `items` are parsed from `playlistPanelVideoRenderer` through `NextPage`; `currentIndex` is the
  index of the row with `selected == true`, or null. `title` is the queue header subtitle's first
  run. `lyricsEndpoint`/`relatedEndpoint` are the browse endpoints of tabs 1 and 2 when present.
  If the last panel content is an `automixPreviewVideoRenderer`, `next` is called recursively for
  its watch-playlist endpoint and the result is merged: original items first, then automix items,
  with the original title/endpoints/currentIndex and `endpoint = automixEndpoint`. Blocked/disliked
  filters apply to the final list. There is no recursion-depth guard; implementations may cap the
  automix chain at 1 level to match observable baseline behaviour.
- `queue`: `videoIds != null` requires `videoIds.size <= YouTube.MAX_GET_QUEUE_SIZE` (1000); a
  larger list fails. Posts `music/get_queue` with both selectors. Maps `queueDatas[i].content`'s
  `playlistPanelVideoRenderer` via `NextPage`; drops unparseable rows.
- `fetchTasteSignals`: `next(videoId)` with no playlist; delegates to `parseTasteSignals`.
- `parseTasteSignals`: pure. Scans chips, shelf titles, responsive-list flex-column runs, carousel
  titles and queue header subtitles from every watch-next tab. Matching is whole-word,
  case-insensitive against a fixed genre vocabulary and a fixed mood vocabulary; a non-matching
  label of length 3–25 containing `mix`, `radio` or `station` (and no `http`) contributes the label
  with those words stripped. Returns distinct `genres` and `moodTags` in first-seen order. Never
  throws.

### 4.8 Lyrics, related, transcript, media info

- `lyrics(endpoint)`: browses `endpoint.browseId` + `endpoint.params` with `WEB_REMIX`; returns the
  concatenated text of the first `musicDescriptionShelfRenderer.description.runs`; returns
  `Result.success(null)` when no description shelf exists (song has no lyrics). The result is
  **plain unsynchronised text**, not LRC; callers must not assume timestamps.
- `related(endpoint)`: browses by browse id (params not sent). Collects songs, albums, artists,
  playlists from carousel/shelf/itemSection contents. A `SongItem` is kept only if its row's play
  navigation endpoint's `musicVideoType == MUSIC_VIDEO_TYPE_ATV` (audio track); videos are dropped.
  Unparseable rows are dropped; the four lists may be empty. No filters are applied here.
- `transcript(videoId)`: posts `get_transcript` with `WEB` and fixed API key
  `AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3`; `params` is Base64 of `"\n" + U+000B + videoId`.
  Joins cue groups with `"\n"`. Each line is `"[%02d:%02d.%03d]" + cueText`, where the timestamp is
  derived from `startOffsetMs` (minutes, seconds, milliseconds) and cue text is `simpleText` with
  leading/trailing `♪` and spaces removed. The first cue of each group is used. A missing cue body
  is a failure. The output is deterministic, one line per group, no trailing newline.
- `getMediaInfo(videoId)`: delegates to `InnerTube.getMediaInfo`; see §5.5.
- `getNewPipeStreamUrls` / `newPipePlayer`: see §7.3.

### 4.9 Comments

- `comments(videoId)`: `next` with `WEB` (not WEB_REMIX) and no continuation. Finds a comment
  continuation token, preferring, in order: the first `continuationItemRenderer` directly in the
  two-column results content, then the first continuation item inside an `itemSectionRenderer`, then
  the comments engagement panel (`panelIdentifier == "engagement-panel-comments-section"`). No token
  is a failure. Returns `commentContinuation(token)`.
- `commentContinuation(token)`: `next` with `WEB` and the continuation. Reads
  `onResponseReceivedEndpoints[*].reloadContinuationItemsCommand.continuationItems` and
  `.appendContinuationItemsAction.continuationItems`. Merges legacy `commentThreadRenderer`s with
  framework `commentEntityPayload` mutations: toolbar state and surface payloads are indexed by key;
  like/reply counts prefer payload toolbar, then surface toolbar, then `"0"`; vote status
  `TOOLBAR_LIKE_STATE_LIKE` maps to `"UPVOTE"`, everything else to `"INDIFFERENT"`; author, avatar,
  content and published time come from the framework payload when present. Framework entries take
  precedence for text; legacy replies are attached when ids match. Next token is the first
  continuation token found. Returns `(comments, nextToken)`.
- `commentReplies(token)`: same continuation call, but returns individual `CommentRenderer`s. When
  both legacy and framework rows have the same id, merge them: framework vote count/status/reply
  count override legacy. Next-token lookup also accepts a continuation command nested in a
  `buttonRenderer.command` or `buttonRenderer.navigationEndpoint`. Returns `(replies, nextToken)`.
- All three methods return empty lists rather than throwing when a page legitimately has no rows;
  only missing/invalid JSON is a failure.

---

## 5. `com.revenge.innertube.InnerTube`

`class InnerTube` is the HTTP-only transport. It is constructible with a no-argument constructor.

### 5.1 State

| Property | Type | Default | Semantics |
|---|---|---|---|
| `locale` | `YouTubeLocale` | host defaults (`gl`, `hl`) | used in context and `Accept-Language`. |
| `visitorData` | `String?` | null | `X-Goog-Visitor-Id` and context client visitorData. |
| `dataSyncId` | `String?` | null | context `user.onBehalfOfUser` when the client supports login. |
| `cookie` | `String?` | null | setter parses cookie pairs; SAPISID triggers `SAPISIDHASH`. |
| `proxy` | `Proxy?` | null | setter closes the current client and builds a new one. |
| `proxyAuth` | `String?` | null | proxy authenticator header value. Changing it *after* construction has no effect until `proxy` is set again. |
| `useLoginForBrowse` | `Boolean` | false | default login policy for browse and search. |
| `ipVersion` | `IpVersion` | `AUTO` | stored only; NewPipe DNS consumes it. |

### 5.2 Transport configuration

The private client uses OkHttp with: `expectSuccess = true`; JSON
`ignoreUnknownKeys = true`, `explicitNulls = false`, `encodeDefaults = true`; gzip (quality 0.9) and
deflate (0.8); request timeout 60 000 ms, connect 30 000 ms, socket 60 000 ms; connection pool of 10
connections kept 5 minutes; HTTP/2 preferred, HTTP/1.1 fallback; `retryOnConnectionFailure = true`;
a 50 MiB disk cache under `java.io.tmpdir/revenge_http_cache`; proxy and proxy authenticator from
the current properties. Default request: base URL `https://music.youtube.com/youtubei/v1/`, headers
`Accept: application/json`, `Accept-Language: <hl>,<gl>;q=0.9,en;q=0.8`,
`Cache-Control: no-cache`.

`attachClient(client, withLogin = false)` adds: `Content-Type: application/json`,
`X-Goog-Api-Format-Version: 1`, `X-YouTube-Client-Name: <clientId>`,
`X-YouTube-Client-Version: <clientVersion>`, `X-Origin: https://music.youtube.com`,
`Referer: https://music.youtube.com/`, `X-Goog-Visitor-Id` when visitorData is non-null,
`cookie` plus `Authorization: SAPISIDHASH <epochSeconds>_<sha1("<epochSeconds> <SAPISID> https://music.youtube.com")>`
when `withLogin && client.loginSupported && cookie != null` (only when SAPISID exists), `User-Agent:
<client.userAgent>`, and query `prettyPrint=false`.

Login policy: search uses `(setLogin ?: useLoginForBrowse) && !cookie.isNullOrEmpty()`; browse uses
`(setLogin || useLoginForBrowse) && !cookie.isNullOrEmpty()`; player, next, feedback, account menu,
likes, subscriptions, playlist writes and playback reporting always request login (subject to
`loginSupported`). Context `user.onBehalfOfUser` is non-null only when the client supports login and
the caller passed dataSyncId for that endpoint (search/browse pass it only when login is active;
next/player/writes always pass it; search-suggestions/queue/transcript pass null).

### 5.3 Methods

All methods below are `suspend` and return the Ktor `HttpResponse` unless stated. They all run
through the retry wrapper §9.2.

- `search(client, query: String? = null, params: String? = null, continuation: String? = null, setLogin: Boolean? = null)` — POST `search`; sends `query`, `params`; passes the continuation both as query parameter `continuation` and `ctoken`. Body `SearchBody(context, query, params)`.
- `player(client, videoId: String, playlistId: String?, signatureTimestamp: Int?, poToken: String? = null)` — POST `player`; body as §5.2/§4.7. `contentCheckOk = true`, `racyCheckOk = true`.
- `registerPlayback(url: String, cpn: String, playlistId: String?, client: YouTubeClient = WEB_REMIX)` — GET the absolute `url` with `ver=2`, `c`, `cpn`, plus `list`/`referrer` when a playlist id is present.
- `reportWatchtime(url, cpn, startSeconds, positionSeconds, lengthSeconds, elapsedSeconds, state, client = WEB_REMIX)` — GET with parameters `ver=2`, `c`, `cver`, `cpn`, `cmt=positionSeconds`, `st=startSeconds`, `et=positionSeconds`, `rt=elapsedSeconds`, `rtn=elapsedSeconds`, `len=lengthSeconds`, `state`.
- `browse(client, browseId: String? = null, params: String? = null, continuation: String? = null, setLogin: Boolean = false)` — POST `browse`; body `BrowseBody`.
- `next(client, videoId: String?, playlistId: String?, playlistSetVideoId: String?, index: Int?, params: String?, continuation: String? = null)` — POST `next`; body `NextBody`.
- `feedback(client, tokens: List<String>)` — POST `feedback`; `FeedbackBody(context, feedbackTokens=tokens, isFeedbackTokenUnencrypted=false, shouldMerge=false)`.
- `getSearchSuggestions(client, input: String)` — POST `music/get_search_suggestions`; body `GetSearchSuggestionsBody(context, input)`.
- `getQueue(client, videoIds: List<String>?, playlistId: String?)` — POST `music/get_queue`; body `GetQueueBody`.
- `getTranscript(client, videoId: String)` — POST absolute `https://music.youtube.com/youtubei/v1/get_transcript` with query `key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3`, explicit `Content-Type: application/json`, body `GetTranscriptBody(context, params)` where `params = Base64("\n" + U+000B + videoId)`.
- `getSwJsData()` — GET absolute `https://music.youtube.com/sw.js_data`.
- `accountMenu(client)` — POST `account/account_menu`; `AccountMenuBody`.
- `likeVideo/unlikeVideo(client, videoId)`, `likePlaylist/unlikePlaylist(client, playlistId)` — POST `like/like` or `like/removelike` with `LikeBody(context, target)`.
- `subscribeChannel/unsubscribeChannel(client, channelId, params: String? = null)` — POST `subscription/subscribe` / `subscription/unsubscribe`; `SubscribeBody(channelIds=listOf(channelId), context)`. `params` is accepted but not sent (wire parity).
- `addToPlaylist(client, playlistId, videoId)` — edit playlist with `AddVideoAction(addedVideoId=videoId)` and id `removePrefix("VL")`.
- `addPlaylistToPlaylist(client, playlistId, addPlaylistId)` — `AddPlaylistAction(addedFullListId=addPlaylistId)`, id prefix-stripped.
- `removeFromPlaylist(client, playlistId, videoId, setVideoId)` — `RemoveVideoAction`, prefix-stripped.
- `moveSongPlaylist(client, playlistId, setVideoId, successorSetVideoId)` — `MoveVideoAction`; id **not** stripped.
- `createPlaylist(client, title)` — POST `playlist/create`, `CreatePlaylistBody`.
- `renamePlaylist(client, playlistId, name)` — edit playlist with `RenamePlaylistAction`.
- `getUploadCustomThumbnailLink(client, contentLength: Int)` — POST absolute
  `https://music.youtube.com/playlist_image_upload/playlist_custom_thumbnail` with
  `X-Goog-Upload-Command: start`, `X-Goog-Upload-Protocol: resumable`,
  `X-Goog-Upload-Header-Content-Length: <n>`.
- `uploadCustomThumbnail(client, uploadId: String, image: ByteArray)` — same URL with query
  `upload_id`, `upload_protocol=resumable`, headers `X-Goog-Upload-Command: upload, finalize`,
  `X-Goog-Upload-Offset: 0`, raw body.
- `setThumbnailPlaylist(client, playlistId, blobId)` — `SetCustomThumbnailAction` with
  `playlistScottyEncryptedBlobId`.
- `removeThumbnailPlaylist(client, playlistId)` — `RemoveCustomThumbnailAction`.
- `deletePlaylist(client, playlistId)` — POST `playlist/delete`, `PlaylistDeleteBody`.
- `initSongUpload(filename: String, contentLength: Long)` — POST absolute
  `https://upload.youtube.com/upload/usermusic/http?authuser=0`; SAPISID auth; headers
  `X-Goog-Upload-Command: start`, `X-Goog-Upload-Protocol: resumable`,
  `X-Goog-Upload-Header-Content-Length`, `X-Goog-AuthUser: 0`, `Origin`; form body
  `filename=<filename>` (not URL-encoded).
- `uploadSongData(uploadUrl, data: ByteArray, onProgress: ((Float) -> Unit)? = null)` — POST
  `uploadUrl`; SAPISID auth; headers `X-Goog-Upload-Command: upload, finalize`,
  `X-Goog-Upload-Offset: 0`, `X-Goog-AuthUser: 0`, `Origin`; form content; reports
  `sent/total` progress when total is known.
- `deletePrivatelyOwnedEntity(entityId: String)` — POST absolute
  `https://music.youtube.com/youtubei/v1/music/delete_privately_owned_entity` with
  `key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3`, `prettyPrint=false`, JSON body
  `{"context":<context JSON>,"entityId":"<entityId>"}` built with `WEB_REMIX` context and SAPISID
  auth.
- `getMediaInfo(videoId): Result<MediaInfo>` — see §5.5.

### 5.4 Retry wrapper

Every method above (including GETs) is wrapped by the retry policy in §9.2. Exceptions thrown by
body decoding are outside the wrapper's retry classification and surface immediately.

### 5.5 `InnerTube.getMediaInfo(videoId)`

Returns `Result<MediaInfo>` (non-throwing to the caller). It calls `next` with the `WEB` client and
the videoId, then the Return-YouTube-Dislike endpoint
`https://returnyoutubedislikeapi.com/Votes?videoId=<videoId>` with `Accept`/content type JSON.
Fields: `videoId` as passed; `title` and `author` cleaned through `ApiText.clean`; `authorId` from
the owner browse endpoint; `authorThumbnail` from the owner thumbnail whose `height == 48`, with
`s48` replaced by `s960`; `description` from the attributed description content; `subscribers` the
first whitespace token of the owner subscriber count text; `uploadDate` the primary date text;
`viewCount`, `like`, `dislike` from the dislike response. Any missing node yields null for that
field. Missing `primary`/`secondary` is allowed; a missing dislike response is a failure. The whole
call is wrapped in `Result`.

---

## 6. `com.revenge.innertube.NetworkConfig`

`object NetworkConfig` — stateless factory. Only used by auxiliary fetchers; the InnerTube transport
builds its own client. No call sites exist in the current tree, so this API is maintained for
source compatibility and must compile.

- `createOptimizedHttpClient(cacheDir: File? = null, enableCache: Boolean = true): HttpClient` —
  OkHttp Ktor client with: `expectSuccess = true`; JSON with `ignoreUnknownKeys=true`,
  `explicitNulls=false`, `encodeDefaults=true`, `isLenient=true`; gzip 0.9 and deflate 0.8; request
  timeout 60 000 ms, connect 30 000 ms, socket 60 000 ms; OkHttp connect 30 s, read 60 s, write 60 s;
  `retryOnConnectionFailure(true)`; when `enableCache`, a 50 MiB disk cache at `cacheDir` or
  `java.io.tmpdir/revenge_http_cache`.
- `createYouTubeMusicClient(cacheDir: File? = null): HttpClient` — identical to
  `createOptimizedHttpClient(cacheDir)`.
- `getAdaptiveTimeouts(networkQuality: NetworkQuality): TimeoutConfig` — table:

| Quality | connectTimeout | readTimeout | requestTimeout |
|---|---|---|---|
| `EXCELLENT` | 10 000 | 30 000 | 45 000 |
| `GOOD` | 20 000 | 45 000 | 60 000 |
| `POOR` | 30 000 | 60 000 | 90 000 |
| `UNKNOWN` | 30 000 | 60 000 | 60 000 |

All values are milliseconds. `enum class NetworkQuality { EXCELLENT, GOOD, POOR, UNKNOWN }`.
`data class TimeoutConfig(val connectTimeout: Long, val readTimeout: Long, val requestTimeout: Long)`.
Total function: every enum value has an entry; no nulls.

---

## 7. NewPipe bridge (`NewPipeExtractor.kt`)

### 7.1 `class NewPipeDownloaderImpl(proxy: Proxy?, proxyAuth: String? = null) : Downloader`

Constructs an OkHttp client whose DNS filters resolved addresses by `YouTube.ipVersion`
(`IPV4` → IPv4 addresses, `IPV6` → IPv6, falling back to all addresses when the filtered list is
empty; `AUTO` → all), and whose `ProxySelector` returns `YouTube.proxy ?: Proxy.NO_PROXY` **at request
time** (the constructor `proxy` argument is retained for signature compatibility but the live
`YouTube.proxy` is authoritative). The proxy authenticator writes `Proxy-Authorization` from
`YouTube.proxyAuth`. `execute(request: Request): Response`:

- method/body from the NewPipe request;
- headers: `User-Agent: YouTubeClient.USER_AGENT_WEB`, `Cookie: <YouTube.cookie>` when non-null, then
  every NewPipe header (multi-valued headers replace the existing header and add all values);
- a 429 response throws `ReCaptchaException("reCaptcha Challenge requested", url)`;
- returns `Response(code, message, headersMultiMap, body ?: "", finalUrl)`;
- I/O errors propagate as `IOException`.

### 7.2 `class NewPipeUtils(downloader: Downloader)`

Constructor calls `NewPipe.init(downloader)` (process-global; last constructed wins).

- `getSignatureTimestamp(videoId: String): Result<Int>` — `YoutubeJavaScriptPlayerManager.getSignatureTimestamp`;
  failures are `Result.failure`.
- `getStreamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String?` — if
  `format.url` is non-null use it; else parse `format.signatureCipher` query string: `s` and `sp`
  and `url` are required; de-obfuscate `s` with the player manager and substitute as `sp` query
  parameter; then run throttling-parameter de-obfuscation (`n` transform) on the URL. Any missing
  parameter, parse failure, or de-obfuscation failure returns `null` (never throws).
- `object NewPipeExtractor` — lazy, `@Synchronized` initialisation of a single downloader/utility
  pair using the current `YouTube.proxy`/`proxyAuth`; subsequent proxy changes do **not** rebuild it.
  - `getSignatureTimestamp(videoId): Result<Int>` — delegates.
  - `getStreamUrl(format, videoId): String?` — delegates.
  - `newPipePlayer(videoId): List<Pair<Int,String>>` — `StreamInfo.getInfo(service 0, watch URL)`;
    returns `(itag, content)` for audio, video and video-only streams whose `itagItem.id` and
    `content` are non-null. Any exception (including no streams) returns `emptyList()`.
  - `getVideoUrlAndDuration(videoId): Pair<String, Long>?` — chooses a video stream by this
    preference: MP4 and height 1080–1440 (highest), then any 1080–1440, then MP4 720–1079, then any
    720–1079, then any MP4 (highest), then highest video. Returns `(url, duration)` only for a
    non-blank URL; otherwise null. `videoStreams + videoOnlyStreams` are de-duplicated by content.
  - `getVideoTracks(videoId): List<NewPipeVideoTrack>` — muxed `videoStreams` labelled `"<height>p"`
    (`height <= 0` → 360) with `isMuxed = true, audioUrl = null`; video-only streams labelled
    `"<height>p"` (`height <= 0` → 720) paired with the best `audioStreams` content by average
    bitrate. Streams without content are skipped. Any exception → `emptyList()`.
- `data class NewPipeVideoTrack(val label: String, val height: Int, val videoUrl: String, val audioUrl: String? = null, val isMuxed: Boolean = true)`.

All NewPipe calls are **blocking**; callers must run them off the main thread. This module does no
synchronisation around them except the lazy singleton init.

### 7.3 `YouTube` NewPipe helpers

- `getNewPipeStreamUrls(videoId)` is non-suspend and returns `NewPipeExtractor.newPipePlayer(videoId)`.
- `newPipePlayer(videoId, tempRes)`: returns null when `tempRes.playabilityStatus.status != "OK"`,
  when `getNewPipeStreamUrls` is empty, or when `streamingData` is null. Otherwise copies formats
  and adaptive formats, replacing `url` with the NewPipe URL whose itag matches; if neither list
  yields a non-null URL, returns null. The returned response keeps every other field unchanged.

---

## 8. Utilities, URL parser, constants

### 8.1 `com.revenge.innertube.utils` (`Utils.kt`)

- `suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage>` (JVM name
  `completedLibrary`): resolves the first page; walks `songsContinuation` up to **50** distinct
  tokens (visited-token set). Each page is fetched with `YouTube.playlistContinuation`; a failure is
  wrapped in `IllegalStateException("Playlist pagination failed", cause)` and fails the whole
  result. Two consecutive empty pages stop the walk; non-empty pages reset the streak. Returns a
  page with all songs appended, `songsContinuation = null`, and `continuation = first.continuation`.
  A `CancellationException` is always rethrown (from the first result or any continuation).
- `suspend fun Result<LibraryPage>.completed(): Result<LibraryPage>` (JVM name `completedPlaylist`):
  the same walk for `items` / `continuation` using `YouTube.libraryContinuation`, wrapping failures
  as `IllegalStateException("Library pagination failed", cause)`, and returning
  `LibraryPage(items, continuation = null)`. Cancellation rethrown.
- `ByteArray.toHex(): String` — lowercase, two hex digits per byte, no separators.
- `sha1(str: String): String` — SHA-1 of UTF-8 bytes as lowercase hex.
- `parseCookieString(cookie: String): Map<String,String>` — split on `"; "`, drop empty parts, split
  each part on the **first** `=`; parts without `=` are skipped; values may contain `=`; an empty
  value after `=` yields `""`. Duplicate names: last one wins (map semantics).
- `String.parseTime(): Int?` — splits on `:`; exactly 2 numeric parts → `mm*60+ss`; exactly 3 →
  `hh*3600+mm*60+ss`; otherwise null. Non-numeric parts make the numeric list shorter, so `"1:x:3"`
  has 2 numeric parts and parses as `1*60+3` (documented quirk); `"1:2:3:4"` → null.
- `isPrivateId(browseId: String): Boolean` — true iff the id contains `"privately"`.

### 8.2 `com.revenge.innertube.utils.YouTubeUrlParser`

`object YouTubeUrlParser` with `sealed class ParsedUrl { abstract val id: String }` and
`data class Video(override val id: String)`, `data class Artist(override val id: String)`.

- `isYouTubeUrl(text: String): Boolean` — `parse(text) != null`.
- `parse(url: String): ParsedUrl?` — trims; tries video patterns in order:
  `(https?://)?(www.)?(music.)?youtube.com/watch?...v=<11 chars>`, `youtu.be/<11 chars>`,
  `youtube.com/shorts/<11 chars>`. Video ids are exactly `[A-Za-z0-9_-]{11}`. If no video matches
  **and** the trimmed URL contains `music.youtube.com`, tries artist patterns:
  `music.youtube.com/channel/<id>` and `music.youtube.com/browse/(MPRE<id>)` where the captured id
  includes the `MPRE` prefix and may contain `[A-Za-z0-9_-]`. Returns null otherwise. Non-music
  hosts never yield an Artist.
- `extractVideoId(url): String?` — video id or null.
- `createWatchEndpoint(url): WatchEndpoint?` — `WatchEndpoint(videoId = id)` for a video URL, else
  null.

### 8.3 `com.revenge.innertube.YouTubeConstants`

`object YouTubeConstants { const val DEFAULT_TOP_RESULT = "Top result"; const val DEFAULT_OTHER_RESULTS = "Other" }`.

---

## 9. Errors, retries, cancellation, threading

### 9.1 Typed error model (`InnerTubeException.kt`)

`com.revenge.innertube.InnerTubeException` is a sealed `Exception` hierarchy carried as the
`exceptionOrNull()` of `Result.failure`. Existing callers that only use `getOrNull`/`getOrThrow`/
`onFailure` stay source-compatible.

```
sealed class InnerTubeException(message: String, cause: Throwable? = null) : Exception(message, cause) {
  class TransportError(cause: Throwable) : InnerTubeException("Transport failure", cause)
  class AuthRequired(message: String, cause: Throwable? = null) : InnerTubeException(message, cause)
  class BotCheck(message: String, cause: Throwable? = null) : InnerTubeException(message, cause)
  class HttpError(val statusCode: Int, message: String, cause: Throwable? = null) : InnerTubeException(message, cause)
  class ParseError(message: String, cause: Throwable? = null) : InnerTubeException(message, cause)
  class Unavailable(val status: String, val reason: String?) : InnerTubeException("Playability: $status${reason?.let { " ($it)" } ?: ""}")
  class ConfigurationError(message: String) : InnerTubeException(message)
  class IllegalArgumentError(message: String) : InnerTubeException(message)
}
```

`object InnerTubeErrors` (same file) exposes pure classifiers:

- `fun classify(throwable: Throwable): InnerTubeException` — idempotent on an existing
  `InnerTubeException`; `CancellationException` is rethrown, never classified; `java.io.IOException`
  and Ktor timeout/connect exceptions → `TransportError`; Ktor `ClientRequestException`/response
  exceptions → status mapping below; serialization exceptions → `ParseError`; assertions →
  `IllegalArgumentError`; other exceptions → `ParseError`.
- `fun fromHttpStatus(code: Int, message: String? = null): InnerTubeException` — `401` →
  `AuthRequired`; `403` → `BotCheck`; `429` → `BotCheck` (rate-limited); all other non-2xx codes →
  `HttpError(code, message)`.
- `fun isRetryable(e: InnerTubeException): Boolean` — true for `TransportError` and `5xx`
  `HttpError`; false for `AuthRequired`, `BotCheck`, parse, unavailable and configuration errors.

Mapping rules:

| Source | Result |
|---|---|
| socket/DNS/connect/read timeout, `IOException` | `TransportError` |
| HTTP 401 | `AuthRequired` |
| HTTP 403, 429 | `BotCheck` |
| other HTTP status (with `expectSuccess=true`) | `HttpError` |
| JSON decode / missing required field | `ParseError` |
| absent panel/header/content that the method requires | `ParseError` |
| `queue` size assertion | `IllegalArgumentError` |
| player `playabilityStatus != "OK"` | **not** an error; returned as data |
| coroutine cancellation | rethrown, never wrapped |

### 9.2 Transport retry policy

The retry wrapper runs the request block at most **3 attempts**. It retries only `IOException` (and
subclasses). Backoff: first retry waits 500 ms, second waits 1000 ms. On the third failure the
original `IOException` is rethrown (then classified by the facade). HTTP status failures produced by
`expectSuccess=true` are **not** retried here. `delay` is coroutine-cancellable; a cancellation
raised during backoff propagates immediately and no further attempt runs. The wrapper must not catch
`CancellationException` as a retryable failure.

`403` is a `BotCheck`, not a transport error, and is not retried inside `InnerTube`. Higher layers
may retry with a different client or rotate the guest session; see §12.4. A retry that later
succeeds must replace the earlier failure in the returned `Result` (callers see only the final
outcome).

### 9.3 Facade error handling and cancellation

Every `YouTube` `suspend` method that returns `Result` must:

1. catch `Throwable` from transport/decoding and map it with `InnerTubeErrors.classify`;
2. **rethrow `CancellationException`** before any catch-all (Kotlin `runCatching` alone is not
   acceptable because it swallows cancellation);
3. return `Result.failure(typedError)` for every other failure.

This is a normative change from any baseline that used bare `runCatching`. The pagination helpers
in §8.1 already rethrow cancellation and are the reference behaviour. Non-`suspend` methods
(`createPlaylist`, `newPlaybackNonce`, `parseTasteSignals`, `getNewPipeStreamUrls`) and
`getChannelId` follow their stated contract; `getChannelId` converts failures to `""`.

### 9.4 Empty/null behaviour

- Collections returned in pages are never null except where the data class field is declared
  nullable (`PlaylistPage.related`, `SearchSuggestions` fields are non-null lists).
- "No results" is a successful empty list/page, not a failure, unless §4 says the required node is
  missing (then `ParseError`).
- `lyrics` distinguishes "no lyrics" (`success(null)`) from "request failed" (`failure`).
- `artistItems` on a page with no recognised container returns a successful empty page.

### 9.5 Threading

- `YouTube` and `InnerTube` are mutable singletons; they are thread-safe for concurrent *requests*
  as long as configuration (`locale`, `cookie`, `proxy`, `visitorData`, `dataSyncId`, filter sets) is
  not mutated concurrently. Set configuration on the main thread at startup, then use from any
  dispatcher.
- Setting `InnerTube.proxy` closes and replaces the client. Do not do this while requests are in
  flight.
- `NewPipeExtractor` initialisation is `@Synchronized`. NewPipe extraction itself is blocking.
- Page/DTO data classes are immutable values and may cross threads freely.
- No method blocks a suspend caller except the explicitly blocking NewPipe and `createPlaylist`
  paths; all HTTP calls use coroutine-aware Ktor I/O.

---

## 10. Wire DTOs (`com.revenge.innertube.models`)

All types below marked `@Serializable` must serialize with the property names shown. `?` means a
nullable Kotlin type; `= x` is a default. Decoding rules for every DTO:

- Unknown JSON keys are ignored (`ignoreUnknownKeys = true`).
- With `explicitNulls = false`, a **missing key for a nullable property decodes as null**, including
  properties without a declared default. A missing key for a non-nullable property without a
  default is a `MissingFieldException` (→ `ParseError`).
- Encoding omits null properties (`explicitNulls = false`) and includes defaults
  (`encodeDefaults = true`), so request bodies carry their defaulted fields and omit nulls.
- `@JsonNames` aliases are part of the wire contract: any listed alias must decode into the same
  property.

### 10.1 Request envelope and bodies (`models/Context.kt`, `models/body/Bodies.kt`)

- `Context(client: Client, thirdParty: ThirdParty? = null, request: Request = Request(), user: User = User())`
  - `Client(clientName: String, clientVersion: String, osName: String? = null, osVersion: String? = null, deviceMake: String? = null, deviceModel: String? = null, androidSdkVersion: String? = null, gl: String, hl: String, visitorData: String?)`
  - `ThirdParty(embedUrl: String)`
  - `Request(internalExperimentFlags: Array<String> = emptyArray(), useSsl: Boolean = true)`
  - `User(lockedSafetyMode: Boolean = false, onBehalfOfUser: String? = null)`
- `AccountMenuBody(context: Context, deviceTheme: String = "DEVICE_THEME_SELECTED", userInterfaceTheme: String = "USER_INTERFACE_THEME_DARK")`
- `BrowseBody(context, browseId: String?, params: String?, continuation: String?)`
- `CreatePlaylistBody(context, title: String, privacyStatus: String = "PRIVATE", videoIds: List<String>? = null)`; nested `object PrivacyStatus { PRIVATE/PUBLIC/UNLISTED }`.
- `EditPlaylistBody(context, playlistId: String, actions: List<Action>)`; `Action` is a sealed class:
  - `AddVideoAction(action = "ACTION_ADD_VIDEO", addedVideoId: String)`
  - `AddPlaylistAction(action = "ACTION_ADD_PLAYLIST", addedFullListId: String)`
  - `MoveVideoAction(action = "ACTION_MOVE_VIDEO_BEFORE", setVideoId: String, movedSetVideoIdSuccessor: String?)`
  - `RemoveVideoAction(action = "ACTION_REMOVE_VIDEO", setVideoId: String, removedVideoId: String)`
  - `RenamePlaylistAction(action = "ACTION_SET_PLAYLIST_NAME", playlistName: String)`
  - `SetCustomThumbnailAction(action = "ACTION_SET_CUSTOM_THUMBNAIL", addedCustomThumbnail: AddedCustomThumbnail)`; `AddedCustomThumbnail(imageKey: ImageKey = ImageKey("studio_square_thumbnail", "PLAYLIST_IMAGE_TYPE_CUSTOM_THUMBNAIL"), playlistScottyEncryptedBlobId: String)`; `ImageKey(name: String, type: String)`
  - `RemoveCustomThumbnailAction(action = "ACTION_REMOVE_CUSTOM_THUMBNAIL", deletedCustomThumbnail = DeletedCustomThumbnail())`; `DeletedCustomThumbnail(name = "studio_square_thumbnail", type = "PLAYLIST_IMAGE_TYPE_CUSTOM_THUMBNAIL")`
  - `typealias Action = EditPlaylistBody.Action` at file level (source compatibility).
- `FeedbackBody(context, feedbackTokens: List<String>, isFeedbackTokenUnencrypted: Boolean = false, shouldMerge: Boolean = false)`
- `GetQueueBody(context, videoIds: List<String>?, playlistId: String?)`
- `GetSearchSuggestionsBody(context, input: String)`
- `GetTranscriptBody(context, params: String)`
- `LikeBody(context, target: Target)`; sealed `Target`: `VideoTarget(videoId)`, `PlaylistTarget(playlistId)`.
- `NextBody(context, videoId: String?, playlistId: String?, playlistSetVideoId: String?, index: Int?, params: String?, continuation: String?)`
- `PlayerBody(context, videoId: String, playlistId: String?, playbackContext: PlaybackContext? = null, serviceIntegrityDimensions: ServiceIntegrityDimensions? = null, contentCheckOk: Boolean = true, racyCheckOk: Boolean = true)`; `PlaybackContext(contentPlaybackContext: ContentPlaybackContext)`, `ContentPlaybackContext(signatureTimestamp: Int)`, `ServiceIntegrityDimensions(poToken: String)`.
- `PlaylistDeleteBody(context, playlistId: String)`
- `SearchBody(context, query: String?, params: String?)`
- `SubscribeBody(channelIds: List<String>, context: Context)`

### 10.2 Core model (`models/*.kt`)

- `YouTubeLocale(gl: String, hl: String)`.
- `enum IpVersion { AUTO, IPV4, IPV6 }` (not serializable).
- `YouTubeClient(clientName, clientVersion, clientId, userAgent, osName?, osVersion?, deviceMake?, deviceModel?, androidSdkVersion?, buildId?, cronetVersion?, packageName?, friendlyName?, loginSupported=false, loginRequired=false, useSignatureTimestamp=false, isEmbedded=false, useWebPoTokens=false)`; `fun toContext(locale, visitorData, dataSyncId)`. Companion constants: `USER_AGENT_WEB`, `ORIGIN_YOUTUBE_MUSIC = "https://music.youtube.com"`, `REFERER_YOUTUBE_MUSIC = "https://music.youtube.com/"`, `API_URL_YOUTUBE_MUSIC = "https://music.youtube.com/youtubei/v1/"`, and clients `WEB`, `WEB_REMIX`, `WEB_CREATOR`, `TVHTML5`, `TVHTML5_SIMPLY_EMBEDDED_PLAYER`, `IOS`, `MOBILE`, `ANDROID_NO_SDK`, `ANDROID_VR_NO_AUTH`, `ANDROID_VR_1_61_48`, `ANDROID_VR_1_65_10`, `ANDROID_VR_1_43_32`, `ANDROID_CREATOR`, `VISIONOS`, `IPADOS` with the client ids, versions and user agents in §12.3 (opaque wire values; preserve byte-for-byte). Flags per client: WEB_REMIX/WEB_CREATOR/TVHTML5 use signature timestamp and web PO tokens; WEB_CREATOR/TVHTML5 require login; embedded client sets `isEmbedded`; MOBILE/ANDROID_CREATOR support login; all others do not.
- `Runs(runs: List<Run>?)`, `Run(text: String, navigationEndpoint: NavigationEndpoint?)`.
- `Thumbnails(thumbnails: List<Thumbnail>)`, `Thumbnail(url: String, width: Int?, height: Int?)`.
- `Icon(iconType: String)`.
- `Badges(musicInlineBadgeRenderer: MusicInlineBadgeRenderer?)`, `MusicInlineBadgeRenderer(icon: Icon)`.
- `ResponseContext(visitorData: String?, serviceTrackingParams: List<ServiceTrackingParam>?)`, `ServiceTrackingParam(params: List<Param>, service: String)`, `Param(key: String, value: String)`.
- `ReturnYouTubeDislikeResponse(id?=null, dateCreated?=null, likes: Int?=null, dislikes: Int?=null, rating: Double?=null, viewCount: Int?=null, deleted: Boolean?=null)`.
- `Continuation(@JsonNames("nextContinuationData","nextRadioContinuationData") nextContinuationData: NextContinuationData?)`, `NextContinuationData(continuation: String)`. Extension `List<Continuation>.getContinuation(): String?` returns the first non-null continuation.
- `ContinuationItemRenderer(continuationEndpoint: ContinuationEndpoint?)`, `ContinuationEndpoint(continuationCommand: ContinuationCommand?)`, `ContinuationCommand(token: String?)`.
- `Endpoint` (`@Serializable sealed class`), and:
  - `WatchEndpoint(videoId?=null, playlistId?=null, playlistSetVideoId?=null, params?=null, index?=null, watchEndpointMusicSupportedConfigs?=null)` with nested `WatchEndpointMusicSupportedConfigs(watchEndpointMusicConfig)` / `WatchEndpointMusicConfig(musicVideoType)` and constants `MUSIC_VIDEO_TYPE_OMV`, `MUSIC_VIDEO_TYPE_UGC`, `MUSIC_VIDEO_TYPE_ATV`, `MUSIC_VIDEO_TYPE_PODCAST_EPISODE`.
  - `BrowseEndpoint(browseId: String, params: String?=null, browseEndpointContextSupportedConfigs?=null)`; derived `isArtistEndpoint`, `isAlbumEndpoint` (album or audiobook), `isPlaylistEndpoint`, `isPodcastEndpoint` (podcast show page type or `browseId` starts with `MPSP`). Nested configs + page-type constants: `MUSIC_PAGE_TYPE_ALBUM`, `AUDIOBOOK`, `PLAYLIST`, `ARTIST`, `LIBRARY_ARTIST`, `USER_CHANNEL`, `TRACK_LYRICS`, `TRACK_RELATED`, `PODCAST_SHOW_DETAIL_PAGE`, `NON_MUSIC_AUDIO_TRACK_PAGE`.
  - `SearchEndpoint(params: String?, query: String)`, `FeedbackEndpoint(feedbackToken: String)`, `ShareEntityEndpoint(serializedShareEntity: String)`, `QueueAddEndpoint(queueInsertPosition: String, queueTarget: QueueTarget)` / `QueueTarget(videoId?=null, playlistId?=null)`, `DefaultServiceEndpoint(var subscribeEndpoint: SubscribeEndpoint?, var feedbackEndpoint: FeedbackEndpoint?)` / `SubscribeEndpoint(channelIds: List<String>, params: String?=null)`, `ToggledServiceEndpoint(var feedbackEndpoint: FeedbackEndpoint?)`.
  - Top-level `const val PODCAST_BROWSE_PREFIX = "MPSP"`.
- `NavigationEndpoint(watchEndpoint?=null, watchPlaylistEndpoint?=null, browseEndpoint?=null, searchEndpoint?=null, queueAddEndpoint?=null, shareEntityEndpoint?=null, feedbackEndpoint?=null, urlEndpoint?=null, continuationCommand?=null)`; `ContinuationCommand(token?=null)`; derived `endpoint`, `anyWatchEndpoint`, `musicVideoType`.
- `UrlEndpoint(url: String?=null, target: String?=null)`.
- `TasteSignals(videoId: String, genres: List<String> = emptyList(), moodTags: List<String> = emptyList())`.
- `MediaInfo(videoId: String, title?=null, author?=null, authorId?=null, authorThumbnail?=null, description?=null, uploadDate?=null, subscribers?=null, viewCount: Int?=null, like: Int?=null, dislike: Int?=null)` (not serializable).
- `AccountInfo(name: String, email: String?, channelHandle: String?, thumbnailUrl: String?)` (not serializable).
- `SearchSuggestions(queries: List<String>, recommendedItems: List<YTItem>)` (not serializable).

### 10.3 Renderers (`models/*.kt`)

- `ThumbnailRenderer(@JsonNames("croppedSquareThumbnailRenderer") musicThumbnailRenderer: MusicThumbnailRenderer?, musicAnimatedThumbnailRenderer: MusicAnimatedThumbnailRenderer?, croppedSquareThumbnailRenderer: MusicThumbnailRenderer?)`; `MusicThumbnailRenderer(thumbnail: Thumbnails, thumbnailCrop: String?, thumbnailScale: String?)` with `getThumbnailUrl(): String?` = last thumbnail URL; `MusicAnimatedThumbnailRenderer(animatedThumbnail: Thumbnails, backupRenderer: MusicThumbnailRenderer)`.
- `MusicCarouselShelfRenderer(header?, contents: List<Content>, itemSize: String, numItemsPerColumn: Int?)`; `Header(musicCarouselShelfBasicHeaderRenderer)`; `MusicCarouselShelfBasicHeaderRenderer(strapline: Runs?, title: Runs, thumbnail?, moreContentButton: Button?)`; `Content(musicTwoRowItemRenderer?, musicResponsiveListItemRenderer?, musicNavigationButtonRenderer?, musicShuffleRenderer?, musicMultiRowListItemRenderer? = null)`.
- `MusicShelfRenderer(title?, contents: List<Content>?, continuations: List<Continuation>?, bottomEndpoint: NavigationEndpoint?, moreContentButton: Button?)`; `Content(musicResponsiveListItemRenderer?, continuationItemRenderer?, musicMultiRowListItemRenderer? = null)`. Extensions `List<Content>.getItems()` and `.getContinuation()` (first continuation item token).
- `MusicPlaylistShelfRenderer(playlistId: String?, contents: List<MusicShelfRenderer.Content> = emptyList(), collapsedItemCount: Int? = null, continuations: List<Continuation>? = null)`.
- `MusicResponsiveListItemRenderer(badges?, fixedColumns: List<FlexColumn>?, flexColumns: List<FlexColumn>, thumbnail?, menu: Menu?, playlistItemData?, overlay?, navigationEndpoint?)`; derived `isSong`, `isPlaylist`, `isAlbum`, `isArtist`, `musicVideoType`; `FlexColumn(@JsonNames("musicResponsiveListItemFixedColumnRenderer") musicResponsiveListItemFlexColumnRenderer)`; `MusicResponsiveListItemFlexColumnRenderer(text: Runs?)`; `PlaylistItemData(playlistSetVideoId: String?, videoId: String)`; `Overlay(musicItemThumbnailOverlayRenderer)`; `MusicItemThumbnailOverlayRenderer(content)`; `Content(musicPlayButtonRenderer)`; `MusicPlayButtonRenderer(playNavigationEndpoint: NavigationEndpoint?)`.
- `MusicTwoRowItemRenderer(title: Runs, subtitle: Runs?, subtitleBadges: List<Badges>?, menu: Menu?, thumbnailRenderer: ThumbnailRenderer, navigationEndpoint: NavigationEndpoint, thumbnailOverlay: MusicResponsiveListItemRenderer.Overlay?)`; derived `isSong`, `isPlaylist`, `isAlbum`, `isArtist`, `musicVideoType`.
- `MusicCardShelfRenderer(title: Runs, subtitle: Runs, thumbnail: ThumbnailRenderer, header?, contents: List<Content>?, buttons: List<Button>, onTap: NavigationEndpoint, subtitleBadges: List<Badges>?)`; `Header(musicCardShelfHeaderBasicRenderer)`, `MusicCardShelfHeaderBasicRenderer(title: Runs)`, `Content(musicResponsiveListItemRenderer?)`.
- `MusicNavigationButtonRenderer(buttonText: Runs, solid: Solid?, iconStyle: IconStyle?, clickCommand: NavigationEndpoint)`; `Solid(leftStripeColor: Long)`, `IconStyle(icon)`.
- `MusicMultiRowListItemRenderer(title?, secondTitle?=null, subtitle?=null, description?=null, thumbnail?, onTap?, playbackProgress?=null)`; `PlaybackProgress(musicPlaybackProgressRenderer?)`; `MusicPlaybackProgressRenderer(playbackProgressPercentage: Int?=null, durationText: Runs?=null)`. `MusicMultiRowImageItemRenderer(title, subtitle, thumbnail, onTap)`.
- `MusicShuffleRenderer(title?, subtitle?, thumbnail?)` with `getThumbnailUrl()`.
- `MusicQueueRenderer(content?, header?)`; `Content(playlistPanelRenderer)`; `Header(musicQueueHeaderRenderer?)`; `MusicQueueHeaderRenderer(title?, subtitle?)`.
- `PlaylistPanelRenderer(title?, titleText?, shortBylineText?, contents: List<Content>, isInfinite?, numItemsToShow?, playlistId?, continuations?)`; `Content(playlistPanelVideoRenderer?, automixPreviewVideoRenderer?)`; `PlaylistPanelVideoRenderer(title?, lengthText?, longBylineText?, shortBylineText?, badges?, videoId?, playlistSetVideoId?, selected: Boolean, thumbnail: Thumbnails, unplayableText?, menu?, navigationEndpoint: NavigationEndpoint)`; `AutomixPreviewVideoRenderer(content)` / `Content(automixPlaylistVideoRenderer)` / `AutomixPlaylistVideoRenderer(navigationEndpoint)`.
- `GridRenderer(header?, items: List<Item>, continuations?)`; `Header(gridHeaderRenderer)`, `GridHeaderRenderer(title: Runs)`, `Item(musicNavigationButtonRenderer?, musicTwoRowItemRenderer?)`.
- `Menu(menuRenderer)`; `MenuRenderer(items: List<Item>?, topLevelButtons: List<TopLevelButton>?)`; `Item(menuNavigationItemRenderer?, menuServiceItemRenderer?, toggleMenuServiceItemRenderer?)`; `MenuNavigationItemRenderer(text: Runs, icon: Icon, navigationEndpoint: NavigationEndpoint)`; `MenuServiceItemRenderer(text: Runs, icon: Icon, serviceEndpoint: NavigationEndpoint)`; `ToggleMenuServiceRenderer(defaultIcon: Icon, defaultServiceEndpoint: DefaultServiceEndpoint, toggledServiceEndpoint: ToggledServiceEndpoint?)`; `TopLevelButton(buttonRenderer?)`; `ButtonRenderer(icon: Icon, navigationEndpoint: NavigationEndpoint)`. `Button(buttonRenderer)`; `Button.ButtonRenderer(text: Runs, navigationEndpoint?, command?, icon?)`. `SubscriptionButton(subscribeButtonRenderer)`; `SubscribeButtonRenderer(subscribed: Boolean, channelId: String, longSubscriberCountText?, shortSubscriberCountText?)`.
- `MusicResponsiveHeaderRenderer(thumbnail?, buttons: List<Button>, title: Runs, subtitle: Runs, secondSubtitle: Runs?, straplineTextOne: Runs?, description: Runs? = null)`; nested `Button(musicPlayButtonRenderer?, menuRenderer?)`, `MusicPlayButtonRenderer(playNavigationEndpoint?)`.
- `MusicEditablePlaylistDetailHeaderRenderer(header: Header, editHeader: EditHeader)`; `Header(musicDetailHeaderRenderer?, musicResponsiveHeaderRenderer?)`; `EditHeader(musicPlaylistEditHeaderRenderer?)`; `MusicPlaylistEditHeaderRenderer(editTitle: Runs?)`.
- `MusicDetailHeaderRenderer(title, subtitle, secondSubtitle, description?, thumbnail: ThumbnailRenderer, menu: Menu)`; `MusicDescriptionShelfRenderer(header?, subheader?, description: Runs, footer?)`.
- `SectionListRenderer(header?, contents: List<Content>?, continuations?)`; `Header(chipCloudRenderer?)`; `ChipCloudRenderer(chips: List<Chip>)`; `Chip(chipCloudChipRenderer)`; `ChipCloudChipRenderer(isSelected=false, navigationEndpoint=NavigationEndpoint(), onDeselectedCommand?, text?, uniqueId?)`; `Content(@JsonNames("musicImmersiveCarouselShelfRenderer") musicCarouselShelfRenderer?, musicShelfRenderer?, musicCardShelfRenderer?, musicPlaylistShelfRenderer?, musicDescriptionShelfRenderer?, musicResponsiveHeaderRenderer?, musicEditablePlaylistDetailHeaderRenderer?, gridRenderer?, itemSectionRenderer?)`; `ItemSectionRenderer(contents: List<Content>?)`; `ItemSectionRenderer.Content(musicResponsiveListItemRenderer?)`.
- `Tabs(tabs: List<Tab>)`; `Tab(tabRenderer)`; `TabRenderer(title: String?, content?, endpoint?)`; `TabRenderer.Content(sectionListRenderer?, musicQueueRenderer?)`.
- `TwoColumnBrowseResultsRenderer(secondaryContents?, tabs: List<Tabs.Tab>?)`; `SecondaryContents(sectionListRenderer?)`; its `SectionListRenderer(contents: List<Content>?, continuations?)`; `Content(musicPlaylistShelfRenderer?, musicShelfRenderer?)`.
- `SearchSuggestionsSectionRenderer(contents: List<Content>)`; `Content(searchSuggestionRenderer?, musicResponsiveListItemRenderer?)`; `SearchSuggestionRenderer(suggestion: Runs, navigationEndpoint: NavigationEndpoint)`.

### 10.4 `YTItem` and filters (`models/YTItem.kt`)

`sealed class YTItem` with `abstract val id: String`, `title: String`, `thumbnail: String?`,
`explicit: Boolean`, `shareLink: String`.

- `Artist(name: String, id: String?)`, `Album(name: String, id: String)`.
- `SongItem(id, title, artists: List<Artist>, album: Album? = null, duration: Int? = null, musicVideoType: String? = null, chartPosition: Int? = null, chartChange: String? = null, thumbnail: String, explicit = false, endpoint: WatchEndpoint? = null, setVideoId: String? = null, libraryAddToken: String? = null, libraryRemoveToken: String? = null, historyRemoveToken: String? = null, publishedText: String? = null, playedPercent: Int? = null, description: String? = null)`; derived `isVideoSong` (musicVideoType non-null and not ATV), `isPodcastEpisode`; `shareLink = "https://music.youtube.com/watch?v=$id"`.
- `AlbumItem(browseId, playlistId, id = browseId, title, artists: List<Artist>?, year: Int? = null, thumbnail: String, explicit = false, description: String? = null)`; `shareLink = "https://music.youtube.com/playlist?list=$playlistId"`.
- `PlaylistItem(id, title, author: Artist?, songCountText: String?, thumbnail: String?, playEndpoint: WatchEndpoint?, shuffleEndpoint: WatchEndpoint?, radioEndpoint: WatchEndpoint?, isEditable = false, isPodcast = false)`; `explicit = false`; share link playlist.
- `ArtistItem(id, title, thumbnail: String?, channelId: String? = null, playEndpoint: WatchEndpoint? = null, shuffleEndpoint: WatchEndpoint?, radioEndpoint: WatchEndpoint?)`; share link `https://music.youtube.com/channel/$id`.
- Extensions: `filterExplicit(enabled=true)`, `filterVideoSongs(disableVideos=false)` (keeps podcast episodes), `filterPodcasts()`, `filterYoutubeShorts(enabled=false)` (drops `PlaylistItem` ids starting `SS`), `filterBlockedArtists(blockedArtists = YouTube.blockedArtists)`, `filterDislikedSongs(dislikedSongs = YouTube.dislikedSongs)`.
- `models/MetadataExtensions.kt`: `val SongItem.cleanedMetadata: CleanedMetadata` (MetadataCleaner over title + first artist name), `val SongItem.displayTitle: String`, `val SongItem.cleanArtistName: String`.

### 10.5 Response DTOs (`models/response/*.kt`)

- `AccountMenuResponse(actions: List<Action>)`; `Action(openPopupAction)`; `OpenPopupAction(popup)`; `Popup(multiPageMenuRenderer)`; `MultiPageMenuRenderer(header?)`; `Header(activeAccountHeaderRenderer)`; `ActiveAccountHeaderRenderer(accountName: Runs, email: Runs?, channelHandle: Runs?, accountPhoto: Thumbnails)` with `toAccountInfo()` (name first run, email/handle first run or null, thumbnail last URL).
- `BrowseResponse(contents?, continuationContents?, onResponseReceivedActions: List<ResponseAction>?, header?, microformat?, responseContext: ResponseContext, background: ThumbnailRenderer?)`.
  - `Contents(singleColumnBrowseResultsRenderer: Tabs?, sectionListRenderer?, twoColumnBrowseResultsRenderer?)`
  - `TwoColumnBrowseResultsRenderer(tabs: List<Tabs.Tab?>?, secondaryContents?)`, `SecondaryContents(sectionListRenderer?)`
  - `ContinuationContents(sectionListContinuation?, musicPlaylistShelfContinuation?, gridContinuation?, musicShelfContinuation: MusicShelfRenderer?)`; `SectionListContinuation(contents = emptyList(), continuations?)`; `MusicPlaylistShelfContinuation(contents = emptyList(), continuations?)`; `GridContinuation(items = emptyList(), continuations?)`
  - `ResponseAction(appendContinuationItemsAction?)`, `ContinuationItems(continuationItems: List<MusicShelfRenderer.Content>?)`
  - `Header(musicImmersiveHeaderRenderer?, musicDetailHeaderRenderer?, musicEditablePlaylistDetailHeaderRenderer?, musicVisualHeaderRenderer?, musicHeaderRenderer?)` with nested `MusicImmersiveHeaderRenderer(title, description?, thumbnail?, playButton?, startRadioButton?, subscriptionButton?, menu: Menu, subscriptionButton2?, monthlyListenerCount?=null)`, `SubscriptionButton2(subscribeButtonRenderer?)` / its `SubscribeButtonRenderer(subscriberCountWithSubscribeText?)`; `MusicVisualHeaderRenderer(title, foregroundThumbnail: ThumbnailRenderer, thumbnail?)`; `Buttons(menuRenderer?)`; `MusicHeaderRenderer(buttons: List<Buttons>?, title?, thumbnail?, subtitle?, secondSubtitle?, straplineTextOne?, straplineThumbnail?)`; `MusicThumbnail(url?)`; `MusicThumbnailRenderer(musicThumbnailRenderer: ThumbnailRenderer.MusicThumbnailRenderer, thumbnails: List<MusicThumbnail>?)`
  - `Microformat(microformatDataRenderer?)`, `MicroformatDataRenderer(urlCanonical: String?)`
- `SearchResponse(contents?, continuationContents?)`; `Contents(tabbedSearchResultsRenderer: Tabs?)`; `ContinuationContents(musicShelfContinuation)`; `MusicShelfContinuation(contents: List<Content>, continuations?)`; `Content(musicResponsiveListItemRenderer)`.
- `NextResponse(contents: Contents, engagementPanels: List<EngagementPanel>? = null, continuationContents?, currentVideoEndpoint?)`; `EngagementPanel(engagementPanelSectionListRenderer?)`; `EngagementPanelSectionListRenderer(panelIdentifier?, content?)`; `Content(sectionListRenderer?)` with `SectionListRenderer(contents: List<YouTubeDataPage.Contents.TwoColumnWatchNextResults.Results.Results.Content>?)`; `Contents(singleColumnMusicWatchNextResultsRenderer?, twoColumnWatchNextResults?)`; `SingleColumnMusicWatchNextResultsRenderer(tabbedRenderer?)`; `TabbedRenderer(watchNextTabbedResultsRenderer?)`; `WatchNextTabbedResultsRenderer(tabs: List<Tabs.Tab>)`; `ContinuationContents(playlistPanelContinuation: PlaylistPanelRenderer)`.
- `PlayerResponse(responseContext, playabilityStatus, playerConfig?, streamingData?, videoDetails?, @SerialName("playbackTracking") playbackTracking?)`.
  - `PlayabilityStatus(status: String, reason: String?)`
  - `PlayerConfig(audioConfig)`; `AudioConfig(loudnessDb: Double?, perceptualLoudnessDb: Double?)`
  - `StreamingData(formats: List<Format>?, adaptiveFormats: List<Format>, expiresInSeconds: Int)`; `Format(itag: Int, url: String?, mimeType: String, bitrate: Int, width: Int?, height: Int?, contentLength: Long?, quality: String, fps: Int?, qualityLabel: String?, averageBitrate: Int?, audioQuality: String?, approxDurationMs: String?, audioSampleRate: Int?, audioChannels: Int?, loudnessDb: Double?, lastModified: Long?, signatureCipher: String?, cipher: String?, audioTrack: AudioTrack?)`; derived `isAudio = width == null`, `isOriginal = audioTrack?.isAutoDubbed == null`; `AudioTrack(displayName?, id?, isAutoDubbed?)`
  - `VideoDetails(videoId, title?, author?, channelId, lengthSeconds: String, musicVideoType?, viewCount: String?, thumbnail: Thumbnails)`
  - `PlaybackTracking(@SerialName("videostatsPlaybackUrl")?, @SerialName("videostatsWatchtimeUrl")?, @SerialName("atrUrl")?)`, each with `@SerialName("baseUrl") baseUrl: String?`
- `GetQueueResponse(queueDatas: List<QueueData>)`, `QueueData(content: PlaylistPanelRenderer.Content)`; `GetSearchSuggestionsResponse(contents: List<Content>?)`, `Content(searchSuggestionsSectionRenderer)`; `ContinuationResponse(onResponseReceivedActions?)`.
- `GetTranscriptResponse(actions: List<Action>?)`; nested chain `Action(updateEngagementPanelAction)` → `Content(transcriptRenderer)` → `Body(transcriptBodyRenderer)` → `cueGroups: List<CueGroup>` → `CueGroup(transcriptCueGroupRenderer)` → `Cues: List<Cue>` → `Cue(transcriptCueRenderer)` → `TranscriptCueRenderer(cue: SimpleText, startOffsetMs: Long, durationMs: Long)` → `SimpleText(simpleText: String)`.
- `AddItemYouTubePlaylistResponse(status, playlistEditResults)`; `CreatePlaylistResponse(playlistId)`; `EditPlaylistResponse(newHeader: BrowseResponse.Header?)`; `FeedbackResponse(feedbackResponses: List<Status>)`, `Status(isProcessed: Boolean)`; `ImageUploadResponse(encryptedBlobId: String)`.

### 10.6 Comment DTOs (`models/comment/Comments.kt`)

- `CommentThreadRenderer(comment?=null, commentViewModel?=null, replies?=null)`; `CommentViewModelWrapper(commentViewModel?)`; `CommentViewModel(commentId?, commentKey?)`; `Comment(commentRenderer?)`; `Replies(commentRepliesRenderer?)`; `CommentRepliesRenderer(contents: List<Content>?, viewReplies: Button?, hideReplies: Button?)`; `Content(continuationItemRenderer?)`.
- `CommentRenderer(authorText?, authorThumbnail?, contentText?, publishedTimeText?, authorEndpoint?, commentId?, voteCount?, voteStatus?, replyCount: Int?)`.
- `ContinuationItemRenderer(trigger?, continuationEndpoint?, button?)`; `ContinuationEndpoint(clickTrackingParams?, continuationCommand?)`; `ContinuationCommand(token?)`.
- `CommentResponse(onResponseReceivedEndpoints: List<OnResponseReceivedEndpoint>?=null, frameworkUpdates?=null)`; `OnResponseReceivedEndpoint(reloadContinuationItemsCommand?, appendContinuationItemsAction?)`; each with `continuationItems: List<ContinuationItem>?`; `ContinuationItem(commentThreadRenderer?, continuationItemRenderer?, commentRenderer?)`.
- `FrameworkUpdates(entityBatchUpdate?)`; `EntityBatchUpdate(mutations: List<Mutation>?)`; `Mutation(payload?)`; `Payload(commentEntityPayload?, engagementToolbarStateEntityPayload?, engagementToolbarSurfaceEntityPayload?)`; `CommentEntityPayload(key?, properties?, author?, toolbar?)`; `Properties(commentId?, content?, publishedTime?, toolbarStateKey?, toolbarSurfaceKey?)`; `Content(content?)`; `Author(displayName?, avatarThumbnailUrl?)`; `EngagementToolbarStateEntityPayload(key?, likeState?, heartState?)`; `EngagementToolbarSurfaceEntityPayload(key?, toolbar?)`; `Toolbar(likeCountNotliked?, likeCountLiked?, replyCount?)`.

### 10.7 `YouTubeDataPage` (desktop watch page shape)

`@Serializable data class YouTubeDataPage(@SerialName("contents") contents: Contents? = null)` with
nested camelCase `@SerialName` mappings documented in the type: `Contents(twoColumnWatchNextResults)`
→ `TwoColumnWatchNextResults(results)` → `Results(results)` → `Results(contents: List<Content?>?)` →
`Content(videoPrimaryInfoRenderer?, videoSecondaryInfoRenderer?, itemSectionRenderer?, continuationItemRenderer?)`.
Primary renderer has `title.runs[].text`, `viewCount.videoViewCountRenderer.viewCount.simpleText`,
`dateText.simpleText`. Secondary renderer has `owner.videoOwnerRenderer.{thumbnail.thumbnails,
subscriberCountText.simpleText, title.runs[].text, navigationEndpoint}` and
`attributedDescription.content`. Continuation items expose `continuationEndpoint.continuationCommand.token`.
Only the fields used by `InnerTube.getMediaInfo` and comments need be modelled; unknown keys are
ignored.

---

## 11. Page models and parsers (`com.revenge.innertube.pages`)

All page types are immutable `data class`es (or objects with pure companion functions). Fields are
exact ABI. Parsers are total over malformed input: a row that cannot be mapped is dropped
(`mapNotNull`); a **container** the method requires may fail the whole `Result` with `ParseError`
as stated in §4.

### 11.1 Type catalogue

- `AlbumPage(album: AlbumItem, songs: List<SongItem>, otherVersions: List<AlbumItem>, releasesForYou: List<AlbumItem> = emptyList(), description: String? = null, descriptionRuns: List<Run>? = null)`; companion `getPlaylistId`, `getTitle`, `getYear`, `getThumbnail`, `getArtists`, `getSongs`, `getSong(renderer, album?)`.
- `ArtistItemsContinuationPage(items: List<YTItem>, continuation: String?)`.
- `ArtistItemsPage(title: String, items: List<YTItem>, continuation: String?)`; companion `fromMusicResponsiveListItemRenderer` (song), `fromMusicTwoRowItemRenderer`.
- `ArtistSection(title: String, items: List<YTItem>, moreEndpoint: BrowseEndpoint?)`.
  `ArtistPage(artist: ArtistItem, sections: List<ArtistSection>, description: String?, subscriberCountText: String?, monthlyListenerCount: String? = null, descriptionRuns: List<Run>? = null)`; companion `fromSectionListRendererContent`.
- `BrowseResult(title: String?, items: List<Item>)`; `Item(title: String?, items: List<YTItem>)`; methods `filterBlockedArtists()`, `filterDislikedSongs()`, `filterExplicit(enabled=true)`, `filterVideoSongs(disableVideos=false)`, `filterYoutubeShorts(enabled=false)` — each maps sections and drops sections that become empty.
- `ChartsPage(sections: List<ChartSection>, continuation: String?)`; `ChartSection(title: String, items: List<YTItem>, chartType: ChartType)`; `enum ChartType { TRENDING, TOP, GENRE, NEW_RELEASES }`.
- `ExplorePage(newReleaseAlbums: List<AlbumItem>, moodAndGenres: List<MoodAndGenres.Item>)`.
- `HistoryPage(sections: List<HistorySection>?)`; `HistorySection(title: String, songs: List<SongItem>)`; companion `fromMusicShelfRenderer`.
- `HomePage(chips: List<Chip>?, sections: List<Section>, continuation: String? = null)`; `Chip(title: String, endpoint: BrowseEndpoint?, deselectEndPoint: BrowseEndpoint?)`; `Section(title: String, label: String?, thumbnail: String?, endpoint: BrowseEndpoint?, items: List<YTItem>, shuffle: Shuffle? = null, liveOnly: Boolean = false)` with `isStationListenTogether` (true when `liveOnly` or title/label contain `LISTEN TOGETHER`, including the station combination) and `Shuffle(title: String, thumbnail: String?)`; companion `fromMusicCarouselShelfRenderer`, `Chip.fromChipCloudChipRenderer`; filters `filterBlockedArtists`, `filterDislikedSongs`, `filterExplicit`, `filterVideoSongs`.
- `LibraryAlbumsPage(albums: List<AlbumItem>, continuation: String?)`.
- `LibraryContinuationPage(items: List<YTItem>, continuation: String?)`.
- `LibraryPage(items: List<YTItem>, continuation: String? = null)`; companion `fromMusicTwoRowItemRenderer`, `fromMusicResponsiveListItemRenderer`.
- `MoodAndGenres(title: String, items: List<Item>)`; `Item(title: String, stripeColor: Long, endpoint: BrowseEndpoint)`; companion `fromSectionListRendererContent`, `fromMusicNavigationButtonRenderer`.
- `object NewReleaseAlbumPage` with `fromMusicTwoRowItemRenderer(renderer): AlbumItem?`.
- `NextResult(title: String? = null, items: List<SongItem>, currentIndex: Int? = null, lyricsEndpoint: BrowseEndpoint? = null, relatedEndpoint: BrowseEndpoint? = null, continuation: String?, endpoint: WatchEndpoint)`; `object NextPage` with `fromPlaylistPanelVideoRenderer(renderer): SongItem?`.
- `object PageHelper` with `data class LibraryFeedbackTokens(addToken: String?, removeToken: String?)`, `isLibraryIcon`, `isAddLibraryIcon`, `isSavedLibraryIcon`, `extractRuns(columns, typeLike)`, `extractLibraryTokensFromMenuItems(items)`, `extractFeedbackToken(menu, type)`. Icon handling must accept both legacy (`LIBRARY_ADD`, `LIBRARY_SAVED`, `LIBRARY_REMOVE`, `BOOKMARK`, `BOOKMARK_BORDER`) and any icon whose name starts `LIBRARY_`, while explicitly treating `KEEP` and `KEEP_OFF` as *not* library state.
- `PlaylistContinuationPage(songs: List<SongItem>, continuation: String?)`.
- `PlaylistPage(playlist: PlaylistItem, songs: List<SongItem>, songsContinuation: String?, continuation: String?, related: List<YTItem>? = null)`; companion `fromMusicResponsiveListItemRenderer`.
- `object PodcastPage` with `showId(browseId) = browseId.removePrefix("MPSP")`, `showBrowseId(showId) = "MPSP"+showId`, `isShowBrowseId(browseId) = browseId?.startsWith("MPSP") == true`, `episode(MusicMultiRowListItemRenderer, show?)`, `episode(MusicTwoRowItemRenderer)`, `show(MusicTwoRowItemRenderer)`, `show(MusicResponsiveListItemRenderer)`, `episode(MusicResponsiveListItemRenderer)`, `parseDuration(runs): Int?`.
- `RelatedPage(songs: List<SongItem>, albums: List<AlbumItem>, artists: List<ArtistItem>, playlists: List<PlaylistItem>)`; companion `fromMusicResponsiveListItemRenderer`, `fromMusicTwoRowItemRenderer`.
- `SearchResult(items: List<YTItem>, continuation: String? = null)`; `object SearchPage` with `toYTItem(renderer): YTItem?` (podcast show/episode first, then song/artist/album/playlist).
- `object SearchSuggestionPage` with `fromMusicResponsiveListItemRenderer(renderer): YTItem?`.
- `SearchSummary(title: String, items: List<YTItem>)`; `SearchSummaryPage(summaries: List<SearchSummary>, ranked: List<YTItem> = emptyList())` with `filterBlockedArtists`, `filterDislikedSongs`, `filterExplicit(enabled)`, `filterVideoSongs(disableVideos)`, `filterYoutubeShorts(enabled=false)`; companion `fromMusicCardShelfRenderer`, `fromMusicResponsiveListItemRenderer`.

### 11.2 Mapping rules common to song rows

- Title is always the first flex-column run's text cleaned by `ApiText.clean`; a row with no title
  is dropped.
- Artists come from the first separator-delimited segment's **odd** runs (every second run, starting
  at index 0) so bullet separators are skipped; each artist id is the run's browse endpoint id or
  null. Missing run endpoints are allowed (id null); for library rows ids may be `""`.
- Album is the appropriate column's first run when it has a browse endpoint.
- Duration parses the fixed column's first run via `parseTime`; `null` when absent/unparseable.
- Explicit is true when any badge icon type is `MUSIC_EXPLICIT_BADGE`.
- Thumbnails use the last URL of `musicThumbnailRenderer`; rows without one are dropped.
- Video id resolution order (first non-null): `playlistItemData.videoId`,
  `navigationEndpoint.watchEndpoint.videoId`,
  `overlay.musicItemThumbnailOverlayRenderer.content.musicPlayButtonRenderer.playNavigationEndpoint.watchEndpoint.videoId`,
  first flex-column run's watch endpoint video id.
- `setVideoId` resolution order: `playlistItemData.playlistSetVideoId`, navigation watch endpoint,
  overlay play watch endpoint, first flex run watch endpoint.
- Library add/remove tokens come from `PageHelper.extractLibraryTokensFromMenuItems`; a row may have
  either or both.
- `PodcastPage.parseDuration` accepts `"<n> s"`, `"<n> min"`, `"<n> h <n> min"`, and `mm:ss` /
  `hh:mm:ss`; zero and unparseable strings return null. It is case-insensitive for `s`, `min`, `h`.

### 11.3 Page-specific semantics

- `NextPage.fromPlaylistPanelVideoRenderer`: requires long-byline runs, video id, title, at least
  one artist, parseable length text and a thumbnail; builds album from the second separator segment
  when it has a browse endpoint; keeps library tokens and `musicVideoType`. Returns null otherwise.
- `PodcastPage.episode(MusicMultiRowListItemRenderer)`: requires an `onTap.watchEndpoint.videoId`;
  duration from playback progress; `musicVideoType` forced to `MUSIC_VIDEO_TYPE_PODCAST_EPISODE`;
  `publishedText` from the last subtitle run (trimmed, non-empty); `playedPercent` from progress;
  `description` from joined description runs. Show fallback is the `secondTitle` artist or the
  supplied show.
- `PodcastPage.episode(MusicTwoRowItemRenderer)`: only page type
  `MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE`; video id from play endpoint or by stripping the
  `MPED` prefix; show from the first subtitle run whose browse id is an `MPSP` id, else the last
  non-bullet subtitle run.
- `SearchPage.toYTItem` dispatches podcast show, podcast episode, then based on
  `MusicResponsiveListItemRenderer.isSong/isArtist/isAlbum/isPlaylist`; a row that matches none is
  dropped.
- `SearchSummaryPage.fromMusicResponsiveListItemRenderer` mirrors search-row mapping and defaults
  artist/album ids to `""` for uploaded tracks when endpoints are missing.
- `LibraryPage.fromMusicResponsiveListItemRenderer` keeps artist/album names with `""` ids for
  uploaded tracks; it never fails on a missing artist endpoint. Artist items take the *last* title
  run (library grids often prefix "Artist • ").
- `HomePage.Section.fromMusicCarouselShelfRenderer` returns null when the header/title is missing or
  no item maps; `liveOnly` is true when every non-shuffle card is a watch video that is not a
  podcast episode and has a subtitle badge with no inline badge renderer.
- `ChartsPage` conversion: a responsive row needs at least three flex columns and a
  `playlistItemData.videoId`, otherwise it is dropped; chart position is the third column's first run
  as Int; chart change is the third column's second run text. A two-row item maps as in
  `RelatedPage`; chart `NEW_RELEASES` is assigned to grid sections regardless of title.
- `MoodAndGenres` requires a grid header title, a stripe colour and a browse endpoint on the click
  command; otherwise that button is dropped.
- `NewReleaseAlbumPage.fromMusicTwoRowItemRenderer` maps album cards only; requires album page type,
  browse id, playlist id from the play overlay's watch-playlist endpoint, title and thumbnail;
  artists are not carried.

---

## 12. Cross-module contracts required to compile

These are the exact shapes downstream modules use. Changing them breaks the build.

### 12.1 `:app`, `:core`, `:playback`, `:lyrics`, `:canvas`

- Properties read/written: `YouTube.locale` (`YouTubeLocale`), `YouTube.visitorData` (`String?`),
  `YouTube.dataSyncId` (`String?`), `YouTube.cookie` (`String?`), `YouTube.proxy`
  (`java.net.Proxy?`), `YouTube.proxyAuth` (`String?`), `YouTube.useLoginForBrowse` (`Boolean`),
  `YouTube.ipVersion` (`IpVersion`), `YouTube.blockedArtists` / `YouTube.dislikedSongs`
  (`Set<String>`).
- Methods used by name (all listed in §4.2): `refreshVisitorData()`, `visitorData()`,
  `accountInfo()`, `clearGuestSession()`, `search`, `searchContinuation`, `searchSummary`,
  `searchSuggestions`, `album`, `albumSongs`, `artist`, `artistItems`, `artistItemsContinuation`,
  `playlist`, `playlistContinuation`, `podcast`, `home`, `explore`, `newReleaseAlbums`,
  `moodAndGenres`, `browse`, `library`, `libraryContinuation`, `libraryRecentActivity`,
  `getChartsPage`, `musicHistory`, `likeVideo`, `likePlaylist`, `subscribeChannel`,
  `getChannelId`, `addToPlaylist`, `addPlaylistToPlaylist`, `removeFromPlaylist`,
  `moveSongPlaylist`, `createPlaylist`, `renamePlaylist`, `uploadCustomThumbnailLink`,
  `removeThumbnailPlaylist`, `deletePlaylist`, `player`, `newPlaybackNonce`, `reportWatchtime`,
  `registerPlayback`, `next`, `fetchTasteSignals`, `lyrics`, `related`, `queue`, `transcript`,
  `feedback`, `addSongToLibrary`, `removeSongFromLibrary`, `toggleSongLibrary`, `getMediaInfo`,
  `getNewPipeStreamUrls`, `newPipePlayer`, `comments`, `commentContinuation`, `commentReplies`.
- `YouTube.SearchFilter` is imported both as a type and via companion object static imports
  (`YouTube.SearchFilter.Companion.FILTER_ALBUM`, etc.); `.value` is read directly. Keep the
  companion `val`s and the public `value`.
- `YouTube.LibraryFilter.FILTER_RECENT_ACTIVITY.value` is used; other values must exist as in §4.5.
- `NewPipeExtractor.getVideoUrlAndDuration`, `getVideoTracks`, `getSignatureTimestamp`,
  `getStreamUrl`, `newPipePlayer` are used by `:app` canvas/podcast/player code.
- `YouTubeUrlParser.isYouTubeUrl`, `parse`, `ParsedUrl.Video`, `ParsedUrl.Artist` are used by the
  search UI; `createWatchEndpoint` and `extractVideoId` are public API.
- `parseCookieString` is imported by `SyncUtils`.
- `Result<PlaylistPage>.completed()` and `Result<LibraryPage>.completed()` are imported by
  `:app` (the `@JvmName` overloads must not clash in JVM signatures).
- `SongItem.cleanedMetadata` returns `com.revenge.metadata.models.CleanedMetadata`;
  `SongItem.displayTitle` / `SongItem.cleanArtistName` are used by core projections.
  Therefore `:metadata` must be an `api` dependency.
- Model filter extensions are used with their default arguments (`filterBlockedArtists()`,
  `filterDislikedSongs()`) and therefore read the current `YouTube` filter sets at call time.

### 12.2 `:app` streaming boundary

- `YouTube.player(videoId, playlistId, client, signatureTimestamp, poToken)` returns a decoded
  `PlayerResponse`. Callers read `playabilityStatus.status`, `streamingData.formats`,
  `streamingData.adaptiveFormats`, `Format.url`, `Format.signatureCipher`, `Format.itag`,
  `Format.mimeType`, `Format.bitrate`, `Format.isAudio`, `Format.isOriginal`,
  `playerConfig.audioConfig.loudnessDb`, `videoDetails`, and `playbackTracking.*.baseUrl`.
- Cipher/PO-token/SABR code lives in `:app`. Its contract with this module: it supplies
  `signatureTimestamp: Int?` and `poToken: String?` to `player`, and consumes the returned formats.
  `InnerTube` must not itself fetch player JavaScript or mint PO tokens.
- `Fix403`/`BotDetectionMitigator` consume `YouTube.proxy`, `YouTube.cookie`,
  `YouTube.visitorData`, `YouTube.locale`, `YouTube.refreshVisitorData()`, and error messages.
  `BotDetectionMitigator.isBotDetectionError` substring-matches error messages including `"403"`,
  `"Error 2000"` and `"Sign in to confirm"`. `InnerTubeException.BotCheck.message` and
  `AuthRequired.message` must therefore contain the server's reason text when available (do not
  replace it with a generic word). `BotCheck` for HTTP 403 should include `"403"` in its message.
- A higher layer may retry a `BotCheck` with another client or after `refreshVisitorData()`. This
  module exposes no cross-client fallback itself.

### 12.3 Opaque wire constants that must be preserved

- `SearchFilter` values:
  - `FILTER_SONG = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"`
  - `FILTER_VIDEO = "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"`
  - `FILTER_ALBUM = "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"`
  - `FILTER_ARTIST = "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"`
  - `FILTER_FEATURED_PLAYLIST = "EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D"`
  - `FILTER_COMMUNITY_PLAYLIST = "EgeKAQQoAEABagoQAxAEEAoQCRAF"`
  - `FILTER_PODCAST = "EgWKAQJQAWoKEAkQBRAKEAMQBA%3D%3D"`
  - `FILTER_EPISODE = "EgWKAQJIAWoKEAkQBRAKEAMQBA%3D%3D"`
- `LibraryFilter` values:
  - `FILTER_RECENT_ACTIVITY = "4qmFsgIrEhdGRW11c2ljX2xpYnJhcnlfbGFuZGluZxoQZ2dNR0tnUUlCaEFCb0FZQg%3D%3D"`
  - `FILTER_RECENTLY_PLAYED = "4qmFsgIrEhdGRW11c2ljX2xpYnJhcnlfbGFuZGluZxoQZ2dNR0tnUUlCUkFCb0FZQg%3D%3D"`
  - `FILTER_PLAYLISTS_ALPHABETICAL = "4qmFsgIrEhdGRW11c2ljX2xpa2VkX3BsYXlsaXN0cxoQZ2dNR0tnUUlBUkFBb0FZQg%3D%3D"`
  - `FILTER_PLAYLISTS_RECENTLY_SAVED = "4qmFsgIrEhdGRW11c2ljX2xpa2VkX3BsYXlsaXN0cxoQZ2dNR0tnUUlBQkFCb0FZQg%3D%3D"`
- `YouTube.MAX_GET_QUEUE_SIZE = 1000`.
- Player client identities. The fields `clientName`, `clientVersion`, `clientId`, `userAgent` and
  per-client flags are wire contract. The exact values in the baseline are: `WEB`
  (`WEB`, `2.20260213.00.00`, `1`, `USER_AGENT_WEB`); `WEB_REMIX` (`WEB_REMIX`,
  `1.20260213.01.00`, `67`, `USER_AGENT_WEB`, login+signature+poToken); `WEB_CREATOR`
  (`WEB_CREATOR`, `1.20260213.00.00`, `62`, `USER_AGENT_WEB`, login+signature+poToken,
  loginRequired); `TVHTML5` (`TVHTML5`, `7.20260213.00.00`, `7`, Samsung TV UA, login+signature+
  poToken, loginRequired); `TVHTML5_SIMPLY_EMBEDDED_PLAYER`
  (`TVHTML5_SIMPLY_EMBEDDED_PLAYER`, `2.0`, `85`, PlayStation UA, login+signature,
  `isEmbedded=true`); `IOS` (`IOS`, `21.03.1`, `5`, iOS UA, osVersion `18.2.22C152`); `MOBILE`
  (`ANDROID`, `21.03.38`, `3`, Android UA, login+signature); `ANDROID_NO_SDK` (`ANDROID`,
  `21.03.38`, `3`, Android UA, no login/signature); `ANDROID_VR_NO_AUTH` (`ANDROID_VR`,
  `1.61.48`, `28`, Oculus UA); `ANDROID_VR_1_61_48` (full device fields); `ANDROID_VR_1_65_10`
  (`1.65.10`, Android 12L eureka UA, `friendlyName="Android VR 1.65"`); `ANDROID_VR_1_43_32`
  (`1.43.32`, Cronet 107); `ANDROID_CREATOR` (`ANDROID_CREATOR`, `25.03.101`, `14`, Pixel 9 Pro
  Fold UA); `VISIONOS` (`VISIONOS`, `0.1`, `101`, macOS Safari UA); `IPADOS` (`IOS`, `21.03.3`,
  `5`, iPad UA, osName iPadOS, packageName `com.google.ios.youtube`).
  `USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"`.
  A rebuild may update versions but must keep the same client ids/names/flags and all fields the
  app reads.
- `YouTubeConstants.DEFAULT_TOP_RESULT = "Top result"`, `DEFAULT_OTHER_RESULTS = "Other"`.

---

## 13. Acceptance tests (deterministic, fixture-based)

Every case below is observable through the public API and must run offline. Network cases use a
scripted `MockWebServer`/fake engine; no test may reach the real YouTube. Tests are JUnit 4.
Names map to the test files in §2.

### 13.1 Serialization and wire fixtures (`SerializationTest`, `PlayerFixtureTest`)

1. `MusicResponsiveListItemRenderer` fixture with a `musicResponsiveListItemFixedColumnRenderer`
   alias decodes into `fixedColumns[0]`; `isSong` is true and `musicVideoType` is read from the
   overlay or navigation endpoint.
2. `ThumbnailRenderer` fixture using `croppedSquareThumbnailRenderer` resolves a URL via either the
   aliased or the explicit property; a fixture with both keys must not throw.
3. `Continuation` fixture using `nextRadioContinuationData` yields the same token as one using
   `nextContinuationData`; `listOf(continuation).getContinuation()` returns it.
4. `SectionListRenderer` fixture using `musicImmersiveCarouselShelfRenderer` populates
   `contents[0].musicCarouselShelfRenderer`.
5. Unknown keys at every nesting level are ignored. A missing nullable key decodes as null. A
   missing non-null key (for example `PlaylistPanelVideoRenderer.selected`) fails with a
   serialization exception.
6. Player fixture: `streamingData.adaptiveFormats[0]` exposes `isAudio`, `isOriginal`, `itag`,
   `signatureCipher`; a `contentCheckOk/racyCheckOk` default of `true` is present in an encoded
   `PlayerBody`; null `playlistId`/`playbackContext`/`serviceIntegrityDimensions` are omitted from
   the encoded JSON.
7. Browse fixture with `microformat.urlCanonical` ending `...=PL123` yields playlist id `PL123`
   via `AlbumPage.getPlaylistId`.
8. Transcript fixture: two cue groups, one cue text padded with `♪`, produce exactly two lines in
   `[mm:ss.SSS]` form separated by `\n`, with the padding removed.

### 13.2 Error classification and 403 handling (`InnerTubeErrorTest`, `RetryAndCancellationTest`)

9. `fromHttpStatus(401)` is `AuthRequired`; `fromHttpStatus(403)` is `BotCheck` and its message
   contains `"403"`; `fromHttpStatus(429)` is `BotCheck`; `fromHttpStatus(500)` is `HttpError(500)`
   and `isRetryable == true`; `fromHttpStatus(400)` is `HttpError(400)` and
   `isRetryable == false`.
10. A scripted socket failure classifies as `TransportError`; a scripted 401 classifies as
    `AuthRequired`; the two are never conflated (`AC-STR-02`).
11. **403 retry:** a MockWebServer dispatcher returns 403 for attempt 1 and 200 with a fixture for
    attempt 2. The first `YouTube` call fails with `BotCheck`; a caller-level retry call succeeds.
    The retry is caller-driven: `InnerTube` must issue exactly one HTTP request for the failed call
    (assert the request count). A 403 must never be reported as `TransportError`.
12. **Transport retry:** a dispatcher that fails with `IOException` twice then returns 200 is
    served by `InnerTube.search` in three attempts with roughly 500 ms and 1000 ms backoff
    (coroutine virtual time). A dispatcher failing three times surfaces `TransportError` after
    exactly three requests.
13. **Cancellation (`AC-STR-04`):** launch `YouTube.search` against a dispatcher that never
    responds; cancel the job after the first request; assert the call does not return
    `Result.success`, no second request is made, and the `CancellationException` propagates.
    Cancel during retry backoff and assert no further request.
14. `YouTube` facade never wraps `CancellationException` in `Result.failure` (test via a fake
    InnerTube or a cancelled MockWebServer response).

### 13.3 Pagination continuation (`PaginationTest`)

15. A fake playlist page with two continuation pages completes to the concatenation of all three
    pages in order; the returned `songsContinuation` is null and `continuation` equals the first
    page's `continuation`.
16. A page sequence where the second token repeats the first stops after one continuation request
    (visited-token guard) and does not loop.
17. Two consecutive empty pages stop the walk; one empty page followed by a non-empty page
    continues.
18. The 50-page budget is enforced: a server that always returns a new token stops after exactly
    50 continuation requests.
19. A continuation HTTP failure fails the whole `completed()` result with an
    `IllegalStateException` whose cause is present; it is never treated as end-of-list.
20. Cancellation during a continuation fetch is rethrown, not returned as failure.
21. Library `completed()` behaves identically for `items`/`continuation` and returns
    `LibraryPage(items, continuation = null)`.
22. `searchContinuation` on an empty page returns `continuation == null`; `playlistContinuation`
    and `artistItemsContinuation` likewise return null continuation on empty mapped lists.

### 13.4 Search and browse mapping (`SearchSummaryTest`)

23. A summary fixture with one top-result card and one shelf produces the card summary first, then
    the shelf summary, with the default titles when headers are absent
    (`"Top result"`, `"Other"`).
24. Duplicate items across the card, shelf and flat sections appear once in `ranked` and once per
    summary group (same `id`).
25. A video song and its matching album track (same title ignoring case/accents/punctuation, one
    shared artist) are ranked track-before-video; with no matching track the original order is
    kept.
26. Flat items are grouped under exactly `"Songs"`, `"Videos"`, `"Episodes"`, `"Albums"`,
    `"Artists"`, `"Playlists"`; an empty group is absent; podcast episodes are excluded from
    `"Videos"` and present in `"Episodes"`.
27. Blocked artists and disliked songs are removed from summaries and `ranked` when the global sets
    are populated (entries `"id"` and `"id||name"` both tested).
28. A row with a missing title, thumbnail or video id is dropped without failing the whole page.
29. `library(browseId)` with no grid and no shelf returns `Result.failure` whose message names the
    browse id.

### 13.5 NewPipe bridge (`NewPipeBridgeTest`)

30. `getStreamUrl` with a plain `format.url` returns that URL after throttling de-obfuscation; a
    de-obfuscation error returns null, never throws.
31. `getStreamUrl` with a `signatureCipher` fixture (`s`, `sp`, `url`) substitutes the
    de-obfuscated signature under the named parameter; a missing `s`, `sp` or `url` returns null.
32. `getVideoUrlAndDuration` prefers MP4 1080–1440, then 1080–1440, then 720–1079, then the highest
    stream; returns `(url, duration)` and null when no URL exists.
33. `getVideoTracks` emits muxed tracks labelled `"<height>p"` (`360` fallback) and video-only
    tracks (`720` fallback) paired with the best audio by average bitrate; missing content is
    skipped.
34. `newPipePlayer` returns null when playability is not `OK`, when the stream list is empty, or
    when `streamingData` is null; otherwise it replaces only itag-matching URLs and preserves all
    other fields.
35. `NewPipeDownloaderImpl` maps HTTP 429 to `ReCaptchaException` and returns a `Response` carrying
    the final URL, headers and body for 200.
36. `NewPipeExtractor` initialises lazily once under concurrent first use (synchronized); no
    duplicate initialisation observed.

### 13.6 Utilities, URL parser and time (`UtilsTest`, `YouTubeUrlParserTest`, `ParsersTest`)

37. `parseCookieString("SID=abc; SAPISID=def=ghi; malformed; empty=")` yields
    `SID→abc`, `SAPISID→def=ghi`, `empty→""`, and no `malformed` key; empty input is empty.
38. `sha1("abc") == "a9993e364706816aba3e25717850c26c9cd0d89d"` and `toHex` is lowercase.
39. `parseTime`: `"03:20"→200`, `"1:05:00"→3900`, `"no time"→null`, `"1:2:3:4"→null`.
40. `isPrivateId("FEmusic_privately_owned_songs")` true; `"MPREb_test"` false.
41. `YouTubeUrlParser` extracts `dQw4w9WgXcQ` from watch (music and www), `youtu.be`, and shorts
    links; rejects example.com; `extractVideoId` and `createWatchEndpoint` agree.
42. Artist parsing only on `music.youtube.com`: `channel/UC123` and `browse/MPREb_abcdef` yield
    `Artist` with the expected id; `www.youtube.com/channel/UC123` yields null.
43. `PodcastPage.parseDuration`: `"23 min"→1380`, `"1 h 5 min"→3900`, `"1:05:00"→3900`, `"45 s"→45`,
    `"0 min"→null`, `"unknown"→null`.
44. `Runs.extractCountText`: `"1.2M views"→"1.2M"`, `"1,234 likes"→"1,234"`, text without digits
    and null runs → null. `splitBySeparator` splits on the `" • "` run. `oddElements` keeps indices
    0,2,4. `clean()` drops a plain leading segment but keeps a linked one.
45. `YouTube.newPlaybackNonce()` returns length 16 with only `[A-Za-z0-9_-]`; two calls may differ
    (randomness is allowed); `parseTasteSignals` is deterministic for a fixed `NextResponse`.

### 13.7 Lyrics boundary fixtures (`LyricsBoundaryTest`)

These exercise the contract between this module and `:lyrics`/`:youlyplus`/`:betterlyrics`. The
LRC/TTML parsers themselves are specified in their own module specs; here we assert what this
module produces and consumes.

46. **Unsynchronised lyrics:** `YouTube.lyrics` returns a plain string with no `[mm:ss]` prefixes;
    `Result.success(null)` is returned for a browse response with no description shelf. A provider
    must treat `null` as "not found" and plain text as unsynchronised (not parse it as LRC).
47. **Transcript to LRC:** feed a transcript response fixture whose cue texts include `♪` padding;
    assert every produced line matches `^\[\d{2,}:\d{2}\.\d{3}\]` and the `:lyrics` LRC parser
    consumes it without error, producing the same line count.
48. **Malformed LRC fixture** (`fixtures/lyrics/malformed.lrc`: unterminated `[` , non-numeric
    timestamps, a lone `]`, mixed CRLF): the boundary test asserts the downstream LRC parser returns
    an empty or best-effort list and never throws; the innertube side has no timestamps to leak.
49. **Malformed TTML / XXE fixture** (`fixtures/lyrics/evil.ttml` with a `<!DOCTYPE` external
    entity): the downstream TTML parser must return an empty list without resolving the entity and
    without throwing; `YouTube.transcript` never emits XML.
50. **Provider timeout:** (a) `NetworkConfig.createOptimizedHttpClient()` honours its configured
    request timeout: a MockWebServer that delays beyond it fails with a Ktor timeout, and
    `NetworkConfig.getAdaptiveTimeouts(POOR).requestTimeout == 90_000L`. (b) The InnerTube transport
    uses its fixed 60 000 ms request timeout and classifies the resulting timeout as
    `TransportError`, never as a parse error. (c) At the `:lyrics` boundary, a provider whose HTTP
    call times out yields a `Result.failure` and the registry falls back to the next provider; no
    call hangs past its timeout.
51. **Translation line-count normalisation** (`fixtures/translation/`): given 4 non-empty lyric
    lines, a translation result with 6 lines stores only the first 4 (extra lines dropped); a
    result with 2 lines stores those 2 and leaves the remaining 2 entries untranslated (original
    text retained); an empty result is an error, not an empty translation. This is a boundary test
    against `LyricsTranslationHelper`; it documents that `YouTube` supplies no timing constraints
    to translation.
52. **End-to-end lyric fallback:** `YouTube.transcript` failure followed by `YouTube.lyrics`
    success returns the unsynchronised text; both failing returns a typed failure to the provider
    registry and never crashes.

### 13.8 Non-functional acceptance

53. Every `models`/`pages` data class is an immutable value with generated `equals`/`hashCode`/`copy`.
54. All public `suspend` entry points are safe to call concurrently from multiple coroutines with
    only reads of configuration; results are independent.
55. No `YouTube` method logs secrets. Cookie values, `SAPISIDHASH`, PO tokens and `dataSyncId` must
    never appear in exception messages or `toString`.
56. `YouTube` methods return within the configured timeout in the presence of a stalled server.

---

## 14. Non-functional and quality requirements

- **Determinism:** all parsers are pure functions of their JSON input. No locale-dependent
  lowercasing: use `ignoreCase` comparisons, not default-locale `lowercase()`. `searchSummary`
  title normalisation is Unicode NFD, strips combining marks, lowercases with locale-independent
  rules, and collapses non-letter/non-digit runs to spaces.
- **No network in tests:** all HTTP tests use a fake engine/MockWebServer.
- **No secret leakage:** error messages may include status codes and the server reason; they must
  not include cookie values, SAPISID or tokens.
- **Memory:** the JSON layer discards unknown keys; no large player response is retained statically.
- **Threading:** immutable DTOs/pages may cross threads; configuration mutation is startup-only.
- **MinSdk:** no API above Android 26 in production sources; `java.util.Base64` is replaced by
  `kotlin.io.encoding.Base64` or equivalent desugared call.

---

## 15. Baseline defects the clean-room implementation must not reproduce

1. Bare `runCatching` swallowing `CancellationException` (§9.3).
2. Untyped errors conflating auth, bot checks, transport and parsing (§9.1).
3. `x-guploader-uploadid!!` and other unchecked `!!` on server-optional nodes: use typed
   `ParseError` instead of `NullPointerException`.
4. Parse failures inside a page mapper aborting the whole page when the mapper is documented to
   drop rows; drop the row and continue.
5. Unbounded automix recursion in `next`: cap at one automix level.
6. Any network call from `parseTasteSignals`, URL parsing, cookie parsing or page mapping.

---

## 16. Out-of-scope subsystem boundary (explicit)

The following are *consumers* and are not implemented here; their contracts are listed so the
implementer does not accidentally duplicate them inside `:innertube`:

- **Cipher / n-transform / QuickJS / WebView** (`:app` `utils/cipher`, `utils/sabr`): consume
  `PlayerResponse` formats and `signatureTimestamp`; return a playable URL. `:innertube` only
  transports the player response.
- **PO token** (`:app` `utils/potoken`): mints a token string passed as `poToken` to
  `YouTube.player`. `:innertube` only places it in `serviceIntegrityDimensions`.
- **SABR**: a higher layer may consume a player response and request media over SABR; the transport
  contract is only the `PlayerResponse` DTO.
- **403 / bot mitigation** (`:app` `Fix403`, `BotDetectionMitigator`): reads the properties and
  `refreshVisitorData()` exposed in §4.1; reacts to `BotCheck`/`AuthRequired` messages.
- **SponsorBlock** (`:app` data layer): uses the YouTube video id and an independent HTTP client; it
  does not call `:innertube`.
- **Lyrics providers / LRC / TTML / translation**: consume `YouTube.lyrics` (plain text) and
  `YouTube.transcript` (`[mm:ss.SSS]` lines); parsing and line-count normalisation are their
  responsibility (§13.7 is the boundary test).

---

## 17. Definition of done

The module is complete when:

- `./gradlew :innertube:assembleDebug :innertube:test` passes with no warnings promoted to errors;
- all files in §2 exist;
- every acceptance test in §13 passes offline;
- downstream modules (`:core`, `:playback`, `:lyrics`, `:app`, `:canvas`) compile unchanged against
  the module;
- no symbol from `../Revenge-source` or `../Echo-Music` was consulted.
