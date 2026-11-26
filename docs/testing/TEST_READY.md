# TEST_READY — E2E Requirement & Test-Readiness Matrix

**Document type:** M2/M3 test-readiness baseline (E2E Requirement Analyst, R2 clean-room track)
**Baseline reference:** `11ae62071f96db3b70557867ab1254dbcc5b125e`
**Governing documents:** `docs/provenance/REIMPLEMENTATION_ROADMAP.md` (feature invariants F4–F16), `docs/provenance/ARCHITECTURE_CONTRACTS.md` (§5 R3 subsystem contracts), `docs/provenance/VALIDATION_AND_RELEASE_GATES.md` (§5 Tiers 1–4, §6 disclosure), `docs/provenance/M2_GATE_DECISIONS.md`.
**Scope:** Feature-to-test coverage for every user-facing capability, requirement IDs, observable acceptance criteria, Tier 1–4 test plans, required fixtures/mocks, and current readiness under the clean-room rebuild.

> This document contains **no source code**. It specifies tests to be written by the E2E Track and gives the current readiness verdict for each capability so the M6 release gate (`G-PROV-2`, `F15`) cannot silently regress a feature.

---

## 1. Readiness model

### 1.1 Status vocabulary

| Status | Meaning |
|---|---|
| **READY** | The tested surface exists in the current tree and the test harness needed for at least its Tier 1 contract tests is present. Tier 1 tests can be authored and executed now; higher tiers may still be gated. |
| **NOT_READY** | The surface is scheduled for a later clean-room wave or the harness (instrumentation, mock server, fixtures) does not yet exist. Tests can be drafted from `docs/specs/` but cannot be declared passing. |
| **BLOCKED** | An unresolved decision, retired dependency, absent asset, or unavailable external system prevents the test from being executed at all until resolved. |

Readiness is stated **per test tier where it differs**; the headline status is the lowest tractable tier, because the M6 gate requires Tiers 1–4 to pass (`F15`).

### 1.2 Tier definitions (operationalised from `VALIDATION_AND_RELEASE_GATES.md` §5)

| Tier | Layer | Runs on | Typical tooling | Purpose |
|---|---|---|---|---|
| **Tier 1** | Pure-JVM unit & contract | JVM, no Android framework | JUnit 4, `kotlinx-coroutines-test`, in-memory fakes | Parsing, scoring, queue math, validators, error mapping |
| **Tier 2** | Android component / integration | JVM, Android APIs | Robolectric, Room in-memory, `MockWebServer` (to be added) | Services, DAOs/migrations, repository wiring, ViewModels |
| **Tier 3** | Instrumented E2E | Emulator / device | `androidTest`, Compose UI test, `MediaSession` harness | Navigation, UI semantics, playback service, widgets, permissions |
| **Tier 4** | System / cross-process E2E | Real device (or emulator with media stack) | Release-gate scripts, fault injection | Deep links, process death/recovery, offline storms, install/update flow |

### 1.3 Current harness inventory (verified in tree)

Present:
- JUnit 4 (`junit:4.13.2`), `kotlinx-coroutines-test` in `:domain`.
- Robolectric `4.14.1` in `:app` and `:core`.
- 79 unit test source files across 20 modules; in-memory fakes already used in `:domain` specs.
- Existing Tier 1 coverage for: recommendation scoring, queue ordering/filters, playback cutoff, crossfade curve, seek targeting, EQ parsing/processing, USB descriptor/driver logic, lyrics parsers, canvas/artist-video models, InnerTube parsers, scrobble manager, Spotify queue, export queue.

Missing (blocks Tiers 2–4 project-wide):
- **No `androidTest` source sets** (verified: zero directories).
- **No `testFixtures`** source sets.
- No `MockWebServer` / OkHttp test dependency.
- No Compose UI test dependency (`androidx.compose.ui:ui-test-junit4`) or `androidx.test.ext:junit`.
- No `room-testing` migration helper.
- No mock framework (`mockk`) or `turbine`; project convention is hand-written fakes, which is sufficient if extended.
- No golden/screenshot or media-session test harness.

**Harness backlog (must land before Tier 3 claims):** add `:app` `androidTest` + Compose UI test + `androidx.test` runner, `MockWebServer` to network-bearing modules (`:innertube`, `:lyrics`, Spotify/Jellyfin/Navidrome), `room-testing` to `:core`, a deterministic `MainDispatcherRule`, and shared JSON/LRC/TTML/proto fixture directories. This backlog is the gating item for the global `NOT_READY` verdict on Tiers 2–4.

---

## 2. Feature coverage matrix

Requirement IDs use `REQ-<DOMAIN>-<nn>`; acceptance IDs use `AC-<DOMAIN>-<nn>`. "Existing" lists tests already in the tree (Tier 1).

### 2.1 Streaming / discovery (`F6`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-STR-01` InnerTube search/stream resolution; `REQ-STR-02` auth-vs-transport error separation; `REQ-STR-03` cipher/`po_token`/SABR `n`-transform; `REQ-STR-04` SponsorBlock + retry/cancel |
| **Observable AC** | `AC-STR-01` A valid video id resolves to a playable, typed `Result.Success` stream URL. `AC-STR-02` A 401/auth failure maps to `AuthRequired`; a socket/DNS failure maps to `TransportError`, never conflated. `AC-STR-03` A player response containing a ciphered URL is de-obfuscated without network fallback and produces the same URL twice (deterministic). `AC-STR-04` Cancelling a request stops retries and emits no success. |
| **Tier 1** | Parser/serialization tests over captured response fixtures; error-classifier tests; cipher transform determinism; URL parser tests. |
| **Tier 2** | `MockWebServer` serving 200/401/500/timeout; repository returns correct typed result; retry count bounded; cancellation honored. |
| **Tier 3** | Search → play smoke on emulator against mocked network interceptor; empty/offline UI states. |
| **Tier 4** | Offline/flaky network storm: repeated transport failures never crash nor leak retries; process kill mid-resolution recovers. |
| **Fixtures/mocks** | Captured InnerTube JSON (search, player, next), cipher response with `n` param, `po_token` HTML fixture, okhttp `Interceptor` fake, `MockWebServer` dispatcher. |
| **Existing** | `innertube/src/test/.../ParsersTest.kt`, `models/SerializationTest.kt`, `utils/YouTubeUrlParserTest.kt`. |
| **Readiness** | **Tier 1 READY.** **Tier 2 NOT_READY** (no MockWebServer). **Tier 3–4 NOT_READY** (no androidTest burden; InnerTube itself is clean-room-pending in Wave 3). |

### 2.2 Playback modes — background, gapless, crossfade, sleep timer, speed (`F5`, `F7`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-PLY-01` queue ordering + lazy continuations; `REQ-PLY-02` gapless transition; `REQ-PLY-03` crossfade with cutoff guard; `REQ-PLY-04` sleep timer; `REQ-PLY-05` speed/pitch; `REQ-PLY-06` audio focus duck/pause + Bluetooth/media buttons; `REQ-PLY-07` process-death queue recovery |
| **Observable AC** | `AC-PLY-01` Appending a page preserves order and resumes playback without a gap. `AC-PLY-02` Consecutive tracks transition with zero audio dropout at the boundary. `AC-PLY-03` Crossfade never truncates the outgoing track below its configured cutoff; guard rejects a fade that would clip. `AC-PLY-04` Sleep timer pauses at the configured elapsed time and cancels cleanly. `AC-PLY-05` Speed change is reflected in the media session and survives rotation. `AC-PLY-06` Incoming call/focus loss pauses; focus regain resumes; Bluetooth next/prev invoke the queue. `AC-PLY-07` After process death, the persisted queue and position are restored. |
| **Tier 1** | Queue filter/order tests, cutoff guard math, equal-power curve, seek target, crossfade pre-arm. |
| **Tier 2** | `PlaybackCutoffGuard` integration with fake player; sleep-timer clock with injected `Clock`; `PersistQueue`/`PersistPlayerState` round-trip via in-memory store. |
| **Tier 3** | `MediaSession` instrumentation: play/pause/next via session controller; notification action routing; speed control; focus loss simulation. |
| **Tier 4** | Kill process mid-track, relaunch, assert queue/session restoration; Bluetooth key injection via media button broadcast. |
| **Fixtures/mocks** | Fake `Player`/`ExoPlayer` seam, fake `AudioFocusManager`, deterministic `Clock`, persisted-state JSON/proto, synthetic two-track audio buffers. |
| **Existing** | `playback/.../PlaybackCutoffGuardTest.kt`, `crossfade/EqualPowerCurveTest.kt`, `AdaptivePreArmManagerTest.kt`, `queues/*Test.kt`, `SeekButtonTargetTest.kt`; `app/.../PlaybackCutoffProtectionTest.kt`. |
| **Readiness** | **Tier 1 READY.** **Tier 2–4 NOT_READY** (Wave 2 playback rebuild pending; no media-session harness). |

### 2.3 Downloads & export (`F5`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-DL-01` download enqueue/resume/retry; `REQ-DL-02` update-safe file handling + collision; `REQ-DL-03` offline playback of downloaded media; `REQ-DL-04` audio export/transcode |
| **Observable AC** | `AC-DL-01` A download survives transient failure and resumes without duplicate files. `AC-DL-02` Re-downloading an existing track resolves a stable filename (no overwrite of unrelated media). `AC-DL-03` A downloaded track plays with the network disabled. `AC-DL-04` Export produces a playable file with correct metadata; export cancellation leaves no partial file. |
| **Tier 1** | `ExportTaskQueue` ordering/cancel; download state reducer; filename/collision resolver. |
| **Tier 2** | `DownloadManager` with fake HTTP + temp dir; Robolectric download service; SAF permission-denied path. |
| **Tier 3** | Enqueue → progress notification → complete → airplane-mode playback; retry after forced failure. |
| **Tier 4** | Kill process mid-download twice; assert no orphan/partial files and resumable state; storage-full error path. |
| **Fixtures/mocks** | `MockWebServer` byte-range responses, temp `File` sandbox, fake storage with injectable free-space, small WAV/MP3 fixture, export output validator. |
| **Existing** | `app/.../ExportTaskQueueTest.kt`, `DownloadStateSyncTest.kt`. |
| **Readiness** | **Downloads: Tier 1 READY / Tier 2–4 NOT_READY.** **Export: BLOCKED** — `ffmpeg-kit-audio:6.0-2` is retired upstream and replacement (Media3 Transformer or maintained fork) is a Wave 2 decision (`PROGRESS.md` known constraints, `G-LIC-1`). Export E2E cannot be specified as passing until the codec backend is chosen. |

### 2.4 EQ / AutoEQ (`F7`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-DSP-01` parametric EQ parse/validate/apply; `REQ-DSP-02` AutoEQ profile lookup (clean MIT index); `REQ-DSP-03` bass boost/virtualizer/loudness; `REQ-DSP-04` enable/disable without audio glitch |
| **Observable AC** | `AC-DSP-01` A valid band list round-trips through the parser and applies biquad coefficients; malformed/out-of-range bands are rejected with a typed error, not applied. `AC-DSP-02` Every AutoEQ device entry parses; an unknown device returns empty without crash; no `Revenge`-corrupted names appear. `AC-DSP-03` Each effect toggles audibly and reports its enabled state. `AC-DSP-04` Toggling EQ mid-playback does not stop the stream. |
| **Tier 1** | `ParametricEQParser`, `BiquadFilter`, `SavedEQProfile`, `CustomEqualizer`, `StereoWidener`, `AutomixDuck`, `DspControllerImpl` behavior. |
| **Tier 2** | `AudioEffect` controller against Robolectric audio service; AutoEQ index loader over real bundled JSON. |
| **Tier 3** | UI: adjust a band, persist, relaunch, assert restored; AutoEQ sheet selects a device and applies. |
| **Tier 4** | Rapid enable/disable loop (100×) with no player teardown; measure no discontinuity via PCM capture harness. |
| **Fixtures/mocks** | `autoeq_index.json` real asset, malformed EQ JSON, known-good band set, fake `AudioEffect` sink, PCM capture buffer. |
| **Existing** | `dsp-core/.../ParametricEQParserTest.kt`, `BiquadFilterTest.kt`, `SavedEQProfileTest.kt`; `audio-dsp/.../CustomEqualizerAudioProcessorTest.kt`, `StereoWidenerAudioProcessorTest.kt`, `AutomixDuckAudioProcessorTest.kt`, `DspControllerImplTest.kt`. |
| **Readiness** | **Tier 1 READY.** **Tier 2–4 NOT_READY** (DSP rebuild is Wave 2; no audio capture harness). |

### 2.5 USB audio routing (`F7`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-USB-01` USB DAC detect/attach; `REQ-USB-02` route through platform `AudioTrack` to USB device; `REQ-USB-03` honest fallback to speaker on detach; `REQ-USB-04` no false bit-perfect claim |
| **Observable AC** | `AC-USB-01` A connected DAC is detected and selected. `AC-USB-02` Audio is rendered through the USB device via `AudioTrack`; the chosen route is observable in logs/state. `AC-USB-03` Detaching the DAC falls back to the built-in speaker without crashing or silent playback. `AC-USB-04` The UI/docs never assert bit-perfect direct URB delivery (disclosure gate §6). |
| **Tier 1** | Descriptor parser, device detector, volume controller, driver state machine, PCM format negotiation. |
| **Tier 2** | Robolectric `UsbManager` fake: attach/detach lifecycle, sink opens/closes correctly. |
| **Tier 3** | Emulator with virtual USB audio: attach mid-playback, assert route switch. |
| **Tier 4** | Physical DAC on real hardware: attach/detach storm, playback continuity, volume mapping. **Requires hardware in the release lab.** |
| **Fixtures/mocks** | Fake `UsbManager`/`UsbDevice`/`UsbEndpoint`, descriptor byte arrays, fake `AudioTrack`, documented manual hardware checklist. |
| **Existing** | `usbaudio/.../UsbAudioDriverTest.kt`, `UsbDacAudioSinkTest.kt`, `UsbDeviceDetectorTest.kt`, `DescriptorParserTest.kt`, `VolumeControllerTest.kt`, `BitPerfectPcmTest.kt`; `app/.../UsbAudioServiceRoutingTest.kt`. |
| **Readiness** | **Tier 1 READY.** **Tier 2 NOT_READY** (fake USB plumbing absent). **Tier 3–4 NOT_READY / hardware-gated.** |

### 2.6 Lyrics & translation (`F8`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-LYR-01` multi-provider fetch + fallback order; `REQ-LYR-02` LRC/TTML parse with XXE hardening; `REQ-LYR-03` sync offset + romanization; `REQ-LYR-04` translation providers + error handling; `REQ-LYR-05` on-device cache | 
| **Observable AC** | `AC-LYR-01` Provider fallback returns the first usable lyric; all-fail returns empty without crash. `AC-LYR-02` Malformed/XXE-bearing LRC/TTML is isolated (no entity expansion, no crash). `AC-LYR-03` Offset shift moves timing deterministically; Kuromoji/TinyPinyin output is stable. `AC-LYR-04` A translation provider error surfaces a typed failure and keeps original lyrics visible. `AC-LYR-05` A cached lyric is served offline; cache invalidation by track id works. |
| **Tier 1** | Provider response parsing, LRC/TTML, selection order, translation parsing, `LyricsUtils`, timing. |
| **Tier 2** | `MockWebServer` per provider; cache DAO under Robolectric; translation failure injection. |
| **Tier 3** | Open lyrics for streamed vs downloaded track; toggle translation; offset control; offline cached lyrics. |
| **Tier 4** | Malformed-provider storm (all providers return garbage) and XXE payload; no ANR/crash; cache cleanup. |
| **Fixtures/mocks** | LRC/TTML fixtures (valid, malformed, offset, multi-language), provider JSON, XXE payload, fake translation backend, cache store. |
| **Existing** | `betterlyrics/TTMLParserTest.kt`, `lrclib/LrcLibLyricsTest.kt`, `TrackMatchingTest.kt`, `youlyplus/LrcFormatTest.kt`, `LyricsResponseTest.kt`, `ServerOrderTest.kt`, `simpmusic/LyricsModelsTest.kt`, `LyricsSelectionTest.kt`, `kugou/KuGouTest.kt`, `paxsenix/PaxsenixCoreTest.kt`, `lyrics/.../AiTranslationParsingTest.kt`, `LyricsUtilsTest.kt`, `app/.../LyricsTimingTest.kt`. |
| **Readiness** | **Tier 1 READY.** **Tier 2–4 NOT_READY** (providers rebuilt Wave 3; no MockWebServer; `Puter` branch already removed). |

### 2.7 Canvas & artist video (`F9`/`F14`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-CAN-01` animated artwork resolution (Apple/TIDAL/YouTube); `REQ-CAN-02` artist video resolution + MIME; `REQ-CAN-03` clean cancellation/fallback to static art |
| **Observable AC** | `AC-CAN-01` A track with canvas resolves to a playable loop; absence falls back to static artwork. `AC-CAN-02` Video response parses to a supported MIME and renders; unsupported codecs fall back. `AC-CAN-03` Navigating away cancels the fetch/render and releases resources. |
| **Tier 1** | `CanvasArtwork`, TIDAL provider logic, Apple canvas logic, artist-video response, MIME mapping. |
| **Tier 2** | Robolectric provider with mocked HTTP; cancellation test. |
| **Tier 3** | Player renders canvas; artist video surface plays; fallback to static cover on failure. |
| **Tier 4** | Rapid track switching (canvas thrash) with no leak/ANR; offline fallback. |
| **Fixtures/mocks** | Canvas JSON, artist-video JSON, small video sample, fake image/video loader. |
| **Existing** | `canvas/CanvasArtworkTest.kt`, `TidalCanvasProviderTest.kt`, `applecanvas/AppleMusicCanvasLogicTest.kt`, `artistvideo/ArtistVideoResponseTest.kt`, `VideoMimeTypeTest.kt`. |
| **Readiness** | **Tier 1 READY.** **Tier 2–4 NOT_READY** (Wave 5 UI/video surface; no instrumented media harness). |

### 2.8 Audio recognition ("Find") (`F10`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-REC-01` mic permission flow; `REQ-REC-02` PCM capture + fingerprint; `REQ-REC-03` provider match + timeout/cancel; `REQ-REC-04` guaranteed temp-audio cleanup; `REQ-REC-05` foreground service + QS tile |
| **Observable AC** | `AC-REC-01` Denying `RECORD_AUDIO` shows a denial state and starts no capture. `AC-REC-02` Captured PCM produces a deterministic signature for a known fixture. `AC-REC-03` No match within timeout returns "not found" and stops cleanly; cancel cancels in-flight work. `AC-REC-04` Temp audio files are deleted after success, failure, cancel, and process kill. `AC-REC-05` The foreground service notification and QS tile can start/stop recognition. |
| **Tier 1** | `ShazamSignatureGenerator` determinism, resampler, `ShazamTest`. |
| **Tier 2** | Robolectric permission grant/deny; service lifecycle; temp-dir cleanup assertion. |
| **Tier 3** | Emulator mic injection of known fixture → match; denial path; QS tile. |
| **Tier 4** | Kill process mid-recognition → assert temp PCM purged on next start; timeout under airplane mode. |
| **Fixtures/mocks** | PCM/WAV fixture, known signature oracle, fake permission grant/deny, fake recognition provider, temp-dir filesystem observer. |
| **Existing** | `shazamkit/ShazamTest.kt`. |
| **Readiness** | **Tier 1 partial READY.** **Tier 2–4 NOT_READY** (Find UI/service rebuild Wave 5; no permission/test harness). |

### 2.9 Spotify import (`F10`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-SPT-01` OAuth/login; `REQ-SPT-02` playlist/library import + track resolution; `REQ-SPT-03` session refresh + error isolation |
| **Observable AC** | `AC-SPT-01` A valid token imports a playlist; a revoked token surfaces an auth error and prompts re-login. `AC-SPT-02` Imported tracks resolve to playable Revenge tracks; unresolvable tracks are reported, not dropped silently. `AC-SPT-03` Token refresh is transparent; a provider 500 isolates to the import and leaves the app usable. |
| **Tier 1** | `SpotifyQueue`, mapper, hash provider, paging models. |
| **Tier 2** | `MockWebServer` OAuth + paging; token-expiry refresh; partial-resolution reporting. |
| **Tier 3** | Login screen → import screen → library shows imported playlist. |
| **Tier 4** | Revoked token mid-import + network storm; no crash, resumable. |
| **Fixtures/mocks** | OAuth token/refresh JSON, playlist/library paging fixtures, resolved-track fixture, `MockWebServer`, fake token store. |
| **Existing** | `app/.../SpotifyQueueTest.kt`. |
| **Readiness** | **Tier 1 READY.** **Tier 2–4 NOT_READY** (Spotify rebuild Wave 5; no MockWebServer). |

### 2.10 Jellyfin (`F11`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-JEL-01` server auth; `REQ-JEL-02` library browse + stream URL; `REQ-JEL-03` failure isolation |
| **Observable AC** | `AC-JEL-01` Valid credentials authenticate and list libraries. `AC-JEL-02` A Jellyfin track resolves to a playable URL and plays. `AC-JEL-03` Unreachable server shows a typed error and does not break local playback. |
| **Tier 1** | Repository mapping and error classification. |
| **Tier 2** | `MockWebServer` auth/browse/stream; unauthorized → typed error. |
| **Tier 3** | Settings add server → browse → play; wrong-password error UI. |
| **Tier 4** | Server down mid-session; retry/error isolation. |
| **Fixtures/mocks** | Jellyfin auth response, library JSON, stream endpoint, `MockWebServer`. |
| **Existing** | None. |
| **Readiness** | **NOT_READY** (Wave 5 integration; no tests, no MockWebServer). |

### 2.11 Navidrome / Subsonic (`F11`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-NAV-01` server auth; `REQ-NAV-02` browse + stream; `REQ-NAV-03` failure isolation |
| **Observable AC** | `AC-NAV-01` Credentials authenticate (token/salt scheme) and list content. `AC-NAV-02` A track resolves and plays. `AC-NAV-03` Bad credentials/server-down isolate to a typed error. |
| **Tier 1** | Auth token generator, response mapping. |
| **Tier 2** | `MockWebServer` auth/browse/stream. |
| **Tier 3** | Settings → browse → play; invalid-credential UI. |
| **Tier 4** | Server down mid-session. |
| **Fixtures/mocks** | Navidrome auth/browse JSON, `MockWebServer`, fake credential store. |
| **Existing** | None. |
| **Readiness** | **NOT_READY** (Wave 5). |

### 2.12 Listen Together (`F11`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-LT-01` session create/join; `REQ-LT-02` protobuf message codec; `REQ-LT-03` synchronized playback state; `REQ-LT-04` reconnect/desync recovery |
| **Observable AC** | `AC-LT-01` Two clients join a room and see each other. `AC-LT-02` A message round-trips through the clean protobuf codec byte-stably. `AC-LT-03` Play/pause/seek on host reflects on guest within tolerance. `AC-LT-04` A dropped connection reconnects and resyncs without diverging. |
| **Tier 1** | `MessageCodec`, `Protocol`, queue-apply logic. |
| **Tier 2** | Fake WebSocket server (MockWebServer WS) room join/sync/reconnect. |
| **Tier 3** | Two-emulator session: host controls guest. |
| **Tier 4** | Network partition/reconnect storm; state convergence. |
| **Fixtures/mocks** | `.proto` golden messages, fake WebSocket server, two-client harness. |
| **Existing** | None. |
| **Readiness** | **NOT_READY** (clean `listentogether.proto` rebuild Wave 5; no WS harness). |

### 2.13 Cast (`F12`, `gms` only)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-CAST-01` device discovery/connect; `REQ-CAST-02` media transfer; `REQ-CAST-03` FOSS isolation (`CAST_AVAILABLE == false`) |
| **Observable AC** | `AC-CAST-01` A Cast device appears and connects on `gms`. `AC-CAST-02` Playback transfers to the device with correct metadata. `AC-CAST-03` `foss` builds contain no Cast services/classes and the UI hides Cast. |
| **Tier 1** | Cast availability flag logic, session state reducer (pure). |
| **Tier 2** | Fake Cast context/session; `foss` variant manifest assertion (no `ReconnectionService`). |
| **Tier 3** | Emulator `gms` with virtual Cast device; `foss` smoke confirms no Cast UI. |
| **Tier 4** | Device disconnect mid-playback; fallback to local. |
| **Fixtures/mocks** | Fake `CastContext`/session, `gms`/`foss` variant APK manifest, virtual Cast device. |
| **Existing** | None. |
| **Readiness** | **BLOCKED for FOSS CI** (Cast intentionally absent in `foss`; E2E requires a `gms` instrumented job). **NOT_READY for `gms`** (Wave 5; no device harness). |

### 2.14 Android Auto (`F12`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-AA-01` Automotive media browse/play; `REQ-AA-02` `AutomotiveArtworkProvider` signed-URI enforcement |
| **Observable AC** | `AC-AA-01` The Auto media browser lists and plays tracks through the same playback service. `AC-AA-02` An unsigned/tampered artwork URI is rejected; a signed URI resolves. |
| **Tier 1** | URI signature validator, media-id mapping. |
| **Tier 2** | Robolectric content-provider test for signed/unsigned URIs. |
| **Tier 3** | Desktop Head Unit (DHU) browse → play. |
| **Tier 4** | DHU + real car head unit; reconnect. |
| **Fixtures/mocks** | Signed/unsigned URI fixtures, fake `ContentResolver`, DHU harness. |
| **Existing** | None. |
| **Readiness** | **NOT_READY** (Wave 5; DHU harness absent). |

### 2.15 Last.fm & ListenBrainz (`F13`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-LFM-01` auth/session; `REQ-LFM-02` scrobble now-playing + threshold; `REQ-LB-01` ListenBrainz submit; `REQ-INT-03` isolated failure |
| **Observable AC** | `AC-LFM-01` Authorized scrobbles post once, respecting the play threshold. `AC-LFM-02` Offline scrobbles queue and flush later. `AC-LB-01` ListenBrainz receives a listen with correct timestamps. `AC-INT-03` A provider outage never blocks local playback. |
| **Tier 1** | `ScrobbleManager` threshold/now-playing logic; ListenBrainz payload mapping. |
| **Tier 2** | `MockWebServer` for both providers; auth failure; offline queue flush. |
| **Tier 3** | Settings login → play 50% → verify scrobble dispatched (mocked). |
| **Tier 4** | Provider outage storm + offline → queued flush, no duplicates. |
| **Fixtures/mocks** | Last.fm auth/session/scrobble responses, ListenBrainz payload fixture, `MockWebServer`, fake clock. |
| **Existing** | `app/.../ScrobbleManagerTest.kt`. |
| **Readiness** | **Last.fm Tier 1 READY / Tier 2–4 NOT_READY.** **ListenBrainz NOT_READY.** |

### 2.16 Podcasts (`F13`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-POD-01` subscription management; `REQ-POD-02` episode playback/progress; `REQ-POD-03` video podcast provider |
| **Observable AC** | `AC-POD-01` Subscribing adds a show and persists across restart; unsubscribing removes it. `AC-POD-02` Episode progress resumes at the saved position. `AC-POD-03` A video podcast resolves and plays. |
| **Tier 1** | `PodcastProgress`, `PodcastShowStore`, provider mapping. |
| **Tier 2** | Repository with fake feed HTTP; progress DAO under Robolectric. |
| **Tier 3** | Subscribe → play episode → relaunch → progress restored. |
| **Tier 4** | Feed fetch failure; offline subscription list. |
| **Fixtures/mocks** | RSS feed JSON/XML, fake feed server, progress store. |
| **Existing** | None. |
| **Readiness** | **NOT_READY** (Wave 5 `PodcastsScreen` refactor). |

### 2.17 Wrapped / listening stats (`F13`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-WRP-01` stats aggregation; `REQ-WRP-02` yearly recap generation; `REQ-WRP-03` shareable card export |
| **Observable AC** | `AC-WRP-01` Play events aggregate into correct top-artist/track counts for a fixed dataset. `AC-WRP-02` A recap with no data yields an empty/neutral state, not a crash. `AC-WRP-03` A shared card renders to an image and shares via the intent without leaking other user data. |
| **Tier 1** | Aggregation/scoring over fixed play-event fixtures; `WrappedModels` mapping. |
| **Tier 2** | ViewModel over in-memory DAO; share-helper intent construction. |
| **Tier 3** | Wrapped screen renders with seeded data; share sheet opens. |
| **Tier 4** | Large history aggregation performance; empty-year path. |
| **Fixtures/mocks** | Play-event dataset, expected stats oracle, fake `Context` for share intent, bitmap capture. |
| **Existing** | None (recommendation-adjacent only). |
| **Readiness** | **NOT_READY** (Wave 5; no stats fixtures). |

### 2.18 Widgets — 10+1 home-screen widgets (`F14`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-WDG-01` widget state sync with player; `REQ-WDG-02` each widget layout renders correct state; `REQ-WDG-03` recognizer widget; `REQ-WDG-04` Glance/RemoteViews update on play/pause/track change |
| **Observable AC** | `AC-WDG-01` Play/pause/track-change updates every installed widget within one update tick. `AC-WDG-02` Adaptive, compact, wide, square, showcase, vinyl, turntable, playlist, modern, slim variants each render their documented state. `AC-WDG-03` The recognizer widget launches recognition and reflects idle/listening states. `AC-WDG-04` A widget tapped while the app is dead opens the app and reflects the pending state. |
| **Tier 1** | `RevengeWidgetManager` state reducer, `IWidgetManager` mapping. |
| **Tier 2** | Robolectric `AppWidgetManager` fake: push state to each provider, assert RemoteViews state. |
| **Tier 3** | Instrumented: add each widget to launcher, change track, assert update. |
| **Tier 4** | Widget update under process death and low-memory; no stale state. |
| **Fixtures/mocks** | Fake `AppWidgetManager`/`RemoteViews`, widget-state snapshot per provider (11 receivers: Adaptive, Modern, Showcase, Slim, Square, Vintage/Vinyl, Turntable, Music, Playlist, MusicRecognizer), seeded player state. |
| **Existing** | None. |
| **Readiness** | **NOT_READY** (Wave 5 widget rebuild; no widget harness). |

### 2.19 Shortcuts & notifications (`F14`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-SYS-01` app shortcuts; `REQ-SYS-02` media notification actions; `REQ-SYS-03` `PendingIntent.FLAG_IMMUTABLE` + exported component security |
| **Observable AC** | `AC-SYS-01` Each shortcut opens the correct destination and does not duplicate the back stack. `AC-SYS-02` Notification play/pause/next control playback. `AC-SYS-03` Every exported component is permission-guarded; every `PendingIntent` is immutable (static audit + lint). |
| **Tier 1** | Shortcut destination mapping (pure). |
| **Tier 2** | Robolectric notification action dispatch; lint `PendingIntent` rule. |
| **Tier 3** | Long-press launcher shortcut → correct screen; notification controls. |
| **Tier 4** | Notification action while app backgrounded/process-killed. |
| **Fixtures/mocks** | Fake `NotificationManager`, shortcut definitions, manifest audit fixture. |
| **Existing** | None. |
| **Readiness** | **NOT_READY** (Wave 4 shell; static manifest gate `G-SEC-2` can run now). |

### 2.20 Updater (`F14`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-UPD-01` release check against `REVENGE_UPDATE_REPO`; `REQ-UPD-02` download + notification; `REQ-UPD-03` install/signature verification; `REQ-UPD-04` disabled when repo unset |
| **Observable AC** | `AC-UPD-01` With `REVENGE_UPDATE_REPO` set, a newer release tag is detected; with it empty, updates are disabled. `AC-UPD-02` Download progresses via a notification and the completed APK is verified. `AC-UPD-03` A mismatched signature/checksum is rejected and never installed. `AC-UPD-04` An update check failure shows a non-blocking error. |
| **Tier 1** | Release JSON parsing, version comparison, checksum/signature validator. |
| **Tier 2** | `MockWebServer` release JSON + APK bytes; download worker with fake storage. |
| **Tier 3** | Update screen → prompt → download notification → install intent (no real install in CI). |
| **Tier 4** | Corrupt APK / checksum mismatch / network drop mid-download; no partial install. |
| **Fixtures/mocks** | GitHub release JSON, small fake APK bytes + checksum, `MockWebServer`, fake package installer, empty-repo config. |
| **Existing** | None. |
| **Readiness** | **NOT_READY** (Wave 5; requires `REVENGE_UPDATE_REPO` fixture and update test repo). |

### 2.21 Local media library & metadata (`F3`/`F5`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-LIB-01` folder scan; `REQ-LIB-02` metadata extraction/cleaning; `REQ-LIB-03` local playback + artwork |
| **Observable AC** | `AC-LIB-01` A scanned folder yields one entry per audio file; duplicates are de-duplicated. `AC-LIB-02` Tags are normalised (artist/title/album) consistently; unreadable files are skipped, not fatal. `AC-LIB-03` A local track plays and shows embedded/generated artwork. |
| **Tier 1** | `MetadataCleaner`, `ApiText`, `CleanedMetadata`, converters. |
| **Tier 2** | Robolectric `MediaStore`/SAF fake scan; Room converter round-trip. |
| **Tier 3** | Grant folder access → library lists → play local file. |
| **Tier 4** | Permission revoked / large library scan performance. |
| **Fixtures/mocks** | Tagged audio fixtures (MP3/FLAC with varied tags), fake `MediaStore`/SAF, metadata oracle. |
| **Existing** | `metadata/.../MetadataCleanerTest.kt`, `ApiTextTest.kt`, `CleanedMetadataTest.kt`; `core/.../ConvertersTest.kt`. |
| **Readiness** | **Tier 1 READY.** **Tier 2–4 NOT_READY.** |

### 2.22 App shell, design system & navigation (`F9`/`F14`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-UI-01` cold start/onboarding; `REQ-UI-02` non-duplicating nav + deep links; `REQ-UI-03` process recreation; `REQ-UI-04` dynamic color/font/theme persistence; `REQ-UI-05` accessibility semantics; `REQ-UI-06` loading/empty/error/offline states |
| **Observable AC** | `AC-UI-01` Fresh install completes onboarding and grants notification permission. `AC-UI-02` Deep links open the right destination without duplicate back-stack entries. `AC-UI-03` Rotating/killing recreates UI without losing state. `AC-UI-04` Theme/font changes persist across restart and rebind to OFL `sans_flex.ttf`. `AC-UI-05` Key screens have content descriptions and pass TalkBack traversal. `AC-UI-06` Each data-driven screen renders loading/empty/error/offline states. |
| **Tier 1** | `DensityScaler`, design-token constants, nav-route mapping. |
| **Tier 2** | Robolectric activity launch; theme preference persistence. |
| **Tier 3** | Compose UI test: onboarding, deep links, back-stack, semantic tree, theme switch, state screens. |
| **Tier 4** | Process death + deep link; cold-start budget; accessibility scan. |
| **Fixtures/mocks** | Compose test rule, fake prefs store, deep-link URIs, semantic assertions, theme snapshots. |
| **Existing** | `app/.../PlaylistNavigationTest.kt`, `HeroCarouselSongsTest.kt`, `ActivityResultLauncherExtTest.kt`, `ModuleIndexTest.kt`. |
| **Readiness** | **Tier 1 partial READY.** **Tier 2–4 NOT_READY** (Wave 4 shell; no Compose UI test dependency). |

### 2.23 Recommendation / Smart Audio / Flow (`F13`)

| Field | Value |
|---|---|
| **Requirement IDs** | `REQ-RCM-01` quick picks/local mix (clean-room `:domain`); `REQ-RCM-02` recommendation engine; `REQ-RCM-03` beat tracking fallback; `REQ-RCM-04` FlowNeuroengine optional endpoint |
| **Observable AC** | `AC-RCM-01` `GetQuickPicks`/`ResolveLocalMix` satisfy `docs/specs/domain.md` B1–B9 / C1–C11. `AC-RCM-02` Recommendation scoring is deterministic for fixed input. `AC-RCM-03` With no beat model, `BeatTracker.session()` returns `null` and playback is unaffected. `AC-RCM-04` With no FlowNeuro API key, AI features degrade to off. |
| **Tier 1** | Domain specs (already specified), recommendation scoring, local taste, beat tracker null-path, loop mix. |
| **Tier 2** | Repository-driven engine over in-memory DB. |
| **Tier 3** | Generate screen produces a queue; offline AI disabled state. |
| **Tier 4** | No-model/no-key degradation; large-history scoring performance. |
| **Fixtures/mocks** | `FakeSongRepository` (domain spec), play-event dataset, absent-model asset, unset API key. |
| **Existing** | `domain/GetQuickPicksUseCaseTest.kt`, `ResolveLocalMixUseCaseTest.kt`, `SongModelTest.kt`; `app/.../RecommendationEngineTest.kt`, `RecommendationScoringTest.kt`, `LocalTasteScoringTest.kt`, `TasteProfileProviderTest.kt`, `GenresRepositoryTest.kt`, `GenerateViewModelTest.kt`, `GenerationStatusTest.kt`, `SongPlayStatsRepositoryTest.kt`, `BeatTrackerTest.kt`, `LoopMixTest.kt`, `VersionAudioAlignerCorrelationTest.kt`, `QuickPickSongsTest.kt`. |
| **Readiness** | **Tier 1 READY.** **Tier 2–4 NOT_READY.** |

---

## 3. Traceability table

Waves and invariants are taken from `REIMPLEMENTATION_ROADMAP.md` §3 and §5. Where the roadmap names a range (`F10–F14`) without per-feature enumeration, the mapping below is the test-track's assignment and is marked accordingly.

| Requirement | Capability | F invariant | Wave | Modules | Spec source | Existing Tier 1 | Status |
|---|---|---|---|---|---|---|---|
| REQ-STR-01..04 | Streaming / discovery | F6 | 3 | `:innertube`, `:app` | ARCH §5 Discovery; `docs/specs/` (pending) | Parsers, Serialization, Utils | READY T1 / NOT_READY T2–4 |
| REQ-PLY-01..07 | Playback modes | F5, F7 | 2 | `:playback`, `:app` | ARCH §5 Playback Engine | Queue, cutoff, crossfade, seek | READY T1 / NOT_READY T2–4 |
| REQ-DL-01..03 | Downloads | F5 | 2 | `:core`, `:app` | ARCH §5 Storage | ExportQueue, DownloadStateSync | READY T1 / NOT_READY T2–4 |
| REQ-DL-04 | Export | F5 | 2 | `:app` | ARCH §5 Storage | ExportQueue (ordering only) | **BLOCKED** (ffmpeg backend) |
| REQ-DSP-01..04 | EQ / AutoEQ | F7 | 2 | `:dsp-core`, `:audio-dsp` | `docs/specs/dsp-core.md` | Parser, biquad, processors | READY T1 / NOT_READY T2–4 |
| REQ-USB-01..04 | USB routing | F7 | 2 | `:usbaudio`, `:playback` | ARCH §5 Audio DSP & USB | Driver, sink, detector, volume | READY T1 / NOT_READY T2–4 |
| REQ-LYR-01..05 | Lyrics / translation | F8 | 3 | `:lyrics`, providers | ARCH §5 Lyrics | TTML, LRC, selection, AI parse | READY T1 / NOT_READY T2–4 |
| REQ-CAN-01..03 | Canvas / artist video | F9, F14 (assigned) | 5 | `:canvas`, `:applecanvas`, `:artistvideo` | ARCH §5 Canvas | Canvas, TIDAL, Apple, artist video | READY T1 / NOT_READY T2–4 |
| REQ-REC-01..05 | Recognition / Find | F10 (assigned) | 5 | `:shazamkit`, `:app` | ARCH §5 Recognition | `ShazamTest` | READY T1 (partial) / NOT_READY T2–4 |
| REQ-SPT-01..03 | Spotify | F10 (assigned) | 5 | `:app` | ARCH §5 Integrations | `SpotifyQueueTest` | READY T1 / NOT_READY T2–4 |
| REQ-JEL-01..03 | Jellyfin | F11 (assigned) | 5 | `:app`, `:core` | ARCH §5 Integrations | none | NOT_READY |
| REQ-NAV-01..03 | Navidrome | F11 (assigned) | 5 | `:app`, `:core` | ARCH §5 Integrations | none | NOT_READY |
| REQ-LT-01..04 | Listen Together | F11 (assigned) | 5 | `:app` | ARCH §5 Integrations | none | NOT_READY |
| REQ-CAST-01..03 | Cast (gms) | F12 (assigned) | 5 | `:app` (`gms`) | ARCH §3, §5 | none | **BLOCKED (FOSS CI)** / NOT_READY (gms) |
| REQ-AA-01..02 | Android Auto | F12 (assigned) | 5 | `:app` | ARCH §5 Integrations | none | NOT_READY |
| REQ-LFM-01..02 | Last.fm | F13 (assigned) | 5 | `:app` | ARCH §5 Integrations | `ScrobbleManagerTest` | READY T1 / NOT_READY T2–4 |
| REQ-LB-01 | ListenBrainz | F13 (assigned) | 5 | `:app` | ARCH §5 Integrations | none | NOT_READY |
| REQ-POD-01..03 | Podcasts | F13 (assigned) | 5 | `:app` | ARCH §5 Integrations | none | NOT_READY |
| REQ-WRP-01..03 | Wrapped / stats | F13 (assigned) | 5 | `:app`, `:core` | ARCH §5 Smart Audio/Flow | none | NOT_READY |
| REQ-WDG-01..04 | Widgets (10+1) | F14 | 5 | `:app` | ARCH §5 Widgets | none | NOT_READY |
| REQ-SYS-01..03 | Shortcuts / notifications | F14 | 4 | `:app` | ARCH §5 Widgets/Notifications | none | NOT_READY (static lint can run) |
| REQ-UPD-01..04 | Updater | F14 | 5 | `:app` | ARCH §5 Widgets/Updater | none | NOT_READY |
| REQ-LIB-01..03 | Local library / metadata | F3, F5 | 1–2 | `:metadata`, `:core` | `docs/specs/domain.md` | Metadata, converters | READY T1 / NOT_READY T2–4 |
| REQ-UI-01..06 | App shell / design system | F9, F14 | 4 | `:app` | ARCH §5 App Entry/Compose | Nav, hero carousel, launcher ext | READY T1 partial / NOT_READY T2–4 |
| REQ-RCM-01..04 | Recommendation / Smart Audio | F13 (assigned) | 1–5 | `:domain`, `:app`, `:core` | `docs/specs/domain.md` | Domain + app scoring suite | READY T1 / NOT_READY T2–4 |
| — | Cross-cutting E2E gate | F15 | Validation | all | VALIDATION §5 | none (no androidTest) | NOT_READY |
| — | Adversarial hardening | F16 | Validation | all | VALIDATION §5 | none | NOT_READY |

---

## 4. Wave 0 feature-decision disclosure (R2 rule 6 / `M2_GATE_DECISIONS.md` §3)

These are the **only** user-facing capability changes introduced by the Wave 0 gate. They must be reflected in release notes and must **not** be treated as silent test gaps. Tests below assert the new (reduced) behaviour so the removal is verifiable, not merely documented.

| # | Decision | User-visible effect | Test obligation | Status |
|---|---|---|---|---|
| W0-1 | **Launcher icon variants removed** — meme/celebrity/personal aliases deleted; picker trimmed to Default + Static. | Icon picker offers only Default and Static Revenge icons. | Tier 2/3: picker asserts exactly the two allowed entries; static manifest audit asserts removed `activity-alias` entries are absent. Celebrity likeness cannot be reinstated without cleared rights. | NOT_READY (Wave 4 UI) |
| W0-2 | **`Puter` AI provider removed.** Provider was listed but had no request path. | No Puter option in AI settings; translation/AI uses remaining providers. | Tier 1/2: settings model exposes no Puter provider; selecting AI translation never routes to Puter; no dead strings. | Implemented in Wave 0; test authoring NOT_READY (Wave 4 settings). |
| W0-3 | **HTTP/3 (Cronet) toggle removed.** `useCronet = false` was hard-disabled and the dependency was the FOSS GMS leak. | No Cronet toggle; RPC always OkHttp. No working capability lost. | Tier 2/4: FOSS DEX/manifest has zero `play-services-cronet`/`org.chromium.net`; no Cronet preference key; network path uses OkHttp. | Static check READY (`G-BUILD-5`); behavioral test NOT_READY. |
| W0-4 | **Neural beat tracking disabled** until a provenance-verified `CPJKU/beat_this` (MIT) re-export lands (Wave 5 follow-up). Both ONNX models removed. | Beat tracking degrades safely; all other Smart Audio features unaffected. | Tier 1: `BeatTracker.session()` returns `null` with the asset absent and playback proceeds; assert no `.onnx` asset ships in the APK. Re-enable test when re-export lands. | **READY T1** (`BeatTrackerTest` exists); asset-absence assertion READY. |

Additional Wave 0 disclosures carried from `M2_GATE_DECISIONS.md` §2–3 (not capability-level but test-relevant):
- `vocals_umxhq_int8.onnx` removed (zero code references) — assert asset absent.
- Apple-style player glyphs replaced by generic Material symbols — visual only; no behavioural test, covered by screenshot/golden checks in Tier 3 if adopted.
- OFL `sans_flex.ttf` v3.007 rebound — Tier 1/3 font-resource and theme-rebind test.

---

## 5. Cross-cutting release-gate tests (`F15`, `F16`)

| Gate test | Tier | Requirement | Fixtures/mocks | Status |
|---|---|---|---|---|
| Cold start, onboarding, notification permission | 3/4 | REQ-UI-01 | Fresh-install emulator snapshot | NOT_READY |
| Process recreation + queue recovery | 4 | REQ-PLY-07, REQ-UI-03 | Persisted state, kill harness | NOT_READY |
| Deep links + non-duplicating back stack | 3/4 | REQ-UI-02 | Deep-link URI suite | NOT_READY |
| Loading/empty/error/offline states | 3 | REQ-UI-06 | Fake repos in each state | NOT_READY |
| Room v48 migration (or declared clean v1) | 2 | ARCH §4 | `room-testing`, schema 1..48 JSON | NOT_READY (schema decision gate) |
| Backup/restore round-trip | 2/4 | F5 | Backup archive fixture | NOT_READY |
| FOSS binary purity (no GMS/Firebase/Cronet) | static | G-BUILD-5 | merged manifest/DEX | **READY** (scriptable now) |
| Malformed JSON/LRC/TTML/proto fuzz (`F16`) | 1/4 | F15/F16 | Fuzz corpus per parser | NOT_READY |
| Kill mid-download / mid-recognition (`F16`) | 4 | F16 | Process-kill harness | NOT_READY |
| Offline/flaky network storm (`F16`) | 4 | F16 | `MockWebServer` fault modes | NOT_READY |
| Similarity/origin scan (`G-PROV-1`) | static | R2 | scanner + baseline corpus | **READY** (tooling defined) |

---

## 6. Readiness summary

| Bucket | READY | NOT_READY | BLOCKED |
|---|---|---|---|
| Tier 1 (JVM) | streaming parsers, playback math, EQ/AutoEQ, USB logic, lyrics/translation parsers, canvas/artist-video models, Spotify queue, Last.fm scrobble, local metadata, recommendation/domain, beat-tracker null-path | Jellyfin, Navidrome, Listen Together, ListenBrainz, Podcasts, Wrapped, widgets, shortcuts/notifications, updater, recognition (partial) | export (ffmpeg backend) |
| Tier 2 (Robolectric) | FOSS binary/manifest purity (static) | all component/integration fixtures (harness absent) | Cast (FOSS CI by design) |
| Tier 3 (instrumented) | none | all (no `androidTest` source sets) | Cast (FOSS), USB hardware |
| Tier 4 (system) | none | all | USB hardware, Cast FOSS, export |

**Dominant blockers, in priority order:**
1. **No instrumentation infrastructure** — zero `androidTest` source sets and no Compose UI test / `androidx.test` dependencies. This alone keeps every Tier 3–4 item NOT_READY.
2. **No network mock layer** — no `MockWebServer`; Tiers 2–4 for streaming, lyrics, Spotify, Jellyfin, Navidrome, Listen Together, Last.fm, ListenBrainz, updater are unexecutable.
3. **Waves 2–5 not implemented** — features exist as baseline/partially-rebuilt code; clean-room tests cannot be certified against a moving target.
4. **Export codec** — `ffmpeg-kit-audio` retirement makes downloads/export E2E BLOCKED until a replacement is chosen (Wave 2).
5. **Room schema decision** — the v48-preserve vs clean-v1 choice (ARCH §4) gates migration tests.
6. **Hardware/Cast jobs** — USB DAC (real hardware) and Cast (`gms` instrumented job) cannot run in the default FOSS CI lane.

**Exit criteria to promote this document from `TEST_READY` to an executable `TEST_INFRA.md`:**
- Add `:app` `androidTest` + Compose UI test + `androidx.test` runner and a deterministic dispatcher rule.
- Add `MockWebServer` to `:innertube`, `:lyrics`, `:app` integration tests; add `room-testing` to `:core`.
- Commit shared fixture trees (InnerTube JSON, LRC/TTML corpus, AutoEq index, PCM/WAV, proto goldens, release JSON).
- Land Wave 2 (`:core`, `:playback`, `:audio-dsp`, `:usbaudio`) clean-room implementations so Tier 2 can bind to stable seams.
- Resolve the export-codec and Room-schema decisions.
- Wire `G-BUILD-5`, `G-SEC-2`, and `G-PROV-1` static gates into CI so their READY status is enforced continuously.

---

## 7. Uncertainty disclosure

- Per-feature `F`-invariant assignment for F10–F14 is the test track's mapping of a roadmap range that is not enumerated per capability; confirm against the final M2 spec set before M6 sign-off.
- Existing test counts (79 files) are measured on the working tree, not the frozen baseline (1,547 tests / 304 suites). The working tree is mid-Wave-1; the baseline figure must not be cited as current coverage.
- Export, Cast, USB Tier 4 and updater depend on decisions/hardware/licensing that are outside the test track's authority; their BLOCKED status is a disclosure, not a test failure.
- No Tier 2–4 claim in this document should be read as passing; all are NOT_READY pending the harness backlog in §6.
