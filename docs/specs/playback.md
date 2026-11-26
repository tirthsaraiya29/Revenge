# Clean-Room Specification — `:playback`

Status: M2 specification (behavioural contract only).
Module: `:playback` — directory `playback`, Gradle path `:playback`.
Declared dependencies: `implementation(project(":core"))`,
`api(project(":audio-dsp"))`, `api(project(":dsp-core"))`, `api(project(":usbaudio"))`,
`api(media3-exoplayer)`, `api(media3-session)`, `api(media3-hls)`;
Hilt (`implementation` + KSP), core-library desugaring, JUnit for tests.
Android: `namespace = "com.revenge.playback"`, `compileSdk = 37`, `minSdk = 26`,
Java 21 source/target, Kotlin toolchain 21.

This document describes observable behaviour and API shape. It intentionally omits
statement-level implementation. Any implementation that satisfies the contracts and
acceptance tests is acceptable.

---

## 1. Module purpose

`:playback` owns the transport-independent parts of the playback engine:

- Queue sources (`Queue` and its implementations) that turn local database rows,
  InnerTube pages, or already-loaded lists into Media3 `MediaItem` windows.
- Queue snapshot/restore mapping (`Queue.toPersistQueue`, `PersistQueue.toQueue`).
- Media3 `AudioSink` routing between the platform sink and a USB DAC sink.
- A chunked, resumable `DataSource` wrapper used for streaming and downloads.
- Premature-cutoff detection, task-removal policy, and relative seek resolution.
- Sleep timer with fade-out, observable as Compose state.
- Adaptive standby-player pre-arming and equal-power crossfade scheduling.
- Offline beat/key/mix-point analysis (`BeatAnalyzer`) and PCM silence detection.

The Media3 `MusicService` / `MediaLibraryService`, media session, audio focus,
notification/Bluetooth transport controls, shuffle/repeat/speed state, gapless
configuration and the actual crossfade driving loop live in `:app`. They consume this
module through the contract in Section 8. This specification does not assign those
files to `:playback`; it constrains what `:playback` must provide to them.

### 1.1 Clean-room namespace

The baseline `:playback` used the split package `com.revenge.music.playback.*` and a
test-only `android.net` shadow. The binding architecture contract
(`docs/provenance/ARCHITECTURE_CONTRACTS.md`, section 2) requires the clean-room
namespace:

| Baseline FQCN | Target FQCN |
|---|---|
| `com.revenge.music.playback.X` | `com.revenge.playback.X` |
| `com.revenge.music.playback.queues.X` | `com.revenge.playback.queues.X` |
| `com.revenge.music.playback.crossfade.X` | `com.revenge.playback.crossfade.X` |
| `com.revenge.music.playback.audio.X` | `com.revenge.playback.audio.X` |

Only the root package changes: every simple type name, member name, parameter name,
parameter order and default value below is unchanged. All `:app` import sites must be
updated to the target names; no production source may declare a class in `android.*`.

Types referenced from `:core` / `:innertube` in this document keep their current
baseline FQCNs because those modules have not migrated yet. If their M2 specs land
first, apply only the documented package rename; field and method shapes must remain.

---

## 2. Threading and coroutine contract

- `Queue.getInitialStatus()` and `Queue.nextPage()` are `suspend`; network/database
  queue implementations do their blocking work inside `withContext(Dispatchers.IO)`.
  They never touch the player or Compose state. Callers invoke them from a background
  dispatcher and apply results on their own thread.
  - Implementations that only reshape an already-loaded list (`ListQueue`,
    `EmptyQueue`) return without dispatch.
  - `YouTubePlaylistQueue.nextPage()` may return already-loaded pending items without
    dispatch; only continuation network work is dispatched.
- `Queue.hasNextPage()` and `RadioQueuePageFilter` are non-suspending, do not block,
  and do not perform I/O.
- `CancellationException` thrown by any downstream call must propagate unchanged; it
  must never be converted to an empty page or a generic failure. Other exceptions are
  handled per Section 3.2.
- `AdaptivePreArmManager.recordPreparationDuration` and `reset` are `@Synchronized`;
  `currentLeadTimeMs` is `@Volatile`. Every other class in this module is confined to
  its owning player/service thread and provides no internal locking. Callers must not
  call `PlaybackCutoffGuard`, `ChunkingDataSource`, `RoutingAudioSink`,
  `SleepTimer`, `StandbyCrossfadeSchedulerImpl`, `SilenceDetectorAudioProcessor` or a
  `Queue` instance concurrently except where explicitly stated.
- `SilenceDetectorAudioProcessor.instantModeEnabled` and its silence counters are
  `@Volatile` because the service toggles instant mode from another thread while the
  audio processor reads on the playback thread.
- `RoutingAudioSink.activeSink` is `@Volatile` and read-only outside the class.
- `SleepTimer` state is Compose `mutableStateOf` / `mutableLongStateOf`; mutations must
  occur on the main thread (the caller supplies a main-dispatcher scope).

---

## 3. Public API — package `com.revenge.playback`

### 3.1 `AudioSinkSelector`

```kotlin
object AudioSinkSelector {
  fun selectSink(
    isBitPerfectActive: Boolean,
    usbDacAudioSink: AudioSink?,
    defaultAudioSink: AudioSink,
  ): AudioSink

  fun createRoutingSink(
    defaultAudioSink: AudioSink,
    usbDacAudioSink: AudioSink,
    isBitPerfectActive: () -> Boolean,
  ): AudioSink
}
```

- `selectSink` returns the USB sink only when `isBitPerfectActive && usbDacAudioSink != null`;
  otherwise the default sink. It is pure and never throws for a null USB sink.
- `createRoutingSink` returns a new `RoutingAudioSink` wired to the supplied provider.
  The provider is consulted on every routing decision; it is not cached.

### 3.2 `RoutingAudioSink`

```kotlin
class RoutingAudioSink(
  val defaultSink: AudioSink,
  val usbSink: AudioSink,
  private val isBitPerfectActive: () -> Boolean,
) : AudioSink {
  @Volatile var activeSink: AudioSink
    private set

  fun reselect()
  // all remaining members are AudioSink overrides
}
```

Contract:

1. Construction performs one `reselect()`; `activeSink` starts as the selected sink.
2. `reselect()` target:
   - provider returns `false` -> `defaultSink`.
   - provider returns `true` -> `usbSink`, except when a configuration exists and
     `usbSink.supportsFormat(configuredFormat)` is `false`, in which case `defaultSink`.
   - if the target is identical (`===`) to `activeSink`, return without side effects.
3. On a real switch: set `activeSink` first, then `previous.pause()` and
   `previous.flush()`, then if a last configuration exists and the new sink supports
   its format, `wanted.configure(format, bufferSize, outputChannels)`. If the sink was
   playing (`play()` was the last transport call and no `pause()`/`reset()` followed),
   call `wanted.play()`.
4. `configure(inputFormat, specifiedBufferSize, outputChannels)` records the
   configuration first, calls `reselect()`, then configures the now-active sink with the
   same arguments. Reconfiguration must always be replayed after a switch.
5. `setListener`: register on both sinks. `supportsFormat`, `getFormatSupport`,
   `getCurrentPositionUs`: delegate to `activeSink`.
6. `play`: `reselect()`, mark playing, `activeSink.play()`. `pause`: clear playing and
   pause both sinks. `flush`: `reselect()` then flush both sinks. `reset`: clear
   playing, reset both sinks, clear the recorded configuration, then `reselect()`.
7. `handleDiscontinuity`, `handleBuffer`, `playToEndOfStream`, `isEnded`,
   `hasPendingData`, `getAudioTrackBufferSizeUs`: delegate to `activeSink`.
8. `getPlaybackParameters`, `getSkipSilenceEnabled`, `getAudioAttributes` read from
   `activeSink`; `getAudioAttributes()` returns `AudioAttributes.DEFAULT` when the
   active sink returns null.
9. `setPlaybackParameters`, `setSkipSilenceEnabled`, `setAudioAttributes`,
   `setAudioSessionId`, `setAuxEffectInfo`, `enableTunnelingV21`, `disableTunneling`,
   `setVolume`: apply to both sinks so a later switch keeps state. These must not be
   lost by a switch.
10. Exceptions from either sink propagate; the class does not swallow them.

### 3.3 `ChunkingDataSource` and `ChunkingDataSourceFactory`

```kotlin
class ChunkingDataSource(
  private val upstream: DataSource,
  private val chunkSize: Long,
  private val maxRetries: Int = 3,
) : DataSource

class ChunkingDataSourceFactory(
  private val upstreamFactory: DataSource.Factory,
  private val chunkSize: Long = 5L * 1024 * 1024,
) : DataSource.Factory
```

`DataSource` contract:

- `addTransferListener` forwards to the upstream instance.
- `open(dataSpec)` stores the spec, `expectedLength = dataSpec.length`, sets consumed
  to 0, marks open, opens the first chunk, and returns `expectedLength` exactly
  (including `C.LENGTH_UNSET` = -1 when the request length is unknown).
- Chunks are opened at `data.position + consumed`, with requested length
  `min(chunkSize, remaining)` when the total is known and `chunkSize` when unknown.
  A non-positive `chunkSize` is not validated; callers must pass a positive value.
- `read(buffer, offset, readLength)`:
  1. `readLength == 0` -> return 0.
  2. not open, or zero bytes remaining (known length) -> `C.RESULT_END_OF_INPUT`.
  3. Delegate to the upstream. Positive byte counts increment `consumed` and are
     returned. `InterruptedIOException` propagates immediately without retry.
  4. Any other `Exception` is remembered, the upstream is closed best-effort, and a
     fresh chunk is opened at the current position.
  5. A reopened chunk that immediately returns bytes succeeds. A zero/negative read
     after reopen counts as an attempt.
  6. `InvalidResponseCodeException` with HTTP 416 -> `RESULT_END_OF_INPUT`.
     `DataSourceException` with `POSITION_OUT_OF_RANGE` -> `RESULT_END_OF_INPUT`.
     Other errors retry up to `maxRetries` attempts.
  7. Between attempts sleep `attempt * 50 ms`, capped at `250 ms`. An interrupted
     sleep restores the interrupt flag and throws `InterruptedIOException`.
  8. After retries, if the total length was known and `consumed < expectedLength`,
     throw `IOException` (premature EOF). Else if an error was seen, throw
     `IOException`; else return end-of-input.
- `getUri()` returns `upstream.uri` (nullable). `close()` marks closed and closes the
  upstream once. A closed source must not be reopened; further reads return end.
- Consumption is stateful and single-threaded. The source must not be shared between
  concurrent readers. The wrapper must not assume that the upstream honours the
  requested chunk length; a short read is handled by the retry/reopen path, not by
  immediately treating it as end-of-stream when bytes remain.

`ChunkingDataSourceFactory.createDataSource()` returns
`ChunkingDataSource(upstreamFactory.createDataSource(), chunkSize)`. Every created
source must be independent.

### 3.4 `CutoffDecision` and `PlaybackCutoffGuard`

```kotlin
sealed interface CutoffDecision {
  data object AllowEnd : CutoffDecision
  data class RecoverPrematureCutoff(
    val resumePositionMs: Long,
    val retryAttempt: Int,
  ) : CutoffDecision
}

class PlaybackCutoffGuard(
  private val thresholdMs: Long = 3000L,
  private val maxRetriesPerTrack: Int = 2,
) {
  fun onTrackChanged(mediaId: String?)
  fun verifyTrackCompletion(
    currentPositionMs: Long,
    canonicalDurationMs: Long,
    playerDurationMs: Long = -1L,
  ): CutoffDecision
  fun getRetryCount(): Int

  companion object {
    fun computeEffectiveDuration(
      canonicalDurationSeconds: Int?,
      playerDurationMs: Long,
    ): Long
  }
}
```

- State is a single per-track retry counter, initially 0. `onTrackChanged` resets it;
  the `mediaId` argument is accepted but unused. The guard is not thread-safe; the
  service calls it from one thread.
- `verifyTrackCompletion` decision order:
  1. `position = max(currentPositionMs, 0)`.
  2. If `playerDurationMs > 0` and `playerDurationMs - position <= thresholdMs`,
     return `AllowEnd`. The decoder's own duration wins over longer metadata.
  3. If `canonicalDurationMs <= 0`, return `AllowEnd` (unknown/live duration).
  4. `missing = canonicalDurationMs - position`. If `missing <= thresholdMs` or
     `retries >= maxRetriesPerTrack`, return `AllowEnd`.
  5. Increment retries and return `RecoverPrematureCutoff(position, retries)`.
- `getRetryCount` returns the current counter.
- `computeEffectiveDuration`: canonical seconds are clamped to at least 0 and
  multiplied by 1000. If positive, return that. Otherwise return `playerDurationMs`
  when it is neither `C.TIME_UNSET` nor non-positive (`> 0` and `!= C.TIME_UNSET`),
  else 0. It never throws for null.
- Threshold and retry values are constructor defaults, not global constants; callers
  may construct a different guard.

### 3.5 `shouldStopPlaybackOnTaskRemoval` and `seekButtonTarget`

```kotlin
fun shouldStopPlaybackOnTaskRemoval(
  stopOnTaskClear: Boolean,
  playWhenReady: Boolean,
  playbackState: Int,
): Boolean

fun seekButtonTarget(positionMs: Long, durationMs: Long, offsetMs: Long): Long
```

- `shouldStopPlaybackOnTaskRemoval` returns true when any of: `stopOnTaskClear`,
  `!playWhenReady`, `playbackState == Player.STATE_IDLE`,
  `playbackState == Player.STATE_ENDED`. It returns false for buffering or ready while
  `playWhenReady` is true. The explicit preference always wins.
- `seekButtonTarget`:
  1. Clamp `positionMs` to at least 0.
  2. If `offsetMs > 0` and `position > Long.MAX_VALUE - offsetMs`, use
     `Long.MAX_VALUE`; otherwise `position + offsetMs`, clamped to at least 0.
  3. If `durationMs >= 0`, clamp the target to `durationMs`; a negative duration means
     unknown, so apply no upper bound.
  4. Offset 0 is a legal no-op seek (after clamping).
  Overflow must never wrap negative.

### 3.6 `SleepTimer`

```kotlin
class SleepTimer(
  private val scope: CoroutineScope,
  var player: Player,
) : Player.Listener {
  var triggerTime: Long
    private set          // Compose mutableLongStateOf
  var pauseWhenSongEnd: Boolean
    private set          // Compose mutableStateOf
  val isActive: Boolean

  fun start(minute: Int)
  fun notifySongTransition()
  fun clear()

  override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int)
  override fun onPlaybackStateChanged(playbackState: Int)
}
```

- Sentinel state: `triggerTime == -1L` means no countdown; `pauseWhenSongEnd == true`
  means "pause after the current song". `isActive = triggerTime != -1L || pauseWhenSongEnd`.
- `start(-1)` cancels any running countdown, sets `pauseWhenSongEnd = true` and
  `triggerTime = -1L`, and does not fade now.
- `start(minute)` for any other value cancels the previous job, sets
  `pauseWhenSongEnd = false`, sets `triggerTime = System.currentTimeMillis() +
  minute.minutes.inWholeMilliseconds`, and launches a job in `scope`. The job waits
  the remaining duration (if positive), fades out and pauses, then resets
  `triggerTime = -1L`. `minute <= -2` therefore schedules an immediate fade. The UI
  only passes `-1` or a non-negative countdown.
- `notifySongTransition()` is a no-op unless `pauseWhenSongEnd` is true. When true it
  clears the flag first, then launches one fade-out-and-pause in `scope`. Repeated
  calls after the first are no-ops. It is safe to call from any thread that may touch
  the Compose state (main).
- `onMediaItemTransition` calls `notifySongTransition()`.
- `onPlaybackStateChanged(STATE_ENDED)` calls `notifySongTransition()`.
- `clear()` cancels the countdown job (if any), clears `pauseWhenSongEnd`, and resets
  `triggerTime` to `-1L`. It is idempotent and safe before any `start`.
- Fade behaviour: capture the current player volume, apply 30 linear steps of 100 ms
  each (about 3 s) with gain `startVolume * (remainingSteps / 30)` for
  `remainingSteps = 30, 29, ..., 1` (the final step is `startVolume / 30`, not zero),
  then call `player.pause()` and restore the original volume. If the player is
  released or throws during the fade, the exception is not swallowed by this class;
  callers should clear the timer when releasing playback.
- `player` is a public mutable property because the service replaces the player on
  reconnect; the next fade uses the current value.

### 3.7 Queue snapshot mapping — `QueueExt.kt`

```kotlin
fun Queue.toPersistQueue(
  title: String?,
  items: List<MediaMetadata>,
  mediaItemIndex: Int,
  position: Long,
): PersistQueue

fun PersistQueue.toQueue(): Queue
```

`toPersistQueue`:

- Runtime type mapping, in this order: `YouTubeQueue` -> `QueueType.YOUTUBE`;
  `YouTubeAlbumRadio` -> `QueueType.YOUTUBE_ALBUM_RADIO`; `LocalAlbumRadio` ->
  `QueueType.LOCAL_ALBUM_RADIO`; every other `Queue` (including `EmptyQueue`,
  `ListQueue`, `LocalMixQueue`, `YouTubePlaylistQueue`, app-defined queues) ->
  `QueueType.LIST`.
- Queue data is a placeholder identity, not a resumable network token:
  - `YOUTUBE` -> `QueueData.YouTubeData(endpoint = "youtube_queue")`.
  - `YOUTUBE_ALBUM_RADIO` ->
    `QueueData.YouTubeAlbumRadioData(playlistId = "youtube_album_radio")`.
  - `LOCAL_ALBUM_RADIO` ->
    `QueueData.LocalAlbumRadioData(albumId = "local_album_radio", startIndex = 0)`.
  - `LIST` -> null.
- Returns `PersistQueue(title, items, mediaItemIndex, position, queueType, queueData)`.
  The caller may `.copy(shuffledIndices = ...)` afterwards.
- The supplied `items`, title, index and position are preserved verbatim; no
  filtering, deduplication or clamping happens here.

`toQueue`:

- Always returns a finite `ListQueue(title, items.map { it.toMediaItem() },
  startIndex = mediaItemIndex, position = position)`.
- Continuations, radio sessions and queue type are intentionally not restored; the
  UI or service may later re-create a continuation queue. Restoration must not throw
  for a `null` title, empty items, negative position or an out-of-range index; those
  values are passed through to `ListQueue`.
- `MediaMetadata.toMediaItem()` is the `:core` extension described in Section 7.

---

## 4. Public API — package `com.revenge.playback.queues`

### 4.1 `Queue` and `Queue.Status`

```kotlin
interface Queue {
  val preloadItem: MediaMetadata?
  suspend fun getInitialStatus(): Status
  fun hasNextPage(): Boolean
  suspend fun nextPage(): List<MediaItem>

  data class Status(
    val title: String?,
    val items: List<MediaItem>,
    val mediaItemIndex: Int,
    val position: Long = 0L,
  ) {
    fun filterExplicit(enabled: Boolean = true): Status
    fun filterVideoSongs(disableVideos: Boolean = false): Status
  }
}

fun List<MediaItem>.filterExplicit(
  enabled: Boolean = true,
  keepMediaId: String? = null,
): List<MediaItem>

fun List<MediaItem>.filterVideoSongs(
  disableVideos: Boolean = false,
  keepMediaId: String? = null,
): List<MediaItem>
```

- `preloadItem` is a metadata-only item that the service may play immediately while
  `getInitialStatus()` is still loading. `null` means the first page is required before
  playback can start. The queue does not mutate the player.
- `getInitialStatus()` returns the first window, its title (nullable), the index in
  `items` of the item to start at, and an optional start position in ms. It may throw;
  callers handle failure. It is called once per queue session. `YouTubeQueue`
  explicitly resets session state on each call; other stateful queues assume one call.
- `hasNextPage()` is a cheap, non-blocking hint. `nextPage()` may still return an empty
  list when the remote continuation is exhausted, deduplicated away, or aborted.
- `nextPage()` returns a non-null list, never null. It may throw; callers keep the
  current queue and stop extending on failure. Cancellation propagates.
- `position` is currently 0 for every implementation except `ListQueue`, which
  forwards its constructor value.

`Queue.Status.filterExplicit(enabled)`:

- `enabled == false` returns `this` unchanged (same instance).
- When enabled, remove every item whose `MediaItem.metadata?.explicit == true` except
  the currently selected item (`items[mediaItemIndex].mediaId`).
- The selected item is identified by both occurrence and id: after removal, keep the
  selection on the same occurrence-of-that-id that existed at or before the old index.
  If that occurrence no longer exists, fall back to the old index clamped into the
  filtered list (`0..max(size-1, 0)`). An empty filtered list yields index 0.
- Items with no readable metadata are treated as non-explicit and kept.

`Queue.Status.filterVideoSongs(disableVideos)`:

- `disableVideos == false` returns `this` unchanged (same instance).
- When disabled/enabled, remove every item where `MediaItem.metadata?.isVideoSong ==
  true` and `musicVideoType != MUSIC_VIDEO_TYPE_PODCAST_EPISODE`, except the selected
  item. Podcast episodes are audio content and are never dropped by this filter.
- Repositioning follows exactly the same occurrence/clamp rule as `filterExplicit`.
- The list extensions apply the same predicates; `keepMediaId` keeps an item even when
  it is not the selected index. `filterExplicit(false)` / `filterVideoSongs(false)`
  return the same list instance. Null `keepMediaId` means no keep.

### 4.2 `EmptyQueue`

```kotlin
object EmptyQueue : Queue
```

- `preloadItem = null`.
- `getInitialStatus()` returns `Status(title = null, items = emptyList(),
  mediaItemIndex = -1)`.
- `hasNextPage() = false`; `nextPage()` returns `emptyList()`.

### 4.3 `ListQueue`

```kotlin
class ListQueue(
  val title: String? = null,
  val items: List<MediaItem>,
  val startIndex: Int = 0,
  val position: Long = 0L,
) : Queue
```

- `preloadItem = null`.
- `getInitialStatus()` returns exactly `Status(title, items, startIndex, position)`;
  no clamping, deduplication or copying is observable.
- `hasNextPage() = false`.
- `nextPage()` throws `UnsupportedOperationException` (a finite queue has no pages).
- Public properties are read by callers and must remain `val` with these names/types.

### 4.4 `Queue.Status` selection helper semantics used by consumers

`mediaItemIndex` is an index into `items`, not into the player timeline. A `-1` index
is legal for an empty queue. Filtering must keep the `position` value unchanged and
only adjust `items` and `mediaItemIndex`.

### 4.5 `YouTubeQueue`

```kotlin
class YouTubeQueue(
  private var endpoint: WatchEndpoint,
  override val preloadItem: MediaMetadata? = null,
) : Queue {

  override suspend fun getInitialStatus(): Status
  override fun hasNextPage(): Boolean
  override suspend fun nextPage(): List<MediaItem>

  companion object {
    fun radio(song: MediaMetadata): YouTubeQueue
  }
}
```

Construction and mode:

- `radioMode = endpoint.playlistId?.startsWith("RD") != false`. This is true when
  `playlistId` is null or starts with `"RD"`; false for playlist ids such as `"PL..."`
  and for the empty string.
- `radio(song)` returns `YouTubeQueue(WatchEndpoint(videoId = song.id), song)`; the
  song is the preload item.

`getInitialStatus()`:

1. Reset `continuation` to null and reset the session radio deduplication set.
2. If `endpoint.videoId` starts with `"addon_"` and `preloadItem != null`, mark an
   addon request and resolve a real endpoint by searching
   `"<title> <first artist name>"` with `YouTube.SearchFilter.FILTER_SONG`, taking the
   first `SongItem`. Search failure (including cancellation) must not abort: cancel
   rethrows, other errors are ignored and the original endpoint is used.
3. Attempt `YouTube.next(endpoint, continuation)` up to 4 times (initial attempt plus
   3 retries). On each success, adopt `page.endpoint` and `page.continuation`.
4. Determine `current = page.items.getOrNull(page.currentIndex ?: 0)` and
   `episodeRadio = current?.isPodcastEpisode == true`.
5. If `episodeRadio` and any `preloadItem.artist.id` satisfies
   `PodcastPage.isShowBrowseId(id)`, return a single-item status:
   `title = page.title`, `items = listOf(preloadItem.toMediaItem())`,
   `mediaItemIndex = 0`, `continuation = null`. A known podcast show must continue with
   its next episode, never with music radio.
6. Apply deduplication only in radio mode, using a session-wide
   `RadioQueuePageFilter`. If `episodeRadio`, keep only podcast episodes from the
   deduplicated page.
7. Map the kept songs to `MediaItem`s. If this is an addon request and the mapped list
   is empty, insert the preload item; if non-empty, replace element 0 with the preload
   item (preserving its position as the requested track).
8. Return `Status(title = page.title, items = mapped,
   mediaItemIndex = if (addon) 0 else indexOfFirst { it.id == current?.id }.coerceAtLeast(0))`.
9. Retry rule: on the first failure only, if the endpoint has a `videoId` and no
   `playlistId`, rewrite the endpoint to
   `WatchEndpoint(videoId = same, playlistId = "RDAMVM" + videoId)` and retry. Other
   failures consume the remaining attempts.
10. If all attempts fail and this is an addon request with a preload item, return
    `Status(title = preloadItem.title, items = listOf(preloadItem.toMediaItem()),
    mediaItemIndex = 0)`. Otherwise rethrow the last error, or
    `IllegalStateException("Failed to get initial status")` if none was recorded.
    `CancellationException` always propagates immediately.

`hasNextPage()` is `continuation != null`.

`nextPage()`:

- If there is no continuation, return `emptyList()`.
- Keep a set of continuation tokens visited during this call. At most 5 page hops per
  call. If the next continuation was already visited, set `continuation = null` and
  return `emptyList()` (a page pointing to itself must not loop).
- For each hop, attempt `YouTube.next(endpoint, activeContinuation)` up to 4 times.
  On success adopt `page.endpoint`; set `continuation = page.continuation` unless it
  equals the token just used (self-loop guard).
- Apply radio deduplication in radio mode and episode filtering as above.
  - Non-empty result: map and return.
  - Empty result: continue to the next hop (another page may have fresh tracks).
- On exception: cancellation rethrows; other failures retry within the hop. After the
  final failure the last exception is thrown.
- After 5 hops without a non-empty result, return `emptyList()`.

### 4.6 `YouTubeAlbumRadio`

```kotlin
class YouTubeAlbumRadio(
  private var playlistId: String,
) : Queue
```

- `preloadItem = null`.
- `getInitialStatus()`: call `YouTube.albumSongs(playlistId).getOrThrow()`; record the
  returned song count; return `Status(title = firstSong.album?.name.orEmpty(),
  items = all songs mapped, mediaItemIndex = 0)`. Failure propagates. The queue is
  single-use; repeated calls reload and reset the count.
- `hasNextPage()` is `!loadedOnce || continuation != null`.
- `nextPage()`: fetch `YouTube.next(WatchEndpoint(playlistId = playlistId),
  continuation).getOrThrow()`; store `page.continuation`. On the first successful
  page, set `loadedOnce = true` and return `page.items.drop(albumSongCount)` because the
  first page repeats the album tracks. Subsequent pages return all items. Failure
  propagates; `loadedOnce` must only become true after a successful page.

### 4.7 `LocalAlbumRadio`

```kotlin
class LocalAlbumRadio(
  private val albumWithSongs: AlbumWithSongs,
  private val startIndex: Int = 0,
) : Queue
```

- `preloadItem = null`.
- `getInitialStatus()`: on `Dispatchers.IO`, return
  `Status(title = albumWithSongs.album.title,
  items = albumWithSongs.songs.map { it.toMediaItem() }, mediaItemIndex = startIndex)`.
  `startIndex` is not clamped. The album is served from the local database.
- `hasNextPage()` is `!loadedOnce || continuation != null`; true before the first
  remote page because the album is followed by its YouTube radio.
- `nextPage()` on `Dispatchers.IO`:
  - If not loaded yet: resolve `YouTube.album(albumWithSongs.album.id).getOrThrow()`,
    take `album.playlistId`, store it, fetch `YouTube.next(endpoint, continuation)`,
    store the continuation, set `loadedOnce = true`, and return
    `page.items.drop(albumWithSongs.songs.size)` (the album part is already local; only
    the radio tail is appended).
  - Otherwise fetch the next page with the stored endpoint/continuation and return all
    items.
  - Any `getOrThrow` failure propagates. A failure before `loadedOnce = true` leaves
    the queue resumable on the next call (album resolution runs again).

### 4.8 `YouTubePlaylistQueue`

```kotlin
class YouTubePlaylistQueue(
  private val playlistId: String,
  private val playlistTitle: String? = null,
  private val initialSongs: List<SongItem> = emptyList(),
  private val initialContinuation: String? = null,
  private val startIndex: Int = 0,
  override val preloadItem: MediaMetadata? = null,
) : Queue {
  override suspend fun getInitialStatus(): Status
  override fun hasNextPage(): Boolean
  fun hasPendingSongs(): Boolean
  override suspend fun nextPage(): List<MediaItem>
}
```

Constants that are observable behaviour:

- `WINDOW_BEFORE = 20` songs kept before the selected song.
- `WINDOW_AFTER = 100` songs queued after the selected song.
- `PAGE_SIZE = 50` already-loaded songs appended per load-more step.
- `MAX_RETRIES = 3` continuation attempts.

`getInitialStatus()`:

- If `initialSongs.isEmpty()`: call `YouTube.playlist(playlistId).getOrThrow()`, set
  `continuation = page.songsContinuation`, and return
  `Status(page.playlist.title, page.songs mapped, mediaItemIndex = startIndex)`.
  `startIndex` is passed through; it is not clamped in this branch.
- Else (no network): `first = (startIndex - WINDOW_BEFORE).coerceAtLeast(0)` and
  `end = (startIndex + WINDOW_AFTER).coerceAtMost(initialSongs.size)`. Store
  `pendingSongs = initialSongs.subList(end, initialSongs.size)`. Return
  `Status(title = playlistTitle, items = initialSongs.subList(first, end) mapped,
  mediaItemIndex = startIndex - first)`.
- Callers must pass `0 <= startIndex <= initialSongs.size`; otherwise `subList`
  semantics apply (out-of-range indices fail before status is returned).
- A repeated call with non-empty `initialSongs` recomputes the window and replaces
  `pendingSongs`; it does not append.

`hasNextPage()` is `pendingSongs.isNotEmpty() || continuation != null`.

`hasPendingSongs()` is `pendingSongs.isNotEmpty()`. It exists because already-loaded
local songs must still be queued even when the user has disabled auto-load-more.

`nextPage()`:

1. If `pendingSongs` is non-empty, return the first `PAGE_SIZE` items mapped and
   remove exactly those from `pendingSongs`; no network.
2. Else if `continuation == null`, return `emptyList()`.
3. Else on `Dispatchers.IO`, attempt up to 4 times (`MAX_RETRIES + 1`):
   - `YouTube.playlistContinuation(active).getOrThrow()`.
   - On success set `continuation = page.continuation`, reset the consecutive failure
     counter to 0, return `page.songs` mapped.
   - On cancellation rethrow.
   - On other exceptions increment the failure counter, which is cumulative across
     `nextPage` calls until a success resets it; once it reaches `MAX_RETRIES`, set
     `continuation = null` (queue exhausted) but allow the current call's remaining
     attempt to run.
4. If every attempt fails, throw the last exception; if none was captured, throw
   `IllegalStateException("Playlist continuation failed")`.

### 4.9 `LocalMixQueue`

```kotlin
class LocalMixQueue(
  private val database: MusicDatabase,
  private val playlistId: String,
  private val maxMixSize: Int = 500,
) : Queue
```

- `preloadItem = null`; title constant is `"Mix from Playlist"`.
- `getInitialStatus()` on `Dispatchers.IO`:
  1. Read the first emission of `database.playlistSongs(playlistId)` and collect
     `map.songId` in playlist order.
  2. Build a local mix: for each playlist song id in order, flat-map
     `database.relatedSongs(id)`, remove songs whose id is in the playlist id list,
     `distinctBy { it.id }`, then `take(maxMixSize)`.
  3. If the local mix is non-empty, return `Status(title = "Mix from Playlist",
     items = local mix mapped, mediaItemIndex = 0)`.
  4. If no playlist songs exist, return `Status("Mix from Playlist", emptyList(), 0)`.
  5. Otherwise take the first playlist song id as the seed. Call
     `YouTube.next(WatchEndpoint(videoId = seedId)).getOrNull()`.
     - `fromNext` = its `items` mapped, filtered to ids not in the playlist.
     - `fromRelated` = if `relatedEndpoint != null`, `YouTube.related(endpoint)`
       `.getOrNull()?.songs` mapped and filtered the same way.
     - Return `fromNext + fromRelated`, `distinctBy { it.mediaId }`, then
       `take(maxMixSize)`, index 0.
  6. YouTube failures are swallowed (`getOrNull`); the flow still returns a status.
     Database failures and cancellation propagate.
- `hasNextPage() = false`; `nextPage()` returns `emptyList()`. The mix is finite; the
  service's related-song extension handles continuation.

### 4.10 `RadioQueuePageFilter` (internal)

```kotlin
internal class RadioQueuePageFilter {
  fun reset()
  fun filter(songs: List<SongItem>): List<SongItem>
}
```

- Holds a set of emitted song ids for one radio session.
- `filter` returns only songs whose id was not already emitted, in input order, and
  records those ids. Duplicate ids inside one page yield only the first occurrence.
- `reset` clears the set so a new session can emit the same ids again.
- Not thread-safe; a single queue owns it.

---

## 5. Public API — package `com.revenge.playback.crossfade`

### 5.1 `AdaptivePreArmManager`

```kotlin
class AdaptivePreArmManager(
  val baseLeadTimeMs: Long = 20_000L,
  val maxLeadTimeMs: Long = 35_000L,
  private val slowPrepThresholdMs: Long = 8_000L,
) {
  @Volatile var currentLeadTimeMs: Long
    private set
  @Synchronized fun recordPreparationDuration(durationMs: Long)
  @Synchronized fun reset()
}
```

- Constructor requires `baseLeadTimeMs > 0`, `slowPrepThresholdMs > 0`,
  `maxLeadTimeMs >= baseLeadTimeMs`; otherwise throws `IllegalArgumentException` with
  a message naming the parameter.
- Initial `currentLeadTimeMs == baseLeadTimeMs`.
- `recordPreparationDuration` appends to a bounded history of the last 5 durations.
  Then:
  - If `durationMs > slowPrepThresholdMs`:
    `extra = ((durationMs - slowPrepThresholdMs) * 1.5).toLong()`;
    `lead = min(maxLeadTimeMs, max(currentLeadTimeMs, baseLeadTimeMs + extra))`.
  - Else, if the arithmetic mean of the history is strictly below
    `slowPrepThresholdMs`: `lead = max(baseLeadTimeMs, currentLeadTimeMs - 2000)`.
    (A fast sample contracts the lead only when the recent average is also fast.)
- `reset` restores `baseLeadTimeMs` and clears history.
- Because the fields are public `val`s, consumers may read the configured bounds.

### 5.2 `EqualPowerCurve`

```kotlin
object EqualPowerCurve {
  data class Gains(val outgoingGain: Float, val incomingGain: Float)
  fun calculateGains(progress: Float): Gains
}
```

- Non-finite progress (`NaN`, infinities) is treated as 0.
- Finite progress is clamped to `[0, 1]`.
- `angle = clamped * (PI / 2)`; `outgoingGain = cos(angle)`,
  `incomingGain = sin(angle)`.
- Invariant: `outgoingGain^2 + incomingGain^2 == 1` within float tolerance for every
  progress in `[0, 1]`. Endpoints are `(1, 0)` and `(0, 1)`; midpoint is
  `(0.7071, 0.7071)` (about -3 dB each).

### 5.3 `ArmedStatus` and `StandbyCrossfadeScheduler`

```kotlin
sealed interface ArmedStatus {
  data object Idle : ArmedStatus
  data class Preparing(val targetMediaId: String, val startedAt: Long) : ArmedStatus
  data class Armed(val standbyPlayer: ExoPlayer, val targetMediaId: String) : ArmedStatus
  data class Fading(val progress: Float) : ArmedStatus
}

interface StandbyCrossfadeScheduler {
  val isCrossfading: StateFlow<Boolean>
  val armedState: StateFlow<ArmedStatus>
  val preArmManager: AdaptivePreArmManager
  var onCrossfadeSwapped: ((outgoingPlayer: ExoPlayer, incomingPlayer: ExoPlayer) -> Unit)?

  fun onQueueOrTrackChanged(
    primaryPlayer: ExoPlayer,
    nextItem: MediaItem?,
    crossfadeDurationMs: Long,
  )
  fun onPlaybackPositionTick(
    primaryPlayer: ExoPlayer,
    currentPositionMs: Long,
    durationMs: Long,
    playerFactory: () -> ExoPlayer,
  )
  fun triggerCrossfadeNow(primaryPlayer: ExoPlayer)
  fun cancelCrossfade()
  fun release()
}
```

`onCrossfadeSwapped` is invoked exactly once per completed fade, after gains are
restored to unity and both volumes are handed to the caller. The caller owns the
incoming player from that point; the scheduler must not release or stop it.

### 5.4 `StandbyCrossfadeSchedulerImpl`

```kotlin
class StandbyCrossfadeSchedulerImpl(
  private val scope: CoroutineScope,
  override val preArmManager: AdaptivePreArmManager = AdaptivePreArmManager(),
) : StandbyCrossfadeScheduler {
  fun onStandbyPlayerStateChanged(playbackState: Int) // public, listener hook
}
```

State and decision contract:

- `isCrossfading` starts false, `armedState` starts `Idle`.
- `onQueueOrTrackChanged(primary, nextItem, duration)`:
  - If the media id of the new `nextItem` differs from the currently remembered id
    (including null vs non-null), call `cancelCrossfade()` first. Any prepared standby
    player for the old target is dropped.
  - Remember `nextItem` and `crossfadeDurationMs`. `primaryPlayer` is accepted but not
    retained in this call.
- `onPlaybackPositionTick(primary, position, duration, playerFactory)`:
  - No target -> return.
  - `fadeDurationMs <= 0` or `durationMs <= fadeDurationMs` -> return (no crossfade on
    a track shorter than the fade).
  - `remaining = durationMs - position`, `lead = preArmManager.currentLeadTimeMs`.
  - If state is `Idle`, `remaining > fadeDurationMs`, and `remaining <= lead`, prepare
    the standby player for the target via `playerFactory()`.
  - If state is `Armed`, `remaining <= fadeDurationMs`, and not already fading, begin
    the fade.
  - Both branches may run on the same tick only in the sense that the first moves
    state to `Preparing`, which the second does not treat as armed.
- Pre-arm:
  - Record `prepStartedAt = System.currentTimeMillis()`, set
    `Preparing(target.mediaId, startedAt)`.
  - Create the standby player via the factory, set volume 0, `playWhenReady = false`,
    attach a listener for `onPlaybackStateChanged`, set the target `MediaItem`, and
    `prepare()`.
  - If the factory throws, the exception propagates; the state may remain
    `Preparing` and the caller is responsible for `cancelCrossfade()`.
  - If the player is already `STATE_READY` synchronously (cached media), transition
    immediately.
- `onStandbyPlayerStateChanged(STATE_READY)` while `Preparing`:
  - `prepareMs = now - prepStartedAt`; if positive, record it in the pre-arm manager.
  - Set `Armed(standbyPlayer, targetMediaId)`.
  - Other playback states are ignored; listener callbacks after arming are ignored.
- `triggerCrossfadeNow(primary)`: if not fading and state is `Armed`, begin the fade
  immediately. No-op otherwise.
- Fade:
  - Requires a standby player and no fade already running; otherwise no-op.
  - Capture the primary, set `isCrossfading = true`, and set the incoming
    `playWhenReady = true`.
  - Cancel any previous fade job, then launch on `scope`:
    - `steps = (fadeDurationMs / 20).toInt().coerceAtLeast(10)`.
    - For step `0..steps` inclusive, `progress = step / steps`; publish
      `Fading(progress)`; apply equal-power gains to the primary (outgoing) and
      incoming players; delay 20 ms between steps. `progress` reaches exactly 1.
    - Volume writes are wrapped so a released player cannot abort the fade.
    - After the loop restore both volumes to 1, set `isCrossfading = false`,
      `armedState = Idle`, detach the listener, forget the standby/primary references,
      and invoke `onCrossfadeSwapped(primary, incoming)`.
  - The incoming player is not released by the scheduler.
- `cancelCrossfade()`:
  - Cancel and forget the fade job, set `isCrossfading = false`, `armedState = Idle`.
  - If a fade was in progress, restore the primary's volume to 1.
  - Detach the listener, then stop, clear media items and release the standby player.
    Release errors are swallowed. Null out all references.
  - Safe to call from any state, including before any queue is set.
- `release()` is exactly `cancelCrossfade()`.
- There is no automatic release of the primary player.

---

## 6. Public API — package `com.revenge.playback.audio`

### 6.1 `SilenceDetectorAudioProcessor`

```kotlin
@UnstableApi
class SilenceDetectorAudioProcessor(
  private val minSilenceDurationUs: Long = 2_000_000L,
  private val silenceThreshold: Int = 256,
  private val onLongSilence: () -> Unit,
) : AudioProcessor {
  @Volatile var instantModeEnabled: Boolean
  fun resetTracking()
  fun isCurrentlySilent(): Boolean
  // AudioProcessor overrides
}
```

- Only PCM 16-bit input is supported. `configure` throws
  `AudioProcessor.UnhandledAudioFormatException` for any other encoding and otherwise
  stores `sampleRate` and `channelCount` and returns the input format unchanged
  (pass-through).
- `isActive()` is always true: the processor stays in the chain even when scanning is
  disabled.
- `queueInput(inputBuffer)`:
  - If the input has no remaining bytes, produce an empty output buffer and return.
  - If `instantModeEnabled` and both sample rate and channel count are known, scan
    complete interleaved frames for silence; otherwise reset all silence state.
  - Always copy the input bytes unchanged to the output buffer. The processor must
    never alter audio, even while scanning.
- Scanning:
  - A frame's peak is the maximum absolute 16-bit sample across all channels.
  - A frame is silent when `peak < silenceThreshold` (strictly less).
  - Consecutive silent frames accumulate; when
    `silentFrames * 1_000_000 / sampleRate >= minSilenceDurationUs`, mark the stream
    silent and invoke `onLongSilence()` once for this silence run.
  - A non-silent frame resets the frame count, the silent flag and the "already
    reported" flag, so a later silence reports again.
  - Incomplete trailing bytes that do not form a full frame are not scanned.
- `queueEndOfStream()` sets end-of-stream. `getOutput()` returns the pending pass-through
  buffer once and clears it. `isEnded()` is true only after end-of-stream was queued
  and no output is pending.
- Deprecated `flush()` clears the pending output, end-of-stream and silence state.
  Deprecated `reset()` calls `flush()` and additionally forgets sample rate and
  channel count.
- `resetTracking()` resets only silence state. `isCurrentlySilent()` reports the
  current silence flag.
- Toggling `instantModeEnabled` off takes effect on the next `queueInput`: state is
  reset and no callbacks fire. Toggling it on mid-silence starts counting from the
  next buffer.
- The callback runs on the audio processing thread; it must be cheap and non-blocking.

### 6.2 `BeatAnalyzer`

```kotlin
@UnstableApi
object BeatAnalyzer {
  data class Result(
    val bpm: Float,
    val firstBeatOffsetMs: Long,
    val confidence: Float,
    val mixInPointMs: Long? = null,
    val mixOutPointMs: Long? = null,
    val keyPitchClass: Int? = null,
    val keyIsMinor: Boolean? = null,
  )

  class CachedAnalysis(
    val result: Result?,
    val complete: Boolean,
  )

  fun analyzeUri(
    context: Context,
    uri: Uri,
    shouldCancel: () -> Boolean = { false },
  ): Result?

  fun analyzeStream(
    dataSourceFactory: DataSource.Factory,
    mediaId: String,
    tempDir: File,
    shouldCancel: () -> Boolean = { false },
  ): CachedAnalysis?
}
```

`analyzeUri`:

- Opens the URI with `MediaExtractor`. It must never throw: any failure (no audio
  track, unsupported codec, decode failure, cancellation, I/O) returns null.
- If `shouldCancel()` is true at any checkpoint, return null.
- Analysis window: the middle 18 s of the track when the duration is known, otherwise
  from the start; seeks to the previous sync point. Fewer than
  `1024 * 8` decoded mono samples means null.
- Pipeline (behaviour, not implementation):
  1. Decode a mono PCM window.
  2. Build a spectral-flux onset envelope (1024-sample FFT, 512 hop, Hann window,
     log-compressed magnitudes, local mean removed).
  3. Estimate tempo by autocorrelation over periods corresponding to 60..180 BPM,
     preferring the doubled-lag candidate; refine the peak; confidence is
     `(peak - median) / peak` clamped to `[0, 1]`, or 0 when the peak is not positive.
     If the envelope is too short or has no energy, there is no result.
  4. Fold octaves into the canonical `[70, 140)` BPM range; the returned `bpm` must be
     in that range and the phase anchor follows the fold.
  5. Estimate the beat phase by comb filtering the onset envelope.
  6. `firstBeatOffsetMs = anchorMs mod periodMs`, normalized to `[0, periodMs)`.
  7. Estimate key from 12-bin chroma over 65..2000 Hz correlated against the
     Krumhansl-Schmuckler major/minor profiles. `keyPitchClass` is 0..11 (0 = C) or
     null when there is insufficient tonal energy; `keyIsMinor` is true for the minor
     profile, false for major, null only when `keyPitchClass` is null.
  8. Mix-in: analyze the first 16 s only when duration > 16 s. Detect the first
     sustained body-level 500 ms block (block energy compared to the 75th percentile);
     snap forward to the beat grid. Return null when the body starts within the first
     block, no sustained block is found, or the intro would exceed 20 s.
  9. Mix-out: analyze the last 24 s only when duration > 24 s. Find the last 500 ms
     block at or above 50% of the 75th percentile; return the block end. Return null
     when the track stays loud within 3 s of the end, or no loud block exists. The
     result is never earlier than `durationMs - 45_000`.
- `firstBeatOffsetMs` is always non-negative; `confidence` is in `[0, 1]`; `bpm` is in
  `[70, 140)`; mix points, when non-null, are non-negative and within the track
  duration.

`analyzeStream`:

- Chooses a temp file `File(tempDir, "beat_${mediaId.hashCode()}.tmp")` and always
  deletes it in a `finally` block, including on cancellation and failure.
- Opens `dataSourceFactory.createDataSource()` with a `DataSpec` whose URI and key are
  both `mediaId` (the URI is `Uri.parse(mediaId)`).
- Copies up to 5 MiB in 64 KiB chunks. While copying, checks `shouldCancel()` before
  each read; cancellation returns null. `C.RESULT_END_OF_INPUT` marks the fetch
  complete. Other read/open exceptions are logged and stop the fetch; partially
  copied bytes are still usable.
- If fewer than 256 KiB (256 * 1024) were copied, return null. If cancelled after the
  fetch, return null.
- Analyse the temp file. Return `CachedAnalysis(result, complete)` where `complete` is
  true only if end-of-input was reached before the 5 MiB cap. A null `result` with
  `complete == false` means "retry later with a fuller cache"; a null result with
  `complete == true` means analysis genuinely failed.
- The data source is always closed best-effort. No exception may escape: cancellation
  is reported by returning null, not by throwing (the caller's surrounding coroutine
  still observes its own cancellation).

Concurrency: both entry points are blocking and may be called from any background
thread. Separate calls must not share mutable state (no static caches).

---

## 7. Cross-module types this module needs (reproduce exactly)

The implementer must be able to compile `:playback` without reading `:core`,
`:innertube`, `:usbaudio` or `:audio-dsp`. The following shapes are the required
contracts. They are external modules; do not duplicate them inside `:playback`.

### 7.1 `:core` — `com.revenge.music.models.MediaMetadata`

```kotlin
enum class QueueItemSource { USER }

@Immutable
data class MediaMetadata(
  val id: String,
  val title: String,
  val artists: List<Artist>,
  val duration: Int,
  val thumbnailUrl: String? = null,
  val album: Album? = null,
  val setVideoId: String? = null,
  val musicVideoType: String? = null,
  val explicit: Boolean = false,
  val liked: Boolean = false,
  val likedDate: LocalDateTime? = null,
  val inLibrary: LocalDateTime? = null,
  val libraryAddToken: String? = null,
  val libraryRemoveToken: String? = null,
  val suggestedBy: String? = null,
  val source: QueueItemSource = QueueItemSource.USER,
) : Serializable {
  val isVideoSong: Boolean
    get() = musicVideoType != null &&
      musicVideoType != "MUSIC_VIDEO_TYPE_ATV"

  data class Artist(val id: String?, val name: String) : Serializable
  data class Album(val id: String, val title: String) : Serializable
}
```

`duration` uses `-1` for unknown. `isVideoSong` is false for a plain audio track
(`MUSIC_VIDEO_TYPE_ATV`) and for unknown types. `artists` is never null; may be empty.

### 7.2 `:core` — queue persistence models

```kotlin
data class PersistQueue(
  val title: String?,
  val items: List<MediaMetadata>,
  val mediaItemIndex: Int,
  val position: Long,
  val queueType: QueueType = QueueType.LIST,
  val queueData: QueueData? = null,
  val shuffledIndices: List<Int>? = null,
) : Serializable

sealed class QueueType : Serializable {
  object LIST : QueueType()
  object YOUTUBE : QueueType()
  object YOUTUBE_ALBUM_RADIO : QueueType()
  object LOCAL_ALBUM_RADIO : QueueType()
}

sealed class QueueData : Serializable {
  data class YouTubeData(
    val endpoint: String,
    val continuation: String? = null,
  ) : QueueData()
  data class YouTubeAlbumRadioData(
    val playlistId: String,
    val albumSongCount: Int = 0,
    val continuation: String? = null,
    val firstTimeLoaded: Boolean = false,
  ) : QueueData()
  data class LocalAlbumRadioData(
    val albumId: String,
    val startIndex: Int = 0,
    val playlistId: String? = null,
    val continuation: String? = null,
    val firstTimeLoaded: Boolean = false,
  ) : QueueData()
}

data class PersistPlayerState(
  val playWhenReady: Boolean,
  val repeatMode: Int,
  val shuffleModeEnabled: Boolean,
  val volume: Float,
  val currentPosition: Long,
  val currentMediaItemIndex: Int,
  val playbackState: Int,
  val timestamp: Long = System.currentTimeMillis(),
) : Serializable
```

These classes are Java-serialized to disk by `:app`. Field names, order, types, nested
class binary names and default values are part of the on-disk format. Do not add,
remove, reorder or rename anything; changing any of them changes the implicit
`serialVersionUID` and silently breaks queue restore after an app upgrade.

### 7.3 `:core` — `MediaItem` bridges

```kotlin
val MediaItem.metadata: MediaMetadata?

fun MediaMetadata.toMediaItem(): MediaItem
fun Song.toMediaItem(): MediaItem
fun SongItem.toMediaItem(): MediaItem
```

`MediaItem.metadata` behaviour required by the queue filters:

- Prefer `localConfiguration?.tag` cast to `MediaMetadata`.
- Otherwise build a fallback from Media3 `mediaMetadata`: `id = mediaId`,
  `title = title ?: displayTitle ?: ""`, one artist from `artist` (id null), duration
  0, thumbnail from `artworkUri` or the `"artwork_uri"` extra. Return null only when
  both the resulting title and `mediaId` are blank.
- The fallback treats `explicit` as false and `musicVideoType` as null.

`toMediaItem` must set `mediaId`, a resolvable seed URI, `customCacheKey` equal to the
id, the local configuration tag (so `metadata` returns the same object), and standard
Media3 metadata. It is used by every queue and by restore.

### 7.4 `:core` — database rows used by `LocalAlbumRadio` / `LocalMixQueue`

```kotlin
data class AlbumWithSongs(
  val album: AlbumEntity,
  val artists: List<ArtistEntity>,
  val songs: List<Song>,
)

data class PlaylistSong(
  val map: PlaylistSongMap,   // map.songId: String, map.playlistId: String, map.position: Int
  val song: Song,
)

// Delegate of MusicDatabase (both methods are also available on the facade):
fun playlistSongs(playlistId: String): Flow<List<PlaylistSong>>
fun relatedSongs(songId: String): List<Song>
```

`Song` exposes at least `id`, `title`, `duration`, `thumbnailUrl`, `album: AlbumEntity?`,
`artists: List<ArtistEntity>`, `explicit`, `isVideo` (via `song` entity fields).
`AlbumEntity` exposes `id`, `title`. `AlbumWithSongs.songs` is already in album order.
`MusicDatabase` is a class implemented as a Room DAO delegate; `:playback` only calls
the two methods above and `.first()` on the returned flow.

### 7.5 `:innertube` — used surface

```kotlin
object YouTube {
  suspend fun next(endpoint: WatchEndpoint, continuation: String? = null): Result<NextResult>
  suspend fun album(browseId: String, withSongs: Boolean = true): Result<AlbumPage>
  suspend fun albumSongs(playlistId: String, album: AlbumItem? = null): Result<List<SongItem>>
  suspend fun playlist(playlistId: String): Result<PlaylistPage>
  suspend fun playlistContinuation(continuation: String): Result<PlaylistContinuationPage>
  suspend fun related(endpoint: BrowseEndpoint): Result<RelatedPage>
  suspend fun search(query: String, filter: SearchFilter, setLogin: Boolean? = null): Result<SearchResult>
  object SearchFilter { val FILTER_SONG: SearchFilter }
}

data class WatchEndpoint(
  val videoId: String? = null,
  val playlistId: String? = null,
  val playlistSetVideoId: String? = null,
  val params: String? = null,
  val index: Int? = null,
  val watchEndpointMusicSupportedConfigs: WatchEndpointMusicSupportedConfigs? = null,
) : Endpoint()

data class SongItem(
  val id: String,
  val title: String,
  val artists: List<Artist>,          // id: String?, name: String
  val album: Album? = null,           // name: String, id: String
  val duration: Int? = null,
  val musicVideoType: String? = null,
  val thumbnail: String,
  val explicit: Boolean = false,
  // remaining fields optional and unused by :playback
) {
  val isVideoSong: Boolean
  val isPodcastEpisode: Boolean
}

data class NextResult(
  val title: String? = null,
  val items: List<SongItem>,
  val currentIndex: Int? = null,
  val lyricsEndpoint: BrowseEndpoint? = null,
  val relatedEndpoint: BrowseEndpoint? = null,
  val continuation: String?,
  val endpoint: WatchEndpoint,
)

data class AlbumPage(val album: AlbumItem, val songs: List<SongItem>, /* ... */)
data class PlaylistPage(
  val playlist: PlaylistItem,   // title: String
  val songs: List<SongItem>,
  val songsContinuation: String?,
  val continuation: String?,
  // ...
)
data class PlaylistContinuationPage(val songs: List<SongItem>, val continuation: String?)
data class RelatedPage(val songs: List<SongItem>, /* albums, artists, playlists */)
data class SearchResult(val items: List<YTItem>, val continuation: String? = null)

object PodcastPage {
  fun isShowBrowseId(browseId: String?): Boolean
}
```

Constants required from
`com.revenge.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion`:
`MUSIC_VIDEO_TYPE_ATV`, `MUSIC_VIDEO_TYPE_PODCAST_EPISODE`. The podcast value is
`"MUSIC_VIDEO_TYPE_PODCAST_EPISODE"`; `PodcastPage.isShowBrowseId` is true for ids
starting with the podcast browse prefix.

All `YouTube` calls return `Result<T>` and never throw for transport/API failures;
`.getOrThrow()` / `.getOrNull()` are used as specified above. `CancellationException`
must still propagate through `runCatching` per Kotlin semantics (do not swallow it).

### 7.6 `:usbaudio`

`RoutingAudioSink` accepts any `androidx.media3.exoplayer.audio.AudioSink`, including
`com.revenge.usbaudio.UsbDacAudioSink`. `:playback` needs no member of `:usbaudio`
at compile time except that `api(project(":usbaudio"))` exposes the sink to `:app`.

---

## 8. Consumer contract in `:app` (must remain compilable)

This section records how downstream code uses the module. It is not a target-file
list for `:playback`, but the API above was shaped by these call sites.

- **Queue playback**
  - `playQueue(queue: Queue)` stores the queue and generation, optionally plays
    `queue.preloadItem?.toMediaItem()` immediately, then calls
    `queue.getInitialStatus()` on `Dispatchers.IO`.
  - The status is passed through `filterExplicit(pref)` and `filterVideoSongs(pref)`
    before use; the queue itself does no preference filtering.
  - A non-null status title becomes the queue title. Empty status items are ignored
    (the preload item keeps playing).
  - When the preload item was used, page items are inserted around
    `mediaItemIndex.coerceIn(0, size-1)` without replacing the preload item.
  - Load-more: when five or fewer items remain, `currentQueue.hasNextPage()` is true,
    auto-load is enabled, and no page is already in flight, the service calls
    `nextPage()` on IO, filters, and appends if the generation and queue are unchanged.
    `YouTubePlaylistQueue.hasPendingSongs()` and the equivalent method on the app's
    Spotify queue force pending local pages even when auto-load is off.
  - Queue replacement increments a generation; stale pages must be discarded. The
    `Queue` interface exposes no cancellation method, so callers rely on coroutine
    cancellation and identity checks.
- **Sleep timer**: service creates `SleepTimer(scope, player)`, sets
  `sleepTimer.player = player` on player replacement, calls `start(-1)` / `start(n)`,
  `clear()`, and `notifySongTransition()` after a track transition. UI observes
  `triggerTime`, `pauseWhenSongEnd`, `isActive` as Compose state.
- **Cutoff guard**: service constructs `PlaybackCutoffGuard()`, calls `onTrackChanged`
  on media item transition, and on `STATE_ENDED` calls `verifyTrackCompletion` with
  the player position, the canonical metadata duration and the player duration. A
  recovery decision seeks back to `resumePositionMs` and resumes; `AllowEnd` proceeds
  normally. `PlayerConnection` uses `computeEffectiveDuration` for display.
- **Task removal**: service calls `shouldStopPlaybackOnTaskRemoval(pref, playWhenReady,
  playbackState)` and stops only on true.
- **Relative seek**: player UI calls `seekButtonTarget(currentPosition, duration,
  offset)` for all seek-back/seek-forward buttons, including cast positions/durations.
- **Silence skip**: service constructs
  `SilenceDetectorAudioProcessor { handleLongSilenceDetected() }`, adds it to the
  DefaultAudioSink processor chain, toggles `instantModeEnabled`, calls
  `resetTracking()` on track change, and reads `isCurrentlySilent()`.
- **Audio routing**: service builds the default sink and `UsbDacAudioSink`, then calls
  `AudioSinkSelector.createRoutingSink(defaultAudioSink, usbDacAudioSink,
  isBitPerfectActive)`. The provider must be cheap and side-effect free.
- **Chunked data source**: the download/streaming chain wraps a cache/HTTP factory with
  `ChunkingDataSourceFactory(factory)` (default 5 MiB chunks).
- **Beat analysis**: service calls `analyzeUri` for local ids and `analyzeStream` for
  streamed ids, persists `BeatAnalyzer.Result` into `BeatInfoEntity`, and consumes
  `mixInPointMs` / `mixOutPointMs` / `firstBeatOffsetMs` / `bpm` / `keyPitchClass` /
  `keyIsMinor` / `confidence`. A null result with `complete == false` must be retried
  later; a null result with `complete == true` is cached as `bpm = 0`.
- **Crossfade**: service owns the driving clock and calls
  `onQueueOrTrackChanged`, `onPlaybackPositionTick`, `triggerCrossfadeNow` and
  `cancelCrossfade`; it supplies an `ExoPlayer` factory for the standby player and
  sets `onCrossfadeSwapped` to swap players. EqualPowerCurve may also be called
  directly by the app's automix fade loop.
- **Service-level transport** (audio focus, media session, Bluetooth/headset and
  notification controls, gapless, shuffle/repeat, speed) is Media3 `ExoPlayer` /
  `MediaSession` state in `:app`. This module must not introduce a second player state
  authority: queues return `MediaItem` windows only, `SleepTimer` mutates the supplied
  `Player` only through `volume`/`pause`, and the crossfade scheduler only touches the
  players it is given.

---

## 9. Persistence and database compatibility

`:playback` owns no Room database, no DAO and no migration. It must not create one.
The tables it indirectly feeds are owned by `:core`:

- `beat_info` (`@Entity(tableName = "beat_info")`), primary key `songId TEXT`,
  columns: `songId`, `bpm REAL`, `firstBeatOffsetMs INTEGER`, `confidence REAL`,
  `analyzedAt INTEGER` (default current time), `mixInPointMs INTEGER NULL`,
  `mixOutPointMs INTEGER NULL`, `keyPitchClass INTEGER NULL`, `keyIsMinor INTEGER NULL`.
  DAO methods: `@Upsert fun upsert(beatInfo: BeatInfoEntity)`,
  `fun beatInfo(songId: String): BeatInfoEntity?`. `:app` stores a scanned-but-absent
  mix point as `-1`; the analyzer itself returns null. Preserve these column names and
  nullability so existing rows remain readable. Migration ids remain the `:core`
  contract (Room v48); this module adds none.
- Queue/player restore uses Java serialization of `PersistQueue`,
  `QueueType`/`QueueData` and `PersistPlayerState` as described in Section 7.2. A
  clean-room implementation must round-trip a file written by the previous build and
  must not change the implicit serial version. If serialization is replaced in a later
  milestone, it needs an explicit versioned envelope and a migration from the Java
  stream format.
- `ChunkingDataSource` is cache-agnostic. Cache keys, `SimpleCache` directories and
  download metadata are owned by `:app`/`:core`; the wrapper may not reorder, skip or
  duplicate bytes, because cached downloads are keyed by byte range.

---

## 10. Target files the implementer must write

Main sources (target package root `com.revenge.playback`; directories mirror packages):

- `playback/src/main/kotlin/com/revenge/playback/AudioSinkSelector.kt` — object with
  `selectSink` / `createRoutingSink`.
- `playback/src/main/kotlin/com/revenge/playback/RoutingAudioSink.kt` — dual-sink
  `AudioSink` with runtime reselection and state replay.
- `playback/src/main/kotlin/com/revenge/playback/ChunkingDataSource.kt` — chunked,
  retrying `DataSource` plus factory.
- `playback/src/main/kotlin/com/revenge/playback/PlaybackCutoffGuard.kt` —
  `CutoffDecision`, guard and duration helper.
- `playback/src/main/kotlin/com/revenge/playback/PlaybackTaskRemoval.kt` — task-removal
  policy function.
- `playback/src/main/kotlin/com/revenge/playback/SeekButtonTarget.kt` — clamped
  relative seek.
- `playback/src/main/kotlin/com/revenge/playback/SleepTimer.kt` — Compose-state sleep
  timer with fade.
- `playback/src/main/kotlin/com/revenge/playback/QueueExt.kt` — queue snapshot and
  restore mapping.
- `playback/src/main/kotlin/com/revenge/playback/queues/Queue.kt` — interface, `Status`,
  explicit/video filters.
- `playback/src/main/kotlin/com/revenge/playback/queues/EmptyQueue.kt`.
- `playback/src/main/kotlin/com/revenge/playback/queues/ListQueue.kt`.
- `playback/src/main/kotlin/com/revenge/playback/queues/YouTubeQueue.kt` — watch/radio
  queue, addon resolution, dedupe, retries.
- `playback/src/main/kotlin/com/revenge/playback/queues/YouTubeAlbumRadio.kt`.
- `playback/src/main/kotlin/com/revenge/playback/queues/LocalAlbumRadio.kt`.
- `playback/src/main/kotlin/com/revenge/playback/queues/YouTubePlaylistQueue.kt` —
  windowed playlist with pending local pages and continuation retries.
- `playback/src/main/kotlin/com/revenge/playback/queues/LocalMixQueue.kt`.
- `playback/src/main/kotlin/com/revenge/playback/queues/RadioQueuePageFilter.kt`
  (internal).
- `playback/src/main/kotlin/com/revenge/playback/crossfade/AdaptivePreArmManager.kt`.
- `playback/src/main/kotlin/com/revenge/playback/crossfade/EqualPowerCurve.kt`.
- `playback/src/main/kotlin/com/revenge/playback/crossfade/StandbyCrossfadeScheduler.kt`
  — `ArmedStatus` and the interface.
- `playback/src/main/kotlin/com/revenge/playback/crossfade/StandbyCrossfadeSchedulerImpl.kt`.
- `playback/src/main/kotlin/com/revenge/playback/audio/SilenceDetectorAudioProcessor.kt`.
- `playback/src/main/kotlin/com/revenge/playback/audio/BeatAnalyzer.kt`.
- `playback/src/main/AndroidManifest.xml` — empty manifest (no components, no
  permissions declared by this module; the service component belongs to `:app`).

Tests (JVM unit tests unless an Android device is explicitly required):

- `playback/src/test/kotlin/com/revenge/playback/ChunkingDataSourceTest.kt`.
- `playback/src/test/kotlin/com/revenge/playback/PlaybackCutoffGuardTest.kt`.
- `playback/src/test/kotlin/com/revenge/playback/PlaybackTaskRemovalTest.kt`.
- `playback/src/test/kotlin/com/revenge/playback/SeekButtonTargetTest.kt`.
- `playback/src/test/kotlin/com/revenge/playback/SleepTimerTest.kt`.
- `playback/src/test/kotlin/com/revenge/playback/QueueExtTest.kt`.
- `playback/src/test/kotlin/com/revenge/playback/queues/QueueFilterSelectedTrackTest.kt`.
- `playback/src/test/kotlin/com/revenge/playback/queues/RadioQueuePageFilterTest.kt`.
- `playback/src/test/kotlin/com/revenge/playback/queues/ListQueueTest.kt`.
- `playback/src/test/kotlin/com/revenge/playback/queues/YouTubePlaylistQueueTest.kt`.
- `playback/src/test/kotlin/com/revenge/playback/crossfade/EqualPowerCurveTest.kt`.
- `playback/src/test/kotlin/com/revenge/playback/crossfade/AdaptivePreArmManagerTest.kt`.
- `playback/src/test/kotlin/com/revenge/playback/audio/SilenceDetectorAudioProcessorTest.kt`.
- `playback/src/test/kotlin/com/revenge/playback/audio/BeatAnalyzerContractTest.kt`
  (JVM-testable parts: cancellation and temp-file cleanup; media decoding cases are
  instrumented).
- `playback/src/test/java/android/net/Uri.java` — optional **test-only** JVM stand-in
  for `android.net.Uri` required by Media3 builders under plain JUnit. If the project
  adopts Robolectric, delete this file and use the real framework class. A class in
  `android.*` must never appear in `src/main` or in any APK.

Build file:

- `playback/build.gradle.kts` — keep the module path, Android namespace
  `com.revenge.playback`, minSdk 26, compileSdk 37, Java 21, desugaring, Hilt/KSP,
  Media3 1.7.x, project dependencies as declared. Add
  `testImplementation(libs.kotlinx.coroutines.test)` and (only if needed for the
  temp-file/queue tests) Mockito or hand-written fakes; do not add a production
  dependency for tests.

---

## 11. Acceptance tests

All tests must be deterministic. Use injected fakes or already-loaded lists; no real
network, no wall-clock sleeps except inside `runTest` virtual time.

### 11.1 `ChunkingDataSourceTest`

C1. **Reassembly.** Upstream body 97 bytes, chunk size 16, known length. Every byte
is read exactly once in order; total 97 bytes; end-of-input returned after.
C2. **Known length smaller than chunks.** 10-byte body, chunk size 16, length 10:
the wrapper requests at most the remaining length and never reads past the body.
C3. **Unknown length.** Length `C.LENGTH_UNSET`: chunks are `chunkSize`; end comes
from upstream; `open` returns -1; reads work.
C4. **Premature drop recovery.** 100-byte body drops once at 55; the source reopens
at the current position and returns all 100 bytes.
C5. **Persistent short stream.** 100-byte expected, upstream stops at 40, two
retries: `IOException` is thrown (premature EOF) and no silent end.
C6. **HTTP 416 / position out of range.** Upstream throws
`InvalidResponseCodeException(416)` or `DataSourceException(POSITION_OUT_OF_RANGE)` on
reopen: read returns end-of-input, no exception.
C7. **Interrupted I/O.** Upstream throws `InterruptedIOException`: it propagates
immediately and does not retry.
C8. **Interrupted backoff.** Interrupt the sleeping test thread: the thread's
interrupt flag is set and `InterruptedIOException` is thrown.
C9. **Close.** After close, read returns end; upstream close is called once.
C10. **Factory.** Two `createDataSource()` calls yield distinct, independent sources
with the configured chunk size.

### 11.2 `PlaybackCutoffGuardTest`

K1. Shortfall within 3 s -> `AllowEnd`.
K2. Known player duration within 3 s of the position wins over a longer canonical
duration -> `AllowEnd`.
K3. Canonical duration `-1` or `0` -> `AllowEnd`.
K4. Canonical 200 s, position 150 s -> recover at 150 s, attempt 1.
K5. With `maxRetriesPerTrack = 2`: attempts 1, 2, then `AllowEnd`; counter is 2.
K6. `onTrackChanged` resets the budget.
K7. Position beyond canonical -> `AllowEnd` (negative missing).
K8. Negative position is clamped to 0 in the recovery result.
K9. `computeEffectiveDuration(245, 123)` -> 245000; `(null, 15000)` -> 15000;
`(null, -1)` -> 0; `(0, C.TIME_UNSET)` -> 0; negative canonical seconds -> fallback.

### 11.3 `PlaybackTaskRemovalTest`

T1. `(false, true, BUFFERING)` and `(false, true, READY)` -> false.
T2. `stopOnTaskClear = true` -> true for any state.
T3. `(false, false, READY)` -> true; `(false, true, IDLE)` -> true;
`(false, true, ENDED)` -> true.

### 11.4 `SeekButtonTargetTest`

S1. `(25000, 60000, +10000)` -> 35000; `(25000, 60000, -10000)` -> 15000.
S2. Clamp at 0 and at duration; `(0, 0, +10000)` -> 0.
S3. Unknown duration (`-1`) has no upper clamp; negative position clamps to 0.
S4. Overflow guard: `(Long.MAX_VALUE - 5, -1, 10000)` -> `Long.MAX_VALUE`.
S5. Offset 0 returns the clamped position.

### 11.5 `QueueFilterSelectedTrackTest`

Q1. Explicit filter keeps the selected explicit item and drops other explicit items;
the index follows the selected id.
Q2. Video filter keeps the selected video and drops other videos.
Q3. Podcast episode (`MUSIC_VIDEO_TYPE_PODCAST_EPISODE`) is never dropped by the video
filter, even when not selected.
Q4. Duplicate media ids `[x, a, x, b]`, selected index 2, no removals -> filtered list
unchanged and index 2 (same occurrence, not just same id).
Q5. When the selected occurrence is itself removed, the fallback index is clamped
into the filtered list (`0..max(size-1,0)`).
Q6. Empty filtered list -> index 0.
Q7. `filterExplicit(false)` and `filterVideoSongs(false)` return the same instance.
Q8. List-extension `keepMediaId` keeps an explicit/video item that is not selected.
Q9. Items whose tag is a `MediaMetadata` are classified by its `explicit` /
`isVideoSong`; items with no tag use the fallback and are kept as non-explicit,
non-video.

### 11.6 `RadioQueuePageFilterTest`

R1. Duplicates within one page collapse to the first occurrence.
R2. Ids emitted by earlier pages are never repeated later.
R3. `reset` starts a new session and allows the same ids again.

### 11.7 `ListQueueTest`

L1. `ListQueue(title = null, items = emptyList())` returns status with null title,
empty items, index 0, position 0; `hasNextPage` false; `nextPage` throws
`UnsupportedOperationException`.
L2. Constructor values (including negative/out-of-range index and position) are
passed through unchanged.
L3. `EmptyQueue` returns null title, empty items, index -1, empty next page.
L4. A custom `Queue` implementation compiles with exactly the five interface members.

### 11.8 `YouTubePlaylistQueueTest` (already-loaded path, no network)

P1. 300 songs, start 150: initial items are indices 130..249 (120 items), initial
index 20, pending 50.
P2. 300 songs, start 0: initial items 0..99, index 0, pending 200.
P3. 300 songs, start 295: initial items 275..299, index 20, pending 0,
`hasNextPage` false when `initialContinuation = null`.
P4. `nextPage` returns the first 50 pending items and retains the rest;
`hasPendingSongs` toggles false after the final chunk.
P5. Snapshot filtering: after `filterExplicit`/`filterVideoSongs`, the selected song
is the same song even when earlier items are removed.
P6. With empty `initialSongs`, a fake `YouTube.playlist` returning 3 songs and a
continuation yields title/items/index and `hasNextPage` true; failure propagates.
P7. Continuation failure: after three consecutive failures the queue is exhausted and
the fourth failed attempt throws the last error; cancellation propagates unchanged.
P8. Three failures followed by a success on the fourth attempt returns the page and
resets the failure counter.

### 11.9 Queue mapping and restore (`QueueExtTest`)

M1. `YouTubeQueue` -> `QueueType.YOUTUBE` with `YouTubeData.endpoint == "youtube_queue"`.
M2. `YouTubeAlbumRadio` -> `QueueType.YOUTUBE_ALBUM_RADIO` with placeholder playlist id.
M3. `LocalAlbumRadio` -> `QueueType.LOCAL_ALBUM_RADIO` with album id placeholder and
startIndex 0.
M4. `ListQueue`, `EmptyQueue`, `LocalMixQueue` and an app-defined queue -> `LIST` with
null data.
M5. Title, items, media index and position are preserved exactly; `shuffledIndices`
survives `.copy`.
M6. `PersistQueue(...).toQueue()` yields a `ListQueue` with the same title/index/
position and one `MediaItem` per `MediaMetadata`, in order; type/data are dropped.
M7. Empty items and negative position restore without throwing.
M8. Java round-trip of `PersistQueue` / `QueueType` / `QueueData` / `PersistPlayerState`
through `ObjectOutputStream`/`ObjectInputStream` preserves equality.

### 11.10 `AdaptivePreArmManagerTest`

A1. Initial lead == base (20000).
A2. `recordPreparationDuration(9000)` -> 21500; then `12000` -> at least 25000
(exact: 26000).
A3. Four 30000 ms preparations -> capped at 35000.
A4. `10000` expands to 23000; three 1000 ms preparations drive it down by 2000 per
sample only while the history average is below 8000, never below 20000.
A5. History retains only the last five durations (a fast sample cannot contract while
older slow samples keep the average high).
A6. `reset` restores the base and clears history.
A7. Invalid constructor arguments throw `IllegalArgumentException`: base 0, threshold
0, max < base.

### 11.11 `EqualPowerCurveTest`

E1. `0f` -> (1, 0); `1f` -> (0, 1) within 1e-3.
E2. For every percent 0..100, `out^2 + in^2` is 1 within 0.005.
E3. `0.5f` -> equal gains and 0.7071 within 2e-3.
E4. `-2f`, `4f`, `NaN`, `+Infinity`, `-Infinity` clamp to safe endpoints (non-finite
treated as 0).

### 11.12 `SleepTimerTest` (fake player, `TestScope`)

Z1. `start(-1)` -> `pauseWhenSongEnd == true`, `triggerTime == -1`, `isActive`.
Z2. `start(1)` -> `pauseWhenSongEnd == false`, `triggerTime` is between `now + 60_000`
and `now + 61_000`; `isActive`.
Z3. `clear()` -> inactive, timer job cancelled; advancing virtual time produces no
fade/pause.
Z4. `notifySongTransition()` before any `start` is a no-op.
Z5. In song-end mode, one notification triggers one fade; a second notification before
the flag is re-armed is a no-op.
Z6. `onPlaybackStateChanged(STATE_ENDED)` in song-end mode triggers the fade.
Z7. The fade performs the configured steps, calls `pause()` exactly once and restores
the original volume. (A minimal `Player` test double may implement only volume,
pause and the listener methods; unsupported calls should fail loudly.)
Z8. `start(n)` twice replaces the first countdown (only one fade after virtual time).

### 11.13 `SilenceDetectorAudioProcessorTest`

D1. PCM 16-bit stereo configure returns the same format.
D2. Any non-16-bit encoding throws `UnhandledAudioFormatException`.
D3. With instant mode off, a long silent buffer produces an identical output buffer
and no callback.
D4. With instant mode on, after `minSilenceDurationUs` of silence, exactly one
callback fires for the run; additional silent buffers do not fire again.
D5. A loud frame resets the run; a later long silence fires a second callback.
D6. Silence threshold is strict: a peak equal to `silenceThreshold` is not silent;
`silenceThreshold - 1` is.
D7. Peak is per-frame across channels: one loud channel makes the frame non-silent.
D8. `flush()`/`resetTracking()` resets the run and allows a new callback.
D9. Byte-for-byte output equals input in every mode.
D10. Zero-remaining input yields an empty output; `isEnded` is false until
`queueEndOfStream` and no pending output.
D11. Toggling instant mode off mid-silence stops scanning and resets the state.

### 11.14 `BeatAnalyzerContractTest` and instrumented analysis

B1. `analyzeStream` with `shouldCancel = { true }` returns null and leaves no
`beat_*.tmp` file in the temp directory.
B2. A source returning fewer than 256 KiB yields null.
B3. A source returning end-of-input after 1 MiB yields `CachedAnalysis(_, complete =
true)`; a source returning exactly 5 MiB without end yields `complete = false`.
B4. A source that throws after 512 KiB still analyses the partial file and returns a
`CachedAnalysis`; if the partial is below 256 KiB, returns null.
B5. A source whose `open` throws returns null; the temp file is always deleted.
B6. Video-only or missing audio track (`analyzeUri`, instrumented) returns null; no
exception escapes.
B7. Instrumented synthetic track: a click track at 120 BPM yields `bpm` in
`[118, 122]`, `firstBeatOffsetMs` in `[0, 500)`, `confidence` in `[0, 1]`;
`mixInPointMs`/`mixOutPointMs`, when non-null, are within the track and
`mixOutPointMs >= durationMs - 45_000`.
B8. A pure-DC or silent buffer produces no result (no BPM/key), not a bogus 60 BPM.
B9. `keyPitchClass`, when non-null, is in `0..11`; `keyIsMinor` is non-null exactly
when `keyPitchClass` is non-null.

### 11.15 `StandbyCrossfadeSchedulerImpl` (instrumented or Robolectric)

X1. New scheduler: `isCrossfading == false`, `armedState == Idle`.
X2. `onQueueOrTrackChanged` with a different target while `Idle` leaves state `Idle`
and does not throw.
X3. A tick with no next item or non-positive fade duration does nothing.
X4. A track shorter than the fade does not arm.
X5. `cancelCrossfade` and `release` are idempotent and safe before any target.
X6. With a factory returning a pre-READY standby player, a tick inside the adaptive
lead arms it (`Armed`), and a subsequent tick inside the fade starts `Fading`, then
reaches `Idle` and calls `onCrossfadeSwapped` exactly once.
X7. `triggerCrossfadeNow` does nothing while `Preparing`.

### 11.16 Integration smoke tests (`:app`, not part of this module's test source set)

I1. Queue state survives process death: save a queue with
`toPersistQueue`, serialize, restore with `toQueue`, and the player can start at the
saved index/position with the same item ids.
I2. Cutoff recovery: a mocked player reporting `STATE_ENDED` 30 s early seeks back and
resumes at most twice, then advances.
I3. Audio focus: loss of focus pauses or ducks according to Media3 attributes; regain
resumes only if playback was playing before the loss (service-level, Media3 contract).
I4. USB fallback: with the DAC disconnected, `RoutingAudioSink` stays on the default
sink; connecting arms the USB sink on the next `reselect`/`configure`; disconnecting
mid-track returns to default without a second playback position discontinuity.
I5. Silence skip: a silent gap longer than the configured threshold invokes the
service callback once and playback advances; a short gap does not.
I6. Chunked download: a dropped connection resumes from the same byte offset and the
completed file hash equals the full-body hash.

---

## 12. Non-goals

- Do not implement `MusicService`, `MediaLibraryService`, `MediaSession`, audio focus,
  notifications, Bluetooth/headset button handling, shuffle/repeat/speed state,
  gapless configuration, download cache management, Room entities/DAOs/migrations, or
  the app-level automix loop in this module. Those are `:app` or `:core` targets; this
  module supplies only the primitives listed above.
- No extra public API is required. Adding a helper is allowed only if every signature
  in Sections 3–6 remains source- and binary-compatible (names, parameter names,
  defaults, nullability, return types).
- No Compose UI, no Hilt bindings, no network client of its own. Queue implementations
  call the shared `YouTube` object; they do not create HTTP clients.
- Do not read or copy `../Revenge-source` or `../Echo-Music`; this document is the
  behavioural reference.
