# Clean-Room Specification — Library, Search, Browse and Playlist Screens

Status: M2 specification (behavioural contract only).
Cluster: `library-search`.
Layer: `:app` presentation (Compose UI + Android ViewModels) plus the stable helpers it exposes.
Directory root: `app/src/main/kotlin/com/revenge/music`.

This document describes observable behaviour, state and public API shape. It intentionally omits
statement-level implementation and does not reproduce Compose subtrees line by line. Any
implementation that satisfies the contracts and acceptance tests is acceptable.

---

## 1. Scope and consumers

The cluster covers the screens that make up the "discover and manage" half of the app:

- Home and its sections (`HomeScreen`, `HomeViewModel`, `HomeSection`).
- Main search (`SearchScreen`), online search results and suggestions (`OnlineSearchResult`,
  `OnlineSearchScreen`, `OnlineSearchViewModel`, `OnlineSearchSuggestionViewModel`,
  `LocalSearchScreen`, `LocalSearchViewModel`), and the Apple Music trending tab.
- Library hub (`LibraryScreen`) and its sub-screens: songs, albums, artists, playlists, mixes,
  podcasts, local songs, Spotify library.
- Browse surfaces: `BrowseScreen`, `ExploreScreen`, `ChartsScreen`, `NewReleaseScreen`,
  `MoodAndGenresScreen`, `YouTubeBrowseScreen`.
- Detail screens: `AlbumScreen`; artist screens (`ArtistScreen`, `ArtistSongsScreen`,
  `ArtistAlbumsScreen`, `ArtistItemsScreen`); playlist screens (`OnlinePlaylistScreen`,
  `LocalPlaylistScreen`, `AutoPlaylistScreen`, `CachePlaylistScreen`, `TopPlaylistScreen`,
  `BottomPlaylistScreen`, `SpotifyPlaylistDetailScreen`, `AiModifyPlaylistDialog`).
- Navigation utilities in `utils/PlaylistNavigation.kt` and the home ranking helpers
  `utils/HeroCarouselSongs.kt` and `utils/QuickPickSongs.kt`.
- The ViewModels under `com.revenge.music.viewmodels` listed in §8.

Other code depends on the following hard contracts:

- `NavigationBuilder.navigationBuilder` registers every route in §3 and passes the exact
  composable arguments. `Screens` (§3.1) is the source of the five main tab routes.
- Tests in `app/src/test/kotlin/com/revenge/music/utils` construct and call the three utility
  files directly (`PlaylistNavigationTest`, `HeroCarouselSongsTest`, `QuickPickSongsTest`).
- `HomeViewModel` is used by `SearchScreen` (`homeViewModel` parameter is retained for API
  compatibility but non-essential behaviour) and by the Home screen.
- `SuggestionsViewModel` and its models are used only inside the suggestions package, but the
  models are public and consumed by the section composables.

### 1.1 Shared app contracts assumed

These are host-provided and must not be re-declared by this cluster; screens consume them and
must compile regardless of their internals:

| Composition local / API | Contract used here |
|---|---|
| `LocalPlayerConnection` | `PlayerConnection?`. Screens that call `?: return` render nothing when absent. Exposes `isPlaying`/`isEffectivelyPlaying`, `mediaMetadata`, `playQueue(...)`, `togglePlayPause()`. |
| `LocalDatabase` | `MusicDatabase` with the DAO/flow methods referenced in §8 (`albumWithSongs`, `playlist`, `playlistSongs`, `searchHistory`, `query { }`, etc.). |
| `LocalMenuState` | `MenuState` with `show { }` / `dismiss()`. |
| `LocalDownloadUtil`, `LocalSyncUtils`, `LocalListenTogetherManager` | Optional/download and sync helpers. |
| `LocalPlayerAwareWindowInsets`, `LocalIsPlayerExpanded`, `LocalBottomSheetPageState` | Layout state used for padding and back handling. |
| `rememberPreference`, `rememberEnumPreference` | DataStore-backed Compose state with default fallback. |
| `dataStore.get(key, default)` | Suspend preference read used inside ViewModels. |

Screens that require the player convention (`?: return`) must not crash if the local is null.

---

## 2. Platforms, permissions and build

- Module: Android application `:app`; Kotlin + Jetpack Compose (Material 3, Foundation, Animation,
  Compose compiler plugin), Hilt (`@HiltViewModel`, `hiltViewModel()`), Navigation Compose,
  Coil, Kotlin coroutines/Flow.
- All screens are Compose-only; no XML layouts, no Android views except platform intent launchers.
- Permissions used by this cluster (declared in `app/src/main/AndroidManifest.xml`, already
  present): `READ_MEDIA_AUDIO` (API 33+) and `READ_EXTERNAL_STORAGE` (API < 33) for local song
  scanning; `RECORD_AUDIO` and the speech-recognition activity intent for voice search;
  `INTERNET`/`ACCESS_NETWORK_STATE` for remote browse/search.
- Voice search launches `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` via
  `ActivityResultContracts.StartActivityForResult`; failure to launch falls back to the
  `"recognition"` route.
- Local folder pickers use `ActivityResultContracts.OpenDocumentTree`. Storage permission is
  re-checked on `ON_RESUME`; denial is represented in the scan sheet, never as a crash.
- No new services, receivers or manifest entries are introduced by this cluster. The
  library/search screens contain no exported components and no background services.

---

## 3. Navigation contract

### 3.1 `com.revenge.music.ui.screens.Screens`

`@Immutable sealed class Screens(@StringRes titleId, @DrawableRes iconIdInactive, @DrawableRes iconIdActive, val route: String)`.
Required objects and routes:

| Object | route |
|---|---|
| `Home` | `"home"` |
| `Podcast` | `"podcasts"` |
| `Search` | `"search_input"` |
| `ListenTogether` | `"listen_together"` |
| `Library` | `"library"` |
| `Generate` | `"generate"` |

`Screens.MainScreens = listOf(Home, Podcast, Search, ListenTogether, Library)`.

### 3.2 Routes owned by this cluster

`navigationBuilder(navController, scrollBehavior, activity, snackbarHostState)` registers:

| Route pattern | Arguments (NavType) | Destination composable |
|---|---|---|
| `home` | — | `HomeScreen(navController, snackbarHostState)` |
| `search_input` | — | `SearchScreen(navController, pureBlack)` |
| `library` | — | `LibraryScreen(navController)` |
| `local_songs` | — | `LocalSongScreen(navController)` |
| `mood_and_genres` | — | `MoodAndGenresScreen(navController, scrollBehavior)` |
| `new_release` | — | `NewReleaseScreen(navController, scrollBehavior)` |
| `charts_screen` | — | `ChartsScreen(navController)` |
| `browse/{browseId}` | `browseId: String` | `BrowseScreen(navController, scrollBehavior, browseId)` |
| `search/{query}` | `query: String` | `OnlineSearchResult(navController)`; search-to-search transitions use fade/slide animation, and the destination reads `query` from its back-stack entry. |
| `album/{albumId}` | `albumId: String` | `AlbumScreen(navController, scrollBehavior)` |
| `artist/{artistId}` | `artistId: String` | `ArtistScreen(navController, scrollBehavior)`, except a podcast-show browse id is redirected to `online_playlist/{showId}` by popping the artist entry. |
| `artist/{artistId}/songs` | `artistId: String` | `ArtistSongsScreen(navController, scrollBehavior)` |
| `artist/{artistId}/albums` | `artistId: String` | `ArtistAlbumsScreen(navController, scrollBehavior)` |
| `artist/{artistId}/items?browseId={browseId}?params={params}` | `artistId: String`, optional `browseId: String?`, optional `params: String?` | `ArtistItemsScreen(navController, scrollBehavior)` |
| `online_playlist/{playlistId}` | `playlistId: String` | `OnlinePlaylistScreen(navController, scrollBehavior)` |
| `spotify_playlist/{playlistId}?title={title}&cover={cover}` | `playlistId: String`, `title` default `"Playlist"`, `cover` default `""` | `SpotifyPlaylistDetailScreen(...)` receiving `playlistId`, `playlistTitle`, `playlistCover` |
| `local_playlist/{playlistId}` | `playlistId: String` | `LocalPlaylistScreen(navController, scrollBehavior)` |
| `auto_playlist/{playlist}` | `playlist: String` | `AutoPlaylistScreen(navController, scrollBehavior)` |
| `cache_playlist/{playlist}` | `playlist: String` | `CachePlaylistScreen(navController, scrollBehavior)` |
| `top_playlist/{top}` | `top: String` | `TopPlaylistScreen(navController, scrollBehavior)` |
| `bottom_playlist/{bottom}` | `bottom: String` | `BottomPlaylistScreen(navController, scrollBehavior)` |
| `youtube_browse/{browseId}?params={params}` | optional `browseId: String?`, optional `params: String?` | `YouTubeBrowseScreen(navController)` |
| `generate` | — | `GenerateViewModel`-backed generation screen (outside this cluster) |

Additional destinations this cluster navigates to but does not own: `recognition`,
`settings/spotify_import`, `settings`.

Navigation argument invariant: ids are URL-encoded when they can contain `/ ? & # + %`; see §6.1.
A literal `"null"` (after navigation's StringType parsing) is never a valid playlist id.

### 3.3 Route builders used by screens

- Playlist item click in Home: `PlaylistItem.id.removePrefix("VL")`; `"LM"` → `auto_playlist/liked`;
  `"SE"` → `auto_playlist/downloaded`; otherwise `online_playlist/{id}`.
- Album click → `album/{id}`; artist click → `artist/{id}`; playlist click →
  `online_playlist/{id}`.
- Library special rows: `auto_playlist/liked`, `auto_playlist/downloaded`,
  `auto_playlist/exported`, `cache_playlist/cached`, `top_playlist/{topSize}`,
  `bottom_playlist/50`, `generate`.
- Mood/genre card → `youtube_browse/{browseId}?params={params}` when a matching mood item exists,
  otherwise `search/{URLEncoded searchQuery}`.
- New playlist creation / AI creation / taste generation → `local_playlist/{id}`.
- Imported YouTube playlist URL → `online_playlist/{id}` or an "invalid playlist URL" toast.
- Search submit → parse URL: video → play queue; artist → `artist/{id}`; else
  `search/{URLEncoded query}`.

---

## 4. Public Compose screen API

Every signature below is a compile-time contract. Default parameters must stay defaulted exactly
(`hiltViewModel()` or the stated literal). Annotations shown are required for source compatibility.
Screens returning `Unit` must be callable with named arguments.

```kotlin
// ui/screens/library
@Composable fun LibraryScreen(navController: NavController)
@Composable fun LibrarySongsScreen(navController: NavController, onDeselect: () -> Unit,
    viewModel: LibrarySongsViewModel = hiltViewModel())
@Composable fun LibraryAlbumsScreen(navController: NavController, onDeselect: () -> Unit,
    viewModel: LibraryAlbumsViewModel = hiltViewModel())
@Composable fun LibraryArtistsScreen(navController: NavController, onDeselect: () -> Unit,
    viewModel: LibraryArtistsViewModel = hiltViewModel())
@Composable fun LibraryPlaylistsScreen(navController: NavController,
    filterContent: @Composable () -> Unit,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
    initialTextFieldValue: String? = null, allowSyncing: Boolean = true)
@Composable fun LibraryMixScreen(navController: NavController,
    filterContent: @Composable () -> Unit,
    viewModel: LibraryMixViewModel = hiltViewModel())
@Composable fun LibraryPodcastsScreen(navController: NavController, onDeselect: () -> Unit,
    viewModel: PodcastsViewModel = hiltViewModel())
@Composable fun LibrarySpotifyScreen(navController: NavController,
    filterContent: @Composable () -> Unit,
    viewModel: LibrarySpotifyViewModel = hiltViewModel())
@Composable internal fun SpotifyConnectBanner(...)        // banner used by LibrarySpotifyScreen
@Composable fun LocalSongScreen(navController: NavController,
    onBack: () -> Unit = { navController.navigateUp() },
    isEmbedded: Boolean = false,
    filterContent: (@Composable () -> Unit)? = null,
    viewModel: LocalSongsViewModel = hiltViewModel())

// ui/screens/search
@Composable fun SearchScreen(navController: NavController, pureBlack: Boolean,
    homeViewModel: HomeViewModel = hiltViewModel(),
    moodAndGenresViewModel: MoodAndGenresViewModel = hiltViewModel())
@Composable fun LocalSearchScreen(query: String, navController: NavController,
    onDismiss: () -> Unit, isFromCache: Boolean = false, pureBlack: Boolean,
    viewModel: LocalSearchViewModel = hiltViewModel())
@Composable fun OnlineSearchScreen(query: String, onQueryChange: (TextFieldValue) -> Unit,
    navController: NavController, onSearch: (String) -> Unit, onDismiss: () -> Unit,
    pureBlack: Boolean, viewModel: OnlineSearchSuggestionViewModel = hiltViewModel())
@Composable fun OnlineSearchResult(navController: NavController,
    viewModel: OnlineSearchViewModel = hiltViewModel(), pureBlack: Boolean = false)
@Composable fun SuggestionItem(modifier: Modifier = Modifier, query: String, online: Boolean,
    shape: Shape, onClick: () -> Unit, onDelete: () -> Unit = {},
    onFillTextField: () -> Unit, pureBlack: Boolean)
@Composable fun ExploreGenreCard(category: ExploreGenreCategory, onClick: () -> Unit,
    modifier: Modifier = Modifier)

// ui/screens/search/suggestions
@Composable fun SuggestionRegionSheet(currentRegionSlug: String,
    onRegionSelected: (String) -> Unit, onDismiss: () -> Unit)
@Composable fun RegionListItem(headlineContent: @Composable (() -> Unit), selected: Boolean,
    items: Int, index: Int, supportingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit)
@Composable fun SuggestionsTabContent(navController: NavController,
    viewModel: SuggestionsViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp))
@Composable fun TrendingAppleMusicSection(tracks: List<SuggestionTrack>, countryCode: String,
    onTrackClick: (SuggestionTrack) -> Unit, onMoreClick: () -> Unit)
@Composable fun TopArtistsSection(artists: List<SuggestionArtist>,
    onArtistClick: (SuggestionArtist) -> Unit)
@Composable fun TrendingAlbumsSection(albums: List<SuggestionAlbum>,
    onAlbumClick: (SuggestionAlbum) -> Unit, onMoreClick: () -> Unit)
@Composable fun TrendingVideosSection(videos: List<SuggestionTrack>,
    onVideoClick: (SuggestionTrack) -> Unit, onMoreClick: () -> Unit)

// ui/screens root browse surfaces
@Composable fun HomeScreen(navController: NavController, snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel())
@Composable fun CommunityPlaylistCard(item: CommunityPlaylistItem, onClick: () -> Unit,
    onSongClick: (SongItem) -> Unit, modifier: Modifier = Modifier)
@Composable fun BrowseScreen(navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior, browseId: String?,
    viewModel: BrowseViewModel = hiltViewModel())
@Composable fun ExploreScreen(navController: NavController,
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    chartsViewModel: ChartsViewModel = hiltViewModel())
@Composable fun ChartsScreen(navController: NavController,
    viewModel: ChartsViewModel = hiltViewModel())
@Composable fun NewReleaseScreen(navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior, viewModel: NewReleaseViewModel = hiltViewModel())
@Composable fun MoodAndGenresScreen(navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior, viewModel: MoodAndGenresViewModel = hiltViewModel())
@Composable fun MoodAndGenresButton(title: String, onClick: () -> Unit,
    modifier: Modifier = Modifier)
@Composable fun YouTubeBrowseScreen(navController: NavController,
    viewModel: YouTubeBrowseViewModel = hiltViewModel())
@Composable fun AlbumScreen(navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior, viewModel: AlbumViewModel = hiltViewModel())

// ui/screens/artist
@Composable fun ArtistScreen(navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior, viewModel: ArtistViewModel = hiltViewModel())
@Composable fun ArtistSongsScreen(navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior, viewModel: ArtistSongsViewModel = hiltViewModel())
@Composable fun ArtistAlbumsScreen(navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior, viewModel: ArtistAlbumsViewModel = hiltViewModel())
@Composable fun ArtistItemsScreen(navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior, viewModel: ArtistItemsViewModel = hiltViewModel())

// ui/screens/playlist
@Composable fun OnlinePlaylistScreen(navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior, viewModel: OnlinePlaylistViewModel = hiltViewModel())
@Composable fun LocalPlaylistScreen(navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior, viewModel: LocalPlaylistViewModel = hiltViewModel())
@Composable fun LocalPlaylistHeader(playlist: Playlist, songs: List<PlaylistSong>,
    onShowEditDialog: () -> Unit, onShowRemoveDownloadDialog: () -> Unit,
    onshowDeletePlaylistDialog: () -> Unit, onStartSearch: () -> Unit, onExport: () -> Unit,
    snackbarHostState: SnackbarHostState, modifier: Modifier)
@Composable fun AutoPlaylistScreen(navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior, viewModel: AutoPlaylistViewModel = hiltViewModel())
@Composable fun CachePlaylistScreen(navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior, viewModel: CachePlaylistViewModel = hiltViewModel())
@Composable fun TopPlaylistScreen(navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior, viewModel: TopPlaylistViewModel = hiltViewModel())
@Composable fun BottomPlaylistScreen(navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior, viewModel: BottomPlaylistViewModel = hiltViewModel())
@Composable fun SpotifyPlaylistDetailScreen(navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior, playlistId: String, playlistTitle: String,
    playlistCover: String, viewModel: SpotifyPlaylistDetailViewModel = hiltViewModel())
@Composable fun AiModifyPlaylistDialog(playlistId: String, currentSongs: List<PlaylistSong>,
    onDismiss: () -> Unit)
fun uriToByteArray(context: Context, uri: Uri): ByteArray?
```

`com.revenge.music.ui.screens.playlist.PlaylistType` enum (public) must exist; current members are
used by playlist menus and may be extended only additively.

---

## 5. State models and preferences

### 5.1 Home

```kotlin
sealed class HomeSection(val id: String, val baseWeight: Int) {
  data object Shortcuts : HomeSection("shortcuts", 100)
  data object HeroCarousel : HomeSection("hero_carousel", 90)
  data object MadeForYou : HomeSection("made_for_you", 80)
  data object AccountPlaylists : HomeSection("account_playlists", 40)
  data object ForgottenFavorites : HomeSection("forgotten_favorites", 30)
  data object FromTheCommunity : HomeSection("from_the_community", 20)
  data class SimilarRecommendation(val index: Int) : HomeSection("similar_recommendation_$index", 10)
  data class HomePageSection(val index: Int) : HomeSection("home_page_section_$index", 10)
  data object MoodAndGenres : HomeSection("mood_and_genres", 5)
}

data class DailyDiscoverItem(val seed: Song, val recommendation: YTItem,
  val relatedEndpoint: BrowseEndpoint?)
data class CommunityPlaylistItem(val playlist: PlaylistItem, val songs: List<SongItem>)
```

Home section composition and ordering (observable, deterministic for fixed seed):

1. Podcast mode: when a selected chip is a podcast chip, or any home section contains a podcast
   episode/show, Home renders only `HomePageSection(i)` entries whose section has podcast items.
2. Main mode (`selectedChip == null`): include `Shortcuts` when the preference is on and
   `speedDialItems` is non-empty; `HeroCarousel` when on and `heroSongs` non-empty; `MadeForYou`
   when `dailyDiscover` or the `aiRecommendedPlaylist` songs are non-empty; `AccountPlaylists`,
   `FromTheCommunity` (preference-gated), `ForgottenFavorites`; at most 2
   `SimilarRecommendation`; `MoodAndGenres` (preference-gated).
3. YouTube Music `homePage.sections` are appended as `HomePageSection(i)`, excluding sections
   whose `isStationListenTogether` is true.
4. Ordering: `Shortcuts` first, `HeroCarousel` second; the first three YouTube `HomePageSection`s
   next (10000 − index); the remaining sections last (−index); personal sections between, ordered
   by weight when `randomizeHomeOrder` is off: `MadeForYou` 900, `AccountPlaylists` 800,
   `FromTheCommunity` 700, `SimilarRecommendation` 600 − index, `ForgottenFavorites` 500,
   `MoodAndGenres` 400, else 0. With randomization on, each personal section receives a stable
   pseudo-random score derived from `randomSeed + section.id.hashCode()` (range 0..999).
   Ties keep insertion order.

### 5.2 Library tabs

`private enum class LibrarySourceTab { IN_APP, SPOTIFY }`.
`LibraryViewModel` holds `val filter: MutableState<LibraryFilter>`; the hub uses
`rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)` as the live filter.

`LibraryFilter` (in `core` `com.revenge.music.constants`): `SONGS, ARTISTS, ALBUMS, PLAYLISTS,
LIBRARY, LOCAL, SPOTIFY, PODCASTS`.

`LibraryScreen` state machine:

- `selectedSourceTab` is `rememberSaveable` and initialised from the persisted filter: a
  `SPOTIFY` filter starts on the Spotify tab.
- The filter row offers Playlists, Songs, Albums, Artists, Podcasts, Local. Tapping the already
  selected chip returns to `LIBRARY`. Selecting a chip while on the Spotify tab switches to
  `IN_APP` and applies that chip.
- `LIBRARY` renders the in-app/Spotify pager with a sliding two-tab selector; `SPOTIFY` in the
  persisted filter is normalised to `LIBRARY` and the Spotify tab.
- Back behaviour: when filter ≠ `LIBRARY`, Back resets filter to `LIBRARY`; when on the Spotify
  tab, Back returns to the in-app tab. Otherwise Back leaves the screen.
- The FAB opens a menu: Create playlist, Create from Taste, Import playlist. Import menu offers
  Spotify import (`settings/spotify_import`) and YouTube Music URL import (validated with
  `onlinePlaylistIdFromUrl`; invalid → toast).
- Sub-screens receive the shared `filterContent` row and dismiss via `onDeselect` (returns to
  `LIBRARY`).

Persisted enum preferences and defaults used by this cluster (must keep these names/defaults):

| Preference key | Type | Default used by UI |
|---|---|---|
| `ChipSortTypeKey` | `LibraryFilter` | `LIBRARY` |
| `SongFilterKey` | `SongFilter` | `LIKED` |
| `AlbumFilterKey` | `AlbumFilter` | `LIKED` |
| `ArtistFilterKey` | `ArtistFilter` | `LIKED` |
| `SongSortTypeKey` / `AlbumSortTypeKey` / `ArtistSortTypeKey` / `PlaylistSortTypeKey` / `MixSortTypeKey` | sort enums | `CREATE_DATE` |
| `SongSortDescendingKey` etc. | `Boolean` | `true` |
| `AlbumViewTypeKey` / `ArtistViewTypeKey` / `PlaylistViewTypeKey` | `LibraryViewType` | `GRID` |
| `GridItemsSizeKey` | `GridItemSize` | `BIG` |
| `HideExplicitKey`, `HideVideoSongsKey`, `HideYoutubeShortsKey` | `Boolean` | `false` |
| `YtmSyncKey` | `Boolean` | `true` |
| `SearchSourceKey` | `SearchSource` | `ONLINE` |
| `PauseSearchHistoryKey` | `Boolean` | `false` |
| `SuggestionRegionKey` | `String` | `"system"` |
| `PureBlackKey`, `DarkModeKey` | theme | `false`, `AUTO` |
| `TopSize` | `String` (numeric) | `"50"` |

Enums (in `core`, package `com.revenge.music.constants`):

- `LibraryViewType { LIST, GRID }` with `toggle()`.
- `SongFilter { LIBRARY, LIKED, DOWNLOADED, UPLOADED, EXPORTED }`.
- `ArtistFilter { LIBRARY, LIKED }`; `AlbumFilter { LIBRARY, LIKED, UPLOADED }`.
- `SongSortType { CREATE_DATE, NAME, ARTIST, PLAY_TIME }`.
- `PlaylistSongSortType { CUSTOM, CREATE_DATE, NAME, ARTIST, PLAY_TIME }`.
- `ArtistSortType { CREATE_DATE, NAME, SONG_COUNT, PLAY_TIME }`.
- `ArtistSongSortType { CREATE_DATE, NAME, PLAY_TIME }`.
- `AlbumSortType { CREATE_DATE, NAME, ARTIST, YEAR, SONG_COUNT, LENGTH, PLAY_TIME }`.
- `PlaylistSortType { CREATE_DATE, NAME, SONG_COUNT, LAST_UPDATED }`.
- `MixSortType { CREATE_DATE, NAME, LAST_UPDATED }`.
- `GridItemSize { BIG, SMALL }`.
- `MyTopFilter { ALL_TIME, DAY, WEEK, MONTH, YEAR }` with
  `toTimeMillis()`: DAY = now − 1 day, WEEK = now − 1 week, MONTH = now − 1 month,
  YEAR = now − 12 months, ALL_TIME = 0 (UTC epoch millis).
- `SearchSource { LOCAL, ONLINE }` with `toggle()`.

### 5.3 Search models

```kotlin
enum class LocalFilter { ALL, SONG, ALBUM, ARTIST, PLAYLIST }
data class LocalSearchResult(val query: String, val filter: LocalFilter,
  val map: Map<LocalFilter, List<LocalItem>>)

data class SearchSuggestionViewState(
  val history: List<SearchHistory> = emptyList(),
  val suggestions: List<String> = emptyList(),
  val items: List<YTItem> = emptyList(),
  val isFromLink: Boolean = false)

data class ExploreGenreCategory(val title: String, val searchQuery: String,
  val startColor: Color, val endColor: Color, val imageUrl: String)
```

`SearchScreen` renders a fixed, ordered list of `ExploreGenreCategory` cards (2-column grid) when
the search field is not active. Each card has its own title/query/gradient/unsplash image.

### 5.4 Suggestions models

```kotlin
data class SuggestionTrack(val rank: Int, val title: String, val artist: String,
  val thumbnailUrl: String?, val appleMusicUrl: String? = null)
data class SuggestionArtist(val rank: Int, val name: String, val thumbnailUrl: String?)
data class SuggestionAlbum(val rank: Int, val title: String, val artist: String,
  val thumbnailUrl: String?, val appleMusicUrl: String? = null)
```

### 5.5 Spotify models

```kotlin
data class SpotifyLibraryUiState(
  val isAuthenticated: Boolean = false, val accountName: String? = null,
  val avatarUrl: String? = null, val playlists: List<SpotifyPlaylist> = emptyList(),
  val likedSongsCount: Int? = null, val isLoading: Boolean = false,
  val error: String? = null)

data class SpotifyPlaylistDetailUiState(
  val playlistId: String = "", val title: String = "", val coverUrl: String? = null,
  val tracks: List<SpotifyTrack> = emptyList(), val totalCount: Int = 0,
  val isLoading: Boolean = false, val resolvingTrackId: String? = null,
  val error: String? = null)
```

### 5.6 Local songs model

```kotlin
data class LocalSongsScanState(
  val isScanning: Boolean = false,
  val lastSummary: LocalSongScanSummary? = null,
  val errorMessage: String? = null)
```

`LocalSongSortType` is a private enum in `LocalSongScreen.kt` with at least `MODIFIED`, `NAME`,
`ARTIST`, `ALBUM`; persisted values that fail `valueOf` fall back to `MODIFIED`.

---

## 6. Utility contracts

### 6.1 `com.revenge.music.utils.PlaylistNavigation`

```kotlin
fun validOnlinePlaylistId(playlistId: String?): String?
fun onlinePlaylistIdFromUrl(url: String): String?
fun onlinePlaylistRoute(playlistId: String?): String?
fun libraryPlaylistRoute(localPlaylistId: String, browseId: String?, isEditable: Boolean,
    songCount: Int, remoteSongCount: Int?): String
```

Invariants:

- `validOnlinePlaylistId` returns `null` for `null`, blank, whitespace-only, or trim-equal to
  `"null"`; otherwise the value unchanged (including uppercase `NULL`).
- `onlinePlaylistIdFromUrl` parses a `list` query parameter from an absolute URI (leading/trailing
  whitespace tolerated), URL-decodes it exactly once, then applies `validOnlinePlaylistId`.
  Invalid URI, missing/malformed `list`, empty/blank/`null` id, or `%`-invalid escape → `null`.
- `onlinePlaylistRoute` returns `"online_playlist/" + encode(id)` or `null`. Encoding is UTF-8
  `URLEncoder` with `+` replaced by `%20`; decoding the segment gives back the original id.
- `libraryPlaylistRoute` returns the online route only when the playlist is not editable, has zero
  local songs, its remote song count is not `0`, and a valid browse id exists; otherwise
  `"local_playlist/" + encode(localPlaylistId)`.

### 6.2 `com.revenge.music.utils.HeroCarouselSongs`

```kotlin
fun Song.toCarouselSong(): SongItem
fun selectHeroCarouselSongs(
  recentlyPlayed: List<SongItem>, library: List<SongItem>,
  recommendations: List<SongItem>, account: List<SongItem> = emptyList(),
  excludedSongIds: Set<String> = emptySet(), blockedArtists: Set<String> = emptySet(),
  hideExplicit: Boolean = false, hideVideoSongs: Boolean = false, limit: Int = 20
): List<SongItem>
```

Behaviour:

- Non-positive `limit` → empty list, no source reading required.
- Each source is filtered in order: excluded ids, blocked artists, explicit (if hidden), video
  songs (if hidden), then de-duplicated by id. Sources are round-robin interleaved
  (recentlyPlayed, [library/account interleaved], recommendations), and merged results are
  de-duplicated by id with the earlier (more recent) entry winning.
- Selection avoids repeating album art: a song is accepted when neither its `album.id` nor the
  normalized thumbnail key (`substringBefore('?').substringBefore('=')`) has been used.
  Songs rejected for repeated artwork are appended after the accepted list and the whole result is
  truncated to `limit`.
- `toCarouselSong()` maps local entities preserving explicit/video flags and uses `""` for a
  null thumbnail.

### 6.3 `com.revenge.music.utils.QuickPickSongs`

```kotlin
fun filterQuickPickSongs(songs: List<Song>, excludedSongIds: Set<String>,
  blockedArtists: Set<String>, hideExplicit: Boolean, hideVideoSongs: Boolean): List<Song>
```

Invariants: drops songs with `hideFromQuickPicks`; drops excluded ids; maps to `SongItem` and
applies the same blocked-artist/explicit/video filters; returns the original `Song` objects in
input order, de-duplicated by id. Hidden ids can never be restored by a later fallback.

### 6.4 Apple Music scraper

`object AppleMusicScraper` (public) exposes:
`fetchTopSongs(countryCode: String = "us"): List<SuggestionTrack>`,
`fetchTopAlbums(...): List<SuggestionAlbum>`,
`fetchTopVideos(...): List<SuggestionTrack>`,
`getTrendingArtists(tracks: List<SuggestionTrack>): List<SuggestionArtist>`.
Network failures return an empty list (never throw). Track/album rank is 1-based. Artwork URLs
have the size token replaced: songs/albums `→ 1400x1400`, videos `→ 1920x1080`. Videos are
returned sorted by rank.

---

## 7. Screen behaviour

Common conventions for all screens:

- A top app bar (or header row) shows the screen title and a back button; long-press on a back
  button returns to the main screen (`navController.backToMain()` where available).
- List/grid items support single click and long click. Long click triggers a haptic and a context
  menu (`SongMenu`, `AlbumMenu`, `ArtistMenu`, `PlaylistMenu`, or the YouTube variants).
- Tapping the now-playing item toggles play/pause; tapping another song starts a queue (radio,
  list, album, or playlist as appropriate) at that index.
- Scroll position/selection survive config change where explicitly `rememberSaveable`; transient
  loading flows do not.
- Pull-to-refresh surfaces use Material 3 `PullToRefreshBox` with an indicator aligned top-center.
- Feed screens scroll to top when the back-stack entry's `savedStateHandle["scrollToTop"]` becomes
  true, then reset that flag.
- Shimmer placeholders (not spinners) are used for first-load list/grid content; contained
  wavy/circular progress indicators are used for in-place loads (album header, playlist header,
  detailed playlist, suggestions).

### 7.1 Home (`HomeScreen`)

Inputs: `HomeViewModel` state flows plus preferences. Actions/effects:

- `NetworkReload` re-fetches on network restoration.
- Pull-to-refresh calls `viewModel.refresh()`.
- Infinite scroll requests `loadMoreYouTubeItems(homePage?.continuation)` when the last three
  items become visible.
- Chip selection toggles the chip and filters the page; Back clears a selected chip.
- Search/FAB-style actions: "surprise me" randomizes (`getRandomItem`), toggling pin on shortcut
  items (`togglePin`), and scroll-to-top from the tab bar.
- Empty state: the page simply omits absent sections. It must never show a section header with no
  content.
- Loading: chips row shimmer while `isLoading` and no explore page; personal section shimmers;
  hero/shortcut sections do not render placeholders.
- The hero carousel uses `heroSongs`; quick picks use `quickPicks` and are horizontally snap-
  scrollable; forgotten favorites use a separate lazy grid.
- Podcast mode renders follow-up episode content (`PodcastsViewModel.newEpisodes`,
  `episodesInProgress`).

### 7.2 Library sub-screens

Common: a dismiss chip labelled with the active filter (`onDeselect`), a chips row for the
sub-filter, a `SortHeader` (sort enum + ascending/descending), and a count label.
Selecting songs/albums/artists supports long-press multi-select; selection mode shows a top bar
with `n_selected` title, select-all checkbox, and a contextual action bar. Back exits selection
mode.

- **Songs**: filters LIKED / LIBRARY / UPLOADED / DOWNLOADED / EXPORTED; sorts CREATE_DATE /
  NAME / ARTIST / PLAY_TIME; hides explicit per preference. DOWNLOADED rows show size and a
  download icon; other filters show liked/download icons. Click starts `ListQueue("All songs")`
  at the tapped index; long-press selects.
- **Albums**: filters LIKED / LIBRARY / UPLOADED; sorts CREATE_DATE / NAME / ARTIST / YEAR /
  SONG_COUNT / LENGTH / PLAY_TIME; grid or list view. Empty text `library_album_empty`.
- **Artists**: filters LIKED / LIBRARY; sorts CREATE_DATE / NAME / SONG_COUNT / PLAY_TIME; grid or
  list. Empty text `library_artist_empty`.
- **Playlists**: special pinned rows (Liked, Offline, Exported, Cached, My Top N, My Bottom 50,
  Taste Mix) plus user playlists; sorts CREATE_DATE / NAME / SONG_COUNT / LAST_UPDATED; grid or
  list; `initialTextFieldValue`/`allowSyncing` pass through to the create dialog. Pinned rows
  render before normal rows; empty text `library_playlist_empty`.
- **Mix**: unified feed of playlists, artists and albums, pinned first; pull-to-refresh; syncs
  when `YtmSyncKey`; sorts CREATE_DATE / NAME / LAST_UPDATED; empty texts
  `library_playlist_empty` / `library_artist_empty` / `library_album_empty` for their sections.
- **Podcasts**: followed shows, filter chip; empty `library_podcast_empty`.
- **Spotify**: unauthenticated → connect banner (`SpotifyConnectBanner`) and `SpotifyEmptyState`;
  authenticated → profile header (name/avatar), liked-songs count, playlist grid, pull-to-refresh.
  Loading with no playlists shows a shimmer; error shows `uiState.error` while keeping prior
  content; playlists sortable by name (locale collator, PRIMARY strength) in ascending/descending.
- **Local songs**: storage permission gate; scan sheet with minimum duration, include/exclude
  folder modes, dedicated folder pickers, scan summary (scanned/removed counts), scanning
  progress, and error text. Songs list filters unsupported MIME types, supports text search over
  title/album/artist, sorts MODIFIED / NAME / ARTIST / ALBUM with locale collator and
  ascending/descending; empty state offers the scan sheet. Scanning is single-flight (a second
  request is ignored while `isScanning`).

### 7.3 Search

**SearchScreen**

- Inactive state: title "Search", pill-shaped search bar (or focused field when active), and the
  genre-card grid.
- Active state: shows local or online suggestion screen after an 80 ms debounce; Back clears and
  closes the field.
- Source toggle switches `SearchSource` (icon: library for LOCAL, globe for ONLINE) and persists.
- Placeholder: "Artists, Songs, Lyrics & More" online / `search_library` local.
- Clear button clears text; mic launches speech recognition. Voice result, when non-blank,
  navigates to `search/{encoded}` (or URL-parsed target) and records history unless paused.
- Submit with IME action or suggestion: closes keyboard/search, parses URLs, navigates, records
  history. Empty queries are ignored.
- On resume while the player is expanded, keyboard and focus are dropped; on pause, focus and
  keyboard are also dropped.

**OnlineSearchScreen** (suggestion overlay)

- History appears only when the query is empty; each row can be clicked (search), have its text
  filled into the field, or be deleted from history.
- While typing, only result items are shown (history and text suggestions are intentionally not
  rendered), plus a parsed-link item when the query is a YouTube URL.
- Debounce: URLs are applied immediately, other text after 300 ms.
- Headers: `search_history`, `suggestions`, and `top_result` or `parsed_from_link`.
- Result item click navigates/plays as in §7.2; selecting any result dismisses the overlay.
- Scrolling hides the keyboard.

**OnlineSearchResult**

- Reads `query` from the navigation arguments, URL-decodes once, and seeds the editable field with
  the cursor at the end. Changing the route query re-seeds and clears focus.
- Filter chips: All, Songs, Videos, Albums, Artists, Community playlists, Featured playlists,
  Podcasts, Episodes. Changing filter scrolls to top and lazily loads that filter's first page.
- All-filter renders grouped summaries with section titles; empty result shows
  `no_results_found` with a search icon.
- Filtered mode renders a flat de-duplicated list and a shimmer row while a continuation exists;
  empty shows `no_results_found`.
- Loading: shimmer placeholders before the first page arrives.
- Pagination: when the item keyed `"loading"` becomes visible, `viewModel.loadMore()` is called.
- Search submission from the field navigates to the new `search/{query}` route, popping the
  current query route, and records history unless paused. History is inserted off the main thread.
- A focused search overlay (`OnlineSearchScreen`) is shown above the results when the field gains
  focus.

**LocalSearchScreen**

- Filters ALL / SONG / ALBUM / ARTIST / PLAYLIST; empty query yields an empty result map.
- `ALL` queries songs, albums, artists and playlists, each limited to `PREVIEW_SIZE = 3`, then
  groups results by type. `hideVideoSongs` removes video songs.
- Each group is shown under its section header; groups may be clicked when the query is non-empty.
- `isFromCache` changes the search scope/copy only; it must not alter the VM contract.

**Suggestions tab**

- Region defaults to `"system"` (resolved to the lowercase `Locale.getDefault().country`).
  Selecting a region refreshes.
- Pull-to-refresh forces a manual refresh (indicator only during manual refresh).
- Initial loading with all four sections null shows a centered wavy progress indicator.
- Sections render only when their state is non-null; empty state after a completed load shows
  "No suggestions available at the moment." and a Refresh button.
- Footer credits "Data from Apple Music" / "revenge".
- Track/artist/album/video clicks resolve through YouTube search and navigate or play; failures
  are silent.
- Section caps: top songs are displayed up to 29 in paged rows of 5; albums/artists/videos render
  their provided lists.

**Region sheet**: modal bottom sheet with a search field; lists every region in
`SuggestionRegionSlugToName` except `"system"` filtered case-insensitively by name; selected row
is highlighted; tapping invokes `onRegionSelected(slug)`.

### 7.4 Browse surfaces

- **BrowseScreen**: consumes `browseId`, loads a `BrowseResult`, flattens its sections into one
  grid (`result.items.flatMap { it.items }`), title from the VM. Loading shows shimmer grid
  placeholders; missing `browseId` leaves an empty list. Item click/long-click follows §7.2.
- **ExploreScreen**: shows charts and explore content in a vertical scroll. While charts are
  loading or either page is null, show shimmer section placeholders. Scroll-to-top is driven by
  the `scrollToTop` back-stack flag. Loads charts once if absent.
- **ChartsScreen**: title `charts`; loads charts when null; shows shimmer while loading or before
  first page. Sections are appended on `loadMore` (when the last section's continuation becomes
  visible). `error` is exposed; the screen must remain usable and retry on next load.
- **NewReleaseScreen**: grid of `newReleaseAlbums` de-duplicated by id; shimmer (8 cells) while the
  list is empty; title `new_release_albums`.
- **MoodAndGenresScreen**: sections of mood chips, 2 per row portrait / 3 landscape; shimmer (8
  rows) while null; clicking a chip navigates to `youtube_browse/{browseId}?params={params}`;
  title `mood_and_genres`.
- **YouTubeBrowseScreen**: grid of all items from the browse result, de-duplicated by id; shimmer
  while result is null; title/back from the host top bar.

### 7.5 Album and artist screens

- **AlbumScreen**: reads `albumId` from `SavedStateHandle`. Shows a collapsing artwork header with
  title, single artist link, album metadata (`Album • year • N Tracks • duration`, explicit badge),
  play/shuffle buttons, and a song list. `playlistId` is fetched from YouTube for non-local albums;
  other versions and "releases for you" are shown when non-empty. Description text/runs are shown
  when available (YouTube, then Wikipedia, then Apple Music). Local albums skip YouTube fetching.
  Loading: contained loading indicator in the header area; the list may render cached songs
  immediately. `NOT_FOUND` removes the cached album. Songs are filtered by explicit/video
  preferences and remain playable from the cached list.
- **ArtistScreen**: shimmer until `artistPage`/local artist resolves. Header shows image (or local
  thumbnail), optional background video (disabled by Data Saver), title, subscriber/monthly
  listener stats when available, radio/play/shuffle actions, and description when enabled.
  Sections: songs, albums, related artists, and videos, each capped and linking to the dedicated
  "see all" routes `artist/{id}/songs`, `artist/{id}/albums`, and
  `artist/{id}/items?browseId=...&params=...`. A "local" toggle switches to the device library
  content for the artist (`showLocal`). Blocked-artist filtering applies. Follow/unfollow and
  share actions follow the standard menu contracts.
- **ArtistSongsScreen**: local library songs for the artist, sorted CREATE_DATE / NAME /
  PLAY_TIME; count plural `n_song`; click plays list queue.
- **ArtistAlbumsScreen**: local library albums preview in a grid; multi-select supported.
- **ArtistItemsScreen**: paged remote list/grid depending on first item type (song → list,
  otherwise grid); `title` from the page; shimmer while null; `loadMore()` when a `"loading"` item
  is visible; empty continuation hides the loading row. Filters explicit, video songs, and
  duplicate ids.

### 7.6 Playlist screens

All playlist screens expose a collapsing header (artwork, title, owner/description, track count and
duration), Play and Shuffle actions, a Save/Download/Share/More action row, a search field that
filters the visible rows, and a sort control where listed.

- **OnlinePlaylistScreen**: reads `playlistId` from args. States: initial loading (contained
  indicator), error (message plus retry), loaded (header + songs), loading-more indicator at the
  bottom. Pagination:
  - Initial page plus proactive background pages up to 200 songs.
  - `loadMoreSongs()` at list end loads one more page; if still below 200 it resumes proactive
    loading.
  - `loadAllSongs()` (search/save/download paths) loads up to 5000 songs.
  - Songs are de-duplicated by id and filtered by the hide-video-songs preference; if filtering
    would empty a non-empty playlist, the unfiltered list is kept to avoid a false empty state.
  - Podcast show pages attribute the show as artist on episodes lacking one; progress is merged.
  - Related items render in a "You might also like" section with the same navigation mapping.
  - `retry()` restarts the initial fetch. Leaving the screen cancels background loading.
  - Empty list text `playlist_is_empty`; no-results text `no_results_found` when searching.
- **LocalPlaylistScreen**: reads `playlistId`; header edits name/cover, exports, removes downloads,
  deletes the playlist, syncs with Spotify (when the playlist is Spotify-linked), opens AI modify,
  starts in-list search, and shows snackbar feedback. Song ordering supports CUSTOM (user order,
  drag/reorder) plus CREATE_DATE / NAME / ARTIST / PLAY_TIME. Empty state
  `playlist_is_empty`. Multi-select supported. `LocalPlaylistHeader` is public for reuse.
- **AutoPlaylistScreen**: arguments `liked`, `uploaded`, `exported`, otherwise "offline". Pull-to-
  refresh syncs the corresponding source. Sort CREATE_DATE / NAME / ARTIST / PLAY_TIME. Empty
  text `playlist_is_empty`.
- **CachePlaylistScreen**: cached songs list; sorts CREATE_DATE / NAME / ARTIST / PLAY_TIME; remove
  cached song action; empty `playlist_is_empty`; no results `no_results_found`.
- **TopPlaylistScreen**: argument `top` is the numeric size; title "My Top N". Period selector
  (`MyTopFilter`) with ALL_TIME / DAY / WEEK / MONTH / YEAR labels. Empty `playlist_is_empty`;
  about text `about_album`.
- **BottomPlaylistScreen**: argument `bottom`; title "My Bottom N"; same period/sort/search
  behaviour; about text `about_playlist` with `bottom_playlist_description(name, count)`.
- **SpotifyPlaylistDetailScreen**: reads `playlistId`, `title`, `cover` from SavedStateHandle.
  Shows a transparent top bar over the artwork; loading with no tracks shows a spinner; tracks
  list de-duplicated; search filters by name/artist/album; empty search shows
  `no_results_found`. Playing a track resolves it through `SpotifyPlaybackResolver` with
  `resolvingTrackId` feedback; failure shows an error snackbar. A Spotify sync action reports
  `spotify_sync_success`/`spotify_sync_failed`. Prewarms the first 15 tracks in the background.
- **AiModifyPlaylistDialog**: modal dialog with a natural-language prompt and a streaming log
  area; while processing, dismissal is blocked; completion produces an updated playlist or an
  error message. It takes `playlistId` and the current `PlaylistSong` list.

---

## 8. ViewModel contracts

All are `@HiltViewModel` with `@Inject constructor` (or default constructor) and extend
`androidx.lifecycle.ViewModel`. Public members are compile-time contracts. Private helpers may be
implemented freely.

### 8.1 `viewmodels/LibraryViewModels.kt`

- `LibrarySongsViewModel(context: Context, database: MusicDatabase, downloadUtil: DownloadUtil,
  syncUtils: SyncUtils)`:
  `val allSongs: StateFlow<List<Song>>` (initial empty; reacts to SongFilter, sort, descending,
  exported ids, hide-explicit/video), `fun syncLikedSongs()`, `fun syncLibrarySongs()`,
  `fun syncUploadedSongs()`.
- `LibraryArtistsViewModel(context, database, syncUtils)`:
  `val allArtists: StateFlow<List<Artist>>`, `fun sync()`. Initially empty; reacts to
  ArtistFilter/sort; refreshes stale/non-local artist metadata.
- `LibraryAlbumsViewModel(context, database, syncUtils)`:
  `val allAlbums: StateFlow<List<Album>>`, `fun sync()`; reacts to AlbumFilter/sort/hide-explicit;
  refreshes albums with `songCount == 0` and deletes `NOT_FOUND` albums.
- `LibraryPlaylistsViewModel(context, database, syncUtils)`:
  `val allPlaylists: StateFlow<List<Playlist>>`, `fun sync()`,
  `val topValue: Flow<String>` (default `"50"`).
- `PodcastsViewModel(context, database, ...)`: `val podcasts: StateFlow<List<Playlist>>`,
  `val exploreSections: MutableStateFlow<List<HomePage.Section>>`,
  `val isLoading / isRefreshing / isLoadingMore: MutableStateFlow<Boolean>`,
  `fun loadPodcastSections()`, `fun loadMorePodcastSections()`, `fun refresh()`,
  `val newEpisodes: MutableStateFlow<List<SongItem>>`, `fun loadNewEpisodes()`.
- `ArtistSongsViewModel(context, database, savedStateHandle)`:
  `val artist: StateFlow<Artist?>`, `val songs: StateFlow<List<Song>>`; reacts to
  ArtistSongSortType/descending/hide preferences.
- `LibraryMixViewModel(context, database, syncUtils)`:
  `val isRefreshing: StateFlow<Boolean>`, `val syncAllLibrary: () -> Unit`,
  `fun refresh()`, `val topValue: Flow<String>`, `var artists`, `var albums`, `var playlists`
  (observable library collections), plus sync entry points.
- `LibraryViewModel()`: `val filter: MutableState<LibraryFilter>` (initial `LIBRARY`).

### 8.2 `viewmodels/HomeViewModel.kt`

`HomeViewModel(context: Context, database: MusicDatabase, syncUtils: SyncUtils,
loadSpotifyRecommendationsUseCase: LoadSpotifyRecommendationsUseCase)` exposes at least:

- `isRefreshing`, `isLoading`, `isRandomizing`: `MutableStateFlow<Boolean>`.
- `dailyDiscover: MutableStateFlow<List<DailyDiscoverItem>?>`,
  `forgottenFavorites: MutableStateFlow<List<Song>?>`,
  `keepListening: MutableStateFlow<List<LocalItem>?>`,
  `similarRecommendations: MutableStateFlow<List<SimilarRecommendation>?>`,
  `accountPlaylists: MutableStateFlow<List<PlaylistItem>?>`,
  `homePage: MutableStateFlow<HomePage?>`, `explorePage: MutableStateFlow<ExplorePage?>`,
  `communityPlaylists: MutableStateFlow<List<CommunityPlaylistItem>?>`,
  `selectedChip: MutableStateFlow<HomePage.Chip?>`.
- `quickPicks: StateFlow<List<Song>?>`, `heroSongs: StateFlow<List<SongItem>>`,
  `aiRecommendedPlaylist`, `speedDialItems: StateFlow<List<YTItem>>`.
- `allLocalItems: MutableStateFlow<List<LocalItem>>`, `allYtItems: MutableStateFlow<List<YTItem>>`.
- `accountName: MutableStateFlow<String>` (initial `"Guest"`),
  `accountImageUrl: MutableStateFlow<String?>`.
- `suspend fun getRandomItem(): YTItem?`, `fun togglePin(item: YTItem)`,
  `fun loadMoreYouTubeItems(continuation: String?)`, `fun toggleChip(chip: HomePage.Chip?)`,
  `fun refresh()`.

Ranking invariants: `quickPicks` applies `filterQuickPickSongs` with hidden ids ∪ disliked ids,
blocked artists, hide-explicit and hide-video preferences. `heroSongs` applies
`selectHeroCarouselSongs` over recently played, a shuffled library (including account songs), and
recommendations (radio + similar + daily discover + home-page songs), with hidden ∪ disliked ids
excluded. Both flows must emit `null`/empty before sources load.

### 8.3 Browsing ViewModels

- `AlbumViewModel(database: MusicDatabase, savedStateHandle: SavedStateHandle)`:
  `val albumId: String` (non-null from `"albumId"`), `val playlistId: MutableStateFlow<String>`,
  `val albumWithSongs: StateFlow<AlbumWithSongs?>`, `var otherVersions:
  MutableStateFlow<List<AlbumItem>>`, `var releasesForYou: MutableStateFlow<List<AlbumItem>>`,
  `var description: MutableStateFlow<String?>`, `var descriptionRuns: MutableStateFlow<List<Run>?>`.
- `ArtistViewModel(context: Context, database: MusicDatabase, savedStateHandle: SavedStateHandle)`:
  `val artistId: String`, `var artistPage: ArtistPage?`, `val artistVideoUrl: StateFlow<String?>`,
  `val artistVideoSong: StateFlow<SongItem?>`, `val libraryArtist: StateFlow<Artist?>`,
  `val librarySongs: StateFlow<List<Song>>`, `val libraryAlbums: StateFlow<List<Album>>`,
  `fun fetchArtistsFromYTM()`.
- `ArtistAlbumsViewModel(database, savedStateHandle)`: `val artist: StateFlow<Artist?>`,
  `val albums: StateFlow<List<Album>>`.
- `ArtistItemsViewModel(context: Context, savedStateHandle)`:
  `val title: MutableStateFlow<String>`, `val itemsPage: MutableStateFlow<ItemsPage?>`,
  `fun loadMore()`. Constructor reads `"browseId"` (non-null) and `"params"`.
- `BrowseViewModel(savedStateHandle)`: `val items: MutableStateFlow<List<YTItem>?>` (initial empty),
  `val title: MutableStateFlow<String?>` (initial empty), reads `"browseId"`.
- `ExploreViewModel(context: Context, database: MusicDatabase)`:
  `val explorePage: MutableStateFlow<ExplorePage?>`; new-release albums are sorted by the user's
  artist play-time ranking (favourite artists first) and explicit-filtered.
- `ChartsViewModel()`: `val chartsPage: StateFlow<ChartsPage?>`, `val isLoading: StateFlow<Boolean>`,
  `val error: StateFlow<String?>`, `fun loadCharts()`, `fun loadMore()`.
- `NewReleaseViewModel(context: Context, database: MusicDatabase)`:
  `val newReleaseAlbums: StateFlow<List<AlbumItem>>`; same sort/filter rules as Explore.
- `MoodAndGenresViewModel()`: `val moodAndGenres: MutableStateFlow<List<MoodAndGenres>?>`.
- `YouTubeBrowseViewModel(context: Context, savedStateHandle)`:
  `val result: MutableStateFlow<BrowseResult?>`; reads `"browseId"` (non-null) and `"params"`;
  applies explicit/video/shorts filters.

### 8.4 Search ViewModels

- `LocalSearchViewModel(context: Context, database: MusicDatabase)`:
  `val query: MutableStateFlow<String>`, `val filter: MutableStateFlow<LocalFilter>`,
  `val result: StateFlow<LocalSearchResult>` (initial `("", filter, emptyMap())`),
  `companion object { const val PREVIEW_SIZE = 3 }`. Empty query → empty map. ALL is limited to
  `PREVIEW_SIZE` per type; a specific filter is unlimited. Hide-video preference applies.
- `OnlineSearchViewModel(context..., extensionManager, ...)`:
  `val query: MutableStateFlow<String>`, `val filter: MutableStateFlow<YouTube.SearchFilter?>`,
  `var summaryPage: SearchSummaryPage?`, `val viewStateMap: MutableStateMap<String, ItemsPage?>`,
  `fun loadMore()`. Unfiltered search fills `summaryPage`; a filter lazily fills
  `viewStateMap[filter.value]`; `loadMore()` follows the active filter's continuation.
- `OnlineSearchSuggestionViewModel(context: Context, database: MusicDatabase)`:
  `val query: MutableStateFlow<String>`,
  `val viewState: StateFlow<SearchSuggestionViewState>`.
  Empty query emits only history (disliked titles filtered out). Non-empty query emits only items
  (parsed URL item first, then de-duplicated YouTube suggestions, explicit/video filtered) and
  `isFromLink` when a URL was parsed. `suggestions` is always empty in the current contract.
- `LocalSearchViewModel`/`OnlineSearchSuggestionViewModel` results must be driven by flows
  (`flatMapLatest`) so that changing query/filter cancels stale work.

### 8.5 Playlist ViewModels

- `OnlinePlaylistViewModel(context: Context, savedStateHandle, database: MusicDatabase,
  podcastShows: PodcastShowStore)`:
  `val playlist: MutableStateFlow<PlaylistItem?>`,
  `val playlistSongs: MutableStateFlow<List<SongItem>>`,
  `val relatedItems: MutableStateFlow<List<YTItem>>`,
  `val isLoading / isLoadingMore: StateFlow<Boolean>`, `val error: StateFlow<String?>`,
  `val dbPlaylist: StateFlow<Playlist?>`, `val continuation: String?` (read-only),
  `fun loadMoreSongs()`, `suspend fun loadAllSongs(): List<SongItem>`, `fun retry()`.
  Constants: proactive limit 200, full-load cap 5000. Errors are non-empty human messages
  (fallback: exception simple name, then "Failed to load playlist").
- `LocalPlaylistViewModel(...)`: `val playlistId: String`, `val playlist: StateFlow<Playlist?>`,
  `val playlistSongs: StateFlow<List<PlaylistSong>>`, `val suggestions: StateFlow<...>`,
  `fun fetchSuggestions()`, `fun addSuggestedSong(song: SongItem)`,
  `val isSpotifySyncing: MutableStateFlow<Boolean>`,
  `suspend fun syncWithSpotify(): Boolean`.
- `TopPlaylistViewModel` / `BottomPlaylistViewModel`:
  `val top`/`val bottom: String` (non-null arg), `val topPeriod`/`bottomPeriod:
  MutableStateFlow<MyTopFilter>` (initial `ALL_TIME`),
  `val topSongs`/`val bottomSongs: StateFlow<List<PlaylistSong>?>` (null until first emission).
- `CachePlaylistViewModel`: `val cachedSongs: StateFlow<List<Song>>`,
  `fun removeSongFromCache(songId: String)`.
- `AutoPlaylistViewModel(...)`: `val playlist: String` (non-null arg),
  `val isRefreshing: StateFlow<Boolean>`, `val likedSongs`/uploaded/exported flows,
  `fun syncLikedSongs()`, `fun syncUploadedSongs()`, `fun refresh()`.
- `PlaylistsViewModel(...)`: `val allPlaylists` (+ sort preferences), `suspend fun sync()`.

### 8.6 Spotify ViewModels

- `LibrarySpotifyViewModel(context: Context, spotifyImportRepository: SpotifyImportRepository)`:
  `val uiState: StateFlow<SpotifyLibraryUiState>` (initial `isLoading = true`),
  `fun refresh()`, `fun loadLibrary(forceRefresh: Boolean = false)`,
  `suspend fun refreshAndAwait(): Boolean`.
  Re-loads whenever `SpotifySpDcKey` changes. Unauthenticated resets to the unauthenticated state;
  failures keep previous content and set `error`; success sets account fields and playlists.
- `SpotifyPlaylistDetailViewModel(savedStateHandle, spotifyImportRepository,
  spotifyPlaybackResolver)`: `val uiState: StateFlow<SpotifyPlaylistDetailUiState>`,
  `fun refresh()`, `fun loadTracks()`, `suspend fun refreshAndAwait(): Boolean`,
  `fun playTrack(track, tracks = uiState.tracks, startIndex: Int? = null,
  onQueueReady: (SpotifyQueue) -> Unit, onError: () -> Unit = {})`,
  `fun playTrack(track, onSuccess: (SongItem) -> Unit, onError: () -> Unit = {})`.
  `playlistId == "liked"` loads liked songs; otherwise playlist tracks. Errors set `error` and
  leave tracks as-is. `resolvingTrackId` is non-null only while a resolution is in flight.

### 8.7 Local songs

`LocalSongsViewModel(database: MusicDatabase, localSongScanner: LocalSongScanner)`:
`val scanState: StateFlow<LocalSongsScanState>`, `val songs: StateFlow<List<Song>>`,
`fun scanDevice(scanConfig: LocalSongScanConfig = LocalSongScanConfig())`.
Single-flight scanning; success sets `lastSummary` and clears error; failure sets `errorMessage`;
scanning always ends (`isScanning = false`).

---

## 9. Accessibility semantics

Required observable semantics (labels may come from resources; the content description must be
the same string used for the visible label where one exists):

- Every icon-only button has a non-empty `contentDescription` (search, dismiss/back, clear, mic,
  share, save, download, shuffle, more options, play/pause, region search).
- Back navigation in search results uses `dismiss` semantics.
- Filter chips and sort headers expose their text as the accessibility label and their selected
  state; selection-mode top bar announces `n_selected` (e.g. "3 selected").
- Playlist/detail play buttons announce play/pause state; download buttons announce
  saved/saving/download; shuffle has both `shuffle_content_desc` and `shuffle_label`.
- Decorative images (artwork, gradients) use `contentDescription = null`; artist/album
  clickable images carry the artist/album name via the adjacent text row.
- Local scan sheet hero icon semantics switch between scanning, error, permission required,
  completed, and ready; the primary button label is "Allow", "Add folders", or "Scan device".
- Empty states are announced as text (not colour alone); error states keep the retry action
  reachable.
- Minimum touch target 48 dp for interactive rows/buttons; text contrast follows the Material
  theme (pure-black mode uses the theme's on-surface colours).
- The screen title (`TopAppBar` title) is the first heading; list section headers use
  `NavigationTitle` semantics.

---

## 10. Acceptance tests

Tests are deterministic: use `kotlinx-coroutines-test`, fakes for DAOs/repositories, fixed clocks,
fixed locale (`en-US`), and a fixed random seed where shuffling is involved. UI-state assertions
should observe public StateFlows/data classes rather than implementation internals.

### 10.1 Navigation utilities (extend `PlaylistNavigationTest`)

- **P1** `validOnlinePlaylistId` returns null for null/""/" "/"\t\n"/"null"/" null "; preserves
  `"PLabc_123-xyz"`, `"OLAK5uy_album"`, `"LM"`, `"123"`, `"NULL"`.
- **P2** URL parsing rejects missing/malformed `list` and blank/`null` ids; decodes exactly once;
  round-trips a fully encoded id through `onlinePlaylistRoute` and `URLDecoder`.
- **P3** `libraryPlaylistRoute` chooses online only for non-editable, zero-local, remoteCount ≠ 0
  with a valid browse id; all other combinations stay local.

### 10.2 Home ranking helpers (extend existing tests)

- **H1** `selectHeroCarouselSongs` returns `limit` items, recent first, including library and
  recommendation sources; limit ≤ 0 → empty; empty sources → empty.
- **H2** De-duplication prefers the recent entry's metadata; account-only songs survive a large
  library; excluded ids/blocked artists never reappear from any source.
- **H3** Artwork spreading: songs sharing an album id or normalized thumbnail move after distinct
  artwork; sparse feeds still fill up to `limit`.
- **H4** Explicit/video preferences filter every source; flags survive `toCarouselSong`.
- **Q1** `filterQuickPickSongs` drops hidden and excluded ids, keeps order, de-duplicates, and
  applies blocked artist/explicit/video filters; hidden ids cannot be restored.

### 10.3 Home section state

- **HS1** With no chip and all sources populated, the section order is Shortcuts, HeroCarousel,
  first three YouTube sections, personal sections by weight, remaining YouTube sections.
- **HS2** With `randomizeHomeOrder = true` and a fixed seed, the order is stable across
  recomposition and differs for a different seed.
- **HS3** Podcast mode keeps only sections containing podcast items; personal sections absent.
- **HS4** Sections whose sources are empty are omitted; `isStationListenTogether` sections are
  excluded.

### 10.4 Library state

- **L1** `LibraryScreen` initial filter is `LIBRARY` with the in-app tab; a persisted `SPOTIFY`
  filter opens on the Spotify tab and normalises the filter to `LIBRARY`.
- **L2** Tapping the active chip resets to `LIBRARY`; tapping another chip from the Spotify tab
  switches to the in-app tab.
- **L3** Back on a non-library filter resets to `LIBRARY`; back on the Spotify tab returns to
  in-app; back at `LIBRARY`/in-app exits.
- **L4** `LibrarySongsViewModel`:
  LIKED vs LIBRARY vs DOWNLOADED vs UPLOADED select the corresponding DAO flows; EXPORTED uses the
  stored id set and drops unknown ids; sort/descending and hide-explicit/video are applied in
  order.
- **L5** `LocalSearchViewModel`: empty query → empty map; ALL returns at most 3 songs, albums,
  artists and playlists each, grouped by type; a specific filter returns all matches of that type;
  hide-video removes video songs; changing query/filter cancels the previous query (fake DAO
  records no stale result).
- **L6** `LocalSongsViewModel.scanDevice` is single-flight; success resets `isScanning` and sets
  the summary; failure sets `errorMessage` and resets `isScanning`.

### 10.5 Search state

- **S1** `OnlineSearchSuggestionViewModel`: empty query emits history only and filters history
  whose text matches a disliked title (equality, or substring containment when the title length
  is ≥ 3); non-empty query emits items only, parsed-link item first, de-duplicated, with
  `isFromLink` set for URLs.
- **S2** Search submit with empty text performs no navigation and writes no history; non-empty
  writes history exactly once unless `PauseSearchHistoryKey` is true.
- **S3** `OnlineSearchResult` reads and URL-decodes the route query; a changed route re-seeds the
  field; a selected filter scrolls to top and requests that filter's page; seeing the `"loading"`
  item requests the next page exactly once per continuation.
- **S4** Empty unfiltered and filtered results both expose `no_results_found`; before the first
  page, shimmer placeholders (not the empty state) render.
- **S5** `ChartsViewModel`: initial state null/idle; `loadCharts` success sets the page and clears
  error; failure sets a non-empty `error` and leaves `isLoading = false`; `loadMore` appends
  sections and replaces the continuation; `loadMore` with no continuation performs no request.
- **S6** Suggestions region: `"system"` resolves to the lowercase device country; changing region
  refreshes with that code; manual refresh clears all four sections before loading and exposes
  `isManualLoading` only while forced.

### 10.6 Playlist state

- **PL1** `OnlinePlaylistViewModel` initial state: `isLoading = true`; success populates playlist,
  songs, related items and continuation and becomes not loading; failure sets `error` and stops
  loading; `retry` re-fetches and clears the error.
- **PL2** Proactive loading stops at 200 songs; `loadMoreSongs` loads exactly one page while
  `isLoadingMore` is true and then resumes below 200; `loadAllSongs` stops at the continuation end
  or 5000 songs; duplicate ids are collapsed.
- **PL3** If the video-song filter would empty a non-empty playlist, the original songs remain.
- **PL4** `SpotifyLibraryViewModel`: no session → not authenticated, loading false, empty lists;
  success → authenticated with account fields and playlists; exception → loading false, error set,
  previous lists retained.
- **PL5** `SpotifyPlaylistDetailViewModel`: `playlistId == "liked"` uses liked songs; otherwise
  playlist tracks; error keeps prior tracks and sets `error`; `resolvingTrackId` is cleared on both
  success and failure; failed resolution calls `onError`.
- **PL6** Top/Bottom period selection: `MyTopFilter.toTimeMillis()` boundaries are now − 1
  day/week/month/12 months in UTC; ALL_TIME is 0.

### 10.7 Process recreation and saved state

- **R1** Killing and recreating the process restores: `albumId`, `artistId`/`browseId`/`params`,
  `playlistId` (online/local/Spotify), `playlist` (auto), `playlist` (cache), `top`/`bottom`,
  Spotify `title`/`cover`. Null missing ids follow the non-null contracts and must not crash the
  route; navigation-level invalid values are caught by route builders.
- **R2** `SearchScreen` restores the query text and position, `searchActive`, the selected source,
  and the genre grid state. `OnlineSearchResult` restores the query text with cursor at end.
- **R3** Playlist selection mode and selected ids survive config change; search field text in
  playlist headers survives config change.
- **R4** `LocalSongScreen` restores the scan sheet visibility and in-select mode; transient
  selection is cleared on process death (must not crash).

### 10.8 Widget/visual and screenshot targets

Compose screenshot/preview targets (render with fixed dark and light themes, phone width 411 dp
and tablet width 800 dp):

- **V1** Library hub: in-app mix grid, Spotify tab unauthenticated, songs list, albums empty
  placeholder, artists empty placeholder, playlists empty placeholder, local songs scan sheet
  (permission denied, scanning, summary, error).
- **V2** Search: inactive genre grid, active online suggestions with history and results, local
  suggestions, online results unfiltered with summaries, filtered results, empty results, loading
  shimmer.
- **V3** Home: populated sections, podcast mode, selected chip, pull-to-refresh indicator, chips
  shimmer.
- **V4** Detail screens: album header + list, artist header + sections, online playlist loading /
  error / loaded / loading-more, local playlist empty, top/bottom playlist, Spotify detail loaded
  and empty-search.
- **V5** Browse surfaces: explore shimmer and loaded, charts shimmer and loaded, new release,
  mood and genres, YouTube browse.
- **V6** Accessibility tree snapshots assert titles, chip labels, button content descriptions,
  selection count, and empty/error strings from §9.

---

## 11. Target files the implementer must write

Source files (all under `app/src/main/kotlin/com/revenge/music/`):

- `ui/screens/Screens.kt`
- `ui/screens/NavigationBuilder.kt` (routes in §3.2; other destinations may remain)
- `ui/screens/HomeScreen.kt`
- `ui/screens/BrowseScreen.kt`
- `ui/screens/ExploreScreen.kt`
- `ui/screens/ChartsScreen.kt`
- `ui/screens/NewReleaseScreen.kt`
- `ui/screens/MoodAndGenresScreen.kt`
- `ui/screens/YouTubeBrowseScreen.kt`
- `ui/screens/AlbumScreen.kt`
- `ui/screens/library/LibraryScreen.kt`
- `ui/screens/library/LibrarySongsScreen.kt`
- `ui/screens/library/LibraryAlbumsScreen.kt`
- `ui/screens/library/LibraryArtistsScreen.kt`
- `ui/screens/library/LibraryPlaylistsScreen.kt`
- `ui/screens/library/LibraryMixScreen.kt`
- `ui/screens/library/LibraryPodcastsScreen.kt`
- `ui/screens/library/LibrarySpotifyScreen.kt`
- `ui/screens/library/LibrarySpotifyViewModel.kt`
- `ui/screens/library/LocalSongScreen.kt`
- `ui/screens/search/SearchScreen.kt`
- `ui/screens/search/LocalSearchScreen.kt`
- `ui/screens/search/OnlineSearchScreen.kt`
- `ui/screens/search/OnlineSearchResult.kt`
- `ui/screens/search/suggestions/AppleMusicScraper.kt`
- `ui/screens/search/suggestions/SuggestionModels.kt`
- `ui/screens/search/suggestions/SuggestionRegionSheet.kt`
- `ui/screens/search/suggestions/SuggestionsViewModel.kt`
- `ui/screens/search/suggestions/TabNewsSuggestion.kt`
- `ui/screens/artist/ArtistScreen.kt`
- `ui/screens/artist/ArtistSongsScreen.kt`
- `ui/screens/artist/ArtistAlbumsScreen.kt`
- `ui/screens/artist/ArtistItemsScreen.kt`
- `ui/screens/playlist/OnlinePlaylistScreen.kt`
- `ui/screens/playlist/LocalPlaylistScreen.kt`
- `ui/screens/playlist/AutoPlaylistScreen.kt`
- `ui/screens/playlist/CachePlaylistScreen.kt`
- `ui/screens/playlist/TopPlaylistScreen.kt`
- `ui/screens/playlist/BottomPlaylistScreen.kt`
- `ui/screens/playlist/SpotifyPlaylistDetailScreen.kt`
- `ui/screens/playlist/SpotifyPlaylistDetailViewModel.kt`
- `ui/screens/playlist/AiModifyPlaylistDialog.kt`
- `viewmodels/LibraryViewModels.kt`
- `viewmodels/HomeViewModel.kt`
- `viewmodels/AlbumViewModel.kt`
- `viewmodels/ArtistViewModel.kt`
- `viewmodels/ArtistAlbumsViewModel.kt`
- `viewmodels/ArtistItemsViewModel.kt`
- `viewmodels/BrowseViewModel.kt`
- `viewmodels/ExploreViewModel.kt`
- `viewmodels/ChartsViewModel.kt`
- `viewmodels/NewReleaseViewModel.kt`
- `viewmodels/MoodAndGenresViewModel.kt`
- `viewmodels/YouTubeBrowseViewModel.kt`
- `viewmodels/LocalSearchViewModel.kt`
- `viewmodels/OnlineSearchViewModel.kt`
- `viewmodels/OnlineSearchSuggestionViewModel.kt`
- `viewmodels/LocalSongsViewModel.kt`
- `viewmodels/OnlinePlaylistViewModel.kt`
- `viewmodels/LocalPlaylistViewModel.kt`
- `viewmodels/TopPlaylistViewModel.kt`
- `viewmodels/BottomPlaylistViewModel.kt`
- `viewmodels/CachePlaylistViewModel.kt`
- `viewmodels/AutoPlaylistViewModel.kt`
- `viewmodels/PlaylistsViewModel.kt`
- `utils/PlaylistNavigation.kt`
- `utils/HeroCarouselSongs.kt`
- `utils/QuickPickSongs.kt`

Resources: every string/resource id referenced above (`library_album_empty`,
`library_artist_empty`, `library_playlist_empty`, `library_podcast_empty`, `playlist_is_empty`,
`no_results_found`, `spotify_empty_title`, `spotify_empty_desc`, `search_history`, `suggestions`,
`top_result`, `parsed_from_link`, `search_library`, `n_song`, `n_selected`, `scanning_device`,
`local_songs_scan_summary`, `local_songs_permission_body`, `scan_device`, sort/filter labels,
playlist/top labels, `shuffle_content_desc`, `shuffle_label`, and the drawables used by icons) must
exist with the meanings used here; translations may follow the baseline resource set.

Tests (all under `app/src/test/kotlin/com/revenge/music/`):

- `utils/PlaylistNavigationTest.kt` (§10.1)
- `utils/HeroCarouselSongsTest.kt` (§10.2)
- `utils/QuickPickSongsTest.kt` (§10.2)
- `viewmodels/HomeSectionOrderingTest.kt` (§10.3)
- `viewmodels/LibraryScreenStateTest.kt` and `viewmodels/LibraryViewModelTest.kt` (§10.4)
- `viewmodels/LocalSearchViewModelTest.kt` and `viewmodels/LocalSongsViewModelTest.kt` (§10.4)
- `viewmodels/SearchStateTest.kt` (suggestions, search result, charts, suggestions tab) (§10.5)
- `viewmodels/PlaylistViewModelTest.kt` (online/local/top/bottom/Spotify) (§10.6)
- `ui/SavedStateContractsTest.kt` (route/builders and SavedStateHandle keys) (§10.7)
- Compose screenshot/preview tests under `app/src/androidTest/kotlin/com/revenge/music/ui/`
  for §10.8 (Robolectric or screenshot harness; skip if instrumentation unavailable, but the
  previews themselves remain required).

---

## 12. Non-goals

- No new database schema, network client, playback engine or DI binding is defined here; this
  cluster consumes existing `:core`, `innertube`, Spotify and playback APIs.
- No implementation-detail requirements (file-internal helper names, composable tree shape,
  animation specs, exact padding/colours) are contractual except where stated in §9.
- Additional public API is allowed only if it preserves every signature, state default and
  invariant in §3–§8.
