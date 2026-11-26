# Clean-Room Specification — `:app` cluster `player-ui`

Status: M2 clean-room specification (implementation-independent)
Target directories:
- `app/src/main/kotlin/com/revenge/music/ui/player`
- `app/src/main/kotlin/com/revenge/music/ui/menu`
- `app/src/main/kotlin/com/revenge/music/ui/component/SongDnaBottomSheet.kt`
- `app/src/main/kotlin/com/revenge/music/ui/component/AppearancePreviewCard.kt`
- `app/src/main/kotlin/com/revenge/music/ui/screens/equalizer`
- `app/src/main/kotlin/com/revenge/music/viewmodels`

Namespace root: `com.revenge.music`
This document describes observable contracts only. It does not reproduce implementation bodies or
Compose subtrees. Any renderer that satisfies the stated state, inputs, outputs, effects,
accessibility semantics and acceptance tests is conformant.

---

## 1. Purpose and scope

This cluster owns everything the user sees and does while a track is playing:

- the collapsed mini-player (classic and floating variants);
- the expanded player bottom sheet, its artwork carousel, transport, slider, pill navigation,
  inline and one-line lyrics views, video background and episode controls;
- the player queue sheet (reorder, remove, clear/undo, selection, automix, sleep timer);
- the context menus for songs, albums, artists, playlists, queue entries, playback sessions and
  multi-selection, plus the Add-to-Playlist, import, CSV mapping, custom-thumbnail and loading
  dialogs;
- the two equalizer surfaces (legacy system/profile picker and the Axion parametric UI);
- the Song DNA artist/credits bottom sheet and the appearance preview cards/modals;
- the app-level playback and library ViewModels under `com.revenge.music.viewmodels`.

The cluster is UI + presentation state. It must not own Media3 session code, DSP math, database
schemas, network clients or download engines; it reads them through the contracts in section 4.

Out of scope for this document (owned by sibling clusters, referenced here as compile contracts):
`:playback` (`PlayerConnection`, `MusicService`), `:core` (models, constants, preference keys),
`:domain`/database (`MusicDatabase`), `:lyrics`, `:audio-dsp`/`:dsp-core`, `:innertube`,
`ui/component` shared primitives other than the two files named above, and `ui/screens/settings`.

---

## 2. Build contract

`app/build.gradle.kts` must continue to provide the following libraries to this cluster (exact
versions come from `gradle/libs.versions.toml`):

- Compose runtime/foundation/ui/material3, Compose animation, `compose.reorderable`
  (`sh.calvin.reorderable`), `viewmodel.compose`, `hilt.navigation`;
- Hilt (`@HiltViewModel`, `hiltViewModel()`) and KSP;
- Media3 common/session (for `Player`, `PlaybackException`, `Timeline`, `MediaItem`);
- Coil 3 with its OkHttp network fetcher (artwork/video thumbnails);
- `smoothCorner` (`racra.compose.smooth_corner_rect_library`) and Material icons extended;
- `androidx.compose.material3` expressive APIs used by the player pill/toggle buttons;
- kotlinx-coroutines, kotlinx-serialization-json, Timber.

Kotlin/Compose compiler settings must keep stable/strong skipping compatible with
`@Immutable`/`@Stable` annotations used on the small data models. No new Gradle dependency may be
introduced by this cluster.

---

## 3. Package map and target files the implementer must write

### 3.1 `com.revenge.music.ui.player` (11 files)

| File | Primary public surface |
|---|---|
| `CanvasArtworkPlayer.kt` | `CanvasArtworkPlayer` |
| `EpisodeControls.kt` | `EpisodeControls` |
| `FloatingMiniPlayer.kt` | `FloatingMiniPlayer` |
| `MiniPlayer.kt` | `MiniPlayer`, `ProgressState` |
| `PlaybackError.kt` | `PlaybackError` |
| `Player.kt` | `BottomSheetPlayer`, `InlineLyricsView`, `MoreActionsButton`, `PlayerTopPill`, `PlayerPillOption` |
| `PlayerSyncedLyricsView.kt` | `PlayerSyncedLyricsView` |
| `Queue.kt` | `Queue` |
| `SeekButtons.kt` | `SeekButtons` |
| `Thumbnail.kt` | `Thumbnail`, `ThumbnailDimensions`, `MediaItemsData`, `CanvasArtworkPlaybackCache`, internal `normalizeCanvasSongTitle` / `normalizeCanvasArtistName` / `splitAndNormalizeArtists` |
| `ThumbnailSnapUtils.kt` | `ThumbnailSnapLayoutInfoProvider`, `calculateDistanceToDesiredSnapPosition`, `LazyGridLayoutInfo.singleAxisViewportSize` |

### 3.2 `com.revenge.music.ui.menu` (21 files)

`AddToPlaylistDialog.kt`, `AddToPlaylistDialogOnline.kt`, `AlbumMenu.kt`, `ArtistMenu.kt`,
`CsvColumnMappingDialog.kt`, `CustomThumbnailMenu.kt`, `ImportPlaylistDialog.kt`,
`LoadingScreen.kt`, `LyricsMenu.kt`, `OldPlayerMenu.kt`, `PlayerMenu.kt`, `PlaylistMenu.kt`,
`PlaylistScreenMenus.kt`, `QueueMenu.kt`, `SelectionSongsMenu.kt`, `SongMenu.kt`,
`YouTubeAlbumMenu.kt`, `YouTubeArtistMenu.kt`, `YouTubePlaylistMenu.kt`,
`YouTubeSelectionSongMenu.kt`, `YouTubeSongMenu.kt`.

### 3.3 Shared components (2 files)

`app/src/main/kotlin/com/revenge/music/ui/component/SongDnaBottomSheet.kt`,
`app/src/main/kotlin/com/revenge/music/ui/component/AppearancePreviewCard.kt`.

### 3.4 `com.revenge.music.ui.screens.equalizer` (7 files)

`EQState.kt`, `EQViewModel.kt`, `EqScreen.kt`,
`axion/AutoEqBottomSheet.kt`, `axion/AxionEqScreen.kt`, `axion/AxionEqViewModel.kt`,
`axion/CircularEqControl.kt`.

### 3.5 `com.revenge.music.viewmodels` (36 files)

`AccountSettingsViewModel.kt`, `AccountViewModel.kt`, `AlbumViewModel.kt`,
`ArtistAlbumsViewModel.kt`, `ArtistItemsViewModel.kt`, `ArtistViewModel.kt`,
`AutoPlaylistViewModel.kt`, `BackupRestoreViewModel.kt`, `BottomPlaylistViewModel.kt`,
`BrowseViewModel.kt`, `CachePlaylistViewModel.kt`, `ChartsViewModel.kt`,
`CreateAiPlaylistViewModel.kt`, `DetailedListeningHistoryViewModel.kt`, `ExploreViewModel.kt`,
`GenerateViewModel.kt`, `HistoryViewModel.kt`, `HomeViewModel.kt`, `LibraryViewModels.kt`,
`ListenTogetherViewModel.kt`, `ListeningSummaryViewModel.kt`, `LocalPlaylistViewModel.kt`,
`LocalSearchViewModel.kt`, `LocalSongsViewModel.kt`, `LyricsMenuViewModel.kt`,
`MoodAndGenresViewModel.kt`, `MusicFlowViewModel.kt`, `NewReleaseViewModel.kt`,
`OnlinePlaylistViewModel.kt`, `OnlineSearchSuggestionViewModel.kt`, `OnlineSearchViewModel.kt`,
`PlaylistsViewModel.kt`, `StatsViewModel.kt`, `ThemeViewModel.kt`, `TopPlaylistViewModel.kt`,
`YouTubeBrowseViewModel.kt`.

Tests the implementer must also write are listed in section 12.

---

## 4. Cross-module contracts required to compile

These are consumed as-is. They are not reimplemented here.

### 4.1 Composition locals (from `:app` root)

| Local | Contract used by this cluster |
|---|---|
| `LocalPlayerConnection` | nullable `PlayerConnection`; every player composable returns early when null |
| `LocalDatabase` | `MusicDatabase` |
| `LocalDownloadUtil` | `getDownload(mediaId): Flow<Download?>`, `downloads: Flow<Map<String, Download>>`, download/enqueue/remove methods |
| `LocalSyncUtils` | playlist/account sync helpers |
| `LocalListenTogetherManager` | nullable manager; `isInRoom`, `isHost`, `isGuestPlaybackRestricted`, `guestPlaybackRestricted: StateFlow<Boolean>` |
| `LocalMenuState` | `MenuState.show { }`, `dismiss()` |
| `LocalBottomSheetPageState` | `BottomSheetPageState.show { }` |
| `LocalRingtoneViewModel` | ringtone setters used by YouTube song menu |
| `LocalGlassEffectConfig` | `isEnabledFor(GlassComponent)`, `textColor` |
| `LocalNetworkConnectivityObserver` / NetworkConnectivityObserver | network status used by LyricsMenuViewModel |

### 4.2 `PlayerConnection` (`com.revenge.music.playback`)

Observable (`StateFlow`/`MutableStateFlow`) properties consumed: `mediaMetadata`,
`currentSong`, `currentLyrics`, `currentFormat`, `playbackState`, `isPlaying`,
`isEffectivelyPlaying`, `duration`, `queueTitle`, `queueWindows`, `currentMediaItemIndex`,
`currentWindowIndex`, `shuffleModeEnabled`, `repeatMode`, `canSkipPrevious`, `canSkipNext`,
`error`, `isMuted`, `waitingForNetworkConnection`, `isCrossfading`, `isAutomixing`,
`automixDebugInfo`.

Exposed `player: ExoPlayer` (read `currentPosition`, `duration`, `currentMediaItemIndex`,
`nextMediaItemIndex`, `previousMediaItemIndex`, `currentTimeline`, `isPlaying`; write
`shuffleModeEnabled`, `playWhenReady`; call `seekTo`, `seekToNext`, `seekToPreviousMediaItem`,
`seekTo`, `play`, `pause`, `setPlaybackSpeed`).

Exposed `service: MusicService` (read `automixItems`, `sleepTimer`, `castConnectionHandler`,
`playerVolume`, `activeQueue`, `isPlayerReady`; call `clearQueue()`, `restoreQueue(state)`,
`startRadioSeamlessly()`, `snapshotQueue()`, `addToQueueAutomix()`, `restoreQueueSnapshot()`).

Convenience methods used: `playQueue`, `playNext`, `addToQueue`, `toggleLike`, `toggleLibrary`,
`toggleMute`, `togglePlayPause`, `seekToNext`, `seekToPrevious`, `startRadioSeamlessly`,
`clearQueue`, `restoreQueue`.

### 4.3 Models and constants (`:core`, `:innertube`, `:domain`)

- `com.revenge.music.models.MediaMetadata`, `Song`, `SongEntity`, `Playlist`, `Album`, `Artist`,
  `Event`, `PlaylistSong`, `PlaylistSongMap`, `MediaMetadata.Artist`, `Album`/`Artist` inner types,
  `YTItem` subtypes (`SongItem`, `AlbumItem`, `ArtistItem`, `PlaylistItem`), `ItemsPage`.
- `com.revenge.music.db.entities.LyricsEntity`, `SongEntity`, `PlaylistEntity`, `Download`
  (`STATE_STOPPED|QUEUED|DOWNLOADING|COMPLETED`), `Event`.
- `com.revenge.innertube.models.*`: `SongItem`, `AlbumItem`, `ArtistItem`, `PlaylistItem`,
  `BrowseEndpoint`, `WatchEndpoint`, `ItemsPage`, `Run`.
- `com.revenge.dsp.core.models.SavedEQProfile`, `ParametricEQBand`, `FilterType`.
- Style enums in `com.revenge.music.constants.PreferenceKeys`: `PlayerBackgroundStyle`
  (`DEFAULT, GRADIENT, BLUR, GLOW_ANIMATED, APPLE_MUSIC, LIVE_MESH, LIQUID_GLASS`),
  `PlayerButtonsStyle` (`DEFAULT, PRIMARY, TERTIARY`), `SliderStyle`
  (`DEFAULT, WAVY, SLIM`), `DarkMode` (`AUTO, ON, OFF`).
- `LyricsResult(providerName: String, lyrics: String)` from `:lyrics`.
- `PodcastProgress` (`SKIP_BACK_MS`, `SKIP_FORWARD_MS`, `speed: StateFlow<Float>`,
  `cycleSpeed()`, `speedLabel(Float)`, `get(mediaId)`, `isEpisode(metadata, durationMs)`).
- `Wikipedia.fetchArtistBio(name)`, `Wikipedia.fetchSongStory(title, artist, album)` returning a
  summary with `extract`, `title`, `thumbnail.source`.
- `YouTube.artist(id)`, `YouTube.getMediaInfo(id)` from `:innertube`.

### 4.4 Shared UI primitives and helpers

`BottomSheet`, `BottomSheetState` (with `progress`, `isExpanded`, `expandedBound`,
`expandSoft`, `collapseSoft`, `preUpPostDownNestedScrollConnection`, `rememberBottomSheetState`),
`MenuState`, `BottomSheetPageState`, `MediaMetadataListItem`, `YouTubeListItem`,
`ArtistListItem`, `Material3MenuItemData`, `Material3SettingsGroup`, `Material3SettingsItem`,
`ActionPromptDialog`, `DefaultDialog`, `TextButtonDialog`, `TextFieldDialog`, `IconButton`,
`AudioDeviceBottomSheet`, `ShowMediaInfo`, `CommentSheet`, `isBluetoothHeadphoneConnected`,
`ShimmerHost`, `TextPlaceholder`, `PlayerSliderColors`, `rememberPreference`,
`rememberEnumPreference`, `rememberSaveable`, `ListItemHeight`, `listItemShape`.

Preference keys read by this cluster include (non-exhaustive, names are the contract):
`PureBlackMiniPlayerKey`, `DarkModeKey`, `PureBlackKey`, `MiniPlayerBackgroundStyleKey`,
`PlayerBackgroundStyleKey`, `PlayerButtonsStyleKey`, `SliderStyleKey`/`isSquiggly` keys,
`ThumbnailCornerRadiusKey`, `CropAlbumArtKey`, `HidePlayerThumbnailKey`, `SwipeThumbnailKey`,
`SwipeSensitivityKey`, `ShowLyricsKey`, `ShowLyricsOnPlayerKey`, `UseNewPlayerDesignKey`,
`QueueEditLockKey`, `ShowCommentButtonKey`, `VarispeedKey`, `EnableExportAsMp3Key`,
`ExportDirectoryUriKey`, `ExportingSongIdsKey`, `ExportedSongIdsKey`,
`AddToPlaylistSortTypeKey`, `AddToPlaylistSortDescendingKey`, `InnerTubeCookieKey`,
`OpenRouterApiKey`, `DeeplApiKey`, `AiProviderKey`, `TranslateLanguageKey`,
`TranslateModeKey`, `AutoTranslateKey`, `OpenRouterBaseUrlKey`, `OpenRouterModelKey`,
`DeeplFormalityKey`, `MusicFlow*Key`, `ListenTogether*Key`, `DataSaverEnabledKey`.

---

## 5. Player surfaces

All player composables observe `LocalPlayerConnection` and return early (render nothing) when it
is null. All numbers displayed for time use a shared formatter (`makeTimeString`); durations are
milliseconds.

### 5.1 `ProgressState`

```
class ProgressState(positionState: MutableLongState, durationState: MutableLongState) {
  val progress: Float
}
```

- `progress = position / duration` clamped to `[0f, 1f]`; `0f` when `duration <= 0`.
- Reads live `MutableLongState`s on every access (no snapshot copy). It is a pure derived view;
  it must not mutate the states.

### 5.2 `MiniPlayer`

```
@Composable
fun MiniPlayer(
  positionState: MutableLongState,
  durationState: MutableLongState,
  modifier: Modifier = Modifier,
  onClick: () -> Unit = {},
)
```

Purpose: collapsed one-row now-playing bar.

State inputs: `mediaMetadata`, `playbackState`, `isPlaying`, `canSkipPrevious`, `canSkipNext`,
`isMuted`, cast state, listen-together guest flag, download/favorite state of the current song.

Visual inputs: `MiniPlayerBackgroundStyleKey` (`DEFAULT` follows theme, other styles use a
media-derived background), `PureBlackMiniPlayerKey`, `DarkModeKey`/`PureBlackKey`, glass-effect
config, `SwipeThumbnailKey`, `SwipeSensitivityKey`.

Behaviour:
- the whole bar invokes `onClick` (caller opens the expanded player) except child controls;
- artwork thumbnail, title and artist (marquee when too long); errors replace the artist line;
- previous button seeks previous media item, disabled when `canSkipPrevious` is false or the user
  is a listen-together guest; next button symmetrical;
- play/pause button: toggles play/pause; when playback state is `STATE_ENDED`, shows replay and
  seeks to 0 then plays; for a listen-together guest it toggles mute instead; while casting it
  controls the cast session;
- favourite button shown when a song id is available; toggles library/like;
- cast indicator with device name when casting;
- a 2 dp progress line at the bottom fills according to `ProgressState.progress`;
- horizontal swipe on the bar seeks previous/next when swipe is enabled and a neighbour exists.
  Gesture disambiguation: direction is normalized for RTL; a swipe only translates while it is
  permitted (at the first item, leftward swipes still accumulate so the bar can return to centre);
  commit when either (|offset| > 50 px AND velocity > sensitivity threshold) or
  |offset| > automatic threshold. The automatic threshold is derived monotonically from
  `SwipeSensitivityKey` in `[0,1]`; default 0.73 yields roughly 155–160 px.
- Tapping the row never triggers as part of a committed swipe.

Accessibility:
- previous, play/pause, next are buttons with content descriptions `previous_track`,
  `play`/`pause`/`replay`, `next_track`; guest variant uses `mute`/`unmute`;
- artwork has description `album_artwork`; title/artist text are normal semantic text;
- root row exposes a click action (open player) and a role of button.

Layout: max width 480 dp, fixed `MiniPlayerHeight`, horizontally inset for system bars, fully
rounded pill; in tablet-landscape the content is centred at 480 dp.

### 5.3 `FloatingMiniPlayer`

```
@Composable
fun FloatingMiniPlayer(
  isInline: Boolean,
  contentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
)
```

Purpose: compact now-playing accessory docked inside the floating navigation bar. `isInline`
selects the slim strip layout; otherwise a wide pill. Selection/playback/swipe semantics match
`MiniPlayer` (same swipe preferences and thresholds, guest restriction disables swiping). It
shows artwork, title (single line, ellipsis), and play/pause + next controls. Content colour is
provided by the host; when the supplied background is light the composable may darken text to
preserve contrast. Tapping outside controls calls `onClick`.

Accessibility: play/pause (`play`/`pause`) and next (`next_track`) buttons; artwork
`album_artwork`; root click opens the player.

### 5.4 `BottomSheetPlayer`

```
@Composable
fun BottomSheetPlayer(
  state: BottomSheetState,
  navController: NavController,
  modifier: Modifier = Modifier,
  pureBlack: Boolean,
  expandQueueRequested: Boolean = false,
  onQueueExpanded: () -> Unit = {},
  showPlayerMenuRequested: Boolean = false,
  onPlayerMenuShown: () -> Unit = {},
  showLyricsRequested: Boolean = false,
  onLyricsShown: () -> Unit = {},
)
```

Purpose: the full now-playing sheet; also renders the mini-player when collapsed.

Two visual modes selected by `UseNewPlayerDesignKey` (new = card/rounded pager design, old =
classic column). Both must satisfy the same contract.

State inputs: all `PlayerConnection` playback/queue/cast/sleep-timer/automix observables, plus
`ShowLyricsOnPlayerKey`, `PlayerBackgroundStyleKey`, `CanvasThumbnailAnimationKey`,
`DataSaverEnabledKey`, `UseNewPlayerDesignKey`, volume keys, equalizer/DSP availability.

Inputs → effects:
- `expandQueueRequested == true` expands the sheet and renders the queue page, then
  `onQueueExpanded()` (host clears the request).
- `showPlayerMenuRequested == true` expands and shows `PlayerMenu` for the current metadata; then
  `onPlayerMenuShown()`. No-op when metadata is null.
- `showLyricsRequested == true` expands and switches the artwork area to inline lyrics; then
  `onLyricsShown()`.

Content contract:
- background per `PlayerBackgroundStyle`: follow-theme, gradient, blur, animated glow, Apple
  Music canvas artwork (only when enabled and not data-saver/episode), live mesh, liquid glass;
  pure-black override when requested; local media forces black;
- collapsed state shows the `MiniPlayer` (classic) or the card mini-player;
- expanded state shows a `Thumbnail` carousel (or `InlineLyricsView` when toggled), title/artist
  with marquee and artist navigation, like and download actions, a more-actions button,
  a seek slider (style from slider preference) with elapsed/remaining time, previous/play/next,
  shuffle and repeat toggles, episode controls when the item is an episode, a queue button that
  opens the queue page, and optional comment button;
- top pill (`PlayerTopPill`) with `SONG`, `VIDEO`, `MUSIC_FLOW`: VIDEO navigates to
  `podcast_video` (single-top) and shows a toast for local tracks; MUSIC_FLOW navigates to
  `music_flow` (single-top) and collapses the sheet; the pill is hidden while inline lyrics are
  shown; episode items label the first tab "Episode";
- when `ShowLyricsOnPlayerKey` is on, a one-line synced lyric (`PlayerSyncedLyricsView`) appears
  below the artwork while inline lyrics are off and the item is not an episode;
- inline lyrics can be toggled from the artwork area; `InlineLyricsView` replaces the thumbnail;
- video episodes render a `CanvasArtworkPlayer` background;
- sleep timer dialog: start for N minutes or at end of track, show remaining time; cancelling
  stops the timer (`sleepTimer.start(-1)` semantics);
- automix: when there is no next item and automix candidates exist, the first candidate is
  appended after a grace period (~3 s constant) so playback continues; a crossfade/automix
  status label may be shown;
- casting: slider follows cast position/duration within ~1.5 s of manual seek; volume keys
  control cast volume; cast device name displayed;
- fullscreen toggle for video content;
- screen-on is held while playing when the keep-screen-on preference is enabled;
- the sheet intercepts back to collapse before the host handles it;
- expanded queue page is a nested sheet anchored at `QueuePeekHeight` and can be swiped between
  collapsed/expanded anchors.

Accessibility: slider exposes a progress/seek role with `seek` semantics and state description
of elapsed/remaining; transport buttons use `previous_track`, `play`/`pause`, `next_track`;
shuffle `shuffle`, repeat `repeat`; queue button `next_in_queue`/`queue`; more `more_options`;
like `like`/`remove_like`; download `action_download`/`remove_download`; cast
`casting_to <device>`; sleep timer `sleep_timer`; inline lyrics toggles announce `lyrics`.

### 5.5 `PlayerTopPill` and `PlayerPillOption`

```
enum class PlayerPillOption { SONG, VIDEO, MUSIC_FLOW }

@Composable
fun PlayerTopPill(
  selectedOption: PlayerPillOption,
  onOptionSelected: (PlayerPillOption) -> Unit,
  isLightBackground: Boolean,
  modifier: Modifier = Modifier,
  musicFlowEnabled: Boolean = true,
  firstTabTitle: String = "Song",
  firstTabIcon: Int = R.drawable.music_note,
)
```

- Rounded translucent pill with one tab per option; `MUSIC_FLOW` hidden when
  `musicFlowEnabled == false`; `firstTabTitle`/`firstTabIcon` override the SONG tab (episode).
- `isLightBackground` selects a darker translucent container for contrast.
- Each tab is a toggle with selected semantics; tapping emits the option. Content descriptions
  are the tab titles.

### 5.6 `Thumbnail`

```
@Composable
fun Thumbnail(
  sliderPositionProvider: () -> Long?,
  modifier: Modifier = Modifier,
  isPlayerExpanded: () -> Boolean = { true },
  isLandscape: Boolean = false,
  isListenTogetherGuest: Boolean = false,
)
```

Purpose: swipeable artwork carousel for the current queue window.

State inputs: `mediaMetadata`, `error`, `queueTitle`, `canSkipPrevious`, `canSkipNext`,
`SwipeThumbnailKey`, `HidePlayerThumbnailKey`, `CropAlbumArtKey`, `PlayerBackgroundStyleKey`,
`ThumbnailCornerRadiusKey`.

Behaviour:
- horizontally snapping grid of up to a small neighbours window around the current index
  (current ±1 when swipe enabled, current only otherwise); current item centred;
- swiping to a neighbour and settling past the midpoint, or a fling, seeks next/previous when
  allowed; the list animates back to the current index when playback changes externally;
- tap toggles play/pause when expanded; long-press shows the player menu; horizontal drag on the
  artwork shows a seek-forward/seek-backward overlay and commits a relative seek using the
  supplied slider position or current position; the overlay text announces
  `seek_forward_dynamic`/`seek_backward_dynamic`;
- hidden-thumbnail preference replaces artwork with a placeholder; crop preference changes
  scaling;
- error state replaces the artwork with `PlaybackError` (clicking retry re-prepares playback);
- canvas/video artwork (`CanvasArtwork` from Media3) may be rendered when available;
- title and artist are overlaid and normalized (see 5.7); long titles marquee; clicking the artist
  opens the artist page.

Accessibility: artwork has `album_artwork`/title semantics, the swipe CTA is exposed as
`seek_backward_dynamic`/`seek_forward_dynamic` while dragging; the retry button is `retry`.

`ThumbnailDimensions(itemWidth, containerSize, thumbnailSize, cornerRadius)`: immutable geometry
model. `itemWidth == containerSize == containerWidth`; `thumbnailSize = width - 2*horizontalPadding`
(landscape uses `min(width,height)`), `cornerRadius = 2 * requested radius`. It is a pure function
of inputs.

`MediaItemsData(items: List<MediaItem>, currentIndex: Int)`: immutable snapshot of the carousel
window. `currentIndex` is the index inside `items`, or a negative value when there is no current
item.

`CanvasArtworkPlaybackCache`: process-wide LRU keyed by media id.
- `get(mediaId): CanvasArtwork?`, `put(mediaId, artwork)`, `clear()`, default capacity 256.
- Blank media ids are never stored. Capacity `<= 0` makes `get` always return null and `put` a
  no-op. Access must be thread-safe and evict least-recently-used entries.

### 5.7 Thumbnail text normalization (internal)

- `normalizeCanvasSongTitle(raw): String` — trims, removes common YouTube suffixes
  (`(Official Video)`, `[Official Music Video]`, `(Lyrics)`, `(Audio)`, feat./ft. annotations where
  applicable) and collapses whitespace. Returns the trimmed input when no rule matches.
- `normalizeCanvasArtistName(raw): String` — trims, removes `- Topic`, `VEVO` suffix and
  surrounding punctuation, collapses whitespace.
- `splitAndNormalizeArtists(raw): List<String>` — splits on comma, ampersand, `feat.`, `ft.`,
  `&`, `x`, `vs.` separators (case-insensitive), applies artist normalization, drops blanks and
  duplicates while preserving first-seen order.
These are pure, deterministic and independently testable.

### 5.8 `CanvasArtworkPlayer`

```
@Composable
fun CanvasArtworkPlayer(
  primaryUrl: String?,
  fallbackUrl: String?,
  isPlaying: Boolean,
  clipStartMs: Long? = null,
  clipEndMs: Long? = null,
  modifier: Modifier = Modifier,
)
```

Purpose: looping canvas/video artwork behind the player. Blank/null urls after fallback → renders
nothing. Chooses primary, falls back on load failure. Video is muted, loops within
`[clipStartMs, clipEndMs]` (whole clip when null), pauses when `isPlaying` is false, resumes when
true, scales centre-crop into the bounds, and releases the player/texture on dispose. It must not
crash on malformed urls; on failure it simply stops rendering video. No accessibility node (it is
decorative).

### 5.9 `PlayerSyncedLyricsView`

```
@Composable
fun PlayerSyncedLyricsView(
  mediaMetadata: MediaMetadata?,
  positionProvider: () -> Long,
  modifier: Modifier = Modifier,
)
```

Purpose: one-line karaoke lyric, usually under the artwork.

- Reads `playerConnection.currentLyrics`; if null, repairs missing lyrics by loading from the
  database/lyrics helper after a short delay (~500 ms) when metadata is present (no crash on
  failure).
- Parses timestamped lyrics; blank lines dropped; sentinel `LyricsEntity.LYRICS_NOT_FOUND` and
  empty text render no line.
- `positionProvider()` is polled each frame; the active line is the last line whose timestamp is
  `<= position` (first line when before the first timestamp).
- Loading → shimmer placeholder. No lyrics → empty (zero height). Otherwise an animated line
  swap (fade/slide) with white bold 18 sp left-aligned text.
- Accessibility: expose the current line as text; decorative loading placeholder hidden from
  semantics.

### 5.10 `InlineLyricsView`

```
@Composable
fun InlineLyricsView(
  mediaMetadata: MediaMetadata?,
  showLyrics: Boolean,
  modifier: Modifier = Modifier,
  positionProvider: () -> Long,
)
```

Purpose: full-height lyrics surface shown in place of the thumbnail. `showLyrics == false` renders
nothing. It resolves/repairs lyrics like 5.9, scrolls the active line into view, allows manual
scroll (temporarily suspending auto-follow) and tapping a line seeks to its timestamp via
`playerConnection.seekTo`. Empty/unavailable lyrics show `lyrics_not_found`. Long-press a line may
open the lyrics menu. Accessibility: each line is selectable text; the active line is marked as
selected and announced.

### 5.11 `Queue`

```
@Composable
fun Queue(
  state: BottomSheetState,
  playerBottomSheetState: BottomSheetState,
  navController: NavController,
  modifier: Modifier = Modifier,
  background: Color,
  onBackgroundColor: Color,
  TextBackgroundColor: Color,
  textButtonColor: Color,
  iconButtonColor: Color,
  pureBlack: Boolean,
  showInlineLyrics: Boolean,
  playerBackground: PlayerBackgroundStyle = PlayerBackgroundStyle.DEFAULT,
  isEpisode: Boolean = false,
  onToggleLyrics: () -> Unit = {},
)
```

Purpose: the player queue sheet: "next in queue", playback order, sleep timer and queue editing.

State inputs: `player.currentTimeline`/`queueWindows`, `queueTitle`, `mediaMetadata`,
`currentSong`, `shuffleModeEnabled`, `repeatMode`, `isEffectivelyPlaying`, `automixItems`,
selection mode, `QueueEditLockKey`, cast/listen-together state.

Behaviour:
- header shows queue title, item count, shuffle/repeat toggles, sleep-timer entry, and a close
  action; a queue-lock toggle enables/disables reordering;
- list starts at the current item; items before it are visually muted; current item highlighted;
  tapping an item seeks/plays it; long-press enters selection mode;
- reorderable drag handles are present only when unlocked and not in selection mode; a reorder
  commits to the player's timeline (`move` semantics) and survives repeat/shuffle rules;
- swipe-to-dismiss removes an item and shows an undo snackbar ("removed_from_queue" + Undo);
- selection mode: multi-select items, with actions to play, play next, add to queue, download,
  remove from queue, like/dislike, add to playlist; back exits selection mode and clears it;
- "clear queue" asks for confirmation, clears the queue (keeping the current item where the
  underlying service semantics dictate), and offers an undo snackbar that restores the exact
  snapshot; snackbar action labels `clear`/`undo`;
- automix candidates are shown below a divider with their own header;
- when `showInlineLyrics` is true, the lyrics affordance is active (`onToggleLyrics`);
- when `isEpisode`, episode-specific labelling/controls are used;
- listen-together guests cannot reorder/remove or skip; those affordances are disabled;
- audio-device bottom sheet opens from a device affordance when Bluetooth is connected; the sheet
  unregisters receivers/audio callbacks on dispose;
- sleep timer countdown recomputes every second from either wall-clock trigger time or remaining
  track duration for "pause at end of song".

Accessibility: every row is a clickable list item with title/artist text and a "more options"
action (`more_options`); reorder handles expose move-up/move-down custom actions when reordering
is locked (or a disabled state); selection uses checkbox semantics with a state description;
snackbar actions are reachable and focusable.

### 5.12 `SeekButtons`

```
@Composable
fun SeekButtons(enabled: Boolean, color: Color, onSeek: (Long) -> Unit)
```

Two 48 dp-minimum text buttons labelled "−10 s" / "+10 s" that call `onSeek(-10_000L)` and
`onSeek(+10_000L)`. Disabled when `enabled == false`. Accessibility: each button clears its visual
text semantics and sets `seek_back_ten_seconds` / `seek_forward_ten_seconds`.

### 5.13 `EpisodeControls`

```
@Composable
fun EpisodeControls(enabled: Boolean, color: Color, onSeek: (Long) -> Unit)
```

Three controls: skip back by `PodcastProgress.SKIP_BACK_MS`, current podcast speed label (tap
cycles speed via `PodcastProgress.cycleSpeed()` and the choice persists for all episodes), skip
forward by `PodcastProgress.SKIP_FORWARD_MS`. Disabled when `enabled == false`. Labels from
`episode_skip_back`, `episode_skip_forward`, and the speed formatter.

### 5.14 `PlaybackError`

```
@Composable
fun PlaybackError(error: PlaybackException, retry: () -> Unit, modifier: Modifier = Modifier)
```

- Derives a user message, in priority order:
  1. server busy — HTTP 429, any 5xx, or message contains "Too Many Requests"/"Service
     Unavailable": "streaming server is busy (HTTP …), retry shortly; the track is fine";
  2. age restricted — only when not server-busy and the message contains "confirm your age",
     "Sign in to confirm your age", "LOGIN_REQUIRED", or "age-restricted/age restricted";
  3. network error — IO network connection failed/timeout or message contains
     SocketTimeoutException/ConnectException/UnknownHostException;
  4. HTTP 403 — "stream link expired, tap Retry";
  5. otherwise the raw message, and `error_unknown` when none exists.
- Renders an error icon, title `error_playback_failed`, the message (max 3 lines), a monospace
  code line `Code: <NAME> (<int>)`, and a `retry` button invoking `retry`.
- Error-code name mapping covers all Media3 `PlaybackException` constants (UNSPECIFIED,
  REMOTE_ERROR, BEHIND_LIVE_WINDOW, TIMEOUT, …) and returns a stable uppercase identifier.
- Accessibility: retry is a button named `retry`; diagnostic code text is readable.

---

## 6. Menus

All menus are shown inside `MenuState`/dialog hosts and call their `onDismiss` when an action
completes or a destructive dialog is dismissed. Every destructive action (delete, clear, remove
download, remove from library/history) must use a confirmation dialog with `cancel` plus a
destructive confirm label. Action labels come from the string resources in section 6.1.

### 6.1 Menu catalog

| Composable | Signature (contract) | Behaviour |
|---|---|---|
| `PlayerMenu` | `(mediaMetadata: MediaMetadata?, navController, playerBottomSheetState, isQueueTrigger: Boolean? = false, isQueueLocked: Boolean? = null, inSelectMode: Boolean = false, onShowDetailsDialog: () -> Unit, onClearQueue: (() -> Unit)? = null, onDismiss: () -> Unit)` | Now-playing actions. Returns immediately for null metadata. `isQueueTrigger == true` suppresses the cast banner and queue-specific items; `isQueueLocked` reflects queue lock where shown. Includes starting radio, add to playlist, share, artist/album navigation, library toggle, download/export/remove-download, listen together, resync/refetch, details (`onShowDetailsDialog`), Song DNA, equalizer (`navigate("equalizer")`), advanced (ambient mode `navigate("ambient_mode")`, speed/tempo/pitch dialogs), clear queue (when `onClearQueue` provided), sleep timer. |
| `OldPlayerMenu` | `(mediaMetadata, navController, playerBottomSheetState, onShowDetailsDialog, onDismiss)` | Legacy player menu with equivalent actions: like, shuffle, repeat, radio, artist navigation, library toggle, download/export, listen together, refetch, details, equalizer, ambient mode, sleep timer, Song DNA. |
| `QueueMenu` | `(mediaMetadata, navController, playerBottomSheetState, onShowDetailsDialog, onDismiss)` | Per-queue-item menu: play next, add to queue, add to playlist, download/remove, radio, share, refetch, details, artist/album navigation. |
| `LyricsMenu` | `(lyricsProvider: () -> LyricsEntity?, songProvider: () -> SongEntity?, mediaMetadataProvider: () -> MediaMetadata, onDismiss, onShowOffsetDialog: () -> Unit = {}, viewModel: LyricsMenuViewModel = hiltViewModel())` | Lyrics search/management: shows current provider/lyrics status, online search results from the VM, search/import/refetch, AI translate/romanize using provider keys, edit offset (`onShowOffsetDialog`), save/delete lyrics. Offline (`isNetworkAvailable == false`) disables online actions and shows `error_no_internet`; empty results show `lyrics_not_found`. |
| `SongMenu` | `(originalSong: Song, event: Event? = null, navController, playlistSong: PlaylistSong? = null, playlistBrowseId: String? = null, onDismiss, isFromCache: Boolean = false)` | Local song menu: play/next/queue, add to playlist, library, like, download/export/remove, cache/history removal, radio, share, refetch, edit, artist/album navigation, hide/show from home, suggest to host (listen together). Observes live DB song and download state. |
| `AlbumMenu` | `(originalAlbum: Album, navController, onDismiss)` | Album: play/shuffle/queue/next, add to playlist, library, download/remove, radio, share, refetch, view artist. Guest restrictions apply. |
| `ArtistMenu` | `(originalArtist: Artist, coroutineScope, onDismiss)` | Artist: play/shuffle, share, subscribe/unsubscribe (library). Observes pin state and library artist. |
| `PlaylistMenu` | `(playlist: Playlist, coroutineScope, onDismiss, autoPlaylist: Boolean? = false, downloadPlaylist: Boolean? = false, songList: List<Song>? = emptyList())` | Local playlist: play/shuffle/queue/next, edit/delete, download/remove, export, radio, share, pin/unpin, YouTube sync (signed-in only; progress state), add to queue. |
| `LocalPlaylistMenu` | `(playlist, songs: List<PlaylistSong>, context, downloadState: Int, onEdit, onSync, onDelete, onDownload, onExport, onModifyWithAi, onQueue, onDismiss)` | Screen-level local playlist menu; delegates to host callbacks. |
| `AutoPlaylistMenu` | `(downloadState: Int, onQueue, onDownload, onDismiss)` | Auto-playlist: queue, download/remove. |
| `TopPlaylistMenu` | `(downloadState: Int, onQueue, onDownload, onDismiss)` | Top playlist: queue, download/remove. |
| `CachePlaylistMenu` | `(downloadState: Int, onQueue, onDownload, onDismiss)` | Cache playlist: queue, download/remove. |
| `SelectionSongMenu` | `(songSelection: List<Song>, onDismiss, clearAction, songPosition: List<PlaylistSongMap>? = emptyList())` | Multi-select local songs: play, shuffle, play next, add to queue, like/dislike all, library add/remove, download/remove, add to playlist; reads aggregate download state. |
| `SelectionMediaMetadataMenu` | `(songSelection: List<MediaMetadata>, currentItems: List<Timeline.Window>, onDismiss, clearAction)` | Multi-select queue items: same actions plus remove from the supplied queue selection. |
| `YouTubeSongMenu` | `(song: SongItem, navController, onDismiss, onHistoryRemoved: () -> Unit = {}, showSongHeader: Boolean = true)` | Online song: play/next/queue, add to playlist, library, download/remove, export, radio, share, history removal, pin/unpin speed dial, details, artist/album navigation, suggest to host. |
| `YouTubeAlbumMenu` | `(albumItem: AlbumItem, navController, onDismiss)` | Online album: play/shuffle/queue/next, add to playlist, library, download/remove, share, view artist; fetches and inserts missing album into DB. |
| `YouTubeArtistMenu` | `(artist: ArtistItem, onDismiss)` | Online artist: share, radio, subscribe/unsubscribe, pin state. |
| `YouTubePlaylistMenu` | `(playlist: PlaylistItem, songs: List<SongItem> = emptyList(), coroutineScope, onDismiss, selectAction: () -> Unit = {}, canSelect: Boolean = false)` | Online playlist: play/shuffle/queue/next, add to playlist, download/remove, export, share, select (`selectAction`, `canSelect`), radio. |
| `YouTubeSelectionSongMenu` | `(songSelection: List<SongItem>, onDismiss, clearAction)` | Online multi-select: play, shuffle, queue all, next, queue, like/dislike all, library, download/remove, add to playlist. |
| `AddToPlaylistDialog` | `(isVisible: Boolean, allowSyncing: Boolean = true, initialTextFieldValue: String? = null, onGetSong: suspend (Playlist) -> List<String>, onDismiss, viewModel: PlaylistsViewModel = hiltViewModel())` | Local add-to-playlist picker: search, sort by name/created/last-updated/song-count, create playlist, duplicate detection (single/multi messages, "Add anyway"/"Skip duplicates"), loading + empty (`no_results_found`) states, toast `added_to_playlist`/`removed_song_from_playlist`. `allowSyncing` controls the synced-playlist note. |
| `AddToPlaylistDialogOnline` | `(isVisible, allowSyncing = true, initialTextFieldValue: String? = null, songs: SnapshotStateList<Song>, onDismiss, onProgressStart: (Boolean) -> Unit, onPercentageChange: (Int) -> Unit, viewModel: PlaylistsViewModel = hiltViewModel())` | Online equivalent: resolves each item, reports progress start/percentage to the host, same sort/create/duplicate behaviour, "liked songs" virtual playlist. |
| `CsvColumnMappingDialog` | `(isVisible: Boolean, csvState: CsvImportState, onDismiss: () -> Unit, onConfirm: (CsvImportState) -> Unit)` | CSV column mapper: choose song title / artist / optional URL columns, first-row-is-header toggle, live preview of the first rows, validation that required columns are selected, then `onConfirm` with the edited state. |
| `CsvImportProgressDialog` | `(isVisible: Boolean, progress: Int, recentLogs: List<ConvertedSongLog>, onDismiss: () -> Unit)` | Non-dismissible progress dialog (back/outside disabled) showing percent and recent conversions; only `onDismiss` (when allowed by host) closes it. |
| `ImportPlaylistDialog` | `(isVisible: Boolean, onGetSong: suspend () -> List<String>, playlistTitle: String, onDismiss)` | Text dialog to name an imported playlist, then inserts a playlist from the supplied song ids. |
| `CustomThumbnailMenu` | `(onEdit: () -> Unit, onRemove: () -> Unit, onDismiss)` | Two items: choose from library (`onEdit`), remove custom thumbnail (`onRemove`). |
| `LoadingScreen` | `(isVisible: Boolean, value: Int)` | Blocking dialog with a progress percentage; cannot be dismissed by the user. |
| `SpeedDialog` | `(onDismiss)` | Playback-speed picker; values persist and apply immediately. |
| `TempoPitchDialog` | `(onDismiss)` | Tempo and pitch adjusters built on `ValueAdjuster`, with reset; applies in varispeed mode. |
| `ValueAdjuster<T>` | `(@DrawableRes icon: Int, currentValue: T, values: List<T>, onValueUpdate: (T) -> Unit, valueText: (T) -> String, modifier = Modifier)` | Generic previous/next stepper; disables at list ends and emits the selected value. |
| `ListenTogetherDialog` | `(visible: Boolean, mediaMetadata: MediaMetadata?, onDismiss)` | Create/join room, connection status, participant list, approve/reject/kick/block/transfer-ownership, room settings; reads the manager's flows. |

### 6.2 Menu invariants

- Null metadata parameters render nothing (no crash).
- Guest (listen-together restricted) users must not see or be able to invoke playback-changing
  actions (play next, add to queue, skip, radio, suggest-to-host where inappropriate); download,
  library and share may remain enabled unless the host disables them.
- Menus never mutate the database on the UI thread; suspend work runs off-main and failures are
  reported via toast/snackbar, not crashes.
- Every menu is a scrolling list and respects system-bar bottom insets.
- Every actionable row has a leading icon (decorative, `contentDescription = null`) and text;
  toggles expose checked state; destructive items are tinted with the error colour.

---

## 7. Equalizer

### 7.1 `EQState`

```
data class EQState(
  val profiles: List<SavedEQProfile> = emptyList(),
  val activeProfileId: String? = null,
  val importStatus: String? = null,
  val error: String? = null,
)
```

Immutable UI state. `activeProfileId == null` means equalization is off.

### 7.2 `EQViewModel` (`@HiltViewModel`, constructor-injected `EQProfileRepository`, `DspController`)

Observable: `state: StateFlow<EQState>`.

Functions and contracts:
- `selectProfile(profileId: String?)` — null disables the equalizer (`dspController.disableEqualizer()`
  and repository active profile cleared); non-null finds the profile, applies it through
  `DspController.applyProfile`, and on success records it active, on failure writes `error` and
  leaves the previous active profile unchanged.
- `clearError()` — clears `error`.
- `deleteProfile(profileId: String)` — deletes; deleting the active profile must not leave a stale
  `activeProfileId` (repository update may set it null or the next active).
- `importCustomProfile(fileName, inputStream, onSuccess, onError)` — reads the whole stream,
  parses with `ParametricEQParser.parseText`, validates; invalid → `onError` and no profile
  created; valid → stores a custom profile named after the file without `.txt`, sets
  `importStatus`, calls `onSuccess`. Exceptions never escape; they route to `onError`.

Init: observes `eqProfileRepository.profiles` (sorted list into `profiles`) and
`eqProfileRepository.activeProfile` (id into `activeProfileId`).

### 7.3 `EqScreen`

```
@Composable
fun EqScreen(
  navController: NavController? = null,
  viewModel: EQViewModel = hiltViewModel(),
  playbackState: PlaybackState? = null,
)
```

Purpose: legacy profile picker, hosted as the `dialog("equalizer")` route.

Content:
- list of profiles; "No equalization" row selected when `activeProfileId == null`;
- each profile row shows device model, band count plural, radio selection, delete affordance with
  confirmation;
- actions: import a custom EQ text file (system file picker, `text/plain`), open the system
  audio-effects panel (`AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL` with the player's
  audio session, package name and music content type; only when an activity resolves), navigate
  to `settings/equalizer` (Axion);
- loading: `profiles` empty shows only the disabled row; file-read/parse failures show an error
  dialog (`import_error_title`); DSP apply failures show `error_eq_apply_failed` with the message
  and an OK action that calls `clearError`.
- Accessibility: rows are selectable radio items; delete has `delete_profile_desc`; the profile
  name and band count are announced together.

### 7.4 `AxionEqViewModel` (`@HiltViewModel`; `@ApplicationContext`, `DspController`, `EQProfileRepository`)

Observable:
- `enabled: StateFlow<Boolean>` — initial value from `revenge_eq_prefs` `enabled`, default false;
- `bandGains: StateFlow<FloatArray>` — 10 bands, persisted as `band_<i>`, default 0f;
- `mode: StateFlow<Int>` — 0 = simple, 1 = advanced, persisted as `mode`;
- `isDirty: StateFlow<Boolean>` — true after a user band change; false after save/mode change;
- `customProfiles: StateFlow<List<SavedEQProfile>>` — repository profiles filtered to
  `isCustom && id != "revenge_tuning"`.

Band centre frequencies (Hz), in order: 31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000.
Gain mapping to the DSP model: `gain = uiGain / 50.0` (UI range −10..+10 → −0.2..+0.2 dB in the
parametric model), `q = 1.41`, `filterType = PK`, all bands enabled.

Functions:
- `setEnabled(Boolean)` — persists and, when enabled, applies the current tuning; when disabled,
  clears the active profile and calls `disableEqualizer`.
- `setMode(Int)` — persists; clears dirty.
- `setBandGain(index, gain)` — clamps index to `0..9`, persists, marks dirty, re-applies when
  enabled.
- `setBandsGains(gains, fromUser = false)` — replaces all 10, persists each, dirty = `fromUser`,
  re-applies when enabled.
- `reset()` — all gains 0.
- `saveCustomProfile(name)` — creates a `SavedEQProfile` id `custom_<millis>`, deviceModel
  "Equalizer", current bands, `preamp = 0.0`, `isCustom = true`, `isActive = true`; saves and
  activates it; clears dirty.
- `deleteProfiles(ids)` — deletes each.
- On init, if enabled, applies the stored tuning to the service.
- Applying writes a built-in profile id `revenge_tuning` named "Revenge Tuning", then activates it
  and applies it to `DspController`.

Invariant: persistence is synchronous to SharedPreferences (`apply`) before the flow value is
exposed; a process restart restores the same band gains, mode and enabled flag. Custom profiles
survive independently through `EQProfileRepository`.

### 7.5 `AxionEqScreen`

```
@Composable
fun AxionEqScreen(onBackClick: () -> Unit, viewModel: AxionEqViewModel = hiltViewModel())
```

Purpose: route `settings/equalizer`.

Sections, top to bottom:
- top app bar title `revenge_equalizer` with back (`onBackClick`);
- enable switch bound to `enabled`; toggling calls `setEnabled`, switch thumb icon check/close;
- "AutoEq Profiles" row opens `AutoEqBottomSheet`; applying results enables EQ, switches to
  advanced mode and writes all ten gains;
- simple/advanced toggle group; each option has radio-button semantics;
- simple mode: `CircularEqControl` plus a save-preset action enabled when dirty;
- advanced mode: ten vertical band sliders (frequency label + gain dB value, range −10..+10),
  reset action;
- save-preset dialog asks for a name and calls `saveCustomProfile`;
- manage-presets dialog lists custom profiles and allows multi-select delete with confirmation;
- layout is vertically scrollable and respects insets.

Loading/empty: no custom profiles → `eq_no_custom_presets` message in the manage dialog.

Accessibility: switch has an on/off state; mode toggles expose radio selection; each slider
exposes its band frequency as label and the current dB as value/state description, adjustable via
accessibility actions; delete actions are labelled `delete_profile_desc`.

### 7.6 `AutoEqBottomSheet`

```
@Composable
fun AutoEqBottomSheet(onDismissRequest: () -> Unit, onApplyEq: (FloatArray) -> Unit)
```

- Modal bottom sheet occupying ~90% height. Loads the bundled `raw/autoeq_index` JSON
  (`AutoEqItem(name, path)` list) off the main thread. Failure → empty list, no crash.
- Search field filters case-insensitively, capping at 100 rows; blank query shows the first 100.
- Selecting a row downloads the AutoEq profile from its `path` via URL, converts the parametric
  bands to the 10-band UI gain array (UI gain = dB × 50, clamped to −10..+10) and calls
  `onApplyEq`. While downloading/parsing shows progress and disables re-entry; parse/network
  errors surface an inline error and keep the sheet open.
- Empty search result shows a no-results message. Back/drag dismisses via `onDismissRequest`.

### 7.7 `CircularEqControl`

```
@Composable
fun CircularEqControl(
  bass: Float, mid: Float, treble: Float, enabled: Boolean,
  onBassChange: (Float) -> Unit, onMidChange: (Float) -> Unit, onTrebleChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
)
```

Canvas three-axis control (Bass / Mid / Treble at −90°, 30°, 150°). Touch picks the nearest axis
when inside 48% of the radius; otherwise no axis. Dragging along an axis maps the projection to
−10..+10 (clamped) and emits that axis's callback continuously; values outside the ring clamp.
Disabled state ignores gestures and dims the drawing. Draws axis lines, value labels
(`eq_label_bass/mid/treble`) and numeric values. Accessibility: expose three adjustable virtual
nodes (or a grouped control) whose actions report the bass/mid/treble values and allow
increment/decrement, with labels matching the drawn labels.

---

## 8. Shared components

### 8.1 `SongDnaState`

```
data class SongDnaState(
  val isLoading: Boolean = true,
  val trackTitle: String,
  val albumTitle: String? = null,
  val albumThumbnailUrl: String? = null,
  val releaseDate: String? = null,
  val performers: List<String> = emptyList(),
  val songwriters: List<String> = emptyList(),
  val producers: List<String> = emptyList(),
  val recordLabel: String? = null,
  val artistName: String? = null,
  val artistId: String? = null,
  val artistAvatarUrl: String? = null,
  val artistBio: String? = null,
  val artistListenersOrSubs: String? = null,
  val songStory: String? = null,
  val songStoryTitle: String? = null,
)
```

Initial state is seeded synchronously from `MediaMetadata` (title, album, thumbnail, performer
names, first artist name/id) with `isLoading = true`.

### 8.2 `SongDnaBottomSheet`

```
@Composable
fun SongDnaBottomSheet(
  mediaMetadata: MediaMetadata,
  navController: NavController,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
)
```

Purpose: modal "Song DNA" sheet combining artist profile, official credits and song background.

Resolution order (all best-effort, failures degrade silently):
1. artist page from `YouTube.artist(id)` for avatar, monthly listeners/subscriber text and bio;
2. official media description parsed for credits — performers, songwriters, producers, record
   label, release date;
3. Wikipedia artist bio/thumbnail when no YouTube bio;
4. Wikipedia song story (`songStory`, `songStoryTitle` default "About the Song").

Merge rules: performers = metadata artist names ∪ parsed performers, distinct; songwriters fall
back to metadata artist names when none parsed; album art falls back to the track thumbnail.

States: loading (skeleton/shimmer while `isLoading`), partial (only some sections rendered;
empty sections omitted), full. Artist tap navigates to `artist/<id>` when the id is non-null.
Dismiss via drag/back/close calls `onDismiss`. Accessibility: section headings are semantic
headings, credit rows read "role: value", artist avatar is decorative, close button is labelled.

### 8.3 Appearance preview and style modals

```
@Composable fun playerBackgroundStyleLabel(style: PlayerBackgroundStyle): String
@Composable fun playerButtonsStyleLabel(style: PlayerButtonsStyle): String

@Composable fun FullPlayerPreview(
  backgroundStyle: PlayerBackgroundStyle, buttonStyle: PlayerButtonsStyle,
  sliderStyle: SliderStyle, isSquiggly: Boolean, wavyPlayPause: Boolean,
  thumbnailCornerRadius: Dp, cropAlbumArt: Boolean, fontFamily: FontFamily,
  modifier: Modifier = Modifier)

@Composable fun MiniPlayerPreview(backgroundStyle: PlayerBackgroundStyle, modifier: Modifier = Modifier)

@Composable fun PlayerBackgroundStyleModal(
  current, availableStyles: List<PlayerBackgroundStyle>, onSelect: (PlayerBackgroundStyle) -> Unit,
  onDismiss, buttonStyle, sliderStyle, isSquiggly, wavyPlayPause,
  thumbnailCornerRadius: Dp, cropAlbumArt: Boolean, fontFamily: FontFamily)

@Composable fun PlayerButtonsStyleModal(
  current, availableStyles: List<PlayerButtonsStyle>, onSelect, onDismiss,
  backgroundStyle, sliderStyle, isSquiggly, wavyPlayPause,
  thumbnailCornerRadius: Dp, cropAlbumArt: Boolean, fontFamily: FontFamily)

@Composable fun MiniPlayerBackgroundStyleModal(
  current, availableStyles: List<PlayerBackgroundStyle>, onSelect, onDismiss)

fun Modifier.fadingEdges(top: Boolean, bottom: Boolean, edgeHeight: Dp = 24.dp, color: Color): Modifier
fun Modifier.drawGridScrollbar(state: LazyGridState, thumbColor: Color,
  trackColor: Color = Color.Transparent, width: Dp = 3.dp, paddingEnd: Dp = 2.dp): Modifier
fun Modifier.drawListScrollbar(state: LazyListState, thumbColor: Color,
  trackColor: Color = Color.Transparent, width: Dp = 3.dp, paddingEnd: Dp = 2.dp): Modifier
```

Labels: DEFAULT→`follow_theme`, GRADIENT→`gradient`, BLUR→`player_background_blur`,
GLOW_ANIMATED→`glow_animated`, APPLE_MUSIC→`apple_music`, LIVE_MESH→`live_mesh`,
LIQUID_GLASS→`player_background_liquid_glass`; buttons DEFAULT→`default_style`,
PRIMARY→`primary_color_style`, TERTIARY→`tertiary_color_style`.

`FullPlayerPreview` is a non-interactive mock of the expanded player that reflects all supplied
style inputs (background, button colours, slider style and squiggle, wavy play/pause, corner
radius, crop, font). `MiniPlayerPreview` mocks the mini-player background style. Both use the
`preview_song_title`/`preview_song_artist` strings, are decorative (not focusable) and must render
under any theme.

Style modals: scrollable grids/lists of every `availableStyles` entry, current entry marked
selected, each cell shows the corresponding preview and label, tapping emits `onSelect` then
`onDismiss`. The grid modals show fading top/bottom edges and a thin scrollbar when content
overflows; the scroll hint disappears once the user scrolls. Accessibility: each cell is a
selectable button whose label is the style name and whose selected state is exposed; the close
action is labelled `cancel`/close.

---

## 9. ViewModels — observable state and public functions

All are `ViewModel`s; the `@HiltViewModel` ones are constructed by Hilt. Public properties are
`StateFlow`/`MutableStateFlow`/Compose `State` unless noted. Only the fields the UI observes are
listed; private backing flows are implementation detail.

### 9.1 Playback-adjacent view-models

**`PlaylistsViewModel`** (`@HiltViewModel`; context, database, syncUtils)
- `allPlaylists: StateFlow<List<Playlist>>` — database playlists ordered by the persisted
  `AddToPlaylistSortTypeKey` + `AddToPlaylistSortDescendingKey`, default sort CREATE_DATE,
  descending true.
- `suspend fun sync()` — syncs saved playlists.

**`LyricsMenuViewModel`** (`@HiltViewModel`; lyricsHelper, database, networkConnectivity)
- `results: MutableStateFlow<List<LyricsResult>>`, `isLoading: MutableStateFlow<Boolean>`,
  `isNetworkAvailable: StateFlow<Boolean>`, `currentSong: State<Song?>`.
- `setCurrentSong(song)`, `search(mediaId, title, artist, duration, album: String? = null)` —
  clears results, streams results in as each provider returns, ends loading; a new search cancels
  the prior job.
- `cancelSearch()`, `refetchLyrics(mediaMetadata, lyricsEntity)` — deletes the old row and upserts
  the freshly resolved lyrics/provider.
- Network availability starts from the observer snapshot; observer failure defaults to `true`.

**`MusicFlowViewModel`** (`@HiltViewModel`; context, MusicFlowSession, database, lyricsHelper)
- `preferences: StateFlow<MusicFlowPreferences>` (defaults from section 4.3/`MusicFlowPreferences`).
- `uiState: StateFlow<MusicFlowUiState>` combining feed state, playback status and restore
  availability. `MusicFlowUiState(items, selectedIndex, isInitialLoading, isLoadingMore, error,
  hasMore, playbackStatus, canRestorePreviousQueue, isOfflineFeed)`; derived `selectedSongId`.
  `MusicFlowPlaybackStatus` = Idle | Switching | Synced | Blocked(reason: TakeoverBlock) |
  ExternalOverride; `TakeoverBlock` = CASTING | LISTEN_TOGETHER_GUEST; `MusicFlowFeedError` =
  OFFLINE | EMPTY | FAILED.
- `attach(playerConnection)` / `detach()` — idempotent attachment that binds the session to the
  live player, installs coordinator jobs and restores playback; detaching cancels jobs but keeps
  feed state.
- `onPageSettled(index)`, `togglePlayPause()`, `next()`, `previous()`, `retryPlayback()`,
  `retryFeed()`, `loadMoreIfNeeded()`, `notInterested(item, persist)`, `lessLikeThis(item)`,
  `restorePreviousQueue(): Boolean`.
- Invariants: repeated failures increment a counter and surface as `error = FAILED`; a feed with
  no items is `EMPTY`; offline is `OFFLINE`; a takeover block prevents switching and reports
  `Blocked`; external playback by another queue reports `ExternalOverride`; `canRestorePreviousQueue`
  is true only when a snapshot exists and this flow owns playback.

**`ThemeViewModel`** (plain `ViewModel`)
- `darkMode: StateFlow<DarkMode>` (default AUTO), `pureBlack: StateFlow<Boolean>` (false),
  `selectedThemeColorInt: StateFlow<Int>` (default theme color hash).
- `updateDarkMode`, `updatePureBlack`, `updateThemeColor`. Pure in-memory; the host is responsible
  for persistence.

**`ListenTogetherViewModel`** (`@HiltViewModel`; manager) — exposes manager flows
`connectionState`, `roomState`, `role`, `userId`, `pendingJoinRequests`, `bufferingUsers`, `logs`,
`events`, `hasPersistedSession`, `blockedUsernames`; functions `connect`, `disconnect`,
`createRoom(username)`, `joinRoom(roomCode, username)`, `leaveRoom`, `approveJoin`,
`rejectJoin(userId, reason?)`, `kickUser`, `blockUser`, `unblockUser`, `clearLogs`,
`forceReconnect`, `reconnect`, `getPersistedRoomCode`, `getSessionAge`,
`updateRoomSettings(allowParticipantControl)`. All delegate to the manager; no local state.

**`BottomPlaylistViewModel`** / **`TopPlaylistViewModel`** — read a `savedStateHandle` key
(`"bottom"` / `"top"`), `bottomPeriod`/`topPeriod: MutableStateFlow<MyTopFilter>` (default
ALL_TIME), and the corresponding song flow. Invalid navigation arguments are a host error.

### 9.2 Library and browse view-models

**`LibrarySongsViewModel`** — `allSongs` (DB-backed), `syncLikedSongs()`, `syncLibrarySongs()`,
`syncUploadedSongs()`.
**`LibraryArtistsViewModel`** — `allArtists`, `sync()`.
**`LibraryAlbumsViewModel`** — `allAlbums`, `sync()`.
**`LibraryPlaylistsViewModel`** — `allPlaylists`, `sync()`.
**`PodcastsViewModel`** — `podcasts`, `exploreSections: MutableStateFlow<List<HomePage.Section>>`,
`isLoading`, `isRefreshing`, `isLoadingMore`; `loadPodcastSections()`,
`loadMorePodcastSections()`, `refresh()`.
**`ArtistSongsViewModel`** — `artist`, `songs`; **`LibraryMixViewModel`** — `isRefreshing`,
`syncAllLibrary`, `refresh()`; **`LibraryViewModel`** — `filter: MutableState<LibraryFilter>`
seeded from `savedStateHandle` and the `topValue`/`artists`/`albums`/`playlists` lists.
**`LocalSongsViewModel`** — `scanState: StateFlow<LocalSongsScanState>` (`isScanning`,
`lastSummary`, `errorMessage`), `songs`; `scanDevice(config: LocalSongScanConfig)`.
**`LocalSearchViewModel`** — `query: MutableStateFlow<String>`, `filter: MutableStateFlow<LocalFilter>`
(ALL/SONG/ALBUM/ARTIST/PLAYLIST), `result: StateFlow<LocalSearchResult>` with
`LocalSearchResult(query, filter, map: Map<LocalFilter, List<LocalItem>>)`.
**`LocalPlaylistViewModel`** — `playlistId` from `savedStateHandle` (required), `playlist`,
`playlistSongs`, `suggestions`, `isSpotifySyncing`; `fetchSuggestions()`,
`addSuggestedSong(item)`, `suspend syncWithSpotify(): Boolean`.
**`PlaylistsViewModel`** — see 9.1.

**`HomeViewModel`** — database/sync/recommendation deps; mutable flows `isRefreshing`, `isLoading`,
`isRandomizing`, `dailyDiscover`, `forgottenFavorites`, `keepListening`, `similarRecommendations`,
`accountPlaylists`, `homePage`, `explorePage`, `communityPlaylists`, `selectedChip`,
`allLocalItems`, `allYtItems`, `accountName`, `accountImageUrl`; derived `quickPicks`,
`heroSongs`, `aiRecommendedPlaylist`, `speedDialItems`; `suspend getRandomItem(): YTItem?`,
`togglePin(item)`, `loadMoreYouTubeItems(continuation)`, `toggleChip(chip)`, `refresh()`.
Model types: `DailyDiscoverItem(seed, recommendation, relatedEndpoint)`,
`CommunityPlaylistItem(playlist, songs)`.

**`AlbumViewModel`** — `albumId` from `savedStateHandle` (required), `playlistId`, `albumWithSongs`,
`otherVersions`, `releasesForYou`, `description`, `descriptionRuns`.
**`ArtistViewModel`** — `artistId` (required), `artistPage`, `artistVideoUrl`, `artistVideoSong`,
`libraryArtist`, `librarySongs`, `libraryAlbums`; `fetchArtistsFromYTM()`.
**`ArtistAlbumsViewModel`** — `artist`, `albums`; **`ArtistItemsViewModel`** — `title`, `itemsPage`,
`loadMore()`.
**`OnlinePlaylistViewModel`** — `playlist`, `playlistSongs`, `relatedItems`, `isLoading`, `error`,
`isLoadingMore`, `dbPlaylist`, `continuation`; `loadMoreSongs()`, `suspend loadAllSongs()`, `retry()`.
**`OnlineSearchViewModel`** — `query`, `filter: MutableStateFlow<YouTube.SearchFilter?>`,
`summaryPage`, `viewStateMap`; `loadMore()`.
**`OnlineSearchSuggestionViewModel`** — `query`, `viewState: StateFlow<SearchSuggestionViewState>`
(`history`, `suggestions`, `items`, `isFromLink`).
**`ExploreViewModel`** — `explorePage`; **`MoodAndGenresViewModel`** — `moodAndGenres`;
**`BrowseViewModel`** — `items`, `title`; **`YouTubeBrowseViewModel`** — `result: MutableStateFlow<BrowseResult?>`.
**`ChartsViewModel`** — `chartsPage`, `isLoading`, `error`; `loadCharts()`, `loadMore()`.
**`NewReleaseViewModel`** — `newReleaseAlbums`; **`CachePlaylistViewModel`** — `cachedSongs`,
`removeSongFromCache(songId)`; **`AutoPlaylistViewModel`** — `playlist` (required arg),
`isRefreshing`, `likedSongs`; `syncLikedSongs()`, `syncUploadedSongs()`, `refresh()`.
**`HistoryViewModel`** — `historySource`, `historyPage`, `events`, `fetchRemoteHistory()`;
`DateAgo` sealed type. **`DetailedListeningHistoryViewModel`** — `startTimestamp`, `totalPlayTime`,
`uniqueSongsCount`, `uniqueArtistsCount`, `uniqueAlbumsCount`, `mostPlayedSongs/Artists/Albums`.
**`StatsViewModel`** — `selectedOption`, `indexChips`, totals/unique counts (all-time and period),
`mostPlayedSongsStats/Songs/Artists/Albums`, `firstEvent`.
**`ListeningSummaryViewModel`** — `weekOffset`, `todayPlayTimeMs`, `weekDailyData`, `weekTotalMs`,
`vibeSummary` (`VibeSummary`), `goToPreviousWeek()`, `goToNextWeek()`, `isCurrentWeek()`,
`setWeekFromEpoch(epochMilli)`; models `VibeSummary`, `DayUsageData`.
**`GenerateViewModel`** — `uiState: StateFlow<GenerateUiState>` (Idle | EmptyHistory | AllExcluded |
Generating(message) | Success(playlistId) | Error(message)), `statsCount`, `exclusionCount`;
`generate()`, `cancelGeneration()`, `clearExclusions()`, `resetState()`.
**`CreateAiPlaylistViewModel`** — `prompt`, `numSongs`, `weatherEnabled`, `weatherUiState`,
`isGenerating`, `generationLog`, `errorLog`; `onPromptChanged`, `onNumSongsChanged`, `clearError`,
`resetState`, `onWeatherToggled`, `fetchWeather(context, hasPermission)`,
`generatePlaylist(context, onPlaylistCreated)`, `generateFromTaste(onPlaylistCreated)`.
**`BackupRestoreViewModel`** — `suspend backup(context, uri)`, `suspend restore(context, uri)`,
`suspend previewCsvFile(context, uri): CsvImportState`,
`suspend importPlaylistFromCsv(...)`, `suspend loadM3UOnline(...)`.
`CsvImportState(previewRows: List<List<String>>, artistColumnIndex = 0, titleColumnIndex = 1,
urlColumnIndex = -1, hasHeader = true)`; `ConvertedSongLog(title, artists)`.
**`AccountViewModel`** — `playlists`, `albums`, `artists`, `selectedContentType`
(default PLAYLISTS); `setSelectedContentType`; `AccountContentType` = PLAYLISTS | ALBUMS | ARTISTS.
**`AccountSettingsViewModel`** — `logoutAndClearSyncedContent(context, onCookieChange)`,
`logoutKeepData(context, onCookieChange)`, `saveTokenAndRestart(...)`.

### 9.3 ViewModel invariants

- Every flow exposed with `asStateFlow()` is read-only to UI; UI never assigns to it.
- `savedStateHandle`-derived required arguments (`!!`) are a host contract: navigation must always
  supply them. The implementer may use graceful defaults instead, but must not crash in tests.
- Network/database failures are captured into an `error`/`errorLog`/`errorMessage` field or a
  state variant; they must never crash the app.
- No ViewModel may hold Activity/Context beyond `@ApplicationContext`.

---

## 10. Navigation, platform and lifecycle

### 10.1 Destinations and arguments

| Route | Host | Arguments | Notes |
|---|---|---|---|
| (player sheet, not a route) | `MainActivity` renders `BottomSheetPlayer` outside settings/immersive routes | — | state object `playerBottomSheetState` persisted by the activity; `pureBlack` passed from theme |
| `equalizer` | `dialog("equalizer")` → `EqScreen(navController)` | none | legacy profile picker |
| `settings/equalizer` | `composable` → `AxionEqScreen(onBackClick = navController::navigateUp)` | none | Axion equalizer |
| `artist/{id}` | shared navigation | `id: String` | opened from thumbnail, menus, Song DNA |
| `album/{id}` | shared navigation | `id: String` | opened from song/queue menus |
| `podcast_video` | shared | none | player pill VIDEO; single-top |
| `music_flow` | shared | none | player pill MUSIC_FLOW; single-top; collapses sheet |
| `ambient_mode` | shared | none | player menu advanced |
| `settings/player?highlightKey={highlightKey}` | shared | optional string | appearance settings host for previews |
| `bottom/{bottom}` / `top/{top}` | shared | required string | read by Bottom/TopPlaylistViewModel |

The player sheet is a host overlay, not a nav destination. It must survive configuration changes
and process recreation of the activity's saved state. `expandQueueRequested`,
`showPlayerMenuRequested`, `showLyricsRequested` are one-shot host intents; the corresponding
`on*` callbacks must be invoked exactly once per accepted request.

### 10.2 Manifest / permissions

This cluster adds **no new manifest components** and declares **no new permissions**. Required
existing declarations:

- `<queries><intent><action android:name="android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL"/></intent></queries>`
  — must remain for `EqScreen` to resolve the system equalizer.
- `<queries>` for `OPEN_DOCUMENT_TREE` and `VIEW`/BROWSABLE must remain for the import/export
  pickers used by menus and custom EQ import.
- `BLUETOOTH_CONNECT` (API 31+) and legacy `BLUETOOTH` (maxSdk 30) for the audio-device sheet and
  Bluetooth detection; `READ_MEDIA_AUDIO`/`READ_EXTERNAL_STORAGE` for local song/file pickers.
- The Media3 playback service and its foreground-service permissions are owned by `:playback`.

Exported rules: this cluster exposes no exported components. Do not add `exported="true"`
activities/receivers. The audio-device `BroadcastReceiver` and `AudioDeviceCallback` registered by
`Queue`/`MiniPlayer`/`FloatingMiniPlayer` are dynamic, app-internal and **must be unregistered in
dispose**; they must not be registered in the manifest.

### 10.3 Lifecycle rules

- `CanvasArtworkPlayer` releases its Media3 player and detaches its `TextureView` on dispose.
- The queue sheet's receivers/callbacks are removed in `awaitDispose`.
- Sleep-timer countdown coroutines stop when the composable leaves composition.
- `MusicFlowViewModel.attach/detach` is idempotent; re-attaching the same connection is a no-op.
- No composable may write to the database from the main thread; use `viewModelScope` or a
  background dispatcher.
- The player sheet must respect `WindowInsets` (system bars, navigation bar) and never draw under
  the status bar without padding.

---

## 11. Global invariants

1. Null connection/metadata never crashes: the relevant composable renders nothing.
2. Guest (listen-together) users cannot change playback selection; all such controls are disabled.
3. Progress is always clamped to `[0,1]`; time is clamped to `[0,duration]`; duration `0` is safe.
4. All user-visible strings come from resources; the only tolerated hardcoded strings are the
   PlaybackError explanatory sentences and "Casting to …"/"Video unavailable for local tracks",
   which the implementer should move to resources while preserving the behaviour.
5. Destructive operations require confirmation and are undoable where the underlying service
   exposes a snapshot (queue clear, queue removal).
6. Menus/dialogs dismiss before the effect runs when the effect is navigation; they may dismiss
   after for async work.
7. Style/state enums must be exhaustive: adding a style to a core enum must fail compilation in
   every `when`.
8. Accessibility: every interactive node has a label or state description, minimum 48 dp touch
   targets for text buttons, and decorative imagery is hidden from semantics.

---

## 12. Acceptance tests (deterministic)

Tests use JUnit4 + kotlinx-coroutines-test for pure state logic, and Compose UI test
(`createComposeRule`) with a fake `PlayerConnection`/composition locals for UI. No network.

### 12.1 Pure unit tests

**`ProgressStateTest`**
- duration 0 → progress 0.
- position 0/duration 100 → 0; 50/100 → 0.5; 200/100 → 1.0; negative position → 0.
- mutating the underlying `MutableLongState` changes `progress` on the next read.

**`ThumbnailSnapUtilsTest`**
- `singleAxisViewportSize` returns height for vertical and width for horizontal orientation.
- `calculateDistanceToDesiredSnapPosition` equals `item.offset - (viewport - itemSize)/2` for a
  zero before/after content padding, and accounts for content padding when present.
- `ThumbnailSnapLayoutInfoProvider(v, threshold = 500f)`: low-velocity snaps to the nearest bound;
  high-velocity snaps in the fling direction; `calculateApproachOffset` is 0.

**`ThumbnailNormalizationTest`** — table-driven:
- title with `(Official Video)`, `[Official Music Video]`, `(Lyrics)`, `(Audio)` → stripped;
- title with no marker unchanged apart from trim/whitespace collapse;
- artist `"X - Topic"` → `"X"`; `"XVEVO"` → `"X"`;
- `"A, B & C feat. D"` → `["A","B","C","D"]` in order; blanks and duplicates dropped.

**`CanvasArtworkPlaybackCacheTest`**
- put/get round-trip; blank id ignored; capacity 0 → get null / put no-op;
- inserting capacity+1 evicts the least-recently-used; a get refreshes recency.

**`CsvColumnMappingTest`**
- default indexes 0/1/−1 and `hasHeader = true`;
- confirm rejects a state where title and artist columns are equal or out of range;
- preview rows render unchanged (no mutation of `CsvImportState`).

**`PlayerStyleLabelsTest`** — every `PlayerBackgroundStyle` and `PlayerButtonsStyle` maps to the
documented resource; no `when` falls through.

**`SongDnaStateTest`**
- seeding from metadata yields title/album/thumbnail/performer names/first artist;
- `isLoading` starts true and becomes false after resolution;
- performers deduplicate metadata ∪ credits; songwriters fall back to artist names when empty;
- all-null optional fields are allowed and do not break rendering.

**`ThemeViewModelTest`** — defaults AUTO/false/default color; each updater emits exactly the new
value and does not affect the other flows.

**`LyricsMenuViewModelTest`** (fake helper/observer, `runTest`)
- `search` sets loading true, emits each result in order, loading false at end;
- a second `search` cancels the first and only the second's results survive;
- `cancelSearch` leaves loading false;
- network observer false → `isNetworkAvailable` false; observer exception → true;
- `refetchLyrics` deletes then upserts one row.

**`MusicFlowViewModelTest`** (fake session/coordinator, `runTest`)
- `uiState` initial: empty items, Idle, hasMore true, not restorable;
- attach twice with the same connection does not duplicate jobs;
- `togglePlayPause` with no connection is a no-op, not a crash;
- feed empty → `error = EMPTY`; offline → `OFFLINE`; repeated failure → `FAILED`;
- takeover block reports `Blocked(reason)` and does not switch tracks;
- `restorePreviousQueue` false without snapshot, true only when snapshot exists and queue owned.

**`EQViewModelTest`** (fake repository/controller)
- select null → controller disabled, active null;
- select valid → apply called, active set; apply failure → `error` set and active unchanged;
- import invalid text → `onError`, no profile; valid text → profile saved, `importStatus` set,
  `onSuccess`;
- delete removes only the requested id.

**`AxionEqViewModelTest`** (fake repo/controller + `ApplicationProvider` prefs)
- initial enabled false, gains ten zeros, mode 0;
- `setBandGain(3, 5f)` → gains[3] 5f, dirty true, persisted, re-applied when enabled;
- `setEnabled(true)` applies; `setEnabled(false)` clears active and disables;
- `saveCustomProfile("X")` creates id `custom_*`, custom, active, dirty false;
- `reset()` zeroes all gains;
- recreate the VM with the same prefs → same gains/mode/enabled.

### 12.2 Compose/UI tests

**`MiniPlayerSemanticsTest`**
- with a fake connection: play button description is `play` when paused and `pause` when playing;
  `STATE_ENDED` shows `replay`; guest shows `mute`/`unmute`;
- previous/next disabled when `canSkipPrevious`/`canSkipNext` false;
- tapping the bar invokes `onClick`; tapping play does not invoke `onClick`;
- progress line width equals `progress` fraction (use a test tag on the fill);
- swipe past the threshold seeks next; a short drag below threshold does not.

**`QueueInteractionTest`**
- reorder is disabled when `QueueEditLockKey` false? (locked=false means enabled) — assert the
  inverse and that the lock toggle flips it;
- swipe-to-dismiss removes one row and shows an undo snackbar; undo restores it;
- clear queue confirmation then undo restores the exact snapshot;
- selection mode: long-press selects, back exits and clears selection;
- empty queue? (queue always contains the current item) — assert header count matches items.

**`AxionEqScreenTest`**
- enable switch toggles and calls the VM;
- simple/advanced toggles update the mode; advanced shows ten band sliders;
- save preset disabled until dirty, enabled after a band change;
- manage-presets empty state message when no custom profiles;
- band slider has a value/state description containing its frequency and dB.

**`MenuA11yTest`**
- `SongMenu`/`YouTubeSongMenu` expose like, download, add-to-playlist, more-options labels;
- guest flag removes play-next/add-to-queue nodes;
- destructive rows show a confirmation dialog and cancel leaves state unchanged;
- `AddToPlaylistDialog` in the empty state shows `no_results_found`; duplicate add shows the
  "add anyway" path.

**Screenshot/preview targets** (Roborazzi/compose-preview, no assertions beyond snapshot):
- `FullPlayerPreview` for each `PlayerBackgroundStyle` × `PlayerButtonsStyle` (at least DEFAULT/
  GRADIENT/BLUR and DEFAULT/PRIMARY/TERTIARY);
- `MiniPlayerPreview` for each background style;
- `MiniPlayer` in light/dark/pure-black with/without casting;
- `Queue` with selection mode off/on and with automix present.

### 12.3 Process-recreation tests

- `AxionEqViewModel`: destroy/recreate with identical `SharedPreferences` → gains, mode and
  enabled identical; custom profiles reload from the repository.
- `BottomSheetPlayer`: saveable state `showInlineLyrics`/`isFullScreen`/selection restore after
  `StateRestorationTester` recreation; one-shot host intents do not re-fire after recreation.
- `Queue`: selection set and selection mode restore after recreation via the list saver; the
  reverse order is preserved.

---

## 13. Definition of done

- All 77 source files compile against the contracts in section 4 and the exact public signatures
  in sections 5–9.
- All tests in section 12 exist and pass on JVM; Compose/screenshot tests run on an API level that
  the project's `compileSdk` supports.
- No new permission or manifest component is introduced.
- `settings/equalizer`, `dialog("equalizer")`, `artist/*`, `album/*`, `podcast_video`,
  `music_flow`, `ambient_mode` routes resolve and round-trip without arguments errors.
- Every UI string is resource-backed (except the tolerated diagnostic sentences, which should be
  moved to resources without changing behaviour).
