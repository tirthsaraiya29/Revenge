# Clean-Room Specification — `:core` cluster `core-models-utils`

> **Required behaviour only.** Any algorithm shape is illustrative; implementers must design
> independently and are audited for baseline similarity.

Status: M2 specification (behavioural contract only; no implementation bodies).
Module: `:core` — directory `core`, Gradle path `:core`.
Cluster scope: `models`, `constants` (DataStore preference keys), `extensions`, `utils`, `ui`, `di`, `ai`, and `com.revenge.core` (domain mapping + repository implementations).
Clean-room packages (must be preserved exactly; downstream modules import them verbatim):
`com.revenge.music.models`, `com.revenge.music.models.lastfm`, `com.revenge.music.constants`,
`com.revenge.music.extensions`, `com.revenge.music.utils`, `com.revenge.music.ui.utils`,
`com.revenge.music.ui.screens`, `com.revenge.music.di`, `com.revenge.music.ai`,
`com.revenge.core.mapping`, `com.revenge.core.repository`.

Module build contract (`core/build.gradle.kts`, must remain):

- Plugins: `com.android.library`, `com.google.devtools.ksp`, `kotlin-serialization`.
- Namespace `com.revenge.core`; `compileSdk 37`; `minSdk 26`; Java/Kotlin 21; core-library desugaring on.
- `api` dependencies (they are part of this module's exposed surface): DataStore preferences, Compose
  runtime/ui/animation, Room runtime+ktx, Timber, Ktor client core + serialization-json, Apache
  commons-lang3, protobuf-javalite, Media3 common+session, `javax.inject:javax.inject:1`,
  core-ktx, and project modules `:innertube`, `:metadata`, `:domain`.
- `ksp(libs.room.compiler)`; `room.schemaLocation = "$projectDir/schemas"`.
- Test dependencies: JUnit 4, `kotlinx-coroutines-test`, Robolectric.

This document describes observable behaviour and API shape. Any implementation satisfying the
contracts and acceptance tests is acceptable. Control flow, helper decomposition, container choices
and loop structure are not specified; where a description names a concrete mechanism it is
illustrative, not required.

---

## 1. Module purpose

`:core` owns the application-wide shared, non-UI, non-service layer:

- Immutable transport/serialisation models shared by `:app`, `:playback`, `:lyrics` and `:core`.
- `PreferenceKeys`: the single source of truth for every DataStore preference name and type. Every
  module reads/writes settings exclusively through these `Preferences.Key` values.
- Extension functions over Media3 `Player`/`MediaItem`, collections, strings, files, and Android
  `Context`.
- Low-level utilities: DataStore accessors, connectivity observation, playback diagnostics, crash
  reporting hook.
- Thumbnail URL rewriting (`resize`), DI qualifiers, AI action log.
- Adapters from Room entities to `:domain` models and the `:domain` repository implementations.

The cluster does not own the Room schema (see `core-db` cluster) but consumes a small, fixed slice of
it; §11.2 reproduces exactly the shape required to compile against it.

Concurrency note: none of the model/data classes are thread-safe mutable state. `PlaybackLogManager`
is a process-wide singleton backed by a `StateFlow`. `NetworkConnectivityObserver` is callback-driven
and safe to construct on any thread; `unregister()` must be called by the owner. Functions that
suspend (`AiLogger.log`, DataStore access) inherit the caller's context except where stated.

---

## 2. Package map and required declarations

The binding contract is the package names (imported verbatim by downstream modules) and the public
declarations described below. File names, and how declarations are split across files, are indicative
only; implementations may organise them differently.

Main sources live under `core/src/main/kotlin/` and must be in the listed packages:

| Package | Required public declarations |
|---|---|
| `com.revenge.music.models` | `MediaMetadata` (nested `Artist`/`Album`, `isVideoSong`), `QueueItemSource`, `AccountData` (`@Serializable`), `ItemsPage`, `SimilarRecommendation`, `SponsorBlockSegment` (`@Serializable`), `PersistPlayerState`, `PersistQueue` (+ `QueueType`/`QueueData`), and the `MediaMetadata` ⇄ `SongEntity`/`Song`/`SongItem` mappers. |
| `com.revenge.music.models.lastfm` | `Authentication` (+ nested `Session`), `TokenResponse`, `LastFmError`. |
| `com.revenge.music.constants` | `PreferenceKeys` and the enums/tables it declares, `Dimensions` (content-type ints, layout `Dp` tokens, animation specs), `HistorySource`, `LibraryFilter`, `MediaSessionConstants` (action names + `SessionCommand` values). |
| `com.revenge.music.extensions` | Login/sync/connectivity predicates; `Flow` scope collectors + `SilentHandler`; `File.div` and zip-stream helpers; collection reordering/merging and entity filters; `MediaItem` construction and metadata extraction; `Player` transport/queue/track-selection helpers; `toEnum`/`toSQLiteQuery`/`toInetSocketAddress`; `tryOrNull`. |
| `com.revenge.music.utils` | `Context.dataStore`, blocking preference readers and property delegates; `exceptionReporter` hook and `reportException`; `NetworkConnectivityObserver`; `PlaybackLogManager` (+ `PlaybackLogLevel`/`PlaybackLogEntry`). |
| `com.revenge.music.ui.utils` | `String.resize`, `ytimgFallbackUrl`. |
| `com.revenge.music.ui.screens` | `OptionStats` enum. |
| `com.revenge.music.di` | Hilt `@Qualifier` annotations. |
| `com.revenge.music.ai` | `AiLogger`. |
| `com.revenge.core.mapping` | Room → `:domain` mapping extensions. |
| `com.revenge.core.repository` | `SongRepositoryImpl`, `PlaylistRepositoryImpl` (`:domain` repository implementations). |

Tests (suggested; see §13):

- `core/src/test/kotlin/com/revenge/music/extensions/CollectionExtTest.kt` (exists; keep passing).
- `core/src/test/kotlin/com/revenge/music/ui/utils/YouTubeUtilsTest.kt` (exists; keep passing).
- `core/src/test/kotlin/com/revenge/music/models/MediaMetadataMappersTest.kt`.
- `core/src/test/kotlin/com/revenge/music/models/PersistQueueTest.kt`.
- `core/src/test/kotlin/com/revenge/music/constants/PreferenceKeysTest.kt`.
- `core/src/test/kotlin/com/revenge/music/utils/DataStoreHelpersTest.kt` (Robolectric, temp DataStore).
- `core/src/test/kotlin/com/revenge/music/extensions/PlayerExtTest.kt` (fake `Player`/`Timeline` or Media3 test utils).
- `core/src/test/kotlin/com/revenge/core/mapping/DomainMappersTest.kt`.
- `core/src/test/kotlin/com/revenge/core/repository/RepositoryImplTest.kt` (in-memory fake `MusicDatabase` or Robolectric Room).

---

## 3. Models — package `com.revenge.music.models`

All Kotlin `data class` equality/hashCode is structural over declared fields; `copy` defaults to the
current value of each field. Constructors are called with named arguments throughout the codebase, so
parameter names and order are compatibility contracts. No constructor performs validation,
normalisation, trimming, or coercion unless stated.

### 3.1 `com.revenge.music.models.MediaMetadata`

Annotated `@androidx.compose.runtime.Immutable`, implements `java.io.Serializable`.

| Order | Field | Type | Default | Meaning |
|---|---|---|---|---|
| 1 | `id` | `String` | required | Stable track/video id. |
| 2 | `title` | `String` | required | Display title. |
| 3 | `artists` | `List<MediaMetadata.Artist>` | required | Ordered artists; may be empty, never null. |
| 4 | `duration` | `Int` | required | Seconds; `-1` denotes unknown. |
| 5 | `thumbnailUrl` | `String?` | `null` | Artwork URL or local URI. |
| 6 | `album` | `MediaMetadata.Album?` | `null` | Owning album. |
| 7 | `setVideoId` | `String?` | `null` | Playlist set-video identity for YouTube queues. |
| 8 | `musicVideoType` | `String?` | `null` | InnerTube video type, e.g. `"MUSIC_VIDEO_TYPE_ATV"`. |
| 9 | `explicit` | `Boolean` | `false` | Explicit flag. |
| 10 | `liked` | `Boolean` | `false` | Liked flag at snapshot time. |
| 11 | `likedDate` | `java.time.LocalDateTime?` | `null` | When liked. |
| 12 | `inLibrary` | `java.time.LocalDateTime?` | `null` | When added to library. |
| 13 | `libraryAddToken` | `String?` | `null` | Token for a library add mutation. |
| 14 | `libraryRemoveToken` | `String?` | `null` | Token for a library remove mutation. |
| 15 | `suggestedBy` | `String?` | `null` | Provenance label for recommendations. |
| 16 | `source` | `QueueItemSource` | `QueueItemSource.USER` | Queue item origin. |

Computed property `val isVideoSong: Boolean`: `true` iff `musicVideoType != null` **and**
`musicVideoType != MUSIC_VIDEO_TYPE_ATV` (constant value `"MUSIC_VIDEO_TYPE_ATV"`). `null` video type
is an audio track. Podcast episodes (`MUSIC_VIDEO_TYPE_PODCAST_EPISODE`) are video-type by this rule.

Nested `data class MediaMetadata.Artist(val id: String?, val name: String) : Serializable` — `id` is
nullable because locally derived artists have no id.

Nested `data class MediaMetadata.Album(val id: String, val title: String) : Serializable`.

### 3.2 `com.revenge.music.models.QueueItemSource`

`enum class QueueItemSource { USER }`. Only `USER` exists; callers reference it as a default. Do not
add constants without checking serialised/deserialised compatibility.

### 3.3 `com.revenge.music.models.AccountData`

`@kotlinx.serialization.Serializable` data class, persisted as JSON under `SavedAccountsKey`.

| Order | Field | Type | Default |
|---|---|---|---|
| 1 | `name` | `String` | required |
| 2 | `email` | `String` | required |
| 3 | `channelHandle` | `String` | required |
| 4 | `cookie` | `String` | required |
| 5 | `visitorData` | `String` | required |
| 6 | `dataSyncId` | `String` | required |
| 7 | `avatarUrl` | `String` | `""` |

`avatarUrl` must be optional in JSON (missing field decodes to `""`). Unknown JSON fields must be
ignored when decoding a list of accounts (forward compatibility); implementations should configure
the JSON parser used by callers, not this class — callers construct their own `Json`.

### 3.4 `com.revenge.music.models.ItemsPage`

```kotlin
data class ItemsPage(val items: List<YTItem>, val continuation: String?)
```

`items` never null; `continuation == null` means "no further pages". No de-duplication is performed
here; callers de-duplicate by `id`.

### 3.5 `com.revenge.music.models.SimilarRecommendation`

```kotlin
data class SimilarRecommendation(val title: LocalItem, val items: List<YTItem>)
```

`title` is the local entity the recommendations were derived from; `items` may be empty.

### 3.6 `com.revenge.music.models.SponsorBlockSegment`

`@Serializable` data class with exact field names (JSON is the public SponsorBlock API):

| Field | Type |
|---|---|
| `segment` | `List<Float>` |
| `UUID` | `String` |
| `category` | `String` |
| `actionType` | `String` |
| `locked` | `Int` |
| `votes` | `Int` |
| `description` | `String` |

All required. `segment` is `[startSeconds, endSeconds]` in video time; values may be fractional and
the list may be malformed from a hostile server — consumers validate length, not this model.

### 3.7 `com.revenge.music.models.PersistPlayerState`

Implements `java.io.Serializable`.

```kotlin
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

`repeatMode` is a Media3 `Player.REPEAT_MODE_*` int; `playbackState` a `Player.STATE_*` int. `timestamp`
defaults to wall-clock millis at construction. The class carries no validation; restore logic clamps
volume in the playback layer.

### 3.8 `com.revenge.music.models.PersistQueue`

Implements `java.io.Serializable`. Written/read with `ObjectOutputStream`/`ObjectInputStream`.

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
```

- `items` may be empty; no validation.
- `mediaItemIndex` may be out of range for `items`; the restore path must tolerate that (see acceptance
  tests).
- `shuffledIndices == null` means shuffle order was not persisted.

`sealed class QueueType : Serializable` with exactly one `object` per case and no fields:
`LIST`, `YOUTUBE`, `YOUTUBE_ALBUM_RADIO`, `LOCAL_ALBUM_RADIO`.

`sealed class QueueData : Serializable` with exactly these data subclasses:

```kotlin
data class YouTubeData(val endpoint: String, val continuation: String? = null) : QueueData()
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
```

All subclass names and parameter names are part of the Java-serialisation surface; changing them
breaks restore.

### 3.9 `com.revenge.music.models.lastfm`

```kotlin
@Serializable data class Authentication(val session: Authentication.Session) {
  @Serializable data class Session(val name: String, val key: String, val subscriber: Int)
}
@Serializable data class TokenResponse(val token: String)
@Serializable data class LastFmError(val error: Int, val message: String)
```

All fields required. `subscriber` is Last.fm's 0/1 integer. `LastFmError` must decode even when the
server returns additional fields.

---

## 4. Constants — package `com.revenge.music.constants`

### 4.1 Preference keys — `PreferenceKeys.kt` (single source of truth)

Every entry below is a top-level `val` of type `androidx.datastore.preferences.core.Preferences.Key<T>`.
The **identifier** and the **stored key string** are both compatibility contracts: the string is the
on-disk name and must never change. Types are `Boolean` (B), `String` (S), `Int` (I), `Long` (L),
`Float` (F), `Set<String>` (SS). Missing keys are read through callers' defaults; no default is stored
in the key object itself.

Each key's public type must be `Preferences.Key<T>`; how keys are constructed is unconstrained.
`PreferenceKeys` also declares the enums and tables in §4.2 and §4.4.

Account / Spotify / app icon

| Identifier | Type | Stored key |
|---|---|---|
| `DataSaverEnabledKey` | B | `dataSaverEnabled` |
| `SpotifySpDcKey` | S | `spotify_sp_dc` |
| `SpotifySpKeyKey` | S | `spotify_sp_key` |
| `SpotifyAccountNameKey` | S | `spotify_account_name` |
| `SpotifyAccountAvatarUrlKey` | S | `spotify_account_avatar_url` |
| `SpotifyAccessTokenKey` | S | `spotify_access_token` |
| `SpotifyAccessTokenExpiresAtKey` | L | `spotify_access_token_expires_at` |
| `EnableLegacyIconKey` | B | `enableLegacyIcon` |
| `AppIconTypeKey` | S | `app_icon_type` |

Haptics / refresh rate

| Identifier | Type | Stored key |
|---|---|---|
| `EnableHighRefreshRateKey` | B | `enableHighRefreshRate` |
| `EnableHapticsKey` | B | `enableHaptics` |
| `HapticIntensityKey` | S | `haptic_intensity` |
| `EnableClickHapticsKey` | B | `enable_click_haptics` |
| `EnableLongPressHapticsKey` | B | `enable_long_press_haptics` |
| `EnableScrollEdgeHapticsKey` | B | `enable_scroll_edge_haptics` |
| `EnableSliderHapticsKey` | B | `enable_slider_haptics` |

Appearance

| Identifier | Type | Stored key |
|---|---|---|
| `AlbumArtThemeRecoloringKey` | B | `albumArtThemeRecoloring` |
| `SelectedThemeColorKey` | I | `selectedThemeColor` |
| `DarkModeKey` | S | `darkMode` |
| `PureBlackKey` | B | `pureBlack` |
| `PureBlackMiniPlayerKey` | B | `pureBlackMiniPlayer` |
| `IsMonochromeThemeKey` | B | `is_monochrome_theme` |
| `DensityScaleKey` | F | `density_scale_factor` |
| `RevengeWrappedBannerDismissedKey` | B | `revenge_wrapped_banner_dismissed_2026` |
| `DefaultOpenTabKey` | S | `defaultOpenTab` |
| `GridItemsSizeKey` | S | `gridItemSize` |
| `SliderStyleKey` | S | `sliderStyle` |
| `SquigglySliderKey` | B | `squigglySlider` |
| `WavyPlayPauseKey` | B | `wavyPlayPause` |
| `SwipeToSongKey` | B | `SwipeToSong` |
| `SwipeToRemoveSongKey` | B | `SwipeToRemoveSong` |
| `UseNewPlayerDesignKey` | B | `useNewPlayerDesign` |
| `UseNewMiniPlayerDesignKey` | B | `useNewMiniPlayerDesign` |
| `ShowCodecOnPlayerKey` | B | `showCodecOnPlayer` |
| `HidePlayerSliderKey` | B | `hidePlayerSlider` |
| `HidePlayerThumbnailKey` | B | `hidePlayerThumbnail` |
| `ThumbnailCornerRadiusKey` | F | `thumbnailCornerRadius` |
| `CropAlbumArtKey` | B | `cropAlbumArt` |
| `SeekExtraSeconds` | B | `seekExtraSeconds` |
| `VarispeedKey` | B | `varispeed` |

Playback behaviour

| Identifier | Type | Stored key |
|---|---|---|
| `PauseOnMute` | B | `pauseOnMute` |
| `ResumeOnBluetoothConnectKey` | B | `resumeOnBluetoothConnect` |
| `AutoplayOnAutomotiveConnectKey` | B | `autoplayOnAutomotiveConnect` |
| `KeepScreenOn` | B | `keepScreenOn` |

Language / content region / lyrics providers / filters

| Identifier | Type | Stored key |
|---|---|---|
| `SYSTEM_DEFAULT` | `const String` | value `"SYSTEM_DEFAULT"` |
| `AppLanguageKey` | S | `appLanguage` |
| `ContentLanguageKey` | S | `contentLanguage` |
| `ContentCountryKey` | S | `contentCountry` |
| `SuggestionRegionKey` | S | `suggestionRegion` |
| `EnableKugouKey` | B | `enableKugou` |
| `FetchFasterLyricsKey` | B | `fetchFasterLyrics` |
| `EnableLrcLibKey` | B | `enableLrclib` |
| `EnableBetterLyricsKey` | B | `enableBetterLyrics` |
| `EnableSimpMusicKey` | B | `enableSimpMusic` |
| `EnableYouLyPlusKey` | B | `enableYouLyPlus` |
| `EnablePaxsenixKey` | B | `enablePaxsenix` |
| `HideExplicitKey` | B | `hideExplicit` |
| `SponsorBlockEnabledKey` | B | `sponsor_block_enabled` |
| `HideVideoSongsKey` | B | `hideVideoSongs` |
| `HideYoutubeShortsKey` | B | `hideYoutubeShorts` |
| `ShowArtistDescriptionKey` | B | `showArtistDescription` |
| `ShowArtistSubscriberCountKey` | B | `showArtistSubscriberCount` |
| `ShowMonthlyListenersKey` | B | `showMonthlyListeners` |
| `ShowArtistVideoKey` | B | `showArtistVideo` |
| `ShowArtistBackgroundVideoKey` | B | `showArtistBackgroundVideo` |
| `ProxyEnabledKey` | B | `proxyEnabled` |
| `ProxyUrlKey` | S | `proxyUrl` |
| `ProxyTypeKey` | S | `proxyType` |
| `ProxyUsernameKey` | S | `proxyUsername` |
| `ProxyPasswordKey` | S | `proxyPassword` |
| `YtmSyncKey` | B | `ytmSync` |

Streaming quality / downloads / audio processing

| Identifier | Type | Stored key |
|---|---|---|
| `AudioQualityWifiKey` | S | `audioQualityWifi` |
| `AudioQualityCellularKey` | S | `audioQualityCellular` |
| `IpVersionKey` | S | `ipVersion` |
| `DownloadOnWifiOnlyKey` | B | `downloadOnWifiOnly` |
| `DownloadWithMetadataKey` | B | `downloadWithMetadata` |
| `AudioOffload` | B | `enableOffload` |
| `SpatialAudioKey` | B | `spatialAudio` |
| `AudioLoudnessPresetKey` | S | `audioLoudnessPreset` |
| `PlaybackEngineKey` | S | `playbackEngine` |
| `ShowSeekButtonsKey` | B | `showSeekButtons` |
| `PersistentQueueKey` | B | `persistentQueue` |
| `PersistentShuffleAcrossQueuesKey` | B | `persistentShuffleAcrossQueues` |
| `RememberShuffleAndRepeatKey` | B | `rememberShuffleAndRepeat` |
| `ShuffleModeKey` | B | `shuffleMode` |
| `SkipSilenceKey` | B | `skipSilence` |
| `SkipSilenceInstantKey` | B | `skipSilenceInstant` |
| `AudioNormalizationKey` | B | `audioNormalization` |
| `AutoLoadMoreKey` | B | `autoLoadMore` |
| `DisableLoadMoreWhenRepeatAllKey` | B | `disableLoadMoreWhenRepeatAll` |
| `AutoDownloadOnLikeKey` | B | `autoDownloadOnLike` |
| `SimilarContent` | B | `similarContent` |
| `AutoSkipNextOnErrorKey` | B | `autoSkipNextOnError` |
| `StopMusicOnTaskClearKey` | B | `stopMusicOnTaskClear` |
| `ShufflePlaylistFirstKey` | B | `shufflePlaylistFirst` |
| `PreventDuplicateTracksInQueueKey` | B | `preventDuplicateTracksInQueue` |
| `CrossfadeEnabledKey` | B | `crossfadeEnabled` |
| `CrossfadeDurationKey` | F | `crossfadeDuration` |
| `CrossfadeGaplessKey` | B | `crossfadeGapless` |
| `AutomixCrossfadeKey` | B | `automixCrossfade` |
| `AutomixDebugOverlayKey` | B | `automixDebugOverlay` |

Cache / export / history / privacy

| Identifier | Type | Stored key |
|---|---|---|
| `MaxImageCacheSizeKey` | I | `maxImageCacheSize` |
| `MaxSongCacheSizeKey` | I | `maxSongCacheSize` |
| `ExportDirectoryUriKey` | S | `exportDirectoryUri` |
| `ExportingSongIdsKey` | S | `exportingSongIds` |
| `ExportedSongIdsKey` | S | `exportedSongIds` |
| `ExportProgressKey` | S | `exportProgress` (entries `"songId:percent"`, comma-separated) |
| `EnableExportAsMp3Key` | B | `enableExportAsMp3` |
| `PauseListenHistoryKey` | B | `pauseListenHistory` |
| `PodcastHidePlayedKey` | B | `podcastHidePlayed` |
| `PauseSearchHistoryKey` | B | `pauseSearchHistory` |
| `DisableScreenshotKey` | B | `disableScreenshot` |
| `EnableGoogleCastKey` | B | `enableGoogleCast` |

Listen Together

| Identifier | Type | Stored key |
|---|---|---|
| `ListenTogetherServerUrlKey` | S | `listenTogetherServerUrl` |
| `ListenTogetherUsernameKey` | S | `listenTogetherUsername` |
| `ListenTogetherAutoApprovalKey` | B | `listenTogetherAutoApproval` |
| `ListenTogetherSyncVolumeKey` | B | `listenTogetherSyncVolume` |
| `ListenTogetherSmartResyncKey` | B | `listenTogetherSmartResync` |
| `ListenTogetherBlockedUsersKey` | S | `listenTogetherBlockedUsers` |
| `ListenTogetherInTopBarKey` | B | `listenTogetherInTopBar` |
| `ListenTogetherSessionTokenKey` | S | `listenTogetherSessionToken` |
| `ListenTogetherRoomCodeKey` | S | `listenTogetherRoomCode` |
| `ListenTogetherUserIdKey` | S | `listenTogetherUserId` |
| `ListenTogetherIsHostKey` | B | `listenTogetherIsHost` |
| `ListenTogetherSessionTimestampKey` | L | `listenTogetherSessionTimestamp` |

Last.fm / scrobbling

| Identifier | Type | Stored key |
|---|---|---|
| `LastFMSessionKey` | S | `lastfmSession` |
| `LastFMUsernameKey` | S | `lastfmUsername` |
| `EnableLastFMScrobblingKey` | B | `lastfmScrobblingEnable` |
| `LastFMUseNowPlaying` | B | `lastfmUseNowPlaying` |
| `LastFMUseSendLikes` | B | `lastfmUseSendLikes` |
| `ScrobbleDelayPercentKey` | F | `scrobbleDelayPercent` |
| `ScrobbleMinSongDurationKey` | I | `scrobbleMinSongDuration` |
| `ScrobbleDelaySecondsKey` | I | `scrobbleDelaySeconds` |

Sort / filter / view / sync

| Identifier | Type | Stored key |
|---|---|---|
| `ChipSortTypeKey` | S | `chipSortType` |
| `SongSortTypeKey` | S | `songSortType` |
| `SongSortDescendingKey` | B | `songSortDescending` |
| `DownloadedSongSortTypeKey` | S | `downloadedSongSortType` |
| `DownloadedSongSortDescendingKey` | B | `downloadedSongSortDescending` |
| `PlaylistSongSortTypeKey` | S | `playlistSongSortType` |
| `PlaylistSongSortDescendingKey` | B | `playlistSongSortDescending` |
| `ArtistSortTypeKey` | S | `artistSortType` |
| `ArtistSortDescendingKey` | B | `artistSortDescending` |
| `AlbumSortTypeKey` | S | `albumSortType` |
| `AlbumSortDescendingKey` | B | `albumSortDescending` |
| `PlaylistSortTypeKey` | S | `playlistSortType` |
| `PlaylistSortDescendingKey` | B | `playlistSortDescending` |
| `AddToPlaylistSortTypeKey` | S | `addToPlaylistSortType` |
| `AddToPlaylistSortDescendingKey` | B | `addToPlaylistSortDescending` |
| `ArtistSongSortTypeKey` | S | `artistSongSortType` |
| `ArtistSongSortDescendingKey` | B | `artistSongSortDescending` |
| `MixSortTypeKey` | S | `mixSortType` |
| `MixSortDescendingKey` | B | **`albumSortDescending`** (deliberately shared with album sorting) |
| `LocalSongsMinDurationSecondsKey` | I | `local_songs_min_duration_seconds` |
| `LocalSongsExcludedFoldersKey` | SS | `local_songs_excluded_folders` |
| `LocalSongsIncludedFoldersKey` | SS | `local_songs_included_folders` |
| `LocalSongsFolderFilterModeKey` | S | `local_songs_folder_filter_mode` |
| `LocalSongsSortTypeKey` | S | `local_songs_sort_type` |
| `LocalSongsSortDescendingKey` | B | `local_songs_sort_descending` |
| `SongFilterKey` | S | `songFilter` |
| `ArtistFilterKey` | S | `artistFilter` |
| `AlbumFilterKey` | S | `albumFilter` |
| `LastFullSyncKey` | L | `last_full_sync` |
| `SYNC_COOLDOWN` | `const Long` | value `1800L` (`30 * 60L`; seconds — do not reinterpret as millis) |
| `ArtistViewTypeKey` | S | `artistViewType` |
| `AlbumViewTypeKey` | S | `albumViewType` |
| `PlaylistViewTypeKey` | S | `playlistViewType` |
| `PlaylistEditLockKey` | B | `playlistEditLock` |
| `QuickPicksKey` | S | **`discover`** |
| `LyricsProviderOrderKey` | S | `lyricsProviderOrder` |
| `QueueEditLockKey` | B | **`queueEditLockV2`** |
| `RandomizeHomeOrderKey` | B | `randomizeHomeOrder` |
| `AlbumCanvasEnabledKey` | B | `albumCanvasEnabled` |

Home shelves / music flow / addons

| Identifier | Type | Stored key |
|---|---|---|
| `ShowLikedPlaylistKey` | B | `show_liked_playlist` |
| `ShowDownloadedPlaylistKey` | B | `show_downloaded_playlist` |
| `ShowExportedPlaylistKey` | B | `show_exported_playlist` |
| `ShowTopPlaylistKey` | B | `show_top_playlist` |
| `ShowBottomPlaylistKey` | B | `show_bottom_playlist` |
| `ShowCachedPlaylistKey` | B | `show_cached_playlist` |
| `ShowSpeedDialKey` | B | `showSpeedDial` |
| `ShowHomeHeroCarouselKey` | B | `showHomeHeroCarousel` |
| `ShowHomeCommunityKey` | B | `showHomeCommunity` |
| `ShowHomeMoodAndGenresKey` | B | `showHomeMoodAndGenres` |
| `ShowCommentButtonKey` | B | `show_comment_button` |
| `MusicFlowEnabledKey` | B | `music_flow_enabled` |
| `MusicFlowAutoplayKey` | B | `music_flow_autoplay` |
| `MusicFlowAutoNextKey` | B | `music_flow_auto_next` |
| `MusicFlowAnimatedBackgroundKey` | B | `music_flow_animated_background` |
| `MusicFlowLyricsKey` | B | `music_flow_lyrics` |
| `MusicFlowCellularPrefetchKey` | B | `music_flow_cellular_prefetch` |
| `MusicFlowHideRecentKey` | B | `music_flow_hide_recent` |
| `PreferExtensionAudioKey` | B | `prefer_extension_audio` |
| `PreferredAudioAddonIdKey` | S | `preferred_audio_addon_id` |

Player look / lyrics / AI / translation

| Identifier | Type | Stored key |
|---|---|---|
| `TopSize` | S | `topSize` |
| `HistoryDuration` | F | `historyDuration` |
| `PlayerButtonsStyleKey` | S | `player_buttons_style` |
| `PlayerBackgroundStyleKey` | S | `playerBackgroundStyle` |
| `MiniPlayerBackgroundStyleKey` | S | `miniPlayerBackgroundStyle` |
| `ShowLyricsKey` | B | `showLyrics` |
| `ShowLyricsOnPlayerKey` | B | `showLyricsOnPlayer` |
| `SwipeLyricsKey` | B | `swipeLyrics` |
| `EnableLyricsThumbnailPlayPauseKey` | B | `enableLyricsThumbnailPlayPause` |
| `LyricsTextPositionKey` | S | `lyricsTextPosition` |
| `LyricsClickKey` | B | `lyricsClick` |
| `LyricsScrollKey` | B | `lyricsScrollKey` |
| `LyricsRomanizeJapaneseKey` | B | `lyricsRomanizeJapanese` |
| `LyricsRomanizeKoreanKey` | B | `lyricsRomanizeKorean` |
| `LyricsRomanizeChineseKey` | B | `lyricsRomanizeChinese` |
| `LyricsRomanizeRussianKey` | B | `lyricsRomanizeRussian` |
| `LyricsRomanizeUkrainianKey` | B | `lyricsRomanizeUkrainian` |
| `LyricsRomanizeSerbianKey` | B | `lyricsRomanizeSerbian` |
| `LyricsRomanizeBulgarianKey` | B | `lyricsRomanizeBulgarian` |
| `LyricsRomanizeBelarusianKey` | B | `lyricsRomanizeBelarusian` |
| `LyricsRomanizeKyrgyzKey` | B | `lyricsRomanizeKyrgyz` |
| `LyricsRomanizeMacedonianKey` | B | `lyricsRomanizeMacedonian` |
| `LyricsRomanizeHindiKey` | B | `lyricsRomanizeHindi` |
| `LyricsRomanizePunjabiKey` | B | `lyricsRomanizePunjabi` |
| `LyricsRomanizeAsMainKey` | B | `lyricsRomanizeAsMain` |
| `LyricsRomanizeCyrillicByLineKey` | B | `lyricsRomanizeCyrillicByLine` |
| `OpenRouterApiKey` | S | `openRouterApiKey` |
| `AiProviderKey` | S | `aiProvider` |
| `OpenRouterBaseUrlKey` | S | `openRouterBaseUrl` |
| `OpenRouterModelKey` | S | `openRouterModel` |
| `TranslateModeKey` | S | `translateMode` |
| `TranslateLanguageKey` | S | `translateLanguage` |
| `AutoTranslateKey` | B | `autoTranslate` |
| `AiRecommendationsKey` | B | `aiRecommendations` |
| `LastAiRecommendationUpdateDayKey` | L | `lastAiRecommendationUpdateDay` |
| `CreateFromTasteDailyKey` | B | `createFromTasteDaily` |
| `DeeplApiKey` | S | `deeplApiKey` |
| `DeeplFormalityKey` | S | `deeplFormality` |
| `LyricsGlowEffectKey` | B | `lyricsGlowEffect` |
| `AppleMusicLyricsBlurKey` | B | `appleMusicLyricsBlur` |
| `LyricsStandardBlurKey` | B | `lyricsStandardBlur` |
| `HideStatusBarOnFullscreenKey` | B | `hideStatusBarOnFullscreen` |
| `LyricsAnimationStyleKey` | S | `lyricsAnimationStyle` |
| `LyricsTextSizeKey` | F | `lyricsTextSize` |
| `LyricsLineSpacingKey` | F | `lyricsLineSpacing` |
| `PlayerVolumeKey` | F | `playerVolume` |
| `RepeatModeKey` | I | `repeatMode` |
| `SearchSourceKey` | S | `searchSource` |
| `SwipeThumbnailKey` | B | `swipeThumbnail` |
| `RotatingThumbnailKey` | B | `rotatingThumbnail` |
| `CanvasThumbnailAnimationKey` | B | `canvasThumbnailAnimation` |
| `SwipeSensitivityKey` | F | `swipeSensitivity` |

Login / session / localisation extras / liquid glass / ambient / fonts / USB

| Identifier | Type | Stored key |
|---|---|---|
| `VisitorDataKey` | S | `visitorData` |
| `DataSyncIdKey` | S | `dataSyncId` |
| `InnerTubeCookieKey` | S | `innerTubeCookie` |
| `AccountNameKey` | S | `accountName` |
| `AccountEmailKey` | S | `accountEmail` |
| `AccountChannelHandleKey` | S | `accountChannelHandle` |
| `UseLoginForBrowse` | B | `useLoginForBrowse` |
| `LastOpenedVersionCodeKey` | I | `lastOpenedVersionCode` |
| `ListenBrainzEnabledKey` | B | `listenbrainz_enabled` |
| `ListenBrainzTokenKey` | S | `listenbrainz_token` |
| `UnisonLyricsEnabledKey` | B | `unison_lyrics_enabled` |
| `ForceOpusKey` | B | `force_opus_audio` |
| `PreloadNextSongEnabledKey` | B | `preload_next_song_enabled` |
| `PreloadNextSongLimitKey` | I | `preload_next_song_limit` |
| `PreloadLyricsEnabledKey` | B | `preload_lyrics_enabled` |
| `LiquidGlassGlobalEnabledKey` | B | `liquidGlassGlobalEnabled` |
| `LiquidGlassTextColorKey` | I | `liquidGlassTextColor` |
| `LiquidGlassSurfaceTintColorKey` | I | `liquidGlassSurfaceTintColor` |
| `LiquidGlassSurfaceOpacityKey` | F | `liquidGlassSurfaceOpacity` |
| `LiquidGlassVibrancyKey` | F | `liquidGlassVibrancy` |
| `LiquidGlassBlurRadiusKey` | F | `liquidGlassBlurRadius` |
| `LiquidGlassLensHeightKey` | F | `liquidGlassLensHeight` |
| `LiquidGlassLensAmountKey` | F | `liquidGlassLensAmount` |
| `LiquidGlassChromaticAberrationKey` | B | `liquidGlassChromaticAberration` |
| `LiquidGlassDepthEffectKey` | B | `liquidGlassDepthEffect` |
| `LiquidGlassPlayerEnabledKey` | B | `liquidGlassPlayerEnabled` |
| `LiquidGlassMiniPlayerEnabledKey` | B | `liquidGlassMiniPlayerEnabled` |
| `LiquidGlassNavBarEnabledKey` | B | `liquidGlassNavBarEnabled` |
| `UseFloatingNavBarKey` | B | `useFloatingNavBar` |
| `SavedAccountsKey` | S | `savedAccounts` |
| `LiveBlurDensityKey` | F | `live_blur_density` |
| `AmbientArtScaleKey` | F | `ambient_art_scale` |
| `AmbientShowTitleKey` | B | `ambient_show_title` |
| `AmbientShowArtistKey` | B | `ambient_show_artist` |
| `AmbientShowLyricsKey` | B | `ambient_show_lyrics` |
| `AmbientShowArtKey` | B | `ambient_show_art` |
| `AmbientFullScreenArtKey` | B | `ambient_full_screen_art` |
| `AmbientSpacingKey` | F | `ambient_spacing` |
| `SelectedFontKey` | S | `selected_app_font` |
| `CustomFontPathKey` | S | `custom_font_path` |
| `BlockedArtistsKey` | SS | `blockedArtists` |
| `DislikedSongsKey` | SS | `dislikedSongs` |
| `EnableBitPerfectUsbDacKey` | B | `enable_bit_perfect_usb_dac` |
| `UsbDacVolumeModeKey` | S | `usb_dac_volume_mode` |
| `RecommendationSourceKey` | S | `recommendation_source` |

### 4.2 Enumerations and helper types declared in `PreferenceKeys.kt`

All are public and referenced by stored values; enum **constant names** are the persisted strings
unless a separate `value`/`label` is documented.

- `enum class HapticIntensity(val label: String, val scaleFactor: Float)`
  `LIGHT("Light", 0.4f)`, `MEDIUM("Medium", 0.75f)`, `STRONG("Strong", 1.0f)`.
  Companion `fromName(name: String?): HapticIntensity` — case-insensitive match on constant name;
  `null`, blank, or unknown ⇒ `MEDIUM`.
- `enum class DensityScale(val value: Float, val label: String)`
  `NATIVE(1.0f, "Native (100%)")`, `SLIGHTLY_COMPACT(0.85f, "Slightly Compact (85%)")`,
  `COMPACT(0.75f, "Compact (75%)")`, `VERY_COMPACT(0.65f, "Very Compact (65%)")`,
  `ULTRA_COMPACT(0.55f, "Ultra Compact (55%)")`. Companion `fromValue(value: Float)` — exact `Float`
  equality, otherwise `NATIVE`.
- `enum class SliderStyle { DEFAULT, WAVY, SLIM }`.
- `enum class AudioQuality(val maxKbps: Int, val label: String, val detail: String, val hourly: String)`:

  | Constant | maxKbps | label | detail | hourly |
  |---|---|---|---|---|
  | `AUTO` | `Int.MAX_VALUE` | `"Auto"` | `"Adaptive based on connection"` | `"Variable"` |
  | `LOW` | `64` | `"Low"` | `"~64 kbps · data saver"` | `"29 MB/hr"` |
  | `MEDIUM` | `160` | `"Medium"` | `"Standard · ~160 kbps Opus"` | `"77 MB/hr"` |
  | `HIGH` | `320` | `"High"` | `"Addon high bitrate, YouTube fallback"` | `"144 MB/hr"` |
  | `LOSSLESS` | `Int.MAX_VALUE` | `"Lossless"` | `"Addons bit-exact FLAC/ALAC, YouTube fallback"` | `"300+ MB/hr"` |
  | `OPUS` | `160` | `"Opus"` | `"Standard ~160 kbps Opus"` | `"77 MB/hr"` |

  Methods: `permitsLossless()` = `LOSSLESS` or `AUTO`; `permitsHigh()` = `LOSSLESS`/`AUTO`/`HIGH`;
  `permitsAddons()` = same as `permitsHigh()`. All four properties and their exact strings are
  user-visible and part of the contract.
- `enum class AudioLoudnessPreset(val gainOffsetMb: Int)`
  `QUIET(-600)`, `NORMAL(0)`, `LOUD(300)`, `AGGRESSIVE(700)`.
- `enum class PlaybackEngine { POTOKEN, BRAVEPIPE, AUTO }`.
- `enum class LibraryViewType { LIST, GRID; fun toggle(): LibraryViewType }` — `LIST→GRID`,
  `GRID→LIST`.
- `enum class SongFilter { LIBRARY, LIKED, DOWNLOADED, UPLOADED, EXPORTED }`.
- `enum class ArtistFilter { LIBRARY, LIKED }`.
- `enum class AlbumFilter { LIBRARY, LIKED, UPLOADED }`.
- `enum class SongSortType { CREATE_DATE, NAME, ARTIST, PLAY_TIME }`.
- `enum class PlaylistSongSortType { CUSTOM, CREATE_DATE, NAME, ARTIST, PLAY_TIME }`.
- `enum class AutoPlaylistSongSortType { CREATE_DATE, NAME, ARTIST, PLAY_TIME }`.
- `enum class ArtistSortType { CREATE_DATE, NAME, SONG_COUNT, PLAY_TIME }`.
- `enum class ArtistSongSortType { CREATE_DATE, NAME, PLAY_TIME }`.
- `enum class AlbumSortType { CREATE_DATE, NAME, ARTIST, YEAR, SONG_COUNT, LENGTH, PLAY_TIME }`.
- `enum class PlaylistSortType { CREATE_DATE, NAME, SONG_COUNT, LAST_UPDATED }`.
- `enum class MixSortType { CREATE_DATE, NAME, LAST_UPDATED }`.
- `enum class GridItemSize { BIG, SMALL }`.
- `enum class MyTopFilter { ALL_TIME, DAY, WEEK, MONTH, YEAR }` with
  `fun toTimeMillis(): Long`: `DAY` = now minus 1 day, `WEEK` = minus 1 week, `MONTH` = minus 1 month,
  `YEAR` = minus 12 months, converted with `ZoneOffset.UTC`; `ALL_TIME` = `0`. "now" is
  `LocalDateTime.now()` at call time; results are non-monotonic across DST-free UTC but depend on
  system wall clock.
- `enum class QuickPicks { QUICK_PICKS, LAST_LISTEN }`.
- `enum class PreferredLyricsProvider { LRCLIB, KUGOU, BETTER_LYRICS, SIMPMUSIC, YOULYPLUS, PAXSENIX, UNISON }`.
- `enum class PlayerButtonsStyle { DEFAULT, PRIMARY, TERTIARY }`.
- `enum class PlayerBackgroundStyle { DEFAULT, GRADIENT, BLUR, GLOW_ANIMATED, APPLE_MUSIC, LIVE_MESH, LIQUID_GLASS }`.
- `enum class LyricsAnimationStyle { NONE, FADE, GLOW, SLIDE, KARAOKE, APPLE, APPLE_V2, revenge_1, LYRICS_V2, METRO_LYRICS }`
  — note `revenge_1` is lower-case with underscore and is a persisted name.
- `enum class SearchSource { LOCAL, ONLINE; fun toggle(): SearchSource }`.
- `enum class AppFont(val value: String)`
  `SYSTEM("system")`, `GOOGLE_SANS("google_sans")`, `SANS_FLEX("sans_flex")`, `OUTFIT("outfit")`,
  `PLUS_JAKARTA_SANS("plus_jakarta_sans")`, `CUSTOM("custom")`. Companion
  `fromValue(value: String)` — first exact match, else `SYSTEM`. The stored value is `value`, not the
  enum name.
- `enum class RecommendationSource { YOUTUBE, SPOTIFY }`.

Localisation tables (immutable maps; keys/values are user-visible, must match baseline exactly;
duplicate keys are forbidden):

- `val LanguageCodeToName: Map<String, String>` — language tag → display name. Includes BCP-47 tags
  (`en-GB`, `en`, `es-419`, `pt-PT`, `pt`, `zh-CN`, `zh-TW`, `zh-HK`, `fr-CA`) and endonyms.
- `val CountryCodeToName: Map<String, String>` — ISO-3166 alpha-2 → English country name.
- `val SuggestionRegionSlugToName: Map<String, String>` — lower-case region slug (`system`, `us`,
  `in`, …) → display name; `system` maps to `"System Default"`.

These maps are read-only lookups; unknown keys return `null` via normal `Map` semantics.

### 4.3 `Dimensions.kt`

Content-type integer constants (used for `MediaItem`/intent extras; values are wire/interface
contracts): `CONTENT_TYPE_HEADER = 0`, `CONTENT_TYPE_LIST = 1`, `CONTENT_TYPE_SONG = 2`,
`CONTENT_TYPE_ARTIST = 3`, `CONTENT_TYPE_ALBUM = 4`, `CONTENT_TYPE_PLAYLIST = 5`.

`androidx.compose.ui.unit.Dp` layout tokens (exact values, used for layout parity):

`FloatingToolbarHeight = 72.dp`, `FloatingToolbarHorizontalPadding = 16.dp`,
`FloatingToolbarBottomPadding = 12.dp`, `NavigationBarHeight = FloatingToolbarHeight`,
`SlimNavBarHeight = 64.dp`, `MiniPlayerHeight = 64.dp`, `MinMiniPlayerHeight = 16.dp`,
`MiniPlayerBottomSpacing = 8.dp`, `QueuePeekHeight = 64.dp`, `AppBarHeight = 64.dp`,
`ListItemHeight = 64.dp`, `SuggestionItemHeight = 56.dp`, `SearchFilterHeight = 48.dp`,
`ListThumbnailSize = 48.dp`, `SmallGridThumbnailHeight = 104.dp`,
`GridThumbnailHeight = 128.dp`, `AlbumThumbnailSize = 144.dp`, `ThumbnailCornerRadius = 6.dp`,
`PlayerHorizontalPadding = 32.dp`.

Animation specs (Compose `AnimationSpec<Dp>`, no-bouncy spring):
`NavigationBarAnimationSpec` (StiffnessLow), `BottomSheetAnimationSpec` (StiffnessMediumLow),
`BottomSheetSoftAnimationSpec` (StiffnessLow). Damping ratio is `Spring.DampingRatioNoBouncy` for all.

### 4.4 `MediaSessionConstants.kt`

`object MediaSessionConstants`. Each action name is a plain `const String`; each `SessionCommand` uses
that action with `Bundle.EMPTY`. These exact strings cross the MediaSession/Bluetooth/Android-Auto
boundary and must not change.

Toggle/like/radio/shuffle/repeat:

| Action constant | Value | `SessionCommand` val |
|---|---|---|
| `ACTION_TOGGLE_LIBRARY` | `"TOGGLE_LIBRARY"` | `CommandToggleLibrary` |
| `ACTION_TOGGLE_START_RADIO` | `"TOGGLE_START_RADIO"` | `CommandToggleStartRadio` |
| `ACTION_TOGGLE_LIKE` | `"TOGGLE_LIKE"` | `CommandToggleLike` |
| `ACTION_TOGGLE_SHUFFLE` | `"TOGGLE_SHUFFLE"` | `CommandToggleShuffle` |
| `ACTION_TOGGLE_REPEAT_MODE` | `"TOGGLE_REPEAT_MODE"` | `CommandToggleRepeatMode` |

Podcast episode controls:

| Action constant | Value | `SessionCommand` val |
|---|---|---|
| `ACTION_EPISODE_SKIP_BACK` | `"EPISODE_SKIP_BACK"` | `CommandEpisodeSkipBack` |
| `ACTION_EPISODE_SKIP_FORWARD` | `"EPISODE_SKIP_FORWARD"` | `CommandEpisodeSkipForward` |
| `ACTION_EPISODE_SPEED` | `"EPISODE_SPEED"` | `CommandEpisodeSpeed` |

Android Auto item actions (target media id supplied in the command args):

| Action constant | Value | `SessionCommand` val |
|---|---|---|
| `ACTION_ITEM_LIKE` | `"ITEM_LIKE"` | `CommandItemLike` |
| `ACTION_ITEM_PLAY_NEXT` | `"ITEM_PLAY_NEXT"` | `CommandItemPlayNext` |
| `ACTION_ITEM_DISMISS` | `"ITEM_DISMISS"` | `CommandItemDismiss` |
| `ACTION_ITEM_FOLLOW` | `"ITEM_FOLLOW"` | `CommandItemFollow` |
| `ACTION_ITEM_UNFOLLOW` | `"ITEM_UNFOLLOW"` | `CommandItemUnfollow` |

### 4.5 Small enums

- `com.revenge.music.constants.HistorySource { LOCAL, REMOTE }`.
- `com.revenge.music.constants.LibraryFilter { SONGS, ARTISTS, ALBUMS, PLAYLISTS, LIBRARY, LOCAL, SPOTIFY, PODCASTS }`.

---

## 5. Extensions — package `com.revenge.music.extensions`

### 5.1 `ContextExt.kt`

```kotlin
fun Context.isSyncEnabled(): Boolean
fun Context.isUserLoggedIn(): Boolean
fun Context.isInternetConnected(): Boolean
```

- `isSyncEnabled()` — `true` iff `YtmSyncKey` reads `true` **and** `isUserLoggedIn()` is `true`.
  The preference read is blocking (see `DataStore.get`), so this must not be called on the main
  thread by new code; existing callers treat it as cheap. Failure to read the preference (I/O error)
  yields the provided default `true`, so the result is then `isUserLoggedIn()`.
- `isUserLoggedIn()` — reads `InnerTubeCookieKey` (blocking, `null` on failure), parses it as a cookie
  string, and returns `true` iff the cookie map contains the key `"SAPISID"` **and**
  `isInternetConnected()` is `true`. An empty/absent cookie ⇒ `false`. A cookie containing
  `SAPISID` but no connectivity ⇒ `false`.
- `isInternetConnected()` — `true` iff the active network's capabilities contain
  `NET_CAPABILITY_INTERNET`. The system service is cast to `ConnectivityManager`; a `null`
  capability/network yields `false`. No validation capability is required here.

### 5.2 `CoroutineExt.kt`

```kotlin
fun <T> Flow<T>.collect(scope: CoroutineScope, action: suspend (value: T) -> Unit)
fun <T> Flow<T>.collectLatest(scope: CoroutineScope, action: suspend (value: T) -> Unit)
val SilentHandler: CoroutineExceptionHandler
```

- The `collect` overloads launch a child coroutine in `scope` and return immediately. They never
  suspend the caller. Exceptions inside `action` propagate through `scope`'s normal
  `CoroutineExceptionHandler` semantics (i.e. they are not swallowed by these functions).
- `SilentHandler` ignores every exception (`CoroutineExceptionHandler { _, _ -> }`). Because it
  swallows, use it only for fire-and-forget flows.

### 5.3 `FileExt.kt`

```kotlin
operator fun File.div(child: String): File          // File(this, child)
fun InputStream.zipInputStream(): ZipInputStream    // wraps this
fun OutputStream.zipOutputStream(): ZipOutputStream // wraps this
```

`File.div` resolves relative to the receiver; it does not canonicalise or validate.

### 5.4 `ListExt.kt`

```kotlin
fun <T> List<T>.reversed(reversed: Boolean): List<T>
fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int): MutableList<T>
fun <T : Any> List<T>.mergeNearbyElements(
  key: (T) -> Any = { it },
  merge: (first: T, second: T) -> T = { first, _ -> first },
): List<T>
fun List<Song>.filterExplicit(enabled: Boolean = true): List<Song>
fun List<Song>.filterVideoSongs(enabled: Boolean = true): List<Song>
fun List<Album>.filterExplicitAlbums(enabled: Boolean = true): List<Album>
fun List<Playlist>.filterYoutubeShorts(enabled: Boolean = false): List<Playlist>
```

- `reversed(true)` returns a live reversed view of the receiver (no copy); `false` returns the
  receiver itself.
- `move(from, to)` repositions the element at `from` to index `to`, shifting the intervening elements,
  mutates the receiver in place, and returns the same instance. Indices are not validated; out-of-range
  throws `IndexOutOfBoundsException`.
- `mergeNearbyElements` collapses only **adjacent** runs where `key` is equal. Empty input ⇒ empty
  list. The default `key` is the element itself; the default `merge` keeps the first element. Output
  order is input order. Elements are of a non-null type (`T : Any`).
- `filterExplicit(enabled)` — when enabled, removes songs whose `song.explicit` is `true`; otherwise
  returns the same list. Return type is `List<Song>` (Room entity relation).
- `filterVideoSongs(enabled)` — when enabled, removes songs whose `song.isVideo` is `true`; otherwise
  returns the same list.
- `filterExplicitAlbums(enabled)` — when enabled, removes albums whose `album.explicit` is `true`.
- `filterYoutubeShorts(enabled = false)` — when enabled, removes playlists whose
  `playlist.browseId` starts with `"SS"`; `null` browseId is kept. Defaults to `false`, so the
  default call is a pass-through.

### 5.5 `MediaItemExt.kt`

```kotlin
val MediaItem.metadata: MediaMetadata?
fun Song.toMediaItem(): MediaItem
fun SongItem.toMediaItem(): MediaItem
fun MediaMetadata.toMediaItem(): MediaItem
```

**`MediaItem.metadata`** — resolution order:

1. If `localConfiguration?.tag` is a `MediaMetadata`, return it (the canonical path: every item built
   by `toMediaItem` stores the full model as the tag).
2. Otherwise reconstruct a partial `MediaMetadata` from Media3's `mediaMetadata`:
   - `title = (title ?? displayTitle)?.toString()`;
   - if the resulting title is null/blank **and** `mediaId` is blank ⇒ return `null`;
   - `id = mediaId`; `title = title.orEmpty()`;
   - `artists` = a single artist with `id = null` and the Media3 `artist` text when that text is
     non-blank/not-null, else empty list;
   - `duration = 0`;
   - `thumbnailUrl = artworkUri?.toString() ?: extras?.getString("artwork_uri")`.
   All other fields take defaults. This fallback exists for externally-created Media3 items (e.g.
   Bluetooth/Auto or restored sessions); it never throws.

**`toMediaItem()` for all three overloads** — builds a `androidx.media3.common.MediaItem` with:

- `mediaId` = track id;
- `uri` = a *playback seed URI* derived from the id: if the id parses as a URI whose scheme
  (lower-cased, `Locale.US`) is one of `content`, `file`, `android.resource`, `http`, `https`, the id
  is used verbatim; otherwise `https://music.youtube.com/watch?v=<percent-encoded id>`. This gives
  ExoPlayer a resolvable placeholder before the real stream URL is selected.
- `customCacheKey` = id;
- `tag` = the `MediaMetadata` instance (`MediaItem.Builder.setTag`);
- Media3 `MediaMetadata` with: `title`, `subtitle` and `artist` both equal to the joined artist names
  (`", "` separator, empty string when no artists), `artworkUri` (parsed from the nullable string),
  `albumTitle`, `albumArtist` = first artist's name or `null`, `displayTitle` = title,
  `mediaType = MEDIA_TYPE_MUSIC`, `isBrowsable = false`, `isPlayable = true`, and extras bundle
  containing `"artwork_uri"` only when artwork is non-null.

Source-specific details:

- `Song.toMediaItem()` — subtitle/artists from the Room relation `artists` (`ArtistEntity.name`);
  album title from `song.albumName`; artwork from `song.thumbnailUrl`.
- `SongItem.toMediaItem()` — artwork is `thumbnail.resize(1200, 1200)`; album title from
  `album?.name`; tag is `SongItem.toMediaMetadata()`.
- `MediaMetadata.toMediaItem()` — artwork is `thumbnailUrl` unchanged; album title from `album?.title`.

### 5.6 `PlayerExt.kt`

```kotlin
fun Player.togglePlayPause()
fun Player.toggleRepeatMode()
fun Player.getQueueWindows(): List<Timeline.Window>
fun Player.getCurrentQueueIndex(): Int
val Player.currentMetadata: MediaMetadata?
val Player.mediaItems: List<MediaItem>
fun Player.findNextMediaItemById(mediaId: String): MediaItem?
fun Player.setOffloadEnabled(enabled: Boolean)
```

- `togglePlayPause()` — if `playWhenReady == false` **and** `playbackState == STATE_IDLE`, calls
  `prepare()` first; then flips `playWhenReady` to its negation. It does not otherwise alter the
  timeline. Side effects are exactly Media3's.
- `toggleRepeatMode()` — cycles `REPEAT_MODE_OFF → REPEAT_MODE_ALL → REPEAT_MODE_ONE → REPEAT_MODE_OFF`.
  Any other current value throws `IllegalStateException`.
- `getQueueWindows()` — returns the timeline windows in playback order around the current item:
  previous items first (in order), then the current item, then following items (in order). When
  shuffle is enabled the order follows the player's effective shuffled playback order. Returns an
  empty list when the timeline is empty or the current index is unset, negative, or `>= windowCount`.
  It never returns more than `windowCount` windows and never repeats a window. The returned list is a
  snapshot; the window objects are mutable Media3 objects owned by the caller.
- `getCurrentQueueIndex()` — the current item's position in the *unshuffled, non-repeating* playback
  order. Returns `-1` for an empty timeline or an unset/out-of-range index. The first item is `0`.
- `currentMetadata` — `currentMediaItem?.metadata` (see §5.5); `null` when nothing is current.
- `mediaItems` — a read-only, live view of the player's media items: size and contents reflect the
  player at access time. Mutating operations are unsupported and throw
  `UnsupportedOperationException`.
- `findNextMediaItemById(mediaId)` — returns the first item at or after `currentMediaItemIndex` whose
  `mediaId` equals the argument, else `null`. When the current index is unset it searches from the
  start (no exception).
- `setOffloadEnabled(enabled)` — sets the player's audio offload mode to enabled/disabled accordingly
  while preserving all other track-selection parameters. Never throws for a null player state.

---

## 6. Utilities — package `com.revenge.music.utils`

### 6.1 `DataStore.kt`

```kotlin
val Context.dataStore: DataStore<Preferences>                 // preferencesDataStore(name = "settings")
operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T?
fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>, defaultValue: T): T
fun <T> preference(context: Context, key: Preferences.Key<T>, defaultValue: T): ReadOnlyProperty<Any?, T>
inline fun <reified T : Enum<T>> enumPreference(context: Context, key: Preferences.Key<String>, defaultValue: T): ReadOnlyProperty<Any?, T>
```

- `Context.dataStore` is the single process-wide preferences store named `"settings"`; repeated access
  on the same application context must return the same underlying store (the delegate guarantees one
  store per file name). Consumers must not create a second store with the same name.
- `operator get(key)` — performs a blocking read of the current stored value and returns it, or
  `null` when the key is absent **or any read throws** (I/O errors, corruption and cancellation are
  swallowed). It must not be called from the main thread by new code.
- `get(key, defaultValue)` — same, but returns `defaultValue` on absence/failure.
- `preference(context, key, default)` — returns a property delegate whose **getter** performs the
  blocking read each time it is accessed and returns `default` if the read fails. It caches nothing;
  writes are not provided by this delegate.
- `enumPreference(context, key, default)` — property delegate reading the string key and converting
  with `String?.toEnum(default)` (§6.5): case-sensitive `enumValueOf`, absent/unknown/`null` ⇒ default.
  It caches nothing.

### 6.2 `CrashUtils.kt`

```kotlin
var exceptionReporter: ((Throwable) -> Unit)?
fun reportException(throwable: Throwable)
```

`reportException` always logs through Timber (`Timber.e`) and then invokes `exceptionReporter` if it
has been installed (typically by the application). It never rethrows. `exceptionReporter` is a
process-global mutable hook; setting it to `null` disables external reporting. Thread-safe usage is
the setter's responsibility (normally set once during `Application.onCreate`).

### 6.3 `NetworkConnectivityObserver.kt`

```kotlin
class NetworkConnectivityObserver(context: Context) {
  val networkStatus: Flow<Boolean>
  fun unregister()
  fun isCurrentlyConnected(): Boolean
}
```

- `networkStatus` is a hot, conflated stream: it emits `true` when connectivity becomes available and
  `false` when it is lost. It is not a replay source, a slow collector may miss intermediate values,
  and values are delivered to one active collector at a time (not broadcast fan-out).
- Construction requests connectivity callbacks for a network requiring `NET_CAPABILITY_INTERNET` and
  `NET_CAPABILITY_NOT_RESTRICTED`. If registration throws (restricted profile / missing permission),
  construction must not throw: it emits `true` (assume usable) and continues.
- The stream's initial value reflects `isCurrentlyConnected()` at construction.
- `isCurrentlyConnected()` returns `true` iff the active network has both `NET_CAPABILITY_INTERNET`
  and `NET_CAPABILITY_VALIDATED`. Any exception ⇒ `false`.
- `unregister()` unregisters the callback and must never throw (unknown callback is swallowed).
  Further calls are safe.

### 6.4 `PlaybackLogManager.kt`

```kotlin
enum class PlaybackLogLevel { INFO, WARNING, ERROR, DEBUG, BOT }
data class PlaybackLogEntry(
  val timestamp: String,
  val level: PlaybackLogLevel,
  val message: String,
  val details: String? = null,
)
object PlaybackLogManager {
  val logs: StateFlow<List<PlaybackLogEntry>>
  fun log(level: PlaybackLogLevel, message: String, details: String? = null)
  fun clearLogs()
}
```

- Process-wide in-memory ring buffer capped at **500** entries. `log` appends and, when the size
  exceeds 500, retains the most recent 500 (oldest dropped).
- `timestamp` is formatted at entry creation with pattern `"HH:mm:ss.SSS"` in the device's default
  time zone.
- `logs` is a read-only `StateFlow`; every `log`/`clearLogs` sets a new immutable list so collectors
  re-emit. Initial value is an empty list.
- `clearLogs()` resets to empty. No persistence; contents are lost on process death.

### 6.5 `StringExt.kt`

```kotlin
inline fun <reified T : Enum<T>> String?.toEnum(defaultValue: T): T
fun String.toSQLiteQuery(): SimpleSQLiteQuery
fun String.toInetSocketAddress(): InetSocketAddress
```

- `toEnum` — matches the string to an enum constant by exact, case-sensitive name; `null` or an
  unknown name ⇒ `defaultValue`. Other exceptions (none expected) propagate.
- `toSQLiteQuery` — wraps the string as `SimpleSQLiteQuery` with no bind args.
- `toInetSocketAddress` — splits on `":"` into exactly host and port and calls
  `InetSocketAddress.createUnresolved(host, port)`, i.e. **no DNS resolution**. A missing port yields
  `IndexOutOfBoundsException`; a non-numeric/out-of-range port yields `NumberFormatException` /
  `IllegalArgumentException`. Callers pass validated config only.

### 6.6 `UtilExt.kt`

```kotlin
fun <T> tryOrNull(block: () -> T): T?
```

Returns the block's value, or `null` if it throws any `Exception`. `Throwable`s that are not
`Exception` (e.g. `OutOfMemoryError`) propagate.

---

## 7. UI utilities — packages `com.revenge.music.ui.utils`, `com.revenge.music.ui.screens`

### 7.1 `YouTubeUtils.kt`

```kotlin
fun String.resize(width: Int? = null, height: Int? = null): String
fun ytimgFallbackUrl(url: String): String?
```

**`resize(width, height)`** — rewrites a thumbnail URL to a size variant. If both arguments are
`null`, returns the receiver unchanged. Otherwise, matching the host by case-sensitive substring,
the result is:

1. Contains `i.ytimg.com` — every occurrence of a size token `default`, `mqdefault`, `hqdefault`,
   `sddefault`, `maxresdefault` or `hq720` immediately followed by `.jpg` becomes
   `maxresdefault.jpg` when `(width ?: 0) >= 1200`, else `hqdefault.jpg`. The rest of the URL
   (path and query) is preserved.
2. Contains `googleusercontent.com` **and** `=w` — the receiver truncated before `=w`, followed by
   `=w{S}-h{S}`, where `S = 1200` when `(width ?: 0) >= 1000 || (height ?: 0) >= 1000`, else `500`.
3. Contains `yt3.ggpht.com` — the receiver truncated before the first `=`, followed by `=s{N}`, where
   `N = width ?: height`. (Avatar ids may contain `-s`; only the first `=` separates the size.)
4. The whole receiver matches `https://lh<digits>.googleusercontent.com/…` — the receiver truncated
   before the first `=`, followed by `=w{S}-h{S}` with the same threshold as case 2.
5. Otherwise the receiver is returned unchanged.

The function never throws for arbitrary strings; it is pure and allocations-only. Unknown hosts and
URLs without the expected suffix are returned verbatim.

**`ytimgFallbackUrl(url)`** — returns the next still to try when a YouTube thumbnail is missing, or
`null`:

- Not containing `ytimg.com` ⇒ `null`. Only the path before `?` is considered; the query is dropped
  from the result.
- Path ends with `/maxresdefault.jpg` or `/sddefault.jpg` ⇒ same directory + `/hq720.jpg`.
- Path ends with `/hq720.jpg` ⇒ same directory + `/hqdefault.jpg`.
- Any other path (including `hqdefault.jpg`, `default.jpg`, `mqdefault.jpg`) ⇒ `null`.

### 7.2 `OptionStats.kt`

`enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }`. `CONTINUOUS` is the default selection in
the stats UI; no methods.

---

## 8. DI qualifiers — package `com.revenge.music.di`

`Qualifiers.kt` declares three Hilt/Javax qualifier annotations, each
`@Qualifier @Retention(AnnotationRetention.BINARY) annotation class …`:

- `PlayerCache` — qualifies the player/preload image or media cache.
- `DownloadCache` — qualifies the download media cache.
- `ApplicationScope` — qualifies the application-wide `CoroutineScope`.

Annotations carry no members. Consumers inject the same qualified type with these annotations.

---

## 9. AI logging — package `com.revenge.music.ai`

```kotlin
object AiLogger {
  suspend fun log(context: Context, action: String, details: String)
}
```

- Appends a record `{ timestamp: Long (epoch millis), action: String, details: String }` to a JSON
  array stored as a `String` under DataStore key `"ai_action_logs"`. The key is private to this file
  and is not part of `PreferenceKeys`.
- If the stored value is absent it is treated as `"[]"`; if it is not valid JSON or not an array, the
  log starts fresh from an empty array.
- After appending, if the array length exceeds **50**, only the last 50 entries are kept (newest kept,
  oldest dropped) and the result is persisted.
- Any exception is logged via Timber and otherwise swallowed; the function never propagates an error
  to callers and is safe to call fire-and-forget. It suspends only for the DataStore read/edit.
- Concurrent calls may race (read-modify-write); last writer wins. No locking is required by the
  contract; lost log entries are acceptable.

---

## 10. Domain mapping and repositories — packages `com.revenge.core.*`

### 10.1 `com.revenge.core.mapping.DomainMappers`

```kotlin
fun com.revenge.music.db.entities.Song.toDomain(): com.revenge.domain.models.Song
fun com.revenge.music.db.entities.SongEntity.toDomain(): com.revenge.domain.models.Song
fun com.revenge.music.db.entities.Playlist.toDomain(): com.revenge.domain.models.Playlist
fun com.revenge.music.db.entities.PlaylistEntity.toDomain(songCount: Int = 0): com.revenge.domain.models.Playlist
```

Domain shapes required (from `docs/specs/domain.md`):

```kotlin
data class Song(
  val id: String, val title: String, val artists: List<ArtistRef>, val album: AlbumRef?,
  val durationSeconds: Int, val thumbnailUrl: String?, val isExplicit: Boolean = false,
  val isLocal: Boolean = false, val liked: Boolean = false, val totalPlayTimeMs: Long = 0L,
)
data class ArtistRef(val id: String, val name: String)
data class AlbumRef(val id: String, val title: String)
data class Playlist(val id: String, val name: String, val songCount: Int = 0, val thumbnailUrl: String?)
```

Mapping semantics:

- `Song.toDomain()` (Room relation): `id/title` from `song.*`; `artists` maps every
  `ArtistEntity` to `ArtistRef(id, name)` preserving order; `album` = full `album` relation as
  `AlbumRef(id, title)` when present, otherwise `song.albumId?.let { AlbumRef(it, song.albumName.orEmpty()) }`;
  `durationSeconds = song.duration`; `thumbnailUrl` = the relation's computed thumbnail
  (local songs may yield a `content://media/external/audio/albumart/<id>` URI; see §11.2);
  `isExplicit = song.explicit`; `isLocal = song.isLocal`; `liked = song.liked`;
  `totalPlayTimeMs = song.totalPlayTime`.
- `SongEntity.toDomain()` (entity only, no relations): `artists = emptyList()` always;
  `album = albumId?.let { AlbumRef(it, albumName.orEmpty()) }`; thumbnail resolution:
  - `thumbnailUrl != null` ⇒ use it;
  - else if `isLocal` and `albumId` has the form `"LOCAL_ALBUM_<n>"` with a numeric `<n> > 0` ⇒
    `"content://media/external/audio/albumart/<n>"`;
  - else if `isLocal` ⇒ the song `id`;
  - else `null`.
  No trimming/normalisation beyond `orEmpty()`.
- `Playlist.toDomain()`: `id/name` from `playlist.*`; `songCount` from the relation;
  `thumbnailUrl = thumbnails.firstOrNull() ?: playlist.thumbnailUrl`. The `thumbnails` getter
  prefers an explicit playlist thumbnail, then non-null song thumbnails in relation order.
- `PlaylistEntity.toDomain(songCount = 0)`: `thumbnailUrl = thumbnailUrl`; the caller supplies the
  count.

All four are pure, non-throwing, allocation-only. Nulls map to the domain's nullable fields; lists
never become `null`.

### 10.2 `com.revenge.core.repository.SongRepositoryImpl`

`@Singleton class SongRepositoryImpl @Inject constructor(private val database: MusicDatabase) :
SongRepository`. Overrides exactly:

```kotlin
fun getSong(id: String): Flow<DomainSong?>
fun getLikedSongs(): Flow<List<DomainSong>>
fun getRecentlyPlayed(limit: Int): Flow<List<DomainSong>>
suspend fun updateLiked(id: String, isLiked: Boolean)
suspend fun incrementPlayTime(id: String, durationMs: Long)
```

Contract:

- `getSong(id)` — maps `database.song(id)` (`Flow<Song?>`) through `Song.toDomain()`, preserving
  `null`. Unknown id emits `null`; never throws for absence.
- `getLikedSongs()` — maps `database.likedSongsByRowIdAsc()` (`Flow<List<Song>>`) with
  `Song.toDomain()`, preserving database order (row-id ascending = creation order; not liked-date
  order). Empty list when none.
- `getRecentlyPlayed(limit)` — maps `database.events()` (`Flow<List<EventWithSong>>`, already ordered
  most-recent-first by event row id descending), converts each `event.song` with `Song.toDomain()`,
  de-duplicates by domain `id` keeping the **first** (most recent) occurrence, then takes at most
  `limit`. `limit <= 0` yields an empty list. Fewer than `limit` when history is short.
- `updateLiked(id, isLiked)` — schedules an asynchronous write (it does not block the caller) that
  sets `liked` and sets `likedDate` to the current wall-clock time when `isLiked` is true, or clears
  the date otherwise. Write failures surface on the write path and are not propagated to the caller.
- `incrementPlayTime(id, durationMs)` — the same asynchronous, non-propagating write semantics; adds
  `durationMs` to `totalPlayTime`. Negative values are passed through unclamped.

The class holds no mutable state and is safe to share; flows are cold Room flows that re-emit on
table changes.

### 10.3 `com.revenge.core.repository.PlaylistRepositoryImpl`

`@Singleton class PlaylistRepositoryImpl @Inject constructor(private val database: MusicDatabase) :
PlaylistRepository`. Overrides exactly:

```kotlin
fun getAllPlaylists(): Flow<List<DomainPlaylist>>
fun getPlaylistSongs(playlistId: String): Flow<List<DomainSong>>
suspend fun addSongToPlaylist(playlistId: String, songId: String)
suspend fun removeSongFromPlaylist(playlistId: String, songId: String)
```

- `getAllPlaylists()` — emits only saved/bookmarked playlists (`bookmarkedAt` non-null), ordered by
  `name` ascending, each mapped via `Playlist.toDomain()` with its `songCount` populated. Empty when
  none exist.
- `getPlaylistSongs(playlistId)` — emits the playlist's songs ordered by `position`, each mapped via
  `it.song.toDomain()`. Unknown playlist emits an empty list.
- `addSongToPlaylist(playlistId, songId)` — schedules an asynchronous write that appends the song with
  `position` equal to the current maximum position for that playlist plus one, or `0` when the
  playlist has no entries. Conflicts and failures (duplicate membership, unknown playlist/song,
  foreign-key violations, other SQLite errors) are swallowed and never propagate to the caller.
- `removeSongFromPlaylist(playlistId, songId)` — schedules an asynchronous write that removes **all**
  memberships of `songId` in that playlist; absent membership is a no-op success. As with the other
  writes, write-time errors are not propagated to the caller.

---

## 11. Cross-cluster compile contracts

These shapes must exist for this cluster to compile. They are owned by other modules/clusters; the
implementer must not redefine them here, but must be able to compile against them. Signatures are
verbatim compatibility contracts.

### 11.1 `:innertube` (`com.revenge.innertube.*`)

```kotlin
// com.revenge.innertube.models
sealed class YTItem {
  abstract val id: String
  abstract val title: String
  abstract val thumbnail: String?
  abstract val explicit: Boolean
  abstract val shareLink: String
}
data class Artist(val name: String, val id: String?)
data class Album(val name: String, val id: String)
data class SongItem(
  override val id: String,
  override val title: String,
  val artists: List<Artist>,
  val album: Album? = null,
  val duration: Int? = null,
  val musicVideoType: String? = null,
  val chartPosition: Int? = null,
  val chartChange: String? = null,
  override val thumbnail: String,
  override val explicit: Boolean = false,
  val endpoint: WatchEndpoint? = null,
  val setVideoId: String? = null,
  val libraryAddToken: String? = null,
  val libraryRemoveToken: String? = null,
  val historyRemoveToken: String? = null,
  val publishedText: String? = null,
  val playedPercent: Int? = null,
  val description: String? = null,
) : YTItem()

// com.revenge.innertube.models.Endpoint
object/companion: WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion
  const val MUSIC_VIDEO_TYPE_ATV = "MUSIC_VIDEO_TYPE_ATV"

// com.revenge.innertube.utils
fun parseCookieString(cookie: String): Map<String, String>
```

`parseCookieString` splits on `"; "`, drops empty parts and parts without `=`, splits each at the
**first** `=`, and returns a map (later duplicates overwrite earlier). It never throws for arbitrary
input.

### 11.2 Room slice (`com.revenge.music.db.*`)

Authoritative schema is the `core-db` cluster spec; the slice this cluster reads/writes:

```kotlin
// com.revenge.music.db.entities
sealed class LocalItem { abstract val id: String; abstract val title: String; abstract val thumbnailUrl: String? }

data class Song(
  @Embedded val song: SongEntity,
  val artists: List<ArtistEntity>,
  val album: AlbumEntity? = null,
  val format: FormatEntity? = null,
) : LocalItem() {
  override val id: String
  override val title: String
  override val thumbnailUrl: String?   // local: MEDIA_STORE album-art URI or song id fallback
  val romanizeLyrics: Boolean
}

data class SongEntity(
  @PrimaryKey val id: String,
  val title: String,
  val duration: Int = -1,
  val thumbnailUrl: String? = null,
  val albumId: String? = null,
  val albumName: String? = null,
  val explicit: Boolean = false,
  val year: Int? = null,
  val date: LocalDateTime? = null,
  val dateModified: LocalDateTime? = null,
  val liked: Boolean = false,
  val likedDate: LocalDateTime? = null,
  val totalPlayTime: Long = 0,
  val inLibrary: LocalDateTime? = null,
  val dateDownload: LocalDateTime? = null,
  val isLocal: Boolean = false,
  val libraryAddToken: String? = null,
  val libraryRemoveToken: String? = null,
  val lyricsOffset: Int = 0,
  val romanizeLyrics: Boolean = true,
  val isDownloaded: Boolean = false,
  val isUploaded: Boolean = false,
  val isVideo: Boolean = false,
  val hideFromQuickPicks: Boolean = false,
) { /* localToggleLike(), toggleLike(), toggleLibrary(syncToYouTube=true), toggleUploaded(), toggleHideFromQuickPicks() */ }

data class Playlist(
  @Embedded val playlist: PlaylistEntity,
  val songCount: Int,
  val songThumbnails: List<String?>,
) : LocalItem() { val thumbnails: List<String> }

data class PlaylistEntity(
  @PrimaryKey val id: String = generatePlaylistId(),
  val name: String,
  val browseId: String? = null,
  val createdAt: LocalDateTime? = LocalDateTime.now(),
  val lastUpdateTime: LocalDateTime? = LocalDateTime.now(),
  val isEditable: Boolean = true,
  val bookmarkedAt: LocalDateTime? = null,
  val remoteSongCount: Int? = null,
  val playEndpointParams: String? = null,
  val thumbnailUrl: String? = null,
  val shuffleEndpointParams: String? = null,
  val radioEndpointParams: String? = null,
  val isLocal: Boolean = false,
  val isAutoSync: Boolean = false,
  val isPinned: Boolean = false,
) { val shareLink: String?; companion { LIKED_PLAYLIST_ID="LP_LIKED"; DOWNLOADED_PLAYLIST_ID="LP_DOWNLOADED"; generatePlaylistId() } }

data class PlaylistSong(@Embedded val map: PlaylistSongMap, val song: Song)
data class EventWithSong(@Embedded val event: Event, val song: Song)
data class PlaylistSongMap(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  val playlistId: String, val songId: String, val position: Int = 0, val setVideoId: String? = null,
)
data class ArtistEntity(/* @PrimaryKey */ val id: String, val name: String, /* … */)
data class AlbumEntity(/* @PrimaryKey */ val id: String, val title: String, val explicit: Boolean = false, /* … */)
data class FormatEntity(/* fields are opaque to this cluster; used only as a nullable @Relation */)
```

DAO methods consumed (on `DatabaseDao`, reached through `MusicDatabase` delegation):

```kotlin
fun song(songId: String?): Flow<Song?>
fun likedSongsByRowIdAsc(): Flow<List<Song>>
fun events(): Flow<List<EventWithSong>>                 // ORDER BY event.rowId DESC
fun playlistsByNameAsc(): Flow<List<Playlist>>          // WHERE bookmarkedAt IS NOT NULL ORDER BY name
fun playlistSongs(playlistId: String): Flow<List<PlaylistSong>> // ORDER BY position
fun playlistSongMaps(songId: String): List<PlaylistSongMap>
fun playlistSongMaps(playlistId: String, from: Int): List<PlaylistSongMap> // position >= from, ORDER BY position
fun updateLikedStatus(songId: String, liked: Boolean, likedDate: LocalDateTime?)
fun incrementTotalPlayTime(songId: String, playTime: Long)
fun insert(map: PlaylistSongMap)                        // onConflict = IGNORE
@Delete fun delete(playlistSongMap: PlaylistSongMap)
```

`MusicDatabase` (delegating wrapper around Room `InternalDatabase`):

```kotlin
fun query(block: MusicDatabase.() -> Unit)       // executes block on the query executor
fun transaction(block: MusicDatabase.() -> Unit)
suspend fun withTransaction(block: suspend MusicDatabase.() -> Unit)
fun close()
// plus every DatabaseDao member via delegation, and DAO accessors
```

`Event` table (needed for recent history ordering):
`@Entity(tableName = "event", foreignKeys = [ForeignKey(SongEntity, ["id"], ["songId"], onDelete=CASCADE)])`
with `id: Long @PrimaryKey(autoGenerate=true) = 0`, `songId: String` (indexed), `timestamp: LocalDateTime`,
`playTime: Long`.

---

## 12. Persistence and upgrade-compatibility invariants

### 12.1 Room

- Database name `song.db`, schema version **48**, `exportSchema = true` to `core/schemas`,
  `JournalMode.WRITE_AHEAD_LOGGING`, fixed 4-thread query and 4-thread transaction executors.
  One-time `onOpen` PRAGMAs: `busy_timeout=60000`, `cache_size=-16000`, `wal_autocheckpoint=1000`,
  `synchronous=NORMAL`; failures are logged, never fatal.
- Auto-migrations (exact set, must be preserved): `2→3`, `3→4`, `4→5`, `5→6` (`Migration5To6`),
  `6→7` (`Migration6To7`), `7→8` (`Migration7To8`), `8→9`, `9→10`, `10→11`, `11→12`, `12→13`, `13→14`,
  `14→15`, `15→16`, `16→17`, `17→18`, `18→19`, `19→20`, `20→21`, `21→22`, `22→23`, `23→24`, `24→25`,
  `25→26`, `26→27`, `27→28`, `30→31`, `32→33`, `33→34`, `34→35`, `35→36`, `36→37` (`Migration36To37Spec`),
  `41→42` (`Migration41To42`), `46→47` (`Migration46To47Spec`), `47→48` (`Migration47To48Spec`).
  Manual migrations registered: `MIGRATION_1_2`, `21→24`, `22→24`, `24→25`, `27→28`, `28→29`, `29→30`,
  `31→32`, `36→37`, `37→38`, `38→39`, `39→40`, `40→41`, `41→42`, `42→43`, `43→44`, `44→45`, `45→46`.
  Together these cover every version 1…48; the authoritative table/column/foreign-key definitions and
  migration bodies belong to the `core-db` cluster spec and must not be altered here.
- Tables this cluster depends on and their columns used: `song` (§11.2), `playlist` (§11.2),
  `playlist_song_map` (`id`, `playlistId` indexed, `songId` indexed, `position`, `setVideoId`;
  foreign keys to `playlist.id` and `song.id`, `ON DELETE CASCADE`), `event` (§11.2), plus relation
  tables `song_artist_map`, `song_album_map` and views `sorted_song_artist_map`,
  `sorted_song_album_map`, `playlist_song_map_preview`.
- No type converter, column name, index, or foreign-key action introduced by this cluster may change.
  `SongEntity.isLocal`/`isVideo`/`isUploaded` and other defaulted columns must keep their
  `@ColumnInfo(defaultValue = …)` declarations, because Room validates them against schema v48 on open.
- Repositories must not run blocking DAO calls on the main thread; all writes go through
  `MusicDatabase.query { }`.

### 12.2 DataStore preferences

- Store file name `"settings"`. Every key in §4.1 is an on-disk name; no renames. Types must not change
  (a `Long` read as `Int` or a value stored under a different type is silently absent from DataStore
  and falls back to the caller default, which is data loss).
- Keys with deliberate mismatches (`MixSortDescendingKey` → `albumSortDescending`, `QuickPicksKey` →
  `discover`, `QueueEditLockKey` → `queueEditLockV2`, `SelectedFontKey` → `selected_app_font`,
  `IsMonochromeThemeKey` → `is_monochrome_theme`, `DensityScaleKey` → `density_scale_factor`) must
  keep those strings.
- `SavedAccountsKey` holds a JSON array of `AccountData`; `AiLogger` owns `"ai_action_logs"` (not a
  `PreferenceKeys` entry).

### 12.3 Java-serialised playback state (`PersistQueue`, `PersistPlayerState`)

- Both classes and every nested type implement `java.io.Serializable`; no `@Serializable` annotations.
- Field names, types, declaration order, and sealed-subclass names are part of the stream shape. Keep
  them exactly. Do not introduce non-transient fields.
- Readers must treat any deserialisation failure as "no saved state": the playback layer deletes the
  corrupt file and starts empty (existing behaviour). Therefore a version mismatch is safe but loses
  the queue; a best-effort match preserves playback across process death.
- `PersistQueue.items` are `MediaMetadata` values and rely on `LocalDateTime` (Serializable) and the
  nested `Artist`/`Album` serialisable classes. `ObjectOutputStream`/`ObjectInputStream` are used
  directly; externalizable/custom `writeObject` is not allowed.

---

## 13. Acceptance tests

Deterministic, requirement-driven. Use JUnit 4 (existing convention), `runTest` where coroutines are
involved, Robolectric only for `Context`-backed DataStore/Connectivity tests, and fakes/in-memory Room
elsewhere. Each case maps to a normative clause above.

### 13.1 Preferences (`PreferenceKeysTest`)

P1. Reflective invariant: every top-level `PreferenceKeys` key is a `Preferences.Key<*>`; there are no
two distinct identifiers with the same stored name **except** the documented deliberate alias
`MixSortDescendingKey` (and any key intentionally shared in §4.1). Freeze a golden map of
identifier → (type, name) and assert equality.
P2. Spot-check the non-obvious names: `QuickPicksKey.name == "discover"`,
`QueueEditLockKey.name == "queueEditLockV2"`, `MixSortDescendingKey.name == "albumSortDescending"`,
`IsMonochromeThemeKey.name == "is_monochrome_theme"`, `SelectedFontKey.name == "selected_app_font"`,
`DensityScaleKey.name == "density_scale_factor"`.
P3. `SYSTEM_DEFAULT == "SYSTEM_DEFAULT"`; `SYNC_COOLDOWN == 1800L`.
P4. `HapticIntensity.fromName("light") == LIGHT`, `fromName("Strong") == STRONG`,
`fromName("  LIGHT ") == MEDIUM` (matching is case-insensitive but whitespace is **not** trimmed, so
the padded name falls back), `fromName(null)` and `fromName("nope")` → `MEDIUM`.
P5. `DensityScale.fromValue(0.75f) == COMPACT`; `fromValue(0.8f) == NATIVE`.
P6. `AppFont.fromValue("sans_flex") == SANS_FLEX`; `fromValue("SANS_FLEX") == SYSTEM` (the stored
`value`, not the enum name, is matched); `fromValue("nope")` → `SYSTEM`.
P7. `LibraryViewType.LIST.toggle() == GRID` and round-trips; `SearchSource.LOCAL.toggle() == ONLINE`.
P8. `MyTopFilter.ALL_TIME.toTimeMillis() == 0L`; `DAY/WEEK/MONTH/YEAR` are strictly less than
`System.currentTimeMillis()` and ordered `DAY > WEEK > MONTH > YEAR` (allow a generous clock skew).
P9. `AudioQuality.AUTO.permitsLossless()` and `permitsHigh()` are true; `LOW` is false for all three;
`HIGH.permitsHigh()` true but `permitsLossless()` false.

### 13.2 Models and mappers (`MediaMetadataMappersTest`)

M1. `MediaMetadata(musicVideoType = null).isVideoSong == false`;
`musicVideoType = "MUSIC_VIDEO_TYPE_ATV"` ⇒ `false`; `"MUSIC_VIDEO_TYPE_OMV"` and
`"MUSIC_VIDEO_TYPE_PODCAST_EPISODE"` ⇒ `true`.
M2. `Song.toMediaMetadata()` maps id/title/duration/thumbnail, ordered artists to
`MediaMetadata.Artist`, and explicit; `album` prefers the full album relation (`id`,`title`) and
otherwise falls back to `albumId` with `albumName.orEmpty()`; `musicVideoType` is
`"MUSIC_VIDEO_TYPE_OMV"` iff `song.isVideo`, else `null`; `source == USER` and `suggestedBy == null`.
It deliberately does **not** copy `liked`, `likedDate`, `inLibrary`, or library tokens; those take
their defaults (`false`/`null`).
M3. `MediaMetadata.toSongEntity()` sets `albumId`/`albumName` from `album`, `isVideo = isVideoSong`,
and copies `liked`, `likedDate`, `inLibrary`, `libraryAddToken`, `libraryRemoveToken`,
`thumbnailUrl`, `explicit`; artists are not part of `SongEntity`.
M4. `SongItem.toMediaMetadata()`: `duration == null` ⇒ `-1`; thumbnail is `resize(1200,1200)`; album
title comes from `album.name`; `setVideoId` and library tokens copied.
M5. `SongItem.toMediaItem()`: media id, URI seed (`https://music.youtube.com/watch?v=<encoded>` for a
bare id), custom cache key, tag identity, artwork URI in extras, `mediaType == MUSIC`,
`isPlayable == true`.
M6. `playbackSeedUri` behaviour: a `content://…`, `file://…`, `http(s)://…`, `android.resource://…`
id is kept verbatim; an uppercase scheme (`HTTPS://…`) is recognised (lower-cased check); a bare id is
URL-encoded (spaces/`?`/`&` encoded) into a watch URL.
M7. `MediaItem.metadata` returns the tag when present; without a tag, an item with blank title and
blank media id returns `null`; with only a media id it returns `MediaMetadata(id=mediaId, title="")`
and empty artists; an artist string produces one `Artist(id=null, name=…)`.
M8. `PersistQueue` and `PersistPlayerState` survive `ObjectOutputStream` → `ObjectInputStream`
round-trip with nested `QueueData` and `QueueType` values preserved; a `PersistQueue` with
`mediaItemIndex` beyond `items.size` survives serialisation (validation is the restore layer's job).

### 13.3 Extensions

E1. `reversed(true)` returns a view reflecting later mutations of the backing list (or at minimum
equals the reversed order without copying semantics observable via identity is not required); `false`
returns the same instance.
E2. `move(1,2)` on `[1,2,3]` ⇒ `[1,3,2]`; `move(2,0)` ⇒ `[3,1,2]`; same instance returned.
E3. `mergeNearbyElements` on `["a1","a2","b1","a3"]` with key `first()` and merge prefix-concat ⇒
`["a12","b1","a3"]`; empty ⇒ empty; default merge keeps the first element of each adjacent run.
E4. `filterExplicit(true)` removes only explicit songs; `false` returns the same list.
`filterVideoSongs(true)` removes only `isVideo`; `filterYoutubeShorts(true)` removes playlists with
`browseId` starting `SS`, keeps `null`; default `filterYoutubeShorts()` is pass-through.
E5. `String.toEnum`: `"BETA"` ⇒ BETA, `"nope"` ⇒ default, `null` ⇒ default.
E6. `tryOrNull { error("x") } == null`; `tryOrNull { 2 } == 2`.
E7. `"host:8080".toInetSocketAddress()` is unresolved (`isUnresolved == true`), host `host`, port
`8080`; `"host"` throws; `"host:abc"` throws.
E8. `toggleRepeatMode()` cycles off→all→one→off; an invalid initial repeat mode throws
`IllegalStateException`.
E9. `togglePlayPause()` on an idle, not-playWhenReady fake calls `prepare()` exactly once and sets
`playWhenReady = true`; a second call sets it back to `false` without preparing again.
E10. `getQueueWindows()` for a 5-item timeline with current index 2 and shuffle off returns all five
windows in timeline order `[0,1,2,3,4]` (previous items first, then current, then next). With shuffle
on, assert the result contains every timeline window exactly once and includes the current window.
E11. `getQueueWindows()` for an empty timeline / unset index returns `emptyList()`.
E12. `getCurrentQueueIndex()` is `2` for current index 2 with shuffle off; `-1` for empty timeline.
E13. `findNextMediaItemById` includes the current item (searching for the current id returns it);
returns `null` for an id only before the current index.
E14. `mediaItems.size` and indexing match `mediaItemCount`/`getMediaItemAt` at access time; mutation
throws `UnsupportedOperationException`.

### 13.4 Thumbnail utilities (`YouTubeUtilsTest`, keep existing cases)

Y1. ytimg `default.jpg` with `resize(1200,1200)` ⇒ `maxresdefault.jpg`; `maxresdefault.jpg` with
`resize(544,544)` ⇒ `hqdefault.jpg`.
Y2. `lh3.googleusercontent.com/abc=w60-h60` with `resize(1200,1200)` ⇒ `…=w1200-h1200`; with
`resize(500,500)` ⇒ `…=w500-h500`.
Y3. `yt3.ggpht.com/abc=s60` with `resize(544,544)` ⇒ `…=s544`; with `height=300,width=null` ⇒ `…=s300`.
Y4. Unknown host and `resize()` (both null) return the input unchanged.
Y5. `ytimgFallbackUrl`: maxres→hq720, sddefault→hq720, hq720→hqdefault, hqdefault→null, non-ytimg→null;
a query string is dropped from the returned URL.

### 13.5 DataStore helpers and observers (`DataStoreHelpersTest`, Robolectric)

D1. `dataStore.get(missingKey)` ⇒ `null`; `get(missingKey, default)` ⇒ default.
D2. After `edit { it[key] = value }`, the blocking `get` (both overloads) returns the value.
D3. `preference(context, key, default)` re-reads on each access: changing the stored value between two
reads changes the delegate result. `enumPreference` maps a stored constant name and falls back for
unknown/null.
D4. Two `Context.dataStore` accesses on the same application context yield the same store (no
"IllegalStateException: There are multiple DataStores active for the same file").
D5. `NetworkConnectivityObserver.isCurrentlyConnected()` returns false when there is no active
network (Robolectric shadow) without throwing; `unregister()` twice does not throw.
D6. `PlaybackLogManager.log` 501 entries ⇒ `logs.value.size == 500` and the first entry is the second
oldest (oldest dropped); `clearLogs()` ⇒ empty.
D7. `AiLogger.log` twice ⇒ stored JSON array length 2 with `action`/`details`; after 51 logs the array
length is 50 and contains the newest entries; malformed pre-existing JSON does not throw and is
replaced by a valid array.

### 13.6 Domain mapping and repositories

R1. `Song.toDomain()` maps a song with two ordered artists to a domain song with the same ordered
`ArtistRef`s, album ref from the relation, `isLocal`, `liked`, and `totalPlayTimeMs`.
R2. `SongEntity.toDomain()` with `isLocal = true`, `thumbnailUrl = null`, `albumId = "LOCAL_ALBUM_42"`
⇒ thumbnail `"content://media/external/audio/albumart/42"`; with a non-numeric/zero album id ⇒ the
song id; `isLocal = false` and null thumbnail ⇒ `null`; `artists` always empty.
R3. `Playlist.toDomain()` prefers `playlist.thumbnailUrl`, else the first non-null `songThumbnails`;
`PlaylistEntity.toDomain(7)` ⇒ `songCount == 7`.
R4. `SongRepositoryImpl.getRecentlyPlayed(2)` over an events flow containing duplicate ids in
most-recent-first order returns 2 unique domain songs and keeps the most recent copy; `limit = 0` and
negative ⇒ empty.
R5. `SongRepositoryImpl.getSong(unknown)` emits `null` and does not throw.
R6. `updateLiked(id, true)` persists `liked = true` and a non-null `likedDate`; `false` clears the
date. `incrementPlayTime` adds to the existing total.
R7. `PlaylistRepositoryImpl.getAllPlaylists()` excludes unbookmarked playlists and is ordered by name.
R8. `getPlaylistSongs` preserves `position` order; unknown playlist ⇒ empty.
R9. `addSongToPlaylist` on a playlist whose max position is 4 inserts position 5; on an empty playlist
inserts position 0; adding the same song twice does not throw; adding to an unknown playlist/song
does not throw either (the DAO wrapper swallows the foreign-key failure).
R10. `removeSongFromPlaylist` removes all duplicate memberships and is a no-op for an absent song.

### 13.7 Out of scope for this cluster (explicitly)

DB migration tests (1→48), audio-focus handling, download/export recovery, EQ parsing, and USB
routing fallback are owned by the `core-db`, `:playback`/`:app`, `:dsp-core`/`:audio-dsp`, and
`:usbaudio` cluster specs respectively. This spec only guarantees the shared types those tests use
compile and behave as documented here.

---

## 14. Non-goals

- No Compose screen/content implementations beyond the small enum in `ui/screens` and URL helpers in
  `ui/utils`.
- No replacement of the app-side split-package helpers (`rememberPreference`, `makeTimeString`,
  `listItemShape`, `backToMain`, etc.) that also live under `com.revenge.music.utils`/`.ui.utils` in
  `:app`; this cluster provides only the files listed in §2.
- No changes to the Room schema, database name, or migration ids.
- No preference-key renames, type changes, or additional persisted keys; `PreferenceKeys` remains the
  only source of truth.
- No new public API is required. Adding helpers is allowed only if it preserves every signature,
  storage name, and documented behaviour above.
