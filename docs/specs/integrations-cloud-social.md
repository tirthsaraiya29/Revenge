# `integrations-cloud-social` Clean-Room Specification

Status: M2 specification (implementation-independent)
Cluster: `integrations-cloud-social`
Target application: `com.revenge.music`
Source roots: `app/src/main/kotlin`, `app/src/gms/kotlin`, `app/src/main/proto`,
`core/src/main/kotlin`
Build contract: `app/build.gradle.kts`

---

## 1. Purpose and Scope

This cluster owns every integration that leaves the app or binds it to another playback surface:

1. **Cloud music libraries**: Subsonic-compatible (`Navidrome`, `NavidromeApiService`,
   `NavidromeRepository`) and `Jellyfin` (`JellyfinApiService`, `JellyfinRepository`) login,
   discovery, stream URL construction and mirroring into the local Room library.
2. **Spotify**: cookie/TOTP token acquisition, the internal GraphQL and REST clients, the shared
   session store, data models, Spotify-to-YouTube matching, the Spotify queue, playlist import and
   Spotify-backed recommendations.
3. **Scrobbling and taste**: Last.fm authentication/scrobble/now-playing, the scrobble timer, the
   ListenBrainz submitter, and the Last.fm taste API used by generated recommendations.
4. **Listen Together**: the JSON/Protobuf WebSocket wire protocol, room state, reconnection,
   notifications and the host/guest playback synchroniser.
5. **Google Cast** (GMS flavour only): Cast options, session handling, queue mirroring, and the
   Cast UI surfaces.
6. **Android Auto / Automotive OS artwork**: the signed `content://` artwork provider and URI
   mapping used by Media3 surfaces in other processes.

Excluded: the YouTube/InnerTube client, the generic queue framework, the Room schema itself, and
lyrics services (`:unison`). They are consumed, not specified here.

All external-service behaviour must be reproducible from this document without copying the
implementation. No source file may be modified while implementing this specification.

---

## 2. Build, Flavour and Platform Contract

### 2.1 Product flavours

`app/build.gradle.kts` declares two `variant` flavours:

| Flavour | `BuildConfig.CAST_AVAILABLE` | Cast sources and libraries |
|---|---|---|
| `foss` (default) | `false` | `app/src/gms` not compiled; no `mediarouter` / `cast.framework` |
| `gms` | `true` | `app/src/gms/kotlin` compiled; links `androidx.mediarouter` and `com.google.android.gms:play-services-cast-framework`, plus Firebase/Location |

`app/src/gms` contains only Kotlin sources; it has no manifest of its own. All Cast entry points
must be guarded by `BuildConfig.CAST_AVAILABLE`. Any caller in shared code that references a Cast
class must be compiled only in the `gms` variant or reached through a shared interface.

`universal` and optional split-ABI flavours (`arm64`, `armeabi`, `x86`, `x86_64`) do not affect this
cluster.

### 2.2 Build-time secrets

The build injects:

- `BuildConfig.LASTFM_API_KEY`, `BuildConfig.LASTFM_SECRET` from `local.properties` or the
  environment; empty string when absent. Last.fm features degrade to uninitialised.
- `BuildConfig.CAST_AVAILABLE` per flavour.

`LastFM.initialize(apiKey, secret)` is called during `Application.onCreate`. `LastFM.isInitialized()`
must be false when either value is empty; all Last.fm network calls must remain inert or fail
without throwing in that state.

### 2.3 Protobuf

`app/src/main/proto/listentogether.proto` is compiled with protobuf-lite into
`com.revenge.music.listentogether.proto.Listentogether`. Runtime dependencies are
`protobuf-javalite` and `protobuf-kotlin-lite`; both flavours include them.

### 2.4 Manifest contract

Required permissions, already declared in `app/src/main/AndroidManifest.xml`:

- `INTERNET`, `ACCESS_NETWORK_STATE` for every integration in this cluster.
- `POST_NOTIFICATIONS` for Listen Together join/suggestion notifications.
- `WAKE_LOCK` for Listen Together room sessions.
- Media playback foreground-service permissions for streaming cloud audio.

Declared components:

- `com.revenge.music.utils.AutomotiveArtworkProvider`, authority `${applicationId}.artwork`,
  `exported=true`, read-only, `image/jpeg`; exported solely so Automotive/Android Auto processes can
  load signed artwork.
- `com.revenge.music.listentogether.ListenTogetherActionReceiver`, `exported=false`, handles
  approve/reject notification actions.
- GMS-only metadata `com.google.android.gms.car.application` pointing at
  `@xml/automotive_app_desc` and `com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME`
  = `com.revenge.music.cast.CastOptionsProvider`.
- `uses-feature android.hardware.type.automotive` is `required=false`.

---

## 3. File Responsibilities

### 3.1 Cloud libraries

| File | Responsibility |
|---|---|
| `app/src/main/kotlin/com/revenge/music/cloud/CloudModels.kt` | Credentials/song/playlist value types; `NavidromeAuthMethod`; Hilt `CloudEntryPoint`. |
| `.../cloud/NavidromeApiService.kt` | Subsonic HTTP protocol, authentication variants, URL builders, JSON parsing, `SubsonicApiException`. |
| `.../cloud/NavidromeRepository.kt` | Credential persistence, login/ping/sync orchestration, Room mirroring, observable login state. |
| `.../cloud/JellyfinApiService.kt` | Jellyfin HTTP protocol, `MediaBrowser` authorization header, JSON parsing, stream/image URL builders. |
| `.../cloud/JellyfinRepository.kt` | Credential persistence, login/ping/sync orchestration, Room mirroring, observable login state. |
| `.../ui/screens/settings/cloud/NavidromeScreens.kt` | `NavidromeLoginScreen`, `NavidromeDashboardScreen`. |
| `.../ui/screens/settings/cloud/JellyfinScreens.kt` | `JellyfinLoginScreen`, `JellyfinDashboardScreen`. |

### 3.2 Spotify and Spotify import

| File | Responsibility |
|---|---|
| `.../spotify/models/*.kt` | Serializable wire/domain models: album, artist, home feed, library item/folder, paging, playlist, recommendations, search result, session, token, track, user, image. |
| `.../spotify/Spotify.kt` | GraphQL partner API client, REST fallback client, response parsers for every operation, token holder, hash-expiry callback, `SpotifyException`. |
| `.../spotify/SpotifyAuth.kt` | `sp_dc` cookie to internal token exchange, TOTP, nuance-secret cache, login URL. |
| `.../spotify/SpotifyHashProvider.kt` | Thread-safe persisted-query hash registry with hardcoded and remote entries. |
| `.../spotify/SpotifySessionManager.kt` | DataStore session read/write/clear and token refresh via `sp_dc`. |
| `.../spotify/SpotifyMapper.kt` | Search-query construction, thumbnail selection, title normalisation, similarity/scoring. |
| `.../spotify/SpotifyPlaybackResolver.kt` | Spotify track to YouTube `SongItem` resolution with caching, concurrency limits and scoring. |
| `.../spotify/SpotifyQueue.kt` | `Queue` implementation that resolves Spotify tracks into Media3 items in order. |
| `.../spotify/LoadSpotifyRecommendationsUseCase.kt` | Local-listening-history seeded Spotify recommendations for the home feed. |
| `.../spotifyimport/SpotifyImportModels.kt` | Immutable UI state/models for the import screen. |
| `.../spotifyimport/SpotifyImportRepository.kt` | Session restore, source loading, URL import, track fetching, matching, Room playlist mirroring, fast sync. |
| `.../spotifyimport/SpotifyImportViewModel.kt` | State machine around the import repository. |
| `.../ui/screens/SpotifyImportScreen.kt` | Import screen, login sheet, source picker, link dialog, progress/summary/error dialogs. |
| `.../ui/screens/SpotifyLoginScreen.kt` | Full-screen WebView cookie capture for `sp_dc`/`sp_key`. |
| `.../ui/screens/LoginScreen.kt` | YouTube/Google WebView login (shared account flow). |

### 3.3 Scrobbling and taste

| File | Responsibility |
|---|---|
| `.../utils/lastfm/LastFM.kt` | Last.fm 2.0 client, request signing, auth/scrobble/now-playing/love, defaults. |
| `.../utils/lastfm/LastFmTasteApi.kt` | `LastFmTasteApi` interface and `RealLastFmTasteApi` implementation for similar tracks/artists/tags and user top lists. |
| `.../utils/ScrobbleManager.kt` | Playback-driven now-playing and scrobble threshold timer. |
| `.../ui/screens/settings/ListenBrainzManager.kt` | ListenBrainz `playing_now` and `single` submission. |
| `.../ui/screens/settings/LastFMSettingsScreen.kt` | Last.fm login dialog, enable switches, threshold editors, logout. |
| `core/src/main/kotlin/com/revenge/music/models/lastfm/Authentication.kt` | `Authentication`, `TokenResponse`, `LastFmError` wire models. |

### 3.4 Listen Together

| File | Responsibility |
|---|---|
| `app/src/main/proto/listentogether.proto` | Binary wire schema, all payloads and `Envelope`. |
| `.../listentogether/Protocol.kt` | Message-type/action constants and all JSON payload models. |
| `.../listentogether/MessageCodec.kt` | Format detection, JSON/Protobuf encode/decode, optional GZIP. |
| `.../listentogether/ListenTogetherClient.kt` | WebSocket lifecycle, rooms, events, state flows, notifications, persistence, ping/RTT, reconnect. |
| `.../listentogether/ListenTogetherManager.kt` | Player coupling, host/guest playback sync, queue/volume/heartbeat, role permissions. |
| `.../listentogether/ListenTogetherServers.kt` | Known server directory and default WebSocket URL. |
| `.../listentogether/ListenTogetherActionReceiver.kt` | Notification action bridge. |

### 3.5 Cast (GMS)

| File | Responsibility |
|---|---|
| `app/src/gms/kotlin/com/revenge/music/cast/CastOptionsProvider.kt` | Cast receiver id, media notification actions, resume/reconnection options. |
| `app/src/gms/kotlin/com/revenge/music/playback/CastConnectionHandler.kt` | Session discovery/connection, remote media client, state flows, queue load/mirror, playback commands, error/retry. |
| `app/src/gms/kotlin/com/revenge/music/ui/component/CastButton.kt` | Cast button/menu item and sheet routing. |
| `app/src/gms/kotlin/com/revenge/music/ui/component/CastDevicePickerSheet.kt` | MediaRouter-based device discovery and selection; device icon classification. |
| `app/src/gms/kotlin/com/revenge/music/ui/component/CastSessionSheet.kt` | Connected-device sheet. |
| `core/src/main/kotlin/com/revenge/music/playback/CastDeviceKind.kt` | Stable device-kind enum shared by GMS UI and handler. |

### 3.6 Automotive artwork

| File | Responsibility |
|---|---|
| `app/src/main/kotlin/com/revenge/music/utils/AutomotiveArtwork.kt` | Remote artwork to signed `content://` URI, on-demand JPEG fetch/cache, cache trimming. |
| `app/src/main/kotlin/com/revenge/music/utils/AutomotiveArtworkProvider.kt` | Exported read-only `ContentProvider` serving those URIs. |

### 3.7 Tests

| File | Responsibility |
|---|---|
| `app/src/test/kotlin/com/revenge/music/spotify/SpotifyQueueTest.kt` | Robolectric queue ordering, unresolved-track, duplicate-track, and match-scoring tests. |
| `app/src/test/kotlin/com/revenge/music/utils/ScrobbleManagerTest.kt` | Local media acceptance and auto-transition metadata switching. |

Additional tests required by this specification may be added under the same package names.

---

## 4. Cloud Library Contract

### 4.1 Shared value types (`cloud/CloudModels.kt`)

```text
enum class NavidromeAuthMethod { TOKEN, PASSWORD }

data class NavidromeCredentials(
  val serverUrl: String,
  val username: String,
  val password: String,
  val authMethod: NavidromeAuthMethod = TOKEN,
) { val isValid: Boolean }

data class NavidromeSong(
  val id: String, val title: String, val artist: String, val album: String,
  val duration: Long, val bitRate: Int? = null, val mimeType: String? = null,
  val suffix: String? = null, val coverArt: String? = null,
  val year: Int? = null, val track: Int? = null,
)

data class NavidromePlaylist(
  val id: String, val name: String, val songCount: Int = 0,
  val duration: Long = 0, val coverArt: String? = null,
)

data class JellyfinCredentials(
  val serverUrl: String, val username: String,
  val accessToken: String? = null, val userId: String? = null,
  val serverId: String? = null,
) { val isValid: Boolean }

data class JellyfinSong(
  val id: String, val title: String, val artist: String, val album: String,
  val durationMs: Long, val bitRate: Int? = null, val container: String? = null,
  val hasCover: Boolean = false, val year: Int? = null,
)

data class JellyfinPlaylist(
  val id: String, val name: String, val songCount: Int = 0, val hasCover: Boolean = false,
)

@EntryPoint @InstallIn(SingletonComponent::class)
interface CloudEntryPoint {
  fun navidromeRepository(): NavidromeRepository
  fun jellyfinRepository(): JellyfinRepository
}
```

Invariants:

- `NavidromeCredentials.isValid` is true iff `serverUrl`, `username` and `password` are all
  non-blank. It does not require `authMethod`.
- `JellyfinCredentials.isValid` is true iff `serverUrl` and `username` are non-blank **and**
  `accessToken` is non-null and non-blank.
- Durations: Navidrome/Subsonic in **seconds** (`NavidromeSong.duration`); Jellyfin in
  **milliseconds** (`JellyfinSong.durationMs`). Consumers must not interchange them.
- `CloudEntryPoint` exists because the Compose navigation graph is not a Hilt entry point. It must
  be installable in `SingletonComponent` and return the same singleton repositories as injection.

### 4.2 Navidrome / Subsonic protocol (`NavidromeApiService`)

Base URL: `trim(serverUrl).trimEnd('/') + "/rest/"`.

Common query parameters for every API call:

| Parameter | Value |
|---|---|
| `u` | username |
| `v` | `1.16.1` |
| `c` | `Revenge` |
| `f` | `json` |

Authentication:

- `TOKEN`: `t = lowercaseHex(MD5(password + salt))`, `s = salt`. Salt is six characters drawn from
  a random UUID string.
- `PASSWORD`: `p = "enc:" + lowercaseHex(UTF-8(password))`.
- `ping` and every data call use the requested method; if a token-authenticated call fails with
  `SubsonicApiException.code == 41`, the call is retried **once** with `PASSWORD`. The successful
  effective method is reported by `ping` via the returned credentials and must be persisted.

Endpoints:

| Method | Operation | Extra parameters | Response mapping |
|---|---|---|---|
| `ping(credentials): Result<NavidromeCredentials>` | `ping.view` | none | Returns credentials copy with effective `authMethod`. |
| `getPlaylists(credentials)` | `getPlaylists.view` | none | `subsonic-response.playlists.playlist[]` to `NavidromePlaylist`; absent arrays produce empty list. |
| `getPlaylistSongs(credentials, playlistId)` | `getPlaylist.view` | `id` | `subsonic-response.playlist.entry[]` to `NavidromeSong`. |
| `getRandomSongs(credentials, size=50)` | `getRandomSongs.view` | `size` | `subsonic-response.randomSongs.song[]`. |
| `getStreamUrl(credentials, songId)` | `stream.view` | `id`, `format=raw`, auth | Direct URL, no network call. |
| `getCoverArtUrl(credentials, coverArtId)` | `getCoverArt.view` | `id`, `size=600`, auth | Direct URL, no network call. |

`getStreamUrl` and `getCoverArtUrl` include fresh auth parameters on each invocation; callers must
treat returned URLs as potentially single-use/expiring and rebuild them rather than caching forever.

Song mapping:

- `id` ← `id`; `title` ← `title` default `"Unknown Title"`; `artist` ← `artist` default
  `"Unknown Artist"`; `album` ← `album` default `"Unknown Album"`.
- `duration` ← `duration` seconds; `bitRate` only when > 0; `mimeType` ← `contentType` when
  non-empty; `suffix`, `coverArt` when non-empty; `year` only when > 0; `track` only when > 0.

Error taxonomy:

- Non-2xx HTTP: `Exception("HTTP <code>: <message>")`.
- JSON body whose `subsonic-response.status != "ok"`: `SubsonicApiException(code, message)` with
  code from `error.code` (default `-1`) and message from `error.message` (default
  `"Subsonic API error"`). `SubsonicApiException` extends `Exception`, exposes `code: Int` and
  `serverMessage: String`, and its message is `"API Error <code>: <serverMessage>"`.
- Any thrown transport/serialisation exception is converted to `Result.failure`.

Threading: every suspend API call switches to `Dispatchers.IO`; calls are blocking OkHttp
`execute()` internally. There is no cancellation check inside an in-flight call; cancellation is
observed at suspension boundaries.

### 4.3 Navidrome repository (`NavidromeRepository`)

Constructor: `NavidromeRepository(@ApplicationContext context, database: MusicDatabase, api:
NavidromeApiService)`; singleton.

Persistent state in `SharedPreferences` file `navidrome_prefs`:
`server_url`, `username`, `password`, `auth_method` (enum name, default `TOKEN`), `connected`.
`logout()` clears the whole file. Password is stored in plaintext preferences; this is an accepted
existing limitation and must not be silently migrated without a migration spec.

Observable state:

- `isLoggedInFlow: StateFlow<Boolean>` — true iff `connected` is true and stored `server_url` and
  `username` are non-blank, evaluated at construction.
- `serverUrlFlow: StateFlow<String?>`, `usernameFlow: StateFlow<String?>`.

Operations:

```text
fun getCredentials(): NavidromeCredentials?
suspend fun login(serverUrl: String, username: String, password: String): Result<Unit>
fun logout()
suspend fun ping(): Result<String>
suspend fun getPlaylists(): Result<List<NavidromePlaylist>>
suspend fun getPlaylistSongs(playlistId: String): Result<List<NavidromeSong>>
suspend fun getRandomSongs(size: Int = 50): Result<List<NavidromeSong>>
fun getStreamUrl(songId: String): String?
fun getCoverArtUrl(coverArtId: String): String?
suspend fun syncPlaylistsAndSongs(): Result<Int>
```

`login` trims the server URL, removes trailing `/`, trims the username, attempts `TOKEN` first, and
persists only on a successful `ping`. On success all three flows update immediately.

All data calls require stored credentials; absent credentials produce
`Result.failure(Exception("Not logged in"))`.

`syncPlaylistsAndSongs` semantics:

1. Fetch playlists. If the call fails, treat the playlist list as empty and continue.
2. For each playlist with local id `"navidrome_pl_<remoteId>"`, upsert a `PlaylistEntity` named
   `"[Subsonic] <name>"`, with `isEditable=false`, `bookmarkedAt`, `createdAt` and
   `lastUpdateTime` set to now; `thumbnailUrl` is the cover URL when `coverArt` exists.
3. Insert each song as `SongEntity(id = "navidrome_<remoteId>")`, duration in seconds (truncated),
   `albumName`, `year`, `inLibrary = now`, cover URL when present. Insert an artist with id
   `"navidrome_artist_<hashCode of artist name>"`, a `SongArtistMap` at position 0 and a
   `PlaylistSongMap` at the remote order.
4. If there were **no playlists at all**, create `"navidrome_pl_library"` named
   `"[Subsonic] Library"` and mirror up to 50 random songs the same way.
5. Return the number of mirrored songs. Any exception outside the per-call `Result` handling is
   caught and returned as `Result.failure` after logging.

Idempotency is not guaranteed by suppression: entities are inserted/updated by primary key.
Re-syncing a playlist does not remove songs that disappeared remotely. This is an accepted
limitation; the spec forbids promising pruning.

### 4.4 Jellyfin protocol (`JellyfinApiService`)

Base URL: `trim(serverUrl).trimEnd('/')`.

Authorization header for authenticated calls:

```text
MediaBrowser Client="Revenge", Device="Android", DeviceId="Revenge-Android", Version="1.0", Token="<accessToken>"
```

`AuthenticateByName` omits the `, Token="..."` segment. The same header is sent as `Authorization`
with `Content-Type: application/json` for login.

Endpoints:

| Method | Endpoint | Notes |
|---|---|---|
| `authenticate(serverUrl, username, password)` | `POST /Users/AuthenticateByName` | JSON `{ "Username", "Pw" }`. Success requires non-blank `AccessToken` and `User.Id`; otherwise failure `"Invalid authentication response from Jellyfin"`. Returns credentials with trimmed URL, `userId`, `serverId`. |
| `ping(credentials)` | `GET /System/Info` | Returns `"<ServerName> (v<Version>)"`, default name `"Jellyfin Server"`. |
| `getPlaylists(credentials)` | `GET /Users/{userId}/Items?includeItemTypes=Playlist&recursive=true` | Requires `userId` or fails `"Missing User ID"`. Maps `Id`, `Name` default `"Unknown Playlist"`, `ChildCount`, `hasCover = ImageTags.Primary exists`. |
| `getPlaylistSongs(credentials, playlistId)` | `GET /Playlists/{id}/Items?userId=...&fields=ItemCounts,PrimaryImageAspectRatio,Path,MediaSources` | Maps audio items. |
| `getSongs(credentials, limit=100)` | `GET /Users/{userId}/Items?includeItemTypes=Audio&recursive=true&sortBy=DateCreated&sortOrder=Descending&limit=...&fields=...` | Newest first. |
| `getStreamUrl(credentials, songId)` | `/Audio/{songId}/stream?static=true&api_key=<token>` | Direct URL. |
| `getItemImageUrl(credentials, itemId)` | `/Items/{itemId}/Images/Primary?quality=90&api_key=<token>` | Direct URL. |

Song mapping from `Items[]`:

- `durationMs = RunTimeTicks / 10_000` when `RunTimeTicks > 0`, else 0.
- `bitRate` and `container` from `MediaSources[0]` (`Bitrate > 0`, non-blank `Container`).
- `artist` = first entry of `Artists` if present, else `AlbumArtist`, else `"Unknown Artist"`.
- `title` default `"Unknown Song"`; `album` default `"Unknown Album"`.
- `hasCover` = `ImageTags.Primary` exists; `year` when `ProductionYear > 0`.

Errors: identical taxonomy to Navidrome except there is no `SubsonicApiException` equivalent:
HTTP failures are `Exception("HTTP <code>: <message>")`, missing user id is
`Exception("Missing User ID")`; every other thrown exception becomes `Result.failure`.

Timeouts: connect 15 s, read 30 s, write 15 s.

### 4.5 Jellyfin repository (`JellyfinRepository`)

Constructor: `JellyfinRepository(@ApplicationContext context, database, api)`; singleton.
Preferences file `jellyfin_prefs`: `server_url`, `username`, `access_token`, `user_id`,
`server_id`, `connected`.

Observable state and operation signatures mirror `NavidromeRepository`, with
`JellyfinPlaylist`/`JellyfinSong` types and an additional `getItemImageUrl(itemId)`.

`login` persists only after `authenticate` succeeds. `syncPlaylistsAndSongs`:

- Playlist local id `"jellyfin_pl_<remoteId>"`, name `"[Jellyfin] <name>"`, `isEditable=false`.
- Song local id `"jellyfin_<remoteId>"`, artist id
  `"jellyfin_artist_<hashCode of artist name>"`, duration `(durationMs / 1000).toInt()`.
- If no playlists exist, create `"jellyfin_pl_library"` named `"[Jellyfin] Library"` with up to
  100 audio items.
- Returns mirrored song count; catches/logs all failures into `Result.failure`.

### 4.6 Cloud UI contract

`NavidromeLoginScreen(navController, repository)` and `JellyfinLoginScreen(navController,
repository)`:

- Fields: server URL, username, password (masked, visibility toggle). Submit is disabled while
  loading.
- Required input is validated before network use; blank required fields show an inline error.
- If the URL lacks `http://`/`https://`, Navidrome defaults to `https://`, Jellyfin to `http://`
  (the UI also offers explicit "Add http://" / "Add https://" chips).
- Success: toast, navigate to the dashboard, popping the login route inclusively.
- Failure: inline error from `Throwable.localizedMessage` or a generic message; no crash.

`NavidromeDashboardScreen(navController, repository)` and `JellyfinDashboardScreen(...)`:

- Collect server URL and username flows; show playlist list on first composition.
- "Test Ping" calls `ping()` and toasts the returned string or failure.
- "Sync to Library" calls `syncPlaylistsAndSongs()`, toasts the count on success, then refreshes the
  playlist list. Controls are disabled/indicate progress while running.

Routes owned by the navigation graph: `settings/navidrome_login`,
`settings/navidrome_dashboard`, `settings/jellyfin_login`, `settings/jellyfin_dashboard`.
`AccountSettingsScreen` obtains both repositories through `CloudEntryPoint` and renders connection
status from their flows.

---

## 5. Spotify Contract

### 5.1 Session and token acquisition

`SpotifySession` wire/domain model:

```text
data class SpotifySession(
  spDc: String, spKey: String? = null, accessToken: String? = null,
  expiresAt: Long = 0L, accountName: String? = null, accountAvatarUrl: String? = null,
) { val isAuthenticated: Boolean }   // spDc non-blank AND accessToken non-blank
```

Persistence keys in the single app `DataStore<Preferences>` (declared in
`core/.../constants/PreferenceKeys.kt`):

- `spotify_sp_dc` (`SpotifySpDcKey`)
- `spotify_sp_key` (`SpotifySpKeyKey`)
- `spotify_access_token` (`SpotifyAccessTokenKey`)
- `spotify_access_token_expires_at` (`SpotifyAccessTokenExpiresAtKey`)
- `spotify_account_name` (`SpotifyAccountNameKey`)
- `spotify_account_avatar_url` (`SpotifyAccountAvatarUrlKey`)

`SpotifySessionManager` API:

```text
suspend fun getSession(context): SpotifySession?
suspend fun saveSession(context, session)
suspend fun clearSession(context)
suspend fun getValidAccessToken(context): String?
```

Token validity uses a 60-second grace: a cached token is used only when
`expiresAt > now + 60_000`. Otherwise refresh with the stored `sp_dc`/`sp_key`. A refresh returning
HTTP 401 clears the session. Any other refresh failure returns null but does not delete credentials.
`clearSession` also clears the WebView cookie session (`clearWebAuthSession`) best-effort and nulls
`Spotify.accessToken`.

`SpotifyAuth` contract:

```text
const val LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
suspend fun fetchAccessToken(
  spDc: String, spKey: String = "", forceRefreshNuance: Boolean = false,
): Result<SpotifyInternalToken>
```

Protocol:

1. Select nuance secret: cached value, or refresh from
   `https://api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef` when forced. The gist contains a
   JSON array of `{ "s": base32Secret, "v": version }`; choose the highest `v`. Any gist failure
   keeps the last cached/default secret. Gist content is untrusted input; only the parsed fields are
   used.
2. Determine Spotify server time from `https://open.spotify.com/api/server-time`
   (`serverTime` epoch seconds). Cache the offset; fall back to device time on failure.
3. Generate a 6-digit RFC 6238 TOTP: HMAC-SHA1 over the 30-second time step from the Base32 secret.
4. `GET https://open.spotify.com/api/token?reason=transport&productType=web-player&totp=<totp>&totpServer=<totp>&totpVer=<version>`
   with headers `Cookie: sp_dc=<spDc>[; sp_key=<spKey>]`, a desktop Chrome user agent, and JSON
   accept headers. Timeouts 15 s connect/read.
5. Decode `SpotifyInternalToken { accessToken, accessTokenExpirationTimestampMs, isAnonymous,
   clientId }`.
6. If the token is anonymous or blank: retry once with `forceRefreshNuance=true`; if still invalid,
   fail with `Spotify.SpotifyException(401, "Received anonymous token — sp_dc cookie is invalid or
   expired")`.
7. Every non-2xx HTTP response becomes `SpotifyException(statusCode, "HTTP <code>: <body>")`.

The secret/default and gist endpoint are operational details, not stable API. Implementations may
replace the recovery source, but must preserve: anonymous-token detection, single forced refresh,
401 failure, and non-crashing fallback.

`SpotifyLoginScreen(navController, onCookiesCaptured: (spDc: String, spKey: String) -> Unit)`:

- Loads `SpotifyAuth.LOGIN_URL` in a JavaScript-enabled WebView with third-party cookies and
  DOM storage; clears existing cookies first.
- On every navigation/page event reads `CookieManager.getCookie("https://open.spotify.com")`,
  extracts `sp_dc` and optional `sp_key`, and fires the callback exactly once when `sp_dc` is
  non-blank. The callback receives an empty string for a missing `sp_key`.
- After capture, navigates up. The WebView is destroyed on dispose. No cookie value may be logged.
- The route is `settings/spotify_login`; it reuses the `settings/spotify_import` back-stack entry's
  `SpotifyImportViewModel` and calls `connectWithCookies(spDc, spKey)`.

`LoginScreen(navController)` is the YouTube/Google login. Its contract relevant to this cluster:

- JavaScript-enabled WebView to `accounts.google.com/ServiceLogin?continue=https://music.youtube.com`,
  multiple windows/popups supported.
- Only hosts matching Google/YouTube/gstatic/googleusercontent/googleapis/googlevideo or `g.co`
  stay in the WebView; all other schemes/URLs open externally and are consumed.
- Captures `VISITOR_DATA` and `DATASYNC_ID`; completes when a cookie containing `SAPISID`,
  `__Secure-3PSID` or `LOGIN_INFO` is present on a YouTube Music page or an authenticated
  youtube.com page.
- On validation success/failure persists an `AccountData` entry, clears WebView state, and
  navigates up. It must never throw to the UI thread.
- Route `login`.

### 5.2 GraphQL/REST client (`Spotify`)

Global state:

- `@Volatile var accessToken: String?`
- `@Volatile var logger: ((level: String, message: String) -> Unit)?`
- `@Volatile var onHashExpired: ((operationName: String) -> Unit)?`
- `fun isAuthenticated(): Boolean = accessToken != null`
- `class SpotifyException(statusCode: Int, message: String, retryAfterSec: Long = 0) : Exception`

Endpoints:

- GraphQL: `POST https://api-partner.spotify.com/pathfinder/v2/query`.
- REST fallback base: `https://api.spotify.com/v1/`.
- Headers: randomized desktop Chrome `User-Agent`, `app-platform: WebPlayer`, `Origin` and
  `Referer` `https://open.spotify.com/`.
- `Authorization: Bearer <accessToken>` on every authenticated request.

GraphQL body shape:

```json
{
  "variables": { ... },
  "operationName": "<name>",
  "extensions": { "persistedQuery": { "version": 1, "sha256Hash": "<hash>" } }
}
```

GraphQL semantics:

- Missing token → `SpotifyException(401, "Not authenticated")` before any request.
- Resolve hashes for the operation: primary hash, then optional `previousHash` when different. Try
  them in order.
- A response is *persisted-query-not-found* when the HTTP status is 412 **or** the JSON `errors[0]
  .message` contains `PersistedQueryNotFound` (case-insensitive).
- On not-found: try the next hash; after exhausting hashes call `onHashExpired(operationName)` and
  throw `SpotifyException(412, "... hash may have rotated")`.
- Retries: up to 3 attempts per hash. HTTP 429 waits `Retry-After` (seconds) or `2 * (attempt+1)`
  and retries; after the last attempt throws `SpotifyException(429, retryAfterSec=...)`.
  HTTP 401 throws immediately. Other non-2xx throws
  `SpotifyException(status, "GraphQL error <status>: <body>")`.
- Any non-persisted GraphQL `errors[0]` throws `SpotifyException(400, "GraphQL: <message>")`.
- Missing required response roots throw `SpotifyException(500, "Invalid <operation> response")`.
- All public methods return `Result<T>` via `runCatching`; exceptions above are the failure values.

REST semantics:

- Up to 3 attempts (1 when `failFastOn429=true`). A 429 with `Retry-After > 3` fails fast. Each
  other retryable 429 waits, then retries. 401 and other non-2xx throw the same `SpotifyException`
  forms. `Retry-After` defaults to `2 * (attempt+1)`.

Hash registry (`SpotifyHashProvider`):

```text
enum class HashSource { HARDCODED, CACHED, REMOTE }
data class GqlHashEntry(hash: String, previousHash: String? = null, source: HashSource)
data class RemoteHashEntry(hash: String, previousHash: String? = null)
data class UpdateResult(updated: Int, unchanged: Int)

fun getHash(operationName: String): String     // throws IllegalStateException if unknown
fun getPreviousHash(operationName: String): String?
fun updateHashes(remoteHashes: Map<String, RemoteHashEntry>, source: HashSource): UpdateResult
fun getAll(): Map<String, GqlHashEntry>
```

- Thread-safe map; all operations must be safe under concurrent `getHash`/`updateHashes`.
- Hardcoded defaults exist for exactly these operation names:
  `profileAttributes`, `libraryV3`, `fetchPlaylist`, `fetchLibraryTracks`, `searchDesktop`,
  `queryArtistOverview`, `getAlbum`, `queryWhatsNewFeed`, `addToPlaylist`, `removeFromPlaylist`,
  `moveItemsInPlaylist`, `editPlaylistAttributes`, `addToLibrary`, `removeFromLibrary`, `home`,
  `canvas`.
- Unknown operation names are a programming error; `getHash` throws.
- `updateHashes` only touches known operation names; it counts entries whose hash changed versus
  unchanged, stores `previousHash`, and never removes hardcoded availability for unknown entries.
- `addToPlaylist`, `removeFromPlaylist` and `moveItemsInPlaylist` currently share one hardcoded
  hash; implementations may split them but must keep all three resolvable.

Public Spotify operations:

```text
suspend fun me(): Result<SpotifyUser>
suspend fun myPlaylists(limit: Int = 50, offset: Int = 0): Result<SpotifyPaging<SpotifyPlaylist>>
suspend fun myLibraryNode(folderUri: String? = null, limit: Int = 50, offset: Int = 0):
  Result<SpotifyPaging<SpotifyLibraryItem>>
suspend fun myArtists(limit: Int = 50, offset: Int = 0): Result<SpotifyPaging<SpotifyArtist>>
suspend fun playlist(playlistId: String): Result<SpotifyPlaylist>
suspend fun playlistTracks(playlistId: String, limit: Int = 100, offset: Int = 0):
  Result<SpotifyPaging<SpotifyPlaylistTrack>>
suspend fun addTracksToPlaylist(playlistId: String, trackUris: List<String>): Result<Unit>
suspend fun removeTracksFromPlaylist(playlistId: String, items: List<PlaylistItemRef>): Result<Unit>
suspend fun moveItemsInPlaylist(playlistId: String, uids: List<String>, beforeUid: String?): Result<Unit>
suspend fun editPlaylistAttributes(playlistId: String, newName: String? = null,
  newDescription: String? = null): Result<Unit>
suspend fun likedSongs(limit: Int = 50, offset: Int = 0):
  Result<SpotifyPaging<SpotifySavedTrack>>
suspend fun addToLibrary(uris: List<String>): Result<Unit>
suspend fun removeFromLibrary(uris: List<String>): Result<Unit>
suspend fun topTracks(timeRange: String = "medium_term", limit: Int = 50, offset: Int = 0):
  Result<SpotifyPaging<SpotifyTrack>>
suspend fun topArtists(timeRange: String = "medium_term", limit: Int = 50, offset: Int = 0):
  Result<SpotifyPaging<SpotifyArtist>>
suspend fun recommendations(seedTrackIds: List<String> = emptyList(),
  seedArtistIds: List<String> = emptyList(), seedGenres: List<String> = emptyList(),
  limit: Int = 50): Result<SpotifyRecommendations>
suspend fun search(query: String, types: List<String> = listOf("track"),
  limit: Int = 20, offset: Int = 0): Result<SpotifySearchResult>
suspend fun newReleases(limit: Int = 20, offset: Int = 0): Result<NewReleasesResponse>
suspend fun home(sectionItemsLimit: Int = 10, timeZone: String = defaultZoneId):
  Result<SpotifyHomeFeed>
suspend fun album(albumId: String): Result<SpotifyAlbum>
suspend fun artist(artistId: String): Result<SpotifyArtist>
suspend fun artistTopTracks(artistId: String, market: String = "US"):
  Result<ArtistTopTracksResponse>
suspend fun artistRelatedArtists(artistId: String): Result<List<SpotifyArtist>>
suspend fun relatedArtists(artistId: String): Result<RelatedArtistsResponse>
suspend fun getCanvas(trackId: String): String?
suspend fun getCanvasForSong(title: String, artist: String): String?

data class PlaylistItemRef(uri: String, uid: String)
data class ArtistTopTracksResponse(tracks: List<SpotifyTrack> = emptyList())
data class RelatedArtistsResponse(artists: List<SpotifyArtist> = emptyList())
data class NewReleasesResponse(albums: SpotifyPaging<SpotifyAlbum>? = null)
```

Operation-to-endpoint mapping and response semantics:

| Public operation | Transport / operation | Required response root | Notes |
|---|---|---|---|
| `me` | GQL `profileAttributes`; REST `me` on any GQL failure | `data.me.profile` | `id` from `uri` after last `:`; images from `avatar.sources`. |
| `myPlaylists` | GQL `libraryV3` | `data.me.libraryV3` | Filter `Playlists`; `flatten=true`; only `PlaylistResponseWrapper` items. `total` from `totalCount`; `limit`/`offset` from `pagingInfo`. |
| `myLibraryNode` | GQL `libraryV3` | same | `flatten=false`; `folderUri` scopes one level. Maps `PlaylistResponseWrapper`→`SpotifyLibraryItem.Playlist`, `FolderResponseWrapper`→`.Folder`; unknown typenames are skipped. |
| `myArtists` | GQL `libraryV3` | same | Filter `Artists`; only typename containing `Artist`. |
| `playlist` | GQL `fetchPlaylist` | `data.playlistV2` | `uri=spotify:playlist:<id>`, `limit=25`. `collaborative` when `members.items.size > 1`. |
| `playlistTracks` | GQL `fetchPlaylist` | `...playlistV2.content` | Item `itemV2.data` → track; `uid` from outer item or wrapper. `isLocal` defaults false. |
| `addTracksToPlaylist` | GQL `addToPlaylist` | any success | `playlistItemUris`, `newPosition = BOTTOM_OF_PLAYLIST`, 20 s overall timeout. |
| `removeTracksFromPlaylist` | GQL `removeFromPlaylist` | any success | Sends item `uid`s. |
| `moveItemsInPlaylist` | GQL `moveItemsInPlaylist` | any success | `BEFORE_UID` with `beforeUid`, else `BOTTOM_OF_PLAYLIST`. |
| `editPlaylistAttributes` | GQL `editPlaylistAttributes` | any success | Sends only non-null fields. |
| `likedSongs` | GQL `fetchLibraryTracks` | `data.me.library.tracks` | Maps `items[].track.data` to `SpotifySavedTrack`. |
| `addToLibrary`/`removeFromLibrary` | GQL | any success | Full URIs passed as `libraryItemUris`. |
| `topTracks`, `topArtists` | REST `me/top/tracks`, `me/top/artists` | JSON body | Fail fast on long 429. |
| `recommendations` | REST `recommendations` | JSON body | Comma-joined seeds; at least one seed should be supplied by callers. |
| `search` | GQL `searchDesktop` | `data.searchV2` | `tracksV2`, `albumsV2`, `artists`, `playlists`. Each requires `*ResponseWrapper` and matching data `__typename`. Empty sections become `null` in the result except `tracks`, which is always present. |
| `newReleases` | GQL `queryWhatsNewFeed` | `data.whatsNewFeedItems` | Only `AlbumResponseWrapper` containing `Album`. |
| `home` | GQL `home` | `data.home` | Missing `sectionContainer` yields an empty section list, not failure. Sections with zero parsed items are dropped. |
| `album` | GQL `getAlbum` | `data.albumUnion` | Tracks from `tracksV2.items[].track`; album override supplies album fields. |
| `artist` | GQL `queryArtistOverview` | `data.artistUnion` | Profile name and avatar. |
| `artistTopTracks` | GQL `queryArtistOverview` | `...artistUnion.discography.topTracks.items` | `market` is accepted but not sent. |
| `artistRelatedArtists` | GQL `queryArtistOverview` | `...relatedContent.relatedArtists.items` | Avoids REST rate limits. |
| `relatedArtists` | REST `artists/<id>/related-artists` | JSON body | |
| `getCanvas` | GQL `canvas` | `data.trackUnion.canvas.url` | Returns null when unauthenticated or on any failure. |
| `getCanvasForSong` | `search` then `getCanvas` | first track | Returns null on any failure. |

Parsing rules that are part of the contract:

- Track ids are always derived from a URI by taking the substring after the last `:`. A missing URI
  yields an empty id.
- Track duration must accept `trackDuration.totalMilliseconds`, `duration.totalMilliseconds`,
  nested under `track`, flat `durationMs`/`duration_ms`, and seconds under `duration`/`track.duration`
  (converted to ms). First positive value wins; otherwise 0.
- Playlist images come from `images.items[].sources[]`; track/album images from `coverArt.sources[]`.
- Artist images come from `visuals.avatarImage.sources[]`.
- Playlist track count must accept `content|contents|tracks|tracksV2.totalCount`, flat
  `totalCount`, `trackCount`, `numTracks`; first present wins.
- All parsers are null-tolerant: malformed optional fields degrade to defaults, required roots throw
  the invalid-response exception.

### 5.3 Models

All Spotify models are `kotlinx.serialization`-compatible (`@Serializable`) unless noted. Field names
below are the Kotlin names; wire aliases in parentheses.

- `SpotifyImage(url="", height: Int?, width: Int?)`
- `SpotifyTrack(id="", name="", artists: List<SpotifySimpleArtist> = [], album:
  SpotifySimpleAlbum? = null, durationMs=0 ("duration_ms"), explicit=false, isLocal=false
  ("is_local"), previewUrl: String? ("preview_url"), trackNumber: Int? ("track_number"), uri: String?,
  popularity: Int?)`
- `SpotifySimpleArtist(id: String? = null, name="", uri: String?)`
- `SpotifySimpleAlbum(id="", name="", images=[], releaseDate: String? ("release_date"),
  albumType: String? ("album_type"), artists=[], uri: String?)`
- `SpotifySavedTrack(addedAt: String? ("added_at"), track: SpotifyTrack)`
- `SpotifyAlbum(id="", name="", albumType ("album_type"), artists, images, releaseDate, totalTracks
  ("total_tracks"), tracks: SpotifyPaging<SpotifyTrack>?, uri, popularity, genres)`
- `SpotifySavedAlbum(addedAt, album)`
- `SpotifyArtist(id="", name="", images, genres, popularity, uri)`
- `SpotifyPaging<T>(items=[], total=0, limit=20, offset=0, next, previous, href)`
- `SpotifyPlaylist(id="", name="", description?, images, owner: SpotifyPlaylistOwner?, tracks:
  SpotifyPlaylistTracksRef?, uri?, public: Boolean?, collaborative=false, snapshotId ("snapshot_id"))`
- `SpotifyPlaylistOwner(id="", displayName? ("display_name"), uri?)`
- `SpotifyPlaylistTracksRef(total: Int? = null, href: String? = null)`
- `SpotifyPlaylistTrack(addedAt?, track: SpotifyTrack?, isLocal=false, uid: String?)`
- `SpotifyRecommendations(tracks=[], seeds=[])`, `SpotifyRecommendationSeed(id?, type?, href?)`
- `SpotifySearchResult(tracks?, playlists?, albums?, artists?)` — each a `SpotifyPaging`.
- `SpotifyUser(id, displayName? ("display_name"), email?, images, product?, country?)`
- `SpotifyToken(accessToken ("access_token"), tokenType ("token_type"), scope="", expiresIn
  ("expires_in"), refreshToken? ("refresh_token"))`
- `SpotifyInternalToken(accessToken, accessTokenExpirationTimestampMs: Long, isAnonymous=false,
  clientId="")`
- `SpotifyHomeFeed(greeting: String?, sections: List<SpotifyHomeFeedSection>)`
- `SpotifyHomeFeedSection(sectionUri, title?, typename, totalCount, items)`
- `SpotifyHomeFeedItem` sealed: `Playlist(uri, id, name, description?, format?, totalCount,
  imageUrl?, extractedColorHex?, ownerName?, madeForUsername?)`, `Album(uri, id, name, albumType?,
  artists, imageUrl?)`, `Artist(uri, id, name, imageUrl?)`; all expose `uri`.
- `SpotifyLibraryItem` sealed: `Playlist(playlist)` / `Folder(folder)`; `uri` is the playlist URI or
  folder URI. `SpotifyLibraryFolder(uri, name, totalChildren=0)`.

`SpotifyToken` is legacy/refresh-token based and not used by the cookie flow. It remains part of the
public model surface.

### 5.4 Matching (`SpotifyMapper`)

```text
data class PrecomputedTrack(normalizedTitle, titleBigrams: Set<String>,
  normalizedArtist, artistBigrams: Set<String>, durationMs: Int)

fun buildSearchQuery(track: SpotifyTrack): String
fun getPlaylistThumbnail(playlist: SpotifyPlaylist): String?
fun getTrackThumbnail(track: SpotifyTrack): String?
fun precompute(title: String, artist: String, durationMs: Int): PrecomputedTrack
fun matchScore(spotifyTitle, spotifyArtist, spotifyDurationMs,
  candidateTitle, candidateArtist, candidateDurationSec: Int?): Double
fun matchScorePrecomputed(precomputed, candidateTitle, candidateArtist,
  candidateDurationSec: Int?): Double
fun earlyExitThreshold(): Double   // 0.95
fun titleSimilarity(titleA: String, titleB: String): Double
```

Normalisation (deterministic):

1. lowercase
2. remove `(feat. …)` and `(ft. …)`
3. remove `[...]`
4. remove `(… remaster …)` case-insensitive
5. remove `(… remix …)` case-insensitive
6. remove every character that is not `a-z`, `0-9` or whitespace
7. collapse whitespace and trim

Similarity:

- Exact normalised strings → 1.0; either empty → 0.0.
- Otherwise Dice coefficient over character bigrams: `2 * |intersection| / (|A| + |B|)`.
- `titleSimilarity` additionally returns at least 0.6 when one normalised title contains the other.

`matchScore` = `0.45 * titleScore + 0.35 * artistScore + 0.20 * durationScore`.

Duration score (Spotify duration converted to seconds, integer division):

| Absolute difference | Score |
|---|---|
| ≤ 2 s | 1.0 |
| ≤ 5 s | 0.8 |
| ≤ 10 s | 0.5 |
| ≤ 30 s | 0.2 |
| > 30 s | 0.0 |

Missing candidate duration or non-positive Spotify duration → 0.5.

Caching: normalised strings and bigram sets use bounded LRU caches (256 entries) guarded by
synchronisation; results must be identical to uncached computation. `buildSearchQuery` is
`"<first artist name> <track name>"`, or just the track name when there is no artist.
Thumbnail selection prefers an image with width in 200..400, else the first image, else null.

### 5.5 Playback resolution (`SpotifyPlaybackResolver`)

Singleton, open class (overridable for tests). Constructor has no required arguments.

```text
fun clearCache()
open suspend fun resolveTrack(track: SpotifyTrack, hideExplicit: Boolean = false): SongItem?
open fun getCached(trackId: String): SongItem?
open suspend fun resolveTracks(tracks: List<SpotifyTrack>, hideExplicit: Boolean = false):
  Map<String, SongItem>
open suspend fun resolveAndFilter(tracks: List<SpotifyTrack>, hideExplicit: Boolean):
  List<SongItem>
open fun findBestMatch(candidates: List<SongItem>, track: SpotifyTrack,
  hideExplicit: Boolean = false): SongItem?
```

Semantics:

- Track cache: bounded 1000-entry LRU keyed by Spotify track id, thread-safe. Blank ids are never
  cached.
- Concurrency: at most 6 simultaneous resolutions.
- `resolveTrack` never throws; logs and returns null on failure.
- Query order: `"artist title"`, then `"title firstArtist"`, then bare `"title"`, deduplicated and
  non-blank.
- First search uses **anonymous** YouTube search (`setLogin=false`, song filter) so watch history
  does not bias results; if no candidates result, retry with default login state.
- Candidates whose `titleSimilarity(track.name, candidate.title) < 0.25` are discarded.
- Explicit candidates are discarded when `hideExplicit`.
- Return immediately at score ≥ 0.85 within a query; stop querying after a query yields ≥ 0.55;
  finally return the best candidate only if ≥ 0.35, else null.
- `resolveTracks` de-duplicates by track id, resolves concurrently, and returns only resolved ids.
  Repeated calls for the same id return the cached item.
- `resolveAndFilter` preserves input order, applies the explicit filter, and drops unresolved tracks.
- `findBestMatch(candidates, track, hideExplicit)` is the synchronous scoring helper:
  - title exact = +30; containment = +15;
  - any artist exact/containment match = +25;
  - duration difference ≤3 s +20, ≤8 s +12, ≤20 s +5, >60 s −25;
  - candidate containing `karaoke`/`instrumental`/`cover`/`tribute`/`parody` when the source title
    does not = −30 each; `live` mismatch = −15;
  - returns the highest-scoring candidate if score > 0, otherwise the first allowed candidate, or
    null if all are filtered.

### 5.6 Spotify queue (`SpotifyQueue`)

Constructor:

```text
SpotifyQueue(
  title: String? = null,
  tracks: List<SpotifyTrack>,
  startIndex: Int = 0,
  preloadItem: MediaMetadata? = null,
  resolver: SpotifyPlaybackResolver,
) : Queue
```

Constants: `WINDOW_BEFORE = 10`, `WINDOW_AFTER = 20`, `PAGE_SIZE = 25`.

Invariants:

- Preserves source order, duplicates and the selected occurrence.
- `getInitialStatus()` runs on `Dispatchers.IO`.
- Empty input → empty status, title preserved, index 0.
- `safeStartIndex = startIndex.coerceIn(0, tracks.lastIndex)`.
- Initial window is `[max(0, safeStartIndex-10), min(size, safeStartIndex+20))`; the remainder is
  pending.
- The selected track is guaranteed to occupy the reported `mediaItemIndex`; if it fails to resolve
  and `preloadItem` exists, the preload item is inserted at that exact position.
- Other unresolved window tracks are skipped. The reported index is clamped to the resolved list.
- `hasNextPage()` and `hasPendingSongs()` are true before initialisation and while pending tracks
  remain.
- `nextPage()` resolves up to 25 pending tracks per call and returns them in order; it returns an
  empty list when exhausted.
- `MusicService` relies on `SpotifyQueue` never being replaced by YouTube radio extension and on
  `hasPendingSongs()` to drive load-more.

### 5.7 Spotify import

#### Repository contract

```text
@Singleton
class SpotifyImportRepository(@ApplicationContext context, database: MusicDatabase) {
  suspend fun restoreSession(): SpotifyImportSession
  suspend fun connectWithCookies(spDc: String, spKey: String): SpotifyImportSession
  suspend fun logout()
  suspend fun loadSources(): List<SpotifyImportSource>
  suspend fun addPlaylistByUrl(url: String): SpotifyImportSource.Playlist
  suspend fun importSources(sources: List<SpotifyImportSource>,
    onProgress: (SpotifyImportProgressUi) -> Unit): SpotifyImportSummaryUi
  suspend fun syncPlaylistFast(localPlaylistId: String)
}

data class SpotifyImportSession(isAuthenticated=false, accountName="", accountAvatarUrl: String? = null)

sealed interface SpotifyImportSource {
  val id: String; val title: String; val subtitle: String; val thumbnailUrl: String?
  val trackCount: Int?; val localPlaylistId: String; val type: SpotifyImportSourceType
  data class Playlist(playlist: SpotifyPlaylist) : SpotifyImportSource
  data class LikedSongs(title: String, trackCount: Int) : SpotifyImportSource
}
```

Source mapping:

| Source | `id` | `localPlaylistId` | subtitle / thumbnail / count |
|---|---|---|---|
| Playlist | `"playlist:<spotifyId>"` | `"SPOTIFY_PLAYLIST_<spotifyId>"` | owner display name; medium thumbnail; `tracks.total` |
| Liked songs | `"liked_songs"` | `"SPOTIFY_LIKED_SONGS"` | empty; null; supplied count |

Session semantics:

- Restore uses a 60-second token grace. A valid cached token wins without network.
- If no token but `sp_dc` exists, refresh; on success verify the profile and return authenticated.
- A 401 during refresh triggers logout and returns unauthenticated.
- Any other refresh failure is treated as transient: return authenticated with cached account
  metadata, retaining credentials.
- `CancellationException` must always be rethrown, never swallowed by these handlers.
- `connectWithCookies` persists the cookies **before** refreshing. A failed refresh must surface as
  a thrown error to the caller (the ViewModel displays it); persisted cookies remain for retry.
- `logout` removes all Spotify preference keys, nulls `Spotify.accessToken`, and clears the WebAuth
  cookie session best-effort.

`loadSources`:

- Requires an authenticated token; otherwise throws `IllegalStateException` with the localized
  "not connected" message.
- Refreshes the profile (best-effort; errors other than cancellation ignored).
- Liked-songs count is `likedSongs(limit=1).total`.
- Playlists are paged with page size 50 until `offset >= total` or a short page, enriched with
  track counts concurrently (max 4) when `tracks.total` is absent.
- Liked Songs is always the first source; then playlists with non-blank ids.

`addPlaylistByUrl`:

- Accepts share URL `https://open.spotify.com/playlist/<id>?si=...`, localized
  `https://open.spotify.com/intl-xx/playlist/<id>`, URI `spotify:playlist:<id>`, or a bare id of at
  least 16 alphanumerics.
- Anything else throws `IllegalArgumentException` with the localized "invalid playlist link"
  message.
- Fetches playlist metadata; when the track count is absent, attempts to fill it from a 1-item page.

`importSources`:

- Iterates sources in caller order. Emits a progress snapshot before each source and after it
  completes, and one per matched track during matching.
- Tracks are fetched page-by-page (size 50) for either liked songs or a playlist; blank-named tracks
  are dropped; pagination stops on an empty page, a short page, or `offset >= total`.
- Matching runs at most 4 concurrently, one YouTube search (`FILTER_SONG`) per track, scored with
  `SpotifyMapper.matchScore`; a source track with no search result or no candidates is a failure,
  not an exception. Matching preserves source order in the resulting playlist.
- Empty source (`0` tracks) still mirrors an empty local playlist and reports zero counts.
- After a source, the local playlist is replaced atomically in one Room transaction:
  - playlist entity keyed by `localPlaylistId`, name = source title, thumbnail = source thumbnail,
    `isEditable=true`, `bookmarkedAt` preserved (or now), `lastUpdateTime=now`;
  - all resolved tracks upserted;
  - existing `PlaylistSongMap` rows cleared and re-inserted at positions 0..n-1 with `setVideoId`;
  - summary records `totalTracks`, `importedTracks = matched`, `failedTracks = total - matched`.
- Progress percent = `round(((completedSources + completedTracks/totalTracks) / totalSources) * 100)`
  clamped 0..100; a source with zero tracks contributes only its completed-source fraction.
- Cancellation propagates; partially completed earlier sources remain mirrored (documented
  at-least-once behaviour).
- 401 responses trigger one token refresh and one retry of the call; a second 401 surfaces. If no
  `sp_dc` remains, the original 401 surfaces.
- `refreshProfile` writes display name and first avatar URL, removing the avatar key when absent.

`syncPlaylistFast(localPlaylistId)`:

- Recognizes only `SPOTIFY_LIKED_SONGS` and `SPOTIFY_PLAYLIST_<spotifyId>`; other ids are a no-op.
- Fetches all remote tracks.
- Reuses an existing local song when its `SpotifyMapper.matchScore` against the remote track is
  **greater than 80.0** (score is 0..1 in `matchScore`, so this predicate effectively never reuses;
  implementations must preserve the observable result: no reuse unless the threshold is corrected
  in a later spec).
- Otherwise searches YouTube and appends the best match. Unmatched tracks are skipped.
- Mirrors the final list into the same local playlist id.

#### ViewModel contract

```text
@HiltViewModel
class SpotifyImportViewModel(repository) : ViewModel() {
  val uiState: StateFlow<SpotifyImportUiState>
  fun restoreSession()
  fun connectWithCookies(spDc: String, spKey: String)
  fun loadSources()
  fun addPlaylistByUrl(url: String)
  fun toggleSource(sourceId: String)
  fun selectAllSources()
  fun clearSelection()
  fun logout()
  fun importSelectedSources()
  fun cancelImport()
  fun dismissSummary()
  fun dismissError()
}
```

State model:

```text
enum class SpotifyImportSourceType { PLAYLIST, LIKED_SONGS }

data class SpotifyImportSourceUi(id, title, subtitle, thumbnailUrl: String?, trackCount: Int?, type)
data class SpotifyImportProgressUi(sourceTitle, completedSources, totalSources,
  matchedTracks, totalTracks, percent)
data class SpotifyImportSourceSummaryUi(title, totalTracks, importedTracks, failedTracks)
data class SpotifyImportSummaryUi(sources) { sourceCount, totalTracks, importedTracks, failedTracks }

data class SpotifyImportUiState(
  isAuthenticated=false, accountName="", accountAvatarUrl=null, isLoading=false,
  sources=[], selectedSourceIds=emptySet(), progress=null, summary=null, errorMessage=null,
) { hasSources, canImport }
```

Invariants:

- Initial state is `isLoading=true`; `init` restores the session and, when authenticated, loads
  sources.
- `loadSources` selects **all** loaded sources by default.
- `addPlaylistByUrl` replaces an existing source with the same id in place, otherwise prepends it
  and selects it. It is ignored while an import is in progress or when the URL is blank.
- `toggleSource` is the only unguarded selection mutator; `selectAll` selects current sources;
  `clearSelection` empties.
- `canImport` = at least one selection AND no progress AND not loading.
- `importSelectedSources` snapshots selected source objects, refuses when empty/already active, and
  runs one import job. Progress updates stream into state; on success `progress=null` and
  `summary` is set; on cancellation `progress=null` and the job ends; other errors set
  `errorMessage` and clear progress.
- `cancelImport` cancels the active job and clears progress.
- `logout` is ignored while progress is non-null; on success the entire UI state resets to defaults.
- Every coroutine failure other than `CancellationException` is reported and converted to
  `errorMessage`; cancellation is rethrown.

#### UI contract

`SpotifyImportScreen(navController, spotifyImportViewModel = hiltViewModel())` route
`settings/spotify_import`:

- Unauthenticated: actions "Connect" (navigates to `settings/spotify_login`) and "Import by link".
- Authenticated: account row; "Select sources"; "Import by link"; "Import selected" (enabled only
  when `canImport`); "Refresh"; "Logout". Actions are disabled while loading or importing.
- Source picker: multi-select rows with type, thumbnail, title, subtitle and count; select-all and
  clear; an import action.
- Link dialog accepts a URL/URI/bare id; invalid input surfaces the repository message.
- Progress dialog shows source title, overall percent and `matchedTracks/totalTracks`; offers
  cancel.
- Summary dialog shows per-source and aggregate total/imported/failed; dismiss clears the summary.
- Error dialog displays `errorMessage`; dismiss clears it.
- The embedded login sheet and full-screen `SpotifyLoginScreen` both capture the same cookies and
  call `connectWithCookies`. Popups opened by Spotify login must be supported and closed on dispose.

### 5.8 Spotify recommendations use case

`LoadSpotifyRecommendationsUseCase(repository, database, resolver)`:

```text
suspend operator fun invoke(hideExplicit: Boolean, fromTimeStamp: Long):
  List<SimilarRecommendation>?
```

- Returns null when no authenticated session exists.
- Seeds from local history: up to 3 shuffled YouTube artists and 2 shuffled most-played songs that
  have an album, each from the top 10 after `fromTimeStamp`.
- For each seed, resolves a Spotify id by search, requests up to 8 recommendations, resolves them
  through `SpotifyPlaybackResolver.resolveAndFilter`, and emits a `SimilarRecommendation` only when
  at least one item resolves.
- If no seed produced recommendations, falls back to the user's top 3 Spotify artists, same flow,
  using a synthetic `Artist` with name/thumbnail and `songCount=0`.
- Every seed failure is contained; the function returns null only when the final list is empty.
- Work inside each phase is concurrent (`async`), cancellation propagates.

---

## 6. Scrobbling, ListenBrainz and Taste Contract

### 6.1 Last.fm client (`LastFM`)

Base URL `https://ws.audioscrobbler.com/2.0/`, form-urlencoded POST, User-Agent identifies the
project. Every signed request:

1. Build parameters `method`, `api_key`, optional `sk`, and method-specific extras.
2. Sort parameters by key, concatenate `key + value` in that order, append the API secret.
3. `api_sig = lowercaseHex(MD5(that string))`.
4. Submit the original parameters plus `api_sig` and `format=json`.

The API key/secret are process-wide, supplied by `initialize`. `isInitialized()` requires both
non-empty; `getApiKey()` returns the key or empty.

```text
var sessionKey: String?
fun initialize(apiKey: String, secret: String)
fun isInitialized(): Boolean
fun getApiKey(): String
fun getKtorClient(): HttpClient
suspend fun getToken(): Result<TokenResponse>
suspend fun getSession(token: String): Result<Authentication>
fun getAuthUrl(token: String): String
suspend fun getMobileSession(username: String, password: String): Result<Authentication>
suspend fun updateNowPlaying(artist, track, album: String? = null,
  trackNumber: Int? = null, duration: Int? = null): Result<...>
suspend fun scrobble(artist, track, timestamp: Long, album: String? = null,
  trackNumber: Int? = null, duration: Int? = null): Result<...>
suspend fun setLoveStatus(artist: String, track: String, love: Boolean): Result<...>
class LastFmException(val code: Int, override val message: String) : Exception
const val DEFAULT_SCROBBLE_DELAY_PERCENT = 0.5f
const val DEFAULT_SCROBBLE_MIN_SONG_DURATION = 30
const val DEFAULT_SCROBBLE_DELAY_SECONDS = 180
```

Methods and parameter names:

- `auth.getToken` → `TokenResponse(token)`.
- `auth.getSession` → extras `token`; `Authentication(session)`.
- `auth.getMobileSession` → extras `username`, `password`; parses JSON. If the body contains an
  `error` field, throw `LastFmException(error, message)`.
- `track.updateNowPlaying` → `artist`, `track`, optional `album`, `trackNumber`, `duration`
  (seconds), `sk`.
- `track.scrobble` → indexed fields `artist[0]`, `track[0]`, `timestamp[0]`, optional
  `album[0]`, `trackNumber[0]`, `duration[0]`, `sk`.
- `track.love` / `track.unlove` → `artist`, `track`, `sk`.

`getMobileSession` is the only method that inspects the error envelope; the others return the raw
HTTP response inside `Result` and do not parse error codes. Callers must treat any failed `Result`
as a scrobble failure. Session key must be non-null before calling authenticated methods; the
current implementation uses `sessionKey!!`, so callers must guard with `isInitialized()` and a
stored session.

`getAuthUrl(token)` = `https://www.last.fm/api/auth/?api_key=<key>&token=<token>`. The web-OAuth
methods are retained for compatibility; the app uses mobile-session login.

### 6.2 Scrobble timer (`ScrobbleManager`)

Constructor `ScrobbleManager(scope: CoroutineScope)`. Mutable configuration:

```text
var minSongDuration: Int = 30
var scrobbleDelayPercent: Float = 0.5f
var scrobbleDelaySeconds: Int = 50
var useNowPlaying: Boolean = true
var enableScrobbling: Boolean = false
var useSendLikes: Boolean = false
```

Operations:

```text
fun destroy()
fun onSongStart(metadata: MediaMetadata?, duration: Long? = null)
fun onSongResume(metadata: MediaMetadata)
fun onSongPause()
fun onPlayerStateChanged(isPlaying: Boolean, metadata: MediaMetadata?, duration: Long? = null)
fun onSongStop()
```

Semantics:

- Submitting a now-playing is attempted only when scrobbling and `useNowPlaying` are enabled and
  Last.fm is initialised. It is fire-and-forget in the supplied scope; failures are logged.
- Artist string is all artist names joined by `", "`, default `"Unknown Artist"`; album is optional;
  duration is seconds.
- A song change resets play time and the `scrobbled` flag. The same metadata id does not restart
  tracking.
- While playing, a one-second ticker accumulates play-seconds. Pausing stops it; resuming resumes
  without resetting the accumulated time. `onSongStart` with a new track resets.
- Scrobble threshold = `min(floor(durationSeconds * scrobbleDelayPercent), scrobbleDelaySeconds)`.
  It fires once per track, cancels the ticker, and submits a scrobble with
  `timestamp = nowSeconds - playTimeSeconds`.
- A track shorter than `minSongDuration` is never scrobbled when its duration is positive. A
  non-positive/unknown duration disables the short-track exclusion and uses 300 seconds for the
  percent threshold.
- `destroy()` cancels both the ticker and the in-flight now-playing/scrobble job. No callback after
  destroy.
- `useSendLikes` is carried for the like-sync feature; `ScrobbleManager` itself does not call
  `setLoveStatus`.

Preference keys and defaults used by the settings screen:
`lastfmSession`, `lastfmUsername`, `lastfm_now_playing` (`LastFMUseNowPlaying`, false),
`lastfm_send_likes` (`LastFMUseSendLikes`, false), `lastfm_scrobbling`
(`EnableLastFMScrobblingKey`, false), `ScrobbleDelayPercentKey` (0.5),
`ScrobbleMinSongDurationKey` (30), `ScrobbleDelaySecondsKey` (180). The last field's UI default
(180) intentionally differs from `ScrobbleManager`'s constructor default (50); the persisted
preference is authoritative.

`LastFMSettingsScreen(navController)` route `settings/lastfm`:

- Shows connection status and a login dialog requiring both username and password.
- On success stores session name/key and updates `LastFM.sessionKey`.
- Toggles: scrobbling, now-playing, send-likes; threshold editors for minimum duration, delay
  percent and absolute delay with reset-to-default actions.
- Logout clears the stored session and username.
- Error mapping for `LastFmException` codes: 4 invalid credentials, 6 invalid parameters,
  9 invalid session key, 10 invalid API key, 13 invalid signature, 14 unauthorized token,
  15 service unavailable; otherwise the server message. Non-Last.fm errors → network error.

### 6.3 ListenBrainz (`ListenBrainzManager`)

Singleton object; no constructor. Token and enable flag come from preferences
`listenbrainz_enabled` and `listenbrainz_token`.

```text
suspend fun submitPlayingNow(context, token, title, artistNames, releaseName,
  durationMs: Long, positionMs: Long): Boolean
suspend fun submitFinished(context, token, title, artistNames, releaseName,
  durationMs: Long, startMs: Long, endMs: Long): Boolean
fun isRunning(): Boolean
val lastSubmitTimeFlow: StateFlow<Long?>
```

Wire contract:

- Endpoint `POST https://api.listenbrainz.org/1/submit-listens`.
- Headers: `Content-Type: application/json`, `Authorization: Token <token>`,
  `User-Agent: Revenge/<version>`.
- `playing_now` body:

```json
{"listen_type":"playing_now","payload":[{"track_metadata":{
  "artist_name":"...","track_name":"...","release_name":"...",
  "additional_info":{"duration_ms":0,"position_ms":0,
    "submission_client":"Revenge","submission_client_version":"..."}}}]}
```

- `single` (finished) body:

```json
{"listen_type":"single","payload":[{"listened_at":<epoch seconds>,"track_metadata":{
  "artist_name":"...","track_name":"...","release_name":"...",
  "additional_info":{"duration_ms":0,"start_ms":0,"end_ms":0,
    "submission_client":"Revenge","submission_client_version":"..."}}}]}
```

- `release_name` is omitted when blank.
- `listened_at = startMs / 1000`; values below `1033430400` (2002-10-01 UTC) are replaced by the
  current epoch seconds.
- Strings are JSON-escaped for `\`, `"` and newline.
- Blank token or blank title returns false without network.
- HTTP success sets `lastSubmitTime`; any HTTP failure or exception returns false and logs. These
  methods never throw except cancellation inside the IO context (they use IO directly; callers may
  cancel the surrounding coroutine).
- Called from `MusicService` only when `listenbrainz_enabled` is true and the trimmed token is
  non-empty.

### 6.4 Last.fm taste API (`LastFmTasteApi`)

Interface and default implementation are used by the generated-recommendations engine. All methods
return `Result` and degrade to an empty list rather than throwing, including a blank API key or
blank required input.

```text
data class SimilarTrack(name, artist, match: Double? = null)
data class SimilarArtist(name, match: Double? = null)
data class Tag(name, count: Int = 0)

interface LastFmTasteApi {
  suspend fun getSimilarTracks(artist: String, track: String): Result<List<SimilarTrack>>
  suspend fun getSimilarArtists(artist: String): Result<List<SimilarArtist>>
  suspend fun getTopTags(artist: String): Result<List<Tag>>
  suspend fun getUserTopArtists(user: String, limit: Int = 30): Result<List<String>>
  suspend fun getUserTopTracks(user: String, limit: Int = 50): Result<List<SimilarTrack>>
  suspend fun getUserTopTags(user: String, limit: Int = 15): Result<List<Tag>>
}
```

Endpoints (GET, `format=json`, `api_key`):

| Method | Last.fm method | Limit | Response root |
|---|---|---|---|
| similar tracks | `track.getsimilar` | 30 | `similartracks.track` |
| similar artists | `artist.getsimilar` | 20 | `similarartists.artist` |
| top tags | `artist.gettoptags` | server default | `toptags.tag` |
| user top artists | `user.gettopartists` | caller | `topartists.artist`, `period=overall` |
| user top tracks | `user.gettoptracks` | caller | `toptracks.track`, `period=overall` |
| user top tags | `user.gettoptags` | caller | `toptags.tag` |

Parsing:

- Root with an `error` key → empty list (no failure).
- `track`/`artist`/`tag` may be an array or a single object.
- `match` may be numeric or numeric string; unparseable → null.
- `count` may be numeric or numeric string; default 0.
- Missing names drop the entry.
- API key resolution: `LastFM.getApiKey()`, falling back to `BuildConfig.LASTFM_API_KEY`.
- Errors are logged and returned as `Result.failure`; downstream generators must treat failure as
  "no signal".

`AppModule` binds `RealLastFmTasteApi` to `LastFmTasteApi` as a singleton.

---

## 7. Listen Together Wire Contract

### 7.1 Transport

- WebSocket URL: preference `listenTogetherServerUrl`; default
  `wss://metroserverx.meowery.eu/ws` (the only directory entry: name "Metrolist Server", location
  Poland, operator "Metrolist").
- OkHttp WebSocket client: connect 30 s, read 60 s, write 30 s, protocol ping 30 s.
- Application ping every 25 s; server must answer `pong`. RTT = time between sending `ping` and
  receiving `pong`.
- Server URL directory is `ListenTogetherServers.serversFlow` / `.servers` / `.defaultServerUrl` /
  `.findByUrl(url)`; entries are `{name, url, location, operator}`.

### 7.2 Framing

Two interchangeable formats:

1. **JSON** (default): UTF-8 JSON object `{ "type": <string>, "payload": <object|null> }`.
2. **Protobuf**: `Envelope` (proto3).

Format detection: empty frame → JSON; first byte `{` → JSON; otherwise Protobuf. If a client
configured for JSON receives a Protobuf frame, it must switch permanently to Protobuf with
compression enabled for the rest of the connection.

Protobuf `Envelope`:

```proto
message Envelope {
  string type = 1;
  bytes payload = 2;
  bool compressed = 3;
}
```

Compression: when enabled, payloads larger than 100 bytes are GZIP-compressed and the envelope's
`compressed` flag is set; if compression does not reduce size, payload is sent uncompressed. JSON
frames may also be GZIP-compressed; they are recognised by the 0x1f 0x8b magic. A decompression
failure must fall back to the raw payload, not crash.

### 7.3 Message types

Client→server message types:

`create_room`, `join_room`, `leave_room`, `approve_join`, `reject_join`, `playback_action`,
`buffer_ready`, `kick_user`, `transfer_host`, `ping`, `chat`, `request_sync`, `reconnect`,
`suggest_track`, `approve_suggestion`, `reject_suggestion`, `update_room_settings`.

Server→client message types:

`room_created`, `join_request`, `join_approved`, `join_rejected`, `user_joined`, `user_left`,
`sync_playback`, `buffer_wait`, `buffer_complete`, `error`, `pong`, `host_changed`, `kicked`,
`sync_state`, `reconnected`, `user_reconnected`, `user_disconnected`, `suggestion_received`,
`suggestion_approved`, `suggestion_rejected`, `room_settings_changed`.

`playback_action.action` values: `play`, `pause`, `seek`, `skip_next`, `skip_prev`, `change_track`,
`queue_add`, `queue_remove`, `queue_clear`, `sync_queue`, `set_volume`.

Important: the Protobuf schema has no message for `PING`, `LEAVE_ROOM`, `REQUEST_SYNC`, `CHAT`
client-side? It does have no `Ping`; those are sent with an empty payload in the envelope (type
string only). `chat` JSON payload model is `ChatPayload(message, replyTo?)`; the server-to-client
`chat` payload is `ChatMessagePayload(userId, username, message, timestamp, replyTo?)`. Protobuf has
no chat message, so chat is inherently JSON-only; implementations must either keep chat on a
JSON-capable connection or omit chat under Protobuf.

### 7.4 Payload schemas (proto field tags are normative)

```proto
message TrackInfo {
  string id = 1; string title = 2; string artist = 3; string album = 4;
  int64 duration = 5; string thumbnail = 6; string suggested_by = 7;
}
message UserInfo {
  string user_id = 1; string username = 2; bool is_host = 3; bool is_connected = 4;
}
message RoomState {
  string room_code = 1; string host_id = 2; repeated UserInfo users = 3;
  TrackInfo current_track = 4; bool is_playing = 5; int64 position = 6;
  int64 last_update = 7; float volume = 8; repeated TrackInfo queue = 9;
  bool allow_participant_control = 10;
}
message UpdateRoomSettingsPayload { bool allow_participant_control = 1; }
message CreateRoomPayload { string username = 1; }
message JoinRoomPayload { string room_code = 1; string username = 2; }
message ApproveJoinPayload { string user_id = 1; }
message RejectJoinPayload { string user_id = 1; string reason = 2; }
message PlaybackActionPayload {
  string action = 1; string track_id = 2; int64 position = 3;
  TrackInfo track_info = 4; bool insert_next = 5; repeated TrackInfo queue = 6;
  string queue_title = 7; float volume = 8; int64 server_time = 9;
}
message BufferReadyPayload { string track_id = 1; }
message KickUserPayload { string user_id = 1; string reason = 2; }
message TransferHostPayload { string new_host_id = 1; }
message SuggestTrackPayload { TrackInfo track_info = 1; }
message SuggestionReceivedPayload {
  string suggestion_id = 1; string from_user_id = 2; string from_username = 3;
  TrackInfo track_info = 4;
}
message ApproveSuggestionPayload { string suggestion_id = 1; }
message RejectSuggestionPayload { string suggestion_id = 1; string reason = 2; }
message SuggestionApprovedPayload { string suggestion_id = 1; TrackInfo track_info = 2; }
message SuggestionRejectedPayload { string suggestion_id = 1; string reason = 2; }
message RoomCreatedPayload { string room_code = 1; string user_id = 2; string session_token = 3; }
message JoinRequestPayload { string user_id = 1; string username = 2; }
message JoinApprovedPayload {
  string room_code = 1; string user_id = 2; string session_token = 3; RoomState state = 4;
}
message JoinRejectedPayload { string reason = 1; }
message UserJoinedPayload { string user_id = 1; string username = 2; }
message UserLeftPayload { string user_id = 1; string username = 2; }
message BufferWaitPayload { string track_id = 1; repeated string waiting_for = 2; }
message BufferCompletePayload { string track_id = 1; }
message ErrorPayload { string code = 1; string message = 2; }
message HostChangedPayload { string new_host_id = 1; string new_host_name = 2; }
message KickedPayload { string reason = 1; }
message SyncStatePayload {
  TrackInfo current_track = 1; bool is_playing = 2; int64 position = 3;
  int64 last_update = 4; repeated TrackInfo queue = 5; float volume = 6;
}
message ReconnectPayload { string session_token = 1; }
message ReconnectedPayload {
  string room_code = 1; string user_id = 2; RoomState state = 3; bool is_host = 4;
}
message UserReconnectedPayload { string user_id = 1; string username = 2; }
message UserDisconnectedPayload { string user_id = 1; string username = 2; }
```

JSON field names are the snake_case names above. Kotlin clients map them to camelCase with
`@SerialName`; unknown keys are ignored and lenient parsing is required.

Protobuf defaults and absence:

- `position`, `volume`, `server_time` at `<= 0` decode to null in the domain payload when they are
  optional.
- Empty strings for `album`, `thumbnail`, `suggested_by`, `reason`, `queue_title`, `track_id`
  decode to null where the Kotlin type allows null.
- `ErrorPayload.code` is a string in the domain model; the proto field is a string.
- `PlaybackActionPayload.insert_next` is non-optional in proto and decodes to a Boolean (false when
  absent).

### 7.5 Room lifecycle semantics

Client state streams:

```text
val connectionState: StateFlow<ConnectionState>   // DISCONNECTED, CONNECTING, CONNECTED,
                                                  // RECONNECTING, ERROR
val roomState: StateFlow<RoomState?>
val role: StateFlow<RoomRole>                     // NONE, HOST, GUEST
val userId: StateFlow<String?>
val pendingJoinRequests: StateFlow<List<JoinRequestPayload>>
val bufferingUsers: StateFlow<List<String>>
val pendingSuggestions: StateFlow<List<SuggestionReceivedPayload>>
val blockedUsernames: StateFlow<Set<String>>
val logs: StateFlow<List<LogEntry>>
val events: SharedFlow<ListenTogetherEvent>       // buffer 64, DROP_OLDEST
val rtt: StateFlow<Long>
val isInRoom: Boolean
val isHost: Boolean
val hasPersistedSession: Boolean
fun getPersistedRoomCode(): String?
fun getSessionAge(): Long
```

Events: `Connected(userId)`, `Disconnected`, `ConnectionError(error)`,
`Reconnecting(attempt,maxAttempts)`, `RoomCreated(roomCode,userId)`,
`JoinRequestReceived(userId,username)`, `JoinApproved(roomCode,userId,state)`,
`JoinRejected(reason)`, `UserJoined`, `UserLeft`, `HostChanged(newHostId,newHostName)`,
`Kicked(reason)`, `Reconnected(roomCode,userId,state,isHost)`, `UserReconnected`,
`UserDisconnected`, `PlaybackSync(action)`, `BufferWait(trackId,waitingFor)`,
`BufferComplete(trackId)`, `SyncStateReceived(state)`, `ServerError(code,message)`,
`ChatMessageReceived(payload)`, `LocalSuggestionApproved(payload)`,
`RoomSettingsChanged(allowParticipantControl)`.

Create/join:

- `createRoom(username)` or `joinRoom(roomCode, username)` while disconnected queues a
  `PendingAction`, clears any persisted session, and connects. On open, a prior session triggers
  `reconnect`; otherwise the pending action is sent.
- Room codes are uppercased before `join_room`.
- `room_created` → role HOST, own user inserted, room state created, session persisted.
- `join_approved` → role GUEST, state adopted, session persisted.
- `join_request` → host appends to pending requests. Blocked usernames are auto-rejected with
  reason `"You are blocked"`. If `listenTogetherAutoApproval` is true, the host auto-approves;
  otherwise, if notification permission is granted, a high-importance notification with approve and
  reject actions appears.
- `user_joined` removes the pending request and its notification.
- `host_changed` updates `hostId`, per-user `isHost`, and local role.
- `kicked` clears room/session-derived state, releases the wake lock, emits `Kicked`.
- `leave_room` clears session, room, role, user, pending requests, buffering and persistence.
- `blockedUsernames` is persisted as a JSON string list under `ListenTogetherBlockedUsersKey`.
- Session persistence keys: `ListenTogetherSessionTokenKey`, `ListenTogetherRoomCodeKey`,
  `ListenTogetherUserIdKey`, `ListenTogetherIsHostKey`, `ListenTogetherSessionTimestampKey`.
  A stored session is restored only when its age is below 10 minutes; older sessions are cleared.

Reconnect and failure:

- On connection loss with a session or room, attempt reconnection. Backoff:
  `min(1000 * 2^(attempt-1), 120000)` ms plus up to 20% jitter, for at most 15 attempts.
- On open with a persisted session, send `reconnect(session_token)`.
- `reconnected` restores room, role and user, clears attempt count, persists the session.
- `error` with a session/room: if the stored room and username are known, wait 500 ms and rejoin
  that room; otherwise clear the session. Always emit `ServerError`.
- If no network, go to DISCONNECTED and wait for connectivity; network restoration reconnects when a
  session, room or pending action exists.
- `disconnect()` closes with code 1000, clears all session state, persistence and emits
  `Disconnected`. Explicit `disconnect` must not auto-reconnect.
- A wake lock is acquired on room entry and released on leave/kick/disconnect.

### 7.6 Playback synchronisation (`ListenTogetherManager`)

Public surface:

```text
class ListenTogetherManager(client: ListenTogetherClient, @ApplicationContext context) {
  val connectionState, roomState, role, userId, pendingJoinRequests, bufferingUsers, logs,
      events, blockedUsernames, pendingSuggestions, rtt
  val isInRoom: Boolean; val isHost: Boolean; val hasPersistedSession: Boolean
  val allowParticipantControl: Boolean
  val canControlMusic: Boolean       // not in a room, or host, or participant control allowed
  val isGuestPlaybackRestricted: Boolean
  val guestPlaybackRestricted: StateFlow<Boolean>
  val chatMessages: StateFlow<List<ChatMessagePayload>>
  fun setPlayerConnection(connection: PlayerConnection?)
  fun initialize(); fun connect(); fun disconnect()
  fun createRoom(username); fun joinRoom(roomCode, username); fun leaveRoom()
  fun approveJoin(userId); fun rejectJoin(userId, reason = null)
  fun kickUser(userId, reason = null)
  fun blockUser(username); fun unblockUser(username); fun getBlockedUsernames(): Set<String>
  fun transferHost(newHostId); fun updateRoomSettings(allowParticipantControl)
  fun sendTrackChange(metadata: MediaMetadata); fun requestSync()
  fun clearLogs(); fun suggestTrack(track: TrackInfo)
  fun approveSuggestion(suggestionId); fun rejectSuggestion(suggestionId, reason = null)
  fun forceReconnect(); fun getPersistedRoomCode(): String?; fun getSessionAge(): Long
  fun sendChatMessage(message: String, replyTo: RepliedMessage? = null)
}
```

Guest rules:

- Only the host or, when `allow_participant_control` is true, a guest may control playback.
  `sendPlaybackAction` enforces this in the client; the manager's `sendTrackChange` also checks
  `canControlMusic` and `isSyncing`.
- Guests that cannot control playback are muted; the previous mute state is saved on entering the
  room and restored on leaving.

Sync semantics:

- Host actions emit `sync_playback` with the action, track id/info, position and `server_time`
  (epoch ms). Guests adjust the position by `max(0, now - server_time)` before seeking.
- Position tolerance: 2000 ms for play/pause decisions, 3000 ms for seeking while playing.
  Duplicate actions within 1000 ms that are already in the desired state are ignored.
- A track change emits `change_track` with `TrackInfo` built from Media3 metadata; duration falls
  back to 180_000 ms when unknown.
- Queue synchronisation emits `sync_queue` with the full queue and optional title; queue add/remove/
  clear emit their corresponding actions.
- Volume synchronisation emits `set_volume` only when host-volume sync is enabled; guest volume is
  applied from the host's action, clamped 0..1.
- Buffer coordination: before a guest starts a new track it sends `buffer_ready`. The host sends
  `buffer_wait(trackId, waitingFor)`, then `buffer_complete(trackId)` when all listed users are
  ready. Until complete, incoming play/pause/seek actions are stored as a pending sync state and
  applied once ready.
- `request_sync` asks the host for `sync_state`; `sync_state` carries current track, playing state,
  position, last update, queue and volume. It is applied only when a player connection exists.
- A heartbeat keeps the session alive; it is stopped on cleanup.
- All manager-to-player mutations run on the main dispatcher; network sends are fire-and-forget.

Chat:

- Guest/host chat is a `chat` message with `ChatPayload.message`.
- Replies are encoded by prefixing the visible message with a zero-width-space-wrapped
  `[RPLY:<base64(username|message)>]` marker, then the reply text. Receivers must parse the marker,
  strip it from the visible text and attach `RepliedMessage`. Malformed markers must degrade to a
  plain message.

### 7.7 Server terms

The default server is community-operated. The app must show the server name/operator/location and
must not silently send a room session to a different server than the selected preference. Users are
responsible for the server's terms; no authentication beyond room/session tokens is implied.

---

## 8. Google Cast Contract (GMS only)

### 8.1 Options

`CastOptionsProvider : OptionsProvider`:

- Receiver application id: `CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID`.
- Notification actions: skip previous, toggle playback, skip next, stop casting; compact view
  actions `[1, 2]`.
- `stopReceiverApplicationWhenEndingSession = true`, `resumeSavedSession = true`,
  `enableReconnectionService = true`.
- No additional session providers.

### 8.2 Connection handler

Constructor `CastConnectionHandler(context, scope: CoroutineScope, musicService: MusicService)`.

State flows: `isCasting`, `isConnecting`, `castDeviceName`, `deviceType` (internal,
`CastDeviceKind`), `castPosition`, `castDuration`, `castIsPlaying`, `castIsBuffering`, `castVolume`,
`autoReconnecting`. `isSyncingFromCast` is a read-only Boolean flag set while Cast-originated
updates are applied locally.

Operations:

```text
fun initialize(): Boolean
fun isCastAvailable(): Boolean
fun disconnect()
fun loadCurrentMedia()
fun loadMedia(metadata: AppMediaMetadata)
suspend fun insertItemsAfterCurrent(items: List<MediaItem>)
suspend fun appendItemsToCastQueue(items: List<MediaItem>)
fun play(); fun pause(); fun seekTo(position: Long); fun setVolume(volume: Float)
fun navigateToMediaIfInQueue(mediaId: String): Boolean
fun skipToNext(); fun skipToPrevious()
fun removeItemFromQueue(itemId: Int); fun moveItemInQueue(itemId: Int, newIndex: Int)
fun clearQueue()
```

Semantics:

- `initialize` and `isCastAvailable` never throw; missing Play Services yields false.
- `loadMedia` builds a Cast queue containing up to 2 previous items, the current item, and up to 2
  next items; `startIndex` points at the current item; `startPosition` is the local position only
  when the ids match. On success the local player pauses (Cast owns playback).
- Each `MediaInfo`:
  - content URL from `MusicService.getStreamUrl(mediaId, title, artists)`, null aborts that item;
  - stream type buffered, content type `audio/mp4`;
  - Cast metadata: title, artists joined `", "`, optional album title, artwork resized to 1080×1080;
  - custom data `{"mediaId": <local id>}` identifies local queue items.
- Queue load retries at most 2 extra times with 1 s × attempt backoff; repeated failure sets
  `autoReconnecting`/error handling and clears buffering.
- Queue mirroring is tracked per occurrence, not per media id, because the receiver reports only a
  small window. Queue mutations are serialised with a mutex. Forward/backward extension keeps the
  Cast queue aligned with the local player around transitions.
- `navigateToMediaIfInQueue` returns true when the target media id exists in the receiver queue and
  the local player is moved to the matching local index; otherwise the caller calls `loadMedia`.
- Volume is clamped 0..1 and applied to the Cast session; `castVolume` reflects session volume, not
  the unreliable media-status stream volume.
- Cast item changes are applied to the local player only when not already in a Cast-originated
  update; `isSyncingFromCast` prevents feedback loops.
- Media errors are logged and recovered per the media-error handler; they must not crash the
  service.

`CastDeviceKind` (shared `:core`): `TV`, `SPEAKER`, `CHROMECAST`, `UNKNOWN`.
`fromName(name, description)` classifies by lowercase name/model: TV keywords → TV; speaker/home/
nest keywords → SPEAKER; chromecast → CHROMECAST; otherwise UNKNOWN. Order matters: TV wins over
Chromecast and Speaker for "Chromecast with Google TV".

### 8.3 Cast UI

- `CastButton(modifier, tintColor, asMenuItem=false)`: hidden when `EnableGoogleCastKey` is false;
  hidden when Cast is unavailable. If casting, opens `CastSessionSheet`; otherwise
  `CastDevicePickerSheet`. Shows a device-kind-specific icon when connected.
- `CastDevicePickerSheet(onDismiss, modifier)`: discovers devices via `MediaRouter` callbacks and
  connects with `MediaRouter.selectRoute` using a fresh route reference; never uses a stale route.
  Shows device name/type, handles empty/loading/error states.
- `CastSessionSheet(onDismiss, modifier)`: auto-dismisses when Cast is unavailable or disconnected;
  shows device, current track and disconnect. Volume is controlled by the main player.

---

## 9. Android Auto / Automotive Artwork Contract

Problem: Android Auto, Automotive OS and instrument clusters load artwork themselves from the
`artworkUri` forwarded by Media3, and only resolve local `content://` / `android.resource://` URIs.
Large bitmaps embedded per item are unreliable.

### 9.1 URI mapping

`AutomotiveArtwork.contentUriFor(context, uri: Uri?): Uri?`

- null → null.
- Non-`http`/`https` schemes are returned unchanged.
- Remote URL → `content://<applicationId>.artwork/art/<base64url(url)>/<signature>`.
  - Base64 is URL-safe, no wrap, no padding.
  - Signature = first 16 bytes of `HMAC-SHA256(per-install secret, url)`, lowercase hex.
- Mapping is memoised in a 1024-entry LRU.
- The per-install secret is 32 random bytes persisted in SharedPreferences file
  `automotive_artwork` under `uri_secret`; it survives process restarts so previously browsed URIs
  remain valid.
- `authority(context)` = `<packageName>.artwork`.

`originalUrl(context, uri): String?`

- Returns null unless scheme is `content`, authority matches, and path has exactly 3 segments
  `["art", base64Url, signature]`.
- Decodes the URL; rejects anything not starting `http://` or `https://`.
- Recomputes the signature and compares in constant time (`MessageDigest.isEqual`).
- Returns the URL only on an exact match.

Extensions:

```text
fun MediaMetadata.withAutomotiveArtworkUri(context): MediaMetadata
fun MediaItem.withAutomotiveArtworkUri(context): MediaItem
```

These return the same object when no remapping occurs, otherwise a copy with the artwork URI
replaced. They must be cheap (LRU hit) because they run for every browse item and metadata read.

### 9.2 Provider

`AutomotiveArtworkProvider : ContentProvider`:

- `openFile` accepts mode `"r"` only; any other mode throws `SecurityException("Artwork is read-only")`.
- Resolves the URL through `originalUrl`; unsigned/unknown URIs throw `FileNotFoundException`.
- Returns a read-only `ParcelFileDescriptor` for the cached JPEG; unavailable artwork throws
  `FileNotFoundException`.
- `getType` is always `image/jpeg`. `query`, `insert`, `delete`, `update` are no-ops.
- The provider must not fetch arbitrary URLs chosen by other apps: the signature check is the
  security boundary.

### 9.3 Fetch and cache

`artworkFile(context, url): File?` is blocking and intended for a provider binder thread:

- Directory `<cacheDir>/automotive_artwork`; filename `sha256Hex(url) + ".jpg"`.
- Existing non-empty file is returned after touching `lastModified` (LRU recency).
- Otherwise fetch through Coil at 1024×1024, software bitmaps only, 10 s timeout. On a network
  error, retry using Coil's disk cache read-only; then compress to JPEG quality 90 on
  `Dispatchers.Default`.
- Write to a temp file and rename atomically; delete temp on failure. Never expose partial files.
- Trim after write: if the directory exceeds 64 MiB, delete oldest files until at most 48 MiB.
- Returns null when fetch/compress fails.

`MusicService` may warm the cache for the current artwork when Android Auto is connected; this is an
optimisation and must not block playback.

---

## 10. Cross-Module Contracts Required to Compile

| Consumer | Contract |
|---|---|
| `MusicService` | Injects `NavidromeRepository` and `JellyfinRepository`. Media ids beginning `navidrome_` / `jellyfin_` resolve stream URLs through `getStreamUrl(rawId)`. The resulting `PlaybackData` uses mime `audio/flac`, quality labels `"Lossless (Subsonic)"` / `"Lossless (Jellyfin)"`, `AUDIO_QUALITY_LOSSLESS`, 24 h expiry. A null URL falls through to normal resolution. |
| `MusicService` / queue | Casts `currentQueue as? SpotifyQueue`; uses `hasPendingSongs()`, `hasNextPage()`, `nextPage()`. Spotify queues must not be replaced by YouTube radio extension when exhausted. |
| `SpotifyPlaylistDetailViewModel` | Constructs `SpotifyQueue(title, tracks, startIndex, preloadItem, resolver)` after pre-resolving the selected track; calls `spotifyImportRepository.restoreSession()` and `Spotify.playlistTracks`. |
| `LibrarySpotifyViewModel` | `restoreSession()`, `Spotify.likedSongs(limit=1).total`, `Spotify.myPlaylists(limit=50)`. |
| `LocalPlaylistViewModel` | Calls `spotifyImportRepository.syncPlaylistFast(playlistId)` for local Spotify playlists. |
| `HomeViewModel` | Injects `LoadSpotifyRecommendationsUseCase` and calls it for the "forgotten favourites" carousel. |
| `NavigationBuilder` | Routes `settings/navidrome_login`, `settings/navidrome_dashboard`, `settings/jellyfin_login`, `settings/jellyfin_dashboard`, `settings/lastfm`, `settings/spotify_import`, `settings/spotify_login`, `settings/integrations/listen_together`; obtains cloud repositories via `CloudEntryPoint`; reuses the import ViewModel for the login callback. |
| `AccountSettingsScreen` | Uses `CloudEntryPoint` and cloud flows; exposes ListenBrainz enable/token preferences. |
| `AppModule` | Binds `LastFmTasteApi` → `RealLastFmTasteApi`; provides `ListenTogetherClient(context)` and `ListenTogetherManager(client, context)` as singletons. |
| `PreferenceKeys` (`:core`) | Spotify keys, Last.fm keys, scrobble keys, ListenBrainz keys, Listen Together server/session/auto-approval/blocked keys. |
| `CastDeviceKind` (`:core`) | Shared by GMS Cast handler and UI; must stay in `com.revenge.music.playback`. |
| `MusicService` ListenBrainz | Calls `ListenBrainzManager.submitPlayingNow` / `submitFinished` only when enabled and token non-blank. |
| `MusicService` Last.fm | Observes `LastFMSessionKey` and assigns `LastFM.sessionKey`; constructs and configures `ScrobbleManager(scope)` and forwards player events. |
| `MediaMetadata` | `id`, `title`, `artists: List<Artist(id,name)>`, `album?.title`, `duration` (seconds), `thumbnailUrl`, `toMediaMetadata()`; used by Spotify queue/resolver and scrobbling. |

---

## 11. Threading, Cancellation and Concurrency Summary

- **Cloud repositories/API**: `Dispatchers.IO`, blocking OkHttp. `Result`-based; no exceptions
  escape public suspend methods except cancellation. SharedPreferences writes use `apply()`.
- **Spotify**: coroutine-based; mutable global token and callbacks are `@Volatile`. Hash registry,
  resolver cache and mapper caches are synchronised. Resolver limits to 6 concurrent resolutions;
  import matching to 4; playlist count enrichment to 4.
- **Import**: repository methods run on IO; ViewModel launches on IO; a `Mutex` serialises source
  list mutation. `CancellationException` is always rethrown. Progress callbacks may arrive from
  multiple matching coroutines; consumers must tolerate concurrent updates (the ViewModel uses an
  atomic `StateFlow.update`).
- **Scrobbling**: one-second ticker in the caller's scope; now-playing/scrobble jobs cancel and
  replace each other. `destroy` cancels both.
- **ListenBrainz**: IO; exceptions converted to false.
- **Listen Together**: client scope is IO + SupervisorJob; events are a 64-slot
  `MutableSharedFlow` with `DROP_OLDEST`; manager player mutations are on `Dispatchers.Main`.
  Reconnect jobs are cancelled/replaced. Wake lock is time-bounded (10 minutes) and released on
  leave.
- **Cast**: callbacks arrive on binder/main threads; queue mutations are serialised by a mutex;
  position polls run in the service scope. No Cast work may run in `foss`.
- **Automotive artwork**: provider `openFile` is blocking by design; fetch runs `runBlocking` with a
  10 s timeout inside that binder call. Compression is offloaded to `Dispatchers.Default`.

---

## 12. Error Taxonomy (Consolidated)

| Area | Failure representation | Retry / isolation |
|---|---|---|
| Navidrome HTTP | `Exception("HTTP <code>: <message>")` | No automatic retry except token→password on code 41. |
| Subsonic API | `SubsonicApiException(code, serverMessage)` | Same. |
| Jellyfin | `Exception("HTTP ...")`, `Exception("Missing User ID")`, invalid-auth message | No retry. |
| Cloud repository | `Result.failure("Not logged in")` or propagated API failure | Login/ping failures never persist credentials. Sync continues past a failed playlist fetch with an empty list. |
| Spotify token | `SpotifyException(401, anonymous/invalid cookie)` | One forced nuance refresh on anonymous token. |
| Spotify HTTP | `SpotifyException(status, message, retryAfterSec)` | 429 retried up to 3; 401 immediate; 412 hash fallback then `onHashExpired`. |
| Spotify public API | `Result.failure(SpotifyException)` | Missing response roots → 500 invalid response. |
| Spotify import | `IllegalStateException("not connected")`, `IllegalArgumentException("invalid link")`, propagated `SpotifyException` | One 401 refresh+retry; per-track failures become summary `failedTracks`; per-playlist count failures fall back to unknown. |
| Last.fm | `LastFmException(code, message)` for mobile-session login only; raw failed `Result` elsewhere | No retry; failures logged. |
| Scrobble | swallowed/logged; no user-visible error | Fire-and-forget. |
| ListenBrainz | `false` | Fire-and-forget. |
| Listen Together | `connectionState=ERROR`, `ListenTogetherEvent.ConnectionError`, `ServerError` | Up to 15 exponential backoffs; session auto-rejoin; network-loss wait. |
| Cast | `isCasting` false, `initialize` false, media errors logged | Queue load 2 retries; auto-reconnect flag. |
| Automotive artwork | `FileNotFoundException` / `SecurityException` | Coil network fallback to disk cache; null result. |

---

## 13. Terms, Privacy and Notices

- **Spotify**: integration uses Spotify's internal web-player endpoints and persisted GraphQL
  hashes, not a public developer application. It is not endorsed by Spotify and is subject to
  Spotify's Terms of Service. `sp_dc`/`sp_key` cookies are credentials and must never be logged,
  exported, or sent anywhere except `open.spotify.com`. The import feature copies metadata and
  matches to YouTube; it does not download Spotify audio.
- **Jellyfin / Navidrome / Subsonic**: user-provided servers. Credentials are stored locally;
  Navidrome stores the password in plaintext preferences and Jellyfin stores an access token.
  Server URLs and content are never sent to the app's operator. Users are responsible for their
  server's terms and network security; plain `http://` is allowed for LAN servers but is not
  confidential.
- **Last.fm**: scrobbling, now-playing and love operations are governed by Last.fm's API Terms of
  Use. API key/secret are build-time secrets and must not be committed.
- **ListenBrainz**: submissions are public or private according to the user's ListenBrainz
  account settings; the token is a bearer credential stored in preferences.
- **Listen Together**: rooms are hosted on the selected community server; room codes and session
  tokens are the only access control. The default server is third-party operated. Users must not
  be led to believe the app operates it.
- **Google Cast**: GMS-only and links proprietary Play Services. The `gms` flavour is not
  GPL-3.0-distributable as a prebuilt binary without complying with Google's terms. The `foss`
  flavour must contain no Cast code or Play Services.
- **Third-party notices**: `THIRD_PARTY_NOTICES.md` must continue to record Ktor, OkHttp,
  kotlinx.serialization, Protobuf (BSD-3-Clause), Media3, AndroidX MediaRouter/Cast (GMS only) and
  the vendored source attributions. Any new dependency in this cluster requires a notice entry.

---

## 14. Acceptance Tests

All tests must be deterministic: no real network, real clock, or real Spotify/Last.fm/ListenBrainz/
Jellyfin/Navidrome servers. Use fake HTTP engines, in-memory DataStore/SharedPreferences, a
Robolectric or JVM `MusicDatabase` substitute, and fixed clocks.

### 14.1 Cloud models and protocol

1. **Credential validity**: blank URL/username/password each make `NavidromeCredentials.isValid`
   false; a Jellyfin credential without a token is invalid; with a token valid.
2. **Subsonic token auth URL**: given a fixed salt generator returning `"abcdef"`, the URL for
   `getStreamUrl` contains `u`, `v=1.16.1`, `c=Revenge`, `f=json`, `id`, `format=raw`,
   `s=abcdef`, and `t = md5(password + "abcdef")`. Assert the exact MD5 for a fixture password.
3. **Subsonic password auth**: `PASSWORD` emits `p=enc:<hex>` and no `t`/`s`.
4. **Code 41 fallback**: fake server returns `{"subsonic-response":{"status":"failed","error":
   {"code":41,"message":"Wrong username or password"}}}` for token auth and `status:"ok"` for
   password auth. `ping` succeeds once, returns `authMethod=PASSWORD`, and the second request
   contains `p=enc:...`.
5. **Subsonic error mapping**: code 70 returns `SubsonicApiException(70, "...")`; HTTP 500 returns
   a failure whose message starts `"HTTP 500"`.
6. **Subsonic parsing**: fixture with missing `artist`/`album` yields the documented defaults;
   `bitRate=0`, `year=0`, `track=0` become null; duration is seconds.
7. **Jellyfin auth**: fixture `{AccessToken:"t", User:{Id:"u"}, ServerId:"s"}` succeeds; missing
   token or user id fails with the invalid-auth message; the Authorization header contains
   `MediaBrowser Client="Revenge"` and, on authenticated calls, `Token="t"`.
8. **Jellyfin ticks**: `RunTimeTicks=1_800_000_000` → `durationMs=180_000`; `0` → 0.
9. **Jellyfin artist fallback**: no `Artists` uses `AlbumArtist`; neither uses `"Unknown Artist"`.
10. **Missing user id**: `getPlaylists`/`getPlaylistSongs`/`getSongs` on credentials with null
    `userId` fail `"Missing User ID"` without issuing a request.
11. **Repository login persistence**: successful login writes all keys and flips the flows;
    failed login writes nothing and leaves `isLoggedInFlow=false`; `logout` clears everything.
12. **Repository sync mapping**: fake API with one playlist/two songs inserts
    `navidrome_pl_p1`, `navidrome_s1`, `navidrome_artist_<hash>`, and two `PlaylistSongMap`
    positions; with zero playlists it creates the library playlist and mirrors random songs;
    a failed playlist call still returns the songs it could mirror (or 0) and never throws.
13. **Jellyfin sync mapping**: analogous with `jellyfin_` prefixes and seconds truncation.

### 14.2 Spotify auth and session

14. **TOTP fixture**: with a known Base32 secret and server time, the generated 6-digit code equals
    an independently computed RFC 6238 vector; code is zero-padded.
15. **Anonymous token retry**: first token response `{isAnonymous:true}` triggers exactly one
    nuance refresh; a second anonymous response fails with status 401. A non-anonymous first
    response performs no gist call.
16. **Non-2xx token**: HTTP 400 yields `SpotifyException(400)` whose message includes the body.
17. **Session grace**: `expiresAt = now + 61s` returns the cached token with no network;
    `now + 59s` refreshes. A 401 refresh clears all Spotify keys; a 500 refresh leaves keys intact
    and returns null.
18. **Cookie persistence order**: `connectWithCookies` persists `sp_dc` even when the token call
    fails, and the failure propagates.
19. **Cookie parsing (UI)**: a cookie header `"a=1; sp_dc=abc; sp_key=def; x=y"` yields
    `spDc="abc"`, `spKey="def"`; no `sp_dc` yields no callback; callback fires at most once.

### 14.3 Spotify GraphQL/REST

20. **GQL body**: a request for `libraryV3` contains `operationName`, `persistedQuery.version=1`
    and the primary hash; `variables` match the documented filters.
21. **Hash fallback**: fake engine rejects the primary hash with a 412, accepts the previous hash;
    exactly two requests occur and the result succeeds. Rejecting both calls `onHashExpired` once
    and fails with 412.
22. **Errors message detection**: HTTP 200 with `errors[0].message` containing
    `PersistedQueryNotFound` is treated as not-found, not as a generic 400.
23. **429**: fake engine returns 429 with `Retry-After: 0` twice then 200; the call succeeds and
    waits per attempt. With `failFastOn429=true` and `Retry-After: 99`, a REST call fails
    immediately with `retryAfterSec=99`.
24. **401**: no retry and `SpotifyException(401)`.
25. **Missing roots**: `me` with `{}` falls back to REST; `myPlaylists` with `{}` fails 500.
26. **Parsing fixtures**: a `fetchPlaylist` fixture with nested playlist/track wrappers produces
    the expected title, owner, image, track count and `uid`; a `searchDesktop` fixture filters out
    non-`Track` wrappers and returns null for empty album/artist/playlist sections.
27. **Duration variants**: each documented duration shape yields the same ms value; seconds are
    multiplied by 1000.
28. **Hash registry**: unknown operation throws; `updateHashes` counts changed vs unchanged and
    preserves `previousHash`; concurrent reads/writes do not throw.

### 14.4 Spotify matching, resolver, queue, import

29. **Normalisation**: `"Song (feat. X) [Live] - Remastered 2011!"` normalises to `"song"` (or the
    documented reduced form) deterministically; caching returns the same value.
30. **Score weights**: identical title/artist and 0 s difference → 1.0; a 31 s difference costs
    the full 0.20; absent duration gives 0.5 for the duration component.
31. **Resolver threshold**: candidates scoring 0.86 return immediately; 0.56 stops further queries
    but returns the candidate; 0.34 returns null; title similarity below 0.25 is skipped.
32. **Resolver cache/concurrency**: two concurrent `resolveTrack` calls for the same id cause one
    search; the second waits and returns the cached item. Blank ids are not cached.
33. **Queue order/unresolved/duplicates**: reuse and extend `SpotifyQueueTest` expectations:
    order preserved, selected occurrence index correct, unresolved tracks skipped and index
    adjusted, duplicates preserved, `preloadItem` used when the selected track fails.
34. **Queue paging**: with 60 tracks and `startIndex=10`, the initial window is 30 and `nextPage`
    returns 25 then 5; `hasPendingSongs` is true until exhausted.
35. **Import source mapping**: playlist/liked source ids, local playlist ids, subtitles and counts
    match the table.
36. **Playlist URL parsing**: accept all four documented forms; reject blank, a track URL, and a
    short bare id. Invalid input throws `IllegalArgumentException`.
37. **Import paging**: a fake API with 120 liked tracks fetches pages 50/50/20 and stops; blank
    titles are dropped.
38. **Import mirroring**: one source with 3 tracks, 1 unmatched, writes playlist entity + 2 songs +
    2 ordered maps atomically and reports `total=3, imported=2, failed=1`; an empty source creates
    an empty playlist with zero counts.
39. **Import 401 retry**: first call 401 triggers a token refresh and a second call succeeds; a
    second 401 surfaces; with no `sp_dc`, the original 401 surfaces.
40. **Import progress**: a fixture with 2 sources × 2 tracks emits percentages 0, 25, 50, 75, 100
    in monotonic order (allowing the documented source-boundary snapshots) and never outside
    0..100.
41. **ViewModel state machine**: initial loading → authenticated + all sources selected; toggling;
    `canImport` false while progress exists; failed import sets `errorMessage` and clears
    progress; cancellation clears progress and does not set an error; logout during progress is a
    no-op and afterwards resets state.
42. **Fast sync**: unmatched local ids are skipped; a recognized liked/playlist id mirrors;
    an arbitrary id is a no-op.

### 14.5 Recommendations, scrobbling, ListenBrainz, taste

43. **Recommendations**: no session → null; empty local history and no top artists → null; one
    resolvable seed → one `SimilarRecommendation`; seed exceptions do not prevent other seeds.
44. **Scrobble thresholds**: duration 200 s, percent 0.5, absolute 50 → fires at 50 s; duration
    40 s → fires at 20 s; duration 10 s (< min 30) → never; unknown duration uses 300 s and still
    respects the absolute cap; fires at most once per track.
45. **Scrobble timestamp**: after 50 ticks, `timestamp = nowSeconds - 50` (fixed clock).
46. **Pause/resume**: pause stops accumulation; resume continues from the accumulated value rather
    than resetting. Transitioning while playing switches metadata and resets the counter.
47. **Destroy**: after `destroy`, no further now-playing or scrobble call is made even if the
    scope is advanced.
48. **Last.fm signing**: a fixture parameter map produces the exact sorted `key+value+secret` MD5;
    the request body contains `api_sig` and `format=json`; no secret appears in logs.
49. **Last.fm mobile session error**: `{"error":4,"message":"Authentication Failed"}` throws
    `LastFmException(4, ...)`; success maps `session.name/key/subscriber`.
50. **ListenBrainz playing_now**: exact JSON shape, headers, `playing_now`, no `listened_at`;
    blank token/title → false with no request.
51. **ListenBrainz finished**: `single`, `listened_at = startMs/1000`; a start below the minimum is
    replaced by the fixed current epoch; blank release name is omitted; HTTP 500 → false.
52. **Taste API**: object-or-array roots parse; `error` roots yield empty success; blank API key
    yields empty without a request; malformed numeric fields become null/0.

### 14.6 Listen Together

53. **Codec JSON round-trip**: every JSON payload model encodes to `{"type","payload"}` and decodes
    back with the documented field names.
54. **Protobuf round-trip**: every supported payload encodes to an `Envelope` and decodes back;
    `TrackInfo` empty optional strings become null; `position/volume/server_time <= 0` become null.
55. **Compression**: a payload over 100 bytes compresses, sets `compressed=true`, and round-trips;
    a smaller payload stays uncompressed; corrupted gzip falls back to raw bytes.
56. **Format detection/upgrade**: a JSON frame starting `{` is JSON; a binary frame is Protobuf and
    permanently upgrades the client; a JSON client receiving binary does not throw.
57. **Message constants**: every documented client/server type and action string equals the
    protocol constant.
58. **Room lifecycle**: fake WebSocket sends `room_created` → role HOST, `isInRoom=true`, session
    persisted; `join_approved` → GUEST and state adopted; `join_rejected` emits the event.
59. **Join gating**: blocked username auto-rejects with `"You are blocked"`; auto-approval on sends
    `approve_join`; with notifications denied no notification is posted but the event still
    arrives.
60. **Reconnect**: a dropped socket with a session backoffs and sends `reconnect`;
    `reconnected` restores role/room; 15 failures end in ERROR while retaining the session for
    manual reconnect; explicit `disconnect` never reconnects.
61. **Ping/RTT**: a `pong` after a fixed 120 ms sets `rtt=120`; an unsolicited pong does not change
    RTT.
62. **Manager permissions**: host may control; guest cannot unless participant control is true;
    `sendTrackChange` during sync is suppressed; guest mute is saved/restored.
63. **Playback sync**: a `play` action with `serverTime = now-1000` seeks to
    `position+1000`; a duplicate within 1000 ms is debounced; a 2500 ms drift while paused seeks,
    a 1500 ms drift does not.
64. **Buffer**: `buffer_wait` stores a pending state, `buffer_complete` applies it; no playback
    mutation occurs before completion.
65. **Chat reply**: `sendChatMessage("hi", RepliedMessage("u","m"))` produces a `chat` payload whose
    visible prefix is the zero-width `[RPLY:<base64>]` marker; the receiver strips it and attaches
    the reply; malformed markers degrade to plain text.
66. **Server directory**: default URL is the first entry; `findByUrl` is exact-match.

### 14.7 Cast and artwork

67. **Cast options**: receiver id is the default media receiver; notification actions are the four
    documented actions with compact indices `[1,2]`; resume/reconnection flags are true.
68. **Cast availability**: `initialize`/`isCastAvailable` return false when Play Services throws,
    and do not throw.
69. **Cast media info**: a track with title/artists/album/thumbnail builds metadata with the exact
    keys; a null stream URL returns null; custom data contains `mediaId`.
70. **Queue load**: a fixture player with previous/current/next items builds 5 queue items and a
    start index of `prevItems.size`; a receiver rejection retries twice then reports failure.
71. **Device kind**: `"Chromecast with Google TV"` → TV; `"Nest Mini"` → SPEAKER; `"Chromecast"` →
    CHROMECAST; `"Unknown"` → UNKNOWN.
72. **Artwork URI**: an `https` URL maps to a 3-segment `content://` URI; the same URL returns a
    cached equal URI; `android.resource://` and null pass through unchanged.
73. **Artwork signature**: `originalUrl` returns the URL only for a correctly signed URI; flipping
    one signature character returns null; wrong authority/path length returns null; a `file://`
    decoded URL is rejected.
74. **Provider security**: `openFile` with mode `"w"` throws `SecurityException`; an unsigned URI
    throws `FileNotFoundException`; a signed URI for an unavailable URL throws
    `FileNotFoundException`; a cached file opens read-only with type `image/jpeg`.
75. **Artwork cache**: a fixture cache over 64 MiB trims to at most 48 MiB, oldest first; a
    concurrent reader never observes a partial file (temp+rename); a second call returns the file
    without refetching and updates `lastModified`.

### 14.8 Build isolation

76. **FOSS build**: compiling the `foss` flavour does not compile `app/src/gms` and does not
    reference Cast classes; `BuildConfig.CAST_AVAILABLE` is false.
77. **GMS build**: `gms` compiles the Cast sources, links MediaRouter/Cast, and sets
    `CAST_AVAILABLE=true`.

---

## 15. Known Limitations and Non-Goals

- Spotify uses internal endpoints and persisted-query hashes; hash rotation is expected and handled
  by remote refresh, but no availability guarantee exists.
- Spotify import is a metadata match, not an audio copy; unmatched tracks are reported, not retried
  indefinitely.
- Cloud sync is additive/upsert-only and does not prune deleted remote items.
- Navidrome passwords are stored in plaintext preferences; Jellyfin tokens are stored unencrypted.
- Listen Together chat is JSON-only; the Protobuf schema does not carry chat payloads.
- Cast is unavailable in FOSS builds by design.
- Automotive artwork depends on the app's exported provider; the signing key is per-install and
  not shared across devices.
