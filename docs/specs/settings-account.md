# Clean-Room Specification — `settings-account` cluster

Status: M2 specification (behavioural contract only).
Cluster id: `settings-account`.
Primary Gradle module: `:app` (namespace `com.revenge.music`, application id `com.revenge.music`).
Companion contracts live in `:core` (`com.revenge.music.constants`, `com.revenge.music.ui.screens`)
and are reproduced here because they are required to compile the cluster.

This document describes observable behaviour, API shape, navigation, persistence keys and platform
declarations. It intentionally omits statement-level implementation and does not reproduce Compose
subtrees. Any implementation that satisfies these contracts and acceptance tests is acceptable.

---

## 1. Scope and package map

The cluster owns every screen surface for settings, account/login, listening statistics, the
"Wrapped"/recap experience, podcasts, Music Flow, the local taste generator, music recognition,
ambient mode, Listen Together, YouTube comments, and Spotify import/login.

Packages in scope:

| Package | Responsibility |
|---|---|
| `com.revenge.music.ui.screens` | `AccountScreen`, `LoginScreen`, `StatsScreen`, `ListeningSummaryScreen`, `ActivityHistory`, `DetailedListeningHistoryScreen`, `ListenTogetherScreen`, `CommentSheet`, `CommentTogetherScreen`, `SpotifyImportScreen`, `SpotifyLoginScreen`, `NavigationBuilder`, `Screens` |
| `com.revenge.music.ui.screens.settings` | Root settings search/screen and all settings sub-screens listed in §4 |
| `com.revenge.music.ui.screens.settings.integrations` | `ListenTogetherSettings` |
| `com.revenge.music.ui.screens.settings.cloud` | Navidrome / Jellyfin login and dashboard screens |
| `com.revenge.music.ui.screens.wrapped` | Recap UI, data model, view-model, PDF/PNG poster share |
| `com.revenge.music.ui.screens.podcast` | Podcast library and video player |
| `com.revenge.music.ui.screens.musicflow` | Music Flow pager UI |
| `com.revenge.music.ui.screens.generate` | Local taste engine UI |
| `com.revenge.music.ui.screens.recognition` | Recognition capture and history UI |
| `com.revenge.music.ui.screens.ambient` | Ambient full-screen mode and backgrounds |
| `com.revenge.music.viewmodels` | Cluster view-models (`Account`, `AccountSettings`, `Stats`, `ListeningSummary`, `DetailedListeningHistory`, `MusicFlow`, `Generate`, `BackupRestore`, `Podcasts`, `ListenTogether`) |
| `com.revenge.music.spotifyimport` | Spotify import models, repository, view-model |
| `com.revenge.music.listentogether` | Listen Together manager, protocol models, client facade |
| `com.revenge.music.constants` (in `:core`) | `OptionStats`, `StatPeriod`, preference keys |

Non-goals: player rendering, download/export, equalizer DSP, database schema, Innertube transport.
Those are referenced only through their public facades.

---

## 2. Cross-module contracts required to compile

### 2.1 Navigation graph

`NavGraphBuilder.navigationBuilder(navController, scrollBehavior, activity, snackbarHostState)` registers
all destinations below. Routes are plain strings; nothing in the cluster may rename them. Arguments are
`NavType.StringType` unless noted.

Root / main destinations used by this cluster:

| Route | Arguments | Destination composable |
|---|---|---|
| `home` | — | `HomeScreen` (out of cluster) |
| `podcasts` | — | `PodcastsScreen(navController)` |
| `search_input` | — | `SearchScreen` (out of cluster) |
| `listen_together` | — | `ListenTogetherScreen(navController, showTopBar = false)` |
| `listen_together_from_topbar` | — | `ListenTogetherScreen(navController, showTopBar = true)` |
| `listen_together/chat` | — | `CommentTogetherScreen(navController)` |
| `library` | — | `LibraryScreen` (out of cluster) |
| `generate` | — | `GenerateScreen(navController)` |
| `stats` | — | `StatsScreen(navController)` |
| `wrapped` | — | `WrappedScreen(navController)` |
| `account` | — | `AccountScreen(navController, scrollBehavior)` |
| `music_flow` | — | `MusicFlowScreen(navController)` |
| `ambient_mode` | — | `AmbientModeScreen(navController)` |
| `podcast_video` | — | `PodcastVideoScreen(navController)` with fade transitions (300 ms) |
| `login` | — | `LoginScreen(navController)` |
| `recognition` | — | `RecognitionScreen(navController)` |
| `recognition_history` | — | `RecognitionHistoryScreen(navController)` |
| `settings` | — | `SettingsScreen(navController, scrollBehavior)` |
| `settings/account?highlightKey={highlightKey}` | `highlightKey: String?` | `AccountSettingsScreen(navController, scrollBehavior, highlightKey)` |
| `settings/integrations?highlightKey={highlightKey}` | `highlightKey: String?` | `AccountSettingsScreen(...)`; when absent, `highlightKey` defaults to the literal `"Integrations"` |
| `settings/appearance?highlightKey={highlightKey}` | `highlightKey: String?` | `AppearanceSettings(navController, scrollBehavior, activity, snackbarHostState, highlightKey)` |
| `settings/appearance/theme` | — | `ThemeScreen(navController)` |
| `settings/appearance/haptics?highlightKey={highlightKey}` | `highlightKey: String?` | `HapticsSettings(..., activity, snackbarHostState, highlightKey)` |
| `settings/appearance/app_icon` | — | `AppIconSettingsScreen(navController, activity, snackbarHostState)` |
| `settings/appearance/font` | — | `FontSelectionScreen(navController, scrollBehavior)` |
| `settings/appearance/liquidglass` | — | `GlassEffectSettings(navController, scrollBehavior)` |
| `settings/player?highlightKey={highlightKey}` | `highlightKey: String?` | `PlayerSettings(navController, scrollBehavior, highlightKey)` |
| `settings/content?highlightKey={highlightKey}` | `highlightKey: String?` | `ContentSettings(navController, scrollBehavior, highlightKey)` |
| `settings/ai?highlightKey={highlightKey}` | `highlightKey: String?` | `AiSettings(navController, scrollBehavior, highlightKey)` |
| `settings/privacy?highlightKey={highlightKey}` | `highlightKey: String?` | `PrivacySettings(navController, scrollBehavior, highlightKey)` |
| `settings/storage?autoOpenExportPicker={autoOpenExportPicker}&highlightKey={highlightKey}` | `autoOpenExportPicker: BoolType = false`, `highlightKey: String?` | `StorageSettings(navController, scrollBehavior, autoOpenExportPicker, highlightKey)` |
| `settings/backup_restore?highlightKey={highlightKey}` | `highlightKey: String?` | `BackupAndRestore(navController, scrollBehavior, highlightKey)` |
| `settings/update?highlightKey={highlightKey}` | `highlightKey: String?` | `UpdateSettings(navController, scrollBehavior, highlightKey)` |
| `settings/about?highlightKey={highlightKey}` | `highlightKey: String?` | `AboutScreen(navController, scrollBehavior, highlightKey)` |
| `settings/music_flow` | — | `MusicFlowSettings(navController, scrollBehavior)` |
| `settings/listening_summary` | — | `ListeningSummaryScreen(navController)` |
| `detailed_listening_history/{startTimestamp}` | `startTimestamp: String` | `DetailedListeningHistoryScreen(navController)`; view-model reads the arg from `SavedStateHandle` |
| `settings/extensions` | — | `ExtensionSettingsScreen(navController, scrollBehavior)` |
| `settings/lastfm` | — | `LastFMSettingsScreen(navController)` |
| `settings/integrations/listen_together` | — | `ListenTogetherSettings(navController, scrollBehavior)` |
| `settings/spotify_import` | — | `SpotifyImportScreen(navController)` |
| `settings/spotify_login` | — | `SpotifyLoginScreen(navController, onCookiesCaptured)`; view-model is resolved against the `settings/spotify_import` back-stack entry when present, otherwise `hiltViewModel()` |
| `settings/navidrome_login` / `settings/navidrome_dashboard` | — | cloud screens resolved through `CloudEntryPoint.navidromeRepository()` |
| `settings/jellyfin_login` / `settings/jellyfin_dashboard` | — | cloud screens resolved through `CloudEntryPoint.jellyfinRepository()` |
| `ambient_settings` and `settings/ambient` | — | both map to `AmbientSettingsScreen(navController)` |
| `uptime` | — | `UptimeScreen(navController, scrollBehavior)` |
| `blocked_artists` | — | `BlockedArtistsScreen(navController)` |
| `disliked_songs` | — | `DislikedSongsScreen(navController)` |
| `settings/equalizer` | — | `AxionEqScreen(onBackClick)` (out of cluster) |
| `settings/changelog` / `settings/commits` / `update` | — | update module screens (out of cluster) |

Invariants:

- A request to `settings/spotify_login` must share the `SpotifyImportViewModel` instance created by
  the `settings/spotify_import` destination, so cookies captured in the login destination are applied
  to the same import session.
- `highlightKey` is the display title of a sub-setting, URI-encoded by the root settings search. The
  destination must scroll to and visually highlight the matching row (§3.1).
- `detailed_listening_history/{startTimestamp}` passes epoch milliseconds as a decimal string.

### 2.2 `Screens` model

```kotlin
@Immutable
sealed class Screens(
  @StringRes val titleId: Int,
  @DrawableRes val iconIdInactive: Int,
  @DrawableRes val iconIdActive: Int,
  val route: String,
)
```

Singletons and routes: `Home("home")`, `Podcast("podcasts")`, `Search("search_input")`,
`ListenTogether("listen_together")`, `Library("library")`, `Generate("generate")`.
`Screens.MainScreens == listOf(Home, Podcast, Search, ListenTogether, Library)`.
`titleId`/`iconIdInactive`/`iconIdActive` values are fixed resources (`R.string.*`, `R.drawable.*`).

### 2.3 Preference keys

All keys are `androidx.datastore.preferences.core.Preferences.Key<T>` declared in
`core/src/main/kotlin/com/revenge/music/constants/PreferenceKeys.kt`. The cluster must use exactly these
names and types; changing a name breaks existing user data.

| Key | Type | Default used by cluster |
|---|---|---|
| `DataSaverEnabledKey` | Boolean | `false` |
| `AppIconTypeKey` | String | `"default"` / `"static"` label persisted; see §10.2 |
| `SelectedThemeColorKey` | Int | theme seed |
| `DarkModeKey` | String | `DarkMode.AUTO` |
| `PureBlackKey` | Boolean | `false` |
| `PureBlackMiniPlayerKey` | Boolean | `false` |
| `IsMonochromeThemeKey` | Boolean | `false` |
| `RevengeWrappedBannerDismissedKey` | Boolean | `false` |
| `GridItemsSizeKey` | String | `GridItemSize.BIG` |
| `HideVideoSongsKey` | Boolean | `false` |
| `HideYoutubeShortsKey` | Boolean | `false` |
| `YtmSyncKey` | Boolean | `true` |
| `MaxImageCacheSizeKey` | Int | `512` (MB) |
| `MaxSongCacheSizeKey` | Int | `1024` (MB) |
| `ExportingSongIdsKey` | String | `""` |
| `PauseListenHistoryKey` | Boolean | `false` |
| `PauseSearchHistoryKey` | Boolean | `false` |
| `DisableScreenshotKey` | Boolean | `false` |
| `LastFMSessionKey` | String | `""` |
| `LastFMUsernameKey` | String | `""` |
| `EnableLastFMScrobblingKey` | Boolean | `false` |
| `LastFMUseNowPlaying` | Boolean | `false` |
| `LastFMUseSendLikes` | Boolean | `false` |
| `PreferExtensionAudioKey` | Boolean | `false` |
| `PreferredAudioAddonIdKey` | String | `""` |
| `LyricsAnimationStyleKey` | String | `LyricsAnimationStyle.revenge_1.name` |
| `LyricsTextSizeKey` | Float | `24f` |
| `LyricsLineSpacingKey` | Float | `1.3f` |
| `CanvasThumbnailAnimationKey` | Boolean | `true` |
| `VisitorDataKey` | String | `""` |
| `DataSyncIdKey` | String | `""` |
| `InnerTubeCookieKey` | String | `""` |
| `AccountNameKey` | String | `""` |
| `AccountEmailKey` | String | `""` |
| `AccountChannelHandleKey` | String | `""` |
| `UseLoginForBrowse` | Boolean | `true` |
| `SavedAccountsKey` | String | `"[]"` (JSON array of `AccountData`) |
| `ListenBrainzEnabledKey` | Boolean | `false` |
| `ListenBrainzTokenKey` | String | `""` |
| `AmbientArtScaleKey` | Float | `0.85f` |
| `AmbientShowTitleKey` | Boolean | `false` |
| `AmbientShowArtistKey` | Boolean | `false` |
| `AmbientShowLyricsKey` | Boolean | `true` |
| `AmbientShowArtKey` | Boolean | `true` |
| `AmbientFullScreenArtKey` | Boolean | `false` |
| `AmbientSpacingKey` | Float | `32f` |
| `SelectedFontKey` | String | `AppFont.PLUS_JAKARTA_SANS.value` |
| `CustomFontPathKey` | String | `""` |
| `BlockedArtistsKey` | Set\<String\> | `emptySet()` |
| `DislikedSongsKey` | Set\<String\> | `emptySet()` |
| `ListenTogetherServerUrlKey` | String | `ListenTogetherServers.defaultServerUrl` |
| `ListenTogetherUsernameKey` | String | `""` |
| `ListenTogetherAutoApprovalKey` | Boolean | `false` |
| `ListenTogetherSyncVolumeKey` | Boolean | `true` |
| `ListenTogetherSmartResyncKey` | Boolean | `true` |
| `ListenTogetherBlockedUsersKey` | String | `""` (persisted JSON) |
| `ListenTogetherInTopBarKey` | Boolean | `true` |
| `ListenTogetherSessionTokenKey` | String | `""` |
| `ListenTogetherRoomCodeKey` | String | `""` |
| `ListenTogetherUserIdKey` | String | `""` |
| `ListenTogetherIsHostKey` | Boolean | `false` |
| `ListenTogetherSessionTimestampKey` | Long | `0L` |
| `AppLanguageKey` | String | system default |
| `ContentCountryKey` / `ContentLanguageKey` | String | system/region defaults |
| `IpVersionKey` | String | default |
| `ProxyEnabledKey` | Boolean | `false` |
| `ProxyTypeKey` / `ProxyUrlKey` / `ProxyUsernameKey` / `ProxyPasswordKey` | String | `""` |
| `EnableLrcLibKey` / `EnableBetterLyricsKey` / `EnableKugouKey` / `EnablePaxsenixKey` / `EnableUnisonKey` / `EnableSimpMusicKey` / `EnableYouLyPlusKey` | Boolean | `true` where offered |
| `ForceOpusKey` | Boolean | `false` |
| `HideExplicitKey` | Boolean | `false` |
| `QuickPicksKey` | Boolean | `true` |
| `RandomizeHomeOrderKey` | Boolean | `false` |
| `ShowHomeCommunityKey`, `ShowHomeHeroCarouselKey`, `ShowHomeMoodAndGenresKey`, `ShowMonthlyListenersKey`, `ShowSpeedDialKey` | Boolean | `true`/`false` per UI description |
| `SponsorBlockEnabledKey` | Boolean | `false` |
| `SpotifySpDcKey` / `SpotifySpKeyKey` / `SpotifyAccountNameKey` / `SpotifyAccountAvatarUrlKey` / `SpotifyAccessTokenKey` | String | `""` |
| `SpotifyAccessTokenExpiresAtKey` | Long | `0L` |
| `SubscriptionFeedFilter` / feed keys | — | use existing declarations |

Enum/value contracts referenced by the cluster:

- `OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }` (`:core`, package `com.revenge.music.ui.screens`).
- `StatPeriod { WEEK_1, MONTH_1, MONTH_3, MONTH_6, YEAR_1, ALL }` with `toTimeMillis()`.
  `statToPeriod(selection: OptionStats, test: Int): Long` computes the UTC epoch-millis lower bound.
  For `CONTINUOUS`, an index ≥ `StatPeriod.entries.size` clamps to the last entry (`ALL`).
- `AppFont { SYSTEM, GOOGLE_SANS, SANS_FLEX, OUTFIT, PLUS_JAKARTA_SANS, CUSTOM }` with
  `val value: String` and `fromValue(value: String): AppFont`.
- `AppIconType { DEFAULT(0), STATIC(2) }` with `IconUtils.setIcon(context, iconType)`.
- `LyricsAnimationStyle { NONE, FADE, GLOW, SLIDE, KARAOKE, APPLE, APPLE_V2, revenge_1, LYRICS_V2, METRO_LYRICS }`.
- `GridItemSize` enum used by account/settings grids (`BIG`, other variants) — stored name.
- `DarkMode { AUTO, ON, OFF }`, `NavigationTab`, `LyricsPosition`, `PlayerTextAlignment`
  declared in `AppearanceSettings.kt` (§4.5).

### 2.4 Preference helpers (`com.revenge.music.utils`)

```kotlin
@Composable fun <T> rememberPreference(
  key: Preferences.Key<T>,
  defaultValue: T,
): MutableState<T>

@Composable inline fun <reified T : Enum<T>> rememberEnumPreference(
  key: Preferences.Key<String>,
  defaultValue: T,
): MutableState<T>
```

Behavioural contract:

- Reading is backed by `Context.dataStore.data`; a read exception is treated as absent and the
  default is returned.
- If the stored value's class differs from the non-null default's class and either value is a
  `Set`, the stored value wins; otherwise the default is returned. Values are never coerced.
- Writes are asynchronous: the returned state's setter updates the in-memory state immediately and
  launches a datastore edit. There is no synchronous completion signal.
- `component1()`/`component2()` support destructuring: value and setter.
- `rememberEnumPreference` stores `Enum.name` and parses with `toEnum(defaultValue = default)`;
  an unknown or non-string stored value yields the default.
- State survives recomposition but not process death; screens that need process-death survival use
  `rememberSaveable` for transient UI state.

### 2.5 Shared UI components

The implementer must provide/use these signatures (existing shared components; do not re-implement):

```kotlin
@Composable fun Material3SettingsGroup(
  title: String? = null,
  compact: Boolean = false,
  scrollState: ScrollState? = null,
  items: List<Material3SettingsItem>,
)

data class Material3SettingsItem(
  val icon: Painter? = null,
  val customIcon: (@Composable () -> Unit)? = null,
  val title: @Composable () -> Unit,
  val description: (@Composable () -> Unit)? = null,
  val trailingContent: (@Composable () -> Unit)? = null,
  val showBadge: Boolean = false,
  val isHighlighted: Boolean = false,
  val tintIcon: Boolean = true,
  val iconShape: Shape? = null,
  val enabled: Boolean = true,
  val onClick: (() -> Unit)? = null,
)
```

- Group renders an optional uppercase section title, then rows. Row shape depends on position
  (single/first/middle/last), and `scrollState` + `isHighlighted` trigger a scroll-to-row on first
  composition of a highlighted item.
- A row with `onClick == null` is a non-interactive information row. A disabled row ignores clicks
  and exposes a disabled semantic state.

Additional shared contracts consumed by the cluster: `ChipsRow`, `ChoiceChipsRow`, `NavigationTitle`,
`DefaultDialog`, `TextFieldDialog`, `EmptyPlaceholder`, `CircularWavyProgressIndicator`,
`LocalMenuState`, `LocalPlayerConnection`, `LocalPlayerAwareWindowInsets`, `LocalDatabase`,
`LocalSyncUtils`, `LocalDownloadUtil`, `LocalListenTogetherManager`, `backToMain` navigation helper.
Their public signatures are assumed fixed by the rest of the app.

### 2.6 Facades used by the cluster

- `YouTube` (Innertube facade): `library(browseId)`, `accountInfo()`, `artist(id)`, `album(id)`,
  `comments(videoId)`, `commentContinuation(token)`, `home(...)`, `cookie/dataSyncId/visitorData`
  mutable properties, `useLoginForBrowse`, `proxy`. Results are `Result<T>`.
- `MusicDatabase`: range queries consumed by stats/wrapped/listening-summary view-models:
  `getTotalPlayTimeInRange(from,to)`, `getUniqueSongCountInRange`, `getUniqueArtistCountInRange`,
  `getUniqueAlbumCountInRange`, `mostPlayedSongs`, `mostPlayedSongsStats`, `mostPlayedArtists`,
  `mostPlayedAlbums`, `firstEvent()`, `getPlayTimeForDay`, `getSongsPlayTimeForDay`,
  `getArtistPlayTimeForDay`, `getAlbumPlayTimeForDay`, `eventsForPeriod`, `playlists`, plus
  `query { ... }` and `insert(...)`. All DB reads are `Flow`s.
- `PlayerConnection`: `isEffectivelyPlaying`, `mediaMetadata`, `currentLyrics`, `player`,
  `playQueue`, `togglePlayPause`, `positionAndDuration()`, `seekTo`, `play`, `pause`.
- `ListenTogetherManager`: state flows `connectionState`, `roomState`, `role`, `userId`,
  `pendingJoinRequests`, `bufferingUsers`, `logs`, `events`, `blockedUsernames`, `pendingSuggestions`,
  `rtt`, `chatMessages`; derived `isInRoom`, `isHost`, `hasPersistedSession`, `allowParticipantControl`,
  `canControlMusic`, `isGuestPlaybackRestricted`, `guestPlaybackRestricted`; operations
  `initialize/connect/disconnect/createRoom/joinRoom/leaveRoom/approveJoin/rejectJoin/kickUser/
  blockUser/unblockUser/transferHost/updateRoomSettings/suggestTrack/approveSuggestion/
  rejectSuggestion/forceReconnect/sendChatMessage/clearLogs/requestSync`.
- `MusicRecognitionService`: `recognitionStatus: StateFlow<RecognitionStatus>`, `reset()`, plus
  start/stop/cancel entry points used by `RecognitionScreen`.
- `SpotifyImportRepository`: `restoreSession()`, `connectWithCookies(spDc, spKey)`, `logout()`,
  `loadSources()`, `addPlaylistByUrl(url)`, `importSources(sources, onProgress)`,
  `syncPlaylistFast(localPlaylistId)`.
- `ListenTogetherServers`: `serversFlow: StateFlow<List<ListenTogetherServer>>`,
  `defaultServerUrl: String`. `ListenTogetherServer` exposes `url`, display name, and region metadata.

### 2.7 Enums / models declared inside the cluster

- `AccountContentType { PLAYLISTS, ALBUMS, ARTISTS }`.
- `WrappedTimeRange(val label: String) { THIS_YEAR("This Year"), LAST_YEAR("Last Year"), ALL_TIME("All Time") }`.
- `WrappedData` (`@Immutable`): fields in order
  `timeRange: WrappedTimeRange`, `yearLabel: String`, `totalPlayTimeMs: Long`,
  `totalMinutes: Long = totalPlayTimeMs / 60000L`, `uniqueSongsCount: Int`,
  `uniqueArtistsCount: Int`, `uniqueAlbumsCount: Int`, `topSong: Song?`,
  `topSongStats: SongWithStats?`, `top5Songs: List<Song>`, `top5SongsStats: List<SongWithStats>`,
  `topArtist: Artist?`, `top5Artists: List<Artist>`, `topAlbum: Album?`,
  `top5Albums: List<Album>`, `dominantVibe: String = "Diverse Explorer"`.
- `WrappedUiState`: `Loading` (object), `InsufficientData(songsPlayed: Int, minSongsRequired: Int = 3)`,
  `Ready(data: WrappedData)`.
- `WrappedPosterTheme` enum with display name and two parallel color representations (Compose `Color`
  and packed `Int`): `VIOLET_MINT`, `OBSIDIAN_GREEN`, `FIRE_RED`, `COBALT_BLUE`, `SUNSET_ORANGE`.
- `SpotifySlideTheme` (`@Immutable`) colors for slides.
- `SpotifyImportSourceType { PLAYLIST, LIKED_SONGS }`.
- `SpotifyImportSourceUi(id, title, subtitle, thumbnailUrl, trackCount, type)`.
- `SpotifyImportProgressUi(sourceTitle, completedSources, totalSources, matchedTracks, totalTracks, percent)`.
- `SpotifyImportSourceSummaryUi(title, totalTracks, importedTracks, failedTracks)`.
- `SpotifyImportSummaryUi(sources)` with derived `sourceCount`, `totalTracks`, `importedTracks`,
  `failedTracks`.
- `SpotifyImportUiState(isAuthenticated=false, accountName="", accountAvatarUrl=null, isLoading=false,
  sources=emptyList(), selectedSourceIds=emptySet(), progress=null, summary=null, errorMessage=null)`
  with derived `hasSources`, `canImport = selectedSourceIds.isNotEmpty() && progress == null && !isLoading`.
- `SpotifyImportSession(isAuthenticated=false, accountName="", accountAvatarUrl=null)`.
- `SpotifyImportSource` sealed interface: `id`, `title`, `subtitle`, `thumbnailUrl: String?`,
  `trackCount: Int?`, `localPlaylistId`, `type`; implementations `Playlist(playlist)` and
  `LikedSongs(title, trackCount)`.
- `ServiceStatus(name, url: () -> String, displayUrl: () -> String = url, status: Status = CHECKING,
  latencyMs: Long? = null, offlineMessage: () -> String? = { null }, fallbackUrls: () -> List<String> = { emptyList() })`
  with nested `enum Status { ONLINE, OFFLINE, CHECKING }`.
- `CsvImportState(previewRows, artistColumnIndex=0, titleColumnIndex=1, urlColumnIndex=-1, hasHeader=true)`,
  `ConvertedSongLog(title, artists)`.
- `VibeSummary(dominantVibe, dominantVibePlayTime, previousVibePlayTime, percentageChange)`.
- `DayUsageData(dayName, timestamp, totalMs, songsMs, artistsMs, albumsMs)`.
- `GenerateUiState`: `Idle`, `EmptyHistory`, `AllExcluded`, `Generating(message)`, `Success(playlistId)`,
  `Error(message)`.
- `RingtoneUiState(showTrimmer, showProgress, targetSongId, targetSongTitle, targetSongArtist,
  targetSongDuration, progress, statusMessage, isComplete, isSuccess, ringtoneUri: Uri?)`.
- `AppIconOption(type: AppIconType, title: String, description: String, iconRes: Int)`.
- `SearchableSetting(title: String, description: String?, category: String, route: String)`.

---

## 3. Shared screen conventions

### 3.1 Highlight and search behaviour (settings family)

- Every settings destination that receives `highlightKey: String?` must, on first composition, scroll
  the containing scrollable to the setting whose display title equals `highlightKey` and mark that
  row `isHighlighted = true`. Passing `null` or a non-matching key must leave scroll at top and
  highlight nothing. Highlighting must not change the row's enabled state.
- The root settings search filters:
  - top-level settings by case-insensitive substring match on localized title or description;
  - when the query is non-empty, also `getAllSearchableSettings()` filtered by substring on title or
    description;
  - navigation to a search result appends `?highlightKey=<Uri.encode(title)>` (or `&highlightKey=…`
    when the route already has a query).
- An empty query shows all top-level groups. A non-empty query with no matches shows the localized
  “no settings found” message including the query text.

### 3.2 Scaffold / bar / inset conventions

- Every settings sub-screen uses `Scaffold` + `TopAppBar` with back navigation. Back button:
  `onClick = navController::navigateUp`, `onLongClick = navController::backToMain` unless stated.
- Content is padded with `LocalPlayerAwareWindowInsets` so the mini-player does not occlude it.
- Screens that manage full-screen orientation or system bars must restore the original orientation,
  keep-screen-on flag and bar visibility in `onDispose`.

### 3.3 Accessibility semantics

- Every icon-only button has either a non-null `contentDescription` or is explicitly decorative
  (`null`) when adjacent text conveys the meaning. User-action icons (back, close, delete, share,
  search, clear) use localized strings or stable English labels (`"Back"`, `"Listening recap"`).
- Toggle rows expose a Switch with its own semantics; the whole row may also be clickable.
- Charts expose a textual summary (e.g. total/week labels) so values are reachable without color.
- The Music Flow pager exposes custom accessibility actions “Previous” and “Next” (§7.3).
- Decorative images (album art backgrounds, banners) use `contentDescription = null`.

### 3.4 Loading / empty / error / offline

- Null-backed lists render shimmer placeholders (fixed count, usually 8) until first emission.
- Network failures must not crash: they surface as empty content, a retry affordance, a snackbar, or
  a dialog as specified per screen.
- Offline: features that require the network retain cached/local content and disable network-only
  actions; Music Flow has an explicit offline-feed flag.

---

## 4. Settings screens

### 4.1 `SettingsScreen`

```kotlin
@Composable fun SettingsScreen(
  navController: NavController,
  scrollBehavior: TopAppBarScrollBehavior,
  highlightKey: String? = null,
)
```

Purpose: root settings hub and full-text setting search.

Inputs/state:

- `searchQuery: String` in `rememberSaveable` (survives process recreation).
- `isUpdateAvailable` = update-available flag AND auto-update-check setting enabled.
- Localized titles/descriptions for every top-level entry.

User actions and effects:

- Typing filters the catalogue live (case-insensitive title/description).
- Clearing the query restores the full catalogue.
- Tapping an entry navigates to its route exactly as listed in §2.1.
- “Supported links” (Android 12+) opens the system “open by default” settings for the package; if
  no activity handles it, shows a toast. It is hidden below API 31.
- “Update” entry shows the update icon and an error-colored “update available” description when an
  update is available.
- Search results navigate with `highlightKey`.

Groups and entries (order within group is fixed): Account (account, AI tools), Listening (listening
summary, recap/wrapped, Listen Together), Appearance, Playback (player, Music Flow), Library
(content, privacy, storage, backup & restore), System (update, supported links, about).

Accessibility: search field exposes placeholder and leading search icon descriptions; trailing clear
button described; no-match text is a single readable text node.

### 4.2 Account / integration screens

#### 4.2.1 `AccountSettingsScreen`

```kotlin
@Composable fun AccountSettingsScreen(
  navController: NavController,
  scrollBehavior: TopAppBarScrollBehavior,
  highlightKey: String? = null,
)
```

Purpose: account switching, YouTube login/logout, browse-login toggle, YouTube sync toggle, personal
cloud (Navidrome/Jellyfin) entry points, Spotify import entry, Last.fm entry, ListenBrainz token.

Inputs/state (preferences): `AccountNameKey`, `AccountEmailKey`, `AccountChannelHandleKey`,
`InnerTubeCookieKey`, `VisitorDataKey`, `DataSyncIdKey`, `SavedAccountsKey`, `UseLoginForBrowse`,
`YtmSyncKey`, `ListenBrainzEnabledKey`, `ListenBrainzTokenKey`.

Saved-account model: `SavedAccountsKey` is a JSON array of `AccountData(name, email, channelHandle,
cookie, visitorData, dataSyncId, avatarUrl)`. Malformed JSON is treated as an empty list.

Actions/effects:

- “Add account” / “Switch account” navigates to `login`.
- Selecting a saved account writes that account's cookie/visitor/sync-id/name/email/handle to the
  corresponding keys, updates `YouTube` globals, and (per implementation) restarts or refreshes.
- Logout opens a dialog with two destructive choices:
  - “Logout, keep data” → `AccountSettingsViewModel.logoutKeepData(context, onCookieChange)`:
    clears the persisted account identity only, then reports `""`.
  - “Logout, clear data” → `logoutAndClearSyncedContent(...)`: clears all synced content via
    `SyncUtils.clearAllSyncedContent()`, forgets the account, then reports `""`.
  - Cancel dismisses without changes.
- `AccountSettingsViewModel` public methods:
  `logoutAndClearSyncedContent(context, onCookieChange)`,
  `logoutKeepData(context, onCookieChange)`,
  `saveTokenAndRestart(context, cookie, visitorData, dataSyncId, accountName, accountEmail, accountChannelHandle)`.
  `saveTokenAndRestart` persists all six values and then relaunches the app's launcher intent with
  `NEW_TASK | CLEAR_TASK`.
- Browse-login toggle: writes `UseLoginForBrowse` and sets `YouTube.useLoginForBrowse`.
- YouTube sync toggle writes `YtmSyncKey`.
- Navidrome/Jellyfin rows navigate to dashboard when configured, else login.
- Spotify Import row → `settings/spotify_import`; Last.fm row → `settings/lastfm`.
- ListenBrainz enable toggle and token editor (dialog) write the two ListenBrainz keys; an enabled
  toggle with a blank token must not crash.

States: an empty cookie is the “not logged in” state. When logged in, the row shows name/email/handle.
Loading is not modelled; the screen is preference-backed and updates reactively.

#### 4.2.2 `PrivacySettings`, `StorageSettings`, `BackupAndRestore`, `UpdateSettings`, `AboutScreen`,
`UptimeScreen`, `AppIconSettingsScreen`, `FontSelectionScreen`, `ThemeScreen`, `HapticsSettings`,
`GlassEffectSettings`, `ExtensionSettingsScreen`, `LastFMSettingsScreen`, `BlockedArtistsScreen`,
`DislikedSongsScreen`, `AmbientSettingsScreen`, cloud screens

These are settings-family screens in scope. Contracts:

- Signatures exactly as in §2.1/§4.1 listing.
- `PrivacySettings`: toggles `PauseListenHistoryKey`, `PauseSearchHistoryKey`, `DisableScreenshotKey`;
  “clear listen history” opens a confirmation dialog and, on confirm, clears listening history via
  the database, then dismisses.
- `StorageSettings`: reads image/song cache usage; sliders write `MaxImageCacheSizeKey` /
  `MaxSongCacheSizeKey` in MB; offers cache clearing; export action uses
  `ExportingSongIdsKey`; when `autoOpenExportPicker` is true the export document picker opens on
  first composition; `LocalPlayerConnection` absence (service not bound) must not crash.
- `BackupAndRestore`: `BackupSubScreen { MAIN, IMPORT }`. `BackupRestoreViewModel` public suspend API:
  `backup(context, uri)`, `restore(context, uri)`, `previewCsvFile(context, uri): CsvImportState`,
  `importPlaylistFromCsv(...)` (two overloads), `loadM3UOnline(...)`. Backup zips the datastore
  settings file and the internal DB; restore extracts them and recreates the DB, then forces an app
  restart (process exit). CSV import flow: pick file → preview/map columns (artist/title/url, header)
  → import with progress/logs → result. Malformed CSV/zip must surface a failure toast, not crash.
- `UpdateSettings`: reads/writes auto-update, update-notification and beta settings through
  `com.revenge.music.updates.updater` helpers; shows downloaded-APK count; clears old APKs on entry;
  exposes release notes and an info dialog.
- `AboutScreen`: optional `onBack`; if `BuildConfig.UPDATE_REPO` is non-blank, loads contributors from
  the GitHub API off the main thread; any network/parse failure leaves contributors empty. Shows app
  version, licenses/links via `LocalUriHandler`, and a logo easter-egg animation.
- `UptimeScreen`: builds `ServiceStatus` entries for music, canvas, lyrics, metadata services;
  concurrently probes each URL (including fallbacks) with timeouts; each card transitions
  `CHECKING → ONLINE (latencyMs) | OFFLINE`; latency shown as `N ms`. Configurable thresholds per
  service. Network failure yields `OFFLINE`; no crash.
- `AppIconSettingsScreen`: rows for `AppIconType.DEFAULT`/`STATIC`; selecting a different icon writes
  `AppIconTypeKey` and calls `IconUtils.setIcon(activity, type)`; shows a snackbar “Icon updated,
  restart to apply” with a “Restart” action that relaunches the app. Selecting the current icon is a
  no-op.
- `FontSelectionScreen`: list of `AppFont` values plus custom font. Selecting writes `SelectedFontKey`.
  Picking a custom font uses `ActivityResultContracts.GetContent`, copies the stream to
  `filesDir/custom_font.ttf`, writes `CustomFontPathKey`; an unreadable URI shows an error and leaves
  the previous font.
- `ThemeScreen(navController, highlightKey)` plus `ThemeModeCard`, `HsvColorPicker`,
  `CustomColorSlider`; writes `DarkModeKey` (enum name), `SelectedThemeColorKey`, `PureBlackKey`,
  `PureBlackMiniPlayerKey`, `IsMonochromeThemeKey`. `highlightKey` scroll/highlight semantics apply.
- `HapticsSettings`: toggles four haptic categories and `HapticIntensityKey`; may preview a haptic
  when toggled.
- `GlassEffectSettings`: writes the `LiquidGlass*` keys and `UseFloatingNavBarKey`; sliders clamp to
  documented ranges; changes take effect on next recomposition.
- `ExtensionSettingsScreen`: `ExtensionSettingsViewModel(extensionManager)` exposes
  `addons: StateFlow<List<AddonEndpoint>>` and `addAddon(name, url)`, `toggleAddon(id, enabled)`,
  `removeAddon(id)`. Add validates a non-blank name/url; invalid input does not add an entry. Writes
  `PreferExtensionAudioKey` / `PreferredAudioAddonIdKey`.
- `LastFMSettingsScreen`: connect/disconnect Last.fm; writes `LastFMSessionKey`, `LastFMUsernameKey`,
  `EnableLastFMScrobblingKey`, `LastFMUseNowPlaying`, `LastFMUseSendLikes`. Uses `BuildConfig.LASTFM_API_KEY`
  / `LASTFM_SECRET`; blank credentials disable connect rather than failing.
- `BlockedArtistsScreen` / `DislikedSongsScreen`: read the string-set preferences
  `BlockedArtistsKey` / `DislikedSongsKey`, list entries, allow removal (with confirmation for
  “unblock all”/“clear”), and reflect changes immediately.
- `AmbientSettingsContent(modifier)` and `AmbientSettingsScreen(navController)`: settings for ambient
  mode; writes ambient keys and lyrics text/animation keys. `AmbientSettingsContent` has no scaffold
  and is reusable inside a sheet.
- Cloud screens: `NavidromeLoginScreen(navController, repository)`,
  `NavidromeDashboardScreen(navController, repository)`, `JellyfinLoginScreen(...)`,
  `JellyfinDashboardScreen(...)`. Login validates non-blank server URL/credentials and reports
  failure without navigating; dashboard displays library info and a disconnect that clears the
  repository session and returns to login.

### 4.3 `getAllSearchableSettings`

```kotlin
data class SearchableSetting(val title: String, val description: String?, val category: String, val route: String)
@Composable fun getAllSearchableSettings(): List<SearchableSetting>
```

Returns the flat catalogue of searchable sub-settings. `title`, `description`, `category` are
localized resource strings; `route` is one of the settings routes in §2.1. The list must include at
least the entries present today grouped by About, Account, AI tools, Appearance, Content, Playback,
Privacy/Storage/Backup, integrations. It is a pure read; no side effects.

### 4.4 `MusicFlowSettings`, `RingtoneViewModel`, `ListenBrainzManager`

- `MusicFlowSettings(navController, scrollBehavior)` writes Music Flow preferences (animated
  background, data-saver, cellular prefetch, auto-next, etc.) and exposes the same highlight/search
  contract as other settings.
- `RingtoneViewModel: ViewModel` (not Hilt) — public state `uiState: StateFlow<RingtoneUiState>`;
  operations `showTrimmer(songId, title, artist, durationSeconds)`, `hideTrimmer()`,
  `getStreamUrl(context, songId)`, `setAsRingtone(context, startMs, endMs)`, `dismissProgress()`,
  `openRingtoneSettings(context)`, `hasSettingsPermission(context)`, `requestSettingsPermission(context)`.
  Progress callbacks update `progress` and `statusMessage`; completion sets `isComplete`, `isSuccess`,
  optional `ringtoneUri`, and never leaves `showProgress` true after dismissal.
- `object ListenBrainzManager` — outbound scrobbling helper; its public functions accept
  `MediaMetadata`/session data and must never throw to the caller (failures logged).

---

## 5. Account, login, statistics

### 5.1 `AccountScreen`

```kotlin
@Composable fun AccountScreen(
  navController: NavController,
  scrollBehavior: TopAppBarScrollBehavior,
  viewModel: AccountViewModel = hiltViewModel(),
)
```

Purpose: the signed-in account's YouTube Music library grid.

State inputs: `AccountViewModel.playlists/albums/artists: MutableStateFlow<List<Item>?>`
(`null` = not yet loaded), `selectedContentType: MutableStateFlow<AccountContentType>`,
plus preference `GridItemsSizeKey`.

`AccountViewModel` public API:

```kotlin
enum class AccountContentType { PLAYLISTS, ALBUMS, ARTISTS }
class AccountViewModel(context) : ViewModel() {
  val playlists: MutableStateFlow<List<PlaylistItem>?>
  val albums: MutableStateFlow<List<AlbumItem>?>
  val artists: MutableStateFlow<List<ArtistItem>?>
  val selectedContentType: MutableStateFlow<AccountContentType>
  fun setSelectedContentType(contentType: AccountContentType)
}
```

Behaviour:

- On creation, loads liked playlists (`FEmusic_liked_playlists`), liked albums
  (`FEmusic_liked_albums`) and library artists (`FEmusic_library_corpus_artists`) sequentially.
  Playlists filter out `id == "SE"` and YouTube Shorts when `HideYoutubeShortsKey` is true; artists
  are progressively resized to 1200×1200. A failure reports the exception and leaves the list `null`
  (or partially loaded), which is the empty/loading state.
- When `HideYoutubeShortsKey` changes after playlists first load, playlists reload.
- UI: adaptive grid; chips for Playlists/Albums/Artists; each item navigates to
  `online_playlist/{id}`, `album/{id}`, `artist/{id}`; long-press opens the corresponding contextual
  menu with haptic feedback.
- While a list is `null`, render shimmer placeholders (8). Empty list renders no items.
- Accessibility: grid item semantics come from `YouTubeGridItem`; long-press action is available via
  the contextual menu.

### 5.2 `LoginScreen`

```kotlin
@Composable fun LoginScreen(navController: NavController)
```

Purpose: WebView-based Google/YouTube Music login that captures the InnerTube cookie, visitor data and
data-sync id, then returns.

State: preferences `VisitorDataKey`, `DataSyncIdKey`, `InnerTubeCookieKey`, `AccountNameKey`,
`AccountEmailKey`, `AccountChannelHandleKey`, `SavedAccountsKey`; local `hasCompletedLogin` latch.

Behaviour:

- Loads `https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com` in a
  JavaScript-enabled WebView with third-party cookies and a WebView-masked user agent. Popups
  (`onCreateWindow`) are allowed.
- URL policy: only HTTP(S) URLs whose host matches the allowlist
  `(^|\.)(google|youtube|gstatic|googleusercontent|googleapis|googlevideo)\.[a-z.]+$` or `g.co` load
  inside the WebView. Any other scheme or host is dispatched to an external `ACTION_VIEW` intent and
  the WebView navigation is cancelled. An unparsable URL may not crash.
- On page finish for YouTube pages, visitor data / data-sync id are extracted from `yt.config_` and
  written through both JS evaluation and the `Android` JS interface (`onRetrieveVisitorData`,
  `onRetrieveDataSyncId`; the latter strips everything after `||`).
- Successful-auth detection: the music.youtube.com cookie (or current URL cookie) must contain
  `SAPISID`, `__Secure-3PSID`, or `LOGIN_INFO` while on YouTube Music (or on a YouTube page holding
  an auth token). The first detection latches and never re-fires.
- After latching, the cookie is stored, `YouTube` globals updated, then `YouTube.accountInfo()` is
  awaited. Success writes name/email/handle, upserts the account into `SavedAccountsKey` (dedupe by
  `name`), clears WebView history/cache/form data, shows “Logged in as …”, and navigates up (or to
  main if up fails). Failure still upserts a fallback account (dedupe by cookie) and navigates up
  without showing a crash.
- Back handling: if the WebView can go back, system back navigates WebView history; otherwise default.
- Accessibility: top app bar back button; the WebView is the only content.

### 5.3 `StatsScreen` and `ActivityHistoryBottomSheet`

```kotlin
@Composable fun StatsScreen(
  navController: NavController,
  viewModel: StatsViewModel = hiltViewModel(),
)
@Composable fun ActivityHistoryBottomSheet(
  onDismiss: () -> Unit,
  totalPlayTimeMs: Long,
  allTimePlayTimeMs: Long,
  uniqueSongs: Int,
  uniqueArtists: Int,
  uniqueAlbums: Int,
  periodLabel: String,
)
```

`StatsViewModel` public API (state flows):

```kotlin
class StatsViewModel(context, database) : ViewModel() {
  val selectedOption: MutableStateFlow<OptionStats>      // default CONTINUOUS
  val indexChips: MutableStateFlow<Int>                  // default 0
  val totalPlayTime, allTimePlayTime: StateFlow<Long>
  val uniqueSongsCount, uniqueArtistsCount, uniqueAlbumsCount: StateFlow<Int>
  val allTimeSongsCount, allTimeArtistsCount, allTimeAlbumsCount: StateFlow<Int>
  val mostPlayedSongsStats: StateFlow<List<SongWithStats>>
  val mostPlayedSongs: StateFlow<List<Song>>
  val mostPlayedArtists: StateFlow<List<Artist>>         // only YouTube artists
  val mostPlayedAlbums: StateFlow<List<Album>>
  val firstEvent: StateFlow<Event?>
}
```

Behaviour:

- Selecting a range option resets the selected chip index to `0`; the chip rows are:
  - `CONTINUOUS`: fixed `StatPeriod` labels (1 week, 1/3/6 months, 1 year, all);
  - `WEEKS`/`MONTHS`/`YEARS`: date-bucket labels generated from the first event timestamp up to now
    (`dd MMM` ranges, month labels, or years). If there is no first event, no date chips exist.
- Period boundaries use `statToPeriod(selection, index)`; for the newest bucket the upper bound is
  “now”; otherwise the upper bound is the previous bucket's lower bound. All boundaries are UTC.
- Showing the “hide video songs” preference removes video entries from songs/song-stats.
- Artists are filtered to artists flagged as YouTube artists.
- Artwork/metadata backfill: when an artist lacks artwork or has stale data (> 10 days), fetch the
  artist page and update the DB. When an album has `songCount == 0`, fetch the album; `NOT_FOUND`
  deletes the album. Failures are reported, never thrown to the UI.
- Screen content: chips → dismissible Wrapped banner (unless dismissed) → “most played songs” row →
  artists row → albums row; song click toggles playback if already current, else plays a
  `YouTubeQueue(WatchEndpoint(song.id))`; long-press opens the song/artist/album menu with haptic.
- A shuffle FAB (visible when songs exist) plays a shuffled `ListQueue` of most-played songs.
- Top bar actions: “Listening recap” → `wrapped`; history icon opens `ActivityHistoryBottomSheet`
  with the current period label.
- `ActivityHistoryBottomSheet`: modal bottom sheet with header (title, period label, close), a hero
  total-time card formatted from `totalPlayTimeMs`, all-time total, and unique song/artist/album
  counts. It is display-only. Dismiss on drag/close/backdrop. `formatDuration(ms)` renders
  hours/minutes (and days when applicable).

### 5.4 `ListeningSummaryScreen`

```kotlin
@Composable fun ListeningSummaryScreen(
  navController: NavController,
  viewModel: ListeningSummaryViewModel = hiltViewModel(),
)
data class VibeSummary(...); data class DayUsageData(...)
```

`ListeningSummaryViewModel` public API:

```kotlin
class ListeningSummaryViewModel(database) : ViewModel() {
  val weekOffset: MutableStateFlow<Int>                 // 0 = current week
  val todayPlayTimeMs: Flow<Long>
  val weekDailyData: StateFlow<List<DayUsageData>>      // Monday..Sunday of selected week
  val weekTotalMs: StateFlow<Long>
  val vibeSummary: StateFlow<VibeSummary>
  fun goToPreviousWeek(); fun goToNextWeek(); fun isCurrentWeek(): Boolean
  fun setWeekFromEpoch(epochMilli: Long)                // clamps to current or earlier
}
```

Behaviour:

- Weeks start Monday 00:00 UTC; each `DayUsageData` carries total/songs/artists/albums ms for that
  day. Advancing to future weeks is clamped at offset 0.
- Vibe classification is a deterministic keyword mapping over `(title + " " + artists).lowercase()`.
  Categories and emoji labels are fixed: Phonk, Chill, Sad, Romantic, Energetic, Rock, Pop, Jazz,
  Hip-Hop, Classical, Acoustic, Indie, and fallback “✨ Diverse”. The dominant vibe excludes the
  fallback unless it is the only category. `percentageChange` is 100 when the previous week had zero
  time for that vibe, else the rounded percent change.
- `setWeekFromEpoch` computes whole weeks between the selected Monday and the current Monday and
  clamps negatives to 0.
- UI: week label (“This week”, or “Nw ago”), previous/next week controls (next disabled at offset 0),
  total time, an expandable per-day breakdown (`showDailyBreakdown`, default expanded), a bar chart,
  and a vibe summary card. A date picker (Material3) selects a week. Values format as `Xh Ym` / `Ym`.
- Empty data: zero totals and an empty day list render zeros, not a crash.

### 5.5 `DetailedListeningHistoryScreen`

```kotlin
@Composable fun DetailedListeningHistoryScreen(
  navController: NavController,
  viewModel: DetailedListeningHistoryViewModel = hiltViewModel(),
)
```

`DetailedListeningHistoryViewModel(database, savedStateHandle)`:

- `startTimestamp: Long` from `savedStateHandle["startTimestamp"]` parsed as decimal; default `0L`.
- Window is `[startTimestamp, startTimestamp + 24h)`.
- Flows: `totalPlayTime: StateFlow<Long>`, `uniqueSongsCount/uniqueArtistsCount/uniqueAlbumsCount:
  StateFlow<Int>`, `mostPlayedSongs: StateFlow<List<SongWithStats>>`,
  `mostPlayedArtists: StateFlow<List<Artist>>` (YouTube artists only),
  `mostPlayedAlbums: StateFlow<List<Album>>`. Lists limit to 20.

UI: top bar title = start day formatted `EEEE, MMM d, yyyy` in UTC; a card shows precise
`Hh Mm Ss` total plus the three unique counts; sections list top songs/artists/albums. Any empty list
shows just its section heading with no items. Back navigates up.

---

## 6. Wrapped / recap

### 6.1 `WrappedScreen`

```kotlin
@Composable fun WrappedScreen(
  navController: NavController,
  viewModel: RevengeWrappedViewModel = hiltViewModel(),
)
```

`RevengeWrappedViewModel(context, database)` public API:

```kotlin
class RevengeWrappedViewModel(context, database) : ViewModel() {
  val selectedRange: StateFlow<WrappedTimeRange>
  val uiState: StateFlow<WrappedUiState>
  val isPlaylistSaving: StateFlow<Boolean>
  val playlistSaved: StateFlow<Boolean>
  fun setTimeRange(range: WrappedTimeRange)
  fun loadWrappedData(range: WrappedTimeRange)
  fun createWrappedPlaylist(data: WrappedData, onComplete: (Boolean) -> Unit)
}
```

Data rules:

- Loads on init with `THIS_YEAR`. Boundaries are UTC: calendar year start → now; previous full year;
  all-time (0 → now).
- Minimum data: fewer than 3 unique songs in the selected range is `InsufficientData`. If the range
  is not `ALL_TIME` and all-time unique songs ≥ 3, the view-model automatically switches to
  `ALL_TIME` and reloads; callers observe `selectedRange` as `ALL_TIME`.
- `Ready` carries `WrappedData` with top song/artist/album, top-5 lists, counts and totals.
  `dominantVibe` is a deterministic rule over minutes/artists/songs/top-artist share:
  “Certified Audiophile”, “Musical Nomad”, “Infinite Playlist Curator”, “Devoted Superfan”, else
  “Sonic Explorer”.
- `createWrappedPlaylist` is idempotency-guarded (`isPlaylistSaving`): inserts a local, editable,
  bookmarked playlist named `"Your <yearLabel> recap"`, inserts up to 50 songs (all-time top 50 when
  the range is ALL_TIME, otherwise the current year's top 50, falling back to `top5Songs`), sets
  `playlistSaved = true`, and invokes `onComplete(true)`. Any failure resets saving and invokes
  `onComplete(false)`.

UI states:

- `Loading`: dark full-screen spinner with text “Synthesizing your year in music…”.
- `InsufficientData`: message with songs played and minimum required; actions “Try all time” and
  back.
- `Ready`: a 9-slide auto-advancing story (`SLIDE_COUNT = 9`, `SLIDE_DURATION_MS = 8500`) with
  per-slide theme colors, progress bar, pause/resume on tap, manual previous/next, a poster-theme
  picker, and a “Share Your Story” action. Back resets to slide 0 and navigates up.
- Auto-play: on entering `Ready`, the top song is played in the background if it is not already the
  current track; leaving Wrapped pauses playback.
- Time-range control re-loads data via `setTimeRange`.
- Saving: while saving, the save action is disabled/busy; on success the UI reflects saved state.
  Sharing invokes `WrappedShareHelper.shareWrappedSummary(context, data, posterTheme, isStoryFormat)`.

`WrappedBannerCard(onClick, modifier = Modifier, onDismiss = null)`:

- Dismissible banner (dismiss button only when `onDismiss != null`) with entry/exit
  fade+expand/shrink animation. Clicking invokes `onClick`; dismiss invokes `onDismiss`. It hides
  itself locally after dismissal.

`WrappedShareHelper`:

```kotlin
object WrappedShareHelper {
  suspend fun shareWrappedSummary(
    context: Context,
    data: WrappedData,
    posterTheme: WrappedPosterTheme = WrappedPosterTheme.VIOLET_MINT,
    isStoryFormat: Boolean = true,
  )
}
```

Contract: renders a 1080×1920 poster bitmap (story) or alternate aspect off the main thread, loads
artwork via Coil with error fallback, draws theme background/headline/accent/secondary colors, and
launches a share sheet for the image. Missing artwork must still produce a shareable poster.

---

## 7. Podcasts, Music Flow, Generate, Recognition, Ambient

### 7.1 `PodcastsScreen`

```kotlin
@Composable fun PodcastsScreen(
  navController: NavController,
  viewModel: PodcastsViewModel = hiltViewModel(),
)
```

`PodcastsViewModel(database, podcastShows)` public flows/functions:

- `podcasts: StateFlow<List<Playlist>>` — followed shows: playlists whose `browseId` is a known
  podcast-show id and that are bookmarked.
- `newEpisodes`, `exploreSections: MutableStateFlow<List<HomePage.Section>>`, `isLoading`,
  `isRefreshing`, `isLoadingMore`.
- `loadPodcastSections()`, `loadMorePodcastSections()`, `refresh()`, `loadNewEpisodes()`.

Behaviour:

- First composition loads new episodes and explore sections. Explore sections come from the home
  feed's “podcast” chip (or `FEmusic_home`); only episode songs, podcast playlists and playlists are
  retained; empty sections are dropped. Continuation pagination appends new sections, de-duplicated
  by title; reaching the end stops further loads.
- Pull-to-refresh refreshes new episodes and sections. Infinite scroll triggers load-more when near
  the end.
- `scrollToTop` in the nav back-stack `SavedStateHandle` scrolls the list to the top and resets the
  flag.
- Section headers navigate to `youtube_browse/{browseId}?params=…` or `browse/{browseId}`.
  Playlist items navigate to `online_playlist/{id}`; song/episode items play a queue and/or navigate
  to `podcast_video` when a video is available.
- Empty folder: localised empty placeholder.

### 7.2 `PodcastVideoScreen`

```kotlin
@Composable fun PodcastVideoScreen(navController: NavController)
```

- Requires current `PlayerConnection` and a current `MediaMetadata`; if absent, pops back.
- Creates a separate `ExoPlayer` merged with the app's audio source; restores the app player position
  on exit. Video starts at the app player's current position and plays when ready.
- Requests sensor-landscape orientation, keeps the screen on, hides system bars; restores portrait,
  keep-screen-on and bars on dispose; on exit seeks the app player to the last video position and
  resumes if it was playing.
- Back exits the video player (same restore path). Controls auto-hide; track selection is supported
  (video/audio options loaded asynchronously; a load failure leaves default tracks).
- Non-podcast media still enters, but episode detection uses podcast progress, duration heuristics,
  `MUSIC_VIDEO_TYPE_PODCAST_EPISODE`, and podcast browse prefixes.
- iOS-style swipe-down-to-dismiss and tap-to-toggle controls are supported.

### 7.3 `MusicFlowScreen` and `MusicFlowViewModel`

```kotlin
@Composable fun MusicFlowScreen(
  navController: NavController,
  viewModel: MusicFlowViewModel = hiltViewModel(),
)
```

`MusicFlowViewModel(context, session, database, lyricsHelper)` public API:

```kotlin
val preferences: StateFlow<MusicFlowPreferences>
val uiState: StateFlow<MusicFlowUiState>   // items, selectedIndex, isInitialLoading, isLoadingMore,
                                           // error, hasMore, playbackStatus, canRestorePreviousQueue,
                                           // isOfflineFeed
fun attach(playerConnection); fun detach()
fun onPageSettled(index: Int)
fun togglePlayPause(); fun next(); fun previous()
fun retryPlayback(); fun retryFeed(); fun loadMoreIfNeeded()
fun notInterested(item, persist: Boolean); fun lessLikeThis(item)
fun restorePreviousQueue(): Boolean
```

Behaviour:

- `attach` binds a `MusicFlowCoordinator` to the player and observes playback/track state; `detach`
  releases jobs. Attaching the same connection twice is a no-op.
- The pager uses the model's `items`/`selectedIndex` as the source of truth. Settling a page tells the
  view-model; external index changes animate/scroll to that page (instant when reduced motion).
  A trailing “loading/error/end” page exists when more may load.
- Actions: play/pause current, next/previous, not-interested/less-like-this, retry playback/feed,
  restore the previous queue, open add-to-playlist and YouTube song menus, and toggle an inline
  lyrics overlay (system back closes the overlay first).
- Visual background animation requires `animatedBackground && !dataSaver && !reducedMotion`;
  artwork prefetch is allowed when not data-saver and either cellular prefetch is enabled or the
  network is unmetered.
- States: initial loading → placeholder; empty + error → localized error with Retry; empty + no error
  → placeholder; offline feed → `isOfflineFeed = true` and cached items; more errors while items
  exist surface as the trailing page, not a full-screen replacement.
- Accessibility: `CustomAccessibilityAction`s “Previous” and “Next” on the pager; descriptive labels
  on lyric expand/collapse and action buttons.

### 7.4 `GenerateScreen`

```kotlin
@Composable fun GenerateScreen(
  navController: NavController,
  viewModel: GenerateViewModel = hiltViewModel(),
)
```

`GenerateViewModel(localTasteEngine, songPlayStatsDao, recommendationExclusionDao, generationStatus?)`
public API: `uiState: StateFlow<GenerateUiState>`, `statsCount`, `exclusionCount`, `generate()`,
`cancelGeneration()`, `clearExclusions()`, `resetState()`.

Behaviour/invariants:

- Initial state `Idle`. `generate()` is guarded: ignored while `Generating`. It moves through
  `Generating(message)` → `Success(playlistId)` or `Error(message)`; failure messages containing
  “No listening history” map to `EmptyHistory`; messages containing “excluded” map to `AllExcluded`;
  an idle status while generating resets to `Idle`.
- A `GenerationStatus` external controller, when present, mirrors its running/done/failed/idle state.
- `statsCount` observes the top-played statistic; `exclusionCount` observes all exclusions.
- Cancel stops the job and returns to a non-generating state; clear exclusions empties exclusions.
- UI: header explaining the on-device taste engine; “Track Profile” shows “Active” when stats exist,
  else “Building”; “Exclusions” shows `<n> hidden`; states render idle CTA, generating progress with
  message and cancel, success (open generated playlist / done), empty history, all excluded, error
  with retry. Nav back returns up.

### 7.5 Recognition screens

```kotlin
@Composable fun RecognitionScreen(navController: NavController)
@Composable fun RecognitionHistoryScreen(navController: NavController)
```

`RecognitionScreen`:

- Resets the recognition service on enter and dispose.
- RECORD_AUDIO permission is required; if absent, show a permission request affordance. Granting
  permission enables capture; denying leaves an explanatory state.
- States follow `RecognitionStatus` (idle/listening/processing/result/error): pulse/animated capture
  indicator while listening; on result show title, artist, artwork, and actions (play, add to
  library, open history). On error show a retry action. Recognition failure must not crash.
- When a result is produced it is written to `recognitionHistory` with a `recognizedAt` timestamp.
- Actions navigate to `recognition_history`.

`RecognitionHistoryScreen`:

- Reads `database.recognitionHistory()` as a list, newest-first.
- Search field (`rememberSaveable`) filters case-insensitively on title or artist.
- Items are grouped by date relative to today: “Today”, “Yesterday”, “This Week”, then
  `MMMM yyyy`.
- Item tap plays/navigates to the recognised song; long-press/menu deletes a single item after
  confirmation; toolbar action clears all history after confirmation.
- Empty state shows a placeholder; no crash on empty history.

### 7.6 Ambient

```kotlin
@Composable fun AmbientModeScreen(navController: NavController)
@Composable fun AmbientCanvasBackground(modifier: Modifier = Modifier)
@Composable fun AmbientGlowBackground(mediaMetadata: MediaMetadata?, modifier: Modifier = Modifier)
```

- `AmbientModeScreen` is a full-screen now-playing surface. On enter: request sensor-landscape,
  keep screen on, hide system bars; restore all of these on exit. Requires `PlayerConnection`; if
  missing, no crash (return).
- Renders art (when enabled), title/artist/lyrics per preferences (`AmbientShowArtKey`,
  `AmbientShowTitleKey`, `AmbientShowArtistKey`, `AmbientShowLyricsKey`, `AmbientFullScreenArtKey`),
  `AmbientArtScaleKey`, `AmbientSpacingKey`.
- Gestures: horizontal swipe beyond 150 px (dominant axis) seeks previous/next; vertical drag adjusts
  media volume. A settings sheet exposes ambient settings. System back pops.
- Backgrounds: `AmbientGlowBackground` derives colours from the current media metadata when present
  and must degrade to a neutral gradient when it is null. `AmbientCanvasBackground` animates a
  decorative canvas honoring reduced-motion and data-saver (static/no animation when disabled).
- Accessibility: the full-screen surface is a single meaningful region; controls and lyrics text
  remain readable; gesture actions have button equivalents in the settings sheet.

---

## 8. Listen Together and comments

### 8.1 `ListenTogetherScreen`

```kotlin
@Composable fun ListenTogetherScreen(
  navController: NavController,
  showTopBar: Boolean = false,
)
```

Purpose: room creation/joining, live room management, participant controls, suggestions and chat
entry.

Contract:

- Requires `LocalListenTogetherManager`; when null, render `NotConfiguredContent()` and nothing else.
- Reads `connectionState`, `roomState`, `userId`, `pendingJoinRequests`, `pendingSuggestions` and
  `ListenTogetherInTopBarKey`. `shouldShowTopBar = showTopBar || listenTogetherInTopBar`.
- On entry, if disconnected/error, call `connect()`. Collect `events`:
  - `JoinRejected` → stop joining/creating and show a reason message (invalid room code is mapped to
    the localized “invalid room code”; blank reason → “join request denied”; otherwise
    “<denied>: <reason>”).
  - `JoinApproved` → clear joining/error.
  - `RoomCreated` → stop creating, copy the room code to the clipboard as `ListenTogetherRoom`.
- Inputs (transient, `rememberSaveable`): room code, username, creating/joining flags, join error,
  selected participant id/name. Username defaults from `ListenTogetherUsernameKey`; saving username
  writes that preference.
- Derived: `isInRoom = manager.isInRoom`; `isHost = roomState.hostId == userId`.
- Actions/effects:
  - Create room (requires non-blank username), join room (requires room code + username), leave,
    cancel a pending join.
  - Host-only: approve/reject join requests, kick user (“Removed by host”), permanently block+kick,
    transfer ownership, approve/reject track suggestions, toggle participant control.
  - Participant: suggest a track, open chat (`listen_together/chat`), open settings
    (`settings/integrations/listen_together`), copy room code/share invite.
  - `scrollToTop` back-stack handle scrolls the list and resets the flag.
- UI states: not-connected, connected-but-not-in-room (create/join forms), joining (waiting for
  approval), in-room (connection status card, room status card, connected users, pending requests,
  suggestions, settings link), and error banners. Cast/volume sync status is shown when enabled.
- Room code is displayed and shareable; it is never shown when not in a room.

### 8.2 `CommentSheet`

```kotlin
@Composable fun CommentSheet(videoId: String, onDismiss: () -> Unit)
@Composable fun CommentItem(
  renderer: CommentRenderer,
  onShowReplies: (() -> Unit)? = null,
  isReply: Boolean = false,
)
@Composable fun CommentDetailPane(thread: CommentThreadRenderer, onBack: () -> Unit)
```

Contract:

- `CommentSheet` is a modal bottom sheet keyed by `videoId`. On (re)entry it loads the first page of
  YouTube comments; state: `comments`, `nextToken`, `totalComments`, `isLoading`, `isError`.
- Loading with no comments → centered wavy spinner. Error OR empty after loading → error icon +
  localized “no comments”. The header shows the comment count when known.
- Endless pagination: when a non-null continuation token exists, a sentinel item loads the next page
  on composition and appends results; token becomes the new one (null ends pagination). A failed
  continuation leaves prior comments visible.
- Two-pane (list/detail) scaffold: tapping a comment with replies opens `CommentDetailPane`; back
  returns to the list. Replies load lazily from the thread's continuation token (button or inline
  continuation), show a reply spinner while loading, and remain empty if none are available.
- `CommentItem` renders author avatar (decorative), author name, published time, like count/content,
  and comment text; it is clickable only if `onShowReplies != null`. `isReply` uses a smaller avatar
  and vertical padding.
- Dismissing hides the sheet then invokes `onDismiss`. Errors print/report but do not crash.

### 8.3 `CommentTogetherScreen`

```kotlin
@Composable fun CommentTogetherScreen(navController: NavController)
```

Contract:

- Requires `LocalListenTogetherManager`; absent → no content (return).
- Reads `chatMessages`, `userId`, `roomState`; title shows the room context. A back button returns.
- Messages render as bubbles: own messages right-aligned with primary container; others left-aligned
  with a username label. Each bubble shows text (YouTube Music links made clickable with primary
  color, underline, bold) and a timestamp (`HH:mm`-style). Long-press/tap on another user's message
  starts a reply.
- Reply state shows a small quoted preview above the input; sending includes
  `RepliedMessage(username, message)` and clears both input and reply.
- The input is a single-line text field with placeholder “Type a message…”, send enabled only for
  non-blank text. On send: `manager.sendChatMessage(text, replyTo)`, clear input, clear focus.
- New messages auto-scroll to the bottom; empty chat shows an illustrated placeholder.
- Keyboard resizing: the bottom input uses IME padding; window insets come from the player-aware
  insets.

---

## 9. Spotify import / login

### 9.1 `SpotifyImportScreen`

```kotlin
@Composable fun SpotifyImportScreen(
  navController: NavController,
  spotifyImportViewModel: SpotifyImportViewModel = hiltViewModel(),
)
```

`SpotifyImportViewModel(repository)` public API:

```kotlin
val uiState: StateFlow<SpotifyImportUiState>
fun restoreSession()
fun connectWithCookies(spDc: String, spKey: String)
fun loadSources()
fun addPlaylistByUrl(url: String)
fun toggleSource(sourceId: String); fun selectAllSources(); fun clearSelection()
fun importSelectedSources(); fun cancelImport()
fun logout(); fun dismissSummary(); fun dismissError()
```

Invariants:

- Initial state is `isLoading = true`. `restoreSession()` populates authentication/name/avatar and,
  when authenticated, loads sources. Failures set `isAuthenticated = false`, `isLoading = false`,
  `errorMessage`.
- `connectWithCookies` ignores a blank `spDc`. Success sets authenticated and loads sources.
- `loadSources()` selects every loaded source by default.
- `addPlaylistByUrl` trims; ignores blank or when an import is in progress; replaces an existing
  source with the same id or prepends the new one and selects it.
- `toggleSource` flips membership; `selectAllSources` selects all current source ids;
  `clearSelection` empties.
- `importSelectedSources` is guarded: needs a non-empty selection, no active job, and no progress.
  While importing, `progress` is non-null; success sets `summary` and clears progress; cancellation
  clears progress; any other failure sets `errorMessage` and clears progress.
- `cancelImport` cancels the job and clears progress. `logout` is blocked during an import and
  resets the state to a fresh unauthenticated `SpotifyImportUiState()`.
- Errors are one-shot: `dismissError`/`dismissSummary` clear them.

UI contract:

- Top bar “Spotify Import”; list group content depends on authentication:
  - Unauthenticated: “Connect Spotify” and “Import by link” entries.
  - Authenticated: account row (name/avatar; loading description), “Select sources”
    (available count), “Import by link”, “Import selected” (`selectedSourceIds.size`, enabled only
    when `canImport`), “Refresh”, “Logout”. Actions are disabled while loading or importing.
- “Connect” navigates to `settings/spotify_login`. There is also a legacy in-screen
  `SpotifyLoginSheet` modal path used when the caller chooses it; both capture `sp_dc` and `sp_key`
  and call `connectWithCookies`.
- Add-by-link dialog: text field; on confirm call `addPlaylistByUrl`. Invalid/blank input closes
  without action when disabled.
- Source picker sheet: select-all/clear controls, per-source rows with thumbnail (fallback icon by
  type), title, subtitle or “N tracks”, and a checkbox; the import button is disabled unless
  `canImport`.
- Progress dialog: shows current source title, completed/total sources, matched/total tracks, and a
  linear percent bar; cancel calls `cancelImport`.
- Summary dialog: per-source title, total/imported/failed counts and totals; dismiss clears it.
- Error dialog: message and OK, dismiss clears.
- Logout has no confirmation in the current contract; it resets the import session.

### 9.2 `SpotifyLoginScreen` and `SpotifyLoginSheet`

```kotlin
@Composable fun SpotifyLoginScreen(
  navController: NavController,
  onCookiesCaptured: (spDc: String, spKey: String) -> Unit,
)
```

- WebView loads `SpotifyAuth.LOGIN_URL` with JS/DOM storage, zoom and a fixed mobile user agent;
  third-party cookies accepted; cookie store cleared before load; touch gestures do not leak to the
  parent pager.
- On every page start/finish/URL override it attempts to read `sp_dc` and `sp_key` from
  `https://open.spotify.com`. The first non-blank `sp_dc` latches capture: it calls
  `onCookiesCaptured(spDc, spKey)` exactly once and navigates up.
- The WebView is stopped, blanked and destroyed on dispose. Back navigates WebView history when
  possible.
- `SpotifyLoginSheet` is the modal variant used inside import: same capture rules, additionally
  handles popup windows and external-scheme fallbacks, and must be destroyed on dismiss.

---

## 10. Platform components

### 10.1 Manifest declarations (must be preserved)

Permissions used by this cluster:

- `INTERNET`, `ACCESS_NETWORK_STATE` — import, login, uptime, recognition lookup.
- `RECORD_AUDIO` and `FOREGROUND_SERVICE_MICROPHONE` — music recognition.
- `WRITE_SETTINGS` — setting a ringtone.
- `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
  `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` — playback/download services.
- `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` — local media.
- `BLUETOOTH_CONNECT` (API 31+) and legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` (maxSdk 30).
- `REQUEST_INSTALL_PACKAGES` — update APK install.
- Optional features: `android.hardware.microphone` (`required=false`),
  `android.hardware.location.gps` (`required=false`), leanback and automotive
  (`required=false`), touchscreen (`required=false`).

Activities / aliases:

- `.MainActivity`: `exported=true`, `launchMode=singleTask`, `configChanges` for
  orientation/screen size/smallest size/uiMode, `adjustResize`. Intent filters include MAIN/launcher,
  `VIEW`+`MUSIC_PLAYER` with LEANBACK/APP_MUSIC categories, `MEDIA_PLAY_FROM_SEARCH`, YouTube
  http/https hosts with video/channel/playlist/browse/search path prefixes, `youtu.be`,
  `vnd.youtube` schemes, text/plain `SEND`, and the Listen Together links below.
- `.MainActivityAlias`: enabled launcher alias, dynamic icon (`ic_launcher`).
- `.MainActivityStatic`: disabled by default, static icon (`ic_launcher_static`). Exactly one of the
  two aliases is enabled according to `AppIconTypeKey`.
- `.ui.screens.CrashActivity`: separate `:crash` process, not exported, excluded from recents.
- `UCropActivity` (`com.yalantis.ucrop`), not exported.
- Services: `.playback.MusicService` (exported, `mediaPlayback`, Media3/MediaBrowser intents),
  `.playback.ExoDownloadService` (`dataSync`), `.playback.AudioExportService` (as declared), plus
  the existing microphone/data-sync services.
- Providers: `FileProvider` (not exported, grantUriPermissions), `.utils.AutomotiveArtworkProvider`
  (exported, serves only signed content URIs), `DensityScaler` (not exported). WorkManager's
  initializer is removed from `InitializationProvider` (on-demand init in `App`).

Listen Together deep links (both must be preserved):

- HTTPS app link: `https://metroserverx.meowery.eu/listen*`, `autoVerify=true`,
  BROWSABLE+DEFAULT.
- Custom scheme: `revenge://listen`, BROWSABLE+DEFAULT.
Both resolve to the room-join flow; parsing must reject malformed codes and show an error rather
than crash.

### 10.2 App icon switching

`IconUtils.setIcon(context, type)` enables exactly one of
`com.revenge.music.MainActivityAlias` / `com.revenge.music.MainActivityStatic` via
`PackageManager.setComponentEnabledSetting(..., DONT_KILL_APP)`:
`DEFAULT` enables Alias, `STATIC` enables Static. `AppIconTypeKey` persists
`AppIconType` as its name (`"DEFAULT"`/`"STATIC"`); the settings UI compares the stored enum.

### 10.3 Build configuration

`app/build.gradle.kts` (`com.android.application`, namespace `com.revenge.music`,
applicationId `com.revenge.music`, compileSdk 37, minSdk 26, targetSdk 36). BuildConfig fields used
by the cluster:

- `UPDATE_REPO` (String, default `""`; blank disables update checks/contributor fetch).
- `LASTFM_API_KEY` / `LASTFM_SECRET` (String, from local.properties/env; blank disables Last.fm).
- `GH_CLIENT_ID` / `GH_CLIENT_SECRET`.
- `FLOW_NEURO_BASE_URL` (default `https://api.flowneuroengine.com`) / `FLOW_NEURO_API_KEY`.
- `IS_NIGHTLY` (Boolean).
- `CAST_AVAILABLE` per flavor (`foss=false`, `gms=true`), `ARCHITECTURE` per ABI flavor.

The app must compile for `universalFossDebug`/`universalGmsDebug` without Google services; Firebase
Crashlytics is conditional on `google-services.json` existing.

---

## 11. Target source and test files

The implementer must produce these files. Paths are relative to
`app/src/main/kotlin/com/revenge/music/` unless noted. “Existing shared” files must keep their
current public contracts; they are listed because the cluster cannot compile without them.

Screens and UI:

- `ui/screens/settings/SettingsScreen.kt`
- `ui/screens/settings/SearchableSettings.kt`
- `ui/screens/settings/AccountSettingsScreen.kt`
- `ui/screens/settings/PrivacySettings.kt`
- `ui/screens/settings/StorageSettings.kt`
- `ui/screens/settings/BackupAndRestore.kt`
- `ui/screens/settings/UpdateSettings.kt`
- `ui/screens/settings/AboutScreen.kt`
- `ui/screens/settings/UptimeScreen.kt`
- `ui/screens/settings/AppIconSettingsScreen.kt`
- `ui/screens/settings/FontSelectionScreen.kt`
- `ui/screens/settings/ThemeScreen.kt`
- `ui/screens/settings/HapticsSettings.kt`
- `ui/screens/settings/GlassEffectSettings.kt`
- `ui/screens/settings/ExtensionSettings.kt`
- `ui/screens/settings/LastFMSettingsScreen.kt`
- `ui/screens/settings/BlockedArtistsScreen.kt`
- `ui/screens/settings/DislikedSongsScreen.kt`
- `ui/screens/settings/AmbientSettingsScreen.kt`
- `ui/screens/settings/MusicFlowSettings.kt`
- `ui/screens/settings/PlayerSettings.kt`
- `ui/screens/settings/ContentSettings.kt`
- `ui/screens/settings/AiSettings.kt`
- `ui/screens/settings/AppearanceSettings.kt`
- `ui/screens/settings/RingtoneViewModel.kt`
- `ui/screens/settings/ListenBrainzManager.kt`
- `ui/screens/settings/integrations/ListenTogetherSettings.kt`
- `ui/screens/settings/cloud/NavidromeScreens.kt`
- `ui/screens/settings/cloud/JellyfinScreens.kt`
- `ui/screens/AccountScreen.kt`
- `ui/screens/LoginScreen.kt`
- `ui/screens/StatsScreen.kt`
- `ui/screens/ListeningSummaryScreen.kt`
- `ui/screens/ActivityHistory.kt`
- `ui/screens/DetailedListeningHistoryScreen.kt`
- `ui/screens/ListenTogetherScreen.kt`
- `ui/screens/CommentSheet.kt`
- `ui/screens/CommentTogether.kt`
- `ui/screens/SpotifyImportScreen.kt`
- `ui/screens/SpotifyLoginScreen.kt`
- `ui/screens/wrapped/WrappedModels.kt`
- `ui/screens/wrapped/WrappedBannerCard.kt`
- `ui/screens/wrapped/RevengeWrappedViewModel.kt`
- `ui/screens/wrapped/WrappedScreen.kt`
- `ui/screens/wrapped/WrappedShareHelper.kt`
- `ui/screens/podcast/PodcastsScreen.kt`
- `ui/screens/podcast/PodcastVideoScreen.kt`
- `ui/screens/musicflow/MusicFlowScreen.kt`
- `ui/screens/generate/GenerateScreen.kt`
- `ui/screens/recognition/RecognitionScreen.kt`
- `ui/screens/recognition/RecognitionHistoryScreen.kt`
- `ui/screens/ambient/AmbientModeScreen.kt`
- `ui/screens/ambient/AmbientCanvasBackground.kt`
- `ui/screens/ambient/AmbientGlowBackground.kt`

Cluster view-models and models:

- `viewmodels/AccountViewModel.kt`
- `viewmodels/AccountSettingsViewModel.kt`
- `viewmodels/StatsViewModel.kt`
- `viewmodels/ListeningSummaryViewModel.kt`
- `viewmodels/DetailedListeningHistoryViewModel.kt`
- `viewmodels/MusicFlowViewModel.kt`
- `viewmodels/GenerateViewModel.kt`
- `viewmodels/BackupRestoreViewModel.kt`
- `viewmodels/ListenTogetherViewModel.kt`
- `viewmodels/LibraryViewModels.kt` — contains `PodcastsViewModel`
- `spotifyimport/SpotifyImportModels.kt`
- `spotifyimport/SpotifyImportRepository.kt`
- `spotifyimport/SpotifyImportViewModel.kt`
- `listentogether/ListenTogetherManager.kt`
- `listentogether/ListenTogetherClient.kt`
- `listentogether/ListenTogetherServers.kt`
- `listentogether/Protocol.kt`
- `ui/screens/NavigationBuilder.kt`
- `ui/screens/Screens.kt`
- `constants/StatPeriod.kt`

Existing shared contracts that must remain compatible (write/modify only as required):
`core/src/main/kotlin/com/revenge/music/constants/PreferenceKeys.kt`,
`core/src/main/kotlin/com/revenge/music/ui/screens/OptionStats.kt`,
`app/src/main/kotlin/com/revenge/music/utils/DataStoreCompose.kt`,
`app/src/main/kotlin/com/revenge/music/utils/IconUtils.kt`,
`app/src/main/kotlin/com/revenge/music/ui/component/Material3SettingsGroup.kt`.

Platform/build:

- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`

Tests the implementer must write/extend (JVM unit tests; no Android framework needed for
view-model logic):

- `app/src/test/kotlin/com/revenge/music/generate/GenerateViewModelTest.kt`
- `app/src/test/kotlin/com/revenge/music/generate/GenerationStatusTest.kt`
- `app/src/test/kotlin/com/revenge/music/generate/LocalTasteScoringTest.kt`
- `app/src/test/kotlin/com/revenge/music/generate/RecommendationEngineTest.kt`
- `app/src/test/kotlin/com/revenge/music/generate/RecommendationScoringTest.kt`
- `app/src/test/kotlin/com/revenge/music/generate/TasteProfileProviderTest.kt`
- `app/src/test/kotlin/com/revenge/music/generate/GenresRepositoryTest.kt`
- `app/src/test/kotlin/com/revenge/music/generate/SongPlayStatsRepositoryTest.kt`
- `app/src/test/kotlin/com/revenge/music/spotify/SpotifyQueueTest.kt`
- Add: `app/src/test/kotlin/com/revenge/music/wrapped/RevengeWrappedViewModelTest.kt`
- Add: `app/src/test/kotlin/com/revenge/music/spotifyimport/SpotifyImportViewModelTest.kt`
- Add: `app/src/test/kotlin/com/revenge/music/settings/StatsPeriodTest.kt`
- Add: `app/src/test/kotlin/com/revenge/music/listentogether/ListenTogetherChatTest.kt`

---

## 12. Acceptance tests

All tests below must be deterministic: fixed clock injection or explicit timestamps, fake DAOs /
repositories returning immediate flows, and no real network. Screens are validated through Compose
UI tests or preview/screenshot targets as noted.

### 12.1 Preference semantics

1. `rememberPreference` with a stored value of the wrong type returns the default; a matching type
   returns the stored value; a stored value written after composition updates readers.
2. `rememberEnumPreference` round-trips every enum entry by name; unknown stored strings and
   non-string stored values fall back to the default.
3. Setting a preference and immediately reading the returned state yields the new value before the
   datastore edit completes.
4. A `Set<String>` default plus a stored set returns the stored set unchanged.

### 12.2 Navigation and highlighting

5. Building the nav graph exposes every route in §2.1 with the specified argument names/types;
   `settings/storage` defaults `autoOpenExportPicker` to false.
6. Navigating to `settings/account?highlightKey=<encoded title>` composes the account screen with a
   non-null highlight and scrolls the matching row into view; an unknown key leaves scroll at top.
7. `settings/integrations` without `highlightKey` supplies the literal `"Integrations"`.
8. Root settings search with `"wrapped"` returns the recap entry; with `"zzzz"` returns the
   no-results message; clearing restores all groups.
9. `detailed_listening_history/1700000000000` yields `startTimestamp == 1700000000000` and a 24h
   window.

### 12.3 Stats and summaries

10. `StatsViewModel`: default option `CONTINUOUS`, chip `0`; changing the option to `WEEKS/MONTHS/
    YEARS` produces a chip list derived from `firstEvent`; with null `firstEvent` the date chips are
    empty.
11. `statToPeriod` boundaries: `WEEKS(0)` is one day before now minus 0 weeks; `CONTINUOUS` with an
    index ≥ `StatPeriod.entries.size` equals `ALL` (epoch 0).
12. Period switch recomputes total/unique flows against the new UTC range and resets chip index.
13. `ListeningSummaryViewModel`: `goToNextWeek` at offset 0 is a no-op; `setWeekFromEpoch` maps a
    Monday to its week offset and clamps future dates to 0.
14. Vibe mapping: title containing “phonk” → “🔥 Phonk”; “lofi” → “🌙 Chill”; unknown → “✨ Diverse”;
    dominant selection ignores “✨ Diverse” when another category exists; percentage change is 100
    when previous play time is 0.
15. `DetailedListeningHistoryViewModel` parses the saved state arg, defaults to 0 on malformed input,
    and queries the 24-hour window.

### 12.4 Wrapped

16. `RevengeWrappedViewModel`: init loads `THIS_YEAR` and ends in `Ready` when ≥ 3 unique songs;
    otherwise `InsufficientData(songsPlayed, 3)`.
17. With < 3 songs this year but ≥ 3 all-time, selecting `THIS_YEAR` auto-falls back: `selectedRange`
    becomes `ALL_TIME` and a `Ready(ALL_TIME)` state is emitted.
18. `setTimeRange` with the same range is a no-op (no reload); a different range reloads.
19. `createWrappedPlaylist` while saving ignores a second call; success inserts ≤ 50 songs and
    invokes `onComplete(true)`; a thrown insert invokes `onComplete(false)` and resets saving.
20. `WrappedBannerCard` hides after dismiss; without `onDismiss` no dismiss control is exposed;
    click invokes `onClick`.
21. `WrappedShareHelper.shareWrappedSummary` produces a non-empty bitmap and launches a share intent
    even when artwork loading fails.

### 12.5 Generate

22. Initial state is `Idle`.
23. `generate()` success → `Success(playlistId)`; a failure message containing “No listening
    history” → `EmptyHistory`; “excluded” → `AllExcluded`; other → `Error(message)`.
24. `generate()` during `Generating` is ignored (no second job).
25. `cancelGeneration`/`resetState` return to `Idle`; `clearExclusions` empties the exclusion flow.
26. External `GenerationStatus` running/done/failed transitions mirror into `uiState`.

### 12.6 Spotify import

27. Initial `uiState.isLoading` is true; `restoreSession` unauthenticated → `isLoading=false`,
    `isAuthenticated=false`.
28. `connectWithCookies("", key)` performs no work and leaves state unchanged.
29. `loadSources` success selects every returned source; `toggleSource` removes then re-adds;
    `selectAllSources` selects all; `clearSelection` empties.
30. `addPlaylistByUrl("  ")` is ignored; a valid URL prepends/replaces and selects the source.
31. `importSelectedSources` with an empty selection or while progress is non-null does nothing;
    success sets `summary` and clears `progress`; failure sets `errorMessage`; `cancelImport` clears
    `progress`.
32. `logout` is ignored during an import and otherwise resets to a fresh unauthenticated state.
33. `canImport` is false while loading or importing and true only with a non-empty selection.

### 12.7 Login, comments, Listen Together

34. `LoginScreen` host policy: `accounts.google.com`, `music.youtube.com`, `gstatic.com`,
   `*.googleusercontent.com` and `g.co` stay in the WebView; `example.com`, `javascript:` and
   `intent:` are dispatched externally and return `true` (cancelled in WebView).
35. Successful-auth latch: the first cookie containing `SAPISID`/`__Secure-3PSID`/`LOGIN_INFO` while
   on YouTube Music triggers exactly one account-upsert and navigation; subsequent page finishes do
   not re-trigger.
36. Malformed `SavedAccountsKey` JSON is treated as an empty list; a successful login appends a new
   account, de-duplicated by name.
37. `CommentSheet`: loading with empty comments shows the spinner; empty after load shows “no
    comments”; a continuation token appends the next page and null stops pagination; a failed initial
    load shows the error state without crashing.
38. `CommentTogether`: blank input cannot be sent; sending a non-blank message calls
    `sendChatMessage` once and clears the field; YouTube Music URLs in a message become clickable
    annotations; new messages scroll to the end.
39. `ListenTogetherScreen` with a null manager renders only `NotConfiguredContent`. `JoinRejected`
    with an “invalid” reason shows the invalid-room-code text; `RoomCreated` copies the code to the
    clipboard; a non-host cannot see host-only controls.
40. `ListenTogetherSettings` preference writes: server URL, username, auto-approval, sync-volume and
    smart-resync update the corresponding keys and manager settings.

### 12.8 State restoration and accessibility

41. Process-recreation tests (Robolectric/Compose): root settings search query, Listen Together room
    code/username/flags, comment history query, and Music Flow lyrics-expanded state survive
    recreation via `rememberSaveable`; ephemeral derived state recomputes.
42. Compose semantics: every icon-only back/close/share/search/clear button has a content
    description; switches expose toggle semantics; the Music Flow pager exposes “Previous”/“Next”
    custom actions; charts expose their totals as text; decorative artwork has null descriptions.
43. Screenshot/preview targets: `StatsScreen` with populated and empty data; `WrappedScreen` in
    Loading, InsufficientData and Ready; `AccountSettingsScreen` logged-out and logged-in;
    `SpotifyImportScreen` authenticated/unauthenticated; `ListenTogetherScreen` not-connected and
    in-room; `GenerateScreen` in each `GenerateUiState`; `AmbientModeScreen` with and without
    metadata. Previews must render without network and without a bound player service.

### 12.9 Platform

44. Manifest contains both Listen Together deep-link filters (`https://metroserverx.meowery.eu/listen`
    and `revenge://listen`), the recognition `RECORD_AUDIO` permission, and `WRITE_SETTINGS`.
45. App icon switching enables exactly one launcher alias: `DEFAULT` → `MainActivityAlias`,
    `STATIC` → `MainActivityStatic`; the other is disabled; the app is not killed by the switch.
46. Debug build with blank `UPDATE_REPO`/`LASTFM_*` compiles and disables update/contributor
    fetching and Last.fm connect without runtime crashes.
