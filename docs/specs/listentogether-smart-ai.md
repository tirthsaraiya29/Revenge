# Clean-Room Specification — `listentogether-smart-ai`

Status: M2 behavioural specification. No implementation bodies are reproduced. Contract is
limited to: public API, wire protocol (message types, fields, tags), permissions, mandated
strings, observable ordering and error outcomes, and the acceptance tests in §14. Everything
else — helper decomposition, internal data structures, exact loops/batching, and code shape —
is illustrative.

> **Implementation-shape note.** The algorithm shape described in this document is
> illustrative. Implementers design their own internal implementation that satisfies the
> observable contracts below, and are similarity-audited against the original source. A stated
> constant or formula is a behavioural requirement (pinned by an acceptance test), not a demand
> to mirror the original code.

Working directory: `c:/Users/tirth/Downloads/Projects/Revenge`.
Module: `:app` (app sources) with one consumed contract from `:lyrics`, `:core`, `:innertube`,
`:playback`, `:domain`.

Cross-module rule: every package name below is compile-visible to the rest of the app and must be
preserved verbatim.

---

## 1. Build contract

`app/build.gradle.kts` must keep:
- `alias(libs.plugins.protobufPlugin)`, `protobuf { protoc { artifact = com.google.protobuf:protoc:<version> } generateProtoTasks { all().forEach { task -> task.builtins { create("java") { option("lite") }; create("kotlin") { option("lite") } } } } }`.
- `app/src/main/proto/listentogether.proto` is the only hand-written proto. Generated classes are
  `com.revenge.music.listentogether.proto.Listentogether` with `java_multiple_files = false`,
  `java_outer_classname = "Listentogether"`, `java_package = com.revenge.music.listentogether.proto`.
  `option lite` means only `MessageLite` / `parseFrom(ByteArray)` / `toByteArray()` are available.
- Dependencies used by this cluster: `protobuf-javalite`, `protobuf-kotlin-lite`, OkHttp, Ktor
  client core + OkHttp + content-negotiation + serialization-json, Media3 (common/session), Hilt,
  Room runtime/ktx, DataStore, WorkManager, Compose runtime/ui, Coil, Timber, `kotlinx-serialization-json`.

Manifest entries owned by the cluster (must stay):
- `ListenTogetherActionReceiver` (`android:exported="false"`).
- Permissions: `INTERNET`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `ACCESS_COARSE_LOCATION`,
  `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE` (+ subtype permissions already present).

---

## 2. Target files and responsibilities

### 2.1 `listentogether` (package `com.revenge.music.listentogether`)
| File | Responsibility |
|---|---|
| `Protocol.kt` | Serde data classes, `MessageTypes`, `PlaybackActions`. |
| `MessageCodec.kt` | JSON/protobuf envelope encode/decode, format detection, optional GZIP, payload mapping. |
| `ListenTogetherServers.kt` | Static server registry and default URL. |
| `ListenTogetherClient.kt` | Singleton WebSocket client, state flows, events, connection lifecycle, session persistence, notifications, room actions. |
| `ListenTogetherManager.kt` | Playback bridge: Player listener, guest/host sync, queue/volume/heartbeat observation, mute handling. |
| `ListenTogetherActionReceiver.kt` | Broadcast receiver for notification actions. |
| `app/src/main/proto/listentogether.proto` | Wire definitions (see §3.4). |

### 2.2 `musicflow` (package `com.revenge.music.musicflow`)
`MusicFlowModels.kt` (enums, `MusicFlowItem`, UI state, MediaMetadata mapper), `MusicFlowPreferences.kt`
(preference projection), `MusicFlowRanking.kt` (candidate filter + ranker), `MusicFlowRepository.kt`
(Innertube-backed `MusicFlowFeedSource` + radio cursor), `MusicFlowSession.kt` (append-only paged
feed), `MusicFlowCoordinator.kt` (`MusicFlowPlayer` interface + request arbitration), `MusicFlowQueue.kt`
(service `Queue` adapter), `PlayerConnectionMusicFlowPlayer.kt` (Media3 adapter).

### 2.3 `generate` (package `com.revenge.music.generate`)
`GenerationStatus.kt`, `SongPlayStatsRepository.kt`, `LocalTasteEngine.kt`, `RecommendationEngine.kt`,
`GenresRepository.kt`, `TasteProfileProvider.kt`.

### 2.4 `ai` (package `com.revenge.music.ai`, `...ai.weather`)
`AiPlaylistGenerator.kt`, `AiPlaylistModifier.kt`, `AiRecommendationHelper.kt`, `AiRecommendationWorker.kt`;
`weather/WeatherModels.kt`, `weather/WeatherRepository.kt`, `weather/IpLocationRepository.kt`,
`weather/LocationProvider.kt`, `weather/WeatherCoverOverlay.kt`.

### 2.5 remaining cluster dirs
`api/FlowNeuroengineApi.kt` (declared external DTO/API contract, currently no call site);
`localmedia/LocalSongScanner.kt`, `localmedia/SupportedLocalAudio.kt`;
`podcast/PodcastVideoProvider.kt`.

### 2.6 `utils` — files owned/specified here
`EpisodeDates.kt`, `NewEpisodesWorker.kt`, `PodcastProgress.kt`, `PodcastShowStore.kt`,
`LocalFileDownloader.kt`, `LocalMediaIntents.kt`, `QuickPickSongs.kt`, `ScrobbleManager.kt`,
`SyncUtils.kt`, `NetworkUtils.kt`, `lastfm/LastFM.kt`, `lastfm/LastFmTasteApi.kt`.
Other `utils` files (`cipher/*`, `potoken/*`, `sabr/*`, `Automotive*`, `Coil*`, `Compose*`,
`Haptic*`, `Icon*`, `Shapes*`, `Wikipedia`, `YTPlayerUtils`, `InnerTubeXResolver`, `Fix403`,
`BotDetectionMitigator`, `AppleMusicAboutAlbum`, `WebAuthSessionCleaner`, `RingtoneHelper`,
`HeroCarouselSongs`, `PlaylistNavigation`, `StringUtils`, `DataStoreCompose`, `Utils`,
`YouTubeThumbnailFallbackInterceptor`, `LocalAlbumArtFetcher`, `PlayerClient`, `CrashHandler`,
`ActivityResultLauncherExt`, `AppContextHolder`) belong to other clusters; only their public
signatures are consumed here and must remain stable.

### 2.7 Tests (existing, must be preserved/extended)
`app/src/test/kotlin/com/revenge/music/generate/`: `GenerationStatusTest`, `GenerateViewModelTest`,
`LocalTasteScoringTest`, `RecommendationScoringTest`, `RecommendationEngineTest`,
`GenresRepositoryTest`, `SongPlayStatsRepositoryTest`, `TasteProfileProviderTest`.
`app/src/test/kotlin/com/revenge/music/utils/`: `ScrobbleManagerTest`, `QuickPickSongsTest`.
`lyrics/src/test/kotlin/com/revenge/music/api/AiTranslationParsingTest`.
No tests currently exist for `listentogether`, `musicflow`, `localmedia`, `podcast`, `ai` — the
acceptance tests in §12 define them.

---

## 3. Listen Together wire contract

### 3.1 Transport
- One OkHttp WebSocket to the URL stored at `ListenTogetherServerUrlKey` (default
  `wss://metroserverx.meowery.eu/ws`). Timeouts: connect 30 s, read 60 s, write 30 s, OkHttp
  ping 30 s.
- Application ping every 25 s (`PING`); server answers `PONG`.
- Either text or binary frames are accepted; text is converted to UTF-8 bytes before decoding.
- Outgoing messages are sent as binary frames (`okio.ByteString`).
- Only one active socket; `connect()` is idempotent while CONNECTED/CONNECTING.

### 3.2 Envelope and format negotiation
- Protobuf envelope: `Envelope { string type = 1; bytes payload = 2; bool compressed = 3; }`.
- JSON envelope: `Message { "type": string, "payload": <JSON object or null> }`.
- Format detection per frame: empty → JSON; first byte `'{'` (0x7B) → JSON; otherwise Protobuf.
- The client starts in JSON, uncompressed. On the first received Protobuf frame it upgrades
  permanent outgoing format to Protobuf **with compression enabled**.
- Compression: GZIP; only when serialized payload/frame is `> 100` bytes and compression reduces
  size. Protobuf uses `envelope.compressed`; JSON uses a whole-frame gzip (magic `1f 8b`).
- Required invariant (fixes an observed gap): payload types that have no protobuf definition
  (§3.5: `ChatPayload`, `ChatMessagePayload`) must still be encodable after the upgrade. The
  encoder must select JSON framing per message for such types (or a proto definition must be
  added) and must never throw out to the socket layer.
- Unknown message types are logged and dropped, never fatal.
- Malformed frame/JSON/protobuf is logged and dropped; connection stays open.

JSON rules: `ignoreUnknownKeys = true`, `isLenient = true`, `encodeDefaults = true` for payloads.
All nullable proto strings map empty string ⇄ `null`; numeric optionals map `<= 0` ⇄ `null`
(`position`, `volume`, `serverTime`). `queue` in `SyncStatePayload` is `null` when empty in JSON and
an empty list in protobuf.

### 3.3 Message types (exact strings)
Client→server: `create_room`, `join_room`, `leave_room`, `approve_join`, `reject_join`,
`playback_action`, `buffer_ready`, `kick_user`, `transfer_host`, `ping`, `chat`, `request_sync`,
`reconnect`, `suggest_track`, `approve_suggestion`, `reject_suggestion`, `update_room_settings`.
Server→client: `room_created`, `join_request`, `join_approved`, `join_rejected`, `user_joined`,
`user_left`, `sync_playback`, `buffer_wait`, `buffer_complete`, `error`, `pong`, `host_changed`,
`kicked`, `sync_state`, `reconnected`, `user_reconnected`, `user_disconnected`,
`suggestion_received`, `suggestion_approved`, `suggestion_rejected`, `chat`, `room_settings_changed`.

Playback action values: `play`, `pause`, `seek`, `skip_next`, `skip_prev`, `change_track`,
`queue_add`, `queue_remove`, `queue_clear`, `sync_queue`, `set_volume`.

### 3.4 Protobuf field numbers (authoritative)
All fields are proto3; all messages live in `Listentogether`.

| Message | Fields (tag: type name) |
|---|---|
| `TrackInfo` | 1:string id, 2:string title, 3:string artist, 4:string album, 5:int64 duration, 6:string thumbnail, 7:string suggested_by |
| `UserInfo` | 1:string user_id, 2:string username, 3:bool is_host, 4:bool is_connected |
| `RoomState` | 1:string room_code, 2:string host_id, 3:repeated UserInfo users, 4:TrackInfo current_track, 5:bool is_playing, 6:int64 position, 7:int64 last_update, 8:float volume, 9:repeated TrackInfo queue, 10:bool allow_participant_control |
| `UpdateRoomSettingsPayload` | 1:bool allow_participant_control |
| `CreateRoomPayload` | 1:string username |
| `JoinRoomPayload` | 1:string room_code, 2:string username |
| `ApproveJoinPayload` | 1:string user_id |
| `RejectJoinPayload` | 1:string user_id, 2:string reason |
| `PlaybackActionPayload` | 1:string action, 2:string track_id, 3:int64 position, 4:TrackInfo track_info, 5:bool insert_next, 6:repeated TrackInfo queue, 7:string queue_title, 8:float volume, 9:int64 server_time |
| `BufferReadyPayload` | 1:string track_id |
| `KickUserPayload` | 1:string user_id, 2:string reason |
| `TransferHostPayload` | 1:string new_host_id |
| `SuggestTrackPayload` | 1:TrackInfo track_info |
| `SuggestionReceivedPayload` | 1:string suggestion_id, 2:string from_user_id, 3:string from_username, 4:TrackInfo track_info |
| `ApproveSuggestionPayload` | 1:string suggestion_id |
| `RejectSuggestionPayload` | 1:string suggestion_id, 2:string reason |
| `SuggestionApprovedPayload` | 1:string suggestion_id, 2:TrackInfo track_info |
| `SuggestionRejectedPayload` | 1:string suggestion_id, 2:string reason |
| `RoomCreatedPayload` | 1:string room_code, 2:string user_id, 3:string session_token |
| `JoinRequestPayload` | 1:string user_id, 2:string username |
| `JoinApprovedPayload` | 1:string room_code, 2:string user_id, 3:string session_token, 4:RoomState state |
| `JoinRejectedPayload` | 1:string reason |
| `UserJoinedPayload` | 1:string user_id, 2:string username |
| `UserLeftPayload` | 1:string user_id, 2:string username |
| `BufferWaitPayload` | 1:string track_id, 2:repeated string waiting_for |
| `BufferCompletePayload` | 1:string track_id |
| `ErrorPayload` | 1:string code, 2:string message |
| `HostChangedPayload` | 1:string new_host_id, 2:string new_host_name |
| `KickedPayload` | 1:string reason |
| `SyncStatePayload` | 1:TrackInfo current_track, 2:bool is_playing, 3:int64 position, 4:int64 last_update, 5:repeated TrackInfo queue, 6:float volume |
| `ReconnectPayload` | 1:string session_token |
| `ReconnectedPayload` | 1:string room_code, 2:string user_id, 3:RoomState state, 4:bool is_host |
| `UserReconnectedPayload` | 1:string user_id, 2:string username |
| `UserDisconnectedPayload` | 1:string user_id, 2:string username |
| `Envelope` | 1:string type, 2:bytes payload, 3:bool compressed |

JSON key mapping (non-default keys): `suggested_by`, `user_id`, `is_host`, `is_connected`,
`room_code`, `host_id`, `current_track`, `is_playing`, `last_update`,
`allow_participant_control`, `track_id`, `insert_next`, `queue_title`, `server_time`, `new_host_id`,
`new_host_name`, `session_token`, `suggestion_id`, `from_user_id`, `from_username`, `reply_to`,
`waiting_for`. Keys not listed keep their Kotlin name (`action`, `position`, `duration`, `volume`,
`reason`, `queue`, `users`, `state`, `username`, `title`, `artist`, `album`, `thumbnail`, `id`,
`name`, `track_info`, `type`, `payload`).

`TrackInfo.duration`, `RoomState.position`, `RoomState.lastUpdate`, `SyncStatePayload.position`,
`SyncStatePayload.lastUpdate`, `PlaybackActionPayload.position` and `serverTime` are **milliseconds**.
`volume` is `0f..1f`.

### 3.5 Serialisable payload types
`TrackInfo`, `UserInfo`, `RoomState`, `UpdateRoomSettingsPayload`, `CreateRoomPayload`,
`JoinRoomPayload`, `ApproveJoinPayload`, `RejectJoinPayload`, `PlaybackActionPayload`,
`BufferReadyPayload`, `KickUserPayload`, `TransferHostPayload`, `ChatPayload` (`message`,
`reply_to: RepliedMessage?`), `RepliedMessage` (`username`, `message`), `SuggestTrackPayload`,
`SuggestionReceivedPayload`, `ApproveSuggestionPayload`, `RejectSuggestionPayload`,
`SuggestionApprovedPayload`, `SuggestionRejectedPayload`, `RoomCreatedPayload`,
`JoinRequestPayload`, `JoinApprovedPayload`, `JoinRejectedPayload`, `UserJoinedPayload`,
`UserLeftPayload`, `BufferWaitPayload`, `BufferCompletePayload`, `ErrorPayload`,
`ChatMessagePayload` (`user_id`, `username`, `message`, `timestamp`, `reply_to?`),
`HostChangedPayload`, `KickedPayload`, `SyncStatePayload`, `ReconnectPayload`,
`ReconnectedPayload`, `UserReconnectedPayload`, `UserDisconnectedPayload`.

### 3.6 Server registry
`ListenTogetherServer(name, url, location, operator)`; one entry:
`("Metrolist Server", "wss://metroserverx.meowery.eu/ws", "Poland", "Metrolist")`.
Exposes `serversFlow: StateFlow<List<ListenTogetherServer>>`, `servers`, `defaultServerUrl`
(first URL), `findByUrl(url)`. Registry is static; no runtime mutation.

### 3.7 Public client surface (`ListenTogetherClient`)
Singleton: `@Singleton @Inject constructor(context)`; also process singleton via
`getInstance(): ListenTogetherClient?` / `setInstance(client)`; constructor calls `setInstance`.
`companion` constants: `ACTION_APPROVE_JOIN`, `ACTION_REJECT_JOIN`,
`ACTION_APPROVE_SUGGESTION`, `ACTION_REJECT_SUGGESTION`, `EXTRA_USER_ID`,
`EXTRA_SUGGESTION_ID`, `EXTRA_NOTIFICATION_ID`.

Enums: `ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR }`,
`RoomRole { HOST, GUEST, NONE }`, `MessageFormat { JSON, PROTOBUF }`, `LogLevel { INFO, WARNING, ERROR, DEBUG }`.
`LogEntry(timestamp, level, message, details?)`. `PendingAction` sealed (`CreateRoom(username)`,
`JoinRoom(roomCode, username)`).

`ListenTogetherEvent` sealed: `Connected(userId)`, `Disconnected`, `ConnectionError(error)`,
`Reconnecting(attempt, maxAttempts)`, `RoomCreated(roomCode, userId)`,
`JoinRequestReceived(userId, username)`, `JoinApproved(roomCode, userId, state)`,
`JoinRejected(reason)`, `UserJoined(userId, username)`, `UserLeft(userId, username)`,
`HostChanged(newHostId, newHostName)`, `Kicked(reason)`,
`Reconnected(roomCode, userId, state, isHost)`, `UserReconnected(userId, username)`,
`UserDisconnected(userId, username)`, `PlaybackSync(action: PlaybackActionPayload)`,
`BufferWait(trackId, waitingFor)`, `BufferComplete(trackId)`, `SyncStateReceived(state)`,
`ServerError(code, message)`, `ChatMessageReceived(payload)`,
`LocalSuggestionApproved(payload: SuggestionReceivedPayload)`, `RoomSettingsChanged(allowParticipantControl)`.

Flows (read-only exposure; `events` is a broadcast event stream with capacity 64 that drops the
oldest event under back-pressure):
`connectionState`, `roomState: StateFlow<RoomState?>`, `role`, `userId`, `pendingJoinRequests`,
`bufferingUsers`, `pendingSuggestions`, `blockedUsernames: StateFlow<Set<String>>`,
`logs: StateFlow<List<LogEntry>>` (newest last, max 500), `events`, `rtt: StateFlow<Long>`.

Read-only properties: `isInRoom` (`roomState != null`), `isHost`, `hasPersistedSession`
(`sessionToken != null && storedRoomCode != null`).

Operations: `connect()`, `disconnect()`, `createRoom(username)`, `joinRoom(roomCode, username)`,
`leaveRoom()`, `approveJoin(userId)`, `rejectJoin(userId, reason?)`, `kickUser(userId, reason?)`,
`transferHost(newHostId)`, `updateRoomSettings(allowParticipantControl)`,
`sendPlaybackAction(action, trackId?, position?, trackInfo?, insertNext?, queue?, queueTitle?, volume?)`,
`sendChatMessage(message, replyTo?)`, `sendBufferReady(trackId)`, `suggestTrack(trackInfo)`,
`approveSuggestion(suggestionId)`, `rejectSuggestion(suggestionId, reason?)`, `requestSync()`,
`blockUser(username)`, `unblockUser(username)`, `isUserBlocked(username)`, `forceReconnect()`,
`clearLogs()`, `getPersistedRoomCode(): String?`, `getSessionAge(): Long`.

### 3.8 Connection lifecycle and recovery
- `connect()`: if CONNECTED/CONNECTING do nothing (log warning); set CONNECTING; resolve URL;
  force codec JSON/uncompressed; open socket.
- On open: set CONNECTED, reset attempts, start 25 s ping loop. If a persisted session token and
  room exist, send `RECONNECT{token}`; otherwise execute the queued `PendingAction`.
- `createRoom`/`joinRoom` clear any prior session first, store `username`, and either send
  immediately when CONNECTED or queue as `PendingAction` and connect. `joinRoom` uppercases the
  room code on send.
- `disconnect()`: release wakelock, cancel ping, close socket (1000), clear all room/session state,
  emit `Disconnected`.
- Network observer: on loss → log only, state DISCONNECTED (no retry loop). On restore, if state is
  DISCONNECTED/ERROR and (session token or room state or pending action exists), reset attempts and
  `connect()`.
- Reconnect attempt budget: 15. Backoff `delay = 1000ms * 2^(min(attempt-1,4)+1)`, capped at
  120000 ms, plus uniform jitter `0..20%`. Emit `Reconnecting`. Retry only if a session token,
  room state, or pending action exists. When budget is exhausted: state ERROR; emit
  `ConnectionError`; if a session token existed keep it (manual reconnect possible), otherwise
  clear session.
- `forceReconnect()`: reset attempts, close socket, DISCONNECTED, connect after 500 ms.
- WAKELOCK: partial wakelock `"revenge:ListenTogether"`, 10-minute timeout, acquired on room
  create/join/reconnect, released on disconnect/leave/kick.

### 3.9 Session persistence
DataStore keys: `ListenTogetherSessionTokenKey`, `ListenTogetherRoomCodeKey`,
`ListenTogetherUserIdKey`, `ListenTogetherIsHostKey`, `ListenTogetherSessionTimestampKey`,
`ListenTogetherBlockedUsersKey`.
- Session is written after room_created / join_approved / reconnected and contains token, room
  code, user id, host flag, current wall-clock ms.
- On startup, restore only when token and room code are non-empty and age < 10 minutes; otherwise
  clear. Expired sessions log a warning.
- `blockedUsernames` is a JSON `string[]` in `ListenTogetherBlockedUsersKey`, loaded at startup,
  saved on every block/unblock. Blocking also removes matching pending join requests and pending
  suggestions.
- DataStore failures never throw out of the client.

### 3.10 Permission gating (client side)
- Host-only: `approveJoin`, `rejectJoin`, `kickUser`, `transferHost`, `updateRoomSettings`,
  `approveSuggestion`, `rejectSuggestion`. Non-host calls log an error and send nothing.
- Playback control: HOST, or GUEST when `roomState.allowParticipantControl == true`.
- `suggestTrack`: only GUEST in a room; HOST logs a warning.
- `sendChatMessage`: only when in room; blank messages ignored by the manager.
- `requestSync`: only when room state exists.
- `approveSuggestion` optimistically emits `LocalSuggestionApproved` for the matching pending
  suggestion and removes it.
- `updateRoomSettings` optimistically updates local `roomState.allowParticipantControl`.

### 3.11 Inbound message semantics (client)
| Type | Effect |
|---|---|
| `room_created` | userId, HOST, token, room code; constructs initial `RoomState` with one user, not playing, position 0, volume 1f; persists; wakelock; Toast; event. |
| `join_request` | If username blocked → log + `rejectJoin(userId, "You are blocked")`. Else append to `pendingJoinRequests`. When HOST: auto-approve if `ListenTogetherAutoApprovalKey`, else post notification if POST_NOTIFICATIONS granted. Emit event. |
| `join_approved` | userId, GUEST, token, room code, replace room state; persist; wakelock; event. |
| `join_rejected` | log; emit event. |
| `user_joined` | append `UserInfo(..., isHost=false)`; remove pending request and its notification; event. |
| `user_left` | remove user; event. |
| `host_changed` | set hostId; recompute `isHost` flags; role HOST iff new host is self, else GUEST if was HOST; event. |
| `kicked` | release wakelock; clear token, room, role; event. |
| `sync_playback` | mutate room state per action (§3.12); emit `PlaybackSync`. |
| `buffer_wait` | replace `bufferingUsers` with `waitingFor`; event. |
| `buffer_complete` | clear `bufferingUsers`; event. |
| `sync_state` | emit only (manager applies when guest restricted). |
| `suggestion_received` | If HOST and sender not blocked: append pending, Toast, notify if permitted. Ignored for guests and blocked senders. |
| `suggestion_approved`/`rejected` | log; cancel the stored notification; no state change. |
| `error` | log; emit `ServerError`. Special case `code == "session_not_found"`: if stored room+username and guest → auto `joinRoom` after 500 ms; if host → clear persisted session/token; otherwise clear persisted session/token. |
| `pong` | RTT = now − pingSentTime (only if a ping is outstanding); event not emitted. |
| `reconnected` | userId, role from `isHost`, replace state, persist, reset attempts, wakelock, event. |
| `user_reconnected` / `user_disconnected` | flip `UserInfo.isConnected` for that user; event. |
| `chat` | decode reply (§3.13); append event. |
| `room_settings_changed` | set `allowParticipantControl`; event. |
| unknown | warning log. |

### 3.12 `sync_playback` local state transitions
- `play`: `isPlaying=true`, `position = payload.position ?: current`.
- `pause`: `isPlaying=false`, `position = payload.position ?: current`.
- `seek`: `position = payload.position ?: current`.
- `change_track`: `currentTrack = trackInfo`, `isPlaying=false`, `position=0`. (Full `queue`/`queueTitle` handled by the manager, not the client state.)
- `queue_add`: append `trackInfo`; prepend if `insertNext == true`.
- `queue_remove`: remove queue items with `id == trackId`.
- `queue_clear`: empty queue.
- `set_volume`: `volume = payload.volume.coerceIn(0f,1f)`.

### 3.13 Chat reply encoding
A reply is transported inside the message text as
`"\u200B[RPLY:" + Base64(NO_WRAP, UTF-8("username|message")) + "]\u200B" + actualMessage`.
Decoding: if the text starts with the marker and contains `"]\u200B"`, split the decoded payload
at the first `|` into `(username, message)`, set `replyTo`, and strip the prefix. Malformed base64
is logged and the raw message is delivered unchanged. `ChatPayload.reply_to` is also sent for JSON
servers.

### 3.14 Notifications
Channel: `listen_together_channel`, IMPORTANCE_HIGH, name/description from string resources.
Join-request and suggestion notifications get a fresh notification id, tracked per user id /
suggestion id, and two actions (approve/reject) backed by distinct immutable broadcast
`PendingIntent`s that carry the target id. Only posted when `POST_NOTIFICATIONS` permission is
granted. The receiver cancels the notification, then invokes the matching client action with the
extra id; missing extras are ignored; a missing client instance is ignored.

### 3.15 Error taxonomy
| Condition | Outcome |
|---|---|
| Frame decode failure | logged (`ERROR`), dropped, socket remains open |
| Unknown type | logged (`WARNING`), dropped |
| Unsupported payload for protobuf | must fall back to JSON framing (§3.2), never disarm socket |
| Send failure (`WebSocket.send` false / exception) | logged (`ERROR`), no local state rollback |
| Reconnect budget exhausted | `ConnectionState.ERROR`, `ConnectionError` event; token kept only if present |
| `session_not_found` | auto-rejoin (guest) or session clear (host) |
| Persistence/DataStore failure | logged, non-fatal |
| Notification creation failure | logged (`WARNING`) |

---

## 4. Listen Together manager (playback bridge)

`@Singleton ListenTogetherManager(client, @ApplicationContext context)`; scope =
`Dispatchers.Main + SupervisorJob`. Aliases every client flow/state and adds:
`allowParticipantControl`, `canControlMusic` (= `!isInRoom || isHost || allowParticipantControl`),
`isGuestPlaybackRestricted` (= `isInRoom && !canControlMusic`),
`guestPlaybackRestricted: StateFlow<Boolean>`, `chatMessages: MutableStateFlow<List<ChatMessagePayload>>`.

Public operations mirror the client: `connect`, `disconnect`, `createRoom`, `joinRoom`, `leaveRoom`,
`approveJoin`, `rejectJoin`, `kickUser`, `blockUser`, `unblockUser`, `getBlockedUsernames`,
`transferHost`, `updateRoomSettings`, `sendTrackChange(MediaMetadata)`, `requestSync`, `clearLogs`,
`suggestTrack`, `approveSuggestion`, `rejectSuggestion`, `forceReconnect`,
`getPersistedRoomCode`, `getSessionAge`, `sendChatMessage(message, replyTo?)`,
`setPlayerConnection(PlayerConnection?)`, `initialize()`.

Constants: sync debounce 1000 ms, position tolerance 2000 ms, playback-position tolerance 3000 ms;
suppression window after a sent action 300 ms (200 ms after applying a guest sync); host heartbeat
10 s; queue observation debounce 500 ms; player-ready wait 5 s.

### 4.1 Player events (host/controller → server)
Observed only while in room and `canControlMusic`; suppressed while a sync is being applied.
- Play/pause state change: if the track changed since the last report, send `CHANGE_TRACK` first;
  then send `PLAY` at the current position when playback starts, or `PAUSE` when it stops and the
  last reported state was playing.
- Track transition: ignore a repeat of the same media id; otherwise send `CHANGE_TRACK` (with the
  current queue and queue title) and, if playing, `PLAY` at the current position.
- Seek: send `SEEK` with the new position.
- Skip-next / skip-previous send the corresponding actions; restart-song sends
  `SEEK(position = 1)` (0 is not transportable as null).

`sendTrackChange` maps `MediaMetadata` → `TrackInfo`: id, title, artist names joined by `", "`,
album title, duration seconds→ms (default `180000` ms when duration ≤ 0), thumbnail, suggestedBy.
The current queue is mapped from timeline windows (unknown metadata → `TrackInfo("unknown",
"Unknown", "Unknown", album "", duration 0, thumbnail "")`).

### 4.2 Guest playback synchronization
Applies only while `isGuestPlaybackRestricted`. Every inbound action sets `isSyncing` and clears it
after 200 ms.
- `PLAY`: target = position + max(0, now − serverTime) when serverTime present. If buffering is
  pending, remember the new target state and wait for buffer completion. Debounce if already
  playing, within 2 s and < 1 s since last sync. While playing, seek only when drift > 3 s; while
  paused, seek when drift > 2 s then play.
- `PAUSE`: analogous; debounce when already paused, in sync and recent; pause then seek if drift > 2 s.
- `SEEK`: debounce within 1 s; seek only when drift > 2 s.
- `CHANGE_TRACK`: with a non-empty queue → replace queue and state; otherwise load the track from
  its radio seed (Innertube queue load, buffer-ready handshake).
- `SKIP_NEXT` / `SKIP_PREV`: delegate to player; no local queue mutation.
- `QUEUE_ADD`: resolve `YouTube.queue([track.id])`; insert next or append; preserve `suggestedBy`;
  wrap player mutation in `allowInternalSync`.
- `QUEUE_REMOVE`: remove the first item after the current one with matching mediaId.
- `QUEUE_CLEAR`: remove all items after the current index.
- `SET_VOLUME`: apply only for guests when `ListenTogetherSyncVolumeKey` is true; clamp 0..1 and set
  `service.playerVolume`.
- `SYNC_QUEUE`: rebuild media items, preserve current media id/position, restore play state, set
  queue title; supersede any in-flight queue sync; ignored if the player connection changed.

A received `sync_state` adjusts the position by `now − lastUpdate` when playing and applies the
state with buffer bypass.

### 4.3 Buffer handshake and state application
Applying a playback state (current track, play state, position, optional queue and queue title,
buffer-bypass flag) follows these observable rules:
- An application is superseded — and must not mutate the player — if a newer application starts or
  the player connection changes before it completes.
- No current track: optionally replace/clear the queue, pause, set the queue title.
- Otherwise set media items (the queue if non-empty and it contains the track, else the single
  track), seek, set the queue title.
- With buffer bypass: wait up to 5 s for the player to become ready, then seek and apply play/pause.
- Without buffer bypass: pause, remember the pending position/play state, send `BUFFER_READY`, and
  apply the pending state only after `BUFFER_COMPLETE` arrives for that same track.
- Loading a single track from a radio seed loads the Innertube radio queue (waiting up to 2 s for
  ready), sets the queue title `"Listen Together"`, pauses, remembers the pending state and sends
  `BUFFER_READY`; failures only log.
- The sync guard `isSyncing` is always cleared, including on failure.

### 4.4 Host-side observers and hooks
- Queue observation: on distinct queue-window changes, debounce 500 ms, send `SYNC_QUEUE` with
  mapped queue and current queue title (host or participant-controlled guest).
- Volume observation: host only, in room, pref enabled; ignore deltas < 0.01; send `SET_VOLUME`.
- Heartbeat: host only, every 10 s while in room, playing and `STATE_READY`; sends `PLAY` at
  current position.
- `setPlayerConnection` installs/removes the listener and sets:
  `shouldBlockPlaybackChanges = { isGuestPlaybackRestricted }`, `onSkipPrevious`, `onSkipNext`,
  `onRestartSong`; clears these hooks on detach.
- Guest mute: on join, remember the current mute state; on cleanup/leave/kick restore it; if none
  was saved, unmute as fallback.
- Events handled: `RoomCreated` (send current track/play state, start observers), `JoinApproved`
  (apply state, host volume, mute), `PlaybackSync`, `UserJoined` (host resends current
  track/play state), `BufferComplete` (guest applies pending), `SyncStateReceived`, `Kicked` /
  `ConnectionError` (cleanup), `Reconnected` (host resends/syncs, guest applies + smart resync
  after 1 s when restricted), `HostChanged` (new host sends current track/play state),
  `LocalSuggestionApproved` (queue `playNext`), `ChatMessageReceived` (append), `RoomSettingsChanged`
  (refresh capabilities).
- Smart resync pref `ListenTogetherSmartResyncKey` (default true) gates the post-reconnect
  `requestSync`.
- `requestSync` is a no-op unless the guest is playback-restricted.

### 4.5 PlayerConnection contract required to compile
`PlayerConnection`: `player: Player`, `service: MusicService`, `queueTitle: StateFlow<String?>`,
`queueWindows: StateFlow<List<Timeline.Window>>`, `isMuted: StateFlow<Boolean>`,
`var shouldBlockPlaybackChanges: (() -> Boolean)?`, `var allowInternalSync: Boolean`,
`var onSkipPrevious/onSkipNext/onRestartSong: (() -> Unit)?`, `playNext(MediaItem)`,
`addToQueue(MediaItem)`, `seekTo(Long)`, `play()`, `pause()`, `setMuted(Boolean)`,
`playQueue(Queue)`, `isPlaying: StateFlow<Boolean>`, `currentMetadata: MediaMetadata?`.
`MusicService`: `activeQueue: StateFlow<Queue>`, `queueTitle: String?`, `playerVolume: MutableStateFlow<Float>`,
`castConnectionHandler?.isCasting: StateFlow<Boolean>`, `playQueue(Queue, playWhenReady)`,
`addToQueue(List<MediaItem>)`, `setPauseAtEndOfItems(Queue, Boolean)`, `snapshotQueue(): QueueSnapshot`,
`restoreQueueSnapshot(QueueSnapshot, Boolean)`.

---

## 5. MusicFlow

### 5.1 Models
`MusicFlowSource { NOW_PLAYING, HISTORY, LIKED, QUICK_PICKS, CATALOG, OFFLINE }`.
`MusicFlowItem(song: SongItem, source)` with `songId = song.id` and
`primaryArtistKey = first artist's id ?: lowercase name`.
`MusicFlowPlaybackStatus { Idle, Switching, Synced, Blocked(TakeoverBlock), ExternalOverride }`;
`TakeoverBlock { CASTING, LISTEN_TOGETHER_GUEST }`; `MusicFlowFeedError { OFFLINE, EMPTY, FAILED }`.
`MusicFlowUiState(items, selectedIndex, isInitialLoading, isLoadingMore, error, hasMore,
playbackStatus, canRestorePreviousQueue, isOfflineFeed)` with `selectedSongId`.
`MediaMetadata.toMusicFlowSongItem()` maps artists name/id, album name/id, duration (omit when ≤ 0),
thumbnail, and `musicVideoType`/`isVideoSong` equivalents required by `SongItem`.
`MusicFlowFeedState(items, selectedIndex, isInitialLoading, isLoadingMore, error, hasMore, isOffline)`.

### 5.2 Ranking (`MusicFlowRanking.kt`)
- `trackKey = trackKeyOf(song.title, artists joined ", ")` (see §6.1).
- `MusicFlowCandidateFilter(seenSongIds, seenTrackKeys, excludedTrackKeys, hiddenSongIds, recentSongIds)`.
- Filter keeps an item iff: non-blank id, not seen by id, not seen by track key, not excluded by
  key, not hidden by id, not a recent id, and the id and track key are both first occurrences within
  the batch.
- `MusicFlowRankingConfig`: `artistSpacing=2`, `likedArtistBoost=1.0`, `lessLikeThisPenalty=2.5`,
  `positionDecay=0.02`, source weights HISTORY 0.6, LIKED 0.8, QUICK_PICKS 0.5, CATALOG 0.2,
  OFFLINE 0.4 (NOW_PLAYING unweighted → 0).
- Score: source weight − index·positionDecay + likedArtistBoost if primary artist liked −
  lessLikeThisPenalty if disliked. Sort descending, then greedily pick the first candidate whose
  primary artist is not among the last `artistSpacing` picked artists (null artist always allowed);
  if none qualify, take the highest remaining. Output is a permutation of the input.

### 5.3 Repository (`MusicFlowRepository implements MusicFlowFeedSource`)
- `signals()`: reads `MusicFlowHideRecentKey` (default true), liked songs ascending, exclusion
  dao keys, hidden-from-quick-picks ids, and — when hideRecent — the 30 most recent song ids.
  `likedArtists` = all artist ids of liked songs.
- `open(nowPlaying)` returns a `RadioCursor`. `hasMore` false only after the seed queue is empty
  and no tail continuation remains.
- Seed pool: now-playing, then interleaved recent (limit 6), liked (last 40, shuffled, limit 6),
  quick picks (limit 6); empty pool falls back to YouTube home song items. Seeds are de-duplicated
  by id.
- Seeds are expanded in batches of up to 2 per page, calling `YouTube.next(WatchEndpoint(videoId,
  playlistId = "RDAMVM"+id))` and tagging results with the seed source; failed seeds are skipped.
  If nothing was produced it follows the last radio continuation. If nothing was produced and a
  network failure occurred, throw `MusicFlowOfflineException`. Results pass content filters and
  are then interleaved round-robin across the per-seed batches.
- Content filters: hide explicit (`HideExplicitKey`), hide video songs when `HideVideoSongsKey` or
  `DataSaverEnabledKey`, blocked artists, disliked songs.
- `offlineCandidates()`: downloaded songs ascending, shuffled, content-filtered, source OFFLINE.
- Constants: seeds per batch 2, seed limit 6, liked pool 40, recent exclusion 30.

### 5.4 Session (`MusicFlowSession`, singleton; operations are serialised and not re-entrant)
- `start(nowPlaying)` is a no-op once started; `reset(nowPlaying)` starts a new generation (any
  outstanding cursor or page from the previous generation becomes inert), seeds the feed with
  now-playing (marked seen), sets `isInitialLoading`, loads signals (failure → empty signals) and
  fetches until `INITIAL_TARGET=12` items or `MAX_FETCHES_PER_LOAD=3` rounds.
- `OFFLINE` with no items beyond the seed triggers one offline batch; state becomes `isOffline`.
- Final error: `EMPTY` if no items and no error; otherwise the fetch error; `hasMore =
  cursor.hasMore && !isOffline`.
- `loadMore()` returns exactly the newly appended items; no-op when not started, already loading,
  no more pages, or `MAX_FEED_SIZE=300` reached. Up to 3 fetch rounds until one item is added.
- `select(index)` clamps to `0..lastIndex`; `indexOf(songId)`, `hide(songId)`,
  `lessLikeThis(item)`, `isHidden(songId)`.
- `append` filters and ranks new items against the seen set and the tail artists, admits up to the
  available room, marks them seen, and clears the error.
- Append-only invariant: a page index, once visible, always maps to the same song for the session.
- The session exposes a queue handle and queue snapshot as the handoff to the coordinator/service;
  the current generation identifies the live feed. `PREFETCH_THRESHOLD=4` is a UI prefetch hint.

### 5.5 Coordinator and player port
`MusicFlowPlayer` (interface): `currentMediaId`, `queueMediaIds`, `ownsQueue(handle)`,
`isAnyFlowQueueActive`, `takeoverBlock`, `seekTo(queueIndex, playWhenReady)`,
`startQueue(items, startIndex, playWhenReady): Any`, `append(handle, items)`,
`setPauseAtEnd(handle, pause)`, `snapshot(): Any?`, `restore(snapshot, playWhenReady)`, `isPlaying`.

`MusicFlowCoordinator(session, player)`:
- `nextRequestId()` monotonically increments; `select(requestId, ...)` drops requests older than
  the newest (returns Switching).
- If the same media id is already current → Synced. If Flow owns the queue and the target is in it
  → seek → Switching. Otherwise if `takeoverBlock` non-null → Blocked(reason).
- On first takeover (no Flow queue active) snapshot the previous queue.
- Start a new `MusicFlowQueue`, store the handle, set pause-at-end = `!autoNext`.
- `applyAutoNext`, `appendIfOwned`, `canRestorePreviousQueue`, `restorePreviousQueue` (only while
  Flow still owns; clears snapshot/handle; returns false and clears snapshot otherwise),
  `invalidateIfSuperseded`.
- `PlayerConnectionMusicFlowPlayer`: `ownsQueue` = service active queue identity; takeover block
  order = Listen Together guest restriction → casting; `seekTo` prepares when idle/ended;
  `startQueue` uses `service.playQueue`; `setPauseAtEnd` only for `Queue` handles; snapshot/restore
  only for `MusicService.QueueSnapshot`.

`MusicFlowQueue(title, items, startIndex, session)` implements `Queue`:
`preloadItem = null`; initial status = title, mapped media items, clamped start index; `hasNextPage`
only while the queue still belongs to the live session generation and `hasMore`; `nextPage`
returns `session.loadMore()` mapped to media items (empty once the generation has changed).
`BROWSE_SKIP_THRESHOLD_MS = 10_000`.

### 5.6 Preferences
`MusicFlowPreferences.from(Preferences)`: enabled/autoplay/autoNext/lyrics/cellularPrefetch/
hideRecent/dataSaver default true, animatedBackground default false but falls back to
`CanvasThumbnailAnimationKey`, dataSaver from `DataSaverEnabledKey`. Keys:
`MusicFlowEnabledKey`, `MusicFlowAutoplayKey`, `MusicFlowAutoNextKey`,
`MusicFlowAnimatedBackgroundKey`, `MusicFlowLyricsKey`, `MusicFlowCellularPrefetchKey`,
`MusicFlowHideRecentKey`.

---

## 6. Taste, recommendation and generation

### 6.1 Shared key and scoring (internal contract)
- `trackKeyOf(title, artist) = ("${title.trim()}|${artist.trim()}").lowercase()`.
- `currentTimeOfDay(hour)`: 6–11 `morning`, 12–17 `afternoon`, 18–21 `evening`, else `night`.
- `scoreStats(stats, likedKeys, timeOfDay)`:
  `score = ln(1 + max(0,totalPlayTimeMs)/60000) · 2.0 − skipCount·3.0 + 5.0(if liked) +
  1.5(if lastPlayed within 7 days)`; then multiply by 1.3 when morning and recent, or night and
  liked; otherwise ×1.0; clamp to ≥ 0.
- `applyCandidateFilter(stats) = skipCount < 2 || totalPlayTimeMs > 45_000`.
- Constants: `MAX_SUGGESTIONS=25`, `PLAY_WEIGHT=2`, `SKIP_PENALTY=3`, `LIKED_BONUS=5`,
  `RECENCY_BONUS=1.5`, `TIME_OF_DAY_BONUS=1.3`.
- External bonuses: `SIMILAR_TRACK_BONUS=2`, `SIMILAR_ARTIST_BONUS=1`, `TAG_MATCH_BONUS=1.5`,
  `GENRE_MATCH_BONUS=1`; `scoreCandidateWithExternals` adds each applicable bonus.

### 6.2 `GenerationStatus`
`GenerationState { Idle, Running(message), Done(playlistId), Failed(error) }`.
`GenerationStatus` interface: `state`, `start(message)`, `update(message)`, `succeed(playlistId)`,
`fail(error)`, `cancel()`.
`RealGenerationStatus` (singleton) rules: `update` only applies while Running; `cancel` → Idle;
`start` → Running; `succeed` → Done; `fail` → Failed. `GenerationStatus` is bound to it in DI.

### 6.3 `SongPlayStatsRepository`
`recordListenedMs(title, artist, videoId?, artworkUrl?, listenedMs, completed)`:
- blank title → no-op; key = `trackKeyOf`; runs on IO; concurrent writes are serialised.
- Existing row: additive `totalPlayTimeMs`, `playCount +1` only when `completed`, update non-null
  videoId/artwork, `lastPlayedAtMillis = now`.
- New row: totals = listenedMs, playCount 1/0, skipCount 0, now.
- `recordSkip(...)`: same accumulation, increments `skipCount`, playCount unchanged.
- All DAO exceptions are logged and swallowed; `CancellationException` is rethrown.

### 6.4 `LocalTasteEngine` (singleton, open)
`generate(count = 25): Result<String>` on IO:
1. Load exclusions and compute time-of-day.
2. Recent stats = `mostPlayedSince(now − 30 days, 100)`; if empty, `recentlyPlayed(20)`; if still
   empty → failure `"No listening history available yet"`.
3. Liked keys from liked songs (row-id ascending).
4. Eligible seeds = non-excluded stats passing the candidate filter, sorted by descending score;
   empty → failure `"All candidate tracks have been excluded"`.
5. Expand up to 5 distinct seed video ids through `YouTube.next` items plus `related` songs,
   de-duplicating by track key (exclusions pre-seeded).
6. If ≥ 5 expanded songs → take `count`; else append local seed fallbacks (title, artist, artwork)
   and de-dupe by id.
7. Empty result → failure `"Could not generate playlist from seeds"`.
8. Persist playlist id `"local_taste_daily_mix"`, name `"My Taste Mix"` (clear + insert playlist,
   songs, position maps, in one transaction). Return success with the id.
Any unexpected exception → `Result.failure(e)`.

### 6.5 `RecommendationEngine : LocalTasteEngine`
Same steps, plus:
- Taste genres = profile top genres ∪ `genresRepository.topGenres(15)`.
- For the top 3 seeds, in parallel call `getSimilarTracks` and `getSimilarArtists`; collect
  `similarTrackKeys` and lowercase `similarArtistNames`; resolve up to 5 similar tracks each via
  `YouTube.search("artist name", FILTER_SONG)` unless excluded.
- Local candidate expansion via `YouTube.next` + `related` for up to 5 seeds.
- Merge/distinct by id, drop excluded keys, cap at `max(30, count×3)`.
- Batch `findByKeys` for stats; score unplayed candidates with base 5.0, played ones with
  `scoreStats`; add external bonuses. Genre match = candidate lead-artist genres intersect user top
  genres; genre-preference match also allows a word-boundary regex match of a genre in title or
  artist.
- Resolve genres per distinct artist under a 2 s timeout; on timeout use an empty map.
- ≥ 5 sorted songs → take `count`; else append local fallbacks.
- No songs → delegate to `super.generate`.
- Playlist id `"local_taste_<epochMillis>"`, name `"Taste Mix (<Title-cased time of day>)"`.
- If the database is null and `allowNullDatabaseForTesting` is false → failure
  `"Database not available to persist playlist"`.
- Any non-cancellation exception → log and `super.generate(count)`.

`allowNullDatabaseForTesting` is a mutable testing switch.

### 6.6 `GenresRepository` (singleton, open)
- `genresForTrack(trackKey)`: memory cache TTL 1 h; artist = second `|`-separated part; blank
  artist → empty; `LastFmTasteApi.getTopTags(artist)`; success → trimmed/lowercased distinct tags
  cached; failure → empty (not cached).
- `topGenres(limit=10)`: add 1000·(N−index) for each cached taste-profile genre (N = profile genre
  count); add per-track weight `max(10, totalPlayTimeMs)` for each genre of up to 15
  `mostPlayedSince(0)` tracks; each per-track lookup has a 2 s timeout and runs in parallel;
  sort descending and take `limit`.
- `clearCache()`.
- `GENRE_CACHE_TTL_MILLIS = 3_600_000`.

### 6.7 `TasteProfileProvider` (singleton, open; concurrent access serialised)
`get(forceRefresh = false): TasteProfile?` layers:
1. In-memory cache if fresh (< 1 h) and not forced.
2. Room `TasteProfileDao.get()` if fresh; decode JSON fields; lowercase artists/genres.
3. Fetch for `LastFMUsernameKey` (trimmed, non-blank) in parallel: top artists (30), top tracks (50),
   top tags (15). If any succeeds and artists or tracks are non-empty → build profile, cache in
   memory, upsert `TasteProfileEntity(id=1, confidence=1.0)`.
4. On failure → stale DB profile if present.
5. Else → local profile from `mostPlayedSince(now−30d, 50)` (artists, track keys, empty genres).
6. Else null.
`clear()` clears memory + dao. `TASTE_PROFILE_TTL_MILLIS = 3_600_000`.
`TasteProfile(topArtistNames, topTrackKeys, topGenres, fetchedAtMillis)`.

### 6.8 `LastFmTasteApi` (external service contract)
Interface methods: `getSimilarTracks(artist, track)`, `getSimilarArtists(artist)`,
`getTopTags(artist)`, `getUserTopArtists(user, limit=30)`, `getUserTopTracks(user, limit=50)`,
`getUserTopTags(user, limit=15)`; all return `Result<List<…>>` and never throw.
`RealLastFmTasteApi` uses `LastFM.getKtorClient()` and `LastFM.getApiKey()` (fallback
`BuildConfig.LASTFM_API_KEY`). Base `https://ws.audioscrobbler.com/2.0/`, GET query parameters,
`format=json`, `api_key`.
Methods/params: `track.getsimilar` (artist, track, limit 30); `artist.getsimilar` (artist, limit
20); `artist.gettoptags` (artist); `user.gettopartists` / `user.gettoptracks` /
`user.gettoptags` (user, period=overall, limit). Any JSON `error` object → empty list. Missing key
or blank inputs → empty list. Malformed entries are skipped. `match`/`count` accept numeric or
string JSON.

---

## 7. AI providers

General rules shared by the three AI objects:
- OpenAI-compatible chat-completions POST.
- Endpoint/key/model from DataStore: `OpenRouterApiKey` (required), `OpenRouterBaseUrlKey`
  (default `https://openrouter.ai/api/v1/chat/completions`), `OpenRouterModelKey`
  (default `openrouter/free`).
- Before any request, the URL must pass `OpenRouterService.isUrlSafe`; otherwise log
  `"Restricted or unsafe AI endpoint destination."` and abort.
- Headers: `Authorization: Bearer <key>`; JSON body `{model, messages[]}`. Timeouts 30/90/30 s.
- Non-2xx → log `"AI Request failed: <code>"`, abort (no retry inside these helpers).
- Response content = `choices[0].message.content`; markdown fences are stripped before parsing.
- All failures return null/Unit after logging through `onLog`; they never throw to callers.
- `AiLogger.log(context, action, details)` records the final success (suspend, best-effort).

### 7.1 `AiPlaylistGenerator.generatePlaylist(context, userPrompt, numberOfSongs=15, weatherInfo?, onLog): String?`
- System prompt demands raw JSON `{name, songs:[{title, artist}]}` with exactly `numberOfSongs`
  entries; optional weather acoustic directive (rain → lofi/ambient, sun → upbeat, fog/cold →
  ambient, night → synthwave/R&B).
- User prompt includes mood, optional weather line, desired length.
- Each returned song is resolved via `YouTube.search("$title $artist", FILTER_SONG)` and the first
  `SongItem` kept. If none resolve → null.
- Persists a local `PlaylistEntity(name, radioEndpointParams = weather snapshot, isLocal=true,
  isEditable=true)` plus `SongEntity`/`PlaylistSongMap` rows; returns the playlist id.
- `numberOfSongs` is a requested count, not a hard cap on resolution.

### 7.2 `AiPlaylistModifier.modifyPlaylist(context, playlistId, currentSongs, userPrompt, onLog): Unit`
- Sends current songs `[{id: map.id, title, artist}]`; expects raw JSON
  `{remove_ids:[int], additions:[{title,artist}]}`.
- Removes map rows whose `map.id` is listed and present in `currentSongs`.
- Resolves additions via InnerTube; upserts songs and appends at `maxPosition + 1 + index`.
- Empty arrays → logs "No modifications requested by AI.".

### 7.3 `AiRecommendationHelper.generateRecommendations(context, onLog?)`
- Taste list = top 20 played + up to 20 liked, distinct by id, first 30, formatted
  `"title by artist"`.
- No history → log and return.
- Expects a raw JSON array of `{title, artist}`; resolves via search; none → return.
- Target playlist name `"From your taste by Revenge"` (exact): reuse by exact name or create local
  editable playlist; on reuse clear existing songs.
- Inserts resolved songs in order; logs count. No return value.

`AiRecommendationWorker` (`CoroutineWorker`): calls the helper; returns success; any exception →
`Result.retry()`.

### 7.4 `OpenRouterService` / translation (module `:lyrics`)
- `isUrlSafe(url)`: blank allowed; must parse as a URI with `http`/`https` and a host; rejects
  link-local/metadata hosts `169.254.169.254`, `169.254.*`, `metadata.google.internal`,
  `instance-data`. This is the SSRF boundary.
- Anthropic-compatible endpoints (`api.anthropic.com` + `/messages`) use `x-api-key` +
  `anthropic-version: 2023-06-01` and a top-level `system`; everything else uses Bearer auth.
- `translate(text, targetLanguage, apiKey, baseUrl, model, mode, maxRetries=3, sourceLanguage=null, onLog)`:
  line-preserving translation; expected line count = input line count; `max_tokens = lines × 100`,
  temperature 0.3; retries with `1s × attempt` backoff on 5xx, unparsable content, exceptions;
  4xx fails immediately; empty text fails; unsafe URL fails with `SecurityException`.
  Result is `List<String>` with exactly the expected line count.
- `testConnection(baseUrl, apiKey, model)`: "ping" with 10 max tokens; returns content or
  "Connection OK"; errors become `Result.failure`.
- Cancellation is always rethrown.
- **Puter is removed**: no Puter endpoint, key or provider participates in any AI/translation path.
  Only OpenAI-compatible and Anthropic-compatible endpoints are supported.

### 7.5 Weather
`WeatherInfo(temperature, feelsLike, condition, conditionCode, humidity, windSpeed, isDay,
weatherEmoji)`; `summaryBadgeText` = `"<emoji> <int>°C • <condition> • <humidity>% Humidity"`.
`WeatherUiState { Idle, Loading, Success(data), Error(message, isPermissionDenied,
isPermanentlyDenied) }`.
`mapWmoCodeToCondition(code, isDay)`: table from 0 (Sunny/Clear Sky), 1, 2, 3, 45/48 fog,
51/53/55 drizzle, 56/57 freezing drizzle, 61/63/65 rain, 66/67 freezing rain, 71/73/75/77 snow,
80/81/82 showers, 85/86 snow showers, 95 thunderstorm, 96/99 heavy thunderstorm; unknown → Clear.
`toSnapshotString()` = `"WEATHER_VIBE:<int>°C • <condition>|<emoji>|<condition>|<humidity>|<int>"`
(stored in `PlaylistEntity.radioEndpointParams`). `parseWeatherSnapshot` returns null for a missing/
wrong prefix or fewer than 4 pipe parts; defaults humidity 50, temp 20, isDay true.

External services:
- Weather: `GET https://api.open-meteo.com/v1/forecast?latitude=&longitude=&current=temperature_2m,
  relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m`; no auth. Maps
  `current.*`; blank body/invalid JSON/non-2xx → `Result.failure`. No caching.
- IP location: primary `GET https://ipwho.is/` (requires `success == true`, numeric lat/lon,
  city/country); fallback `GET https://ipapi.co/json/` with `User-Agent: Mozilla/5.0`
  (lat/lon, city, country_name). Both HTTPS; failure of one falls through; total failure →
  `Result.failure("Could not determine location from IP.")`.
- Device location: `LocationManager` providers GPS → NETWORK → PASSIVE, last-known only, freshness
  ≤ 30 min, newest `elapsedRealtimeNanos` wins; optional reflection-based Play Services fused
  provider attempted for up to 2 s; any failure → null. Requires location permissions; annotated
  `@SuppressLint("MissingPermission")` — caller must obtain the runtime permission.
- `WeatherCoverOverlay(imageUrl?, playlistName, weatherInfo?, modifier, shape, placeholderPainter?)`
  is a pure Compose composable (square, rounded, gradient + title + optional weather row); no
  side effects.

### 7.6 `FlowNeuroengineApi`
Retrofit-style interface `@POST("/v1/brain/analyze") suspend fun analyzeEvent(NeuroengineRequest):
NeuroengineResponse`, with `NeuroengineRequest(trackId, durationMs, skipped)`,
`NeuroengineResponse(suggestions, tasteProfileUpdate?)`,
`SuggestedTrack(id, title, artist, album?, matchReason)`,
`TasteProfileDto(genres, confidence, patternsFound, modelVersion)`. It is a declared contract only
and currently has no caller; it must remain source-compatible but may be unused.

---

## 8. Local media scanner

### 8.1 `SupportedLocalAudio`
Supported extensions: aac, amr, flac, m4a, m4b, m4p, mid, mka, mp3, mp4, oga, ogg, opus, wav,
weba, webm, 3ga, 3gp. Blocked extensions: ape, cue, dsd, dsf, dff, midi, iso, tta, wv, wvc.
Supported MIME set: audio/aac, audio/aac-adts, audio/amr, audio/amr-wb, audio/flac, audio/mp4,
audio/mpeg, audio/ogg, audio/opus, audio/wav, audio/wave, audio/webm, audio/x-aac, audio/x-flac,
audio/x-m4a, audio/x-mka, audio/x-matroska, audio/x-mpeg, audio/x-wav, application/ogg, video/mp4,
video/webm, video/3gpp.
`isSupported(displayName, mimeType)`: blocked extension wins → false; supported extension → true;
else normalized MIME (before `;`, trimmed, lowercased) in set. `isSupportedMimeType` additionally
accepts `audio/*`. MIME normalisation is locale-independent.

### 8.2 Configuration
`LocalFolderFilterMode { EXCLUDE, INCLUDE }`.
`LocalSongScanConfig(minimumDurationSeconds=0, excludedFolders, includedFolders, filterMode,
allowClearAll=false)` exposes:
`sanitizedMinimumDurationSeconds = max(0, n)`; folder entries normalized by trimming, replacing
`\` with `/`, collapsing repeated `/`, trimming leading/trailing `/`; deduplicated case-insensitively
(lowercase key, first spelling wins, empty dropped); `hasActiveFolderFilter` for INCLUDE = any
included, for EXCLUDE = any excluded.
`LocalSongScanSummary(scannedSongs, removedSongs)`.

### 8.3 `scanDevice(config): LocalSongScanSummary` (IO)
MediaStore query on `EXTERNAL_CONTENT_URI` with projection of id, title, display name, artist,
artist id, album, album id, duration, year, date-modified, size, MIME, and RELATIVE_PATH (Q+) or
DATA (legacy). Selection: `SIZE > 0`, `DURATION >= min` (else `> 0`), `IS_PENDING = 0` (Q+),
`is_trashed = 0` (R+); ordered by title NOCASE then id.

Per row: folder = RELATIVE_PATH normalised/lowercased, else directory of DATA; INCLUDE mode keeps
only matches, EXCLUDE drops matches; match means equal, prefix with `/`, suffix `/<folder>`, or
containing `/<folder>/`. Unsupported audio skipped. Blank/`<unknown>` artist → localized unknown
artist; artists split on `[,;/&]`, blank → unknown. Album blank/`<unknown>` → null. Title falls
back to display name (sans extension) then localized unknown. Album id = `LOCAL_ALBUM_<MediaStoreId>`
when present, else `LOCAL_ALBUM_<stableHash(albumName|primaryArtistId)>`; artist id =
`LOCAL_ARTIST_<MediaStoreId>` for a single artist with id, else
`LOCAL_ARTIST_<stableHash(name|index)>`; stable hash = UUID name-based (MD5) hex without dashes.
Track id = content URI; duration = ms/1000 clamped to `Int`; year only when > 0; dateModified from
epoch seconds in system zone; thumbnail = albumart content URI when album id > 0 else track URI.

Transaction:
1. Existing local ids vs scanned ids; removed = existing − scanned.
2. Empty scan safeguard: clear all only if `allowClearAll`, or the query completed with device rows,
   a folder filter is active and no rows matched the filter, or there were no existing local ids;
   otherwise make no changes and return `(0,0)`.
3. Otherwise delete removed ids in 900-id chunks.
4. Upsert artists (preserve thumbnail/lastUpdate/bookmarked; isLocal true), albums (preserve
   theme/bookmark/liked/inLibrary; drop `content://media/external/audio/media/` thumbnails; isLocal
   true), replace album-artist maps, then upsert songs/formats and replace song-artist and
   song-album maps, preserving liked/likedDate/totalPlayTime/date/dateDownload/explicit and
   discarding MediaStore media-URI thumbnails.
5. Prune orphan local albums/artists/formats/play counts.
6. Summary = scanned size, removed size.
Batch size 900. The whole scan must be atomic within `withTransaction`.

---

## 9. Podcast logic

### 9.1 `PodcastVideoProvider`
- `findOfficialMusicVideoId(title, artist)`: blank title → null. Cleans title (remove bracket
  groups, feat/ft/with parentheticals, official video/lyrics/audio/visualizer/live/remaster/version/
  edit/mix/remix markers and trailing `- official video`-style suffixes, collapse spaces) and artist
  (first contributor before comma/&/×/x/feat/ft/featuring/with). Searches `FILTER_VIDEO` with
  `"title artist"` then `"title artist official music video"`; returns the first OMV whose title
  contains/contained-by the clean title, else the first OMV, else an "official/video/mv" title
  match that is not ATV, else any non-ATV title match. Exceptions → null.
- `resolveVideoTracks(videoId)`: tries InnerTube player clients VISIONOS → TVHTML5 →
  ANDROID_VR_1_65_10. For the first client yielding data: muxed formats with height ≥ 144 become
  options with `audioUrl = null, isMuxed = true`; adaptive video formats with height ≥ 144 use the
  highest-bitrate audio URL (`isMuxed = false`). If none, fall back to `NewPipeExtractor`.
  Deduplicate by height, preferring a muxed MP4, then any MP4, then the first; sort by height
  descending. Exceptions → empty list.

### 9.2 `PodcastProgress` (process singleton, SharedPreferences `podcast_progress`)
`EpisodeProgress(id, positionMs, durationMs, finished, updatedAt, title, show?, showBrowseId?,
thumbnailUrl?, dismissed=false)`; `fraction` (1 when finished, 0..1 when duration > 0, else null);
`remainingMs`; `isInProgress`; `asPlayed()`; `isMislabelled` = duration 1..<10 min and no podcast
show browse prefix.
Rules:
- Near end: `duration − position < clamp(duration/20, 60s, 180s)`.
- Persistence: at most 500 entries; JSON per id; on load drop malformed entries and mislabelled
  legacy entries; legacy in-progress entries that are near end become finished.
- `inProgress`: unfinished, position > 0, not dismissed, newest first.
- `isEpisode(metadata, durationMs)`: musicVideoType is podcast episode AND (an artist id starts with
  `PODCAST_BROWSE_PREFIX` OR duration ≥ 10 min).
- `dismiss`/`restore` toggle `dismissed`.
- Podcast speed is separate from music (`podcast_settings`), default 1f; cycle order
  `1, 1.25, 1.5, 1.75, 2, 0.75`; `speedLabel` prints integral speeds without decimal and trims
  trailing zeros, suffixed `x`.
- `mergeRemote(SongItem)`: ignore when `playedPercent <= 0`; finished at ≥ 95%; unfinished requires
  duration > 0; position = duration × percent / 100. Insert when absent (`updatedAt = 0`), ignore if
  existing finished, otherwise accept when the remote is finished or ahead by > 60 s.
- `withoutContinueShelf(sections, title)`: removes a section when all its items are podcast episodes
  and either the title matches (case-insensitive) or every episode is in 1..94%; removal happens
  only when every episode merged successfully.
- `update(metadata, position, duration)`: ignore positions < 30 s when no entry exists; finished =
  near end; finished also sticks if the previous entry was finished and position < 30 s; preserve
  dismissed.
- `markFinished(id)`: finished, position 0, timestamp now.
- Skip constants `SKIP_BACK_MS=15000`, `SKIP_FORWARD_MS=30000`.

### 9.3 Other podcast utils
- `PodcastShowStore` (singleton): SharedPreferences `podcast_shows`, `ids: StateFlow<Set<String>>`,
  `add(id)` idempotent.
- `NewEpisodesWorker`: periodic 6 h, network-required, unique work `NewEpisodesWorker` with KEEP.
  Followed shows = bookmarked playlists whose browseId is a known show. Per show: fetch
  `YouTube.podcast(showId)`; first ever check stores all ids without notifying; afterwards notify
  only for the first id not seen, and record ids only when the notification was actually posted
  (no POST_NOTIFICATIONS → defer so it can notify later). Notification channel `podcast_episodes`,
  a notification id derived from the show id, opens
  `https://music.youtube.com/playlist?list=<showId>` in MainActivity. Missing permission is not an
  error.
- `EpisodeDates.parse(text, now)`: best-effort, locale-aware (device then English month names);
  handles today/yesterday, absolute `D MMM [YYYY]` / `MMM D[, YYYY]` (current year unless future
  when year omitted), and relative `<n> <unit>` for seconds/minutes/hours/days/weeks/months/years
  (Italian + English prefixes); returns null when unrecognised.
- `LocalFileDownloader.download(context, url, destinationDirUri, fileName, mimeType, userAgent?)`:
  SAF tree document; validate directory writable; create/reuse file; OkHttp `Range: bytes=0-`
  request; stream to the file with a progress notification on channel `local_downloads` (id
  derived from the file name, updates ≤ 1/s); Toasts on failure/success; all errors logged. No
  return value.
- `LocalMediaIntents`: `String.isLocalMediaId()`, `shareLocalAudio(...)`; keep signatures.
- `QuickPickSongs.filterQuickPickSongs(songs, excludedSongIds, blockedArtists, hideExplicit,
  hideVideoSongs)`: exclude hidden/`excludedSongIds`, then blocked artists / explicit / video songs;
  keep order, distinct by id.

---

## 10. Stats / Wrapped contract

Wrapped UI consumes DAO aggregates; the cluster guarantees the following shapes:
- `WrappedTimeRange { THIS_YEAR, LAST_YEAR, ALL_TIME }`.
- `WrappedData(timeRange, yearLabel, totalPlayTimeMs, totalMinutes = ms/60000, uniqueSongsCount,
  uniqueArtistsCount, uniqueAlbumsCount, topSong?, topSongStats?, top5Songs, top5SongsStats,
  topArtist?, top5Artists, topAlbum?, top5Albums, dominantVibe="Diverse Explorer")`.
- `WrappedUiState { Loading, InsufficientData(songsPlayed, minSongsRequired=3), Ready(data) }`.
- The Year view falls back to ALL_TIME when all-time unique songs ≥ 3 and the selected range has
  fewer; otherwise `InsufficientData`.
- Stats are derived from `SongPlayStatsDao` and listening-history entities; no separate store.
`SongPlayStatsRepository` (§6.3) is the only writer of per-track play aggregates.

---

## 11. Other utils contracts

### 11.1 `SyncUtils` (singleton)
- `SyncOperation` sealed: FullSync, LikedSongs, LibrarySongs, UploadedSongs, LikedAlbums,
  UploadedAlbums, ArtistsSubscriptions, SavedPlaylists, AutoSyncPlaylists, SinglePlaylist(browseId,
  playlistId), LikeSong(SongEntity), CleanupDuplicates, ClearAllSynced.
- `SyncStatus { Idle, Syncing, Error(message), Completed }`;
  `SyncState(overallStatus, likedSongs, librarySongs, uploadedSongs, likedAlbums, uploadedAlbums,
  artists, playlists, currentOperation)`.
- Operations are processed serially on IO; an exception in one operation is logged and does not
  stop subsequent operations. `syncState` is a `StateFlow`.
- All operations are no-ops when not logged in (`SAPISID` present in the InnerTube cookie).
- Retry: 3 attempts, 1 s initial, doubling. Inter-operation delay 50 ms.
- Full sync order: liked songs → library songs → uploaded songs → liked albums → uploaded albums →
  artist subscriptions → saved playlists → auto-sync playlists; writes `LastFullSyncKey` (epoch
  seconds) on success.
- `tryAutoSync` requires login, sync enabled, internet, and cooldown `SYNC_COOLDOWN = 1800 s`.
- Playlist sync safeguards: empty remote is ignored (no local clear); when local has songs missing
  remotely, additive-only insert (preserve local); otherwise replace atomically.
- Cleanup duplicates keeps the playlist with most songs per browseId. Clear-all removes synced
  flags/bookmarks and remote playlists.
- Public API: all `perform*`/`sync*` fire-and-forget, the matching `*Suspend` variants, `tryAutoSync`,
  `runAllSyncs`, `likeSong`, `cancelAllSyncs`.

### 11.2 `LastFM` + `ScrobbleManager`
- `LastFM` object: base `https://ws.audioscrobbler.com/2.0/`, form-encoded POST, MD5
  `api_sig = md5(concat(sorted key+value pairs) + secret)`, methods `auth.getToken`,
  `auth.getSession`, `auth.getMobileSession`, `track.updateNowPlaying`, `track.scrobble`,
  `track.love/unlove`; all return `runCatching`; `initialize(apiKey, secret)`,
  `isInitialized()`, `getApiKey()`, `getKtorClient()`, `sessionKey`.
- `ScrobbleManager(scope, minSongDuration=30, scrobbleDelayPercent=0.5, scrobbleDelaySeconds=50)`:
  flags `useNowPlaying`, `enableScrobbling`, `useSendLikes`. Starts a track change → optional
  `updateNowPlaying`; while playing increments seconds each second; scrobbles once when played
  seconds ≥ `min(percent×duration, absoluteSeconds)`, skipping tracks shorter than the minimum
  during 1..min. Timestamp = now − playedSeconds. Errors logged, never thrown. `destroy()`
  cancels jobs. Local media tracks are accepted.

### 11.3 `NetworkUtils`
`isInternetAvailable(context): Boolean` — availability check with no exceptions escaping.

---

## 12. Cross-module compile contracts

Required types/signatures (must not drift):
- `com.revenge.innertube.YouTube`: `next(WatchEndpoint, continuation?): Result<NextPage>` with
  `items`, `endpoint`, `continuation`, `relatedEndpoint`; `related(endpoint): Result<…>` with
  `songs`; `search(query, filter): Result<SearchPage>` with `items`; `home()`;
  `library(browseId, tabIndex?): Result<…>` with `.completed()`; `playlist(id)`, `podcast(id)`,
  `album(browseId)`, `queue(ids): Result<List<SongItem>>`, `player(videoId, client)`,
  `likeVideo(id, liked)`, `getChannelId`.
- `com.revenge.innertube.models`: `SongItem(id, title, artists, album?, duration?, thumbnail,
  musicVideoType?, playedPercent?, isVideoSong, musicVideoType constants OMV/ATV/PODCAST_EPISODE)`,
  `Artist(name, id)`, `Album(name, id)`, `PlaylistItem`, `AlbumItem`, `ArtistItem`, `WatchEndpoint`,
  `HomePage.Section`, filters `filterExplicit`, `filterVideoSongs`, `filterBlockedArtists`,
  `filterDislikedSongs`, `PODCAST_BROWSE_PREFIX`, `YouTubeClient` constants, `NewPipeExtractor`.
- `com.revenge.music.models.MediaMetadata` with `id,title,artists,album,duration,thumbnailUrl,
  suggestedBy,musicVideoType` and `toMediaItem()`, `toMediaMetadata()`; `SongEntity.toMediaMetadata()`.
- `MusicDatabase` (Room) methods used: `likedSongsByCreateDateAsc`, `likedSongsByRowIdAsc`,
  `likedSongsByPlayTimeAsc`, `likedSongsByNameAsc`, `songsByNameAsc`, `uploadedSongsByNameAsc`,
  `recentlyPlayedSongs(n)`, `quickPicks`, `downloadedSongsByCreateDateAsc`,
  `hiddenFromQuickPicksSongIds`, `playlistsByNameAsc`, `playlistsByCreateDateAsc`,
  `playlistSongs(id)`, `topSongs(n)`, `song(id)`, `album(id)`, `artist(id)`,
  `searchPlaylists(name)`, `getSongsByIds`, `getArtistEntitiesByIds`, `getAlbumEntitiesByIds`,
  `localSongIds`, `deleteSongsByIds`, `deleteAlbumArtistMapsByAlbumIds`, `deleteSongArtistMaps`,
  `deleteSongAlbumMaps`, `pruneLocalAlbums`, `pruneLocalArtists`, `pruneFormats`,
  `prunePlayCounts`, `clearLocalSongs`, `clearPlaylist`, `insert`, `upsert`, `update`, `delete`,
  `withTransaction`/`transaction`; `songPlayStatsDao`; `recommendationExclusionDao`;
  `tasteProfileDao`.
- DAOs: `SongPlayStatsDao` (`find`, `findByKeys`, `upsert`, `mostPlayedSince`, `recentlyPlayed`,
  `observeTopPlayed`, `getAllTrackKeys`, `clearAll`); `RecommendationExclusionDao` (`getAll`,
  `observeAll`, `upsert`, `upsertAll`, `count`, `isExcluded`, `delete`, `clear`);
  `TasteProfileDao` (`get`, `upsert`, `clear`).
- Entities: `SongPlayStatsEntity(trackKey,title,artist,videoId?,artworkUrl?,totalPlayTimeMs,
  playCount,skipCount,lastPlayedAtMillis)`, `PlaylistSongMap(playlistId,songId,position,setVideoId?)`,
  `SongEntity(id,title,duration,thumbnailUrl,albumId,albumName,explicit,year,date,dateModified,
  liked,likedDate,totalPlayTime,inLibrary,dateDownload,isLocal,isUploaded,isVideo,…)`,
  `ArtistEntity`, `AlbumEntity`, `FormatEntity`, `AlbumArtistMap`, `SongArtistMap`,
  `SongAlbumMap`, `TasteProfileEntity(id=1,topArtistsJson,topTracksJson,topGenresJson,confidence,
  updatedAtMillis)`, `RecommendationExclusionEntity(trackKey)`, `PlaylistSong(map, song)`.
- `Queue`/`Queue.Status(title, items, startIndex)`, `preloadItem`, `getInitialStatus`,
  `hasNextPage`, `nextPage`.
- Preferences in `com.revenge.music.constants` as named in §3.9, §5.6, §7.5, §11.1, plus
  `HideExplicitKey`, `HideVideoSongsKey`, `DataSaverEnabledKey`, `CanvasThumbnailAnimationKey`,
  `LastFMUsernameKey`, `LastFMUseSendLikes`, `LastFullSyncKey`, `SYNC_COOLDOWN`.
- `com.revenge.music.ai.AiLogger.log(Context, action, details)` (suspend, from `:core`).
- DI: `AppModule` must bind `LastFmTasteApi → RealLastFmTasteApi`,
  `GenerationStatus → RealGenerationStatus`, `LocalTasteEngine → RecommendationEngine`, and provide
  `ListenTogetherClient`/`ListenTogetherManager`.

---

## 13. Threading, cancellation and invariants

- Listen Together client: all socket callbacks and state updates may occur on OkHttp/IO threads;
  `StateFlow` is the thread-safe handoff. `events` may drop oldest at overflow. Manager collects on
  Main and must not block.
- `isSyncing` is the single re-entrancy guard for playback; every entry/exit must be balanced,
  including on failure. A stale asynchronous application must abort instead of mutating the
  player.
- Manager coroutines are `SupervisorJob`; one failed observer must not cancel others.
- Music Flow session operations are serialised; `loadMore` and `reset` are not re-entrant.
  Generation invalidation makes an old `MusicFlowQueue` stop paging.
- Taste/generation/AI run on `Dispatchers.IO`. `CancellationException` is always rethrown; ordinary
  exceptions become `Result.failure`, logs or null per §6–§7.
- `SongPlayStatsRepository` and `TasteProfileProvider` serialise their writes.
- Local scan is one Room transaction; cancellation must not leave a partially applied scan.
- No source function may throw across a public boundary unless documented (`translate` returns
  `Result`, `get` returns nullable, AI returns null/Unit, scanner throws only DB failures from the
  transaction).

---

## 14. Acceptance tests (deterministic; mocks/fixtures; failure paths)

### 14.1 Listen Together codec
1. JSON round-trip: for every §3.5 payload type, `encode(type, payload)` then `decode` returns the
   same type string and a payload that `decodePayload` restores semantically (use a fixed `Json`
   fixture; assert null/empty asymmetry for album/thumbnail/suggestedBy/reason).
2. Format detection: `{}`-first byte → JSON; `0x08` etc → PROTOBUF; empty → JSON.
3. Protobuf round-trip for all types listed in §3.4; assert exact field tags by asserting the
   serialized bytes of a fixture match the documented `tag = wiretype` sequence (e.g. `RoomState`
   tag 8 float, tag 10 bool).
4. GZIP: a >100-byte payload is compressed when smaller; envelope `compressed` is set; decoding
   restores it. A non-compressible payload is stored raw.
5. `decodePayload` for an unknown `msgType` returns null without throwing.
6. Malformed/truncated bytes → callers log and keep the connection (assert no exception escapes
   the message handler).
7. **Regression:** chat still encodes after a protobuf upgrade (must not throw
   `Unsupported payload type`).

### 14.2 Listen Together client/state
Use a fake transport (WebSocket factory) and a fake DataStore.
1. Inbound `room_created` → state HOST, userId/token set, one-user room, persisted keys written,
   `RoomCreated` event.
2. `join_request` while HOST and auto-approval on → an `approve_join` frame is sent and no
   notification is posted. Auto-approval off + permission denied → request stays pending and no
   notification attempt crashes.
3. Blocked username join request → `reject_join` with reason "You are blocked", no pending entry.
4. `sync_playback` action truth table (§3.12), including `set_volume` clamping and `queue_add`
   `insertNext` ordering.
5. `host_changed` to self promotes role to HOST and flips user flags; to another demotes HOST to
   GUEST.
6. `error{session_not_found}`: guest with stored room → `join_room` sent after the 500 ms delay
   (virtual time); host → session keys removed; no stored room → keys removed.
7. Reconnect: `onFailure` schedules attempts 1..15 with the documented backoff bounds and jitter
   (inject time/random); after 15 → ERROR + `ConnectionError`; token retained only when present.
8. Network loss → DISCONNECTED and no retry; restore → connect once.
9. Session grace: token+room with age 9:59 → restored; 10:01 → cleared.
10. Permission gating: every host-only action from GUEST sends nothing; playback action from guest
    blocked when `allowParticipantControl=false`; allowed when true.
11. Chat reply: `sendChatMessage("hi", RepliedMessage("bob","yo"))` produces the marker with
    base64 of `bob|yo`; inbound marker decodes back to `replyTo` and strips the prefix; malformed
    base64 leaves the message intact.
12. Notification receiver: missing extras / null singleton do not crash; valid extras call the
    right client method and cancel the notification.

### 14.3 Listen Together manager
Fake `Player`/`PlayerConnection`/`MusicService` with virtual time.
1. Host play/pause/seek/track-change listener emits the documented actions and debounces via
   `isSyncing`.
2. Guest `PLAY` with `serverTime` 1000 ms in the past advances the target by 1000 ms; drift under
   2 s does not seek; over the inter-track tolerance seeks.
3. Buffer handshake: `CHANGE_TRACK` without queue → queue load, `BUFFER_READY` sent, pending sync
   stored; no apply until `BUFFER_COMPLETE` for that id; a different id does not apply.
4. `SYNC_QUEUE` preserves the current media id/position and play state.
5. `QUEUE_REMOVE` only removes after the current index; `QUEUE_CLEAR` removes only after current.
6. Volume sync ignores deltas < 0.01 and is disabled for guests; host volume applies on
   join only when the pref is true.
7. Generation invalidation: start sync A, then B; A must not mutate the player afterwards.
8. Stale `PlayerConnection`: replacing the connection aborts in-flight applies.
9. Reconnect as guest with smart resync on → `request_sync` after 1 s; off → none; host as guest
   not restricted → none.
10. Guest mute is restored on leave/kick/cleanup; no saved state → unmute.

### 14.4 Music Flow
Fixtures: fixed `SongItem` lists, in-memory DAOs.
1. `filterMusicFlowCandidates` truth table: blank id, seen id, seen key, excluded key, hidden id,
   recent id, duplicate id in same batch, duplicate key in same batch.
2. Ranking: assert exact order for a fixture; verify artist spacing including the `feedTailArtists`
   boundary; verify tie → earlier index; verify output is a permutation.
3. Session: reset loads 12 items max in ≤3 fetches; `loadMore` returns only new items; append-only
   index stability after `loadMore`; `MAX_FEED_SIZE` cap; offline exception → `OFFLINE` + offline
   batch when only seeds; signals failure → empty signals and no crash.
4. Coordinator: stale request id → Switching without player calls; current id → Synced; owned queue
   hit → seek; takeover block → Blocked; snapshot taken only on first takeover; restore only while
   owned, otherwise snapshot cleared.
5. `MusicFlowQueue`: generation change stops `hasNextPage`/`nextPage`; initial start index clamped.
6. Player adapter: takeover block precedence (Listen Together before CASTING); `ownsQueue` identity;
   `setPauseAtEnd` ignores non-Queue handles.

### 14.5 Taste/generation
1. `trackKeyOf` trims and lowercases (whitespace/case fixtures).
2. `scoreStats` exact numeric expectations for: neutral, liked, skipped ×2, recent, morning+recent,
   night+liked; clamp at 0; `applyCandidateFilter` truth table.
3. `currentTimeOfDay` boundaries 5/6/11/12/17/18/21/22.
4. `GenerationStatus` transitions including `update` ignored when not Running and `cancel`.
5. `SongPlayStatsRepository`: new row, incomplete listen (playCount unchanged), accumulation,
   skip increment, new skip row, DAO error swallowed, cancellation rethrown.
6. `GenresRepository`: cache hit within TTL performs one API call; expiry/clear refetches; top
   genres weight formula with profile + stats; API failure → empty and not cached; recovery fetch.
7. `TasteProfileProvider`: memory hit, DB hit, force refresh, network success persists, network
   failure → stale DB, no DB → local history, no history → null, clear.
8. `LocalTasteEngine`: empty history → failure message; all excluded → failure message; success
   persists id/name/positions in order; <5 expansion uses local fallbacks; unexpected exception →
   failure.
9. `RecommendationEngine`: null DB without testing flag → failure; empty history; all excluded;
   Last.fm failure degrades to local-only; genre bonuses applied; 2 s genre timeout yields no
   bonuses but still succeeds; success id prefix `local_taste_` and name `Taste Mix (…)`;
   cancellation propagates.
10. `GenerateViewModel` (existing tests remain green): Idle → Success / EmptyHistory / AllExcluded,
    clear exclusions, cancel.

### 14.6 AI and weather
Inject a fake HTTP transport.
1. Missing API key → null + `"API Key is missing…"` log; unsafe URL (metadata IP) → abort before
   any request.
2. Non-2xx → null; malformed JSON / missing `choices` → null; markdown fences stripped.
3. Generator: N returned songs, only resolvable ones persist, positions 0..n−1, playlist is local
   and editable, returns the id; zero resolvable → null.
4. Modifier: remove ids delete exact map rows; unknown ids ignored; additions appended after the
   max position; empty arrays → no writes.
5. Recommendation helper: no history returns early; existing playlist is cleared and repopulated;
   exact name reuse.
6. Worker: success → success; thrown exception → retry.
7. `OpenRouterService.isUrlSafe`: blank true; https true; ftp/file false; metadata hosts false;
   `169.254.0.1` false.
8. Translation: line count preserved; 5xx retries up to max then failure; 4xx fails immediately;
   empty text fails; cancellation rethrown; Anthropic URL uses `x-api-key`/version and top-level
   system.
9. Weather: every WMO code maps to the documented pair for day/night; snapshot round-trips;
   malformed snapshots → null; non-2xx/blank/invalid weather → failure; IP primary success, primary
   failure → fallback, both fail → failure.
10. `WeatherCoverOverlay` is a smoke/Compose test (no network side effects).

### 14.7 Local media
Fake `ContentResolver` cursor + fake DAO.
1. `SupportedLocalAudio.isSupported` extension precedence, blocked wins, MIME normalisation with
   parameters/case, `audio/*` only for the MIME-only helper.
2. Config normalisation/dedup (slashes, backslashes, case, blank).
3. Scan creates artists/albums/songs/maps in order; IDs stable across scans; liked/play fields
   preserved; missing songs removed; artist/album/format pruning happens.
4. Empty-scan safeguard: no clear-all without `allowClearAll`; with an active folder filter and
   device rows but zero matches → clear; with no existing rows → clear.
5. Folder matching equality/prefix/suffix/containment; INCLUDE mode drops non-matches.
6. Unsupported extension skipped; unknown artist/title fallbacks.
7. Transaction rollback on injected DAO failure leaves prior data intact.

### 14.8 Podcast
1. `PodcastProgress.isNearEnd` boundaries (5% clamp 60–180 s).
2. `update` ignores <30 s when new; sets finished near end; preserves dismissed; `markFinished`.
3. Load migration drops mislabelled entries and marks legacy near-end entries finished.
4. Trim keeps the 500 newest.
5. `mergeRemote`: no percent → false; ≥95% → finished; remote ahead by >60 s wins; not ahead →
   ignored; existing finished untouched.
6. `withoutContinueShelf` removes only when all merge; title match vs all-in-progress match.
7. `isEpisode` requires label plus show prefix or ≥10 min.
8. `EpisodeDates.parse` table (Italian/English absolute and relative, future year rollover, nulls).
9. `NewEpisodesWorker`: first check stores ids without notifying; later unseen id notifies once;
   no permission → no record; notification intent target.
10. `PodcastVideoProvider` title/artist cleaning fixtures; OMV preference order; dedupe by height
    preferring muxed MP4; InnerTube failure falls back to NewPipe; both fail → empty.

### 14.9 Utils/DI
1. `SyncUtils`: operations no-op when not logged in; playlist additive-only preservation; empty
   remote leaves locals; cooldown blocks auto-sync; retry count/backoff; per-operation failure does
   not stop the queue.
2. `ScrobbleManager`: exact threshold = min(percent·duration, absolute); one scrobble per track;
   short-track rule; timestamp = now − played; local media accepted; destroy cancels.
3. `LastFM` signature construction: sorted keys + secret MD5 (golden vector fixture).
4. `QuickPickSongs`: hidden/excluded/blocked/explicit/video filtering and order/dedup.
5. DI graph test: `AppModule` resolves `ListenTogetherManager`, `GenerationStatus`,
   `LocalTasteEngine` (as `RecommendationEngine`), `LastFmTasteApi`.
6. Compile guard: a fixture module referencing every §12 signature compiles, proving the
   cross-module contract.
