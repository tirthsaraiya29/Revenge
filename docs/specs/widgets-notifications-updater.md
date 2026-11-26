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
