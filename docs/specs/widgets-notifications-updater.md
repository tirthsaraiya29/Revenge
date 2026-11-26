# Clean-Room Specification — Widgets, Notifications, Recognition, Updater

Status: M2 specification (behavioural contract only).
Cluster: `widgets-notifications-updater`.
Packages covered: `com.revenge.music.widget`, `com.revenge.music.updates`,
`com.revenge.music.updates.{updater,updater.downloadmanager,changelog,commitscreen,component,shapes}`,
`com.revenge.music.quicksettings`, `com.revenge.music.recognition`,
`com.revenge.music.utils.IconUtils`, plus `res/xml` and the manifest entries owned by this cluster.

This document describes observable behaviour, public API shape, lifecycle and security rules. It
deliberately omits statement-level implementation and Compose subtree structure. Any implementation
that satisfies the contracts and acceptance tests is acceptable. <br/>
Implementation lives in `:app` (`namespace = "com.revenge.music"`, `minSdk = 26`,
`targetSdk = 36`, `compileSdk = 37`).

---

## 1. Cluster purpose and boundaries

This cluster owns:

1. The home-screen widget family (ten player/quick-access widgets plus the recognizer widget):
   RemoteViews rendering, widget sizing, widget button intents, playlist quick picks, and the
   scrollable adaptive queue.
2. Platform entry points that expose app features outside the activity: app shortcuts, the Quick
   Settings tile, and broadcast receivers.
3. Music recognition: microphone capture, resample/fingerprint pipeline, the widget-side and
   app-side foreground services, and their notifications.
4. The GitHub updater: update check, download worker, download notifications, install handoff,
   background-reminder notification, changelog and commit screens.
5. Audio-output UI that lives in the `updates` package by historical accident
   (`AudioDeviceBottomSheet`, `BluetoothUtils`) and the launcher-icon switcher (`IconUtils`).

Out of scope for this cluster (implemented elsewhere): the media player itself, the media3 session
and its playback notification (`RevengeNotificationProvider` is the only session-notification
wrapper described here), settings screens that merely call this cluster's preferences, and the
database schema (contracts are reproduced in §9).

Hard compatibility rule: every public/internal type, name, constant value, parameter name/order,
default value and manifest component name in this document is consumed by other modules. Changing
any of them breaks compilation or runtime behaviour elsewhere.

---

## 2. Shared widget model

### 2.1 `WidgetQueueItem` — `com.revenge.music.widget`

```kotlin
data class WidgetQueueItem(
  val index: Int,
  val title: String,
  val artist: String,
  val isCurrent: Boolean,
)
```

`index` is the player media-item index used for skip-to-item. `title`/`artist` are already
display-ready (unknowns are empty strings). `isCurrent` is false for every item currently produced by
`MusicService`; the field exists so an adapter may mark the playing row.

### 2.2 `RevengeWidgetManager` — `com.revenge.music.widget`

`@Singleton`, constructor-injected (Hilt) with `@ApplicationContext Context`, `MusicDatabase`,
`PlaylistWidgetManager`. Public surface:

```kotlin
suspend fun updateWidgets(
  title: String,
  artist: String,
  artworkUri: String?,
  isPlaying: Boolean,
  isLiked: Boolean,
  duration: Long = 0,
  currentPosition: Long = 0,
  isShuffle: Boolean = false,
  repeatMode: Int = 0,
  isMuted: Boolean = false,
  queueItems: List<WidgetQueueItem> = emptyList(),
)
```

Contract:

- Updates every widget kind that currently has at least one bound instance; kinds with zero
  instances are skipped. It never binds widgets and never throws for "no widgets".
- Always forwards to `PlaylistWidgetManager.updateWidgets(title, artist, artworkUri, isPlaying,
  isLiked, duration, currentPosition)` as the final step, even when no player widget exists.
- `repeatMode` uses media3 `Player.REPEAT_MODE_*` integer values (0 off, 1 one, 2 all).
- `isMuted` is accepted for API compatibility and may be unused by the rendered layouts.
- `queueItems` is capped by the caller at 50 entries.
- Artwork pipeline:
  - If `artworkUri` is non-null and equals the previously cached URI and a cached bitmap exists,
    reuse the cached album art, circular art, rounded art, banner art and accent colour.
  - Otherwise decode `artworkUri` at 400×400 maximum into software bitmaps, centre-crop to square,
    and derive: circular art, rounded art (corner radius 36), banner rounded art (44). On decode
    failure all art is `null`.
  - Accent colour: AndroidX Palette with `maximumColorCount(16)`; first non-null of vibrant,
    light-vibrant, dominant swatch; fallback `0xFFA855F7` (violet). Any failure falls back to the
    same violet. The accent is computed but may be unused by several layouts.
  - A null URI clears the cache; the next update with a URI reloads.
- Progress rendering: for widgets that show a progress bar, `level = 0` when `duration <= 0`,
  otherwise `((currentPosition.toDouble() / duration) * 10000).toInt()`; all redesigned widgets
  clamp the result to `0..10000`. The legacy music widget does not clamp.
- Time text: `formatDuration(ms)` = `totalSeconds = max(0, ms/1000)`, `minutes = total/60`,
  `seconds = total%60`, formatted `"%d:%02d"`; minutes are not converted to hours. Widgets with a
  time row show `"0:00"` for both values when `duration <= 0`.
- Vinyl rotation: when `isPlaying && duration > 0`, angle degrees =
  `(currentPosition % 1800) / 1800 * 360`; otherwise 0. The same angle is baked into the disc bitmap
  for all APIs and additionally applied with `RemoteViews.setFloat(view, "setRotation", angle)`
  only on API 31+. Tonearm rotates to 0° when playing and −18° when paused (API 31+ only).
- Every widget kind is updated through `AppWidgetManager.updateAppWidget(widgetId, views)`.
- `updateWidgets` is suspend and performs image loading on `Dispatchers.IO`; callers must treat it
  as cancellable and concurrency-safe (the cache is accessed from `MusicService`'s scope).

### 2.3 Widget kinds and layouts

All receiver classes are `AppWidgetProvider` subclasses. Except where stated, every receiver extends
`MusicWidgetReceiver`, inherits its button handling, and is registered exported. Layout ids below are
hard contracts because RemoteViews updates target them by id.

| # | Receiver | Layout(s) | Provider XML | Size | Notes |
|---|---|---|---|---|---|
| 1 | `MusicWidgetReceiver` | `widget_compact_square` (2×2), `widget_compact_wide` (4×1), `widget_music_player` (full) | `music_widget_info` | 250×56 min, resize 180×48 | Adaptive by `OPTION_APPWIDGET_MIN_WIDTH/HEIGHT` |
| 2 | `TurntableWidgetReceiver` | `widget_turntable` | `turntable_widget_info` | 2×2 | Circular art, prev/play-pause/next |
| 3 | `RevengeModernWidgetReceiver` | `widget_modern_flow` | `revenge_modern_widget_info` | 4×2 | Cast, like, prev/pause/next, queue |
| 4 | `RevengeSquareWidgetReceiver` | `widget_compact_glow` | `revenge_square_widget_info` | 2×2 | Prev/pause/next |
| 5 | `RevengeShowcaseWidgetReceiver` | `widget_showcase_card` | `revenge_showcase_widget_info` | 4×4 | Logo, banner art, options, cast, like, prev/pause/next |
| 6 | `RevengeSlimWidgetReceiver` | `widget_slim_strip` | `revenge_slim_widget_info` | 4×1 | Progress + times, cast, prev/pause/next |
| 7 | `RevengeVinylWidgetReceiver` | `widget_vinyl_square` | `revenge_vinyl_widget_info` | 2×2 / 3×3 | Rotating disc + tonearm |
| 8 | `RevengeVintageWidgetReceiver` | `widget_vintage_turntable` | `revenge_vintage_widget_info` | 4×2 | Brown control set, shadowed disc |
| 9 | `RevengeAdaptiveWidgetReceiver` | `widget_adaptive_compact`, `widget_adaptive_expanded` | `revenge_adaptive_widget_info` | 2×1 compact ↔ expanded | Shuffle/repeat, scrollable queue |
| 10 | `PlaylistWidgetReceiver` | `widget_playlist` (preview: `widget_playlist_preview`) | `playlist_widget_info` | 4×2 | Quick picks + mini player |
| 11 | `MusicRecognizerWidgetReceiver` | `widget_recognizer_tiny`, `_compact`, `_wide` | `recognizer_widget_info` | 1×1 / 1×3 / 1×4 | State-driven recognition widget |

`widget_device_connect` and `widget_aurora_studio` exist in resources but are not registered or
updated by any receiver; they are not required. `widget_queue_item` is required by the adaptive
queue service.

Provider XML contract (all files):
`minWidth`, `minHeight`, `minResizeWidth`, `minResizeHeight`, `targetCellWidth`,
`targetCellHeight`, `updatePeriodMillis`, `previewLayout`, `initialLayout`,
`description`, `widgetCategory="home_screen"`, `resizeMode="horizontal|vertical"`.
`updatePeriodMillis` is 1800000 for player/playlist widgets and 0 for the recognizer widget.
`initialLayout` is always a valid static representation of the widget's idle state so the launcher
can render before the first update.

### 2.4 Size behaviour

- Legacy music widget (`createRemoteViewsForSize`):
  - `minWidth < 180 && minHeight < 100` → compact square (`widget_compact_square`).
  - `minWidth >= 180 && minHeight < 100` → compact wide (`widget_compact_wide`).
  - otherwise full layout (`widget_music_player`).
- Adaptive widget:
  - API 31+: build a `RemoteViews` mapping `SizeF(200f,80f) -> compact` and
    `SizeF(200f,150f) -> expanded`, so the launcher picks by actual size.
  - Pre-31: use `OPTION_APPWIDGET_MIN_HEIGHT`; `>= 130` → expanded, else compact.
  - After each adaptive update call
    `notifyAppWidgetViewDataChanged(widgetId, R.id.widget_queue_list)`.
- Recognizer widget: `minWidth < 110` → tiny; `< 230` → compact; else wide.
- Playlist widget quick-pick capacity is defined in §3.3.

### 2.5 Legacy music widget view contract

`widget_music_player` ids: `widget_song_title`, `widget_artist_name`, `widget_album_art`,
`widget_play_pause` (icon `ic_widget_pause`/`ic_widget_play`), `widget_like_button`
(`ic_widget_heart_nav`/`ic_widget_heart_outline_nav`), `widget_progress_fill` (level), and click
targets `widget_album_art`, `widget_play_pause_container`, `widget_like_button`.
`widget_compact_wide` ids: `widget_wide_song_title`, `widget_wide_artist_name`,
`widget_wide_album_art`, `widget_wide_play_pause`, `widget_wide_like_button`,
`widget_wide_progress_fill`, click targets `widget_wide_album_art`, `widget_wide_play_container`,
`widget_wide_like_button`.
`widget_compact_square` ids: `widget_compact_album_art`, `widget_compact_play_pause`,
click targets `widget_compact_album_art`, `widget_compact_play_container`.
Missing album art renders a rounded/circular version of the launcher icon (300 px source) or, for the
turntable, `widget_turntable_default_art` made circular.

### 2.6 Redesigned player widgets

Common element ids where present: `widget_song_title`, `widget_artist_name`, `widget_album_art`,
`widget_play_pause`, `widget_like_icon`, `widget_progress_fill`, `widget_time_current`,
`widget_time_duration`, `widget_prev_button`, `widget_play_pause_container`, `widget_next_button`,
`widget_cast_button`, `widget_queue_button`, `widget_options_button`, `widget_vinyl_disc`,
`widget_tonearm`, `widget_vinyl_container`, `widget_soundwave_logo`, plus adaptive-specific ids
listed in §2.8.

Per-widget observables:

| Widget | Text | Art | Controls | Extra |
|---|---|---|---|---|
| Modern Flow | title, artist, time row | rounded 36 | like, prev, play/pause, next, cast→output switcher, queue→queue screen | title/artist/art → now playing |
| Compact Glow | title, artist | rounded 36 | prev, play/pause, next | no like/cast |
| Showcase Card | title, artist, time row | banner rounded 44 | logo/title/artist/art→now playing, like, prev, play/pause, next, cast, options→song options | logo `icon` |
| Slim Strip | title, artist, time row | rounded 32 | prev, play/pause, next, cast | progress |
| Vinyl Square | title, artist, time row | rotating disc | art/title/artist→now playing, prev, play/pause, next | tonearm |
| Vintage Turntable | title, artist, time row | vintage disc with shadow | art/title/artist→now playing, prev, play/pause, next | brown icons |
| Turntable | none | circular art | art→now playing, prev, play/pause, next | secondary-colour controls |
| Adaptive Compact | title, artist | rounded 28 or placeholder icon | shuffle, prev, play/pause, next, repeat; root/art/title/artist→now playing | shuffle/repeat icons reflect state |
| Adaptive Expanded | title, artist | rounded 28 or placeholder icon | same as compact + queue list | list data from `AdaptiveQueueWidgetService` |

Icons:
- play/pause `ic_widget_pause_bold_white` / `ic_widget_play_bold_white` (low variants used by the
  legacy compact layouts; secondary variants by turntable; brown variants by vintage).
- like `ic_widget_heart_filled_clean` when liked, `ic_widget_heart_clean` otherwise (nav variants
  for legacy/playlist).
- shuffle `ic_widget_shuffle_on` / `ic_widget_shuffle_off`.
- repeat: `REPEAT_MODE_ALL → ic_widget_repeat_all`, `REPEAT_MODE_ONE → ic_widget_repeat_one`,
  otherwise `ic_widget_repeat_off`.
- adaptive no-art state hides `widget_album_art` (GONE) and shows `widget_placeholder_icon`
  (VISIBLE); with art the inverse.

### 2.7 Button intents and security

All widget `PendingIntent`s are explicit (target component named) and immutable except the one
mutable case below. The request codes are part of the contract because identical request codes with
`FLAG_UPDATE_CURRENT` overwrite each other:

| Request code | Type | Target / action |
|---|---|---|
| 0 | activity | `MainActivity` (plain open) |
| 1 | broadcast | `MusicWidgetReceiver.ACTION_PLAY_PAUSE` |
| 2 | broadcast | `MusicWidgetReceiver.ACTION_LIKE` |
| 3 | broadcast | `TurntableWidgetReceiver.ACTION_TURNTABLE_PLAY_PAUSE` |
| 4 | broadcast | `TurntableWidgetReceiver.ACTION_TURNTABLE_NEXT` |
| 5 | broadcast | `TurntableWidgetReceiver.ACTION_TURNTABLE_PREVIOUS` |
| 10 | broadcast | `MusicWidgetReceiver.ACTION_PREVIOUS` |
| 11 | broadcast | `MusicWidgetReceiver.ACTION_NEXT` |
| 12 | activity | `MainActivity` `com.revenge.music.action.LIBRARY`, flags NEW_TASK|CLEAR_TOP |
| 13 | activity | media-output panel when resolvable, else `MainActivity` `ACTION_OUTPUT_SWITCHER` |
| 14 | activity | `MainActivity` `ACTION_NOW_PLAYING`, flags NEW_TASK|CLEAR_TOP|SINGLE_TOP |
| 15 | activity | `MainActivity` `ACTION_QUEUE`, same flags |
| 16 | activity | `MainActivity` `ACTION_SONG_OPTIONS`, same flags |
| 17 | activity | `MainActivity` `ACTION_LYRICS`, same flags |
| 901 | broadcast | `MusicWidgetReceiver.ACTION_SHUFFLE` |
| 905 | broadcast | `MusicWidgetReceiver.ACTION_REPEAT` |
| 907 | broadcast | `MusicWidgetReceiver.ACTION_MUTE` |
| 910 + queue index | broadcast | `ACTION_SKIP_TO_QUEUE_ITEM` + `EXTRA_QUEUE_INDEX` |
| 920 | broadcast | adaptive ListView `setPendingIntentTemplate` for `ACTION_SKIP_TO_QUEUE_ITEM` |

Security rules:

- All PendingIntents use `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE` on API 23+.
- The only mutable intent is the adaptive ListView template (request 920). On API 31+ it uses
  `FLAG_UPDATE_CURRENT | FLAG_MUTABLE` because the platform must merge the row's fill-in extras
  (`EXTRA_QUEUE_INDEX`); without mutability the fill-in is ignored. On older APIs it uses
  `FLAG_UPDATE_CURRENT` only. This mutability is confined to a broadcast whose only extra is an
  integer, and the receiver is exported only to the system widget host.
- `getOutputSwitcherIntent` first tries
  `Intent(MainActivity.ACTION_MEDIA_OUTPUT)` with extra
  `MainActivity.EXTRA_MEDIA_OUTPUT_PACKAGE_NAME = packageName`; it uses that only when
  `PackageManager.resolveActivity(intent, 0) != null`. Any exception falls back to the in-app output
  switcher.
- Widget receivers are exported so launcher hosts can deliver `APPWIDGET_UPDATE` and bind buttons.
  They accept only the documented actions; unknown actions are ignored.

### 2.8 Adaptive queue

`AdaptiveQueueWidgetService : RemoteViewsService`, exported=false,
`permission="android.permission.BIND_REMOTEVIEWS"`. Companion:

```kotlin
@Volatile var queueData: List<WidgetQueueItem> = emptyList()
```

- The manager assigns `AdaptiveQueueWidgetService.queueData = queueItems` before building expanded
  views. This is process-global mutable state; the factory copies it in `onCreate` and
  `onDataSetChanged`.
- Expanded `RemoteViews` sets `setRemoteAdapter(R.id.widget_queue_list, intent)` where the intent
  targets the service, carries `EXTRA_APPWIDGET_ID`, and has a unique data URI
  `widget://adaptive/queue/<widgetId>` (distinct URIs are required for separate widget instances).
- `setEmptyView(R.id.widget_queue_list, R.id.widget_queue_empty)`.
- A `PendingIntentTemplate` on the ListView is the mutable broadcast described above. Each row
  supplies `setOnClickFillInIntent` on `queue_item_root`, `queue_item_title`, `queue_item_artist`
  with `EXTRA_QUEUE_INDEX = item.index`.
- Factory: `getCount` = item count; out-of-range `getViewAt` returns null; layout
  `widget_queue_item` with `queue_item_title`, `queue_item_artist`; `getLoadingView` null;
  view type count 1; `getItemId` = position; `hasStableIds` false.
- After `updateAppWidget` the manager calls
  `notifyAppWidgetViewDataChanged(widgetId, R.id.widget_queue_list)` so the adapter reloads.

### 2.9 Receiver action contract

`MusicWidgetReceiver` companion constants (exact values):

```kotlin
ACTION_PLAY_PAUSE          = "com.revenge.music.widget.PLAY_PAUSE"
ACTION_LIKE               = "com.revenge.music.widget.LIKE"
ACTION_NEXT               = "com.revenge.music.widget.NEXT"
ACTION_PREVIOUS           = "com.revenge.music.widget.PREVIOUS"
ACTION_SHUFFLE            = "com.revenge.music.widget.SHUFFLE"
ACTION_REPEAT             = "com.revenge.music.widget.REPEAT"
ACTION_MUTE               = "com.revenge.music.widget.MUTE"
ACTION_SKIP_TO_QUEUE_ITEM = "com.revenge.music.widget.SKIP_TO_QUEUE_ITEM"
ACTION_UPDATE_WIDGET      = "com.revenge.music.widget.UPDATE_WIDGET"
EXTRA_QUEUE_INDEX         = "extra_queue_index"
```

`TurntableWidgetReceiver` constants:

```kotlin
ACTION_TURNTABLE_PLAY_PAUSE = "com.revenge.music.widget.TURNTABLE_PLAY_PAUSE"
ACTION_TURNTABLE_NEXT       = "com.revenge.music.widget.TURNTABLE_NEXT"
ACTION_TURNTABLE_PREVIOUS   = "com.revenge.music.widget.TURNTABLE_PREVIOUS"
ACTION_UPDATE_TURNTABLE_WIDGET = "com.revenge.music.widget.UPDATE_TURNTABLE_WIDGET"
```

Behaviour:

- `onUpdate` / `onAppWidgetOptionsChanged`: do **not** start the service unless
  `MusicService.isRunning` is true. This avoids `BackgroundServiceStartNotAllowedException` on
  Android 14+. If running, start `MusicService` with `ACTION_UPDATE_WIDGET` (turntable:
  `ACTION_UPDATE_TURNTABLE_WIDGET`); swallow start failures. With the service stopped, the widget
  keeps its static `initialLayout` until playback starts.
- `onReceive` forwards user-button actions to `MusicService` by explicit `startService` with the same
  action string (turntable maps play/pause→`ACTION_PLAY_PAUSE`, next→`ACTION_NEXT`,
  previous→`ACTION_PREVIOUS`), copying all extras. Start failures are swallowed. Widget clicks are a
  user-visible interaction and are allowed to start the service.
- `MusicService` maps `ACTION_UPDATE_TURNTABLE_WIDGET` as an alias of `ACTION_UPDATE_WIDGET`
  (full refresh).
- All redesigned receivers are empty subclasses of `MusicWidgetReceiver`; they must remain open
  (non-final) so the subclasses inherit update handling.

### 2.10 Manifest entries for widgets

Every receiver below is `android:exported="true"` with an `APPWIDGET_UPDATE` intent filter and a
`android.appwidget.provider` meta-data pointing at its provider XML. Receiver class names and labels:

- `.widget.MusicWidgetReceiver` (`@string/widget_music_player`) — additional declared actions
  PLAY_PAUSE, LIKE, UPDATE_WIDGET.
- `.widget.TurntableWidgetReceiver` (`@string/widget_turntable`) — TURNTABLE_PLAY_PAUSE,
  TURNTABLE_LIKE (declared but not implemented; harmless), UPDATE_TURNTABLE_WIDGET.
- `.widget.PlaylistWidgetReceiver` (`@string/widget_playlists`) — APPWIDGET_UPDATE only.
- `.widget.RevengeModernWidgetReceiver` (`@string/widget_modern_name`), `RevengeSquareWidgetReceiver`
  (`widget_square_name`), `RevengeShowcaseWidgetReceiver` (`widget_showcase_name`),
  `RevengeSlimWidgetReceiver` (`widget_slim_name`), `RevengeVinylWidgetReceiver`
  (`widget_vinyl_name`), `RevengeVintageWidgetReceiver` (`widget_vintage_name`) —
  PLAY_PAUSE, LIKE, NEXT, PREVIOUS, UPDATE_WIDGET.
- `.widget.RevengeAdaptiveWidgetReceiver` (`widget_adaptive_name`) — PLAY_PAUSE, NEXT, PREVIOUS,
  SHUFFLE, REPEAT, SKIP_TO_QUEUE_ITEM (declared twice in the baseline; once is sufficient),
  UPDATE_WIDGET.
- `.widget.MusicRecognizerWidgetReceiver` (`widget_recognizer_name`) — recognizer actions in §4.6.

`<service android:name=".widget.AdaptiveQueueWidgetService"
android:permission="android.permission.BIND_REMOTEVIEWS" android:exported="false" />`.

Provider XMLs must keep the dimensions in §2.3 and be parseable by the launcher; `description`
strings exist for every widget.

### 2.11 App shortcuts (`res/xml/shortcuts.xml`)

Two static shortcuts, both targeting `com.revenge.music.MainActivity` in package
`com.revenge.music`:

| id | label | icon | action |
|---|---|---|---|
| `search` | `@string/search` | `@drawable/shortcut_search` | `com.revenge.music.action.SEARCH` |
| `library` | `@string/filter_library` | `@drawable/shortcut_library` | `com.revenge.music.action.LIBRARY` |

`MainActivity` declares `<meta-data android:name="android.app.shortcuts"
android:resource="@xml/shortcuts" />`. `MainActivity.ACTION_SEARCH =
"com.revenge.music.action.SEARCH"` and `ACTION_LIBRARY = "com.revenge.music.action.LIBRARY"` must
exist (they are public constants); the search shortcut opens search, the library shortcut opens the
library tab.
---

## 3. Playlist / quick-pick widget

### 3.1 `PlaylistWidgetReceiver` — `com.revenge.music.widget`

`@AndroidEntryPoint`, injects `PlaylistWidgetManager`. Constants (exact):

```kotlin
ACTION_PLAY_TARGET   = "com.revenge.music.widget.playlists.PLAY_TARGET"
ACTION_UPDATE_WIDGET = "com.revenge.music.widget.playlists.UPDATE_WIDGET"
EXTRA_TARGET_TYPE    = "playlist_widget_target_type"
EXTRA_TARGET_ID      = "playlist_widget_target_id"
EXTRA_TARGET_TITLE   = "playlist_widget_target_title"
TARGET_TYPE_LOCAL       = "local"
TARGET_TYPE_ONLINE      = "online"
TARGET_TYPE_LIKED       = "liked"
TARGET_TYPE_DOWNLOADED  = "downloaded"
TARGET_TYPE_TOP         = "top"
```

Behaviour:

- `onUpdate` asynchronously refreshes each bound widget id with its current options via
  `PlaylistWidgetManager.updateIdleWidget(id, options)`.
- `onAppWidgetOptionsChanged` refreshes the single widget id (used while the user resizes, so the
  grid capacity changes without waiting for playback).
- `onReceive ACTION_PLAY_TARGET`: start `MusicService` with the same action and copy the three
  target extras; on any start failure log and fall back to opening the target in the app: start
  `MainActivity` with action `com.revenge.music.action.OPEN_WIDGET_TARGET`, flags
  `NEW_TASK|SINGLE_TOP`, and extras `extra_widget_target_type` / `extra_widget_target_id`.
- `onReceive ACTION_UPDATE_WIDGET`: refresh all bound playlist widgets from the manager's cached
  last state.
- All async work uses `goAsync()` and is finished in `finally`.
- Widget card click opens the target in the app via a direct activity PendingIntent (not the
  receiver); the play button uses a broadcast PendingIntent to this receiver.

### 3.2 `PlaylistWidgetManager`

`@Singleton`, constructor `(@ApplicationContext Context, MusicDatabase, @ApplicationScope
CoroutineScope)`. Public API:

```kotlin
suspend fun updateWidgets(title: String, artist: String, artworkUri: String?, isPlaying: Boolean,
                          isLiked: Boolean, duration: Long = 0, currentPosition: Long = 0)
suspend fun updateIdleWidgets()
suspend fun updateIdleWidget(appWidgetId: Int, options: Bundle)
```

State model:

- `lastWidgetState` (in-memory `WidgetState`) defaults to
  `title = @string/not_playing`, `artist = @string/choose_something_below`,
  `artworkUri = null`, `isPlaying = false`, `isLiked = false`, `duration = 0`,
  `currentPosition = 0`.
- `updateWidgets` writes `lastWidgetState` **before** checking whether any widget is bound, then
  returns early if `getAppWidgetIds(PlaylistWidgetReceiver)` is empty.
- `updateIdleWidget(id, options)` renders the cached `lastWidgetState` using the supplied options
  (size bucket). It is used on add/resize and does not change the playback state.
- `updateIdleWidgets()` resets the cached state to the not-playing defaults and re-renders all bound
  widgets.
- On construction the manager starts observing quick-pick source data in the application scope:
  `combine(speedDialDao.getAll(), playlistsByCreateDateAsc(), likedSongsByCreateDateAsc(),
  downloadedSongsByCreateDateAsc(), mostPlayedSongs(0L, limit = 50))`,
  mapped to immutable snapshots, `distinctUntilChanged()`, `debounce(500 ms)`, then refresh all
  widgets from `lastWidgetState`. Collector failures must not crash the app scope.

### 3.3 Quick picks

Builder output is an ordered, de-duplicated list of private `QuickPick` values with fields
`key: String, targetType: String, targetId: String, title: String, thumbnailUrl: String?,
fallbackIconRes: Int`.

Order and de-duplication (`seen` set keyed by `QuickPick.key`):

1. Speed-dial items whose type is `"PLAYLIST"` or `"LOCAL_PLAYLIST"`:
   - `"LOCAL_PLAYLIST"` → key `local:<id>`, target `local`, id `item.id`, title `item.title`.
   - `"PLAYLIST"` → prefer the saved playlist whose `browseId == item.id || id == item.id` and emit
     its quick pick; if unsaved, key `online:<id>`, target `online`, id `item.id`.
2. Saved playlists with `browseId == null || isLocal` → local quick pick
   (key `local:<playlist.id>`, target `local`).
3. Saved playlists with `browseId != null && !isLocal` → online quick pick
   (key `online:<browseId>`, target `online`, target id `browseId`).
4. If liked songs exist: key `auto:liked`, target `liked`, target id `liked`, title
   `@string/liked_songs`, thumbnail = first liked song thumbnail, fallback icon
   `ic_widget_heart_nav`.
5. If downloaded songs exist: key `auto:downloaded`, target `downloaded`, target id
   `downloaded`, title `@string/downloaded_songs`, thumbnail = first downloaded thumbnail,
   fallback icon `cached`.
6. If top songs exist: key `auto:top50`, target `top`, target id `"50"`, title `@string/my_top`,
   thumbnail = first top-song thumbnail, fallback icon `trending_up`.
7. Saved playlists use `title = playlist.name`, `thumbnailUrl = thumbnails.firstOrNull()`,
   fallback icon `playlist_play`.

Each quick pick appears once even when it originates from both speed dial and saved playlists.

Capacity rules (`options` from `AppWidgetManager.getAppWidgetOptions`):

```text
maxItems = 0  if minWidth < 210
         = 4  if minHeight < 330
         = 8  if minWidth >= 360
         = 4  otherwise
displayCount = 8 if min(available, maxItems, 8) >= 8
             = 4 if min(available, maxItems, 8) >= 4
             = min(available, maxItems, 8) otherwise
```

- `displayCount > 0` shows `widget_playlist_quick_picks_section`; otherwise GONE.
- `displayCount > 4` shows `widget_playlist_row_2`; otherwise GONE.
- Slots 1..8 exist as `widget_playlist_card_N` with children `_art`, `_play_container`, `_play`,
  `_title` (N = 1..8). Slots `< displayCount` are VISIBLE, slots reserved to keep a partial first
  row balanced are INVISIBLE, remaining slots GONE:
  - displayCount 1 → slots 1..3 INVISIBLE; 2 → slots 2..3; 3 → slot 3; otherwise none.
- Visible cards display title, play icon `ic_widget_play_low`, artwork loaded at 180 px and rounded
  by 30 (fallback artwork when loading fails), card click → open target in app, play click → play
  target broadcast.

Fallback artwork: 300×300 rounded square tinted by target type — liked `#C62856`,
downloaded `#268076`, top `#7A56B2`, otherwise `#424856`; white fallback icon centred at 128 px.
Results are cached by `(targetType, fallbackIconRes, cornerRadius)`. Rounded launcher icon is cached
by corner radius.

### 3.4 Playlist widget rendering and intents

Layout `widget_playlist` ids: `widget_playlist_song_title`, `widget_playlist_artist_name`,
`widget_playlist_album_art`, `widget_playlist_play_pause`, `widget_playlist_like_button`,
`widget_playlist_progress_fill`, `widget_playlist_prev_container`,
`widget_playlist_play_pause_container`, `widget_playlist_next_container`, quick-pick ids above.
Progress level is `coerceIn(0, 10000)`; icons as in §2.6.

Album art is loaded at 240 px, centre-cropped and rounded by 36; a missing/failed image shows the
rounded launcher icon (36). Artwork cache is keyed by URI; the rounded result is cached against the
same source bitmap instance.

PendingIntents (all immutable, explicit):

| Request code | Type | Action |
|---|---|---|
| 500 | activity | `MainActivity` (album art) |
| 501 | broadcast | `MusicWidgetReceiver.ACTION_PREVIOUS` |
| 502 | broadcast | `MusicWidgetReceiver.ACTION_PLAY_PAUSE` |
| 503 | broadcast | `MusicWidgetReceiver.ACTION_NEXT` |
| 504 | broadcast | `MusicWidgetReceiver.ACTION_LIKE` |
| `item.key.hashCode()` | activity | `MainActivity` `OPEN_WIDGET_TARGET` + target extras |
| `item.key.hashCode() xor 0x3522` | broadcast | `PlaylistWidgetReceiver.ACTION_PLAY_TARGET` + target extras |

Hash collisions between two quick picks are tolerated (last wins with `FLAG_UPDATE_CURRENT`); ids
must remain deterministic for a given set of picks.

---

## 4. Music recognizer widget

### 4.1 `MusicRecognizerWidgetService` state contract

`Service`, exported=false, `foregroundServiceType="microphone"`, `START_NOT_STICKY`. Companion
constants (exact):

```kotlin
ACTION_START_RECOGNITION = "com.revenge.music.widget.recognizer.START"
ACTION_STOP_RECOGNITION  = "com.revenge.music.widget.recognizer.STOP"

PREFS_NAME        = "recognizer_widget_prefs"
PREF_STATE        = "state"
PREF_SONG_TITLE   = "song_title"
PREF_ARTIST_NAME  = "artist_name"
PREF_ERROR_MESSAGE= "error_message"
PREF_PULSE_FRAME  = "pulse_frame"
PREF_COVER_ART_PATH = "cover_art_path"

STATE_IDLE=0, STATE_LISTENING=1, STATE_PROCESSING=2, STATE_SUCCESS=3, STATE_NO_MATCH=4, STATE_ERROR=5
ALBUM_ART_CACHE_FILE = "recognizer_widget_art.png"
```

All widget state lives in these private SharedPreferences so it survives process death and widget
host rebinds. The service never holds the authoritative result in memory; it writes prefs and
broadcasts an update.

`onStartCommand`:
- `ACTION_START_RECOGNITION`: call `startForeground` first (notification below), then begin
  recognition.
- `ACTION_STOP_RECOGNITION`: cancel jobs, write state IDLE and pulse 0, update widgets, remove the
  foreground notification, `stopSelf()`.
- Any other/null action: return `START_NOT_STICKY` without starting foreground.

Recognition flow:
1. Persist `STATE_LISTENING`, broadcast update.
2. Launch a pulse animation job that every 600 ms writes `frame = (frame+1) % 4` and broadcasts an
   update. Four frames exist (`widget_mic_pulse_1..4`); idle uses `widget_mic_pulse_idle`.
3. Call `MusicRecognitionService.recognize(this)`.
4. On terminal result cancel the pulse job and persist:
   - `Success` → `STATE_SUCCESS`, title, artist, `cover_art_path` = cached art file path or `""`,
     pulse 0. If a cover URL (HQ preferred over normal) exists, download at 200×200, centre-crop,
     round by 24, compress PNG quality 90 to `cacheDir/recognizer_widget_art.png`; failures leave
     path empty. Then insert a `RecognitionHistory` row via a Hilt entry point
     (`RecognizerWidgetEntryPoint.databaseDao()`), with all result fields and
     `recognizedAt = LocalDateTime.now()`. DB failure is non-fatal and must not alter displayed
     state.
   - `NoMatch` → `STATE_NO_MATCH`, error message = result message, path `""`.
   - `Error` → `STATE_ERROR`, error message = result message, path `""`.
   - other → `STATE_IDLE`, path `""`.
5. Any exception → cancel pulse, `STATE_ERROR` with exception message (or generic error string),
   path `""`.
6. Finally: broadcast update, `stopForeground(STOP_FOREGROUND_REMOVE)`, `stopSelf()`.

Foreground notification:
- Channel id `music_recognizer_widget`, name `@string/widget_recognizer_channel_name`,
  description `@string/widget_recognizer_channel_desc`, `IMPORTANCE_LOW`, no badge.
- Notification id 9001, small icon `ic_widget_mic`, title `@string/widget_recognizer_listening`,
  text `@string/widget_recognizer_notification_text`, ongoing, `PRIORITY_LOW`, content intent opens
  `MainActivity` (immutable request 0), action "Cancel" (`android.R.string.cancel`) with a
  service PendingIntent carrying `ACTION_STOP_RECOGNITION` (immutable request 1).
- On API 34+ pass `FOREGROUND_SERVICE_TYPE_MICROPHONE`; older APIs use the two-argument
  `startForeground`.
- The service must be started with `startForegroundService` from the widget/UI; it calls
  `startForeground` before doing any work.

Widget refresh is a broadcast to `MusicRecognizerWidgetReceiver` with
`ACTION_UPDATE_WIDGET`; the receiver rebuilds all widgets from prefs.

### 4.2 `MusicRecognizerWidgetReceiver`

Constants:

```kotlin
ACTION_START_RECOGNITION = "com.revenge.music.widget.recognizer.TAP_MIC"
ACTION_UPDATE_WIDGET     = "com.revenge.music.widget.recognizer.UPDATE"
ACTION_RESET_STATE       = "com.revenge.music.widget.recognizer.RESET"
```

`onUpdate` / `onAppWidgetOptionsChanged`: rebuild all widgets from prefs.
`onReceive`:
- `TAP_MIC` → `handleStartRecognition`:
  - current state LISTENING or PROCESSING → start the widget service with
    `ACTION_STOP_RECOGNITION` (stop toggle).
  - current state SUCCESS/NO_MATCH/ERROR → write IDLE first (clear previous result).
  - missing `RECORD_AUDIO` → start `MainActivity` with `com.revenge.music.action.RECOGNITION`,
    flags `NEW_TASK|SINGLE_TOP`; do not start the service.
  - otherwise `startForegroundService(ACTION_START_RECOGNITION)`; swallow
    `ForegroundServiceStartNotAllowedException` on API 31+, rethrow other exceptions.
- `UPDATE` → rebuild all widgets.
- `RESET` → write IDLE, empty title/artist/error/path, pulse 0; delete
  `cacheDir/recognizer_widget_art.png`; rebuild widgets.

Rendering (all sizes read the same prefs):
- Tiny (`minWidth < 110`): layout `widget_recognizer_tiny`, ids `widget_recognizer_tiny_root`,
  `widget_recognizer_tiny_mic_container`, `widget_recognizer_tiny_pulse`. Whole root starts/stops
  recognition; no text or art.
- Compact (`110 <= minWidth < 230`): layout `widget_recognizer_compact`.
- Wide (`minWidth >= 230`): layout `widget_recognizer_wide`.
  Both have `widget_recognizer_album_art`, `widget_recognizer_text_area`,
  `widget_recognizer_song_title`, `widget_recognizer_artist_name`,
  `widget_recognizer_mic_container`, `widget_recognizer_pulse`.

State → observable content (compact/wide):

| State | Title text | Artist line | Album art |
|---|---|---|---|
| IDLE | `@string/widget_recognizer_tap_to_search` | GONE | GONE |
| LISTENING | `@string/widget_recognizer_listening` | GONE | GONE |
| PROCESSING | `@string/widget_recognizer_processing` | GONE | GONE |
| SUCCESS | song title, or `@string/widget_recognizer_unknown_song` when empty | artist, or `@string/widget_recognizer_unknown_artist` when empty; VISIBLE | shown only if decoded file exists |
| NO_MATCH | `@string/widget_recognizer_no_match` | GONE | GONE |
| ERROR | `@string/widget_recognizer_error` | error message, or `@string/widget_recognizer_error_generic`; VISIBLE | GONE |

Mic container background is `widget_mic_button_bg_active` when LISTENING/PROCESSING, else
`widget_mic_button_bg`. Pulse drawable is `widget_mic_pulse_(1..4)` indexed by `pulseFrame % 4` while
active, else `widget_mic_pulse_idle`. Album art is decoded synchronously from the cached path only in
SUCCESS.

Click behaviour:
- Mic container/root → broadcast to this receiver `TAP_MIC` (immutable request 20).
- Info area and album art → activity `MainActivity` action `ACTION_RECOGNITION`, flags
  `NEW_TASK|SINGLE_TOP` (immutable request 21). On SUCCESS the recognition screen reads
  `MusicRecognitionService.recognitionStatus` for the result.
- Manifest intent filter includes `APPWIDGET_UPDATE` and the three recognizer actions.

### 4.3 Quick Settings tile

`MusicRecognizerTileService : TileService`, package `com.revenge.music.quicksettings`.
- `onStartListening`: icon `R.drawable.mic`, state `Tile.STATE_INACTIVE`, `updateTile()`. The tile
  represents a one-shot action, not a toggle, so it never becomes ACTIVE.
- `onClick`: launch `RecognitionLaunchActivity` with `FLAG_ACTIVITY_NEW_TASK|FLAG_ACTIVITY_NO_ANIMATION`.
  On API 34+ wrap it in an immutable activity PendingIntent (request 0) and call
  `startActivityAndCollapse(PendingIntent)`; older APIs use the deprecated Intent overload.
- Manifest: exported=true, permission `android.permission.BIND_QUICK_SETTINGS_TILE`, label
  `@string/qs_tile_music_recognizer`, icon `@drawable/ic_qs_revenge_logo`, intent filter
  `android.service.quicksettings.action.QS_TILE`.

---

## 5. Recognition engine

### 5.1 `MusicRecognitionService` — package `com.revenge.music.recognition`

Plain Kotlin `object` (no Android service). Public surface:

```kotlin
val recognitionStatus: StateFlow<RecognitionStatus>
fun hasRecordPermission(context: Context): Boolean
suspend fun recognize(context: Context): RecognitionStatus
fun reset()
```

- Status starts at `RecognitionStatus.Ready`; `reset()` sets Ready. Only this object mutates the
  state; collectors see every transition.
- `hasRecordPermission` = `RECORD_AUDIO` granted.
- `recognize` runs on `Dispatchers.IO` and:
  1. If permission is missing, returns `Error("Microphone permission not granted")` **without**
     changing `recognitionStatus` (it stays at its previous value).
  2. Sets `Listening`.
  3. Records 10 seconds of mono 16-bit PCM at 44 100 Hz from the microphone, honouring coroutine
     cancellation; always stops/releases `AudioRecord`.
  4. Sets `Processing`.
  5. Builds `DecodedAudio(data, channelCount = 1, sampleRate = 44100, pcmEncoding =
     AudioFormat.ENCODING_PCM_16BIT)` and resamples to `VibraSignature.REQUIRED_SAMPLE_RATE`
     (16 000 Hz). Resample failure → `Error("Failed to resample audio: <message>")`.
  6. Validates the resampled stream: mono, 16 000 Hz, PCM 16-bit, little-endian host, non-empty,
     even byte length; violation → `Error` with the validation message.
  7. Fingerprints with `VibraSignature.fromI16`; failure → `Error("Failed to generate fingerprint:
     <message>")`.
  8. `sampleDurationMs = (bytes / 2) * 1000 / 16000`.
  9. Calls `Shazam.recognize(signature, sampleDurationMs)`. Success → `Success(result)`. Failure
     maps to `NoMatch("No matches found. Try again with clearer audio.")` when the message contains
     "No match" ignoring case, otherwise `Error(message)`.
  10. Unexpected exceptions → `Error(message ?: "Recognition failed")`.
- `recognitionStatus` is updated before `recognize` returns in every branch that reaches those
  stages, so screens observing the flow and callers using the return value agree.

`DecodedAudio` (`com.revenge.music.recognition`): `data class` with `data: ByteArray`,
`channelCount: Int`, `sampleRate: Int`, `pcmEncoding: Int`; equality/hashCode use
`ByteArray.contentEquals/contentHashCode` plus the scalar fields.

### 5.2 `AudioResampler`

```kotlin
object AudioResampler {
  suspend fun resample(decodedAudio: DecodedAudio, outputSampleRate: Int): Result<DecodedAudio>
}
```

- Runs on `Dispatchers.Default`.
- If the sample rate already equals `outputSampleRate`, returns `Result.success` with the **same
  instance**.
- Otherwise uses media3 `SonicAudioProcessor` configured for the output rate, feeds the input,
  queues end-of-stream, drains all output, and returns `DecodedAudio` with the processor's output
  channel count, sample rate and encoding. `Result.failure` on any error; coroutine cancellation is
  preserved.
- No mutation of the input object.

### 5.3 `VibraSignature` and `ShazamSignatureGenerator`

```kotlin
object VibraSignature {
  const val REQUIRED_SAMPLE_RATE = 16_000
  @JvmStatic fun fromI16(samples: ByteArray): String
}
```

`VibraSignature.fromI16` delegates to `internal object ShazamSignatureGenerator.fromI16`.
Input is little-endian signed 16-bit mono PCM. Output is a Shazam-compatible signature data URI:

```text
data:audio/vnd.shazam.sig;base64,<Base64 NO_WRAP of the binary signature>
```

Binary signature contract (must match the service format; the fingerprint is unusable otherwise):

- Requires a non-empty even-length byte array; otherwise throws `IllegalArgumentException`.
- Processes 128-sample hops until `12.0 s` of audio or at least `255` peaks have been accumulated.
- 2048-point real FFT per hop with a Hann window whose denominator is 2049.
- Peak spreading across time/frequency and peak picking over four frequency bands:
  250–520 Hz, 520–1450 Hz, 1450–3500 Hz, 3500–5500 Hz. Peaks outside 250–5500 Hz are dropped.
- Per band, peaks are delta-encoded: each peak stores a pass-number delta, `peakMagnitude`
  (16-bit LE) and corrected frequency bin (16-bit LE); a delta >= 255 is escaped with a `0xFF`
  marker followed by the absolute 32-bit LE pass number.
- Band blocks are emitted in band order as `magic 0x60030040 + bandId` (32-bit LE), a 32-bit LE
  payload size, the payload, and zero padding to a 4-byte boundary.
- 48-byte little-endian header beginning `0xcafe2580`, with zero CRC initially, a size field at
  offset 8 equal to `contents.size + 8`, `0x94119c00`, `3 shl 27`, `samplesAndOffset =
  (numSamples + 16000 * 0.24).toInt()`, and `(15 shl 19) + 0x40000`.
- A second block marker `0x40000000` + 32-bit LE size `contents.size + 8`, then contents.
- CRC32 over all bytes from offset 8 to the end; write the 32-bit little-endian CRC into bytes 4..7.
- Base64 `NO_WRAP`.
- Determinism: identical PCM input must produce byte-identical output.

### 5.4 `RecognitionForegroundService`

`Service`, exported=false, `foregroundServiceType="microphone"`, `START_NOT_STICKY`.
Constants:

```kotlin
EXTRA_RECOGNITION_TRACK_ID = "recognition_track_id"
EXTRA_RECOGNITION_TITLE    = "recognition_title"
EXTRA_RECOGNITION_ARTIST   = "recognition_artist"
```

Lifecycle:
1. `onCreate` creates channel `recognition_channel` (name
   `@string/recognition_notification_channel_name`, desc
   `@string/recognition_notification_channel_desc`, `IMPORTANCE_LOW`, no badge).
2. `onStartCommand`: start foreground immediately with notification id 9100, small icon
   `ic_widget_mic`, title `@string/recognize_music`, text
   `@string/recognition_notification_listening`. API 29+ passes
   `FOREGROUND_SERVICE_TYPE_MICROPHONE`. If `startForeground` throws `SecurityException` or
   `ForegroundServiceStartNotAllowedException` (API 31+, matched by class name), log, `stopSelf()`
   and return without starting recognition. Otherwise begin recognition once (re-entrant starts
   while a job is active are ignored).
3. On start: `MusicRecognitionService.reset()`, collect `recognitionStatus`, and call `recognize`;
   if `recognize` returns `Error` while the flow has not already reported Error, render that error.
4. Status rendering (only when a result is not already terminal-handled):
   - LISTENING → title `@string/recognize_music`, text
     `@string/recognition_notification_listening`, ongoing.
   - PROCESSING → text `@string/recognition_notification_processing`, ongoing.
   - SUCCESS → title result title, text result artist, non-ongoing/auto-cancel, content intent and
     action `@string/listen_on_revenge` both point to a result PendingIntent. Then, in a coroutine,
     load cover art (HQ URL preferred, 1.5 s timeout, direct `HttpURLConnection`, 1.2 s connect/read
     timeouts) and update the notification with the large icon when available. Finally detach.
   - NO_MATCH → text `@string/recognition_notification_no_match`, terminal, then detach.
   - ERROR → text `@string/recognition_notification_failed`, terminal, then detach.
   - READY → no render.
   The `terminalStateHandled` guard ensures exactly one terminal notification per run; duplicate
   emissions after a terminal state are ignored.
5. Terminal handling: `keepNotificationOnStop = true`, `stopForeground(STOP_FOREGROUND_DETACH)`,
   `stopSelf()`. The notification therefore remains after the service dies, with `setOngoing(false)`
   and `setAutoCancel(true)` for terminal states. `onDestroy` removes the foreground notification
   unless detached.
6. Result PendingIntent (immutable, request 9101): `MainActivity` action
   `MainActivity.ACTION_RECOGNITION`, extras track id/title/artist, flags
   `NEW_TASK|SINGLE_TOP`. `MainActivity` reads those extras to open/populate the recognition result.
7. Notification builder: small icon `ic_widget_mic`, `PRIORITY_LOW`, `setOnlyAlertOnce(true)`,
   `setOngoing(!isTerminal)`, `setAutoCancel(isTerminal)`, optional large icon and action.

### 5.5 `RecognitionLaunchActivity`

Transparent, no-history, single-task activity (`exported=false`,
`theme=Theme.Metrolist.Transparent`, `noHistory=true`, `excludeFromRecents=true`).
- `onCreate` and `onNewIntent` both run the same handler; it always finishes the activity.
- With `RECORD_AUDIO` granted: start `RecognitionForegroundService` with
  `startForegroundService` (API 26+) / `startService`, swallowing
  `ForegroundServiceStartNotAllowedException` on API 31+.
- Without permission: start `MainActivity` with action `MainActivity.ACTION_RECOGNITION`, extra
  `MainActivity.EXTRA_AUTO_START_RECOGNITION = true`, flags `NEW_TASK|SINGLE_TOP`, so the app can run
  its permission flow and continue.
- Manifest entries for both recognition services declare `foregroundServiceType="microphone"` and
  `exported="false"`.

### 5.6 Recognition permissions

Permissions required by this cluster: `RECORD_AUDIO`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`,
`FOREGROUND_SERVICE_DATA_SYNC` (update worker), `INTERNET`, `ACCESS_NETWORK_STATE`,
`REQUEST_INSTALL_PACKAGES`, `BLUETOOTH_CONNECT` (API 31+), legacy `BLUETOOTH`/`BLUETOOTH_ADMIN`
(maxSdk 30). Features `android.hardware.microphone` and
`android.hardware.location.gps` are optional (`required=false`).
---

## 6. Updater

All updater UI/behaviour is inert when `BuildConfig.UPDATE_REPO` is blank. `UPDATE_REPO` is read at
build time from local property `REVENGE_UPDATE_REPO` or environment variable of the same name,
defaulting to `""`; `BuildConfig.VERSION_NAME` comes from `revengeVersionName` (default `1.0.0`).
The string is an `"owner/name"` GitHub repository.

### 6.1 Version and preference helpers — `com.revenge.music.updates.updater`

Constants and functions (all public; consumed by `MainActivity`, `SettingsScreen`,
`UpdateSettings`):

```kotlin
const val PREFS_NAME = "settings"
const val KEY_AUTO_UPDATE_CHECK = "auto_update_check"
const val KEY_LAST_CHECKED_TIME = "last_checked_time"
const val KEY_BETA_UPDATES = "beta_updates"
const val KEY_UPDATE_AVAILABLE = "update_available"
const val KEY_UPDATE_NOTIFICATIONS = "update_notifications"
const val KEY_LAST_SEEN_CHANGELOG_VERSION = "last_seen_changelog_version"

fun getUpdateAvailableState(context: Context): Boolean
fun saveUpdateAvailableState(context: Context, available: Boolean)
fun getAutoUpdateCheckSetting(context: Context): Boolean      // default true
fun saveAutoUpdateCheckSetting(context: Context, enabled: Boolean)
fun getUpdateNotificationsSetting(context: Context): Boolean  // default true
fun saveUpdateNotificationsSetting(context: Context, enabled: Boolean)
fun getBetaUpdatesSetting(context: Context): Boolean          // default false
fun saveBetaUpdatesSetting(context: Context, enabled: Boolean)
fun saveLastCheckedTime(context: Context, timestamp: String)
fun getLastCheckedTime(context: Context): String              // default ""
fun getLastSeenChangelogVersion(context: Context): String     // default ""
fun saveLastSeenChangelogVersion(context: Context, version: String)
```

All preferences use the private `SharedPreferences` file `settings` and must round-trip exactly.
`getUpdateAvailableState` alone drives the "update" badge in Settings.

`isNewerVersion(latestVersion, currentVersion): Boolean`:

1. Strip a single leading `"b"` then a single leading `"v"` from each string (in that order).
2. Split on `.`; each part parses to `Int`, non-numeric parts become 0. Compare part by part over
   `max(size)` with missing parts = 0; the first difference decides.
3. If the cleaned strings are equal and the *current* is a beta (`startsWith("b")`) while the latest
   is not, return true.
4. Otherwise false (including equal versions).

`formatGitHubDate("yyyy-MM-dd'T'HH:mm:ss'Z'")` → `"d MMMM yyyy, h:mm a"`, falling back to the raw
string on parse failure (used for release dates).

`String.extractUrls(): List<Pair<IntRange, String>>` — finds `http(s)://`, `www.` and `pic.` URLs
preceded by start-of-string or whitespace; the returned range refers to the matched group in the
original string; URLs without an `http` scheme are normalised with `https://`.

### 6.2 Update check contract

```kotlin
data class ChangelogSection(val title: String, val items: List<String>)

suspend fun checkForUpdate(
  context: Context,
  onSuccess: (
    tag: String, isAvailable: Boolean, changelog: List<ChangelogSection>, size: String,
    date: String, description: String?, imageUrl: String?, apkUrl: String?
  ) -> Unit,
  onError: () -> Unit,
)
```

- Blank `UPDATE_REPO`: immediately calls `onSuccess(VERSION_NAME, false, emptyList(), "", "", null,
  null, null)`; never touches the network.
- Otherwise on `Dispatchers.IO`: GET
  `https://api.github.com/repos/<UPDATE_REPO>/releases/latest`.
- `tag_name` is compared with `VERSION_NAME` after stripping leading `b` then `v` and trimming. If
  equal → success with `isAvailable = false`, current version and empty payload.
- If different, the release is offered. Changelog retrieval:
  1. Try `https://github.com/<repo>/releases/download/<tag>/changelog.json`. On success read
     `description` (non-empty → value), `image` (non-empty → value), and `changelog` array of
     `{title, items[]}`.
  2. On any failure, parse the release `body`: extract the first Markdown image
     `![alt](url)`, set `imageUrl` to the URL and remove it from the body; split the body into a
     description plus sections via `parseMarkdownToSections` (a shared UI utility, contract:
     returns `(description: String?, sections: List<ChangelogSection>)`); if no sections were found,
     treat the body as the description.
- Asset scan (first match wins): asset name ends with `.apk` ignoring case and does not contain
  `debug` in lowercase. `size` = bytes / (1024*1024) formatted `"%.1f"`; `apkUrl = browser_download_url`.
- If no APK asset is found, report "no update" (`isAvailable=false`).
- All callbacks are invoked on `Dispatchers.Main`. Any network/JSON exception → `onError()`.
- `onSuccess` is the only place that saves `last_checked_time` and `update_available` — callers do
  that (see `UpdateScreen`).

### 6.3 What's-new lookup

```kotlin
data class WhatsNewInfo(val changelog: List<ChangelogSection>, val description: String?)

suspend fun fetchChangelogForVersion(currentVersion: String): WhatsNewInfo?
```

- Blank repo or any failure → null.
- GET `https://api.github.com/repos/<repo>/releases` (all releases, with 15 s connect/read
  timeouts) and find the release whose `tag_name` matches `currentVersion` after the same
  strip-`b`/`v`/trim normalisation. No match → null.
- Parse `changelog.json` for that exact tag as in §6.2; on failure fall back to the release body,
  stripping one Markdown image and using `parseMarkdownToSections`.
- Returns null when both the changelog list is empty and the description is blank.
- Used by `MainActivity` on launch to show `WhatsNewDialog` when
  `last_seen_changelog_version != currentVersion`.

### 6.4 `UpdateScreen(navController: NavHostController)`

Navigation route `"update"`; back navigation uses `navigateUp()`.

State machine (`RevengeUpdateStatus`):

```kotlin
sealed class RevengeUpdateStatus {
  object Idle
  object Checking
  data class Available(version, changelog: List<ChangelogSection>, size, releaseDate,
                       description: String?, imageUrl: String?, apkUrl: String?)
  data class NoUpdate(version: String)
  data class Error(message: String)
}
```

Initial state is `NoUpdate(BuildConfig.VERSION_NAME)`. On first composition, if
`getAutoUpdateCheckSetting(context)` (default true), trigger a check automatically. A check sets
`Checking`, waits 1000 ms, then calls `checkForUpdate`; on success saves the last-checked timestamp
(`"d MMMM yyyy, h:mm a"`) and `update_available`, then moves to `Available` or `NoUpdate`; on error
moves to `Error(@string/cant_check_updates)`. Manual "Check for update" uses the same path.

Download state:
- `isDownloading`, `downloadProgress: Float`, `isDownloadComplete`, `downloadedFile: File?`.
- Observes WorkManager unique work `"update_download"`:
  - RUNNING → downloading, progress from `progress.getFloat("progress", 0f)`.
  - SUCCEEDED → not downloading, complete, file from `outputData.getString("file_path")`.
  - FAILED → not downloading + error snackbar `@string/download_failed`.
  - CANCELLED → not downloading, progress 0.
- If the completed file no longer exists, reset to not-complete/0 progress.

Screen contract:
- Top bar: title `@string/new_update <version>` (version highlighted) when `Available`, else
  `@string/settings_check_updates_title`; back button content description `@string/cancel`.
  Large collapsing top app bar.
- Bottom bar:
  - Idle/Checking/NoUpdate/Error → one full-width "Check for update" button, disabled while
    checking or downloading.
  - Available → "Later" (outlined, navigates up) and a primary button with text
    `"<n>%"` while downloading, `@string/install` when complete, else `@string/update_available`.
    Primary is disabled while downloading unless the download is complete; "Later" is disabled
    while downloading.
  - Primary click when complete: verify the file exists (else reset); API 26+ require
    `PackageManager.canRequestPackageInstalls()`; when false launch
    `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` with `package:<packageName>` and stop. Otherwise
    open the APK with `FileProvider` authority `"<packageName>.FileProvider"`, MIME
    `application/vnd.android.package-archive`, flags `GRANT_READ_URI_PERMISSION|NEW_TASK`.
  - Primary click when not complete: URL is `status.apkUrl` or
    `https://github.com/<UPDATE_REPO>/releases/download/<version>/revenge.apk`; enqueue the worker
    (see §6.5) and set downloading.
- Body:
  - Checking → centred loading indicator.
  - NoUpdate → large icon, `@string/on_latest_version` title, `@string/current_version_v`
    (version).
  - Error → large error icon and the message in the error colour.
  - Available → release date `@string/release_date_v`, size `@string/update_size_v`; optional image
    (200 dp, rounded 24, crop); optional Markdown description rendered with clickable URL
    annotations that open via `safeOpenUri`; changelog sections (title + items using grouped
    leading/middle/end/detached item shapes); download progress linear indicator (indeterminate
    when progress is 0) while downloading.
- Container max width 700 dp; error snackbar host; all text is real text (screen-reader visible);
  decorative icons have null content descriptions.
- Auto-update state and notification state are read from §6.1 preferences; toggling them is the
  responsibility of Settings (`UpdateSettings`), which also uses `UpdateInfoDialog`.

### 6.5 `UpdateDownloadWorker`

`CoroutineWorker`; input Data keys: `"apk_url"` (required; missing → `Result.failure()`),
`"version"` (default `"unknown"`), `"file_size"` (default `""`). Output on success:
`workDataOf("file_path" to absolutePath)`. Progress: `workDataOf("progress" to fraction 0..1)`.

- Publishes a foreground `ForegroundInfo` using `DownloadNotificationManager.NOTIFICATION_ID` and
  the "starting" notification; API 29+ declares `FOREGROUND_SERVICE_TYPE_DATA_SYNC`. Failure to set
  foreground is swallowed.
- Downloads with `HttpURLConnection`, 15 s connect/read timeouts:
  - Non-200: show failure notification; HTTP >= 500 → `Result.retry()`, otherwise
    `Result.failure()`.
  - Destination `externalFilesDir(DIRECTORY_DOWNLOADS)/revenge_updates/revenge.apk`, or
    `revenge_temp.zip` when the URL contains `nightly.link` or ends with `.zip`.
  - Streams to disk; when stopped ends retry and deletes the partial file.
  - Progress notification at most once per second and only when the integer percentage increases;
    also publishes WorkManager progress.
- ZIP case: extract the first non-directory entry ending in `.apk` into `revenge.apk`; delete the
  temp zip in all paths; no APK in archive or extraction error → failure notification +
  `Result.failure()`.
- If `version` starts with `nightly-r`, parse the remainder as an Int and store it as
  `last_installed_nightly_run` in SharedPreferences file `update_settings`.
- On success: show completing notification (tap opens install) and return success.
- Errors: `IOException` → failure notification + retry; other exceptions → failure notification +
  failure.

### 6.6 Download notifications — `DownloadNotificationManager`

`object`; must be initialised with `initialize(context)` before use (the Update screen does this in
a `LaunchedEffect`); otherwise late-init fields throw. Public surface:

```kotlin
const val CHANNEL_ID = "download_progress_channel"
const val NOTIFICATION_ID = 5678
fun initialize(context: Context)
fun getDownloadStartingNotification(version: String, fileSize: String): Notification
fun getDownloadProgressNotification(progress: Int, version: String): Notification
fun showDownloadComplete(version: String, filePath: String)
fun showDownloadFailed(version: String, errorMessage: String)
fun cancelNotification()
```

- Channel: `download_progress_channel`, name `@string/download_progress_channel`,
  `IMPORTANCE_HIGH`, description, no badge, public lockscreen visibility, no vibration/lights.
- API 36 (`BAKLAVA`) uses `Notification.ProgressStyle` with four 25 % segments coloured alternating
  `#4285F4` and `#8E24AA`; starting progress 0, in-progress uses the percentage, complete uses 100.
  It sets short critical text ("Starting"/"<n>%"/"Done") and requests promoted-ongoing through
  reflection with an extras fallback (`android.requestPromotedOngoing`,
  `android.shortCriticalText`); reflection failures must not crash.
- Pre-36: `NotificationCompat` with `setProgress(100, progress, false)`, ongoing while < 100,
  high priority, public.
- Complete notification, both API paths: tap opens the downloaded APK through
  `FileProvider.getUriForFile(context, "<packageName>.FileProvider", File(filePath))`, MIME
  `application/vnd.android.package-archive`, flags `GRANT_READ_URI_PERMISSION|NEW_TASK`, immutable
  PendingIntent. Title `@string/update_ready`, text `@string/tap_to_install_version`.
- Failure notification: warning small icon, title `@string/update_failed`, text
  `@string/failed_to_download_version` with a big-text body containing the error, high priority,
  auto-cancel.
- All notifications use id 5678; starting/progress are ongoing and only alert once.

### 6.7 Legacy `CustomDownloadManager`

`class CustomDownloadManager` in `updater.downloadmanager` (public but unused by current screens;
retained for source compatibility):

```kotlin
fun downloadApk(context, apkUrl, onProgress: (Float) -> Unit,
                onDownloadComplete: (File) -> Unit, onError: (String) -> Unit)
fun pauseDownload()
fun cancelDownload()
```

Starts at most one job (a new download cancels the previous), writes
`externalFilesDir(DOWNLOADS)/revenge_updates/revenge.apk`, reports fractional progress on the main
dispatcher, reports `"Server returned HTTP <code>"` for non-200, and `onError(message)` on
exceptions. `pauseDownload` stops the stream and leaves the partial file; `cancelDownload` cancels
without callbacks.

### 6.8 Media session notification wrapper — `RevengeNotificationProvider`

`class RevengeNotificationProvider(context, notificationIdProvider, channelId,
channelNameResourceId) : MediaNotification.Provider`. It wraps a
`DefaultMediaNotificationProvider` and:
- `fun setSmallIcon(iconResId: Int): RevengeNotificationProvider` — forwards and returns `this`.
- `createNotification(...)` delegates; then toggles `Notification.FLAG_ONGOING_EVENT` on the built
  notification: set when `player.playWhenReady && playbackState != STATE_IDLE && != STATE_ENDED`,
  cleared otherwise. Returns `MediaNotification(notificationId, notification)`.
- `handleCustomCommand` delegates unchanged.

### 6.9 Update storage utilities

```kotlin
fun getDownloadedApksDir(context: Context): File
fun getDownloadedApkCount(context: Context): Int
fun clearDownloadedApks(context: Context): Boolean
fun autoClearOldApks(context: Context)
```

- Directory is `externalFilesDir(Environment.DIRECTORY_DOWNLOADS)/"revenge_updates"`.
- Count = files ending `.apk` (case-insensitive) directly in the directory; 0 if missing.
- Clear deletes only `.apk` files; returns true if the directory is missing or every delete
  succeeded, false if any delete failed.
- Auto-clear deletes `.apk` files with `lastModified` older than 24 hours; never deletes other files.
- Called by `UpdateSettings` on entry (auto-clear) and "clear downloaded updates".

### 6.10 Update dialogs and Compose screens

`UpdateAvailableDialog(version, changelog, description, onDismiss)`:
- Modal alert dialog: badge `@string/update_available_title`; headline
  `"Version <version> is available"`; scrollable changelog block shown when a changelog or
  description exists (description parsed to sections when the structured list is empty); buttons
  "Next time" (dismiss) and "Update" (dismiss, then open
  `https://github.com/<UPDATE_REPO>/releases/latest` via `safeOpenUri`; no-op when repo blank).
- Uses the shared smooth-corner shapes, `BasicAlertDialog`; decorative icons null; all actions are
  text buttons.

`UpdateInfoDialog(onDismiss)`:
- Explains automatic cleanup of downloaded updates; info badge `@string/update_info_badge`,
  title `@string/update_info_title`, body `@string/clear_downloaded_updates_auto_info`, a hint
  block (`@string/manual_delete_hint_title` / `_desc`), and an "OK" button with a forward icon.

`WhatsNewDialog(version, info: WhatsNewInfo, onDismiss)`:
- Title `@string/whats_new_title` with the version; scrolls up to 420 dp; shows description then
  changelog sections; when `info.changelog` is empty but the description exists, it is converted
  with `parseMarkdownToSections`; "OK" dismisses.

`ChangelogScreen(navController: NavController, scrollBehavior: TopAppBarScrollBehavior,
versionTag: String = "v${BuildConfig.VERSION_NAME}")` (route `"settings/changelog"`):
- Data classes (public in `com.revenge.music.updates.changelog`):
  `ChangelogSection(title, items)`, `ReleaseMetadata(tagName, name, date, imageUrl?)`,
  `CachedChangelogData(sections, image, description, warning)`.
- State: sections, image, description, warning, `isLoading`, `hasError`, `showingCached`,
  `currentVersionTag`, `availableReleases`, `isFetchingOldReleases`. Initial loading true; error
  when `UPDATE_REPO` is blank.
- `fetchChangelog(tag)`: first try `filesDir/changelog_cache_<tag>.json`; if present render it and
  set `showingCached = true`. Else GET the release asset `changelog.json` (User-Agent
  `revenge-Changelog-App`); HTTP 200 parses `description`, `image`, `warning`, and `changelog`
  entries that may be objects `{title, items}` or plain strings (plain strings are appended to an
  untitled first section). Persist cache; on non-200/exception set error.
- On `currentVersionTag` change: delete every other `filesDir/changelog_cache_*.json` then fetch.
  Old releases are fetched once per screen: GitHub releases endpoint, keep only tags starting `"v"`
  (case-insensitive) that have a `changelog.json` asset; prepend the current version; de-duplicate
  by tag. Each release is a `ToggleButton` chip (`role = RadioButton`) in a horizontally scrolling
  group; a 24 dp progress indicator shows while loading releases.
- UI: top bar `@string/changelog_title` with back arrow; pull-to-refresh re-fetches the selected
  tag; error state text `@string/error_loading_changelog`; content shows the tag, an optional
  "Cached" badge, optional image, description, sections (bullet + markdown with clickable links),
  and a warning card. No section renders nothing (empty state).
- All text is exposed to accessibility; links are clickable spans that call `safeOpenUri`.

`CommitScreen(navController: NavController, scrollBehavior: TopAppBarScrollBehavior)` (route
`"settings/commits"`):
- Data class `CommitData(sha, message, authorName, authorAvatarUrl?, authorLogin?, date, htmlUrl)`.
- Loads once on first composition: `https://api.github.com/repos/<repo>/commits?branch=main&per_page=50`.
  For each commit: first non-blank line of the message, author name (default "Unknown"), date
  parsed from RFC-3339 to `"MMM d, yyyy"` with the raw string as fallback, SHA, HTML URL, and
  author login/avatar when the API author object is non-null. Blank repo → error state.
- UI: top bar `@string/commits_title`; error text `@string/error_loading_commits`; otherwise a
  grouped settings-style list. Each row shows title (max 2 lines), author · date, a 7-character SHA
  chip, and either the circular avatar (content description = author name) or a circular initial.
  Tapping a row opens `htmlUrl` with `safeOpenUri`. Pull-to-refresh; empty list shows nothing.
- `CommitItem(commit: CommitData, onClick: () -> Unit)` is a public row composable with the same
  content contract, used or usable outside the group.

### 6.11 `UpdateNotificationHelper`

`object`, channel `"updates"`, notification id `1001`.
- `showUpdateNotification(context, versionName)` creates the channel (name
  `@string/app_updates_title`, `IMPORTANCE_DEFAULT`) on API 26+.
- If `BuildConfig.UPDATE_REPO` is blank, returns without posting (after channel creation).
- Content intent: `Intent.ACTION_VIEW` on
  `https://github.com/<UPDATE_REPO>/releases/latest`, immutable PendingIntent.
- Notification: small icon `@string/ic_revenge_notification` drawable, title
  `@string/update_available_title`, text = version, auto-cancel.
- Post only on API < 33 or when `POST_NOTIFICATIONS` is granted.

### 6.12 Update install/security rules

- APK installation always goes through the app's `FileProvider` authority
  `"${applicationId}.FileProvider"`, never a raw `file://` URI.
- `REQUEST_INSTALL_PACKAGES` is declared; the UI must still respect
  `canRequestPackageInstalls()` and route the user to the system grant screen.
- FileProvider paths (`res/xml/provider_paths.xml`) expose only
  `<external-path name="external_files" path="." />`, `<cache-path name="cache" path="." />`,
  `<external-cache-path name="external_cache" path="." />`; `exported=false` and
  `grantUriPermissions=true`.
- Download URLs are always HTTPS (network security config permits cleartext only for the Listen
  Together feature; the updater must not depend on cleartext).
---

## 7. Audio output UI and Bluetooth helpers (`com.revenge.music.updates`)

These two files live in the `updates` package but are consumed by the player UI and `MainActivity`.

### 7.1 `AudioDeviceBottomSheet`

Public models:

```kotlin
data class AudioDevice(
  val name: String,
  val type: AudioDeviceType,
  val isConnected: Boolean,
  val isActive: Boolean = false,
  val batteryLevel: Int? = null,
  val deviceId: Int? = null,
)

enum class AudioDeviceType {
  BLUETOOTH, WIRED_HEADPHONES, PHONE_SPEAKER, EXTERNAL_SPEAKER, USB_HEADSET, HDMI, CHROMECAST
}
```

Composables:

```kotlin
@Composable fun AudioDeviceBottomSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier)
@Composable fun VolumeControlRow(
  label: String, icon: ImageVector, volume: Float, maxVolume: Int,
  onVolumeChange: (Float) -> Unit, onDragStart: () -> Unit = {}, onDragEnd: () -> Unit = {},
  modifier: Modifier = Modifier,
)
@Composable fun DeviceSelector(devices: List<AudioDevice>,
  onDeviceSelect: (AudioDevice) -> Unit, modifier: Modifier = Modifier)
```

`AudioDeviceBottomSheet` behaviour:

- Modal bottom sheet, skip partially expanded; dismiss calls `onDismiss`.
- Data inputs: `LocalPlayerConnection` service (preferred device id, cast handler), extension
  addons via `ExtensionSettingsViewModel`, preference `PreferredAudioAddonIdKey` (default `""`).
- Loads local output devices and marks exactly one active:
  - Active selection precedence: the service's `preferredDeviceId` if it matches a device; else
    Bluetooth A2DP; else wired headset/headphones; else built-in speaker. On pre-API 23 the legacy
    path returns only the single active device.
  - Device mapping: Bluetooth A2DP → `BLUETOOTH`, name = product name or `"Bluetooth Device"`,
    battery level from `BluetoothDevice.getBatteryLevel()` when `BLUETOOTH_CONNECT` is granted and
    the value is 0..100; wired headset/headphones → `WIRED_HEADPHONES` with
    `@string/wired_headphones`; USB headset/device → `USB_HEADSET` with product name or
    `"USB Audio"`; HDMI → `HDMI` with `@string/hdmi`; built-in speaker → `PHONE_SPEAKER` with
    `@string/phone_speaker`; unknown types are ignored.
  - Sort order: PHONE_SPEAKER, WIRED_HEADPHONES, USB_HEADSET, BLUETOOTH, others; then by name.
    De-duplicate by name.
- States: loading (spinner), error (error icon, message, "Retry" button), content. Empty device
  list is not an error; the volume row is still shown.
- When casting: prepend a synthetic `CHROMECAST` device named the cast device name (fallback
  `@string/volume`), marked active, and mark all local devices inactive. Volume range becomes
  0..100; otherwise `AudioManager.getStreamMaxVolume(STREAM_MUSIC)`.
- Content:
  - Optional "Audio Source" selector when enabled addons exist: a "Automatic" chip plus one chip
    per enabled addon, `role = RadioButton`, persists the addon id (blank = automatic).
  - Active device row: device icon, name, "Connected"/"Available", volume percentage for active
    non-speaker devices; a scalloped highlight animates for the active device.
  - If any Bluetooth device is present, a chevron expands a device list. Each row is
    `role = RadioButton`, shows the type icon and name (phone speaker displayed as
    `@string/this_phone`); tapping a non-cast device calls
    `service.setPreferredAudioDevice(deviceId)` and refreshes. The list closes after selection.
  - Volume row: horizontal pill; tap and drag anywhere set the value proportionally. While
    casting, volume RPCs are throttled to one per 200 ms during a drag and the final value is sent
    on drag end; local volume is written with `AudioManager.setStreamVolume(STREAM_MUSIC, value, 0)`.
    External volume changes update the displayed value when the user is not dragging and not
    casting.
  - Battery ring (56 dp) with percentage when the active device is Bluetooth with a known level.
  - "Done" button dismisses.
- Lifecycle while composed: registers a volume-changed receiver, headset/HDMI/becoming-noisy
  receiver (API 23+), an `AudioDeviceCallback`, and Bluetooth ACL connect/disconnect receiver;
  refreshes immediately and 1 s / 2.5 s after ACL changes; polls battery every 30 s; unregisters
  everything on dispose. Receiver registration/unregistration failures are tolerated.
- Permission: on API 31+ without `BLUETOOTH_CONNECT`, requests it with
  `ActivityResultContracts.RequestPermission`; denial shows `@string/bluetooth_permission_required`.
- Accessibility: device and addon selection chips expose `Role.RadioButton` with checked state;
  interactive controls have text labels or content descriptions (icon-only controls must not be
  unlabeled in the final implementation).

`VolumeControlRow`: 72 dp pill, fill proportional to `volume/maxVolume` with a spring animation,
volume-up/mute icon by value, label, and a small indicator dot at the end. Tap/drag updates the
callback; drag start/end are surfaced; when `volume` changes externally and the user is not dragging
the displayed value updates. It must remain usable with TalkBack (expose the slider semantics with
value and range).

`DeviceSelector`: flow row of connected-device toggle chips, `role = RadioButton`, selecting a chip
calls `onDeviceSelect` only when it was not already selected; phone speaker label `@string/this_phone`.

### 7.2 `BluetoothUtils` (top-level functions)

```kotlin
fun isBluetoothHeadphoneConnected(context: Context): Boolean
fun getConnectedBluetoothDeviceName(context: Context): String?
fun isBuds(name: String?): Boolean
fun isSpeaker(name: String?): Boolean
```

- `isBluetoothHeadphoneConnected`: API 23+ true when any output device is `TYPE_BLUETOOTH_A2DP` or
  `TYPE_BLUETOOTH_SCO`; older APIs use `isBluetoothA2dpOn || isBluetoothScoOn`.
- `getConnectedBluetoothDeviceName`: returns null when Bluetooth A2DP/SCO is not active on pre-23
  or no A2DP/SCO device is found; otherwise the product name of the A2DP device, falling back to
  SCO. Consumed by the player screen's device-name state.
- `isBuds`: case-insensitive substring match on `buds`, `airpods`, `earpods`, `earphone`,
  `freebuds`, `pods`; null → false.
- `isSpeaker`: case-insensitive substring match on `speaker`, `soundbar`, `homepod`, `echo`,
  `boombox`, `audio system`, `sound`, `audio`, `stereo`, `music`, `box`, `party`, `waves`;
  null → false.

### 7.3 `IconUtils` — `com.revenge.music.utils`

```kotlin
enum class AppIconType(val value: Int) { DEFAULT(0), STATIC(2) }

object IconUtils {
  fun setIcon(context: Context, iconType: AppIconType)
}
```

Switches launcher aliases with `PackageManager.setComponentEnabledSetting` and `DONT_KILL_APP`:
- `DEFAULT` enables `com.revenge.music.MainActivityAlias`, disables
  `com.revenge.music.MainActivityStatic`.
- `STATIC` enables `MainActivityStatic`, disables `MainActivityAlias`.
Both aliases target `.MainActivity`; the manifest ships `MainActivityAlias` enabled with
`ic_launcher`/`ic_launcher_round` and `MainActivityStatic` disabled with
`ic_launcher_static`/`ic_launcher_static_round`. Every call must leave exactly one launcher alias
enabled. Consumed by `AppearanceSettings` and `AppIconSettingsScreen`.

---

## 8. Cross-module contracts required to compile

### 8.1 `MusicService` (`com.revenge.music.playback`)

- Public companion `@Volatile var isRunning: Boolean` (private setter) — widget receivers read it
  before attempting to start the service.
- Injects `RevengeWidgetManager` as `widgetManager`.
- `onStartCommand` handles the widget actions with these effects:
  - `ACTION_PLAY_PAUSE` → toggle play/pause.
  - `ACTION_LIKE` → `toggleLike()`.
  - `ACTION_NEXT` / `ACTION_PREVIOUS` → seek next/previous.
  - `ACTION_SHUFFLE` → toggle `shuffleModeEnabled`.
  - `ACTION_REPEAT` → OFF → ALL → ONE → OFF.
  - `ACTION_MUTE` → `toggleMute()`.
  - `ACTION_SKIP_TO_QUEUE_ITEM` → read `EXTRA_QUEUE_INDEX`; when `0 <= index < mediaItemCount`,
    seek to default position and play if paused.
  - `ACTION_UPDATE_WIDGET` (and turntable alias) → immediate widget refresh.
  - Every handled action refreshes widgets except the pure refresh path which also refreshes.
- `updateWidgetUI(isPlaying)` collects the current song state and calls `updateWidgets` with:
  `title` (or empty), `artist` (or empty), `artworkUri = song?.thumbnailUrl`, `isPlaying`,
  `isLiked`, `duration = player.duration` unless `C.TIME_UNSET` then 0, `currentPosition`,
  `isShuffle = player.shuffleModeEnabled` (guarded until player initialised),
  `repeatMode = player.repeatMode`, `isMuted`, and `queueItems`.
- Queue building (contract for the adaptive widget):
  - Start at `currentIndex + 1`, follow `timeline.getNextWindowIndex(..., repeatForQueue,
    shuffleModeEnabled)` while the index differs, is unvisited, and fewer than 50 items exist;
    each `WidgetQueueItem(index, title, artist, isCurrent = false)` uses media metadata with
    fallbacks `"Track <n>"` and `""`.
  - If that produces nothing and more than one item exists, wrap forward from
    `currentIndex + 1` for `min(50, count - 1)` items.
  - Exceptions are swallowed; widgets must never break playback.
- Periodic refresh: while playing, `startWidgetUpdates` refreshes once per second; it also refreshes
  on lifecycle/state changes. `stopWidgetUpdates` cancels the job when playback stops.
- `MusicDatabase`, `Player` (media3), and `C.TIME_UNSET` are the only cross-module data sources.

### 8.2 `MainActivity` actions/extras

Public constants consumed by this cluster:

```kotlin
ACTION_SEARCH  = "com.revenge.music.action.SEARCH"
ACTION_LIBRARY = "com.revenge.music.action.LIBRARY"
ACTION_RECOGNITION = "com.revenge.music.action.RECOGNITION"
ACTION_NOW_PLAYING = "com.revenge.music.action.NOW_PLAYING"
ACTION_QUEUE = "com.revenge.music.action.QUEUE"
ACTION_OUTPUT_SWITCHER = "com.revenge.music.action.OUTPUT_SWITCHER"
ACTION_SONG_OPTIONS = "com.revenge.music.action.SONG_OPTIONS"
ACTION_LYRICS = "com.revenge.music.action.LYRICS"
ACTION_MEDIA_OUTPUT = "com.android.settings.panel.action.MEDIA_OUTPUT"
EXTRA_MEDIA_OUTPUT_PACKAGE_NAME = "com.android.settings.panel.extra.PACKAGE_NAME"
EXTRA_AUTO_START_RECOGNITION = "auto_start_recognition"
```

Additional string extras consumed by the host: `OPEN_WIDGET_TARGET` with
`extra_widget_target_type` / `extra_widget_target_id`; recognition result extras
`recognition_track_id`, `recognition_title`, `recognition_artist`.
Handling expectations: NOW_PLAYING opens the player; QUEUE opens the queue; SONG_OPTIONS opens the
current song menu; OUTPUT_SWITCHER opens output selection (or the system media output panel);
LYRICS opens lyrics; LIBRARY opens the library; SEARCH opens search; OPEN_WIDGET_TARGET resolves the
target type (`local`/`online`/`liked`/`downloaded`/`top`) and opens it; RECOGNITION opens the
recognition screen and, when `EXTRA_AUTO_START_RECOGNITION` is true, starts recognition after the
permission flow.

### 8.3 Database (`com.revenge.music.db`, `:core`)

Used by this cluster, all on the injected `MusicDatabase` facade:

```kotlin
val speedDialDao: SpeedDialDao
fun speedDialDao.getAll(): Flow<List<SpeedDialItem>>       // fields type,id,title,thumbnailUrl
fun playlistsByCreateDateAsc(): Flow<List<Playlist>>
fun likedSongsByCreateDateAsc(): Flow<List<Song>>
fun downloadedSongsByCreateDateAsc(): Flow<List<Song>>
fun mostPlayedSongs(fromTimeStamp: Long, limit: Int): Flow<List<Song>>
fun insert(recognitionHistory: RecognitionHistory): Long
```

`Playlist` exposes `playlist` (`PlaylistEntity`: id, browseId?, name, isLocal) and `thumbnails:
List<String>`; `Song` exposes id, thumbnailUrl.
`RecognitionHistory` constructor fields used: `trackId, title, artist, album, coverArtUrl,
coverArtHqUrl, genre, releaseDate, label, shazamUrl, appleMusicUrl, spotifyUrl, isrc,
youtubeVideoId, recognizedAt` (defaults exist for nullables and `recognizedAt`).

### 8.4 `:shazamkit`

```kotlin
object Shazam { suspend fun recognize(signature: String, sampleDurationMs: Long): Result<RecognitionResult> }
sealed class RecognitionStatus {
  data object Ready; data object Listening; data object Processing
  data class Success(val result: RecognitionResult)
  data class NoMatch(val message: String = "No matches found")
  data class Error(val message: String)
}
data class RecognitionResult(
  trackId, title, artist, album, coverArtUrl, coverArtHqUrl, genre, releaseDate, label,
  lyrics, shazamUrl, appleMusicUrl, spotifyUrl, isrc, youtubeVideoId = null,
)
```
Nullable fields are `String?` except `trackId/title/artist` which are non-null `String`.
Recognition screens outside this cluster observe `MusicRecognitionService.recognitionStatus`, so
the state object and its emission order are a hard contract.

### 8.5 Build/config

- `BuildConfig.UPDATE_REPO: String` (blank disables all update features).
- `BuildConfig.VERSION_NAME` (used in update comparisons, screen defaults, changelog default tag).
- Manifest application id/namespace `com.revenge.music`; `FileProvider` authority
  `${applicationId}.FileProvider`.
- Navigation routes `"update"`, `"settings/changelog"`, `"settings/commits"` are registered in
  `NavigationBuilder`.

### 8.6 Manifest ownership summary

This cluster owns these manifest entries: all widget receivers in §2.10, the adaptive queue
service, the recognizer widget service, both recognition services, `RecognitionLaunchActivity`,
`MusicRecognizerTileService`, the app-shortcuts meta-data on `MainActivity`, the FileProvider
declaration (authority + `provider_paths` meta-data), and the permissions in §5.6 / §6.12.
`MainActivity` must remain `launchMode="singleTask"` so widget/notification intents reuse the task.

### 8.7 `res/xml` contracts

- Widget provider XMLs: §2.3/§2.10.
- `shortcuts.xml`: §2.11.
- `provider_paths.xml`: §6.12.
- `network_security_config.xml`: base config `cleartextTrafficPermitted="true"` with system trust
  anchors (required by Listen Together; the updater still uses HTTPS).
- `backup_rules.xml`, `data_extraction_rules.xml`, `automotive_app_desc.xml` remain referenced by
  the application tag and must exist.

---

## 9. Target files the implementer must write

Source (paths are relative to the repository root; all under `app/src/main/kotlin/`):

Widgets:
- `com/revenge/music/widget/RevengeWidgetManager.kt`
- `com/revenge/music/widget/MusicWidgetReceiver.kt`
- `com/revenge/music/widget/TurntableWidgetReceiver.kt`
- `com/revenge/music/widget/RevengeModernWidgetReceiver.kt`
- `com/revenge/music/widget/RevengeSquareWidgetReceiver.kt`
- `com/revenge/music/widget/RevengeShowcaseWidgetReceiver.kt`
- `com/revenge/music/widget/RevengeSlimWidgetReceiver.kt`
- `com/revenge/music/widget/RevengeVinylWidgetReceiver.kt`
- `com/revenge/music/widget/RevengeVintageWidgetReceiver.kt`
- `com/revenge/music/widget/RevengeAdaptiveWidgetReceiver.kt`
- `com/revenge/music/widget/AdaptiveQueueWidgetService.kt`
- `com/revenge/music/widget/PlaylistWidgetManager.kt`
- `com/revenge/music/widget/PlaylistWidgetReceiver.kt`
- `com/revenge/music/widget/MusicRecognizerWidgetReceiver.kt`
- `com/revenge/music/widget/MusicRecognizerWidgetService.kt`

Recognition and tile:
- `com/revenge/music/quicksettings/MusicRecognizerTileService.kt`
- `com/revenge/music/recognition/MusicRecognitionService.kt`
- `com/revenge/music/recognition/RecognitionForegroundService.kt`
- `com/revenge/music/recognition/RecognitionLaunchActivity.kt`
- `com/revenge/music/recognition/AudioResampler.kt`
- `com/revenge/music/recognition/VibraSignature.kt`
- `com/revenge/music/recognition/ShazamSignatureGenerator.kt`

Updater:
- `com/revenge/music/updates/UpdateNotificationHelper.kt`
- `com/revenge/music/updates/BluetoothUtils.kt`
- `com/revenge/music/updates/AudioDeviceBottomSheet.kt`
- `com/revenge/music/updates/updater/updaterscreen.kt`
- `com/revenge/music/updates/updater/UpdateStorageUtils.kt`
- `com/revenge/music/updates/updater/downloadmanager/DownloadManagerUtils.kt`
- `com/revenge/music/updates/updater/downloadmanager/downloadnotificationmanager.kt`
- `com/revenge/music/updates/updater/downloadmanager/RevengeNotificationProvider.kt`
- `com/revenge/music/updates/updater/downloadmanager/UpdateDownloadWorker.kt`
- `com/revenge/music/updates/changelog/changelogscreen.kt`
- `com/revenge/music/updates/commitscreen/commitscreen.kt`
- `com/revenge/music/updates/commitscreen/CommitItem.kt`
- `com/revenge/music/updates/component/UpdateAvailableDialog.kt`
- `com/revenge/music/updates/component/UpdateInfoDialog.kt`
- `com/revenge/music/updates/shapes/RoundedStarShape.kt`
- `com/revenge/music/utils/IconUtils.kt`

Resources:
- `app/src/main/res/xml/music_widget_info.xml`, `turntable_widget_info.xml`,
  `playlist_widget_info.xml`, `revenge_modern_widget_info.xml`, `revenge_square_widget_info.xml`,
  `revenge_showcase_widget_info.xml`, `revenge_slim_widget_info.xml`,
  `revenge_vinyl_widget_info.xml`, `revenge_vintage_widget_info.xml`,
  `revenge_adaptive_widget_info.xml`, `recognizer_widget_info.xml`, `shortcuts.xml`,
  `provider_paths.xml`, `network_security_config.xml`, `backup_rules.xml`,
  `data_extraction_rules.xml`, `automotive_app_desc.xml`.
- `app/src/main/res/layout/`: `widget_music_player.xml`, `widget_compact_square.xml`,
  `widget_compact_wide.xml`, `widget_turntable.xml`, `widget_modern_flow.xml`,
  `widget_compact_glow.xml`, `widget_showcase_card.xml`, `widget_slim_strip.xml`,
  `widget_vinyl_square.xml`, `widget_vintage_turntable.xml`, `widget_adaptive_compact.xml`,
  `widget_adaptive_expanded.xml`, `widget_queue_item.xml`, `widget_playlist.xml`,
  `widget_playlist_preview.xml`, `widget_recognizer_tiny.xml`, `widget_recognizer_compact.xml`,
  `widget_recognizer_wide.xml`.
- Drawables referenced by this cluster (at minimum): all `ic_widget_*` listed in §2.6 plus
  `widget_turntable_default_art`, `widget_mic_button_bg`, `widget_mic_button_bg_active`,
  `widget_mic_pulse_1..4`, `widget_mic_pulse_idle`, `mic`, `ic_qs_revenge_logo`,
  `ic_revenge_notification`, `updated`, `ic_launcher_foreground`, `shortcut_search`,
  `shortcut_library`, `arrow_back`, `arrow_forward`, `error`, `info`, `update`, `commit`,
  `deployed_app_update`, `manage_search`, `playlist_play`, `cached`, `trending_up`, `icon`.
  Background/scrim drawables used by the widget layouts (for example
  `widget_adaptive_card_bg`, `widget_adaptive_queue_bg`, `widget_artwork_scrim`,
  `widget_card_gradient_bg`, `widget_circle_play_accent_bg`, `widget_device_badge_bg`,
  `widget_like_button_bg`) must exist to match the layouts.
- Strings: all `widget_*`, `widget_recognizer_*`, `qs_tile_music_recognizer`, `recognize_music`,
  `recognition_notification_*`, `listen_on_revenge`, `update_*`, `downloading_update`,
  `update_ready`, `tap_to_install_version`, `download_progress_*`, `version_file_size`,
  `version_progress`, `failed_to_download*`, `server_error`, `no_changelog_available`,
  `changelog_title`, `commits_title`, `error_loading_changelog`, `error_loading_commits`,
  `whats_new_title`, `cached`, `current`, `this_phone`, `phone_speaker`, `wired_headphones`,
  `hdmi`, `audio_devices`, `volume`, `done`, `retry`, `bluetooth_permission_required`,
  `not_playing`, `choose_something_below`, `liked_songs`, `downloaded_songs`, `my_top`, `new_update`,
  `check_for_update`, `later`, `install`, `on_latest_version`, `current_version_v`,
  `release_date_v`, `update_size_v`, `cant_check_updates`, `download_failed`,
  `clear_downloaded_updates_auto_info`, `manual_delete_hint_*`, `update_info_*`,
  `update_available_title`, `app_updates_title`, `settings_check_updates_title`.
- Manifest: the entries in §2.10, §4.3, §5.5, §5.6, §6.12, §8.6.
- `app/build.gradle.kts`: `BuildConfig.UPDATE_REPO` and `versionName` wiring from §6.

Tests the implementer must write (deterministic, JVM unit tests unless noted):

- `app/src/test/kotlin/com/revenge/music/widget/WidgetStateTest.kt` — sizing, progress, duration,
  vinyl angle, queue-cap rules (pure helpers extracted for testability).
- `app/src/test/kotlin/com/revenge/music/widget/PlaylistQuickPickTest.kt` — ordering, de-dup,
  capacity/balanced slot rules, target type/id mapping.
- `app/src/test/kotlin/com/revenge/music/widget/RecognizerWidgetStateTest.kt` — state table, pulse
  mapping, toggle transition table.
- `app/src/test/kotlin/com/revenge/music/recognition/RecognitionSignatureTest.kt` — signature
  determinism, header/CRC, invalid input, resampler identity/failure.
- `app/src/test/kotlin/com/revenge/music/recognition/RecognitionStatusTest.kt` — status transition
  and NoMatch mapping with a fake Shazam result.
- `app/src/test/kotlin/com/revenge/music/updates/UpdateVersionTest.kt` — `isNewerVersion`,
  `formatGitHubDate`, `extractUrls`, preference round-trips, storage utils with a temp dir.
- `app/src/test/kotlin/com/revenge/music/updates/DownloadWorkerTest.kt` — HTTP status / zip
  extraction / retry classification with a local test server or injected URL opener.
- `app/src/androidTest/kotlin/com/revenge/music/widget/WidgetRenderingTest.kt` — RemoteViews
  inflation for every widget layout and id presence, plus `AppWidgetManager` size selection.
- `app/src/androidTest/kotlin/com/revenge/music/recognition/RecognitionServiceTest.kt` — foreground
  start/stop, notification channel/id, process-recreation state from prefs.
- `app/src/androidTest/kotlin/com/revenge/music/updates/UpdaterUiTest.kt` — screen state machine,
  install/handoff intents, dialog semantics.
- Screenshot/preview targets: one preview per widget layout (all sizes), one for
  `UpdateScreen` in Checking/NoUpdate/Error/Available, `UpdateAvailableDialog`,
  `UpdateInfoDialog`, `WhatsNewDialog`, `ChangelogScreen` (loading/error/content/cached),
  `CommitScreen` (loading/error/list), and `AudioDeviceBottomSheet`
  (loading/error/content/casting plus device popup expanded).

---

## 10. Acceptance tests

All tests below are deterministic; no test may depend on wall-clock time or a live GitHub/microphone.
Use fake clocks, fake `Player` state, prefs, temporary directories and local HTTP servers.

### 10.1 Widget rendering and state

W1. With options `minWidth=100, minHeight=100`, legacy music widget renders
`widget_compact_square`; `minWidth=200, minHeight=80` renders `widget_compact_wide`;
`minWidth=200, minHeight=120` renders `widget_music_player`.
W2. Progress level: duration 0 → 0; position 50 000/duration 100 000 → 5000; position > duration
clamps to 10000 on redesigned widgets; legacy widget yields >10000 (no clamp).
W3. `formatDuration`: 0→`0:00`, 59 999→`0:59`, 60 000→`1:00`, 3 600 000→`60:00`, negative→`0:00`.
W4. Album art cache: two consecutive updates with the same URI perform one decode; a changed URI
reloads; a null URI clears; decode failure leaves null art and the fallback icon is used.
W5. Vinyl angle: playing with duration 100 and position 900 → 180°; paused → 0°; position 1800 →
0° (wrap); `setRotation` is only invoked on API 31+.
W6. Adaptive sizing: API 31+ RemoteViews is constructed with both `SizeF` keys (200×80 compact,
200×150 expanded); API < 31 uses `minHeight >= 130` to pick expanded; both call
`notifyAppWidgetViewDataChanged` for `widget_queue_list`.
W7. No bound widgets: `updateWidgets` completes without touching `AppWidgetManager.updateAppWidget`
and still calls `PlaylistWidgetManager.updateWidgets`.
W8. Each redesigned receiver inherits `MusicWidgetReceiver` and is non-final; the size dispatch
never renders a layout belonging to another widget kind.

### 10.2 Widget intents and security

I1. Every generated PendingIntent is explicit (component set) and immutable except request 920.
I2. Request 920 (adaptive list template) is MUTABLE on API 31+ and non-mutable on lower APIs; the
row fill-in extras carry the integer queue index and the template's action is
`ACTION_SKIP_TO_QUEUE_ITEM`.
I3. Request codes map exactly as in §2.7: play/pause 1, like 2, turntable 3/4/5, previous 10,
next 11, library 12, output 13, now-playing 14, queue 15, options 16, lyrics 17, shuffle 901,
repeat 905, mute 907, queue item 910+index, playlist 500–504, recognizer 20/21, download/result
1001/9100/9101/5678.
I4. Output-switcher intent: when `resolveActivity` returns null it falls back to the in-app
`ACTION_OUTPUT_SWITCHER`; when resolvable it uses `ACTION_MEDIA_OUTPUT` with the package extra.
I5. Music widget receiver with `MusicService.isRunning == false` performs no `startService` on
update/options-change; with `true` it starts the service with `ACTION_UPDATE_WIDGET`.
I6. Button receive forwards the identical action/extras to `MusicService`; turntable maps
play/pause, next and previous to the music-widget action strings.

### 10.3 Playlist quick picks

P1. Source order and de-duplication: speed-dial local + saved same playlist + saved others + liked
+ downloaded + top yields each playlist once, speed-dial first, auto entries last.
P2. Capacity: width 180 → 0; width 250/height 200 → 4; width 360+/height 400 → 8.
P3. Balanced count: available 5 with capacity 8 → display 4; available 8 → 8; available 2 → 2.
P4. Reserved slots: display 1 → slots 2–4 INVISIBLE; display 2 → slots 3–4; display 3 → slot 4;
all others GONE.
P5. Resize to two rows toggles `widget_playlist_row_2` only when display > 4.
P6. `updateIdleWidgets` renders not-playing/choose-something defaults; `updateWidgets` updates the
cached state even with zero widgets; later add renders the cached song.
P7. Quick-pick source flows emit, then a 500 ms debounce refresh happens once per settled change
(distinct values); a duplicate emission does not rebuild twice.
P8. Play-target start failure falls back to `MainActivity` `OPEN_WIDGET_TARGET` with the same
type/id extras.

### 10.4 Recognizer widget and process recreation

R1. State/mic transition table:
IDLE+mic→start; LISTENING+mic→stop; PROCESSING+mic→stop; SUCCESS+mic→write IDLE then start;
NO_MATCH+mic→write IDLE then start; ERROR+mic→write IDLE then start.
R2. Missing `RECORD_AUDIO`: mic starts `MainActivity` with `ACTION_RECOGNITION` and never starts a
service.
R3. Size: width 100→tiny, 150→compact, 300→wide; text/art visibility per §4.2 table for all six
states.
R4. Pulse: frame values 0,1,2,3 map to `widget_mic_pulse_1..4`; any other frame uses frame % 4;
inactive states use `widget_mic_pulse_idle`; active background uses the active drawable.
R5. Process recreation: after writing prefs and destroying/recreating the receiver, all widgets
render from prefs (success title/artist/art path, no in-memory object needed).
R6. RESET clears all prefs keys, deletes the cached art file, and renders IDLE.
R7. Service success writes prefs, attempts history insertion exactly once, and removes the
foreground notification; DB failure leaves the success state intact.
R8. Terminal no-match/error writes the corresponding state and removes foreground.
R9. Service declares `START_NOT_STICKY`; a null intent starts no foreground notification.

### 10.5 Recognition engine

C1. `AudioResampler`: same-rate input returns the same instance and success; 44100→16000 returns
mono, 16000, PCM16 with non-empty output and does not mutate the input; invalid input yields
failure without throwing.
C2. `ShazamSignatureGenerator`: identical PCM → identical data URI on repeated calls; output starts
`data:audio/vnd.shazam.sig;base64,`; decoded bytes begin `0xcafe2580`; bytes 4..7 equal the CRC32
of bytes 8..end; size fields match the payload.
C3. Odd-length or empty input throws `IllegalArgumentException`.
C4. `recognize` without permission returns `Error("Microphone permission not granted")` and leaves
the previously observed status unchanged.
C5. Status order with a fake recorder and fake Shazam: Ready→Listening→Processing→Success; on a
"No match" failure → NoMatch with the clearer-audio message; other failures → Error.
C6. Sample duration is `(bytes/2)*1000/16000` for a known byte count.

### 10.6 Recognition services and tile

F1. `RecognitionForegroundService` calls `startForeground` before recognition; SecurityException /
start-not-allowed stops the service without crash and without recognition.
F2. Notification texts/ongoing/auto-cancel match §5.4 for Listening, Processing, Success, NoMatch,
Error; only one terminal notification is emitted per run.
F3. Success content/action PendingIntent carries track/title/artist and targets
`MainActivity.ACTION_RECOGNITION` with `NEW_TASK|SINGLE_TOP`, immutable.
F4. `RecognitionLaunchActivity` finishes immediately; with permission starts the service; without
permission starts `MainActivity` with `EXTRA_AUTO_START_RECOGNITION = true`.
F5. Tile: `onStartListening` sets `STATE_INACTIVE` and `mic`; `onClick` targets
`RecognitionLaunchActivity`; API 34+ uses the PendingIntent overload.

### 10.7 Updater

U1. `isNewerVersion` table: `1.2.3 > 1.2.2`; `1.10 > 1.9`; `v1.2 == 1.2` false; `b1.2.3` vs
`1.2.3` with current beta → true; current stable vs latest beta same numbers → false; missing
parts behave as 0.
U2. Blank `UPDATE_REPO`: `checkForUpdate` immediately reports no update with the current version
and performs no network I/O; changelog/commit screens enter error state.
U3. `checkForUpdate` parsing (with an injected/faked response): equal tag → no update; newer tag
with changelog.json → sections/description/image; missing changelog.json → body fallback with the
Markdown image removed; APK asset selection skips debug assets and formats size to one decimal;
no APK asset → no update; malformed JSON → onError.
U4. `fetchChangelogForVersion` matches the exact normalised tag, not just latest; returns null when
no match or empty content.
U5. Preferences round-trip and defaults (§6.1).
U6. Worker: non-200 5xx → retry; 4xx → failure; IOException → retry; zip URL extracts the first
APK and deletes the zip; `nightly-r42` writes `last_installed_nightly_run = 42`; progress
notifications are at most 1 Hz; success output contains `file_path`.
U7. Download notifications: channel id and notification id match; complete notification PendingIntent
is an immutable `content://<authority>.FileProvider/...` URI with package-archive MIME and grant
flag; failure uses a BigText containing the error.
U8. Install flow: when `canRequestPackageInstalls()` is false, launch
`ACTION_MANAGE_UNKNOWN_APP_SOURCES` with `package:<packageName>` and do not launch the APK intent;
when true, launch with the FileProvider URI and flags. Missing file resets completion state.
U9. Update screen state machine: auto-check enabled → Checking → Available/NoUpdate; disabled →
stays NoUpdate; error → Error; WorkManager RUNNING/SUCCEEDED/FAILED/CANCELLED map as §6.4; primary
button label transitions percentage → Install → Update available; buttons disabled as specified.
U10. Storage: count/clear/auto-clear only touch `.apk` files in the expected directory; missing
directory is safe; clear reports false if a delete fails.

### 10.8 Audio, Bluetooth, icons

A1. Device load: mapping of every `AudioDeviceInfo` type; battery only for granted Bluetooth and
0..100; active precedence preferred id → A2DP → wired → built-in speaker; sorting and de-dup.
A2. Casting prepends one active `CHROMECAST` device, deactivates local devices, volume range 100.
A3. `isBuds`/`isSpeaker` match the documented substrings case-insensitively and return false for
null.
A4. `isBluetoothHeadphoneConnected` true for A2DP or SCO output; `getConnectedBluetoothDeviceName`
prefers A2DP product name and returns null when Bluetooth is inactive.
A5. `IconUtils.setIcon(DEFAULT)` enables `MainActivityAlias` and disables `MainActivityStatic`;
`STATIC` does the inverse; both use `DONT_KILL_APP`; exactly one is enabled after each call.
A6. A11y: device and addon chips report `Role.RadioButton` and checked/selected state to
accessibility services; the volume control exposes its label, current value and range; icon-only
interactive buttons expose a content description.

### 10.9 Accessibility and screenshot/preview targets

S1. Every interactive widget element is either a text view with meaningful text or has an
`android:contentDescription` in its layout. Decorative art/icons that duplicate adjacent text have
no content description.
S2. Screens expose headings/actions as text: Update screen buttons, dialog buttons, changelog
release chips, commit rows, recognizer state text.
S3. Preview/screenshot targets exist and render deterministically for:
widgets all sizes (idle and playing), recognizer all sizes/states, UpdateScreen all four states,
all update dialogs, ChangelogScreen loading/error/content/cached, CommitScreen loading/error/list,
AudioDeviceBottomSheet loading/error/content/casting/expanded.
S4. Rotation/theme: layouts do not crash in dark/light themes; widget bitmaps are software bitmaps
(`allowHardware(false)`) and RemoteViews never carries a hardware bitmap.

---

## 11. Non-goals

- No additional widgets, widget settings screens, or live wallpaper style rendering.
- No new persistence: widget and recognizer state stays in SharedPreferences; updater cache stays in
  files; downloaded APKs stay in `externalFilesDir`.
- No dependency on a live Shazam/GitHub service for unit tests; network parsing must be testable
  through pure helpers or injectable responses.
- No unrequested public API beyond the types/constants listed here; adding helpers is allowed only
  if all names, signatures, constants, manifest entries and behaviours above remain intact.
- `widget_device_connect.xml`, `widget_aurora_studio.xml`, the unused `createDeviceConnectRemoteViews`
  path, and unused accent-colour plumbing need not be recreated; if retained they must not change
  any contract above.
