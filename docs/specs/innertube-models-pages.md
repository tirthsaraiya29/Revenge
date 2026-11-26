# Clean-Room Specification — `:innertube` models & pages

Status: M2 specification (behavioural contract only).
Module: `:innertube` — directory `innertube`, Gradle path `:innertube`.
Sub-scope: `com.revenge.innertube.models` and `com.revenge.innertube.pages`.
Clean-room namespace (must be preserved exactly): `com.revenge.innertube.models`,
`com.revenge.innertube.models.body`, `com.revenge.innertube.models.comment`,
`com.revenge.innertube.models.response`, `com.revenge.innertube.pages`.

This document describes observable wire shape, public API, parsing behaviour and error semantics. It
intentionally omits statement-level implementation. Any implementation that satisfies the contracts
and acceptance tests is acceptable.

Consumers whose code must keep compiling are listed in Section 11.

---

## 1. Scope and purpose

These two packages are the typed boundary between YouTube Music's InnerTube JSON and the rest of the
application:

- `models` contains (a) Kotlin `data class` DTOs annotated with `kotlinx.serialization.@Serializable`
  whose property names (or explicit aliases) are the wire contract, (b) request-body DTOs, and
  (c) the app-facing playback/listing value types (`YTItem` hierarchy, `MediaInfo`, `TasteSignals`,
  `AccountInfo`).
- `pages` contains app-facing immutable page results plus stateless parsers that turn decoded
  renderer DTOs into `YTItem` values.

Nothing in these packages performs I/O. The `com.revenge.innertube.YouTube` object (out of scope,
separate cluster) performs all network calls and assembles the page objects.

---

## 2. Build and package contract

The module must build as an Android library with the Kotlin serialization compiler plugin:

- `plugins { id("com.android.library"); alias(libs.plugins.kotlin.serialization) }`
- `namespace = "com.revenge.innertube"`
- `compileSdk = 37`, `defaultConfig.minSdk = 26`
- Java source/target compatibility 21, core-library desugaring enabled, `kotlin { jvmToolchain(21) }`
- Runtime dependencies used by this scope: `ktor-client-core`, `ktor-client-okhttp`,
  `ktor-client-content-negotiation`, `ktor-serialization-json`, `ktor-client-encoding`, `brotli`,
  `newpipeextractor` and `pipepipe-extractor` (both excluding `protobuf-java`), `project(":metadata")`.
- `testImplementation(libs.junit)` (JUnit 4).

Serialization only needs the `kotlinx-serialization-json` runtime transitively supplied by the Ktor
JSON plugin.

---

## 3. Shared resource model — `com.revenge.innertube.models`

All types in this section are plain Kotlin values (NOT `@Serializable`). They are the public output
of the parsers and are constructed by name throughout the app, so field names, order, types and
defaults are hard compatibility contracts.

### 3.1 `YTItem`

```
sealed class YTItem {
  abstract val id: String
  abstract val title: String
  abstract val thumbnail: String?
  abstract val explicit: Boolean
  abstract val shareLink: String
}
```

`thumbnail` is nullable on the base even though some subclasses declare it non-null.

### 3.2 `Artist` and `Album`

| FQCN | Fields (order, type, default) |
|---|---|
| `com.revenge.innertube.models.Artist` | `name: String`; `id: String?` |
| `com.revenge.innertube.models.Album` | `name: String`; `id: String` |

No validation or normalisation is performed by the constructors.

### 3.3 `SongItem : YTItem`

Constructor order and defaults (all named-argument call sites depend on them):

| # | Field | Type | Default |
|---|---|---|---|
| 1 | `id` | `String` (overrides) | required |
| 2 | `title` | `String` (overrides) | required |
| 3 | `artists` | `List<Artist>` | required |
| 4 | `album` | `Album?` | `null` |
| 5 | `duration` | `Int?` (seconds) | `null` |
| 6 | `musicVideoType` | `String?` | `null` |
| 7 | `chartPosition` | `Int?` | `null` |
| 8 | `chartChange` | `String?` | `null` |
| 9 | `thumbnail` | `String` (overrides; non-null) | required |
| 10 | `explicit` | `Boolean` (overrides) | `false` |
| 11 | `endpoint` | `WatchEndpoint?` | `null` |
| 12 | `setVideoId` | `String?` | `null` |
| 13 | `libraryAddToken` | `String?` | `null` |
| 14 | `libraryRemoveToken` | `String?` | `null` |
| 15 | `historyRemoveToken` | `String?` | `null` |
| 16 | `publishedText` | `String?` (localised date) | `null` |
| 17 | `playedPercent` | `Int?` (0..100) | `null` |
| 18 | `description` | `String?` | `null` |

Computed (read-only) properties:

- `isVideoSong`: `musicVideoType != null && musicVideoType != "MUSIC_VIDEO_TYPE_ATV"`.
- `isPodcastEpisode`: `musicVideoType == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"`.
- `shareLink`: `"https://music.youtube.com/watch?v=$id"`.

`artists` is non-null and may be empty. `thumbnail` is non-null; a parser that cannot find a
thumbnail must drop the row rather than synthesise one (see Section 8).

### 3.4 `AlbumItem : YTItem`

| # | Field | Type | Default |
|---|---|---|---|
| 1 | `browseId` | `String` | required |
| 2 | `playlistId` | `String` | required |
| 3 | `id` | `String` (overrides) | `browseId` |
| 4 | `title` | `String` (overrides) | required |
| 5 | `artists` | `List<Artist>?` (nullable list) | required |
| 6 | `year` | `Int?` | `null` |
| 7 | `thumbnail` | `String` (overrides; non-null) | required |
| 8 | `explicit` | `Boolean` (overrides) | `false` |
| 9 | `description` | `String?` | `null` |

`id` defaults to `browseId`. `shareLink` = `"https://music.youtube.com/playlist?list=$playlistId"`.

### 3.5 `PlaylistItem : YTItem`

| # | Field | Type | Default |
|---|---|---|---|
| 1 | `id` | `String` (overrides) | required |
| 2 | `title` | `String` (overrides) | required |
| 3 | `author` | `Artist?` | required |
| 4 | `songCountText` | `String?` | required |
| 5 | `thumbnail` | `String?` (overrides; nullable) | required |
| 6 | `playEndpoint` | `WatchEndpoint?` | required |
| 7 | `shuffleEndpoint` | `WatchEndpoint?` | required |
| 8 | `radioEndpoint` | `WatchEndpoint?` | required |
| 9 | `isEditable` | `Boolean` | `false` |
| 10 | `isPodcast` | `Boolean` | `false` |

`explicit` is always `false`. `shareLink` = `"https://music.youtube.com/playlist?list=$id"`.
`isPodcast = true` means `id` is a podcast show's playlist id and its entries are episodes.

### 3.6 `ArtistItem : YTItem`

| # | Field | Type | Default |
|---|---|---|---|
| 1 | `id` | `String` (overrides) | required |
| 2 | `title` | `String` (overrides) | required |
| 3 | `thumbnail` | `String?` (overrides) | required |
| 4 | `channelId` | `String?` | `null` |
| 5 | `playEndpoint` | `WatchEndpoint?` | `null` |
| 6 | `shuffleEndpoint` | `WatchEndpoint?` | required |
| 7 | `radioEndpoint` | `WatchEndpoint?` | required |

`explicit` is always `false`. `shareLink` = `"https://music.youtube.com/channel/$id"`.

### 3.7 List extension contracts (`YTItem.kt`)

| Signature | Semantics |
|---|---|
| `fun <T : YTItem> List<T>.filterExplicit(enabled: Boolean = true): List<T>` | When `enabled`, removes items with `explicit == true`; otherwise returns the same list instance. |
| `fun <T : YTItem> List<T>.filterVideoSongs(disableVideos: Boolean = false): List<T>` | When `disableVideos`, removes `SongItem`s with `isVideoSong == true` **except** podcast episodes. |
| `fun <T : YTItem> List<T>.filterPodcasts(): List<T>` | Removes podcast-episode `SongItem`s and `PlaylistItem`s with `isPodcast`. |
| `fun <T : YTItem> List<T>.filterYoutubeShorts(enabled: Boolean = false): List<T>` | When `enabled`, removes `PlaylistItem`s whose `id` starts with `"SS"`. |
| `fun <T : YTItem> List<T>.filterBlockedArtists(blockedArtists: Set<String> = YouTube.blockedArtists): List<T>` | No-op for empty set. Each entry is either `id` or `id||name`; matches `ArtistItem.id`, `ArtistItem.channelId`, or `title` for artists; any `SongItem.artists`; `AlbumItem.artists`; `PlaylistItem.author`. Matching by name or id. |
| `fun <T : YTItem> List<T>.filterDislikedSongs(dislikedSongs: Set<String> = YouTube.dislikedSongs): List<T>` | No-op for empty set. Removes `SongItem`s whose `id` equals the part of an entry before `||`. |

The two default arguments reference `com.revenge.innertube.YouTube.blockedArtists` and
`.dislikedSongs` (declared as `var … : Set<String> = emptySet()`). That cross-package reference is
part of the compile contract; keep the property names.

### 3.8 Other app-facing values

| FQCN | Shape |
|---|---|
| `com.revenge.innertube.models.AccountInfo` | `name: String`; `email: String?`; `channelHandle: String?`; `thumbnailUrl: String?` |
| `com.revenge.innertube.models.MediaInfo` | `videoId: String`; `title: String? = null`; `author: String? = null`; `authorId: String? = null`; `authorThumbnail: String? = null`; `description: String? = null`; `uploadDate: String? = null`; `subscribers: String? = null`; `viewCount: Int? = null`; `like: Int? = null`; `dislike: Int? = null` |
| `com.revenge.innertube.models.TasteSignals` (`@Serializable`) | `videoId: String`; `genres: List<String> = emptyList()`; `moodTags: List<String> = emptyList()` |
| `com.revenge.innertube.models.SearchSuggestions` | `queries: List<String>`; `recommendedItems: List<YTItem>` |
| `com.revenge.innertube.models.YouTubeLocale` (`@Serializable`) | `gl: String`; `hl: String` |
| `com.revenge.innertube.models.IpVersion` | enum: `AUTO`, `IPV4`, `IPV6` |
| `com.revenge.innertube.models.MetadataExtensions` | `SongItem.cleanedMetadata: CleanedMetadata`; `SongItem.displayTitle: String`; `SongItem.cleanArtistName: String` (see Section 11). |

---

## 4. Endpoints and navigation

### 4.1 `com.revenge.innertube.models.Endpoint`

`@Serializable sealed class Endpoint` — marker base. A `NavigationEndpoint` never decodes into an
`Endpoint` directly; it carries one nullable property per concrete endpoint and exposes the first
present one.

### 4.2 Concrete endpoints (all `@Serializable`)

| FQCN | Fields |
|---|---|
| `WatchEndpoint` (`: Endpoint`) | `videoId: String? = null`; `playlistId: String? = null`; `playlistSetVideoId: String? = null`; `params: String? = null`; `index: Int? = null`; `watchEndpointMusicSupportedConfigs: WatchEndpointMusicSupportedConfigs? = null` |
| `WatchEndpoint.WatchEndpointMusicSupportedConfigs` | `watchEndpointMusicConfig: WatchEndpointMusicConfig` (required) |
| `WatchEndpoint….WatchEndpointMusicConfig` | `musicVideoType: String` (required) |
| `BrowseEndpoint` (`: Endpoint`) | `browseId: String` (required); `params: String? = null`; `browseEndpointContextSupportedConfigs: BrowseEndpointContextSupportedConfigs? = null` |
| `BrowseEndpoint….BrowseEndpointContextSupportedConfigs` | `browseEndpointContextMusicConfig: BrowseEndpointContextMusicConfig` |
| `BrowseEndpoint….BrowseEndpointContextMusicConfig` | `pageType: String` |
| `SearchEndpoint` (`: Endpoint`) | `params: String?`; `query: String` |
| `FeedbackEndpoint` (`: Endpoint`) | `feedbackToken: String` |
| `QueueAddEndpoint` (`: Endpoint`) | `queueInsertPosition: String`; `queueTarget: QueueTarget`; nested `QueueTarget(videoId: String? = null, playlistId: String? = null)` |
| `ShareEntityEndpoint` (`: Endpoint`) | `serializedShareEntity: String` |
| `DefaultServiceEndpoint` (`: Endpoint`) | `var subscribeEndpoint: SubscribeEndpoint?`; `var feedbackEndpoint: FeedbackEndpoint?`; nested `SubscribeEndpoint(channelIds: List<String>, params: String? = null)` |
| `ToggledServiceEndpoint` (`: Endpoint`) | `var feedbackEndpoint: FeedbackEndpoint?` |

Constants (wire string tokens; values must be exactly these):

`WatchEndpointMusicConfig`: `MUSIC_VIDEO_TYPE_OMV`, `MUSIC_VIDEO_TYPE_UGC`,
`MUSIC_VIDEO_TYPE_ATV`, `MUSIC_VIDEO_TYPE_PODCAST_EPISODE` — values equal their names.

`BrowseEndpointContextMusicConfig`: `MUSIC_PAGE_TYPE_ALBUM`, `MUSIC_PAGE_TYPE_AUDIOBOOK`,
`MUSIC_PAGE_TYPE_PLAYLIST`, `MUSIC_PAGE_TYPE_ARTIST`, `MUSIC_PAGE_TYPE_LIBRARY_ARTIST`,
`MUSIC_PAGE_TYPE_USER_CHANNEL`, `MUSIC_PAGE_TYPE_TRACK_LYRICS`,
`MUSIC_PAGE_TYPE_TRACK_RELATED`, `MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE`,
`MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE` — values equal their names.

Package-level constant: `const val PODCAST_BROWSE_PREFIX = "MPSP"`.

Computed properties on `BrowseEndpoint`:

- `isArtistEndpoint`: page type is `MUSIC_PAGE_TYPE_ARTIST`.
- `isAlbumEndpoint`: page type is `MUSIC_PAGE_TYPE_ALBUM` **or** `MUSIC_PAGE_TYPE_AUDIOBOOK`.
- `isPlaylistEndpoint`: page type is `MUSIC_PAGE_TYPE_PLAYLIST`.
- `isPodcastEndpoint`: page type is `MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE` **or** `browseId`
  starts with `"MPSP"`.

### 4.3 `com.revenge.innertube.models.NavigationEndpoint`

`@Serializable`. All properties nullable with `null` defaults:

`watchEndpoint: WatchEndpoint?`, `watchPlaylistEndpoint: WatchEndpoint?`, `browseEndpoint:
BrowseEndpoint?`, `searchEndpoint: SearchEndpoint?`, `queueAddEndpoint: QueueAddEndpoint?`,
`shareEntityEndpoint: ShareEntityEndpoint?`, `feedbackEndpoint: FeedbackEndpoint?`,
`urlEndpoint: UrlEndpoint?`, `continuationCommand: ContinuationCommand?` (nested `ContinuationCommand
(token: String? = null)`).

Computed:

- `endpoint: Endpoint?` — first non-null, in this exact precedence: `watchEndpoint`,
  `watchPlaylistEndpoint`, `browseEndpoint`, `searchEndpoint`, `queueAddEndpoint`,
  `shareEntityEndpoint`. It deliberately does **not** consider feedback, URL or continuation.
- `anyWatchEndpoint: WatchEndpoint?` — `watchEndpoint ?: watchPlaylistEndpoint`.
- `musicVideoType: String?` — from `anyWatchEndpoint.watchEndpointMusicSupportedConfigs.
  watchEndpointMusicConfig.musicVideoType`.

---

## 5. Serialization contract (applies to every `@Serializable` DTO)

### 5.1 Decoder configuration

The transport is created with:

```
Json {
  ignoreUnknownKeys = true
  explicitNulls = false
  encodeDefaults = true
}
```

A conforming implementation must behave identically when given this configuration; tests decode
with the same three flags. Consequences:

- Unknown JSON object keys are ignored at every level.
- A missing key for a **nullable** property (with or without a Kotlin default) decodes as `null`.
- A missing key for a **non-null** property that has a default uses the default.
- A missing key for a **non-null** property without a default raises
  `kotlinx.serialization.MissingFieldException` (a `SerializationException`). This propagates out of
  `decodeFromString` and is caught by the caller's `runCatching`.
- Explicit JSON `null` for a non-null property raises `SerializationException`.
- A value of the wrong JSON type raises `SerializationException`.
- Polymorphic `Endpoint` is never decoded polymorphically: use `NavigationEndpoint`'s flat shape.

### 5.2 Aliases and explicit wire names

These are the ONLY deviations from "property name == wire name". They must be reproduced.

| Owner | Kotlin property | Accepted wire key(s) | Mechanism |
|---|---|---|---|
| `Continuation` | `nextContinuationData` | `nextContinuationData`, `nextRadioContinuationData` | `@JsonNames("nextContinuationData","nextRadioContinuationData")` |
| `MusicResponsiveListItemRenderer.FlexColumn` | `musicResponsiveListItemFlexColumnRenderer` | `musicResponsiveListItemFlexColumnRenderer`, `musicResponsiveListItemFixedColumnRenderer` | `@JsonNames("musicResponsiveListItemFixedColumnRenderer")` |
| `ThumbnailRenderer` | `musicThumbnailRenderer` | `musicThumbnailRenderer`, `croppedSquareThumbnailRenderer` | `@JsonNames("croppedSquareThumbnailRenderer")` |
| `SectionListRenderer.Content` | `musicCarouselShelfRenderer` | `musicCarouselShelfRenderer`, `musicImmersiveCarouselShelfRenderer` | `@JsonNames("musicImmersiveCarouselShelfRenderer")` |
| `PlayerResponse` | `playbackTracking` | `playbackTracking` | `@SerialName("playbackTracking")` (explicit, same spelling) |
| `PlayerResponse.PlaybackTracking` | `videostatsPlaybackUrl`, `videostatsWatchtimeUrl`, `atrUrl` | same names; each child uses `baseUrl` | `@SerialName` on each |
| `YouTubeDataPage` subtree | all listed properties | same camelCase names | `@SerialName` documenting the wire names |

Note on `ThumbnailRenderer`: with the decoder above, a payload containing only
`croppedSquareThumbnailRenderer` may populate the aliased `musicThumbnailRenderer` property, the
explicit `croppedSquareThumbnailRenderer` property, or both. Callers therefore always read it as
`musicThumbnailRenderer?.getThumbnailUrl() ?: croppedSquareThumbnailRenderer?.getThumbnailUrl()`.
Both access paths must remain available.

### 5.3 Optionality summary

- Envelope/root fields are mostly declared nullable-but-required (no default). Under the transport
  config they decode to `null` when absent; they are still required in constructor calls.
- List fields declared as `List<T>?` may be absent. `MusicShelfRenderer.contents`,
  `BrowseResponse.ContinuationContents.*.contents`, `GridRenderer.items`, `MusicPlaylistShelfRenderer.
  contents` and similar grant non-null defaults (`emptyList()`), so an absent key yields an empty
  list rather than `null`.
- `Runs.runs` and `Run.navigationEndpoint` are nullable-without-default; an absent key decodes as
  `null` only under `explicitNulls = false`. With a strict decoder they are required.

---

## 6. Serializable DTO catalogue

Wire names are identical to property names unless Section 5.2 lists an alias. Every class is a
Kotlin `data class` with the declared constructor order; every `@Serializable` class must keep the
annotation so nested serializers are generated.

### 6.1 Request context — `Context.kt`

`Context`:
`client: Client` (required), `thirdParty: ThirdParty? = null`, `request: Request = Request()`,
`user: User = User()`.

- `Client`: `clientName: String`; `clientVersion: String`; `osName: String? = null`; `osVersion:
  String? = null`; `deviceMake: String? = null`; `deviceModel: String? = null`; `androidSdkVersion:
  String? = null`; `gl: String`; `hl: String`; `visitorData: String?`.
- `ThirdParty`: `embedUrl: String`.
- `Request`: `internalExperimentFlags: Array<String> = emptyArray()`; `useSsl: Boolean = true`.
- `User`: `lockedSafetyMode: Boolean = false`; `onBehalfOfUser: String? = null`.

`YouTubeClient.toContext(locale: YouTubeLocale, visitorData: String?, dataSyncId: String?): Context`
builds a `Context` from the client identity: copies `clientName/clientVersion/osName/osVersion/
deviceMake/deviceModel/androidSdkVersion`, sets `gl = locale.gl`, `hl = locale.hl`, `visitorData =
visitorData`, and sets `user.onBehalfOfUser = dataSyncId` only when `loginSupported` is true
(otherwise `null`). All `Request`/`User`/`ThirdParty` values other than that use their defaults.

### 6.2 Client identities — `YouTubeClient.kt`

`@Serializable data class YouTubeClient` fields (defaults shown):

`clientName: String`, `clientVersion: String`, `clientId: String`, `userAgent: String`,
`osName: String? = null`, `osVersion: String? = null`, `deviceMake: String? = null`,
`deviceModel: String? = null`, `androidSdkVersion: String? = null`, `buildId: String? = null`,
`cronetVersion: String? = null`, `packageName: String? = null`, `friendlyName: String? = null`,
`loginSupported: Boolean = false`, `loginRequired: Boolean = false`,
`useSignatureTimestamp: Boolean = false`, `isEmbedded: Boolean = false`,
`useWebPoTokens: Boolean = false`.

Companion constants (exact values are wire/data contract):

- `USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"`
- `ORIGIN_YOUTUBE_MUSIC = "https://music.youtube.com"`
- `REFERER_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/"`
- `API_URL_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/youtubei/v1/"`

Predefined instances (all values must match; callers import them by name):
`WEB`, `WEB_REMIX`, `WEB_CREATOR`, `TVHTML5`, `TVHTML5_SIMPLY_EMBEDDED_PLAYER`, `IOS`, `MOBILE`,
`ANDROID_NO_SDK`, `ANDROID_VR_NO_AUTH`, `ANDROID_VR_1_61_48`, `ANDROID_VR_1_65_10`,
`ANDROID_VR_1_43_32`, `ANDROID_CREATOR`, `VISIONOS`, `IPADOS`. Exact identity tuples (clientName,
clientVersion, clientId, userAgent, flags) are pinned in the baseline and are load-bearing for
YouTube bot-gating; reproduce them verbatim, including the VR 1.65.10 pin.

### 6.3 Continuations — `Continuation.kt`, `ContinuationItemRenderer.kt`

- `Continuation`: `nextContinuationData: NextContinuationData?` (aliases in Section 5.2);
  `NextContinuationData(continuation: String)`.
- `fun List<Continuation>.getContinuation(): String?` — token of the first element that has one,
  else `null`.
- `ContinuationItemRenderer`: `continuationEndpoint: ContinuationEndpoint?`; nested
  `ContinuationEndpoint(continuationCommand: ContinuationCommand?)`; nested
  `ContinuationCommand(token: String? = null)`.
- `fun List<MusicShelfRenderer.Content>.getContinuation(): String?` — token from the first content
  entry carrying a `continuationItemRenderer`, or `null` (declared in `MusicShelfRenderer.kt`).

### 6.4 Navigation/endpoints

See Sections 4.1–4.3.

### 6.5 Runs — `Runs.kt`

- `Runs`: `runs: List<Run>?`.
- `Run`: `text: String`; `navigationEndpoint: NavigationEndpoint?`.

Helper functions (pure, deterministic):

- `fun Runs?.extractCountText(): String?` — joins non-blank trimmed run texts, then finds the first
  human count value matching digits plus digits/space/comma/`.`/full-width punctuation and optional
  compact suffixes (`K M B T`, CJK `万 萬 億 亿 兆 千`, Korean `천 만 억`). If the joined string has
  no match, tries each run individually. Returns the matched text without a space before the suffix.
  Returns `null` when no digit-bearing match exists. Examples: `"1.2M views"` → `"1.2M"`;
  `"1,234 likes"` → `"1,234"`; `"no numbers here"` → `null`; `null` → `null`.
- `fun List<Run>.splitBySeparator(): List<List<Run>>` — splits on runs whose `text` is exactly
  `" • "`. Always returns at least one group (a trailing empty group is legal).
- `fun List<List<Run>>.clean(): List<List<Run>>` — drops the first group when its first run has no
  `navigationEndpoint` AND its text does not contain `&` or `,`; otherwise returns `this`.
- `fun List<Run>.oddElements(): List<Run>` — elements at even indices (0, 2, 4…).

### 6.6 Thumbnails — `Thumbnails.kt`, `ThumbnailRenderer.kt`

- `Thumbnails`: `thumbnails: List<Thumbnail>` (required).
- `Thumbnail`: `url: String`; `width: Int?`; `height: Int?`.
- `ThumbnailRenderer`: `musicThumbnailRenderer: MusicThumbnailRenderer?` (alias accepted),
  `musicAnimatedThumbnailRenderer: MusicAnimatedThumbnailRenderer?`,
  `croppedSquareThumbnailRenderer: MusicThumbnailRenderer?`.
  - `MusicThumbnailRenderer`: `thumbnail: Thumbnails`; `thumbnailCrop: String?`;
    `thumbnailScale: String?`. `getThumbnailUrl(): String?` returns the last thumbnail's `url`, or
    `null` when absent.
  - `MusicAnimatedThumbnailRenderer`: `animatedThumbnail: Thumbnails`;
    `backupRenderer: MusicThumbnailRenderer`.

### 6.7 Common layout DTOs

| FQCN | Fields |
|---|---|
| `Icon` | `iconType: String` |
| `Badges` | `musicInlineBadgeRenderer: MusicInlineBadgeRenderer?`; nested `MusicInlineBadgeRenderer(icon: Icon)` |
| `UrlEndpoint` | `url: String? = null`; `target: String? = null` |
| `ResponseContext` | `visitorData: String?`; `serviceTrackingParams: List<ServiceTrackingParam>?`; nested `ServiceTrackingParam(params: List<Param>, service: String)`; `Param(key: String, value: String)` |
| `Tabs` | `tabs: List<Tab>`; `Tab(tabRenderer: TabRenderer)`; `TabRenderer(title: String?, content: Content?, endpoint: NavigationEndpoint?)`; `Content(sectionListRenderer: SectionListRenderer?, musicQueueRenderer: MusicQueueRenderer?)` |
| `TwoColumnBrowseResultsRenderer` | `secondaryContents: SecondaryContents?`; `tabs: List<Tabs.Tab>?`; nested `SecondaryContents(sectionListRenderer: SectionListRenderer?)`; nested `SectionListRenderer(contents: List<Content>?, continuations: List<Continuation>?)`; `Content(musicPlaylistShelfRenderer: MusicPlaylistShelfRenderer?, musicShelfRenderer: MusicShelfRenderer?)` |
| `GridRenderer` | `header: Header?`; `items: List<Item>`; `continuations: List<Continuation>?`; `Header(gridHeaderRenderer: GridHeaderRenderer)`; `GridHeaderRenderer(title: Runs)`; `Item(musicNavigationButtonRenderer: MusicNavigationButtonRenderer?, musicTwoRowItemRenderer: MusicTwoRowItemRenderer?)` |
| `SectionListRenderer` | `header: Header?`; `contents: List<Content>?`; `continuations: List<Continuation>?`; `Header(chipCloudRenderer: ChipCloudRenderer?)`; `ChipCloudRenderer(chips: List<Chip>)`; `Chip(chipCloudChipRenderer: ChipCloudChipRenderer)`; `ChipCloudChipRenderer(isSelected: Boolean = false, navigationEndpoint: NavigationEndpoint = NavigationEndpoint(), onDeselectedCommand: NavigationEndpoint? = null, text: Runs?, uniqueId: String?)`; `Content` fields below |
| `SectionListRenderer.Content` | `musicCarouselShelfRenderer` (alias `musicImmersiveCarouselShelfRenderer`), `musicShelfRenderer`, `musicCardShelfRenderer`, `musicPlaylistShelfRenderer`, `musicDescriptionShelfRenderer`, `musicResponsiveHeaderRenderer`, `musicEditablePlaylistDetailHeaderRenderer`, `gridRenderer`, `itemSectionRenderer` — all nullable, default `null` |
| `ItemSectionRenderer` | `contents: List<Content>?`; `Content(musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?)` |
| `YouTubeDataPage` | desktop watch-page shape; nested path: `contents.twoColumnWatchNextResults.results.results.contents[]`; each content may carry `videoPrimaryInfoRenderer`, `videoSecondaryInfoRenderer`, `itemSectionRenderer`, `continuationItemRenderer`. Sub-shapes: primary `title.runs[].text`, `viewCount.videoViewCountRenderer.viewCount.simpleText`, `dateText.simpleText`; secondary `owner.videoOwnerRenderer.{thumbnail.thumbnails[], subscriberCountText.simpleText, title.runs[].text, navigationEndpoint}`, `attributedDescription.content`; item section `contents[].continuationItemRenderer.{trigger, continuationEndpoint.continuationCommand.{token, request}}`, `sectionIdentifier`. All nullable with `null` defaults. |

### 6.8 List and tile renderers

**`MusicResponsiveListItemRenderer`** — `badges: List<Badges>?`; `fixedColumns:
List<FlexColumn>?`; `flexColumns: List<FlexColumn>` (required); `thumbnail: ThumbnailRenderer?`;
`menu: Menu?`; `playlistItemData: PlaylistItemData?`; `overlay: Overlay?`;
`navigationEndpoint: NavigationEndpoint?`.

- `FlexColumn`: `musicResponsiveListItemFlexColumnRenderer: MusicResponsiveListItemFlexColumnRenderer`
  (alias `musicResponsiveListItemFixedColumnRenderer`); nested `…Renderer(text: Runs?)`.
- `PlaylistItemData`: `playlistSetVideoId: String?`; `videoId: String`.
- `Overlay`: `musicItemThumbnailOverlayRenderer: MusicItemThumbnailOverlayRenderer`; nested
  `content: Content`; nested `musicPlayButtonRenderer: MusicPlayButtonRenderer`; nested
  `playNavigationEndpoint: NavigationEndpoint?`.

Computed:

- `isSong`: `navigationEndpoint == null` OR it has `watchEndpoint` OR `watchPlaylistEndpoint`.
- `isPlaylist`: page type `MUSIC_PAGE_TYPE_PLAYLIST`.
- `isAlbum`: page type `MUSIC_PAGE_TYPE_ALBUM` or `MUSIC_PAGE_TYPE_AUDIOBOOK`.
- `isArtist`: page type `MUSIC_PAGE_TYPE_ARTIST` or `MUSIC_PAGE_TYPE_LIBRARY_ARTIST`.
- `musicVideoType`: overlay play endpoint's `musicVideoType` if present, else
  `navigationEndpoint.musicVideoType`.

**`MusicTwoRowItemRenderer`** — `title: Runs` (required); `subtitle: Runs?`;
`subtitleBadges: List<Badges>?`; `menu: Menu?`; `thumbnailRenderer: ThumbnailRenderer` (required);
`navigationEndpoint: NavigationEndpoint` (required); `thumbnailOverlay:
MusicResponsiveListItemRenderer.Overlay?`.

Computed: `isSong` = `navigationEndpoint.endpoint is WatchEndpoint`; `isPlaylist`/`isAlbum`/`isArtist`
as above (`isArtist` only `MUSIC_PAGE_TYPE_ARTIST`); `musicVideoType` = overlay play endpoint type
else `navigationEndpoint.musicVideoType`.

**`MusicShelfRenderer`** — `title: Runs?`; `contents: List<Content>?`; `continuations:
List<Continuation>?`; `bottomEndpoint: NavigationEndpoint?`; `moreContentButton: Button?`.
`Content`: `musicResponsiveListItemRenderer?`, `continuationItemRenderer?`,
`musicMultiRowListItemRenderer? = null`.
`fun List<Content>.getItems(): List<MusicResponsiveListItemRenderer>` keeps only non-null
`musicResponsiveListItemRenderer`s, preserving order.

**`MusicCarouselShelfRenderer`** — `header: Header?`; `contents: List<Content>` (required);
`itemSize: String` (required); `numItemsPerColumn: Int?`.
`Header(musicCarouselShelfBasicHeaderRenderer: MusicCarouselShelfBasicHeaderRenderer)`; nested
`strapline: Runs?`, `title: Runs`, `thumbnail: ThumbnailRenderer?`, `moreContentButton: Button?`.
`Content`: `musicTwoRowItemRenderer?`, `musicResponsiveListItemRenderer?`,
`musicNavigationButtonRenderer?`, `musicShuffleRenderer?`, `musicMultiRowListItemRenderer? = null`.

**`MusicCardShelfRenderer`** — `title: Runs`; `subtitle: Runs`; `thumbnail: ThumbnailRenderer`;
`header: Header?`; `contents: List<Content>?`; `buttons: List<Button>`; `onTap:
NavigationEndpoint`; `subtitleBadges: List<Badges>?`. `Header(musicCardShelfHeaderBasicRenderer)` with
`title: Runs`. `Content(musicResponsiveListItemRenderer?)`.

**`MusicPlaylistShelfRenderer`** — `playlistId: String?`; `contents:
List<MusicShelfRenderer.Content> = emptyList()`; `collapsedItemCount: Int? = null`;
`continuations: List<Continuation>? = null`.

### 6.9 Menus, buttons, headers, misc renderers

| FQCN | Fields |
|---|---|
| `Menu` | `menuRenderer: MenuRenderer` (required) |
| `Menu.MenuRenderer` | `items: List<Item>?`; `topLevelButtons: List<TopLevelButton>?` |
| `Menu.MenuRenderer.Item` | `menuNavigationItemRenderer?`; `menuServiceItemRenderer?`; `toggleMenuServiceItemRenderer?` |
| `Item.MenuNavigationItemRenderer` | `text: Runs`; `icon: Icon`; `navigationEndpoint: NavigationEndpoint` |
| `Item.MenuServiceItemRenderer` | `text: Runs`; `icon: Icon`; `serviceEndpoint: NavigationEndpoint` |
| `Item.ToggleMenuServiceRenderer` | `defaultIcon: Icon`; `defaultServiceEndpoint: DefaultServiceEndpoint`; `toggledServiceEndpoint: ToggledServiceEndpoint?` |
| `Menu.MenuRenderer.TopLevelButton` | `buttonRenderer: ButtonRenderer?`; `ButtonRenderer(icon: Icon, navigationEndpoint: NavigationEndpoint)` |
| `Button` | `buttonRenderer: ButtonRenderer`; nested `text: Runs`, `navigationEndpoint: NavigationEndpoint?`, `command: NavigationEndpoint?`, `icon: Icon?` |
| `SubscriptionButton` | `subscribeButtonRenderer: SubscribeButtonRenderer`; nested `subscribed: Boolean`, `channelId: String`, `longSubscriberCountText: Runs? = null`, `shortSubscriberCountText: Runs? = null` |
| `MusicResponsiveHeaderRenderer` | `thumbnail: ThumbnailRenderer?`; `buttons: List<Button>`; `title: Runs`; `subtitle: Runs`; `secondSubtitle: Runs?`; `straplineTextOne: Runs?`; `description: Runs? = null`; nested `Button(musicPlayButtonRenderer?, menuRenderer?)`, `MusicPlayButtonRenderer(playNavigationEndpoint: NavigationEndpoint?)` |
| `MusicEditablePlaylistDetailHeaderRenderer` | `header: Header`; `editHeader: EditHeader`; nested `Header(musicDetailHeaderRenderer?, musicResponsiveHeaderRenderer?)`; `EditHeader(musicPlaylistEditHeaderRenderer?)` |
| `MusicDetailHeaderRenderer` | `title: Runs`; `subtitle: Runs`; `secondSubtitle: Runs`; `description: Runs?`; `thumbnail: ThumbnailRenderer`; `menu: Menu` |
| `MusicPlaylistEditHeaderRenderer` | `editTitle: Runs?` |
| `MusicDescriptionShelfRenderer` | `header: Runs?`; `subheader: Runs?`; `description: Runs`; `footer: Runs?` |
| `MusicNavigationButtonRenderer` | `buttonText: Runs`; `solid: Solid?`; `iconStyle: IconStyle?`; `clickCommand: NavigationEndpoint`; nested `Solid(leftStripeColor: Long)`, `IconStyle(icon: Icon)` |
| `MusicMultiRowListItemRenderer` | `title: Runs?`; `secondTitle: Runs? = null`; `subtitle: Runs? = null`; `description: Runs? = null`; `thumbnail: ThumbnailRenderer?`; `onTap: NavigationEndpoint?`; `playbackProgress: PlaybackProgress? = null`; nested `PlaybackProgress(musicPlaybackProgressRenderer?)`, `MusicPlaybackProgressRenderer(playbackProgressPercentage: Int? = null, durationText: Runs? = null)` |
| `MusicMultiRowImageItemRenderer` | `title: Runs`; `subtitle: Runs`; `thumbnail: ThumbnailRenderer`; `onTap: NavigationEndpoint` |
| `MusicShuffleRenderer` | `title: Runs?`; `subtitle: Runs?`; `thumbnail: ThumbnailRenderer?`; `getThumbnailUrl(): String?` from `musicThumbnailRenderer` then `croppedSquareThumbnailRenderer`, last thumbnail url |
| `MusicQueueRenderer` | `content: Content?`; `header: Header?`; nested `Content(playlistPanelRenderer: PlaylistPanelRenderer)`, `Header(musicQueueHeaderRenderer?)`, `MusicQueueHeaderRenderer(title: Runs?, subtitle: Runs?)` |
| `PlaylistPanelRenderer` | `title: String?`; `titleText: Runs?`; `shortBylineText: Runs?`; `contents: List<Content>`; `isInfinite: Boolean?`; `numItemsToShow: Int?`; `playlistId: String?`; `continuations: List<Continuation>?`; `Content(playlistPanelVideoRenderer?, automixPreviewVideoRenderer?)` |
| `PlaylistPanelVideoRenderer` | `title: Runs?`; `lengthText: Runs?`; `longBylineText: Runs?`; `shortBylineText: Runs?`; `badges: List<Badges>?`; `videoId: String?`; `playlistSetVideoId: String?`; `selected: Boolean`; `thumbnail: Thumbnails`; `unplayableText: Runs?`; `menu: Menu?`; `navigationEndpoint: NavigationEndpoint` |
| `AutomixPreviewVideoRenderer` | `content: Content`; `Content(automixPlaylistVideoRenderer)`; `AutomixPlaylistVideoRenderer(navigationEndpoint: NavigationEndpoint)` |
| `SearchSuggestionsSectionRenderer` | `contents: List<Content>`; `Content(searchSuggestionRenderer?, musicResponsiveListItemRenderer?)`; `SearchSuggestionRenderer(suggestion: Runs, navigationEndpoint: NavigationEndpoint)` |

### 6.10 Response envelopes — `models.response`

| FQCN | Fields |
|---|---|
| `BrowseResponse` | `contents: Contents?`; `continuationContents: ContinuationContents?`; `onResponseReceivedActions: List<ResponseAction>?`; `header: Header?`; `microformat: Microformat?`; `responseContext: ResponseContext`; `background: ThumbnailRenderer?` |
| `BrowseResponse.Contents` | `singleColumnBrowseResultsRenderer: Tabs?`; `sectionListRenderer: SectionListRenderer?`; `twoColumnBrowseResultsRenderer: TwoColumnBrowseResultsRenderer?` |
| `BrowseResponse.ContinuationContents` | `sectionListContinuation: SectionListContinuation?`; `musicPlaylistShelfContinuation: MusicPlaylistShelfContinuation?`; `gridContinuation: GridContinuation?`; `musicShelfContinuation: MusicShelfRenderer?` |
| `SectionListContinuation` | `contents: List<SectionListRenderer.Content> = emptyList()`; `continuations: List<Continuation>?` |
| `MusicPlaylistShelfContinuation` | `contents: List<MusicShelfRenderer.Content> = emptyList()`; `continuations: List<Continuation>?` |
| `GridContinuation` | `items: List<GridRenderer.Item> = emptyList()`; `continuations: List<Continuation>?` |
| `BrowseResponse.ResponseAction` | `appendContinuationItemsAction: ContinuationItems?`; nested `continuationItems: List<MusicShelfRenderer.Content>?` |
| `BrowseResponse.Header` | `musicImmersiveHeaderRenderer?`; `musicDetailHeaderRenderer?`; `musicEditablePlaylistDetailHeaderRenderer?`; `musicVisualHeaderRenderer?`; `musicHeaderRenderer?` |
| `MusicImmersiveHeaderRenderer` | `title: Runs`; `description: Runs?`; `thumbnail: ThumbnailRenderer?`; `playButton: Button?`; `startRadioButton: Button?`; `subscriptionButton: SubscriptionButton?`; `menu: Menu`; `subscriptionButton2: SubscriptionButton2?`; `monthlyListenerCount: Runs? = null`; nested SubscriptionButton2 shape `subscribeButtonRenderer.subscriberCountWithSubscribeText: Runs?` |
| `MusicVisualHeaderRenderer` | `title: Runs`; `foregroundThumbnail: ThumbnailRenderer`; `thumbnail: ThumbnailRenderer?` |
| `BrowseResponse.Header.Buttons` | `menuRenderer: Menu.MenuRenderer?` |
| `MusicHeaderRenderer` | `buttons: List<Buttons>?`; `title: Runs?`; `thumbnail: MusicThumbnailRenderer?`; `subtitle: Runs?`; `secondSubtitle: Runs?`; `straplineTextOne: Runs?`; `straplineThumbnail: MusicThumbnailRenderer?` |
| `BrowseResponse.Microformat` | `microformatDataRenderer?`; nested `urlCanonical: String?` |
| `PlayerResponse` | `responseContext: ResponseContext`; `playabilityStatus: PlayabilityStatus`; `playerConfig: PlayerConfig?`; `streamingData: StreamingData?`; `videoDetails: VideoDetails?`; `playbackTracking: PlaybackTracking?` |
| `PlayerResponse.PlayabilityStatus` | `status: String`; `reason: String?` |
| `PlayerConfig` | `audioConfig: AudioConfig`; `AudioConfig(loudnessDb: Double?, perceptualLoudnessDb: Double?)` |
| `PlayerResponse.StreamingData` | `formats: List<Format>?`; `adaptiveFormats: List<Format>`; `expiresInSeconds: Int` |
| `StreamingData.Format` | `itag: Int`; `url: String?`; `mimeType: String`; `bitrate: Int`; `width: Int?`; `height: Int?`; `contentLength: Long?`; `quality: String`; `fps: Int?`; `qualityLabel: String?`; `averageBitrate: Int?`; `audioQuality: String?`; `approxDurationMs: String?`; `audioSampleRate: Int?`; `audioChannels: Int?`; `loudnessDb: Double?`; `lastModified: Long?`; `signatureCipher: String?`; `cipher: String?`; `audioTrack: AudioTrack?`; computed `isAudio = width == null`, `isOriginal = audioTrack?.isAutoDubbed == null`; `AudioTrack(displayName: String?, id: String?, isAutoDubbed: Boolean?)` |
| `PlayerResponse.VideoDetails` | `videoId: String`; `title: String?`; `author: String?`; `channelId: String`; `lengthSeconds: String`; `musicVideoType: String?`; `viewCount: String?`; `thumbnail: Thumbnails` |
| `PlayerResponse.PlaybackTracking` | `videostatsPlaybackUrl`; `videostatsWatchtimeUrl`; `atrUrl`; each `baseUrl: String?` |
| `NextResponse` | `contents: Contents`; `engagementPanels: List<EngagementPanel>? = null`; `continuationContents: ContinuationContents?`; `currentVideoEndpoint: NavigationEndpoint?` |
| `NextResponse.Contents` | `singleColumnMusicWatchNextResultsRenderer?`; `twoColumnWatchNextResults?` (the `YouTubeDataPage` shape) |
| `SingleColumnMusicWatchNextResultsRenderer` | `tabbedRenderer?`; `TabbedRenderer(watchNextTabbedResultsRenderer?)`; `WatchNextTabbedResultsRenderer(tabs: List<Tabs.Tab>)` |
| `NextResponse.EngagementPanel` | `engagementPanelSectionListRenderer?`; nested `panelIdentifier: String?`, `content?`; `content.sectionListRenderer.contents: List<YouTubeDataPage…Content>?` |
| `NextResponse.ContinuationContents` | `playlistPanelContinuation: PlaylistPanelRenderer` (required) |
| `SearchResponse` | `contents: Contents?`; `continuationContents: ContinuationContents?`; `Contents(tabbedSearchResultsRenderer: Tabs?)`; `ContinuationContents(musicShelfContinuation: MusicShelfContinuation)`; `MusicShelfContinuation(contents: List<Content>, continuations: List<Continuation>?)`; `Content(musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer)` |
| `GetTranscriptResponse` | `actions: List<Action>?`; nested `Action(updateEngagementPanelAction)`; `UpdateEngagementPanelAction(content)`; `Content(transcriptRenderer)`; `TranscriptRenderer(body)`; `Body(transcriptBodyRenderer)`; `TranscriptBodyRenderer(cueGroups: List<CueGroup>)`; `CueGroup(transcriptCueGroupRenderer)`; `TranscriptCueGroupRenderer(cues: List<Cue>)`; `Cue(transcriptCueRenderer)`; `TranscriptCueRenderer(cue: SimpleText, startOffsetMs: Long, durationMs: Long)`; `SimpleText(simpleText: String)` |
| `GetQueueResponse` | `queueDatas: List<QueueData>`; `QueueData(content: PlaylistPanelRenderer.Content)` |
| `GetSearchSuggestionsResponse` | `contents: List<Content>?`; `Content(searchSuggestionsSectionRenderer: SearchSuggestionsSectionRenderer)` |
| `ContinuationResponse` | `onResponseReceivedActions: List<ResponseAction>?`; nested `appendContinuationItemsAction.continuationItems: List<MusicShelfRenderer.Content>?` |
| `AccountMenuResponse` | `actions: List<Action>`; nested through `openPopupAction.popup.multiPageMenuRenderer.header.activeAccountHeaderRenderer`; that renderer has `accountName: Runs`, `email: Runs?`, `channelHandle: Runs?`, `accountPhoto: Thumbnails`, and `toAccountInfo(): AccountInfo` (first run text of each; `name` empty string when absent; `thumbnailUrl` = last photo url) |
| `AddItemYouTubePlaylistResponse` | `status: String`; `playlistEditResults: List<PlaylistEditResult>`; nested `playlistEditVideoAddedResultData.setVideoId` and `.videoId` |
| `CreatePlaylistResponse` | `playlistId: String` |
| `EditPlaylistResponse` | `newHeader: BrowseResponse.Header?` |
| `FeedbackResponse` | `feedbackResponses: List<Status>`; `Status(isProcessed: Boolean)` |
| `ImageUploadResponse` | `encryptedBlobId: String` |
| `ReturnYouTubeDislikeResponse` | `id: String? = null`; `dateCreated: String? = null`; `likes: Int? = null`; `dislikes: Int? = null`; `rating: Double? = null`; `viewCount: Int? = null`; `deleted: Boolean? = null` |

### 6.11 Comment DTOs — `models.comment.Comments`

Everything is `@Serializable`, every nested property nullable with `null` default unless noted:

- `CommentThreadRenderer`: `comment: Comment? = null`, `commentViewModel: CommentViewModelWrapper? =
  null`, `replies: Replies? = null`; `CommentViewModelWrapper(commentViewModel?)` with
  `commentViewModel(commentId?, commentKey?)`; `Comment(commentRenderer?)`;
  `Replies(commentRepliesRenderer?)`; `CommentRepliesRenderer(contents: List<Content>?,
  viewReplies: Button?, hideReplies: Button?)`; `Content(continuationItemRenderer?)`.
- `CommentRenderer`: `authorText: Runs? = null`, `authorThumbnail: Thumbnails? = null`,
  `contentText: Runs? = null`, `publishedTimeText: Runs? = null`, `authorEndpoint:
  NavigationEndpoint? = null`, `commentId: String? = null`, `voteCount: Runs? = null`,
  `voteStatus: String? = null`, `replyCount: Int? = null`.
- Comment-local `ContinuationItemRenderer`: `trigger: String? = null`,
  `continuationEndpoint: ContinuationEndpoint? = null`, `button: Button? = null`; nested
  `ContinuationEndpoint(clickTrackingParams: String? = null, continuationCommand?)`;
  `ContinuationCommand(token: String? = null)`.
- `CommentResponse`: `onResponseReceivedEndpoints: List<OnResponseReceivedEndpoint>? = null`,
  `frameworkUpdates: FrameworkUpdates? = null`.
  - `OnResponseReceivedEndpoint`: `reloadContinuationItemsCommand?`,
    `appendContinuationItemsAction?`; both expose `continuationItems: List<ContinuationItem>?`.
  - `ContinuationItem`: `commentThreadRenderer?`, `continuationItemRenderer?`, `commentRenderer?`.
  - `FrameworkUpdates.entityBatchUpdate.mutations[].payload` with `commentEntityPayload` (key,
    properties{commentId, content{content}, publishedTime, toolbarStateKey, toolbarSurfaceKey},
    author{displayName, avatarThumbnailUrl}, toolbar), `engagementToolbarStateEntityPayload`
    (key, likeState, heartState), `engagementToolbarSurfaceEntityPayload` (key, toolbar{likeCountNotliked,
    likeCountLiked, replyCount}).

### 6.12 Request bodies — `models.body.Bodies`

All `@Serializable`; field names are the POST JSON contract.

| FQCN | Fields |
|---|---|
| `AccountMenuBody` | `context: Context`; `deviceTheme: String = "DEVICE_THEME_SELECTED"`; `userInterfaceTheme: String = "USER_INTERFACE_THEME_DARK"` |
| `BrowseBody` | `context: Context`; `browseId: String?`; `params: String?`; `continuation: String?` |
| `CreatePlaylistBody` | `context: Context`; `title: String`; `privacyStatus: String = "PRIVATE"`; `videoIds: List<String>? = null`; nested `object PrivacyStatus { PRIVATE="PRIVATE"; PUBLIC="PUBLIC"; UNLISTED="UNLISTED" }` |
| `EditPlaylistBody` | `context: Context`; `playlistId: String`; `actions: List<Action>`; sealed `Action` family below |
| `EditPlaylistBody.Action.AddVideoAction` | `action: String = "ACTION_ADD_VIDEO"`; `addedVideoId: String` |
| `AddPlaylistAction` | `action = "ACTION_ADD_PLAYLIST"`; `addedFullListId: String` |
| `MoveVideoAction` | `action = "ACTION_MOVE_VIDEO_BEFORE"`; `setVideoId: String`; `movedSetVideoIdSuccessor: String?` |
| `RemoveVideoAction` | `action = "ACTION_REMOVE_VIDEO"`; `setVideoId: String`; `removedVideoId: String` |
| `RenamePlaylistAction` | `action = "ACTION_SET_PLAYLIST_NAME"`; `playlistName: String` |
| `SetCustomThumbnailAction` | `action = "ACTION_SET_CUSTOM_THUMBNAIL"`; `addedCustomThumbnail: AddedCustomThumbnail`; nested `AddedCustomThumbnail(imageKey: ImageKey = ImageKey("studio_square_thumbnail","PLAYLIST_IMAGE_TYPE_CUSTOM_THUMBNAIL"), playlistScottyEncryptedBlobId: String)`; `ImageKey(name: String, type: String)` |
| `RemoveCustomThumbnailAction` | `action = "ACTION_REMOVE_CUSTOM_THUMBNAIL"`; `deletedCustomThumbnail: DeletedCustomThumbnail = DeletedCustomThumbnail()`; nested defaults name/type as above |
| `FeedbackBody` | `context: Context`; `feedbackTokens: List<String>`; `isFeedbackTokenUnencrypted: Boolean = false`; `shouldMerge: Boolean = false` |
| `GetQueueBody` | `context: Context`; `videoIds: List<String>?`; `playlistId: String?` |
| `GetSearchSuggestionsBody` | `context: Context`; `input: String` |
| `GetTranscriptBody` | `context: Context`; `params: String` |
| `LikeBody` | `context: Context`; `target: Target`; sealed `Target` = `VideoTarget(videoId)`, `PlaylistTarget(playlistId)` |
| `NextBody` | `context: Context`; `videoId: String?`; `playlistId: String?`; `playlistSetVideoId: String?`; `index: Int?`; `params: String?`; `continuation: String?` |
| `PlayerBody` | `context: Context`; `videoId: String`; `playlistId: String?`; `playbackContext: PlaybackContext? = null`; `serviceIntegrityDimensions: ServiceIntegrityDimensions? = null`; `contentCheckOk: Boolean = true`; `racyCheckOk: Boolean = true`; nested `PlaybackContext(contentPlaybackContext)` with `ContentPlaybackContext(signatureTimestamp: Int)`; `ServiceIntegrityDimensions(poToken: String)` |
| `PlaylistDeleteBody` | `context: Context`; `playlistId: String` |
| `SearchBody` | `context: Context`; `query: String?`; `params: String?` |
| `SubscribeBody` | `channelIds: List<String>`; `context: Context` |

Package-level type alias: `typealias Action = EditPlaylistBody.Action` (kept for source
compatibility).

---

## 7. Page result types and parsers — `com.revenge.innertube.pages`

All page containers are immutable `data class`es. All parsers are stateless, synchronous and pure;
they never throw for expected missing optional metadata — they return `null` for an unparsable row,
and callers drop `null`s with `mapNotNull`. A thrown exception is limited to programmer error (e.g.
non-null assertion in `HistoryPage.fromMusicShelfRenderer`, documented below). Network/page
assembly is done by `YouTube` (out of scope).

### 7.0 `PageHelper`

`object PageHelper` with nested `data class LibraryFeedbackTokens(addToken: String?, removeToken:
String?)` and:

| Signature | Semantics |
|---|---|
| `isLibraryIcon(iconType: String?): Boolean` | `false` for `null`, `"KEEP"`, `"KEEP_OFF"`. Otherwise true when the type is in the add/saved sets (`LIBRARY_ADD`, `BOOKMARK_BORDER`, `LIBRARY_SAVED`, `BOOKMARK`, `LIBRARY_REMOVE`) or starts with `"LIBRARY_"`. |
| `isAddLibraryIcon(iconType: String?): Boolean` | true only for `LIBRARY_ADD`, `BOOKMARK_BORDER`. |
| `isSavedLibraryIcon(iconType: String?): Boolean` | true only for `LIBRARY_SAVED`, `BOOKMARK`, `LIBRARY_REMOVE`. |
| `extractRuns(columns: List<FlexColumn>, typeLike: String): List<Run>` | Concatenates, in column order then run order, runs whose endpoint music type contains `typeLike`. Endpoint type = watch endpoint `musicVideoType`, else browse endpoint `pageType`; runs with neither are skipped. |
| `extractLibraryTokensFromMenuItems(items: List<Menu.MenuRenderer.Item>?): LibraryFeedbackTokens` | `null` items → `(null, null)`. Iterates toggle menu items whose icon `isLibraryIcon`. For an add-icon entry the default feedback token is the add token and the toggled token the remove token; for a saved-icon entry the default is the remove token and the toggled the add token. First non-null wins per slot; missing endpoint/token leaves `null`. |
| `extractFeedbackToken(menu: Menu.MenuRenderer.Item.ToggleMenuServiceRenderer?, type: String): String?` | `null` menu → `null`. `LIBRARY_ADD` → default token when currently not-saved, else toggled. `LIBRARY_REMOVE`/`LIBRARY_SAVED` → toggled when not-saved, else default. Any other `type` → default when the icon equals `type`, else toggled. |

### 7.1 `BrowseResult`

`data class BrowseResult(title: String?, items: List<Item>)`, nested
`Item(title: String?, items: List<YTItem>)`. Filter helpers return `BrowseResult`:

- `filterBlockedArtists()`, `filterDislikedSongs()` — map every section's items.
- `filterExplicit(enabled: Boolean = true)`, `filterVideoSongs(disableVideos: Boolean = false)`,
  `filterYoutubeShorts(enabled: Boolean = false)` — when disabled, return `this` unchanged; when
  enabled, apply the corresponding `YTItem` list filter and drop sections that become empty.

### 7.2 `HomePage`

`data class HomePage(chips: List<Chip>?, sections: List<Section>, continuation: String? = null)`.

- `Chip(title: String, endpoint: BrowseEndpoint?, deselectEndPoint: BrowseEndpoint?)`.
  `Chip.fromChipCloudChipRenderer(renderer: SectionListRenderer.Header.ChipCloudRenderer.Chip):
  Chip?` — `null` when the chip has no text run; `endpoint` from the chip navigation endpoint's
  `browseEndpoint`; `deselectEndPoint` from `onDeselectedCommand?.browseEndpoint`.
- `Section(title: String, label: String?, thumbnail: String?, endpoint: BrowseEndpoint?, items:
  List<YTItem>, shuffle: Shuffle? = null, liveOnly: Boolean = false)`.
  - `val isStationListenTogether: Boolean` — true when `liveOnly`; else case-insensitive title /
    label checks: title contains `LISTEN TOGETHER`, or (label contains `STATION` and title contains
    `LISTEN TOGETHER`), or (label contains `LISTEN TOGETHER` and title contains `STATION`).
  - `Shuffle(title: String, thumbnail: String?)`.
  - `Section.fromMusicCarouselShelfRenderer(renderer): Section?` — requires a carousel basic
    header and a non-blank title run; otherwise `null`.
    - `thumbnail` resolution: header thumbnail → first `musicShuffleRenderer` thumbnail → first
      two-row item thumbnail (`musicThumbnailRenderer`, then `croppedSquareThumbnailRenderer`) →
      first multi-row item thumbnail.
    - `endpoint`: header more-content button's `browseEndpoint`.
    - `items`: for each content, two-row renderers (with podcast show/episode precedence, see
      Section 7.12), multi-row renderers as podcast episodes, responsive list renderers via
      `SearchSummaryPage`; if the resulting list is empty → `null`.
    - `liveOnly`: true only when all non-shuffle contents are two-row items with a watch videoId,
      not a podcast episode, and at least one subtitle badge has no `musicInlineBadgeRenderer`.
    - `shuffle`: first `musicShuffleRenderer`; title is its first run cleaned, or the literal
      `"Shuffle"`; thumbnail via `getThumbnailUrl()`.
- `filterBlockedArtists()`, `filterDislikedSongs()`, `filterExplicit(enabled)`,
  `filterVideoSongs(disableVideos)` — same "disabled returns this" semantics as `BrowseResult`, but
  empty sections are **not** removed.

### 7.3 `AlbumPage`

`data class AlbumPage(album: AlbumItem, songs: List<SongItem>, otherVersions: List<AlbumItem>,
releasesForYou: List<AlbumItem> = emptyList(), description: String? = null, descriptionRuns:
List<Run>? = null)`.

Companion functions (all pure):

- `getPlaylistId(response: BrowseResponse): String?` — `microformat.microformatDataRenderer.
  urlCanonical` substring after the last `=`, else the first top-level button's
  `watchPlaylistEndpoint.playlistId`.
- `getTitle(response): String?` — first run of the responsive header title, else
  `musicDetailHeaderRenderer.title`; `null` if no header.
- `getYear(response): Int?` — last run of the responsive header subtitle (or detail-header
  subtitle) parsed as `Int`, else `null`.
- `getThumbnail(response): String?` — background music thumbnail url, else detail header
  `croppedSquareThumbnailRenderer` url.
- `getArtists(response): List<Artist>` — responsive header `straplineTextOne` odd runs, else detail
  header subtitle split on `" • "`, second group's odd runs, else empty list. Names are cleaned with
  `ApiText.clean`; ids from browse endpoints.
- `getSongs(response, album: AlbumItem): List<SongItem>` — finds the first tab's section-list first
  content's `musicShelfRenderer`, or the two-column secondary contents' first music shelf; maps
  contents through `getSong`; no shelf ⇒ empty list.
- `getSong(renderer: MusicResponsiveListItemRenderer, album: AlbumItem? = null): SongItem?`
  — returns `null` when any required field is missing:
  - `videoId`: first present of `playlistItemData.videoId`, `navigationEndpoint.watchEndpoint.
    videoId`, overlay play endpoint watch videoId, first flex-column run's watch videoId.
  - `title`: first run from `extractRuns(flexColumns, "MUSIC_VIDEO")`, cleaned with `ApiText.clean`.
  - `artists`: runs from `extractRuns(flexColumns, "MUSIC_PAGE_TYPE_ARTIST")`; if empty, the
    supplied album's artists; otherwise empty list.
  - `album`: supplied album (`Album(album.title, album.browseId)`), else flex column index 2 first
    run with a browse id. Unlike most optional fields this path uses a non-null assertion: when
    neither the supplied album nor the flex column provides a browse id, the parser throws (an
    assembly error) rather than dropping the row. `YouTube.album` always supplies the album.
  - `duration`: first fixed-column run text parsed by `parseTime`.
  - `musicVideoType`, thumbnail (`musicThumbnailRenderer`, else album thumbnail), explicit badge
    (`MUSIC_EXPLICIT_BADGE`), library tokens.

### 7.4 `ArtistPage`

`data class ArtistSection(title: String, items: List<YTItem>, moreEndpoint: BrowseEndpoint?)`.
`data class ArtistPage(artist: ArtistItem, sections: List<ArtistSection>, description: String?,
subscriberCountText: String?, monthlyListenerCount: String? = null, descriptionRuns: List<Run>? =
null)`.

Companion:

- `fromSectionListRendererContent(content: SectionListRenderer.Content): ArtistSection?` — dispatch
  to `musicShelfRenderer` or `musicCarouselShelfRenderer`; `null` otherwise.
- Music-shelf section: title = first title run cleaned or `""`; items = mapped responsive list rows,
  dropped when empty (returns `null`); `moreEndpoint` = title first run's browse endpoint.
- Carousel section: requires a basic header and title run, else `null`; items = each content's
  two-row renderer (falling back to responsive list rows), dropped when empty; `moreEndpoint` =
  header more-content button's browse endpoint.
- Responsive-list-turned-`SongItem` row rules: `videoId` same four-step fallback as `AlbumPage`;
  `title` first flex-column run cleaned; artists from flex column 1 split on `" • "` first group's
  odd runs (required — otherwise drop row); album from the last flex column's first run when it has
  a browse id; `duration = null`; thumbnail, music video type, explicit badge, library tokens as
  usual.
- Two-row mapping:
  - Song: id = watch videoId; title first run cleaned; artists = odd subtitle runs carrying browse
    endpoints, falling back to a single artist from the first subtitle run (id `null`); album
    `null`; duration `null`; thumbnail required.
  - Album: `AlbumItem` with browse id, playlist id from overlay `watchPlaylistEndpoint.playlistId`,
    title, `artists = null`, year from last subtitle run `toIntOrNull`, thumbnail, explicit.
  - Playlist: id = browse id with `"VL"` prefix removed; author name = first subtitle run cleaned
    (id `null`); `songCountText = null`; play endpoint required; shuffle/radio from menu icons
    `MUSIC_SHUFFLE`/`MIX` (shuffle required, radio required).
  - Artist: id = browse id; title = **last** title run cleaned; `channelId` = first channel id of
    the `SUBSCRIBE` toggle's default subscribe endpoint; shuffle/radio required.

### 7.5 `ArtistItemsPage` / `ArtistItemsContinuationPage`

- `ArtistItemsPage(title: String, items: List<YTItem>, continuation: String?)` with companion
  `fromMusicResponsiveListItemRenderer(renderer): SongItem?` and
  `fromMusicTwoRowItemRenderer(renderer): YTItem?`.
  - Responsive row: same videoId/title/artist/album/duration rules as `ArtistPage`; album browse id
    optional; artists required; endpoint from overlay play watch endpoint; library tokens.
  - Two-row: album (artists `null`, year from last subtitle run, playlist id required), song
    (artists required from first subtitle group's odd runs, endpoint = watch endpoint), playlist
    (author optional; song count text = subtitle run index 4; play endpoint required;
    shuffle/radio required). Artist rows are not parsed here (returns `null`).
- `ArtistItemsContinuationPage(items: List<YTItem>, continuation: String?)` — plain container.

### 7.6 `ChartsPage`

`data class ChartsPage(sections: List<ChartSection>, continuation: String?)`.
`data class ChartSection(title: String, items: List<YTItem>, chartType: ChartType)`.
`enum class ChartType { TRENDING, TOP, GENRE, NEW_RELEASES }`. Plain containers; `YouTube` builds
them.

### 7.7 `ExplorePage`, `MoodAndGenres`

- `ExplorePage(newReleaseAlbums: List<AlbumItem>, moodAndGenres: List<MoodAndGenres.Item>)`.
- `MoodAndGenres(title: String, items: List<Item>)`, `Item(title: String, stripeColor: Long,
  endpoint: BrowseEndpoint)`.
  - `fromSectionListRendererContent(content): MoodAndGenres?` — requires `gridRenderer`, grid
    header title run, else `null`; items = grid items' `musicNavigationButtonRenderer` mapped,
    dropping unparsable entries.
  - `fromMusicNavigationButtonRenderer(renderer): Item?` — title from first button-text run;
    `stripeColor` = `solid.leftStripeColor` (required); endpoint = `clickCommand.browseEndpoint`
    (required).

### 7.8 `HistoryPage`

`data class HistoryPage(sections: List<HistorySection>?)`, nested
`HistorySection(title: String, songs: List<SongItem>)`.

- `fromMusicShelfRenderer(renderer): HistorySection` — **non-null contract**: title = first run
  cleaned (non-null assertion), songs = mapped rows (non-null assertion). A malformed shelf without
  title/contents may throw; this is accepted behaviour, but `YouTube` only calls it for a real
  shelf.
- Row parser: videoId four-step fallback (required); title first flex-column run cleaned; artists
  from flex column 1's first split group odd runs (empty list if absent); album from flex column 3;
  duration fixed column; thumbnail required; explicit badge; library tokens; `historyRemoveToken`
  from the `${REMOVE_FROM_HISTORY}` menu-service feedback token.

### 7.9 `LibraryPage` and continuations

- `LibraryPage(items: List<YTItem>, continuation: String?)`.
  - `fromMusicTwoRowItemRenderer(renderer): YTItem?` — album (artists parsed from subtitle runs
    carrying browse ids; year last run; playlist id required), playlist (author optional; song
    count last subtitle run; play endpoint may be `null`; shuffle/radio may be `null`;
    `isEditable` = menu contains an `EDIT` icon), artist (title last run; shuffle/radio required).
  - `fromMusicResponsiveListItemRenderer(renderer): YTItem?` — song (videoId four-step fallback,
    title first run, artists from flex column 1 odd runs with **empty-string** ids when the browse
    id is absent, album from flex column 2 with empty-string id fallback, duration, thumbnail
    required, endpoint, library tokens) or artist (thumbnail required, shuffle/radio nullable).
    Other page types return `null`.
  - Album/playlist/artist thumbnails are required; playlist/artist `thumbnail` may be `null` only
    if the row lacks a renderer (then the row is dropped).
- `LibraryContinuationPage(items: List<YTItem>, continuation: String?)` — plain container.
- `LibraryAlbumsPage(albums: List<AlbumItem>, continuation: String?)` with
  `fromMusicTwoRowItemRenderer(renderer): AlbumItem?` — title required, artists `null`, year from
  last subtitle run, thumbnail required, explicit badge.

### 7.10 `NextPage` / `NextResult`

`data class NextResult(title: String? = null, items: List<SongItem>, currentIndex: Int? = null,
lyricsEndpoint: BrowseEndpoint? = null, relatedEndpoint: BrowseEndpoint? = null, continuation:
String?, endpoint: WatchEndpoint)`.

`NextPage.fromPlaylistPanelVideoRenderer(renderer: PlaylistPanelVideoRenderer): SongItem?`:

- byline = `longBylineText.runs.splitBySeparator()`, required.
- `id = renderer.videoId`, required.
- `title = renderer.title.runs.first.text`, required.
- artists = byline group 0 odd runs → `Artist(clean(text), browseId)`; group 0 itself required.
- album = byline group 1 first run only when it has a browse endpoint; otherwise `null`.
- `duration = renderer.lengthText.runs.first.text.parseTime()`, required (a non-parsable length
  drops the row).
- `musicVideoType` = renderer navigation endpoint.
- `thumbnail = renderer.thumbnail.thumbnails.last.url`, required.
- explicit when any badge icon is `MUSIC_EXPLICIT_BADGE`.
- library tokens from the menu.
- `playlistSetVideoId` is intentionally not copied into `setVideoId` here.

### 7.11 `PlaylistPage` and `PlaylistContinuationPage`

- `PlaylistPage(playlist: PlaylistItem, songs: List<SongItem>, songsContinuation: String?,
  continuation: String?, related: List<YTItem>? = null)`.
- Companion `fromMusicResponsiveListItemRenderer(renderer): SongItem?`:
  - `videoId`: four-step fallback required.
  - title: first flex-column run cleaned, required.
  - artists: flex column 1 runs split on `" • "`, first group's odd runs (empty list allowed).
  - album: flex column 2 first run, only when its browse id is non-null (otherwise `null`).
  - duration: first fixed-column run `parseTime`, may be `null`.
  - `musicVideoType`, thumbnail required (drops row), explicit badge.
  - `endpoint`: overlay play `watchEndpoint` (may be `null`).
  - `setVideoId`: first present of `playlistItemData.playlistSetVideoId`,
    `navigationEndpoint.watchEndpoint.playlistSetVideoId`, overlay play
    `watchEndpoint.playlistSetVideoId`, first flex-column run watch `playlistSetVideoId`.
  - library tokens.
- `PlaylistContinuationPage(songs: List<SongItem>, continuation: String?)`.

### 7.12 `PodcastPage`

`object PodcastPage` (behaviour contract; not a DTO):

- `showId(browseId: String): String` — removes the `"MPSP"` prefix (idempotent if absent).
- `showBrowseId(showId: String): String` — `"MPSP" + showId`.
- `isShowBrowseId(browseId: String?): Boolean` — non-null and starts with `"MPSP"`.
- `episode(renderer: MusicMultiRowListItemRenderer, show: Artist? = null): SongItem?`
  — onTap watch endpoint required; id required; title first run cleaned required; artists =
  first `secondTitle` run converted (name cleaned, id from browse endpoint) or `show`;
  duration = playback progress `durationText` through `parseDuration`; `musicVideoType` forced to
  `"MUSIC_VIDEO_TYPE_PODCAST_EPISODE"`; thumbnail required; endpoint = onTap watch endpoint;
  `publishedText` = last subtitle run trimmed if non-empty; `playedPercent` =
  `playbackProgressPercentage`; `description` = concatenated description run texts if not blank.
- `episode(renderer: MusicTwoRowItemRenderer): SongItem?` — requires a browse endpoint with page
  type `MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE`; video id = overlay play watch videoId, else
  browse id with `"MPED"` prefix removed; title required; show = first subtitle run whose browse id
  is an `MPSP` id, else the last non-blank subtitle run whose trimmed text is not `"•"`; thumbnail
  from music or cropped thumbnail required; explicit badge; endpoint = play endpoint copied with
  the resolved videoId, else a fresh `WatchEndpoint(videoId = videoId)`.
- `show(renderer: MusicTwoRowItemRenderer): PlaylistItem?` — requires an `MPSP` browse id; id =
  prefix-stripped; title first run required; author = first subtitle run with any navigation
  endpoint; `songCountText = null`; thumbnail from music/cropped thumbnail (may be `null`);
  play endpoint optional; shuffle/radio `null`; `isPodcast = true`.
- `show(renderer: MusicResponsiveListItemRenderer): PlaylistItem?` — same, using flex columns;
  author = first flex-column-1 run with a navigation endpoint; thumbnail optional; play endpoint
  optional.
- `episode(renderer: MusicResponsiveListItemRenderer): SongItem?` — requires `musicVideoType ==
  MUSIC_VIDEO_TYPE_PODCAST_EPISODE` and an overlay play watch endpoint; id/title required; artists =
  subtitle run whose browse id is `MPSP`; thumbnail required; publishedText = first flex-column-1
  run without a navigation endpoint, trimmed.
- `parseDuration(runs: List<Run>): Int?`:
  - Joins run texts, replaces `"•"` with a space, trims. Empty → `null`.
  - If the joined text contains `":"`, delegate to `String.parseTime()` (Section 11).
  - Otherwise scan `(\d+)\s*(\p{L}+)` pairs, accumulate: unit starting `h`/`o`/`st` → hours,
    `m` → minutes, `s` → seconds; unknown units ignored. Return accrued seconds only when at
    least one unit matched and the total is `> 0`; otherwise `null`.
  - Examples: `"23 min"` → 1380; `"1 h 5 min"` → 3900; `"1:05:00"` → 3900; `"45 s"` → 45;
    `"unknown"` → `null`; `"0 min"` → `null`.

### 7.13 `RelatedPage`

`RelatedPage(songs: List<SongItem>, albums: List<AlbumItem>, artists: List<ArtistItem>,
playlists: List<PlaylistItem>)`.

- Responsive row → `SongItem?`: videoId four-step fallback; title required; artists from flex
  column 1 split-by-separator first group's odd runs (required); album from flex column 2 with a
  required browse id when present; duration `null`; thumbnail required; explicit badge; library
  tokens.
- Two-row mapping:
  - Album: playlist id from overlay `watchPlaylistEndpoint.playlistId` required; artists from the
    second split group of subtitle odd runs (may be `null`); year last subtitle run.
  - Playlist: id with `"VL"` removed; author name = last subtitle run cleaned, id `null`;
    song count text = last subtitle run that contains a digit and does not contain `"view"`
    (case-insensitive); play endpoint required; shuffle required; radio nullable.
  - Artist: title first run; shuffle and radio required.
  - Song two-row rows are not produced here (returns `null`).

### 7.14 `SearchPage`, `SearchResult`

`data class SearchResult(items: List<YTItem>, continuation: String? = null)`.

`SearchPage.toYTItem(renderer: MusicResponsiveListItemRenderer): YTItem?` dispatches:

1. `PodcastPage.show(renderer)` (podcast show) takes precedence.
2. `PodcastPage.episode(renderer)` (podcast episode) next.
3. Then type dispatch:
   - `isSong`: videoId four-step fallback required; title required; artists from flex column 1
     split-by-separator first group's odd runs (required); album from second group's first run only
     when it has a browse endpoint; duration = last split group's first run `parseTime`; thumbnail
     required; explicit badge; library tokens.
   - `isArtist`: id = browse id required; title required; thumbnail required; shuffle endpoint =
     menu item with icon `MUSIC_SHUFFLE` (required); radio endpoint = icon `MIX` (required).
   - `isAlbum`: browseId required; playlistId = overlay play `anyWatchEndpoint.playlistId`
     required; title required; artists from second split group's odd runs (required); year =
     third group's first run `toIntOrNull`; thumbnail required; explicit.
   - `isPlaylist`: id = browse id with `"VL"` removed; title; author = first split group's first
     run (required); `songCountText` = last run of flex column 1 (required); thumbnail; play
     endpoint (overlay `watchPlaylistEndpoint`) required; shuffle and radio required.
   - else `null`.
4. If the required secondary (flex column 1 split) is absent at the top, returns `null` before
   dispatch (except podcast branches, which run first).

### 7.15 `SearchSuggestionPage`

`object SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer): YTItem?` — song, artist
or album only (playlist/podcast return `null`):

- Song: secondary = flex column 1 split then `.clean()`, optional; videoId four-step fallback
  required; title required; artists from secondary first group odd runs (required); album from flex
  column 2 first run with required browse id; duration `null`; thumbnail required; explicit.
- Artist: id/title/thumbnail required; shuffle/radio from menu (nullable, unlike SearchPage).
- Album: requires flex column 1 split; browseId required; playlistId from the `MUSIC_SHUFFLE` menu
  item's `watchPlaylistEndpoint.playlistId` (required); title required; artists from second split
  group odd runs (required); year = last split group first run `toIntOrNull`; thumbnail; explicit.

### 7.16 `SearchSummaryPage`

`data class SearchSummary(title: String, items: List<YTItem>)`.
`data class SearchSummaryPage(summaries: List<SearchSummary>, ranked: List<YTItem> = emptyList())`.

- Filters: `filterBlockedArtists`, `filterDislikedSongs`, `filterExplicit(enabled)`,
  `filterVideoSongs(disableVideos)`, `filterYoutubeShorts(enabled = false)`.
  `filterVideoSongs` **keeps** a summary titled exactly `"Videos"` unchanged; other summaries are
  filtered and dropped when empty. The shared helper returns `this` when disabled.
- `fromMusicCardShelfRenderer(renderer): YTItem?` — dispatch on `onTap`:
  - watch endpoint → `SongItem`: id = videoId; title first run; artists from split subtitle group 1
    odd runs (required); album from group 2 first run with browse endpoint; duration = last group
    first run `parseTime`; musicVideoType from onTap; thumbnail required; explicit.
  - browse endpoint that is an artist endpoint → `ArtistItem`: shuffle/radio from buttons with
    `MUSIC_SHUFFLE`/`MIX` icons (both required).
  - browse endpoint that is an album endpoint → `AlbumItem`: playlistId = first button's
    `command.anyWatchEndpoint.playlistId` (required); artists from split subtitle group 1 odd runs
    (required); year `null`.
  - browse endpoint that is a playlist endpoint → `PlaylistItem`: id with `"VL"` removed; title =
    header basic title runs joined and cleaned (required); author name = all subtitle runs joined
    with `", "` and cleaned, id `null` (required); play endpoint from the `PLAY_ARROW` button
    (required); shuffle from `MUSIC_SHUFFLE` (required); radio `null`.
  - else `null`.
- `fromMusicResponsiveListItemRenderer(renderer): YTItem?` — podcast show/episode branches first.
  Then `secondary = flex column 1 split`, required; `third = flex column 2 split` or empty;
  `listRun = (secondary + third).clean()`. Then same dispatch shapes as `SearchPage` but:
  - song artists/album come from `listRun` (index 0 odd runs required, index 1 first run with
    browse endpoint); duration from `secondary.lastOrNull().firstOrNull().text.parseTime()`.
  - artist/album/playlist rules equivalent to `SearchPage` (playlist author from
    `secondary.getOrNull(1).firstOrNull()`).
  - else `null`.

### 7.17 `NewReleaseAlbumPage`

`fromMusicTwoRowItemRenderer(renderer): AlbumItem?` — browseId required; playlistId from overlay
`watchPlaylistEndpoint.playlistId` required; title required; artists from the second subtitle split
group's odd runs (required); year = last subtitle run `toIntOrNull`; thumbnail required; explicit.

---

## 8. Pagination, continuation and malformed-response semantics

### 8.1 Token extraction

A continuation token is always a non-blank string taken from one of:

- `MusicShelfRenderer.Content.continuationItemRenderer.continuationEndpoint.continuationCommand.token`
- `MusicShelfRenderer.continuations` / `GridRenderer.continuations` /
  `MusicPlaylistShelfRenderer.continuations` / `SectionListRenderer.continuations` /
  `PlaylistPanelRenderer.continuations` (first element with `nextContinuationData`).
- `BrowseResponse.ContinuationContents.{sectionListContinuation,musicPlaylistShelfContinuation,
  gridContinuation}.continuations`.
- `NextResponse.ContinuationContents.playlistPanelContinuation.continuations`.

The page DTOs expose the token as `continuation: String?` and `songsContinuation: String?` exactly
as declared in Section 7. A token is never invented; absence means `null` (end of list).

### 8.2 Continuation-walk rules (contract for helpers built on these pages)

The canonical walk (used by `innertube/utils/Utils.kt` and `YouTube`) must satisfy:

1. Start from the first page's continuation field.
2. Maintain a visited set of tokens. Stop when the token is `null` or already visited.
3. Stop after at most 50 pages (`MAX_PAGES = 50`).
4. An empty page does not immediately end the walk: allow at most two consecutive empty pages,
   then stop. A non-empty page resets the empty counter.
5. A transient failure while fetching a continuation must fail the whole aggregate result (never be
   treated as end-of-list), so callers do not drop the tail.
6. `CancellationException` is always rethrown, including from inside `runCatching`; helper
   functions that collect into `Result` must rethrow it after collecting.
7. The aggregate page returns `continuation = null` (the walk is complete) while preserving the
   first page's own metadata (e.g. `PlaylistPage.playlist`, `PlaylistPage.continuation`).

`YouTube.searchContinuation` additionally returns `continuation = null` when the parsed item list
is empty, even if the wire still supplied a token. The page classes themselves do not enforce that;
it is an assembly rule. `YouTube.playlistContinuation` likewise returns `null` next token when the
parsed songs list is empty.

### 8.3 Malformed-row handling

- `mapNotNull` semantics: a row missing its key identity (video/browse id, title, required
  thumbnail) is dropped silently. The page remains valid with fewer items.
- Missing optional metadata yields `null`/empty fields; it never aborts the page.
- Missing required **envelope** fields (e.g. `MusicResponsiveListItemRenderer.flexColumns`,
  `PlaylistPanelVideoRenderer.selected`, `BrowseResponse.responseContext`) raises
  `SerializationException` at decode time and the whole call fails via `runCatching`.
- An unknown renderer kind is ignored (the corresponding nullable property decodes to `null`); it
  never causes a decode failure because unknown keys are ignored and every field is optional.
- Empty JSON arrays and `null` arrays are equivalent for list properties declared with defaults:
  they produce an empty list. For nullable lists they stay `null`.

### 8.4 Determinism

Parsers must not depend on locale, time, randomness, network or global mutable state other than the
two `YouTube` sets used as filter defaults. Given the same `BrowseResponse`/renderer value, output
is byte-for-byte structurally equal (`data class` equality).

---

## 9. Error taxonomy, retries, cancellation, threading

These are the observable behaviours the models/pages contract relies on from the transport; an
implementer of this cluster must not add retry logic inside DTOs or parsers.

| Situation | Observable outcome |
|---|---|
| Socket/connection failure (`java.io.IOException`) | Transport retries up to 3 total attempts with 500 ms then 1000 ms delay, then rethrows the `IOException`. |
| HTTP 403/401/4xx | Not retried by the transport (it is not an `IOException`); Ktor throws `ClientRequestException` and the enclosing `YouTube` `runCatching` yields `Result.failure`. Callers must not expect an automatic 403 retry in this module. |
| HTTP 5xx | Not retried here; `ServerResponseException` propagates into `Result.failure`. |
| Request/connect/socket timeout | Modelled as `IOException` (or Ktor timeout exception); if it is an `IOException` it participates in the 3-attempt retry; otherwise it fails once. Configured limits: request 60 s, connect 30 s, socket 60 s. |
| JSON decode failure (malformed JSON, missing non-null field, wrong type) | `SerializationException`/`MissingFieldException`; the enclosing `Result` is a failure. No partial object is returned. |
| Row missing optional metadata | Row parser returns `null`; page stays successful. |
| Cancellation | `CancellationException` is never swallowed by `runCatching` wrappers that use the `propagateCancellation` contract; it is rethrown. Retry `delay` is cancellable. |
| Empty page | Successful page with empty list; not an error. |

Threading:

- All response DTOs, page containers and parsers are immutable/stateless and safe to use from any
  thread once constructed.
- `YouTube` network methods are `suspend`; the transport does not force a dispatcher. Callers
  choose `Dispatchers.IO` (e.g. `YouTubeQueue`); blocking `runBlocking` exists only for the legacy
  `createPlaylist` helper (out of scope).
- `YouTube.blockedArtists` / `dislikedSongs` are plain mutable `var`s; no locking is promised.

---

## 10. Wire-format notes that must be preserved

- `MusicResponsiveListItemRenderer` uses the fixed-column alias only on the column wrapper, not on
  `fixedColumns` itself: the parent key remains `fixedColumns` / `flexColumns`.
- `ThumbnailRenderer` field order is `musicThumbnailRenderer`, `musicAnimatedThumbnailRenderer`,
  `croppedSquareThumbnailRenderer`. Because the first is aliased to the third's wire name, both
  read paths must exist.
- `SectionListRenderer.Content.musicCarouselShelfRenderer` accepts the immersive alias; this is the
  only way Home/Artist carousels decode when YouTube renames the key.
- `Continuation.nextContinuationData` accepts `nextRadioContinuationData`; radio queues use it.
- `YouTubeDataPage` is a separate desktop watch-page shape used by `getMediaInfo`; it is not the
  same class as `BrowseResponse`.
- All enum-like values are raw strings; no Kotlin enum may replace the `musicVideoType` /
  `pageType` string fields (unknown values from YouTube must survive).

---

## 11. Cross-module contracts required to compile

| Required symbol | Owner | Contract used here |
|---|---|---|
| `com.revenge.metadata.cleaner.ApiText.clean(raw: String): String` | `:metadata` | HTML-entity decode + removal of orphan `amp;`/`quot;`/`apos;`/`lt;`/`gt;`/`nbsp;` fragments. Pages call it on every user-visible title/artist/author name. Must be pure and total (never throws for any string). |
| `com.revenge.metadata.cleaner.MetadataCleaner.clean(rawTitle: String, rawArtist: String = ""): CleanedMetadata` | `:metadata` | Used by `SongItem.cleanedMetadata`. |
| `com.revenge.metadata.models.CleanedMetadata.toDisplayTitle(): String`; `.primaryArtist: String` | `:metadata` | Used by `SongItem.displayTitle` / `SongItem.cleanArtistName`. |
| `com.revenge.innertube.YouTube.blockedArtists: Set<String>` and `YouTube.dislikedSongs: Set<String>` | `:innertube` (YouTube cluster) | Default arguments of `filterBlockedArtists` / `filterDislikedSongs`; names and mutability are compile contracts. |
| `com.revenge.innertube.utils.parseTime(): Int?` | `:innertube` utils | `"mm:ss"` → seconds, `"hh:mm:ss"` → seconds, anything else `null`. Pages depend on this exact function name/signature. |
| `com.revenge.innertube.models.response.PlayerResponse` and its `StreamingData.Format`, `PlayerConfig.AudioConfig` | this cluster | Imported by `:app` (`YouTubeVideoCanvasProvider`) and playback. Field names above are consumed directly. |
| `com.revenge.innertube.models.YouTubeClient` companion instances and `toContext` | this cluster | Imported by `:app` playback/canvas code (`WEB_REMIX`, `WEB_CREATOR`, `VISIONOS`, `TVHTML5`, `IPADOS`, `IOS`, `ANDROID_VR_1_65_10`, `ANDROID_VR_1_43_32`, `MUSIC_VIDEO_TYPE_*`). |
| `com.revenge.innertube.models.comment.CommentThreadRenderer` / `CommentRenderer` | this cluster | Imported by `:core`/`:app` comment UI. |
| `YTItem`, `SongItem`, `PlaylistItem`, `AlbumItem`, `ArtistItem`, `Artist`, `Album`, `WatchEndpoint`, `BrowseEndpoint`, `PODCAST_BROWSE_PREFIX`, filter extensions | this cluster | Imported by `:core`, `:playback`, `:app`; named-argument construction and property access must match exactly. `SongItem.copy(...)` is used by `AutoHomeSnapshotStore`/`MediaLibrarySessionCallback`. |
| `HomePage`, `HomePage.Section`, `HomePage.Chip`, `PodcastPage`, `ExplorePage`, `ArtistPage`, `ChartsPage`, `BrowseResult`, `AlbumPage`, `HistoryPage`, `SearchSummaryPage`, `PlaylistPage`, `MoodAndGenres` | this cluster | Imported by `:app` viewmodels and playback; see the per-type shapes in Section 7. In particular `HomePage.Section.isStationListenTogether`, `HomePage.Chip` value equality, `PodcastPage.isShowBrowseId/showId/showBrowseId`, and `ChartsPage.ChartSection`/`ChartType` are used downstream. |

---

## 12. Target files the implementer must write

Source files (70). All paths relative to the repository root:

`innertube/src/main/kotlin/com/revenge/innertube/models/`:
`AccountInfo.kt`, `Badges.kt`, `Context.kt`, `Continuation.kt`, `ContinuationItemRenderer.kt`,
`Endpoint.kt`, `GridRenderer.kt`, `Icon.kt`, `IpVersion.kt`, `MediaInfo.kt`, `Menu.kt`,
`MetadataExtensions.kt`, `MusicCardShelfRenderer.kt`, `MusicCarouselShelfRenderer.kt`,
`MusicHeaders.kt`, `MusicMisc.kt`, `MusicPlaylistShelfRenderer.kt`,
`MusicResponsiveListItemRenderer.kt`, `MusicShelfRenderer.kt`, `MusicTwoRowItemRenderer.kt`,
`NavigationEndpoint.kt`, `PlaylistPanel.kt`, `ResponseContext.kt`,
`ReturnYouTubeDislikeResponse.kt`, `Runs.kt`, `SearchSuggestions.kt`, `SectionListRenderer.kt`,
`Tabs.kt`, `TasteSignals.kt`, `ThumbnailRenderer.kt`, `Thumbnails.kt`,
`TwoColumnBrowseResultsRenderer.kt`, `UrlEndpoint.kt`, `YTItem.kt`, `YouTubeClient.kt`,
`YouTubeDataPage.kt`, `YouTubeLocale.kt`;
`…/models/body/Bodies.kt`; `…/models/comment/Comments.kt`;
`…/models/response/{AccountMenuResponse,ActionResponses,BrowseResponse,GetTranscriptResponse,
NextResponse,PlayerResponse,QueueAndSuggestionsResponses,SearchResponse}.kt`.

`innertube/src/main/kotlin/com/revenge/innertube/pages/`:
`AlbumPage.kt`, `ArtistItemsContinuationPage.kt`, `ArtistItemsPage.kt`, `ArtistPage.kt`,
`BrowseResult.kt`, `ChartsPage.kt`, `ExplorePage.kt`, `HistoryPage.kt`, `HomePage.kt`,
`LibraryAlbumsPage.kt`, `LibraryContinuationPage.kt`, `LibraryPage.kt`, `MoodAndGenres.kt`,
`NewReleaseAlbumPage.kt`, `NextPage.kt`, `PageHelper.kt`, `PlaylistContinuationPage.kt`,
`PlaylistPage.kt`, `PodcastPage.kt`, `RelatedPage.kt`, `SearchPage.kt`, `SearchSuggestionPage.kt`,
`SearchSummaryPage.kt`.

Tests the implementer must write (5):

- `innertube/src/test/kotlin/com/revenge/innertube/models/RunsTest.kt`
- `innertube/src/test/kotlin/com/revenge/innertube/models/SerializationTest.kt`
- `innertube/src/test/kotlin/com/revenge/innertube/ParsersTest.kt`
- `innertube/src/test/kotlin/com/revenge/innertube/models/PaginationContractTest.kt`
- `innertube/src/test/kotlin/com/revenge/innertube/models/WireFormatTest.kt`

The first three match the existing baseline test files. The last two are required by the
acceptance tests in Section 13.

---

## 13. Acceptance tests

All tests are deterministic JVM tests (JUnit 4) using inline JSON fixtures and the decoder
configuration from Section 5.1. No network, clock, locale or disk access. Fixture payloads below may
be embedded as string constants.

### 13.1 `RunsTest` — run helpers

| Case | Fixture | Expected |
|---|---|---|
| Compact count | `Runs([Run("1.2M views")])` | `"1.2M"` |
| Grouped count | `Runs([Run("1,234 likes")])` | `"1,234"` |
| No count | `Runs([Run("no numbers here")])` | `null` |
| Null runs | `null` | `null` |
| Separator split | `[Run("Song"), Run(" • "), Run("Artist")]` | 2 groups `["Song"]`, `["Artist"]` |
| Odd elements | `[A, " • ", B]` | `[A, B]` |
| Clean drops plain lead | `[[Artist plain],[Song]]` | one group `[Song]` |
| Clean keeps linked lead | `[[Artist with NavigationEndpoint],[Song]]` | unchanged |
| Count with suffix-only CJK | `"1.2万 views"` | `"1.2万"` (suffix retained) |

### 13.2 `SerializationTest` / `WireFormatTest` — DTO decoding

Fixture 1 — responsive row with aliased fixed column:
`{"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Never
Gonna Give You Up"}]}}}],"fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":
{"runs":[{"text":"3:33"}]}}}],"thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":
[{"url":"https://img/1","width":60,"height":60}]}}},"navigationEndpoint":{"watchEndpoint":
{"videoId":"dQw4w9WgXcQ","watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":
{"musicVideoType":"MUSIC_VIDEO_TYPE_ATV"}}}}}`

Expected: decodes; `isSong == true`; videoId `dQw4w9WgXcQ`; `musicVideoType ==
MUSIC_VIDEO_TYPE_ATV`; first flex run text; first fixed run text `3:33`.

Fixture 2 — `ThumbnailRenderer` with only `croppedSquareThumbnailRenderer`: decoding succeeds;
`musicThumbnailRenderer?.getThumbnailUrl() ?: croppedSquareThumbnailRenderer?.getThumbnailUrl() ==
"https://img/sq"` (either path may hold it; the combined expression must yield the URL).

Fixture 3 — `Continuation` with `{"nextRadioContinuationData":{"continuation":"token123"}}`:
decodes and `listOf(continuation).getContinuation() == "token123"`.

Fixture 4 — `SectionListRenderer` with `contents[0].musicImmersiveCarouselShelfRenderer
{"contents":[],"itemSize":"MEDIUM"}`: `contents[0].musicCarouselShelfRenderer != null`.

Fixture 5 — unknown-field tolerance: a `MusicShelfRenderer` JSON containing an extra
`"someFutureRenderer": {...}` decodes and the known fields are intact.

Fixture 6 — absent nullable: `{"flexColumns":[]}` alone as `MusicResponsiveListItemRenderer`
(the remaining top-level keys absent) decodes with `thumbnail == null`, `menu == null`,
`badges == null`, `playlistItemData == null`, `overlay == null`, `navigationEndpoint == null`.

Fixture 7 — missing required: `{}` decoded as `MusicResponsiveListItemRenderer` fails with
`MissingFieldException` (because `flexColumns` is non-null without default).

Fixture 8 — wrong type: `{"flexColumns":{}}` fails with `SerializationException`.

Fixture 9 — `Runs` helpers: `{"runs":[]}` decodes to an empty list, not `null`; `{}` under
`explicitNulls=false` yields `runs == null`.

Fixture 10 — endpoint precedence: a `NavigationEndpoint` containing both `watchEndpoint` and
`browseEndpoint` returns the watch endpoint; containing only `feedbackEndpoint` returns
`endpoint == null` but `feedbackEndpoint` is populated.

Fixture 11 — `BrowseEndpoint` flags: page type `MUSIC_PAGE_TYPE_AUDIOBOOK` makes
`isAlbumEndpoint == true`; browse id `MPSP123` makes `isPodcastEndpoint == true` even without a
page type.

### 13.3 `PaginationContractTest` — continuation walk

Use an in-memory fake page source; no HTTP.

| # | Scenario | Expected |
|---|---|---|
| P1 | First page token `A`; page(A) returns songs `[s1]`, token `B`; page(B) returns `[]`, token `null` | Aggregate `[s1]`; final `continuation == null`; exactly 2 fetches. |
| P2 | Token repeats: first `A`; page(A) returns `[s1]`, token `A` | Stop after 1 fetch of A; aggregate `[s1]`; no infinite loop. |
| P3 | Two consecutive empty pages then non-empty | Walk stops after the second consecutive empty page; items collected so far are returned. |
| P4 | Empty then non-empty | Empty counter resets; the non-empty page's items are appended. |
| P5 | 51 distinct tokens | At most 50 fetches; no 51st fetch. |
| P6 | page(A) throws `IllegalStateException` | Aggregate result is a failure wrapping the cause; collected items are not returned as success. |
| P7 | page(A) throws `CancellationException` | The same `CancellationException` is rethrown from the helper, not wrapped in `Result.failure`. |
| P8 | Null first token | Zero fetches; aggregate equals the first page items; continuation `null`. |
| P9 | `YouTube.searchContinuation`-shaped page with empty items but a non-null wire token | Returned continuation is `null` (assembly rule, Section 8.2). |

### 13.4 `ParsersTest` — page/parser fixtures

| Case | Fixture | Expected |
|---|---|---|
| Podcast duration localized | `[Run("23 min")]`, `[Run("1 h 5 min")]`, `[Run("1:05:00")]`, `[Run("45 s")]` | 1380, 3900, 3900, 45 |
| Podcast duration invalid | `[Run("unknown")]`, `[Run("0 min")]`, empty | `null` each |
| Podcast show id | `showBrowseId("PLabc")`, `showId("MPSPPLabc")`, `isShowBrowseId("MPSPPLabc")` | `"MPSPPLabc"`, `"PLabc"`, `true`; `isShowBrowseId("PLabc") == false` |
| PageHelper tokens (add-first menu) | toggle icon `LIBRARY_ADD`, default token `add`, toggled token `remove` | `LibraryFeedbackTokens("add","remove")` |
| PageHelper tokens (saved-first menu) | toggle icon `LIBRARY_REMOVE`, default `remove`, toggled `add` | `LibraryFeedbackTokens("add","remove")` |
| PageHelper ignores keep | icon `KEEP`/`KEEP_OFF` | `isLibraryIcon == false`; ignored by token extraction |
| HomePage chip | chip text run "Chill", browse endpoint id `X`, deselected browse endpoint id `Y` | `Chip("Chill", BrowseEndpoint("X"), BrowseEndpoint("Y"))` |
| Album song precedence | renderer with both `playlistItemData.videoId="v1"` and `navigationEndpoint.watchEndpoint.videoId="v2"` | id `v1` |
| Album song missing duration | fixed column absent, everything else present | returns `null` (duration is required in `AlbumPage.getSong`) |
| Playlist song missing thumbnail | thumbnail absent | returns `null` |
| Library uploaded song | flex column 1 artist run without browse endpoint | artist id is `""`, not null; row kept |
| Podcast episode (two-row) | browse page type `MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE`, no play overlay, browse id `MPEDvid` | `SongItem(id="vid", musicVideoType=MUSIC_VIDEO_TYPE_PODCAST_EPISODE)` |
| SearchPage podcast precedence | renderer that is both `MPSP…` show and `isSong` | returns `PlaylistItem(isPodcast=true)`, not SongItem |
| SearchSummary "Videos" keep | summary title `"Videos"` with a video song item, `filterVideoSongs(true)` | summary retained with same items |
| Empty section drop | `BrowseResult` section becomes empty under `filterExplicit(true)` | section removed |
| Home empty section kept | `HomePage` section becomes empty under `filterExplicit(true)` | section retained with empty list |
| malformed row | `MusicResponsiveListItemRenderer` with no videoId and no title | `toYTItem` returns `null`; page list drops it |
| unsupported renderer | `MusicCarouselShelfRenderer.Content` with only nulls | no item emitted, no exception |

### 13.5 Transport-level acceptance cases (implementation outside this cluster)

These verify Section 9 but are exercised through `InnerTube`/`YouTube` fakes, not through DTOs:

| Case | Expected |
|---|---|
| 403 retry | A fake HTTP engine returning `403` for a browse call produces one request, no retry, and `Result.failure` whose cause is a Ktor `ClientRequestException`. |
| Provider timeout | A fake engine throwing `SocketTimeoutException` (an `IOException`) is attempted exactly 3 times with delays 500 ms and 1000 ms; the third attempt's exception is rethrown. Use a virtual-time coroutine test dispatcher so the test is deterministic. |
| Cancellation during retry | Cancel while the retry delay is pending: `CancellationException` propagates; no further attempt is made. |
| malformed response body | A fake engine returning `{not-json` yields `Result.failure` with a `SerializationException`; no page object is produced. |

### 13.6 Workflow-listed lyric cases — explicitly out of cluster

The workflow's test list also names `malformed LRC/TTML`, `unsynchronised lyrics`, and
`translation line-count normalization`. Those behaviours do not exist in
`com.revenge.innertube.models` / `.pages` and must NOT be implemented here. They belong to the
lyrics-provider clusters (`:lyrics`, `:betterlyrics`, `:lrclib`, `:paxsenixlyrics`, `:kugou`,
`:youlyplus`). For this cluster the corresponding acceptance rule is only:

| Case | Rule in this cluster |
|---|---|
| malformed LRC/TTML | Not applicable; no LRC/TTML parser is exposed by these packages. |
| unsynchronised lyrics | Not applicable; the only lyrics-adjacent model is `NextResult.lyricsEndpoint` (a `BrowseEndpoint?`), and `GetTranscriptResponse` (WebVTT transcript), neither of which parses LRC/TTML. |
| translation line-count normalization | Not applicable; no lyric translation type is declared here. |
| provider timeout | Applies only as the generic transport timeout case in Section 13.5. |

The transcript DTO (`GetTranscriptResponse`) has one in-cluster acceptance rule: a fixture with
`actions == null` must decode successfully to `actions == null`; a fixture where
`transcriptCueRenderer.startOffsetMs` is a JSON number decodes to `Long`; a missing `cue` object
under `explicitNulls=false` leaves `cue` as an empty `SimpleText` only if the enclosing field is
nullable (it is non-null here, so the decode fails). The `YouTube.transcript` text formatting is out
of scope.

---

## 14. Non-goals

- HTTP request construction, headers, cookies, visitor data, retry/backoff and JSON decoder
  creation (owned by `InnerTube`/`YouTube`).
- Assembling page objects from responses (owned by `YouTube`): album/artist/playlist/home/search
  pagination orchestration, continuation walks, podcast fallback, search ranking heuristics.
- Playback URL resolution, NewPipe/PipePipe extraction, signature/PO-token handling.
- Lyrics parsing (LRC/TTML), lyric translation and synchronisation.
- UI rendering, persistence and DI.
- Any change to the wire strings listed in Sections 4 and 6; they are external contracts.
