# Clean-Room Specification — `app-entry-lifecycle` cluster (`:app` entry + shell)

Status: M2 specification (behavioural contract only).
Module: `:app` (`app/`), plus the in-app `com.revenge.dpi` package that ships in the same APK.
Cluster scope: `App`, `MainActivity`, `CrashActivity`, `WelcomeDialog`, `NetworkReload`,
`NavigationBuilder`, `Screens`, the DPI scaler provider, the Hilt DI modules/entry point, and the
manifest/build surface those components require.

This document describes observable behaviour, API shape, process/lifecycle rules and navigation
contracts. It deliberately omits statement-level implementation and does not reproduce Compose
subtrees. Any implementation that satisfies the contracts and acceptance tests is acceptable.

**Compatibility is a hard requirement for externally observable contracts.** The shipped APK
launches from launchers, widgets, media buttons, voice search, custom schemes, HTTPS app links and
share sheets. Route strings, action constants, extras, process names, export flags and launch
modes below are consumed by other modules (`:playback`, `:innertube`, widgets, recognition) and by
Android itself, so they must not be renamed.

---

## 1. Module build contract

`app/build.gradle.kts` must:

- apply `com.android.application`, Hilt, Kotlin KSP, the Compose compiler and Kotlin serialization;
- set `namespace = "com.revenge.music"`, `applicationId = "com.revenge.music"`,
  `compileSdk = 37`, `minSdk = 26`, `targetSdk = 36`;
- keep `versionCode`/`versionName` overridable from Gradle properties
  (`revengeVersionCode`, `revengeVersionName`) with defaults `1` / `"1.0.0"`;
- expose `BuildConfig.DEBUG`, `BuildConfig.VERSION_NAME`, `BuildConfig.VERSION_CODE`,
  `BuildConfig.LASTFM_API_KEY`, `BuildConfig.LASTFM_SECRET`, `BuildConfig.UPDATE_REPO`,
  `BuildConfig.IS_NIGHTLY` (and the existing flavour fields); these are read by the entry code;
- depend on `:core`, `:playback`, `:usbaudio`, `:audio-dsp`, `:dsp-core`, `:innertube`, `:lyrics`,
  `:kugou`, `:lrclib`, `:betterlyrics`, `:simpmusic`, `:youlyplus`, `:canvas`, `:shazamkit`,
  `:artistvideo`, `:applecanvas`, `:paxsenixlyrics`, `:unison`;
- apply the Hilt Gradle plugin and `ksp(libs.hilt.compiler)` so `@HiltAndroidApp` /
  `@AndroidEntryPoint` / `@EntryPoint` generate;
- add `testImplementation` JUnit 4, `kotlinx-coroutines-test` and Robolectric (already declared);
  any Compose UI semantics tests added under `androidTest` must add
  `androidTestImplementation` for `androidx.compose.ui:ui-test-junit4` and its manifest runner;
- keep the `foss`/`gms` variant dimension and the ABI flavours; entry behaviour below is
  flavour-independent.

The `com.revenge.dpi` classes live under `app/src/main/kotlin/com/revenge/dpi/` and ship in
`:app` (there is no separate Gradle module).

---

## 2. Target files

Every path below is relative to the repository root and must exist.

| Target path | Responsibility |
|---|---|
| `app/src/main/kotlin/com/revenge/music/App.kt` | `@HiltAndroidApp` Application, process routing, startup configuration, Coil image-loader factory, sign-out helper. |
| `app/src/main/kotlin/com/revenge/music/MainActivity.kt` | Launcher/shell activity, service binding, intent dispatch (widget/deep link/recognition/voice), Compose shell, composition locals. |
| `app/src/main/kotlin/com/revenge/music/ui/screens/CrashActivity.kt` | Crash-process activity + `CrashScreen` composable. |
| `app/src/main/kotlin/com/revenge/music/ui/screens/WelcomeDialog.kt` | First-launch-after-update dialog composable. |
| `app/src/main/kotlin/com/revenge/music/ui/screens/NetworkReload.kt` | Offline→online reload trigger composable. |
| `app/src/main/kotlin/com/revenge/music/ui/screens/NavigationBuilder.kt` | `NavGraphBuilder.navigationBuilder(...)` and every destination. |
| `app/src/main/kotlin/com/revenge/music/ui/screens/Screens.kt` | Top-level tab model. |
| `app/src/main/kotlin/com/revenge/dpi/BaseLifecycleContentProvider.kt` | Abstract no-op `ContentProvider`. |
| `app/src/main/kotlin/com/revenge/dpi/ActivityLifecycleManager.kt` | Provider base that observes activity lifecycle and runs a periodic timer. |
| `app/src/main/kotlin/com/revenge/dpi/DensityConfiguration.kt` | Density scaling application/re-application. |
| `app/src/main/kotlin/com/revenge/dpi/DensityScaler.kt` | Manifest-registered provider entry point. |
| `app/src/main/kotlin/com/revenge/music/di/AppModule.kt` | Singleton providers (scope, DAOs, database, caches, listen-together). |
| `app/src/main/kotlin/com/revenge/music/di/DomainBindingsModule.kt` | `@Binds` repository/feed bindings. |
| `app/src/main/kotlin/com/revenge/music/di/NetworkModule.kt` | Connectivity observer provider. |
| `app/src/main/kotlin/com/revenge/music/di/LyricsHelperEntryPoint.kt` | Hilt entry point for non-injected Compose code. |
| `app/src/main/AndroidManifest.xml` | Application/activity/provider/service/receiver declarations and intent filters (§11). |
| `app/src/main/res/xml/shortcuts.xml` | Static shortcuts (`search`, `library`) targeting `MainActivity`. |
| `app/src/main/res/values/styles.xml` | `Theme.Revenge` window theme used by `MainActivity`/`CrashActivity`. |

### 2.1 Target test files

| Target path | Kind |
|---|---|
| `app/src/test/kotlin/com/revenge/music/AppProcessRoutingTest.kt` | Robolectric unit test (§12.1). |
| `app/src/test/kotlin/com/revenge/music/DensityScalerTest.kt` | Robolectric unit test (§12.2). |
| `app/src/test/kotlin/com/revenge/music/ui/screens/ScreensContractTest.kt` | JVM unit test (§12.5). |
| `app/src/test/kotlin/com/revenge/music/ui/screens/NavigationGraphContractTest.kt` | Robolectric/JVM structural test (§12.6). |
| `app/src/test/kotlin/com/revenge/music/ui/screens/NetworkReloadTest.kt` | Coroutine test of the reload transition rule (§12.7). |
| `app/src/test/kotlin/com/revenge/music/ui/screens/WelcomeGateTest.kt` | Pure gate-function test (§12.8). |
| `app/src/androidTest/kotlin/com/revenge/music/MainActivityIntentTest.kt` | Instrumented singleTask/intent test (§12.3, §12.4). |
| `app/src/androidTest/kotlin/com/revenge/music/CrashScreenSemanticsTest.kt` | Compose semantics/accessibility test (§12.9). |

Implementations may keep pure helpers `internal` to make §12.7/§12.8 testable; those helpers are
not part of the public API consumed elsewhere.

---

## 3. `App` — application process contract

`@HiltAndroidApp class App : Application(), SingletonImageLoader.Factory, Configuration.Provider`.

Injected: `@ApplicationScope applicationScope: CoroutineScope` (`lateinit`, field injection).

### 3.1 Process classification

- The **main process** is the process whose name is `null` or exactly `packageName`
  (`com.revenge.music`).
- The **crash process** is the process whose name is `"$packageName:crash"`
  (`com.revenge.music:crash`). Process name is obtained via `Application.getProcessName()` on
  API 28+; on older APIs it is resolved from `ActivityManager.runningAppProcesses` by pid.
- Any other process (e.g. `:reconnection_service` declared by the Cast SDK) is a **secondary
  process**.

### 3.2 `onCreate()` behaviour

Order and effects are observable and must hold:

1. Crash process: install the global uncaught-exception handler (`CrashHandler.install(this)`) and
   return. No `AppContextHolder`, no workers, no settings, no Coil cache read, no locale/proxy
   setup.
2. Every other process: install `CrashHandler`.
3. Secondary process: return after installing the handler.
4. Main process only, in order:
   - `AppContextHolder.initialize(this)`;
   - schedule `NewEpisodesWorker` (`NewEpisodesWorker.schedule(this)`);
   - `CipherDeobfuscator.initialize(this)`;
   - `YTPlayerUtils.initialize()`;
   - in `BuildConfig.DEBUG`, plant `Timber.DebugTree()`;
   - launch on `Dispatchers.IO`: read `MaxImageCacheSizeKey` from the settings DataStore
     (null/exception → `512`) into a volatile `Int?` cache used by `newImageLoader`;
   - launch on `Dispatchers.IO`: best-effort DNS warm-up of `music.youtube.com`,
     `youtubei.googleapis.com`, `googlevideo.com`; failures are ignored;
   - launch (default dispatcher): `initializeSettings()`, then
     `InnerTubeXResolver.init(this@App)`, then `observeSettingsChanges()`.

No database deletion or other destructive migration may run on startup.

### 3.3 WorkManager configuration

`workManagerConfiguration` returns a `Configuration` with minimum logging level `Log.DEBUG` in
debug builds and `Log.ERROR` otherwise. Because the manifest removes the default
`WorkManagerInitializer` (§11), WorkManager is initialised on demand from this provider.

### 3.4 Foreground-service suppression

`override fun startForegroundService(service: Intent): ComponentName?` must call through; when
API ≥ 31 and the call throws `ForegroundServiceStartNotAllowedException`, log and return `null`
instead of propagating. Any other exception is rethrown.

### 3.5 Initial settings (`initializeSettings`)

Reads the settings DataStore once and applies:

- **YouTube locale**: `gl` = stored `ContentCountryKey` unless it equals `SYSTEM_DEFAULT`, else
  device country if in `CountryCodeToName`, else `"US"`; `hl` = stored `ContentLanguageKey`
  unless `SYSTEM_DEFAULT`, else device language / language tag if in `LanguageCodeToName`, else
  `"en"`.
- `KuGou.useTraditionalChinese = true` for a `zh-TW` device language.
- **Proxy** when `ProxyEnabledKey == true`: username/password from `ProxyUsernameKey` /
  `ProxyPasswordKey`; type from `ProxyTypeKey` (default `Proxy.Type.HTTP`). HTTP proxy with
  credentials sets `YouTube.proxyAuth` to HTTP Basic; non-HTTP installs a default `Authenticator`.
  `ProxyUrlKey` is parsed into a `Proxy`; a parse failure shows
  `R.string.failed_to_parse_proxy` on the main thread and reports the exception. Proxy must not be
  set when `ProxyEnabledKey` is false.
- `YouTube.useLoginForBrowse` = `UseLoginForBrowse` or `true`.
- `YouTube.ipVersion` = `IpVersionKey` enum or `AUTO`.
- `YTPlayerUtils.playbackEngine` = `PlaybackEngineKey` enum, `AUTO` if absent or invalid.
- Creates/updates the notification channel id `"updates"` with
  `R.string.update_channel_name` / `R.string.update_channel_desc`, importance `DEFAULT`.

### 3.6 Settings observers (`observeSettingsChanges`)

Long-lived collectors on `applicationScope + Dispatchers.IO`, all `.distinctUntilChanged()` on the
relevant key. Missing/exceptional values are treated as `null` (never crash the app):

- `VisitorDataKey` → `YouTube.visitorData`; if blank/`"null"`/absent, refresh from
  `YouTube.visitorData()` and persist the new value.
- `DataSyncIdKey` → `YouTube.dataSyncId`, using the legacy normalisation: a value containing
  `"||"` becomes the part before `||` when it ends with `||`, otherwise the part after `||`.
- `InnerTubeCookieKey` → `YouTube.cookie`; a parse exception logs and calls
  `App.forgetAccount(this)`.
- `ContentCountryKey` + `ContentLanguageKey` + `AppLanguageKey` together recompute
  `YouTube.locale`: app language overrides system locale; stored content country/language override
  respectively; same fallbacks (`US`/`en`) as §3.5.
- `IpVersionKey` → `YouTube.ipVersion` (default `AUTO`).
- `LastFM.initialize(apiKey = BuildConfig.LASTFM_API_KEY`, `secret = BuildConfig.LASTFM_SECRET)`
  called once (empty keys allowed).

### 3.7 Coil image loader

`newImageLoader(context)` returns a singleton `ImageLoader` built on `this` with:

- `crossfade(true)`, `allowHardware(true)` on API 28+;
- components: `YouTubeThumbnailFallbackInterceptor` and `LocalAlbumArtFetcher.Factory`;
- memory cache at 25 % of app memory;
- disk cache: if the configured max image cache size is `0`, disk cache policy `DISABLED`;
  otherwise a disk cache under `cacheDir/coil` with size `sizeMiB * 1024 * 1024` bytes.

The cache size may already be cached from §3.2; if not, it is read synchronously with default
`512`. The loader must never return a disk cache when the size is `0`.

### 3.8 Account sign-out

`companion object fun suspend forgetAccount(context: Context)`:

- removes `InnerTubeCookieKey`, `VisitorDataKey`, `DataSyncIdKey`, `AccountNameKey`,
  `AccountEmailKey`, `AccountChannelHandleKey` from the settings DataStore;
- clears `YouTube.cookie`, `YouTube.visitorData`, `YouTube.dataSyncId`;
- calls `InnerTubeXResolver.onSessionChanged()`;
- on the main dispatcher clears WebView cookies (`CookieManager.removeAllCookies` + `flush`).

It must be safe to call from any dispatcher and must not throw.

---

## 4. `MainActivity` — public contract

`@AndroidEntryPoint class MainActivity : ComponentActivity()`.

### 4.1 Constants (must keep exact values)

| Constant | Value |
|---|---|
| `EXTRA_VOICE_REQUEST_HANDLED` (private) | `"com.revenge.music.voice_request_handled"` |
| `ACTION_SEARCH` | `"com.revenge.music.action.SEARCH"` |
| `ACTION_LIBRARY` | `"com.revenge.music.action.LIBRARY"` |
| `ACTION_RECOGNITION` | `"com.revenge.music.action.RECOGNITION"` |
| `ACTION_NOW_PLAYING` | `"com.revenge.music.action.NOW_PLAYING"` |
| `ACTION_QUEUE` | `"com.revenge.music.action.QUEUE"` |
| `ACTION_OUTPUT_SWITCHER` | `"com.revenge.music.action.OUTPUT_SWITCHER"` |
| `ACTION_SONG_OPTIONS` | `"com.revenge.music.action.SONG_OPTIONS"` |
| `ACTION_LYRICS` | `"com.revenge.music.action.LYRICS"` |
| `ACTION_MEDIA_OUTPUT` | `"com.android.settings.panel.action.MEDIA_OUTPUT"` |
| `EXTRA_MEDIA_OUTPUT_PACKAGE_NAME` | `"com.android.settings.panel.extra.PACKAGE_NAME"` |
| `EXTRA_AUTO_START_RECOGNITION` | `"auto_start_recognition"` |

All constants are `public` in the companion. Widget, recognition and quick-settings modules build
`Intent`s against these; changing a value silently breaks those senders.

### 4.2 Injected dependencies

`lateinit var database: MusicDatabase`, `localTasteEngine: LocalTasteEngine`,
`downloadUtil: DownloadUtil`, `syncUtils: SyncUtils`,
`listenTogetherManager: ListenTogetherManager` — all field-injected via Hilt. A missing binding is
a startup failure, not a runtime fallback.

### 4.3 Lifecycle

**`onCreate`** (observable order):

1. `installSplashScreen()` before `super.onCreate`.
2. Attempt to pre-start `MusicService`; any exception is logged and ignored.
3. Force LTR layout direction; draw edge-to-edge (`WindowCompat.setDecorFitsSystemWindows(false)`).
4. `listenTogetherManager.initialize()`.
5. On API < 33, apply the stored app locale (`AppLanguageKey`, `SYSTEM_DEFAULT` → system) with
   `setAppLocale`.
6. If `filesDir/clear_export_state` exists, clear `ExportingSongIdsKey`, `ExportedSongIdsKey`,
   `ExportProgressKey` and delete the marker file.
7. Start the AI-recommendation and screenshot-guard observers:
   - once per day (day = `System.currentTimeMillis() / 86_400_000`), when
     `AiRecommendationsKey == true` and `LastAiRecommendationUpdateDayKey != today`, persist today
     and call `AiRecommendationHelper.generateRecommendations(this)`;
   - observe `DisableScreenshotKey`: true sets `FLAG_SECURE`, false clears it;
   - observe `AiRecommendationsKey`: true enqueues the unique periodic
     `AiRecommendationWorker` (1 day, `NetworkType.CONNECTED`, `KEEP`), false cancels it. Worker
     scheduling failures are logged, never fatal.
8. `setContent { revengeApp(...) }`.

**`onStart`**: on API 33+ request `POST_NOTIFICATIONS` if not granted (request code `1000`);
bind `MusicService` with `BIND_AUTO_CREATE`. Binding must tolerate the service being absent.

**`onStop`**: unbind the service connection.

**`onDestroy`**: record whether playback was active before teardown; detach the listen-together
player and dispose the `PlayerConnection`. If `StopMusicOnTaskClearKey == true` **and** the player
was playing **and** `isFinishing`, stop `MusicService` and drop the connection. Configuration
changes (handled by the manifest `configChanges`) must not trigger this path.

**Service connection** (`MusicBinder`):

- On connect, construct `PlayerConnection(this, binder, database, lifecycleScope)` and pass it to
  `listenTogetherManager.setPlayerConnection(...)`.
- If construction throws, retry once after 500 ms; a second failure leaves the app usable with no
  player.
- On disconnect, detach the listen-together player, dispose and null the connection.

**`onNewIntent(intent)`**: `setIntent(intent)`. If the nav controller is initialised, dispatch the
deep-link, recognition and assistant-search handlers immediately. Otherwise store it as
`pendingIntent` for the first composition. Widget actions are dispatched by the composition's
new-intent listener, not here.

**`startForegroundService`**: same suppression contract as §3.4.

### 4.4 Intent dispatch contract

The first composition consumes, in order:

1. `pendingIntent ?: intent` for widget actions;
2. `pendingIntent` for deep links, recognition and voice search; then clears `pendingIntent`;
3. the activity `intent` when its action is `ACTION_VIEW` or `ACTION_SEND` → deep links;
4. action `ACTION_RECOGNITION` → recognition;
5. action `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` → voice search.

While the activity lives, an `addOnNewIntentListener` handles new intents with the same action
routing. Every listener added must be removed on dispose.

#### 4.4.1 Widget/action routing (`handleWidgetAction`)

| Action | Effect |
|---|---|
| `ACTION_NOW_PLAYING` | Expand the player sheet softly. |
| `ACTION_QUEUE` | Expand the player sheet and request queue expansion once; the request flag is cleared by the sheet callback. |
| `ACTION_SONG_OPTIONS` | Expand the player sheet and request the song menu once; cleared by callback. |
| `ACTION_OUTPUT_SWITCHER` | On API 30+, if `ACTION_MEDIA_OUTPUT` resolves with
`EXTRA_MEDIA_OUTPUT_PACKAGE_NAME = packageName`, launch the system media-output panel (`NEW_TASK`);
otherwise show the in-app audio-device bottom sheet. |
| `ACTION_LYRICS` | Expand the player sheet and request lyrics once; cleared by callback. |

Unknown actions are ignored. Repeated delivery of the same action must not stack duplicate
requests (`expandQueueRequested`, `showPlayerMenuRequested`, `showLyricsRequested` are one-shot).

#### 4.4.2 Deep links (`handleDeepLinkIntent`)

Input URI is `intent.data`; if absent, the first `https?://\S+` substring of `EXTRA_TEXT` (share
sheet) is used. If neither yields a URI, nothing happens. On a successful parse `intent.data` and
`EXTRA_TEXT` are cleared so recreation does not replay the link.

Resolution order:

1. **Listen Together.** `listenCode` = query `code`, else `room`, else path segment index 1.
   `isListenLink` = first path segment is `listen` (case-sensitive comparison) **or** host equals
   `listen` ignoring case. When both are satisfied and the code is non-blank, join with
   `listenTogetherManager.joinRoom(code, username)`, username = `ListenTogetherUsernameKey`
   (blank → `"Guest"`). This covers `https://metroserverx.meowery.eu/listen?...` and
   `revenge://listen/<code>`; no navigation occurs.
2. Path segment `playlist`: `list` query → `validOnlinePlaylistId`. Null/blank → Toast
   `R.string.invalid_playlist_url`. `OLAK5uy_`-prefixed ids are album playlists: resolve
   `YouTube.albumSongs(id)`, take the first song's album browse id, navigate
   `album/<urlEncoded browseId>`. Other ids navigate `onlinePlaylistRoute(id)`.
3. Path `browse`: navigate `album/<urlEncoded lastPathSegment>`.
4. Paths `channel` or `c`: navigate `artist/<urlEncoded lastPathSegment>`.
5. Path `search`: query `q` → navigate `search/<urlEncoded q>`.
6. Everything else: `videoId` = query `v` when path is `watch`; when host is `youtu.be` or
   `music.youtube.com`, the first path segment; else null. `playlistId` = query `list`.
   - video id present → `YouTube.queue(listOf(videoId), playlistId)`; on success wait up to
     20 × 100 ms for a non-null `playerConnection`, then
     `playerConnection.playQueue(YouTubeQueue(WatchEndpoint(videoId, playlistId), firstItem.toMediaMetadata()))`.
   - otherwise playlist id present → `YouTube.queue(null, playlistId)` and play
     `YouTubeQueue(WatchEndpoint(firstItem.id, playlistId), firstItem.toMediaMetadata())`.
   - failures are reported through `reportException`; the user stays on the current screen.

URL encoding uses UTF-8; encode with `URLEncoder.encode(value, "UTF-8")`.

#### 4.4.3 Recognition intent

Only for `ACTION_RECOGNITION`. Reads `EXTRA_AUTO_START_RECOGNITION` (Boolean), removes it, and
navigates to `recognition?autoStart=true` when true, else `recognition`, always with
`launchSingleTop = true`.

#### 4.4.4 Assistant / voice search

Only for `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH`.

- A recreated activity receives the same intent again; it must play at most once. A boolean extra
  keyed `EXTRA_VOICE_REQUEST_HANDLED` is set on the intent; when already set, return.
- `query` = `SearchManager.QUERY`; `extras` = full intent extras.
- Increment a monotonic `voiceRequestCount`; a request whose captured count is stale when it
  reaches playback must abort (latest request wins).
- Await a non-null `playerConnection` (cold start ordering).
- On IO, call
  `playerConnection.service.mediaLibrarySessionCallback.voiceSearchQueue(query, extras)` inside
  `runCatching`; on failure report and treat as null.
- If a queue exists: warm artwork for the start item
  (`service.warmArtwork(mediaMetadata.artworkUri)`) and, if still current, play
  `ListQueue(items = queue.mediaItems, startIndex = queue.startIndex, position = max(0, queue.startPositionMs))`.
- If no queue and `query` is non-blank: navigate `search/<urlEncoded query>`.

### 4.5 Shell UI contract (`revengeApp`)

The shell is driven by DataStore preferences and player state. It must provide:

**Theme.** `DarkMode` (`AUTO`/`ON`/`OFF`) resolved against the system; pure black only when the
`PureBlackKey` preference is enabled *and* dark; monochrome flag from `IsMonochromeThemeKey`;
base theme colour from `SelectedThemeColorKey` (default `BrandThemeColor`). With
`AlbumArtThemeRecoloringKey` enabled while effectively playing, the theme colour follows the
current artwork's extracted colour and falls back to the selected colour on any failure. System
bar icon appearance follows the effective dark theme.

**Frame rate.** `EnableHighRefreshRateKey` true requests the display's preferred/default mode
(mode id `0` / preferred rate `0f`); false selects the supported mode closest to 60 Hz (exact 60
preferred). API 30 uses `preferredDisplayModeId`, older uses `preferredRefreshRate`.

**System bars / insets.** Edge-to-edge; transparent status and navigation bars; LTR; the app is
not RTL. Insets account for system bars, display cutout and the player/nav-bar stack; the
composition-local `LocalPlayerAwareWindowInsets` is the single inset contract for screens.

**Top app bar.** Visible only on top-level destinations (`home`, `podcasts`, `library`,
`listen_together`, `settings`), never on `settings`, and never on `listen_together` when the
listen-together entry lives in the top bar (`ListenTogetherInTopBarKey`). Title mapping:
`home → "Revenge"`, `search_input → R.string.search`, `library → R.string.filter_library`,
`listen_together → R.string.together`, `podcasts → R.string.podcast`, else empty. Actions:
overflow menu (History when listening history is not paused or has events; Stats; Together;
Recognition) and the account/settings icon (account avatar when available, else settings icon).
Content descriptions: `R.string.more_options`, `R.string.account`, `R.string.settings`.

**Bottom navigation / rail.** Items = `Screens.MainScreens`, with `ListenTogether` removed when
`ListenTogetherInTopBarKey` is true. On compact screens a bottom bar is shown; on wide screens
(width ≥ desktop breakpoint, or width ≥ tablet breakpoint in landscape) a navigation rail replaces
it. Bottom bar visibility is limited to: no route (startup), any tab route, and routes whose first
path segment is one of `search`, `album`, `online_playlist`, `local_playlist`,
`spotify_playlist`, `auto_playlist`, `cache_playlist`, `top_playlist`, `bottom_playlist`,
`artist`. The bottom bar hides on `update`, `listen_together/chat`, `ambient_mode`, `music_flow`,
`podcast_video`, `uptime`, `wrapped`, and any `settings*` route (and on the rail layout hides on
`update`).

**Tab selection semantics.** Selecting the already-current tab sets `scrollToTop = true` in that
destination's `SavedStateHandle` and resets the app-bar height offset. Selecting another tab first
tries `popBackStack(route, inclusive = false)`; only if that returns false does it navigate with
`popUpTo(startDestinationId) { saveState = true }`, `launchSingleTop = true`,
`restoreState = true`. This produces the standard "restore tab state" behaviour.

**Start destination.** Determined once per activity creation:
`ACTION_SEARCH` intent → `Screens.Library`, `ACTION_LIBRARY` intent → `Screens.Search` (the static
shortcuts intentionally cross-map); otherwise `DefaultOpenTabKey` mapped `HOME → home`,
`LIBRARY → library`, anything else → `home`.

**Search.** The search field value is saved with `TextFieldValue.Saver`; when the current route is
`search/{query}` the field is populated from the URL-decoded argument, and selecting a tab clears
it. Submitting a non-empty query navigates `search/<urlEncoded query>` and, unless
`PauseSearchHistoryKey` is true, inserts a `SearchHistory(query = …)` row on IO.

**Top-level transitions.** Enter/exit/pop transitions slide horizontally by ±1/8 of the width with
`fadeIn`/`fadeOut` over 400 ms using `EmphasizedEasing = CubicBezierEasing(0.2, 0, 0, 1)`;
direction is derived from the index of the source/target tab in the navigation items. Routes not
in the item list are treated as "forward".

**Mini player / player sheet.** A bottom-sheet player is present on all routes except `update`,
`listen_together/chat`, `ambient_mode`, `music_flow`, `podcast_video`, `uptime`, `wrapped` and
`settings*`. The sheet is dismissed exactly when there is no media metadata and collapsed softly
when metadata appears. The player-aware bottom inset includes the nav bar and the mini player
height when applicable. `LocalExpandPlayer` is the shared "expand player" callback.

**Dialogs and overlays in the shell:**

- Update-available dialog when a startup update check reports an available version; its dismissal
  hides it. The check runs 2 s after start only when `getAutoUpdateCheckSetting(context)` is true;
  the available state is persisted (`saveUpdateAvailableState`) and, when
  `getUpdateNotificationsSetting(context)` is true, a notification is posted.
- "What's new" dialog when `lastSeenVersion != currentVersion` and a changelog fetch succeeds; the
  version is marked seen only when this dialog is dismissed. A fresh install (`lastSeenVersion`
  empty) is marked seen immediately without a dialog.
  On any version change (fresh install or update) `ForceOpusKey` is set to `true`.
- `WelcomeDialog` (see §6) when `LastOpenedVersionCodeKey < BuildConfig.VERSION_CODE`.
- Ringtone trimmer/progress dialogs driven by `RingtoneViewModel.uiState`.
- Share-sheet song menu dialog for a shared `SongItem`.
- Audio-device bottom sheet for `ACTION_OUTPUT_SWITCHER` fallback.
- A one-day-only celebration overlay on 14 October; it disappears when its particle systems end.

**Haptics.** A global pointer-down haptic of type `CLICK` when the `HapticHelper` master and click
toggles are enabled; the Compose `LocalHapticFeedback` is replaced so `LongPress` maps to
`LONG_PRESS`, text-handle moves are silent, and other types map to `CLICK`.

### 4.6 Composition locals (cross-module compile contract)

These top-level properties live in `MainActivity.kt` and are imported by screens/components.
Names, generic types and defaults are part of the contract:

```kotlin
val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalRingtoneViewModel = compositionLocalOf<RingtoneViewModel> { error("No RingtoneViewModel provided") }
val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalListenTogetherManager = staticCompositionLocalOf<ListenTogetherManager?> { null }
val LocalIsPlayerExpanded = compositionLocalOf { false }
val LocalExpandPlayer = staticCompositionLocalOf<() -> Unit> { {} }
```

The shell provides `LocalDatabase`, `LocalRingtoneViewModel`, `LocalPlayerConnection`,
`LocalPlayerAwareWindowInsets`, `LocalDownloadUtil`, `LocalSyncUtils`,
`LocalListenTogetherManager`, `LocalExpandPlayer`, plus the existing
`LocalGlassEffectConfig`, `LocalAppBackdrop`, `LocalHapticFeedback`, `LocalContentColor` and
`LocalShimmerTheme`. `LocalIsPlayerExpanded` may remain unprovided (default `false`).

---

## 5. `CrashActivity` — crash process

`class CrashActivity : ComponentActivity()` — no Hilt annotation, because it runs in the separate
`:crash` process and must not require the app DI graph. It reads
`intent.getStringExtra(CrashHandler.EXTRA_CRASH_LOG)`; null → `R.string.crash_no_log`.

Behaviour:

- Edge-to-edge, `revengeTheme(darkTheme = isSystemInDarkTheme())`.
- `CrashScreen(crashLog, onClose, onShare, onCopy)` (public composable).
- **Close**: `finishAffinity()`. **Copy**: plain-text clip labelled `"CrashLog"` plus a
  `R.string.copied_to_clipboard` toast.
- **Share**: write `cacheDir/revenge_crash_yyyyMMdd_HHmmss.txt` (UTC-independent, `Locale.US`),
  expose it through `FileProvider` authority `"${packageName}.FileProvider"`, then
  `ACTION_SEND` `text/plain` with `EXTRA_STREAM`, subject `R.string.crash_report_subject`,
  `FLAG_GRANT_READ_URI_PERMISSION`, via a chooser titled `R.string.crash_share_title`. If any of
  that fails, fall back to the same `ACTION_SEND` with `EXTRA_TEXT = crashLog`.
- `CrashHandler` launches this activity with `NEW_TASK | CLEAR_TASK` and then kills the process.

UI contract (`CrashScreen`):

- Scaffold top bar with title `R.string.crash_title`; actions: copy icon (`contentDescription =
  R.string.copy_logs`) and close icon (`contentDescription = R.string.crash_close`).
- Body: description `R.string.crash_description`, then the crash log in a monospaced,
  11 sp, horizontally scrollable, vertically scrollable surface; the full text must be reachable
  without truncation.
- Extended FAB with share icon and label `R.string.crash_share_logs`.
- No crash log content is exposed as a content description; the visible text is the semantics.
- The screen must render with an arbitrarily long log and with the plural fallback string.

Required resources: strings `crash_no_log`, `crash_title`, `copy_logs`, `crash_close`,
`crash_share_logs`, `crash_description`, `copied_to_clipboard`, `crash_report_subject`,
`crash_share_title`; drawables `content_copy`, `close`, `share`.

---

## 6. `WelcomeDialog`

`@Composable fun WelcomeDialog(onDismissRequest: () -> Unit)` — shown once per `versionCode` by
the shell (§4.5).

- Modal `Dialog` using the full available width; dismissing through the Continue button or the
  platform back/outside gesture invokes `onDismissRequest`.
- Content: app icon (`R.mipmap.ic_launcher`, decorative, `contentDescription = null`), app name
  `R.string.app_name`, version badge `BuildConfig.VERSION_NAME`, and a full-width primary button
  labelled `R.string.continue_action` that calls `onDismissRequest`.
- The card is vertically scrollable so large font scales do not clip the button.
- Only elements that convey meaning are announced: the app name and version are text semantics;
  the button is a standard button with its visible label. No extra custom semantics.

On dismissal the shell persists `LastOpenedVersionCodeKey = BuildConfig.VERSION_CODE`; the gate is
`lastOpened < BuildConfig.VERSION_CODE` (default `-1`).

---

## 7. `NetworkReload`

`@Composable fun NetworkReload(onReload: () -> Unit)` — no visible UI.

- Owns a `NetworkConnectivityObserver` for the application context for the composition's lifetime
  and unregisters it on dispose (including cancellation).
- Observes the conflated `networkStatus: Flow<Boolean>`.
- Calls `onReload()` exactly once per offline→online transition: the emission after a `false`
  emission. The first emission (usually `true` at subscribe time) must **not** reload because the
  prior state was not observed offline.
- Repeated `true` emissions without an intervening `false` do not reload.
- The composable must not leak the callback after disposal.

Consumer: `HomeScreen` passes `viewModel::refresh`.

---

## 8. `Screens` — top-level tab model

`@Immutable sealed class Screens(@StringRes titleId, @DrawableRes iconIdInactive,
@DrawableRes iconIdActive, val route: String)`.

| Object | `route` | `titleId` | inactive icon | active icon |
|---|---|---|---|---|
| `Home` | `"home"` | `R.string.home` | `home_outlined` | `home_filled` |
| `Podcast` | `"podcasts"` | `R.string.podcast` | `radio` | `radio` |
| `Search` | `"search_input"` | `R.string.search` | `search` | `search` |
| `ListenTogether` | `"listen_together"` | `R.string.together` | `group_outlined` | `group_filled` |
| `Library` | `"library"` | `R.string.filter_library` | `library_music_outlined` | `library_music_filled` |
| `Generate` | `"generate"` | `R.string.playlists` | `playlist_play` | `playlist_play` |

`companion object { val MainScreens = listOf(Home, Podcast, Search, ListenTogether, Library) }` —
order is the bottom-bar/rail order and the source of `NavController.backToMain()`'s "main routes"
set. Consumers: `AppNavigation`, floating nav bar/toolbar, settings ("Together" shortcut),
`NavControllerUtils`.

---

## 9. Navigation graph

`@OptIn(ExperimentalMaterial3Api::class) fun NavGraphBuilder.navigationBuilder(
navController: NavHostController, scrollBehavior: TopAppBarScrollBehavior, activity: Activity,
snackbarHostState: SnackbarHostState)`.

All destinations are declared inside the single `NavHost` of the shell, whose start destination is
`Screens.Home.route`, `Screens.Library.route` or `"home"` per §4.5.

### 9.1 Destinations and arguments

String arguments are URL-decoded by the destination where they represent user text
(`search/{query}`); ids used in routes are URL-encoded by the sender.

| Route | Arguments / defaults | Screen entry point |
|---|---|---|
| `home` | — | `HomeScreen(navController, snackbarHostState)` |
| `podcasts` | — | `PodcastsScreen(navController)` |
| `search_input` | — | `SearchScreen(navController, pureBlack)` where `pureBlack` follows §4.5 |
| `library` | — | `LibraryScreen(navController)` |
| `listen_together` | — | `ListenTogetherScreen(navController, showTopBar = false)` |
| `listen_together_from_topbar` | — | `ListenTogetherScreen(navController, showTopBar = true)` |
| `listen_together/chat` | — | `CommentTogetherScreen(navController)` |
| `history` | — | `HistoryScreen(navController)` |
| `ambient_mode` | — | `AmbientModeScreen(navController)` |
| `music_flow` | — | `MusicFlowScreen(navController)` |
| `podcast_video` | — | `PodcastVideoScreen(navController)`; fade in/out 300 ms on all four transitions |
| `local_songs` | — | `LocalSongScreen(navController)` |
| `stats` | — | `StatsScreen(navController)` |
| `wrapped` | — | `WrappedScreen(navController)` |
| `mood_and_genres` | — | `MoodAndGenresScreen(navController, scrollBehavior)` |
| `account` | — | `AccountScreen(navController, scrollBehavior)` |
| `new_release` | — | `NewReleaseScreen(navController, scrollBehavior)` |
| `charts_screen` | — | `ChartsScreen(navController)` |
| `browse/{browseId}` | `browseId: String` | `BrowseScreen(navController, scrollBehavior, browseId)` |
| `search/{query}` | `query: String` | `OnlineSearchResult(navController)`; see §9.2 transitions |
| `album/{albumId}` | `albumId: String` | `AlbumScreen(navController, scrollBehavior)` |
| `artist/{artistId}` | `artistId: String` | `ArtistScreen`, or redirect for podcast shows (§9.3) |
| `artist/{artistId}/songs` | `artistId: String` | `ArtistSongsScreen` |
| `artist/{artistId}/albums` | `artistId: String` | `ArtistAlbumsScreen` |
| `artist/{artistId}/items?browseId={browseId}?params={params}` | `artistId` String; `browseId`, `params` nullable String | `ArtistItemsScreen` |
| `online_playlist/{playlistId}` | `playlistId: String` | `OnlinePlaylistScreen` |
| `spotify_playlist/{playlistId}?title={title}&cover={cover}` | `playlistId` String; `title` String default `"Playlist"`; `cover` String default `""` | `SpotifyPlaylistDetailScreen(navController, scrollBehavior, playlistId, title, cover)` |
| `local_playlist/{playlistId}` | `playlistId: String` | `LocalPlaylistScreen` |
| `auto_playlist/{playlist}` | `playlist: String` | `AutoPlaylistScreen` |
| `cache_playlist/{playlist}` | `playlist: String` | `CachePlaylistScreen` |
| `top_playlist/{top}` | `top: String` | `TopPlaylistScreen` |
| `bottom_playlist/{bottom}` | `bottom: String` | `BottomPlaylistScreen` |
| `youtube_browse/{browseId}?params={params}` | `browseId`, `params` nullable String | `YouTubeBrowseScreen` |
| `settings` | — | `SettingsScreen(navController, scrollBehavior)` |
| `blocked_artists` | — | `BlockedArtistsScreen` |
| `disliked_songs` | — | `DislikedSongsScreen` |
| `settings/update?highlightKey={highlightKey}` | nullable String | `UpdateSettings(..., highlightKey)` |
| `settings/account?highlightKey={highlightKey}` | nullable String | `AccountSettingsScreen(..., highlightKey)` |
| `settings/integrations?highlightKey={highlightKey}` | nullable String | `AccountSettingsScreen(..., highlightKey ?: "Integrations")` |
| `ambient_settings` | — | `AmbientSettingsScreen` |
| `settings/ambient` | — | `AmbientSettingsScreen` |
| `settings/appearance?highlightKey={highlightKey}` | nullable String | `AppearanceSettings(..., activity, snackbarHostState, highlightKey)` |
| `settings/appearance/theme` | — | `ThemeScreen` |
| `settings/music_flow` | — | `MusicFlowSettings` |
| `settings/appearance/haptics?highlightKey={highlightKey}` | nullable String | `HapticsSettings(..., activity, snackbarHostState, highlightKey)` |
| `settings/appearance/app_icon` | — | `AppIconSettingsScreen(navController, activity, snackbarHostState)` |
| `settings/appearance/font` | — | `FontSelectionScreen` |
| `settings/listening_summary` | — | `ListeningSummaryScreen` |
| `detailed_listening_history/{startTimestamp}` | String | `DetailedListeningHistoryScreen` |
| `settings/appearance/liquidglass` | — | `GlassEffectSettings` |
| `settings/content?highlightKey={highlightKey}` | nullable String | `ContentSettings` |
| `uptime` | — | `UptimeScreen` |
| `settings/ai?highlightKey={highlightKey}` | nullable String | `AiSettings` |
| `settings/player?highlightKey={highlightKey}` | nullable String | `PlayerSettings` |
| `settings/storage?autoOpenExportPicker={autoOpenExportPicker}&highlightKey={highlightKey}` | Bool default `false`; nullable String | `StorageSettings` |
| `settings/equalizer` | — | `AxionEqScreen(onBackClick = { navController.navigateUp() })` |
| `settings/privacy?highlightKey={highlightKey}` | nullable String | `PrivacySettings` |
| `settings/backup_restore?highlightKey={highlightKey}` | nullable String | `BackupAndRestore` |
| `settings/extensions` | — | `ExtensionSettingsScreen` |
| `settings/lastfm` | — | `LastFMSettingsScreen` |
| `settings/spotify_import` | — | `SpotifyImportScreen` |
| `settings/spotify_login` | — | `SpotifyLoginScreen`; resolves `SpotifyImportViewModel` from the parent `settings/spotify_import` entry when present, else creates one; `onCookiesCaptured(spDc, spKey)` calls `connectWithCookies` |
| `settings/integrations/listen_together` | — | `ListenTogetherSettings` |
| `settings/about?highlightKey={highlightKey}` | nullable String | `AboutScreen` |
| `update` | — | `UpdateScreen` |
| `login` | — | `LoginScreen` |
| `equalizer` (declared with `dialog`) | — | `EqScreen(navController)` — must render as a dialog destination, not a full screen |
| `recognition` | — | `RecognitionScreen` |
| `recognition_history` | — | `RecognitionHistoryScreen` |
| `settings/changelog` | — | `ChangelogScreen` |
| `settings/commits` | — | `CommitScreen` |
| `generate` | — | `GenerateScreen` |
| `settings/navidrome_login` | — | `NavidromeLoginScreen(navController, entryPoint.navidromeRepository())` |
| `settings/navidrome_dashboard` | — | `NavidromeDashboardScreen(navController, entryPoint.navidromeRepository())` |
| `settings/jellyfin_login` | — | `JellyfinLoginScreen(navController, entryPoint.jellyfinRepository())` |
| `settings/jellyfin_dashboard` | — | `JellyfinDashboardScreen(navController, entryPoint.jellyfinRepository())` |

Cloud screens obtain `CloudEntryPoint` with
`EntryPointAccessors.fromApplication(context.applicationContext, CloudEntryPoint::class.java)`.

### 9.2 `search/{query}` transitions

- enter: `fadeIn(250)`.
- exit: `fadeOut(200)` when the target route also starts with `search/`, else
  `fadeOut(200) + slideOutHorizontally { -it / 2 }`.
- popEnter: `fadeIn(250)` when the initial route starts with `search/`, else
  `fadeIn(250) + slideInHorizontally { -it / 2 }`.
- popExit: `fadeOut(200)`.

### 9.3 Podcast-show redirect in `artist/{artistId}`

If `PodcastPage.isShowBrowseId(artistId)` is true, the destination must not render an artist page:
it navigates to `online_playlist/${PodcastPage.showId(artistId)}` with
`popUpTo(backStackEntry.destination.id) { inclusive = true }` and shows nothing in the meantime.
`artistId` must be non-null for this branch (`showId` accepts a non-null String).

### 9.4 Route invariants

- `Screens.MainScreens.map { it.route }` must all exist in this graph.
- Navigation route strings are public contract: deep links, widgets and other screens build them.
  Do not rename, add a leading slash, or change argument names.
- Argument names exactly as tabled (`browseId`, `query`, `albumId`, `artistId`, `playlistId`,
  `title`, `cover`, `playlist`, `top`, `bottom`, `params`, `highlightKey`,
  `autoOpenExportPicker`, `startTimestamp`).

---

## 10. DPI scaler (`com.revenge.dpi`)

### 10.1 `BaseLifecycleContentProvider`

`abstract class BaseLifecycleContentProvider : ContentProvider()`. Every CRUD operation is a
no-op: `query`/`getType` → `null`, `insert` → `null`, `delete`/`update` → `0`, `onCreate` → true.
It exists only to give providers a lifecycle hook.

### 10.2 `ActivityLifecycleManager`

`abstract class ActivityLifecycleManager : BaseLifecycleContentProvider()`.

- `onCreate()` obtains the `Application` (via `ActivityThread.currentActivityThread()` /
  `getApplication()` reflection; failing that, returns `true`) and, if `onInit(application)`
  returns true, registers an `Application.ActivityLifecycleCallbacks`.
- Tracks resumed activities in a thread-safe set. It starts a main-looper periodic runnable on
  resume and stops/restarts it appropriately; the runnable calls `onActivityTimer(activity)` for
  each active activity every `activityTimerDelayMillis` (open, default `3000` ms). Individual
  timer failures are caught so one bad activity cannot kill the timer.
- Open hooks: `onInit(Application): Boolean = true`, `onActivityCreated/Started/Resumed/Paused/
  Stopped/Destroyed(Activity)`, `onActivityTimer(Activity)`. `onActivitySaveInstanceState` is a
  no-op.
- The manager must never crash the process if reflection or an individual callback fails.

### 10.3 `DensityConfiguration`

`internal class DensityConfiguration(private val densityScale: Float) : ActivityLifecycleManager()`.

- `fun applyDensityScaling(context: Context)`:
  - if `densityScale == 1.0f`, do nothing (no callbacks registered, no resource change);
  - otherwise register lifecycle callbacks by invoking its own `onCreate`, capture the current
    `Resources.configuration.densityDpi` as the original, and set
    `densityDpi = round(original * densityScale)` via
    `Resources.updateConfiguration(config, displayMetrics)`.
  - Any exception is logged and swallowed.
- On `onActivityCreated`, `onActivityStarted` and `onActivityResumed`, re-apply the same computed
  density to the activity's resources. Re-application is **idempotent**: it always derives from
  the captured original, never from an already-scaled value.
- `densityScale` is a plain ratio; `0.75`, `1.0`, `1.25` are all valid. Negative/zero scales are
  not special-cased, but an exception during application must not crash.
- `densityScale == 1.0f` must be observationally identical to the provider being absent.

### 10.4 `DensityScaler`

`class DensityScaler : BaseLifecycleContentProvider()`.

- `onCreate()`: missing context → return `false`; otherwise read the scale from
  `SharedPreferences("revenge_settings", MODE_PRIVATE)` float key
  `"density_scale_factor"` (default `1.0f`, exceptions → `1.0f`), call
  `DensityConfiguration(scale).applyDensityScaling(context)` (which also registers the activity
  callbacks), and return `true`.
- Preferences contract shared with `AppearanceSettings`: writing a new scale writes the same
  float key to the same SharedPreferences file and requires an app restart to take effect.
- The provider must never throw from `onCreate`; a malformed preference leaves density unscaled.

---

## 11. Manifest, DI and cross-module contracts

### 11.1 Application and entry components (exact attributes)

- `<application android:name=".App" android:allowBackup="true" android:appCategory="audio"
  android:dataExtractionRules="@xml/data_extraction_rules"
  android:enableOnBackInvokedCallback="true" android:extractNativeLibs="true"
  android:fullBackupContent="@xml/backup_rules" android:icon="@mipmap/ic_launcher"
  android:label="@string/app_name" android:largeHeap="true"
  android:networkSecurityConfig="@xml/network_security_config"
  android:roundIcon="@mipmap/ic_launcher_round" android:supportsRtl="false"
  android:theme="@style/Theme.Revenge" tools:targetApi="tiramisu">`.
- `.ui.screens.CrashActivity`: `exported="false"`, `theme="@style/Theme.Revenge"`,
  `excludeFromRecents="true"`, `process=":crash"`.
- `.MainActivity`: `exported="true"`, `launchMode="singleTask"`,
  `theme="@style/Theme.Revenge"`,
  `configChanges="orientation|screenSize|screenLayout|smallestScreenSize|uiMode"`,
  `windowSoftInputMode="adjustResize"`, with:
  - `MAIN` action filter (no LAUNCHER category; the launcher entry is the alias);
  - `VIEW` + `MUSIC_PLAYER` filter with categories `LEANBACK_LAUNCHER`, `APP_MUSIC`, `DEFAULT`;
  - `MEDIA_PLAY_FROM_SEARCH` filter (DEFAULT) for Assistant;
  - verified HTTPS app link filter (`autoVerify="true"`, DEFAULT + BROWSABLE) for
    `https://metroserverx.meowery.eu` `pathPrefix="/listen"`;
  - custom scheme filter for `revenge://listen` (DEFAULT + BROWSABLE);
  - YouTube filters (`autoVerify="true"`) over `http`/`https` hosts `youtube.com`,
    `m.youtube.com`, `www.youtube.com`, `music.youtube.com`, paths `v/`, `embed/`, `watch`,
    `channel/`, `user/`, `c/`, `playlist`, `browse/`, `search`, plus `youtu.be` path `/`, plus
    schemes `vnd.youtube` and `vnd.youtube.launch`; these also accept
    `MEDIA_PLAY_FROM_SEARCH` and `NDEF_DISCOVERED`;
  - `SEND` filter with `text/plain` (share sheet);
  - `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts"/>`.
- Aliases targeting `.MainActivity`:
  - `.MainActivityAlias`: `enabled="true"`, `exported="true"`, launcher icons
    `@mipmap/ic_launcher`/`ic_launcher_round`, `MAIN` + `LAUNCHER` filter.
  - `.MainActivityStatic`: `enabled="false"`, `exported="true"`, icons
    `@mipmap/ic_launcher_static`/`ic_launcher_static_round`, `MAIN` + `LAUNCHER` filter.
  - `IconUtils.setIcon` toggles these two component states with `DONT_KILL_APP`; the manifest
    defaults must make exactly one launcher entry visible.
- Providers:
  - `androidx.core.content.FileProvider` authority `${applicationId}.FileProvider`,
    `exported="false"`, `grantUriPermissions="true"`, meta-data
    `android.support.FILE_PROVIDER_PATHS = @xml/provider_paths`.
  - `com.revenge.dpi.DensityScaler` authority
    `${applicationId}.com.revenge.dpi.DensityScaler`, `enabled="true"`, `exported="false"`.
  - `androidx.startup.InitializationProvider` merged with
    `<meta-data android:name="androidx.work.WorkManagerInitializer" tools:node="remove"/>`
    (WorkManager on-demand init; required by §3.3).
- `MusicService` must be declared `exported="true"`,
  `foregroundServiceType="mediaPlayback"`, with the media3 session/media-library/media-browser
  actions; the entry cluster binds it (§4.3) and voice search reaches it (§4.4.4). Its internals
  belong to the playback cluster.
- The notification permission request (§4.3) requires `POST_NOTIFICATIONS` in the manifest.
  `CrashActivity` sharing requires no extra permission beyond the FileProvider.
- Keep the remaining receivers/services from the baseline (widgets, listen-together action
  receiver, recognition) unchanged in the manifest; removing one breaks cross-cluster intents.

### 11.2 DI graph

`AppModule` (`@Module @InstallIn(SingletonComponent::class) object`):

| Provider | Return type | Contract |
|---|---|---|
| `provideApplicationScope()` `@Singleton @ApplicationScope` | `CoroutineScope` | `SupervisorJob() + Dispatchers.Default`; injected into `App`. |
| `provideDao` | `DatabaseDao` | `InternalDatabase.dao`. |
| `provideSongPlayStatsDao` | `SongPlayStatsDao` | `InternalDatabase.songPlayStatsDao`. |
| `provideRecommendationExclusionDao` | `RecommendationExclusionDao` | corresponding DAO. |
| `provideTasteProfileDao` | `TasteProfileDao` | corresponding DAO. |
| `provideLastFmTasteApi(RealLastFmTasteApi)` | `LastFmTasteApi` | binds real implementation. |
| `providePreferencesDataStore(context)` | `DataStore<Preferences>` | `context.dataStore` (file `settings`). |
| `provideGenerationStatus(RealGenerationStatus)` | `GenerationStatus` | binds real. |
| `provideLocalTasteEngine(RecommendationEngine)` | `LocalTasteEngine` | recommendation engine implements it. |
| `provideDatabase(InternalDatabase)` | `MusicDatabase` | wraps internal DB. |
| `provideInternalDatabase(context)` | `InternalDatabase` | Room builder with the checked-in migrations `MIGRATION_1_2`, `21_24`, `22_24`, `24_25`, `27_28`, `28_29`, `29_30`, `31_32`, `36_37`, `37_38`, `38_39`, `39_40`, `40_41`, `41_42`, `42_43`, `43_44`, `44_45`, `45_46`; WAL journal; fixed 4-thread transaction and query executors; `onOpen` PRAGMAs `busy_timeout=60000`, `cache_size=-16000`, `wal_autocheckpoint=1000`, `synchronous=NORMAL`, plus the legacy ALAC/format cleanup statement. Failures are logged, never fatal. |
| `provideDatabaseProvider(context)` | `DatabaseProvider` | `StandaloneDatabaseProvider`. |
| `providePlayerCache(context, provider)` `@PlayerCache` | `SimpleCache` | dir `filesDir/exoplayer`; `MaxSongCacheSizeKey` `-1` → `NoOpCacheEvictor`, otherwise `DynamicLruCacheEvictor(sizeMiB * 1024 * 1024)`. |
| `provideDownloadCache(context, provider)` `@DownloadCache` | `SimpleCache` | dir `filesDir/download`, `NoOpCacheEvictor`. |
| `provideListenTogetherClient(context)` | `ListenTogetherClient` | singleton. |
| `provideListenTogetherManager(context, client)` | `ListenTogetherManager` | singleton. |

`DomainBindingsModule` (`abstract class`, `@Binds @Singleton`):
`SongRepositoryImpl → SongRepository`, `PlaylistRepositoryImpl → PlaylistRepository`,
`MusicFlowRepository → MusicFlowFeedSource`.

`NetworkModule`: `@Provides @Singleton fun provideNetworkConnectivityObserver(@ApplicationContext
context): NetworkConnectivityObserver` = `NetworkConnectivityObserver(context)` (class lives in
`:core`).

`LyricsHelperEntryPoint` (`@EntryPoint @InstallIn(SingletonComponent::class) interface`):
`fun lyricsHelper(): LyricsHelper`. Consumed by `Player.kt` and `PlayerSyncedLyricsView.kt` via
`EntryPointAccessors.fromApplication`.

### 11.3 Cross-module types the entry code compiles against

- `com.revenge.music.playback.MusicService` + `inner class MusicBinder : Binder()` exposing
  `service: MusicService`.
- `PlayerConnection(context, binder: MusicBinder, database: MusicDatabase, scope: CoroutineScope)`
  with `isPlaying: StateFlow<Boolean>`, `isEffectivelyPlaying: Flow<Boolean>`,
  `shuffleModeEnabled: Flow<Boolean>`, `mediaMetadata: Flow<MediaMetadata?>`, `player`,
  `service`, `playQueue(...)`, `dispose()`. Used by the shell, deep links, voice search.
- `ListenTogetherManager.initialize()`, `setPlayerConnection(PlayerConnection?)`,
  `joinRoom(roomCode: String, username: String)`.
- `HomeViewModel.accountImageUrl: StateFlow<String?>`, obtained with `hiltViewModel()`.
- `NetworkConnectivityObserver` with `networkStatus: Flow<Boolean>` and `unregister()`.
- `com.revenge.music.utils.dataStore` (`Context.dataStore`), `DataStore.get(key)`,
  `DataStore.get(key, default)`, `rememberPreference`, `rememberEnumPreference`.
- Preference keys referenced above: `MaxImageCacheSizeKey`, `ContentCountryKey`,
  `ContentLanguageKey`, `AppLanguageKey`, `ProxyEnabledKey`, `ProxyUsernameKey`,
  `ProxyPasswordKey`, `ProxyTypeKey`, `ProxyUrlKey`, `UseLoginForBrowse`, `IpVersionKey`,
  `PlaybackEngineKey`, `VisitorDataKey`, `DataSyncIdKey`, `InnerTubeCookieKey`,
  `AccountNameKey`, `AccountEmailKey`, `AccountChannelHandleKey`, `DarkModeKey`,
  `PureBlackKey`, `IsMonochromeThemeKey`, `SelectedThemeColorKey`,
  `AlbumArtThemeRecoloringKey`, `EnableHighRefreshRateKey`, `DefaultOpenTabKey`,
  `ListenTogetherInTopBarKey`, `ListenTogetherUsernameKey`, `StopMusicOnTaskClearKey`,
  `DisableScreenshotKey`, `AiRecommendationsKey`, `LastAiRecommendationUpdateDayKey`,
  `ExportingSongIdsKey`, `ExportedSongIdsKey`, `ExportProgressKey`,
  `LastOpenedVersionCodeKey`, `PauseSearchHistoryKey`, `BlockedArtistsKey`, `DislikedSongsKey`,
  `MusicFlowEnabledKey`, `UseNewMiniPlayerDesignKey`, `PauseListenHistoryKey`, plus the liquid
  glass keys and `ForceOpusKey`. Keys live in `:core` (`PreferenceKeys.kt`) and must keep their
  string names; changing one is a data migration, not a rename.
- `Screens` consumers: `AppNavigation`, `FloatingNavBar`, `FloatingNavigationToolbar`,
  `SettingsScreen`, `NavControllerUtils`.
- `NavigationTab` (`HOME`, `SEARCH`, `LIBRARY`), `DarkMode` (`ON`, `OFF`, `AUTO`).
- `PodcastPage.isShowBrowseId(String?)`, `PodcastPage.showId(String)`.
- `validOnlinePlaylistId(String?)`, `onlinePlaylistRoute(String?)`.
- `YouTube.queue(videoIds, playlistId)`, `YouTube.albumSongs(playlistId)`, `YouTubeLocale`,
  `IpVersion`, `WatchEndpoint`, `SongItem.toMediaMetadata()`.
- `MusicDatabase` (`query { insert(SearchHistory(...)) }`, `eventCount()`).
- `DownloadUtil`, `SyncUtils`, `LocalTasteEngine`, `RingtoneViewModel`, `GlassEffectConfig`,
  `AiRecommendationHelper`, `UpdateNotificationHelper`, updater functions
  (`getLastSeenChangelogVersion`, `saveLastSeenChangelogVersion`, `fetchChangelogForVersion`,
  `checkForUpdate`, `getAutoUpdateCheckSetting`, `getUpdateNotificationsSetting`,
  `saveUpdateAvailableState`), `NewEpisodesWorker.schedule`, `CipherDeobfuscator.initialize`,
  `YTPlayerUtils.initialize`, `InnerTubeXResolver.init/onSessionChanged`, `LastFM.initialize`,
  `CrashHandler.install`, `AppContextHolder.initialize`.
- `CloudEntryPoint.navidromeRepository()` / `jellyfinRepository()`.

### 11.4 Resources referenced by this cluster

Drawables: `home_outlined`, `home_filled`, `radio`, `search`, `group_outlined`, `group_filled`,
`library_music_outlined`, `library_music_filled`, `playlist_play`, `more_vert`,
`music_history`, `stats`, `mic`, `settings`, `swipe_vertical`, `shuffle`, `ic_revenge_brain`,
`arrow_forward`, `content_copy`, `close`, `share`, `shortcut_search`, `shortcut_library`.

Strings (non-exhaustive but required): `app_name`, `home`, `podcast`, `search`, `together`,
`filter_library`, `playlists`, `more_options`, `account`, `settings`, `history`, `stats`,
`recognition`, `music_flow`, `shuffle`, `ai_lyrics_translation`, `continue_action`,
`crash_no_log`, `crash_title`, `copy_logs`, `crash_close`, `crash_share_logs`,
`crash_description`, `copied_to_clipboard`, `crash_report_subject`, `crash_share_title`,
`invalid_playlist_url`, `failed_to_parse_proxy`, `update_channel_name`, `update_channel_desc`.

---

## 12. Acceptance tests

Tests must be deterministic: no network, no clock dependence beyond injected values, no reliance
on device-specific display modes or DPI.

### 12.1 `AppProcessRoutingTest` (Robolectric)

- With process name `com.revenge.music`: `onCreate` initialises `AppContextHolder`, schedules
  `NewEpisodesWorker`, initialises cipher/YT player, plants Timber only in debug, and starts the
  settings collectors. Use Robolectric shadows to assert worker scheduling and no exception.
- With process name `com.revenge.music:crash`: the default uncaught handler is installed, and
  `AppContextHolder` is **not** initialised, no worker is scheduled, and the settings DataStore is
  not read.
- With process name `com.revenge.music:reconnection_service`: the handler is installed and the
  function returns without initialising the context holder or workers.
- `startForegroundService` on API 31 with a `ForegroundServiceStartNotAllowedException` returns
  `null` and does not rethrow; on other exceptions it rethrows.

### 12.2 `DensityScalerTest` (Robolectric)

- Scale `1.0`: `DensityScaler.onCreate()` returns true and leaves `Resources.configuration
  .densityDpi` unchanged.
- Scale `1.25` with original 160: provider applies `200`; a second activity `onActivityResumed`
  keeps `200` (no compounding). Scale `0.75` with original 160 → `120`.
- Missing/foreign prefs → default `1.0`, no change.
- Corrupt prefs (wrong type) → `1.0`, no throw.
- `ActivityLifecycleManager` timer invokes `onActivityTimer` only while an activity is resumed,
  and a callback throwing does not cancel subsequent ticks.

### 12.3 `MainActivity` singleTask/deep-link tests (instrumented)

- Launch with `ACTION_VIEW` `https://music.youtube.com/watch?v=ABC&list=PL1`: the player queue
  starts (or a queue request is recorded); the activity's `intent.data` is null afterwards.
- Deliver a second identical intent through `onNewIntent`: it is not replayed (data already
  consumed); the current screen stays stable.
- `revenge://listen/ROOM42` joins the listen-together room with username from prefs or `"Guest"`
  and does not navigate.
- `https://metroserverx.meowery.eu/listen?code=XYZ` also joins.
- `https://www.youtube.com/playlist?list=OLAK5uy_x` resolves an album and navigates
  `album/<browseId>`.
- Invalid playlist list id shows the `invalid_playlist_url` toast and does not navigate.
- `SEND` with `text/plain` containing a YouTube URL runs the same deep-link path.
- Cold-start voice search: a `MEDIA_PLAY_FROM_SEARCH` intent with `SearchManager.QUERY` plays once;
  recreating the activity with the same intent does not play again (handled flag).
- `ACTION_RECOGNITION` with `auto_start_recognition=true` navigates to
  `recognition?autoStart=true`; without the extra, `recognition`.

### 12.4 Process recreation / back stack

- Recreate the activity (rotation is a `configChanges` event and must not recreate; simulate
  process death instead) after navigating to `artist/{id}`: the NavHost rebuilds from
  `defaultOpenTab`, the previous `pendingIntent` is consumed exactly once, and no duplicate
  navigation occurs.
- Tab reselect sets `scrollToTop` in the current destination's `SavedStateHandle` and does not
  push a new entry.
- Switching tabs and returning restores the tab's saved back stack (`restoreState`).
- `StopMusicOnTaskClearKey = true`, playback active, activity finishing → `MusicService` stopped.
  Same preference with a non-finishing destroy (configuration) → service untouched.
- `onStart` requests `POST_NOTIFICATIONS` only on API 33+ when not granted; denial leaves the app
  fully usable.
- Unbind on `onStop`; a second `onDestroy`/late service callback does not crash.

### 12.5 `ScreensContractTest` (JVM)

- Exact route strings per §8.
- `MainScreens` equals `[Home, Podcast, Search, ListenTogether, Library]` in that order.
- `@Immutable` annotation is present on the class (reflection or source check).

### 12.6 `NavigationGraphContractTest`

- Build a `TestNavHostController` and the graph via `navigationBuilder`; assert every route in
  §9.1 resolves (`graph.findNode(route)`), every argument name/type/default matches, and
  `equalizer` is a dialog destination.
- Assert every `Screens.MainScreens` route exists.
- Assert `artist/{artistId}` redirects when `artistId` is a podcast show id and otherwise renders
  the artist destination.
- Assert `search/{query}` transition lambdas are non-null and the `search` family uses fades.
- Assert `settings/spotify_login` resolves its parent `settings/spotify_import` view-model entry
  when that entry exists and still composes when it does not.

### 12.7 `NetworkReloadTest` (coroutine test)

Using a fake observer flow `[true, false, false, true, true, true, false, true]`, `onReload` is
called exactly twice (after the first and second offline→online transitions), never on the initial
`true`, never on repeated `true`, and never after cancellation/unregistration.

### 12.8 `WelcomeGateTest`

- `lastOpened = -1`, version `10` → shown.
- `lastOpened = 9`, version `10` → shown.
- `lastOpened = 10`, version `10` → not shown.
- Dismissing writes `LastOpenedVersionCodeKey = versionCode`; a second launch at the same version
  does not show it.

### 12.9 `CrashScreenSemanticsTest` (Compose semantics)

- The top bar exposes nodes with content descriptions `copy_logs` and `crash_close`; the FAB has
  the visible label `crash_share_logs`.
- With a 10,000-character log, the full text is present in the semantics tree (no ellipsis) and
  the screen scrolls vertically and horizontally.
- Null log renders `crash_no_log`.
- Copy places the exact crash-log text on the clipboard and shows the toast.
- Share writes a file whose name matches `revenge_crash_\d{8}_\d{6}\.txt` and whose content equals
  the log; when FileProvider fails, the chooser still receives `EXTRA_TEXT`.
- Close calls `finishAffinity`.

### 12.10 Screenshot/preview targets

- `CrashScreen` preview with a sample multi-line log, light and dark.
- `WelcomeDialog` preview, light and dark, and at 200 % font scale.
- Shell preview at compact width (bottom bar) and at ≥ desktop breakpoint (rail) with a fake
  player connection and no network.
- Empty/offline Home preview must show the offline state and recover through `NetworkReload`.

### 12.11 Manifest assertions (Robolectric or lint)

Parsed manifest contains exactly the attributes in §11.1: `CrashActivity` in `:crash` and
non-exported; `MainActivity` `singleTask`/exported with the listed `configChanges`; both aliases
present with the stated enabled defaults; `DensityScaler` authority/exported state;
`WorkManagerInitializer` removed; FileProvider authority and grants.

---

## 13. Out of scope

- Internals of every destination screen (owned by their own clusters); this spec fixes only the
  route, argument, transition and entry-point contract.
- `MusicService`, `PlayerConnection` playback logic, widget rendering, recognition capture, update
  downloading, cloud APIs and extension loading.
- Room schema/migrations beyond the fact that `AppModule` registers the existing migration list.
- Visual styling details beyond the accessibility and state contracts above.

Any implementation that satisfies §3–§12 is acceptable.
